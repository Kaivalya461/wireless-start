#ifndef BATTERY_MANAGER_H
#define BATTERY_MANAGER_H

#include "ConfigManager.h"
#include "BleManager.h"

// Note: PIN_VBAT is already defined by the Seeed nRF52 board package as (32).
// Digital pin 14 controls the voltage divider enable pin (P0.14).
#define PIN_VBAT_ENABLE 14

// Variables for non-blocking battery tracking
unsigned long lastBatteryCheck = 0;
const unsigned long batteryInterval = 5000; // Check every 5 seconds (adjust as needed)
float currentBatteryVoltage = 0.0;
uint8_t currentBatteryPercent = 0;

void initBatteryMonitor() {
    pinMode(PIN_VBAT_ENABLE, OUTPUT);
    digitalWrite(PIN_VBAT_ENABLE, HIGH); // Keep disabled by default to save power
}

float readBatteryVoltage() {
    digitalWrite(PIN_VBAT_ENABLE, LOW); // Enable voltage divider
    delayMicroseconds(500);             // Allow capacitor to charge / stabilize

    analogReference(AR_INTERNAL_3_0);
    analogReadResolution(12);

    analogRead(PIN_VBAT);
    delayMicroseconds(10);

    int rawValue = analogRead(PIN_VBAT);
    digitalWrite(PIN_VBAT_ENABLE, HIGH); // Disable voltage divider to save power

    // 1. Calculate base voltage at the ADC pin using the 3.0V reference
    float measuredV = (rawValue / 4095.0) * 3.0;

    // 2. Multiply by the hardware voltage divider ratio (3.0 for Seeed nRF52 boards)
    measuredV = measuredV * 3.0;

    return measuredV;
}

uint8_t calculateBatteryPercentage(float voltage) {
    if (voltage >= 4.15) return 100;
    if (voltage <= 3.30) return 0;
    return (uint8_t)((voltage - 3.30) / (4.15 - 3.30) * 100.0);
}

// Optimized 2-byte millivolt getter for BLE notifications (e.g., 4200 mV)
uint16_t getBatteryMilliVolts() {
    return (uint16_t)(currentBatteryVoltage * 1000.0);
}

void updateBatteryTask() {
    // If telemetry is globally turned off in configuration, skip checking battery
    if (!currentConfig.liveBatteryVoltageEnabled) {
        return;
    }

    unsigned long currentMillis = millis();
    if (currentMillis - lastBatteryCheck >= batteryInterval || lastBatteryCheck == 0) {
        lastBatteryCheck = currentMillis;

        // Check if anyone is actually listening (BLE Client or USB Serial Debug)
        bool usbConnected = (NRF_POWER->USBREGSTATUS & POWER_USBREGSTATUS_VBUSDETECT_Msk) != 0;
        bool clientConnected = isBleClientConnected(); // Or Bluefruit.connected()

        // If nobody is connected anywhere, skip the hardware read to save power!
        if (!usbConnected && !clientConnected) {
            return;
        }

        // Safe to read since someone is listening
        currentBatteryVoltage = readBatteryVoltage();
        currentBatteryPercent = calculateBatteryPercentage(currentBatteryVoltage);

        // 1. Print over Serial if USB debug is connected
        if (usbConnected) {
            Serial.print("Battery Telemetry: ");
            Serial.print(currentBatteryVoltage, 2);
            Serial.print("V (");
            Serial.print(currentBatteryPercent);
            Serial.print("%) -> ");
            Serial.print(getBatteryMilliVolts());
            Serial.println(" mV");
        }

        // 2. Send over BLE if a client is connected
        if (clientConnected) {
            uint16_t currentMilliVolts = getBatteryMilliVolts();
            transmitBatteryTelemetry(currentMilliVolts);
        }
    }
}

void setTelemetryActive(bool active) {
    currentConfig.liveBatteryVoltageEnabled = active;
    saveConfig();

    if (Serial) {
        Serial.print(">>> Telemetry Configuration Updated: ");
        Serial.println(active ? "ACTIVE" : "INACTIVE");
    }
}

bool isTelemetryEnabled() {
    return currentConfig.liveBatteryVoltageEnabled;
}

#endif