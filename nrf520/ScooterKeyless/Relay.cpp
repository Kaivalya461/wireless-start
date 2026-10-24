#include "Relay.h"
#include "BleManager.h"
#include <sys/time.h>

// Night Sleep Window (1:30 AM to 8:30 AM)
#define SLEEP_START_HOUR   1
#define SLEEP_START_MIN    30
#define WAKE_END_HOUR      8
#define WAKE_END_MIN       30

const int SLEEP_START_MINUTES_TOTAL = (SLEEP_START_HOUR * 60) + SLEEP_START_MIN;
const int WAKE_END_MINUTES_TOTAL    = (WAKE_END_HOUR * 60) + WAKE_END_MIN;
const unsigned long INACTIVITY_SLEEP_WINDOW_MS = 15UL * 60UL * 1000UL;

// Operational Safety Constants
const unsigned long MIN_SAFE_PULSE_MS      = 100;
const unsigned long MAX_SAFE_PULSE_MS      = 5000;
const unsigned long DEFAULT_PULSE_MS       = 1500;
const unsigned long STARTER_COOLDOWN_MS    = 3000;

// Fail-Safe Variables
const unsigned long DISCONNECT_GRACE_PERIOD_MS = 20000;
unsigned long disconnectionStartTime       = 0;
bool isDisconnectTimerActive               = false;
bool lastBleConnectionState                = false;
bool stopFailSafeEnabled                   = true;
static bool hasEverConnected               = false;

unsigned long lastStartExecutionTime       = 0;
bool isPulseActive                         = false;

int activeRelayPin                         = -1;
unsigned long pulseStartTime               = 0;
unsigned long activePulseDuration          = 0;
bool timeIsSynced                          = false;

// Scheduled Task Variables
unsigned long targetExecutionEpoch         = 0;
bool scheduledTaskPending                  = false;
const unsigned long SCHEDULED_TASK_DURATION_MS = 2UL * 60UL * 1000UL;

static bool isScheduledTaskActive          = false;
static unsigned long scheduledTaskStartTime = 0;

unsigned long lastSchedLogTime             = 0;
const unsigned long SCHED_LOG_INTERVAL_MS  = 30UL * 1000UL;

void setRelayState(int pin, bool state) {
    if (pin == START_RELAY_PIN) {
        // START Relay: Active-HIGH
        digitalWrite(pin, state ? HIGH : LOW);
    } else if (pin == STOP_RELAY_PIN) {
        // STOP Relay: Active-LOW
        digitalWrite(pin, state ? LOW : HIGH);
    }
}

bool getIsPulseActive() {
    return isPulseActive;
}

void initRelays() {
    // Configure Start Relay
    pinMode(START_RELAY_PIN, OUTPUT);
    setRelayState(START_RELAY_PIN, false);

    // Configure Stop Relay
    pinMode(STOP_RELAY_PIN, OUTPUT);
    setRelayState(STOP_RELAY_PIN, false);

    if (Serial) {
        Serial.println(">>> Relays Initialized on nRF52840.");
    }
}

void syncTime(unsigned long epochTime) {
    struct timeval tv;
    tv.tv_sec = epochTime;
    tv.tv_usec = 0;
    settimeofday(&tv, NULL);
    timeIsSynced = true;

    if (Serial) {
        Serial.print(">>> System Time Synced: ");
        Serial.println(epochTime);
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
        if (Serial) Serial.println(">>> REJECTED: Hardware pulse already in progress!");
        return false;
    }

    if (pin != START_RELAY_PIN && pin != STOP_RELAY_PIN) {
        if (Serial) Serial.println(">>> REJECTED: Invalid relay pin requested!");
        return false;
    }

    if (pin == START_RELAY_PIN && (now - lastStartExecutionTime < STARTER_COOLDOWN_MS)) {
        if (Serial) Serial.println(">>> REJECTED: Starter motor cooling down!");
        return false;
    }

    activeRelayPin      = pin;
    pulseStartTime      = now;
    activePulseDuration = durationMs;
    isPulseActive       = true;

    if (pin == START_RELAY_PIN) {
        setRelayState(pin, true);
        if (Serial) {
            Serial.print("-> Start Relay ON for ");
            Serial.print(durationMs);
            Serial.println(" ms");
        }
        lastStartExecutionTime = now;
    } else if (pin == STOP_RELAY_PIN) {
        setRelayState(pin, false);
        if (Serial) {
            Serial.print("-> Stop Relay RELEASED (OFF) for ");
            Serial.print(durationMs);
            Serial.println(" ms");
        }
    }
    return true;
}

