#include "BleManager.h"
#include <bluefruit.h>
#include "Relay.h"
#include "ConfigManager.h"
#include <time.h>
#include "BatteryManager.h"

// UUIDs mapped for Adafruit Bluefruit format
#define SERVICE_UUID        "4fafc201-1fb5-459e-8fcc-c5c9c331914b"
#define CHARACTERISTIC_UUID "beb5483e-36e1-4688-b7f5-ea07361b26a8"

const unsigned long DEFAULT_UNLOCK_PULSE_MS = 1700;
const unsigned long DEFAULT_LOCK_PULSE_MS   = 1700;

// Define custom BLE Service and Characteristic using Adafruit classes
BLEService scooterService(SERVICE_UUID);
BLECharacteristic customCharacteristic(CHARACTERISTIC_UUID);

bool bleConnected = false;

// Pull device name from persistent flash storage struct instead of hardcoded string
String getStoredDeviceName() {
    return String(currentConfig.deviceName);
}

// Forward declaration
void handleIncomingCommand(const uint8_t* data, size_t length);

// Helper function to send ACK back to the app via notification
void sendAppAck(uint8_t cmdId, uint8_t seqId, uint8_t status) {
    if (!bleConnected || seqId == 0) return;

    uint8_t ackPayload[3] = { cmdId, seqId, status }; // 0x00 = SUCCESS, 0x01 = FAILED
    customCharacteristic.notify(ackPayload, sizeof(ackPayload));

    if (Serial) {
        Serial.print("Sent ACK -> Cmd: 0x"); Serial.print(cmdId, HEX);
        Serial.print(" | Seq: "); Serial.print(seqId);
        Serial.print(" | Status: "); Serial.println(status == 0x00 ? "SUCCESS" : "FAILED");
    }
}

// Corrected Adafruit write callback signature
void characteristic_write_callback(uint16_t conn_hdl, BLECharacteristic* chr, uint8_t* data, uint16_t length) {
    handleIncomingCommand(data, length);
}

void transmitConfigToApp() {
    if (!bleConnected) return;

    // Format properties neatly as text/JSON payload so app can parse easily:
    // Format: CONF:Name,UnlockEn(0/1),LockEn(0/1),BattEn(0/1),PowerEn(0/1),SupportsAck(0/1)
    String configPayload = "CONF:" + String(currentConfig.deviceName) + "," +
                           String(currentConfig.isUnlockEnabled ? 1 : 0) + "," +
                           String(currentConfig.isLockEnabled ? 1 : 0) + "," +
                           String(currentConfig.liveBatteryVoltageEnabled ? 1 : 0) + "," +
                           String(currentConfig.isPowerSwitchEnabled ? 1 : 0) + "," +
                           String(currentConfig.ackNotificationsEnabled ? 1 : 0);

    customCharacteristic.notify((const uint8_t*)configPayload.c_str(), configPayload.length());

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
    if (length >= 2 && data[0] >= 0x01 && data[0] <= 0x15) {
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
            case 0x01: // UNLOCK
                if (!currentConfig.isUnlockEnabled) {
                    executionSuccess = false;
                } else {
                    unsigned long duration = DEFAULT_UNLOCK_PULSE_MS;
                    if (command.startsWith("UNLOCK:")) {
                        duration = getValidatedDuration(command.substring(7).toInt());
                    }
                    requestRelayPulse(UNLOCK_RELAY_PIN, duration);
                }
                break;

            case 0x02: // LOCK
                if (!currentConfig.isLockEnabled) {
                    executionSuccess = false;
                } else {
                    unsigned long duration = DEFAULT_LOCK_PULSE_MS;
                    if (command.startsWith("LOCK:")) {
                        duration = getValidatedDuration(command.substring(5).toInt());
                    }
                    requestRelayPulse(LOCK_RELAY_PIN, duration);
                }
                break;

            case 0x03: // POWER_STATE
                if (command.equals("PWR:ON")) {
                    setRelayState(POWER_RELAY_PIN, true);
                } else if (command.equals("PWR:OFF")) {
                    setRelayState(POWER_RELAY_PIN, false);
                } else {
                    executionSuccess = false;
                }
                break;

            case 0x10: // TIME SYNC
                if (command.startsWith("TIME:")) {
                    unsigned long epoch = command.substring(5).toInt();
                    setenv("TZ", "IST-5:30", 1);
                    tzset();
                    syncTime(epoch);
                } else {
                    executionSuccess = false;
                }
                break;

            case 0x14: // GET CONFIG
                if (command.equals("GET_CONFIG")) {
                    transmitConfigToApp();
                } else {
                    executionSuccess = false;
                }
                break;

            default:
                executionSuccess = false;
                break;
        }

        // Send ACK back for structured commands
        uint8_t statusCode = executionSuccess ? 0x00 : 0x01;
        sendAppAck(cmdId, seqId, statusCode);

    } else {
        // --- LEGACY PLAIN TEXT FALLBACK (Bootstrapping / Initial Handshake) ---
        if (command.equals("GET_CONFIG")) {
            transmitConfigToApp();
            if (Serial) Serial.println(">>> Handshake: Transmitted config via plain text GET_CONFIG request.");
        } else {
            if (Serial) Serial.println(">>> Ignored unknown plain text command.");
        }
    }
}

