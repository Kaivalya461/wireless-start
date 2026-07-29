#include "Battery.h"

const int BATTERY_PIN = 34;
const float DIVIDER_RATIO = 5.7;
const float FILTER_ALPHA = 0.05;
float smoothedBatteryVoltage = -1.0;

// Auto-compiler detection selects the proper multiplier profile based on board target
#if defined(ARDUINO_ESP32C3_DEV) || defined(ARDUINO_ESP32_C3_SUPER_MINI)
const float CALIBRATION_MULTIPLIER = 1.084;
#else
const float CALIBRATION_MULTIPLIER = 1.079;
#endif

void initBattery() {
    pinMode(BATTERY_PIN, INPUT);
    analogSetAttenuation(ADC_11db); // Keep 11dB to read 14.X Volts safely
}

void updateBatteryFilter() {
    uint32_t pinMilliVolts = analogReadMilliVolts(BATTERY_PIN);
    float pinVoltage = pinMilliVolts / 1000.0;
    float voltageAfterDiode = pinVoltage * DIVIDER_RATIO;

    float instantBatteryVoltage = (voltageAfterDiode * CALIBRATION_MULTIPLIER) + 0.31;
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

bool batteryStreamEnabled = false; // Disabled by default on boot

void setBatteryStreamActive(bool active) {
    batteryStreamEnabled = active;
    if (!active) {
        smoothedBatteryVoltage = -1.0; // Reset tracking if disabled
    }
}

bool isBatteryStreamActive() {
    return batteryStreamEnabled;
}