void updateRelayPulses() {
    if (isPulseActive) {
        if (millis() - pulseStartTime >= activePulseDuration) {
            if (activeRelayPin == START_RELAY_PIN) {
                setRelayState(activeRelayPin, false);
                if (Serial) Serial.println("-> Start pulse complete. Relay restored to OFF.");
            } else if (activeRelayPin == STOP_RELAY_PIN) {
                bool restoreState = isBleClientConnected() && stopFailSafeEnabled;
                setRelayState(activeRelayPin, restoreState);
                if (Serial) Serial.println("-> Stop pulse complete. Relay restored.");
            }
            isPulseActive = false;
            activeRelayPin = -1;
        }
    }
}

void updateStopRelayFailSafe() {
    if (isPulseActive && activeRelayPin == STOP_RELAY_PIN) {
        return;
    }

    if (!stopFailSafeEnabled) {
        setRelayState(STOP_RELAY_PIN, false);
        isDisconnectTimerActive = false;
        lastBleConnectionState = isBleClientConnected();
        return;
    }

    bool currentBleState = isBleClientConnected();

    if (currentBleState) {
        hasEverConnected = true;
        setRelayState(STOP_RELAY_PIN, true); // Active-Low -> LOW (Closed)

        if (isDisconnectTimerActive) {
            isDisconnectTimerActive = false;
            if (Serial) Serial.println(">>> Phone reconnected within grace period.");
        }
    } else {
        if (!hasEverConnected) {
            setRelayState(STOP_RELAY_PIN, false);
            return;
        }

        if (lastBleConnectionState || !isDisconnectTimerActive) {
            disconnectionStartTime = millis();
            isDisconnectTimerActive = true;
            if (Serial) Serial.println(">>> BLE connection lost! Starting grace period...");
        }

        if (isDisconnectTimerActive && (millis() - disconnectionStartTime >= DISCONNECT_GRACE_PERIOD_MS)) {
            setRelayState(STOP_RELAY_PIN, false); // Kill switch active
        } else {
            setRelayState(STOP_RELAY_PIN, true);
        }
    }

    lastBleConnectionState = currentBleState;
}

void setStopFailSafeActive(bool active) {
    stopFailSafeEnabled = active;
    if (active) {
        setRelayState(STOP_RELAY_PIN, true);
    } else {
        lastBleConnectionState = isBleClientConnected();
        isDisconnectTimerActive = false;
    }
}

unsigned long getScheduledTaskEpoch() {
    return targetExecutionEpoch;
}

void setScheduledTaskEpoch(unsigned long epochTime) {
    targetExecutionEpoch = epochTime;
    scheduledTaskPending = (epochTime > 0);
    lastSchedLogTime = millis();
}

void checkScheduledEngineTask() {
    if (isScheduledTaskActive) {
        if (!isPulseActive || activeRelayPin != STOP_RELAY_PIN) {
            setRelayState(STOP_RELAY_PIN, true);
        }

        if (millis() - scheduledTaskStartTime >= SCHEDULED_TASK_DURATION_MS) {
            if (Serial) Serial.println(">>> Scheduled Task Duration Reached: Stopping engine.");
            setRelayState(STOP_RELAY_PIN, false);
            isScheduledTaskActive = false;
        }
        return;
    }
}