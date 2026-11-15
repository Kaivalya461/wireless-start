#include "Relay.h"
#include "BleManager.h"
#include "ConfigManager.h"

DeviceConfig currentConfig;

void setup() {
    // 1. Enable internal DC/DC buck converter for efficiency
    NRF_POWER->DCDCEN = 1;
    NRF_POWER->DCDCEN0 = 1;

    // Turn off onboard user/power LEDs
    pinMode(LED_BUILTIN, OUTPUT);
    digitalWrite(LED_BUILTIN, HIGH);

    // 3. Check if a USB cable is actively plugged in (VBUS detected)
    bool usbConnected = (NRF_POWER->USBREGSTATUS & POWER_USBREGSTATUS_VBUSDETECT_Msk) != 0;

    if (usbConnected) {
        // We are plugged into a computer for debugging / programming
        Serial.begin(115200);

        // Wait up to 3 seconds for the Serial Monitor to open so you don't miss early prints
        unsigned long startWait = millis();
        while (!Serial && (millis() - startWait < 3000));

        Serial.println(">>> USB Connected: Serial Debugging Active.");
    }

    // 2. Load Persistent Configuration from Flash
    loadConfig();

    // NOW burn the config safely after Serial is ready to print messages
    // Make sure the vehicle name is <= 9 characters to stay within the default BLE ATT MTU limit of nrf52.
    // burnVehicleConfig("Bike", 1, 1, 0, 0, 1);

    // 4. Initialize sub-components (Pass currentConfig.deviceName to your BLE init)
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