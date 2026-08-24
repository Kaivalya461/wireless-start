#include "BleManager.h"
#include <NimBLEDevice.h>
#include <Preferences.h>
#include "Relay.h"
#include "Battery.h"

#define SERVICE_UUID        "4fafc201-1fb5-459e-8fcc-c5c9c331914b"
#define CHARACTERISTIC_UUID "beb5483e-36e1-4688-b7f5-ea07361b26a8"

const unsigned long DEFAULT_START_PULSE_MS = 1700;
const unsigned long DEFAULT_STOP_PULSE_MS = 1700;

Preferences blePreferences;
NimBLECharacteristic *pCharacteristic;
bool bleConnected = false;

// Eco Mode tracking variables encapsulated inside BleManager
static unsigned long disconnectionTime = 0;
static bool isFastAdvertising = true;
const unsigned long ECO_MODE_DELAY_MS = 70UL * 60UL * 1000UL; // 70 minutes
const unsigned long FAST_BLE_MIN_INTERVAL = 400; // 250ms
const unsigned long FAST_BLE_MAX_INTERVAL = 800; // 500ms

class MyServerCallbacks: public NimBLEServerCallbacks {
    void onConnect(NimBLEServer* pServer, NimBLEConnInfo& connInfo) override {
        bleConnected = true;
        Serial.println(">>> App Connected! Restoring Fast BLE Advertising parameters.");

        disconnectionTime = millis();
        isFastAdvertising = true;

        // Reset advertising back to fast speeds for immediate response next time
        NimBLEAdvertising *pAdvertising = NimBLEDevice::getAdvertising();
        pAdvertising->setMinInterval(FAST_BLE_MIN_INTERVAL);
        pAdvertising->setMaxInterval(FAST_BLE_MAX_INTERVAL);
    }

    void onDisconnect(NimBLEServer* pServer, NimBLEConnInfo& connInfo, int reason) override {
        bleConnected = false;
        Serial.println(">>> App Disconnected! Restarting with Fast BLE Advertising...");

        disconnectionTime = millis();
        isFastAdvertising = true;

        // Ensure fast intervals are set before restarting advertising on drop
        NimBLEAdvertising *pAdvertising = NimBLEDevice::getAdvertising();
        pAdvertising->setMinInterval(FAST_BLE_MIN_INTERVAL);
        pAdvertising->setMaxInterval(FAST_BLE_MAX_INTERVAL);
        pAdvertising->start();
    }
};

class MyCallbacks: public NimBLECharacteristicCallbacks {
    void onWrite(NimBLECharacteristic *pCharacteristic, NimBLEConnInfo& connInfo) override {
        // 1. Extract raw data as an Arduino String natively
        std::string rawValue = pCharacteristic->getValue();
        int valueLength = rawValue.length();
        if (valueLength == 0) return;

        // 2. Intercept single-byte configuration toggles (0x02 or 0x03)
        if (valueLength == 1) {
            uint8_t commandByte = (uint8_t)rawValue[0]; // Access index 0 of the Arduino String

            if (commandByte == 0x02) {
                setTelemetryActive(false);
                Serial.println(">>> App Command: Battery Telemetry Stream DISABLED (Power Saving Active).");
                return; // Terminate execution block early
            }
            else if (commandByte == 0x03) {
                setTelemetryActive(true);
                Serial.println(">>> App Command: Battery Telemetry Stream ENABLED.");
                return; // Terminate execution block early
            }
        }

        // 3. Fallback Route: Handle incoming plain-text action messages
        String command = String(rawValue.c_str());
        command.trim();

        if (command.length() == 0) return;

        Serial.print("Received Command: ");
        Serial.println(command);

        // 1. Time Sync Parsing Route
        if (command.startsWith("TIME:")) {
            unsigned long epoch = command.substring(5).toInt();
            setenv("TZ", "IST-5:30", 1);
            tzset();
            syncTime(epoch);
        }
            // 2. Start Action Execution Route
        else if (command.startsWith("START")) {
            unsigned long duration = DEFAULT_START_PULSE_MS;
            if (command.startsWith("START:")) {
                duration = getValidatedDuration(command.substring(6).toInt());
            }
            requestRelayPulse(START_RELAY_PIN, duration);
        }
            // 3. Stop Action Execution Route
        else if (command.startsWith("STOP")) {
            unsigned long duration = DEFAULT_STOP_PULSE_MS;
            if (command.startsWith("STOP:")) {
                duration = getValidatedDuration(command.substring(6).toInt());
            }
            requestRelayPulse(STOP_RELAY_PIN, duration);
        }
            // 4. STOP Relay Fail-Safe toggle
        else if (command.equals("FAILSAFE:OFF")) {
            setStopFailSafeActive(false);
            Serial.println(">>> App Command: Fail-Safe Stop Relay DISABLED by App.");
            return; // Terminate execution block early
        }
        else if (command.equals("FAILSAFE:ON")) {
            setStopFailSafeActive(true);
            Serial.println(">>> App Command: Fail-Safe Stop Relay ENABLED by App.");
            return; // Terminate execution block early
        }
    }
};