void connect_callback(uint16_t conn_handle) {
    bleConnected = true;
    if (Serial) Serial.println(">>> App Connected!");
}

void disconnect_callback(uint16_t conn_handle, uint8_t reason) {
    bleConnected = false;
    if (Serial) Serial.println(">>> App Disconnected! Restarting advertising.");
}

void initBle() {
    Bluefruit.begin();
    Bluefruit.setTxPower(4);

    // --- POWER OPTIMIZATION: Disable the automatic connection/status LED ---
    Bluefruit.autoConnLed(false);

    String savedDeviceName = getStoredDeviceName();
    Bluefruit.setName(savedDeviceName.c_str());

    Bluefruit.Periph.setConnectCallback(connect_callback);
    Bluefruit.Periph.setDisconnectCallback(disconnect_callback);

    scooterService.begin();

    customCharacteristic.setProperties(CHR_PROPS_READ | CHR_PROPS_WRITE | CHR_PROPS_WRITE_WO_RESP | CHR_PROPS_NOTIFY);
    customCharacteristic.setPermission(SECMODE_OPEN, SECMODE_OPEN);
    customCharacteristic.setWriteCallback(characteristic_write_callback);
    customCharacteristic.begin();

    Bluefruit.Advertising.addFlags(BLE_GAP_ADV_FLAGS_LE_ONLY_GENERAL_DISC_MODE);
    Bluefruit.Advertising.addTxPower();
    Bluefruit.Advertising.addService(scooterService);
    Bluefruit.ScanResponse.addName();

    Bluefruit.Advertising.restartOnDisconnect(true);
    Bluefruit.Advertising.setInterval(160, 320); // 100ms - 200ms interval
    Bluefruit.Advertising.setFastTimeout(30);
    Bluefruit.Advertising.start(0);

    if (Serial) {
        Serial.print(">>> Ultra-Low Power BLE Initialized: ");
        Serial.println(savedDeviceName);
    }
}

bool isBleClientConnected() {
    return bleConnected;
}

void updateBleAdvertisingState() {
    // Handled automatically via async Bluefruit callbacks
}

void transmitBatteryTelemetry(uint16_t mvPayload) {
    if (!bleConnected) return;

    // Check if battery stream telemetry is permitted by current flash configuration
    if (!currentConfig.liveBatteryVoltageEnabled) return;

    uint8_t payloadBuffer[2];
    payloadBuffer[0] = (mvPayload >> 8) & 0xFF; // High Byte
    payloadBuffer[1] = mvPayload & 0xFF;        // Low Byte

    customCharacteristic.notify(payloadBuffer, 2);
}

void setDeviceName(String newName) {
    newName.toCharArray(currentConfig.deviceName, sizeof(currentConfig.deviceName));
    saveConfig(); // Save permanently to flash

    // --- Instantly update BLE advertising name live ---
    Bluefruit.setName(currentConfig.deviceName);

    // Optional: Restart advertising so nearby phones see the new name immediately
    if (Bluefruit.Advertising.isRunning()) {
        Bluefruit.Advertising.stop();
        Bluefruit.Advertising.start(0);
    }

    if (Serial) {
        Serial.print(">>> Device Name updated live & saved to flash: ");
        Serial.println(currentConfig.deviceName);
    }
}