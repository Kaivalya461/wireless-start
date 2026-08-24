// Relay.cpp
#include "Relay.h"
#include "BleManager.h" // Needed to check if a phone is connected before sleeping
#include "esp_sleep.h"
#include <sys/time.h>

// --- Relay Logic Configuration ---
// Set to true if LOW turns the relay ON (common for optocoupler modules/MOSFETs)
// Set to false if HIGH turns the relay ON
//bool RELAY_ACTIVE_LOW = false;

// Night Sleep Window (1:30 AM to 8:30 AM)
#define SLEEP_START_HOUR   1
#define SLEEP_START_MIN    30
#define WAKE_END_HOUR      8
#define WAKE_END_MIN       30

const int SLEEP_START_MINUTES_TOTAL = (SLEEP_START_HOUR * 60) + SLEEP_START_MIN;
const int WAKE_END_MINUTES_TOTAL    = (WAKE_END_HOUR * 60) + WAKE_END_MIN;
// 15 minutes inactivity before going to sleep during Night Time
const unsigned long INACTIVITY_SLEEP_WINDOW_MS = 15UL * 60UL * 1000UL; // 15 minutes

// Operational Safety Constants
const unsigned long MIN_SAFE_PULSE_MS      = 100;
const unsigned long MAX_SAFE_PULSE_MS      = 5000;
const unsigned long DEFAULT_PULSE_MS       = 1500;
const unsigned long STARTER_COOLDOWN_MS    = 3000;

// Fail-Safe Stop Relay & Debounce Configuration Variables
const unsigned long DISCONNECT_GRACE_PERIOD_MS = 20000; // 20-second anti-stall grace period
unsigned long disconnectionStartTime       = 0;
bool isDisconnectTimerActive               = false;
bool lastBleConnectionState                = false;
// Fail-Safe User Configuration Variable (Defaults to true)
bool stopFailSafeEnabled = true;
static bool hasEverConnected = false;

unsigned long lastStartExecutionTime       = 0;
bool isPulseActive                         = false;

int activeRelayPin                         = -1;
unsigned long pulseStartTime               = 0;
unsigned long activePulseDuration          = 0;
bool timeIsSynced                          = false;

// --- Helper Functions for Mixed Active-High / Active-Low Logic ---
void setRelayState(int pin, bool state) {
    if (pin == START_RELAY_PIN) {
        // START Relay is Active-HIGH (TRUE = HIGH, FALSE = LOW)
        digitalWrite(pin, state ? HIGH : LOW);
    } else if (pin == STOP_RELAY_PIN) {
        // STOP Relay is Active-LOW (TRUE = LOW, FALSE = HIGH)
        digitalWrite(pin, state ? LOW : HIGH);
    }
}

bool getIsPulseActive() {
    return isPulseActive;
}

void initRelays() {
    // Configure Start Relay (Active-High)
    pinMode(START_RELAY_PIN, OUTPUT);
    setRelayState(START_RELAY_PIN, false);
    gpio_set_pull_mode((gpio_num_t)START_RELAY_PIN, GPIO_PULLDOWN_ONLY);

    // Configure Stop Relay (Active-Low)
    pinMode(STOP_RELAY_PIN, OUTPUT);
    setRelayState(STOP_RELAY_PIN, false);
    gpio_set_pull_mode((gpio_num_t)STOP_RELAY_PIN, GPIO_PULLUP_ONLY);

    gpio_hold_dis((gpio_num_t)START_RELAY_PIN);
    gpio_hold_dis((gpio_num_t)STOP_RELAY_PIN);
    gpio_deep_sleep_hold_dis();
}

void syncTime(unsigned long epochTime) {
    struct timeval tv;
    tv.tv_sec = epochTime;
    tv.tv_usec = 0;
    settimeofday(&tv, NULL);
    timeIsSynced = true;
    Serial.print(">>> System Time Synced: ");
    Serial.println(epochTime);
}