void initBle() {
    blePreferences.begin("ble_cfg", false);
    String savedDeviceName = blePreferences.getString("device_name", "Scooter Keyless Target");
    blePreferences.end();

    NimBLEDevice::init(savedDeviceName.c_str());
    NimBLEDevice::setPower(9);

    NimBLEServer *pServer = NimBLEDevice::createServer();
    pServer->setCallbacks(new MyServerCallbacks());

    NimBLEService *pService = pServer->createService(SERVICE_UUID);

    pCharacteristic = pService->createCharacteristic(
            CHARACTERISTIC_UUID,
            NIMBLE_PROPERTY::WRITE  |
            NIMBLE_PROPERTY::WRITE_NR |
            NIMBLE_PROPERTY::READ   |
            NIMBLE_PROPERTY::NOTIFY
    );

    pCharacteristic->setCallbacks(new MyCallbacks());
    pCharacteristic->createDescriptor("2902", NIMBLE_PROPERTY::READ | NIMBLE_PROPERTY::WRITE);

    pService->start();

    disconnectionTime = millis();
    isFastAdvertising = true;

    NimBLEAdvertising *pAdvertising = NimBLEDevice::getAdvertising();
    pAdvertising->setName(savedDeviceName.c_str());
    pAdvertising->addServiceUUID(SERVICE_UUID);
    pAdvertising->enableScanResponse(true);
    pAdvertising->setPreferredParams(0x06, 0x12);

    pAdvertising->setMinInterval(FAST_BLE_MIN_INTERVAL);
    pAdvertising->setMaxInterval(FAST_BLE_MAX_INTERVAL);

    // Explicitly start advertising via the advertising object pointer
    pAdvertising->start();
    Serial.println(">>> NimBLE Initialized & Advertising Started.");
}

bool isBleClientConnected() {
    return bleConnected;
}

unsigned long getDisconnectionTime() {
    return disconnectionTime;
}

void updateBleAdvertisingState() {
    if (!bleConnected) {
        if (isFastAdvertising && (millis() - disconnectionTime > ECO_MODE_DELAY_MS)) {
            Serial.println(">>> Switching BLE Advertising to Eco Mode (Slow Interval) to save battery.");
            isFastAdvertising = false;

            NimBLEDevice::stopAdvertising();
            NimBLEAdvertising *pAdvertising = NimBLEDevice::getAdvertising();
            // Eco Mode BLE Advert
            pAdvertising->setMinInterval(3200); // 2s
            pAdvertising->setMaxInterval(4800); // 3s
            NimBLEDevice::startAdvertising();
        }
    }
}

void transmitBatteryTelemetry(uint16_t mvPayload) {
    if (!bleConnected) return;

    // Packs 16-bit payload down into exactly two raw bytes
    uint8_t payloadBuffer[2];
    payloadBuffer[0] = (mvPayload >> 8) & 0xFF; // High Byte
    payloadBuffer[1] = mvPayload & 0xFF;        // Low Byte

    pCharacteristic->setValue(payloadBuffer, 2);
    pCharacteristic->notify();
}

void setDeviceName(String newName) {
    blePreferences.begin("ble_cfg", false);
    blePreferences.putString("device_name", newName);
    blePreferences.end();

    Serial.printf(">>> New Device Name Saved -> %s\n", newName.c_str());
    Serial.println(">>> Please restart your ESP32 for the new BLE name to take effect.");
}