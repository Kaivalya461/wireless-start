#include "Battery.h"
#include "Globals.h"

const float DIVIDER_RATIO = 5.7;
const float FILTER_ALPHA = 0.222; //1.0: No smoothing at all (passes raw instant voltage straight through)
float smoothedBatteryVoltage = -1.0;
bool telemetryEnabled = false; // Initial fallback state

// Dynamic calibration variables loaded from NVS
float calibrationMultiplier = 1.109;
float calibrationOffset = 0.81;

// Auto-compiler detection selects the proper multiplier profile based on board target
#if defined(ARDUINO_ESP32C3_DEV) || defined(ARDUINO_ESP32_C3_SUPER_MINI)
const int BATTERY_PIN = 4; // Use GPIO 4 for ESP32-C3 Super Mini
#else
const int BATTERY_PIN = 34; // Default to GPIO 34 for standard ESP32 Dev Board
#endif

void initBattery() {
    pinMode(BATTERY_PIN, INPUT);
    analogSetAttenuation(ADC_11db); // Keep 11dB to read 14.X Volts safely

    // Initialize Non-Volatile Storage (NVS) for Telemetry state persistence
    preferences.begin("telemetry", false); // Namespace: "telemetry", read-write mode: false

    // Load persisted state. Defaults to false if it's the very first boot.
    telemetryEnabled = preferences.getBool("telemetry_state", false);

    // Load per-device calibration values from NVS (fallback to defaults if first boot)
    calibrationMultiplier = preferences.getFloat("cal_mult", 1.109);
    calibrationOffset = preferences.getFloat("cal_off", 0.81);

    Serial.printf(">>> Loaded Telemetry State: %s\n", telemetryEnabled ? "ENABLED" : "DISABLED");
    Serial.printf(">>> Loaded Calibration -> Mult: %.3f, Offset: %.3f\n", calibrationMultiplier, calibrationOffset);
}

void updateBatteryFilter() {
    uint32_t pinMilliVolts = analogReadMilliVolts(BATTERY_PIN);
    float pinVoltage = pinMilliVolts / 1000.0;
    float voltageAfterDiode = pinVoltage * DIVIDER_RATIO;

    float instantBatteryVoltage = (voltageAfterDiode * calibrationMultiplier) + calibrationOffset;
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

// Optional helper function to dynamically save new calibration values for Board 2 via code/serial
void setCalibration(float mult, float offset) {
    calibrationMultiplier = mult;
    calibrationOffset = offset;
    preferences.putFloat("cal_mult", calibrationMultiplier);
    preferences.putFloat("cal_off", calibrationOffset);
    Serial.printf(">>> New Calibration Saved -> Mult: %.3f, Offset: %.3f\n", calibrationMultiplier, calibrationOffset);
}