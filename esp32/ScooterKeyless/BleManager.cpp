#include "BleManager.h"
#include <BLEDevice.h>
#include <BLEUtils.h>
#include <BLEServer.h>
#include <BLE2902.h>
#include "Relay.h"   // Links commands directly to relay execution engines
#include "Battery.h" // Links toggle states to the ADC stream engine

#define SERVICE_UUID        "4fafc201-1fb5-459e-8fcc-c5c9c331914b"
#define CHARACTERISTIC_UUID "beb5483e-36e1-4688-b7f5-ea07361b26a8"

const unsigned long DEFAULT_START_PULSE_MS = 1700;

BLECharacteristic *pCharacteristic;
bool bleConnected = false;

// Eco Mode tracking variables encapsulated inside BleManager
static unsigned long disconnectionTime = 0;
static bool isFastAdvertising = true;
const unsigned long ECO_MODE_DELAY_MS = 30UL * 60UL * 1000UL; // 30 minutes
const unsigned long FAST_BLE_MIN_INTERVAL = 400; // 250ms
const unsigned long FAST_BLE_MAX_INTERVAL = 800; // 500ms

class MyServerCallbacks: public BLEServerCallbacks {
    void onConnect(BLEServer* pServer) override {
        bleConnected = true;
        Serial.println(">>> App Connected! Restoring Fast BLE Advertising parameters.");

        disconnectionTime = millis();
        isFastAdvertising = true;

        // Reset advertising back to fast speeds for immediate response next time
        BLEAdvertising *pAdvertising = BLEDevice::getAdvertising();
        pAdvertising->setMinInterval(FAST_BLE_MIN_INTERVAL);
        pAdvertising->setMaxInterval(FAST_BLE_MAX_INTERVAL);
    }
    void onDisconnect(BLEServer* pServer) override {
        bleConnected = false;
        Serial.println(">>> App Disconnected! Restarting with Fast BLE Advertising...");

        disconnectionTime = millis();
        isFastAdvertising = true;

        // Ensure fast intervals are set before restarting advertising on drop
        BLEAdvertising *pAdvertising = BLEDevice::getAdvertising();
        pAdvertising->setMinInterval(FAST_BLE_MIN_INTERVAL);
        pAdvertising->setMaxInterval(FAST_BLE_MAX_INTERVAL);
        pAdvertising->start();
    }
};

class MyCallbacks: public BLECharacteristicCallbacks {
    void onWrite(BLECharacteristic *pCharacteristic) override {
        // 1. Extract raw data as an Arduino String natively
        String rawValue = pCharacteristic->getValue();
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
        String command = rawValue;
        command.trim();

        if (command.length() == 0) return;

        Serial.print("Received Command: ");
        Serial.println(command);

        // 1. Time Sync Parsing Route
        if (command.startsWith("TIME:")) {
            unsigned long epoch = command.substring(5).toInt();

            setenv("TZ", "IST-5:30", 1); // Set timezone explicitly to Indian Standard Time
            tzset();

            syncTime(epoch);

            time_t now = epoch;
            struct tm timeinfo;
            localtime_r(&now, &timeinfo);

            char strftime_buf[64];
            strftime(strftime_buf, sizeof(strftime_buf), "%Y-%m-%d %H:%M:%S (%A)", &timeinfo);

            Serial.println("==========================================");
            Serial.printf("Configured Time (IST): %s\n", strftime_buf);
            Serial.println("==========================================");
        }
            // 2. Start Action Execution Route
        else if (command.startsWith("START")) {
            unsigned long duration = DEFAULT_START_PULSE_MS;
            if (command.startsWith("START:")) {
                duration = getValidatedDuration(command.substring(6).toInt());
            }
            requestRelayPulse(START_RELAY_PIN, duration);
        }
    }
};

void initBle() {
    BLEDevice::init("Scooter Keyless Target");
    BLEServer *pServer = BLEDevice::createServer();
    pServer->setCallbacks(new MyServerCallbacks());

    BLEService *pService = pServer->createService(SERVICE_UUID);

    pCharacteristic = pService->createCharacteristic(
            CHARACTERISTIC_UUID,
            BLECharacteristic::PROPERTY_WRITE  |
            BLECharacteristic::PROPERTY_WRITE_NR |
            BLECharacteristic::PROPERTY_READ   |
            BLECharacteristic::PROPERTY_NOTIFY
    );

    pCharacteristic->setCallbacks(new MyCallbacks());
    pCharacteristic->addDescriptor(new BLE2902());

    pService->start();

    disconnectionTime = millis();
    isFastAdvertising = true;

    BLEAdvertising *pAdvertising = BLEDevice::getAdvertising();
    pAdvertising->addServiceUUID(SERVICE_UUID);
    pAdvertising->setScanResponse(true);

    pAdvertising->setMinPreferred(0x06);
    pAdvertising->setMinInterval(FAST_BLE_MIN_INTERVAL);
    pAdvertising->setMaxInterval(FAST_BLE_MAX_INTERVAL);

    BLEDevice::startAdvertising();
}

bool isBleClientConnected() {
    return bleConnected;
}

// Background handler to drop into Eco Mode automatically if left disconnected
void updateBleAdvertisingState() {
    if (!bleConnected) {
        if (isFastAdvertising && (millis() - disconnectionTime > ECO_MODE_DELAY_MS)) {
            Serial.println(">>> Switching BLE Advertising to Eco Mode (Slow Interval) to save battery.");
            isFastAdvertising = false;

            BLEDevice::stopAdvertising();
            BLEAdvertising *pAdvertising = BLEDevice::getAdvertising();
            // Eco Mode BLE Advert
            pAdvertising->setMinInterval(3200); // 2000ms (2s)
            pAdvertising->setMaxInterval(4800); // 3000ms (3s)
            BLEDevice::startAdvertising();
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