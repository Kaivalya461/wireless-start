#include "Relay.h"
#include "BleManager.h"
#include "ConfigManager.h"
#include "BatteryManager.h"

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
    // burnVehicleConfig("Car", 1, 1, 1, 1);

    // 4. Initialize sub-components
    initRelays();
    initBle();
    initBatteryMonitor();

    if (usbConnected) {
        Serial.print("Loaded Device Name: "); Serial.println(currentConfig.deviceName);
        Serial.println(">>> Modular Keyless Controller Initialized (Low Power Mode Ready)!");
    }
}

void loop() {
    // 1. Maintain background relay timers asynchronously
    updateRelayPulses();

    // 2. Monitor and manage BLE advertising state
    updateBleAdvertisingState();

    // 3. Update battery monitoring tasks
    updateBatteryTask();

    // 4. POWER OPTIMIZATION: Put the CPU to sleep until the next
    // millisecond timer tick, relay event, or Bluetooth interrupt.
    // This stops the CPU from busy-waiting and drops current dramatically.
    // Bench Test observations: With BLE Advert mode (from average 5.XmA to 0.5mA current draw)
     __WFI();
}