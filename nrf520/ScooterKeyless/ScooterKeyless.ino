#include "Relay.h"
#include "BleManager.h"

void setup() {
    // 1. Enable internal DC/DC buck converter for efficiency
    NRF_POWER->DCDCEN = 1;
    NRF_POWER->DCDCEN0 = 1;

    // Turn off onboard user/power LEDs
    pinMode(LED_BUILTIN, OUTPUT);
    digitalWrite(LED_BUILTIN, HIGH);

    // 2. Check if a USB cable is actively plugged in (VBUS detected)
    bool usbConnected = (NRF_POWER->USBREGSTATUS & POWER_USBREGSTATUS_VBUSDETECT_Msk) != 0;

    if (usbConnected) {
        // We are plugged into a computer for debugging / programming
        Serial.begin(115200);
        delay(1000); // Give serial monitor time to open
        Serial.println(">>> USB Connected: Serial Debugging Active.");
    } else {
        // Running purely on battery: completely bypass Serial overhead
        // (Do not call Serial.begin at all to save power)
    }

    // 3. Initialize sub-components
    initRelays();
    initBle();

    if (usbConnected) {
        Serial.println(">>> Modular Scooter Controller Initialized (Low Power Mode Ready)!");
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
    // (Note: Adafruit Bluefruit handles this via async callbacks,
    // so updateBleAdvertisingState() can be empty or omitted)
    updateBleAdvertisingState();

    // 5. POWER OPTIMIZATION: Put the CPU to sleep until the next
    // millisecond timer tick, relay event, or Bluetooth interrupt.
    // This stops the CPU from busy-waiting and drops current dramatically.
    // Bench Test observations: With BLE Advert mode (from average 5.XmA to 0.5mA current draw)
    __WFI();
}