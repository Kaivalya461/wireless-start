#include "Relay.h"
#include "BleManager.h"

void setup() {
    // Initialize Serial (TinyUSB handles this cleanly without blocking if USB is disconnected)
    Serial.begin(115200);

    // Initialize individual sub-components independently
    initRelays();
    initBle();

    if (Serial) {
        Serial.println(">>> Modular Scooter Controller Initialized (Fully Optimized)!");
    }
}

void loop() {
    // 1. Maintain background relay timers asynchronously
    updateRelayPulses();

    // 2. Maintain continuous fail-safe stop logic & anti-stall debounce
    updateStopRelayFailSafe();

    // 3. Scheduled Engine Start/Kill Task Check
    checkScheduledEngineTask();

    // 4. Monitor and manage BLE advertising state
    updateBleAdvertisingState();

    // 5. POWER OPTIMIZATION: Put CPU to sleep until next interrupt, timer, or BLE event.
    // This drops idle/advertising power consumption down to microamps (µA).
    __WFI();
}