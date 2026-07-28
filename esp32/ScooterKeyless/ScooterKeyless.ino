#include "Battery.h"
#include "Relay.h"
#include "BleManager.h"

unsigned long lastBatteryCheckTime = 0;
const unsigned long BATTERY_INTERVAL = 1000; // Check and push metrics every 1000s

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

        // ONLY consume processor juice and transmit if the phone requested it!
        if (isBatteryStreamActive()) {
            updateBatteryFilter();

            if (isBleClientConnected()) {
                uint16_t currentMilliVolts = getBatteryMilliVolts();
                transmitBatteryTelemetry(currentMilliVolts);
            }
        }
    }
}
