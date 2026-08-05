#include "Relay.h"
#include "BleManager.h" // Needed to check if a phone is connected before sleeping
#include "esp_sleep.h"
#include <sys/time.h>

// --- Relay Logic Configuration ---
// Set to true if LOW turns the relay ON (common for optocoupler modules/MOSFETs)
// Set to false if HIGH turns the relay ON
bool RELAY_ACTIVE_LOW = true;

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

unsigned long lastStartExecutionTime       = 0;
bool isPulseActive                         = false;

int activeRelayPin                         = -1;
unsigned long pulseStartTime               = 0;
unsigned long activePulseDuration          = 0;
bool timeIsSynced                          = false;

// --- Helper Functions for Dynamic Relay Logic ---
void setRelayState(int pin, bool state) {
    if (RELAY_ACTIVE_LOW) {
        digitalWrite(pin, state ? LOW : HIGH);
    } else {
        digitalWrite(pin, state ? HIGH : LOW);
    }
}

bool getIsPulseActive() {
    return isPulseActive;
}

void initRelays() {
    pinMode(START_RELAY_PIN, OUTPUT);
    setRelayState(START_RELAY_PIN, false); // Force off safely instantly

    // Retain states across deep sleep cycles safely
    if (RELAY_ACTIVE_LOW) {
        gpio_set_pull_mode((gpio_num_t)START_RELAY_PIN, GPIO_PULLUP_ONLY);
    } else {
        gpio_set_pull_mode((gpio_num_t)START_RELAY_PIN, GPIO_PULLDOWN_ONLY);
    }

    gpio_hold_dis((gpio_num_t)START_RELAY_PIN);
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

    setRelayState(START_RELAY_PIN, false); // Ensure Relay is OFF before sleeping
    gpio_hold_en((gpio_num_t)START_RELAY_PIN);
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

    setRelayState(pin, true); // Turn Relay ON using dynamic logic
    Serial.print("-> Relay PIN ");
    Serial.print(pin);
    Serial.print(" ON for ");
    Serial.print(durationMs);
    Serial.println(" ms");

    if (pin == START_RELAY_PIN) {
        lastStartExecutionTime = now;
    }
    return true;
}

void updateRelayPulses() {
    if (isPulseActive) {
        if (millis() - pulseStartTime >= activePulseDuration) {
            setRelayState(activeRelayPin, false); // Turn Relay OFF using dynamic logic
            Serial.println("-> Pulse complete. Relay Pin restored to OFF state.");
            isPulseActive = false;
            activeRelayPin = -1;
        }
    }
}