void enterDeepSleep(uint64_t sleepDurationSeconds) {
    Serial.println(">>> Entering Scheduled Night Deep Sleep...");

    setRelayState(START_RELAY_PIN, false);
    setRelayState(STOP_RELAY_PIN, false);

    gpio_hold_en((gpio_num_t)START_RELAY_PIN);
    gpio_hold_en((gpio_num_t)STOP_RELAY_PIN);
    gpio_deep_sleep_hold_en();

    esp_sleep_enable_timer_wakeup(sleepDurationSeconds * 1000000ULL);
    Serial.flush();
    esp_deep_sleep_start();
}

void checkScheduledNightSleep() {
    // 1. Check basic blocks: time must be synced, no active pulse, and NO current active BLE connection
    if (!timeIsSynced || isBleClientConnected() || isPulseActive) return;

    // 2. Enforce the 15-minute connection inactivity window after a drop
    unsigned long timeSinceDisconnection = millis() - getDisconnectionTime();
    if (timeSinceDisconnection < INACTIVITY_SLEEP_WINDOW_MS) {
        return; // Skip deep sleep until 15 minutes of continuous disconnection have passed
    }

    time_t now;
    struct tm timeinfo;
    time(&now);
    localtime_r(&now, &timeinfo);

    int currentTotalMinutes = (timeinfo.tm_hour * 60) + timeinfo.tm_min;

    if (currentTotalMinutes >= SLEEP_START_MINUTES_TOTAL && currentTotalMinutes < WAKE_END_MINUTES_TOTAL) {
        int minutesToWait = WAKE_END_MINUTES_TOTAL - currentTotalMinutes - 1;
        int secondsToWait = 59 - timeinfo.tm_sec;
        uint64_t totalSleepSeconds = (minutesToWait * 60) + secondsToWait;

        Serial.print(">>> Night Window & 15-min Inactivity Met. Sleeping for ");
        Serial.print(totalSleepSeconds / 3600.0);
        Serial.println(" hours until 8:30 AM.");

        enterDeepSleep(totalSleepSeconds);
    }
}

unsigned long getValidatedDuration(unsigned long customMs) {
    if (customMs < MIN_SAFE_PULSE_MS || customMs > MAX_SAFE_PULSE_MS) {
        return DEFAULT_PULSE_MS;
    }
    return customMs;
}

bool requestRelayPulse(int pin, unsigned long durationMs) {
    unsigned long now = millis();

    if (isPulseActive) {
        Serial.println(">>> REJECTED: Hardware pulse already in progress!");
        return false;
    }

    if (pin != START_RELAY_PIN && pin != STOP_RELAY_PIN) {
        Serial.println(">>> REJECTED: Invalid relay pin requested!");
        return false;
    }

    if (pin == START_RELAY_PIN && (now - lastStartExecutionTime < STARTER_COOLDOWN_MS)) {
        Serial.print(">>> REJECTED: Starter motor cooling down! Wait ");
        Serial.print((STARTER_COOLDOWN_MS - (now - lastStartExecutionTime)) / 1000.0);
        Serial.println("s.");
        return false;
    }

    activeRelayPin       = pin;
    pulseStartTime       = now;
    activePulseDuration  = durationMs;
    isPulseActive        = true;

    // --- PULSE BEHAVIOR SPLIT ---
    if (pin == START_RELAY_PIN) {
        // Start Relay: Standard pulse ON (energize)
        setRelayState(pin, true);
        Serial.print("-> Start Relay ON for ");
        Serial.print(durationMs);
        Serial.println(" ms");
        lastStartExecutionTime = now;
    } else if (pin == STOP_RELAY_PIN) {
        // Stop Relay: Temporary pulse OFF (release/open circuit)
        setRelayState(pin, false);
        Serial.print("-> Stop Relay RELEASED (OFF) for ");
        Serial.print(durationMs);
        Serial.println(" ms");
    }

    if (pin == START_RELAY_PIN) {
        lastStartExecutionTime = now;
    }
    return true;
}

