// blemanager.cpp
#include "BleManager.h"
#include <NimBLEDevice.h>
#include "ConfigManager.h"
#include "Relay.h"
#include "Battery.h"
#include <time.h>

#define SERVICE_UUID        "4fafc201-1fb5-459e-8fcc-c5c9c331914b"
#define CHARACTERISTIC_UUID "beb5483e-36e1-4688-b7f5-ea07361b26a8"

const unsigned long DEFAULT_START_PULSE_MS = 1700;
const unsigned long DEFAULT_STOP_PULSE_MS = 1700;

NimBLECharacteristic *pCharacteristic;
bool bleConnected = false;

// Eco Mode tracking variables encapsulated inside BleManager
static unsigned long disconnectionTime = 0;
static bool isFastAdvertising = true;
const unsigned long ECO_MODE_DELAY_MS = 70UL * 60UL * 1000UL; // 70 minutes
const unsigned long FAST_BLE_MIN_INTERVAL = 200; // 125ms
const unsigned long FAST_BLE_MAX_INTERVAL = 400; // 250ms

// Forward declarations
void handleIncomingCommand(const uint8_t* data, size_t length);
void sendAppAck(uint8_t cmdId, uint8_t seqId, uint8_t status);
void transmitConfigToApp();

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
        // 1. Extract raw data as an Arduino std::string natively
        std::string rawValue = pCharacteristic->getValue();
        int valueLength = rawValue.length();
        if (valueLength == 0) return;

        // 2. Pass raw byte array and length to the structured command handler
        const uint8_t* data = (const uint8_t*)rawValue.data();
        handleIncomingCommand(data, valueLength);
    }
};

// Helper function to send ACK back to the app via notification
void sendAppAck(uint8_t cmdId, uint8_t seqId, uint8_t status) {
    if (!bleConnected || seqId == 0) return;

    // Respect config toggle for ACK notifications if needed, or always send if requested by app seqId
    uint8_t ackPayload[3] = { cmdId, seqId, status }; // 0x00 = SUCCESS, 0x81 = FAILED
    pCharacteristic->setValue(ackPayload, sizeof(ackPayload));
    pCharacteristic->notify();

    if (Serial) {
        Serial.print("Sent ACK -> Cmd: 0x"); Serial.print(cmdId, HEX);
        Serial.print(" | Seq: "); Serial.print(seqId);
        Serial.print(" | Status: "); Serial.println(status == 0x00 ? "SUCCESS" : "FAILED");
    }
}

void transmitConfigToApp() {
    if (!bleConnected) return;

    // Format properties cleanly using currentConfig values from ConfigManager
    // Format: CONF:Name,StartEn(0/1),StopEn(0/1),BattEn(0/1),PowerEn(0/1),SupportsAck(0/1)
    String configPayload = "CONF:" + String(currentConfig.deviceName) + "," +
                           (currentConfig.isStartEnabled ? 1 : 0) + "," +
                           (currentConfig.isStopEnabled ? 1 : 0) + "," +
                           (currentConfig.liveBatteryVoltageEnabled ? 1 : 0) + "," +
                           (currentConfig.isPowerSwitchEnabled ? 1 : 0) + "," +
                           (currentConfig.ackNotificationsEnabled ? 1 : 0);

    pCharacteristic->setValue(configPayload.c_str());
    pCharacteristic->notify();

    if (Serial) {
        Serial.print("Transmitted Config to App: ");
        Serial.println(configPayload);
    }
}

