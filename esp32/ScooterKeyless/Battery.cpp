#include "Battery.h"
#include <Preferences.h>

Preferences preferences;

const float DIVIDER_RATIO = 5.7;
const float FILTER_ALPHA = 0.05;
float smoothedBatteryVoltage = -1.0;
bool telemetryEnabled = false; // Initial fallback state

// Auto-compiler detection selects the proper multiplier profile based on board target
#if defined(ARDUINO_ESP32C3_DEV) || defined(ARDUINO_ESP32_C3_SUPER_MINI)
const int BATTERY_PIN = 4; // Use GPIO 4 for ESP32-C3 Super Mini
const float CALIBRATION_MULTIPLIER = 1.109;
#else
const int BATTERY_PIN = 34; // Default to GPIO 34 for standard ESP32 Dev Board
const float CALIBRATION_MULTIPLIER = 1.079;
#endif

void initBattery() {
    pinMode(BATTERY_PIN, INPUT);
    analogSetAttenuation(ADC_11db); // Keep 11dB to read 14.X Volts safely

    // Initialize Non-Volatile Storage (NVS) for Telemetry state persistence
    preferences.begin("telemetry", false); // Namespace: "telemetry", read-write mode: false

    // Load persisted state. Defaults to false if it's the very first boot.
    telemetryEnabled = preferences.getBool("telemetry_state", false);

    Serial.printf(">>> Loaded Telemetry State from NVS: %s\n", telemetryEnabled ? "ENABLED" : "DISABLED");
}

void updateBatteryFilter() {
    uint32_t pinMilliVolts = analogReadMilliVolts(BATTERY_PIN);
    float pinVoltage = pinMilliVolts / 1000.0;
    float voltageAfterDiode = pinVoltage * DIVIDER_RATIO;

    float instantBatteryVoltage = (voltageAfterDiode * CALIBRATION_MULTIPLIER) + 0.81;
    if (pinMilliVolts == 0) instantBatteryVoltage = 0.0;

    if (smoothedBatteryVoltage < 0.0) {
        smoothedBatteryVoltage = instantBatteryVoltage;
    } else {
        smoothedBatteryVoltage = (FILTER_ALPHA * instantBatteryVoltage) + ((1.0 - FILTER_ALPHA) * smoothedBatteryVoltage);
    }
}

float getBatteryVoltage() {
    return smoothedBatteryVoltage;
}

uint16_t getBatteryMilliVolts() {
    if (smoothedBatteryVoltage < 0) return 0;
    return (uint16_t)(smoothedBatteryVoltage * 1000.0); // 12.66V becomes 12660
}

void setTelemetryActive(bool active) {
    if (telemetryEnabled != active) {
        telemetryEnabled = active;

        // Save the updated state to flash memory instantly
        preferences.putBool("telemetry_state", telemetryEnabled);
        Serial.printf(">>> Telemetry State Saved to NVS: %s\n", telemetryEnabled ? "ENABLED" : "DISABLED");
    }

    if (!active) {
        smoothedBatteryVoltage = -1.0; // Reset tracking if disabled
    }
}

bool isTelemetryEnabled() {
    return telemetryEnabled;
}
