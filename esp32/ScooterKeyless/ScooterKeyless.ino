#include "Battery.h"
#include "Relay.h"
#include "BleManager.h"
#include "ConfigManager.h"

unsigned long lastBatteryCheckTime = 0;
const unsigned long BATTERY_INTERVAL = 1000; // Check and push metrics every 1000s

void setup() {
    Serial.begin(115200);
    setCpuFrequencyMhz(80); // Drops the clock speed from 160MHz to 80MHz

    // Initialize individual sub-components independently
    loadConfig();
    initRelays();
    initBattery();
    initBle();

    // setDeviceName("Vehicle001");
    // setCalibration(1.253, 0.81);
    //                   name, startEn, stopEn, battEn, powerEn, ackEn
    // burnVehicleConfig("Bike", 1, 1, 1, 0, 1);

    Serial.println(">>> Modular Scooter Controller Initialized successfully. Firmware V4.2 (Supports Command ACK)");
}

void loop() {
    // 1. Maintain background relay timers asynchronously
    updateRelayPulses();

    // 2. Maintain continuous fail-safe stop logic & anti-stall debounce
    updateStopRelayFailSafe();

    // 3. Scheduled Night Sleep Check (Evaluates if time synced & conditions clear)
    checkScheduledNightSleep();

    // 4. Scheduled Engine Start/Kill Task Check
    checkScheduledEngineTask();

    // 5. Monitor and manage BLE advertising intervals (Fast vs Eco Mode)
    updateBleAdvertisingState();

    // 6. Timed Battery Evaluation & App Notification Loop
    if (millis() - lastBatteryCheckTime >= BATTERY_INTERVAL) {
        lastBatteryCheckTime = millis();

        // ONLY consume processor juice and transmit if the phone requested it!
        if (isTelemetryEnabled()) {
            updateBatteryFilter();

            if (isBleClientConnected()) {
                uint16_t currentMilliVolts = getBatteryMilliVolts();
                transmitBatteryTelemetry(currentMilliVolts);
            }
        }
    }
}