void handleIncomingCommand(const uint8_t* data, size_t length) {
    if (length == 0) return;

    uint8_t cmdId = 0;
    uint8_t seqId = 0;
    bool isStructuredAckPacket = false;

    // Check if this is a modern ACK-tracked packet format: [cmdId, seqId, ...payload]
    if (length >= 2 && data[0] >= 0x01 && data[0] <= 0x20) {
        cmdId = data[0];
        seqId = data[1];
        isStructuredAckPacket = true;
    }

    // Extract command string based on packet type
    String command = "";
    size_t startIndex = isStructuredAckPacket ? 2 : 0;
    for (size_t i = startIndex; i < length; i++) {
        command += (char)data[i];
    }
    command.trim();

    if (Serial) {
        Serial.print("Received Command: ");
        if (isStructuredAckPacket) {
            Serial.print("[Structured CmdID: 0x"); Serial.print(cmdId, HEX); Serial.print("] ");
        } else {
            Serial.print("[Legacy Plain Text] ");
        }
        Serial.print(command);
        if (isStructuredAckPacket) {
            Serial.print(" [SeqId: "); Serial.print(seqId); Serial.print("]");
        }
        Serial.println();
    }

    bool executionSuccess = true;

    if (isStructuredAckPacket) {
        // --- STRUCTURED ACK COMMANDS ---
        switch (cmdId) {
            case 0x01: // START
                if (!currentConfig.isStartEnabled) {
                    executionSuccess = false;
                } else {
                    unsigned long duration = DEFAULT_START_PULSE_MS;
                    if (command.startsWith("START:")) {
                        duration = getValidatedDuration(command.substring(6).toInt());
                    }
                    requestRelayPulse(START_RELAY_PIN, duration);
                }
                break;

            case 0x02: // STOP
                if (!currentConfig.isStopEnabled) {
                    executionSuccess = false;
                } else {
                    unsigned long duration = DEFAULT_STOP_PULSE_MS;
                    if (command.startsWith("STOP:")) {
                        duration = getValidatedDuration(command.substring(5).toInt());
                    }
                    requestRelayPulse(STOP_RELAY_PIN, duration);
                }
                break;

            case 0x03: // POWER_STATE
                // Handle power state command if applicable
                break;

            case 0x10: // TIME_SYNC
                if (command.startsWith("TIME:")) {
                    unsigned long epoch = command.substring(5).toInt();
                    setenv("TZ", "IST-5:30", 1);
                    tzset();
                    syncTime(epoch);
                } else {
                    executionSuccess = false;
                }
                break;

            case 0x11: // FAILSAFE_STATE
                if (command.equals("FAILSAFE:OFF")) {
                    setStopFailSafeActive(false);
                    Serial.println(">>> App Command: Fail-Safe Stop Relay DISABLED.");
                } else if (command.equals("FAILSAFE:ON")) {
                    setStopFailSafeActive(true);
                    Serial.println(">>> App Command: Fail-Safe Stop Relay ENABLED.");
                } else {
                    executionSuccess = false;
                }
                break;

            case 0x12: // SCHEDULED_RUN
                if (length >= 6) { // 2 bytes header + 4 bytes epoch
                    uint32_t epoch = ((uint32_t)data[2] << 24) |
                                     ((uint32_t)data[3] << 16) |
                                     ((uint32_t)data[4] << 8)  |
                                     (uint32_t)data[5];
                    setScheduledTaskEpoch(epoch);
                } else {
                    executionSuccess = false;
                }
                break;

            case 0x13: // GET_ENGINE_SCHEDULE
                if (command.equals("GET_ENG_SCH")) {
                    transmitEngineScheduleTime(getScheduledTaskEpoch());
                } else {
                    executionSuccess = false;
                }
                break;

            case 0x14: // GET_CONFIG
                if (command.equals("GET_CONFIG")) {
                    transmitConfigToApp();
                } else {
                    executionSuccess = false;
                }
                break;

            case 0x16: // GET_PWR_STATE
                // Handle get power state command if applicable
                break;

            case 0x17: // TELEMETRY_STATE
                if (command.equals("TELEMETRY:OFF")) {
                    setTelemetryActive(false);
                    Serial.println(">>> App Command: Battery Telemetry Stream DISABLED.");
                } else if (command.equals("TELEMETRY:ON")) {
                    setTelemetryActive(true);
                    Serial.println(">>> App Command: Battery Telemetry Stream ENABLED.");
                } else {
                    executionSuccess = false;
                }
                break;

            default:
                executionSuccess = false;
                break;
        }

        // Skip generic ACK for commands that transmit their own data payloads
        if (cmdId != 0x13 && cmdId != 0x14) {
            uint8_t statusCode = executionSuccess ? 0x00 : 0x81; // SUCCESS, FAILURE
            sendAppAck(cmdId, seqId, statusCode);
        }

    } else {
        // --- LEGACY PLAIN TEXT FALLBACK (GET_CONFIG Handshake Only) ---
        if (command.equals("GET_CONFIG")) {
            transmitConfigToApp();
            if (Serial) Serial.println(">>> Handshake: Transmitted config via plain text GET_CONFIG request.");
        } else {
            Serial.println(">>> Ignored unknown/unsupported plain text command.");
        }
    }
}

void initBle() {
    // Read device name directly from pre-loaded ConfigManager structure
    String savedDeviceName = String(currentConfig.deviceName);

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
            pAdvertising->setMinInterval(1200); // 750ms
            pAdvertising->setMaxInterval(1600); // 1s
            NimBLEDevice::startAdvertising();
        }
    }
}

void transmitBatteryTelemetry(uint16_t mvPayload) {
    if (!bleConnected) return;

    // Check if live battery stream telemetry is permitted by flash configuration
    if (!currentConfig.liveBatteryVoltageEnabled) return;

    // Packs 16-bit payload down into exactly two raw bytes
    uint8_t payloadBuffer[2];
    payloadBuffer[0] = (mvPayload >> 8) & 0xFF; // High Byte
    payloadBuffer[1] = mvPayload & 0xFF;        // Low Byte

    pCharacteristic->setValue(payloadBuffer, 2);
    pCharacteristic->notify();
}

void transmitEngineScheduleTime(unsigned long epochPayload) {
    if (!bleConnected) return;

    // 1. Format the response string matching your parser protocol
    String response = "SCHED_IS:" + String(epochPayload);

    // 2. Set the characteristic value with the response string
    pCharacteristic->setValue(response.c_str());

    // 3. Notify the connected phone app client
    pCharacteristic->notify();

    Serial.print("Transmitted active schedule to phone: ");
    Serial.println(response);
}