void updateRelayPulses() {
    if (isPulseActive) {
        if (millis() - pulseStartTime >= activePulseDuration) {
            // --- RESTORE BEHAVIOR SPLIT ---
            if (activeRelayPin == START_RELAY_PIN) {
                setRelayState(activeRelayPin, false); // Turn Start Relay back OFF
                Serial.println("-> Start pulse complete. Relay restored to OFF.");
            } else if (activeRelayPin == STOP_RELAY_PIN) {
                // Restore Stop Relay back to active fail-safe state (energized / closed) if connected, 
                // or let the fail-safe workflow manage it.
                bool restoreState = isBleClientConnected() && stopFailSafeEnabled;
                setRelayState(activeRelayPin, restoreState);
                Serial.println("-> Stop pulse complete. Relay restored to operational state.");
            }

            isPulseActive = false;
            activeRelayPin = -1;
        }
    }
}

// --- Updated Fail-Safe Stop Relay Workflow with User-Configurable Check ---
void updateStopRelayFailSafe() {
    // If a hardware pulse (User Action) is actively running on the stop relay,
    // pause fail-safe overrides so the pulse can freely control the pin.
    if (isPulseActive && activeRelayPin == STOP_RELAY_PIN) {
        return;
    }

    // Check if the user has turned off the fail-safe feature from the Android app
    if (!stopFailSafeEnabled) {
        // Feature is disabled: Keep the stop relay de-energized
        setRelayState(STOP_RELAY_PIN, false);

        // Reset timers so it triggers cleanly if re-enabled later
        isDisconnectTimerActive = false;
        lastBleConnectionState = isBleClientConnected();
        return;
    }

    // --- Original Fail-Safe Logic (Runs only when enabled) ---
    bool currentBleState = isBleClientConnected();

    if (currentBleState) {
        // Connected: Keep Stop Relay held closed (Active-Low -> LOW)
        hasEverConnected = true;
        setRelayState(STOP_RELAY_PIN, true);

        if (isDisconnectTimerActive) {
            isDisconnectTimerActive = false;
            Serial.println(">>> Phone reconnected within grace period. Ride uninterrupted!");
        }

        if (!lastBleConnectionState) {
            Serial.println(">>> Phone Connected: Fail-safe disarmed.");
        }
    } else {
        // --- COLD START PROTECTION ---
        // If the phone has never connected since boot, do NOT trigger a grace period.
        // Just keep the stop relay de-energized until the user actually connects their phone.
        if (!hasEverConnected) {
            setRelayState(STOP_RELAY_PIN, false);
            return;
        }

        // Disconnected: Handle grace period before cutting power
        if (lastBleConnectionState || !isDisconnectTimerActive) {
            disconnectionStartTime = millis();
            isDisconnectTimerActive = true;
            Serial.println(">>> BLE connection lost! Starting 4s grace period...");
        }

        if (isDisconnectTimerActive && (millis() - disconnectionStartTime >= DISCONNECT_GRACE_PERIOD_MS)) {
            // Grace period expired — Kill switch active (Active-Low -> HIGH)
            setRelayState(STOP_RELAY_PIN, false);
//            Serial.println(">>> Grace period expired. Fail-safe ARMED (Stop relay opened).");
        } else {
            // Within grace period window — keep relay energized to prevent stalling
            setRelayState(STOP_RELAY_PIN, true);
        }
    }

    lastBleConnectionState = currentBleState;
}

void setStopFailSafeActive(bool active) {
    stopFailSafeEnabled = active;

    if (active) {
        // Force the stop relay to stay securely closed right now!
        setRelayState(STOP_RELAY_PIN, true);
    } else {
        // Re-arm tracking state so it guards against drops right away
        lastBleConnectionState = isBleClientConnected();
        isDisconnectTimerActive = false;
    }
}