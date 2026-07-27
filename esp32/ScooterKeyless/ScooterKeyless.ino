#include "Battery.h"
#include "Relay.h"
#include "BleManager.h"

unsigned long lastBatteryCheckTime = 0;
const unsigned long BATTERY_INTERVAL = 200; // Check and push metrics every 200ms

void setup() {
    Serial.begin(115200);

    // Initialize individual sub-components independently
    initRelays();
    initBattery();
    initBle();

    Serial.println(">>> Modular Scooter Controller Initialized successfully.");
}

void loop() {
    // 1. Maintain background relay timers asynchronously
    updateRelayPulses();

    // 2. Scheduled Night Sleep Check (Evaluates if time synced & conditions clear)
    checkScheduledNightSleep();

    // 3. Timed Battery Evaluation & App Notification Loop
    if (millis() - lastBatteryCheckTime >= BATTERY_INTERVAL) {
        lastBatteryCheckTime = millis();

        // Pull voltage, process filter arrays
        updateBatteryFilter();

        // Stream live to the Android application if connected
        if (isBleClientConnected()) {
            uint16_t currentMilliVolts = getBatteryMilliVolts();
            transmitBatteryTelemetry(currentMilliVolts);
        }
    }
}
