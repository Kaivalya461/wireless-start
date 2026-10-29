#include "BleManager.h"
#include <bluefruit.h>
#include "Relay.h"
#include "ConfigManager.h" // Include your persistent storage manager
#include <time.h>

// UUIDs mapped for Adafruit Bluefruit format
#define SERVICE_UUID        "4fafc201-1fb5-459e-8fcc-c5c9c331914b"
#define CHARACTERISTIC_UUID "beb5483e-36e1-4688-b7f5-ea07361b26a8"

const unsigned long DEFAULT_START_PULSE_MS = 1700;
const unsigned long DEFAULT_STOP_PULSE_MS = 1700;

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

// Corrected Adafruit write callback signature
void characteristic_write_callback(uint16_t conn_hdl, BLECharacteristic* chr, uint8_t* data, uint16_t length) {
    handleIncomingCommand(data, length);
}

void transmitConfigToApp() {
    if (!bleConnected) return;

    // Format properties neatly as text/JSON payload so app can parse easily:
    // Format: CONFIG:Name,StartEn(0/1),StopEn(0/1),BattEn(0/1)
    String configPayload = "CONFIG:" + String(currentConfig.deviceName) + "," +
                           String(currentConfig.isStartEngineEnabled ? 1 : 0) + "," +
                           String(currentConfig.isStopEngineEnabled ? 1 : 0) + "," +
                           String(currentConfig.liveBatteryVoltageEnabled ? 1 : 0);

    customCharacteristic.notify((const uint8_t*)configPayload.c_str(), configPayload.length());

    if (Serial) {
        Serial.print("Transmitted Config to App: ");
        Serial.println(configPayload);
    }
}

void handleIncomingCommand(const uint8_t* data, size_t length) {
    if (length == 0) return;

    if (length == 1) {
        uint8_t commandByte = data[0];
        if (commandByte == 0x02) {
            if (Serial) Serial.println(">>> App Command: Battery Telemetry Stream DISABLED.");
            return;
        } else if (commandByte == 0x03) {
            if (Serial) Serial.println(">>> App Command: Battery Telemetry Stream ENABLED.");
            return;
        }
    }

    String command = "";
    for (size_t i = 0; i < length; i++) {
        command += (char)data[i];
    }
    command.trim();

    if (command.length() == 0) return;

    if (Serial) {
        Serial.print("Received Command: ");
        Serial.println(command);
    }

    if (command.startsWith("TIME:")) {
        unsigned long epoch = command.substring(5).toInt();
        setenv("TZ", "IST-5:30", 1);
        tzset();
        syncTime(epoch);
    }
    else if (command.equals("GET_CONFIG")) {
        // App requests to fetch all persistent properties
        transmitConfigToApp();
    }
    else if (command.startsWith("START")) {
        if (!currentConfig.isStartEngineEnabled) {
            if (Serial) Serial.println(">>> Ignored: Start Engine is disabled via config.");
            return;
        }
        unsigned long duration = DEFAULT_START_PULSE_MS;
        if (command.startsWith("START:")) {
            duration = getValidatedDuration(command.substring(6).toInt());
        }
        requestRelayPulse(START_RELAY_PIN, duration);
    }
    else if (command.startsWith("STOP")) {
        if (!currentConfig.isStopEngineEnabled) {
            if (Serial) Serial.println(">>> Ignored: Stop Engine is disabled via config.");
            return;
        }
        unsigned long duration = DEFAULT_STOP_PULSE_MS;
        if (command.startsWith("STOP:")) {
            duration = getValidatedDuration(command.substring(5).toInt());
        }
        requestRelayPulse(STOP_RELAY_PIN, duration);
    }
    else if (command.equals("FAILSAFE:OFF")) {
        setStopFailSafeActive(false);
        if (Serial) Serial.println(">>> App Command: Fail-Safe Stop Relay DISABLED.");
    }
    else if (command.equals("FAILSAFE:ON")) {
        setStopFailSafeActive(true);
        if (Serial) Serial.println(">>> App Command: Fail-Safe Stop Relay ENABLED.");
    }
    else if (command.startsWith("SCHED_RUN:")) {
        unsigned long epoch = command.substring(10).toInt();
        setScheduledTaskEpoch(epoch);
    }
    else if (command.equals("GET_ENGINE_SCHEDULE")) {
        transmitEngineScheduleTime(getScheduledTaskEpoch());
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

void transmitEngineScheduleTime(unsigned long epochPayload) {
    if (!bleConnected) return;

    String response = "SCHED_IS:" + String(epochPayload);
    customCharacteristic.notify((const uint8_t*)response.c_str(), response.length());

    if (Serial) {
        Serial.print("Transmitted active schedule to phone: ");
        Serial.println(response);
    }
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