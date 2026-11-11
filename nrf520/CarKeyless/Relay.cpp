#include "Relay.h"

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

// Software-based Epoch Time Tracking
static unsigned long baseEpochTime         = 0;
static unsigned long baseMillisMark        = 0;

void setRelayState(int pin, bool state) {
    digitalWrite(pin, state ? HIGH : LOW);
}

bool getIsPulseActive() {
    return isPulseActive;
}

void initRelays() {
    // Configure Unlock Relay
    pinMode(UNLOCK_RELAY_PIN, OUTPUT);
    setRelayState(UNLOCK_RELAY_PIN, false);

    // Configure Lock Relay
    pinMode(LOCK_RELAY_PIN, OUTPUT);
    setRelayState(LOCK_RELAY_PIN, false);

    // Configure Power Relay
    pinMode(POWER_RELAY_PIN, OUTPUT);
    setRelayState(POWER_RELAY_PIN, false);

    if (Serial) {
        Serial.println(">>> Relays Initialized on nRF52840.");
    }
}

// Software time synchronization using millis() offset
void syncTime(unsigned long epochTime) {
    baseEpochTime = epochTime;
    baseMillisMark = millis();
    timeIsSynced = true;

    if (Serial) {
        Serial.print(">>> System Time Synced (Software Offset): ");
        Serial.println(epochTime);
    }
}

// Helper to get current epoch time anywhere in your code if needed
unsigned long getCurrentEpoch() {
    if (!timeIsSynced) return 0;
    return baseEpochTime + ((millis() - baseMillisMark) / 1000);
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

    if (pin != UNLOCK_RELAY_PIN && pin != LOCK_RELAY_PIN) {
        if (Serial) Serial.println(">>> REJECTED: Invalid relay pin requested!");
        return false;
    }

    if (pin == UNLOCK_RELAY_PIN && (now - lastStartExecutionTime < STARTER_COOLDOWN_MS)) {
        if (Serial) Serial.println(">>> REJECTED: Cooldown period active!");
        return false;
    }

    activeRelayPin      = pin;
    pulseStartTime      = now;
    activePulseDuration = durationMs;
    isPulseActive       = true;

    if (pin == UNLOCK_RELAY_PIN) {
        setRelayState(pin, true);
        if (Serial) {
            Serial.print("-> Unlock Relay ON for ");
            Serial.print(durationMs);
            Serial.println(" ms");
        }
        lastStartExecutionTime = now;
    } else if (pin == LOCK_RELAY_PIN) {
        setRelayState(pin, true);
        if (Serial) {
            Serial.print("-> Lock Relay ON for ");
            Serial.print(durationMs);
            Serial.println(" ms");
        }
    }
    return true;
}

void updateRelayPulses() {
    if (isPulseActive) {
        if (millis() - pulseStartTime >= activePulseDuration) {
            setRelayState(activeRelayPin, false);
            if (Serial) {
                Serial.print("-> Pulse complete on pin ");
                Serial.print(activeRelayPin);
                Serial.println(". Relay restored to OFF.");
            }
            isPulseActive = false;
            activeRelayPin = -1;
        }
    }
}