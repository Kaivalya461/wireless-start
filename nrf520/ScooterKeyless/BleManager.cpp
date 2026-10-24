#include "BleManager.h"
#include <ArduinoBLE.h>
#include "Relay.h"
#include <time.h>

// UUIDs mapped to ArduinoBLE format
#define SERVICE_UUID        "4fafc201-1fb5-459e-8fcc-c5c9c331914b"
#define CHARACTERISTIC_UUID "beb5483e-36e1-4688-b7f5-ea07361b26a8"

const unsigned long DEFAULT_START_PULSE_MS = 1700;
const unsigned long DEFAULT_STOP_PULSE_MS = 1700;

// Define custom BLE Service and Characteristic
BLEService scooterService(SERVICE_UUID);
BLECharacteristic customCharacteristic(CHARACTERISTIC_UUID, BLERead | BLEWrite | BLEWriteWithoutResponse | BLENotify, 32);

bool bleConnected = false;

// Default Device Name handler
String getStoredDeviceName() {
    return "Honda Dio nrf520";
}

void handleIncomingCommand(const uint8_t* data, size_t length) {
    if (length == 0) return;

    // 1. Intercept single-byte configuration toggles (0x02 or 0x03)
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

    // 2. Fallback Route: Handle incoming plain-text action messages
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
    else if (command.startsWith("START")) {
        unsigned long duration = DEFAULT_START_PULSE_MS;
        if (command.startsWith("START:")) {
            duration = getValidatedDuration(command.substring(6).toInt());
        }
        requestRelayPulse(START_RELAY_PIN, duration);
    }
    else if (command.startsWith("STOP")) {
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

void initBle() {
    if (!BLE.begin()) {
        if (Serial) Serial.println("Starting Bluetooth® Low Energy failed!");
        while (1);
    }

    String savedDeviceName = getStoredDeviceName();
    BLE.setLocalName(savedDeviceName.c_str());
    BLE.setDeviceName(savedDeviceName.c_str());

    BLE.setAdvertisedService(scooterService);
    scooterService.addCharacteristic(customCharacteristic);
    BLE.addService(scooterService);

    BLE.advertise();

    if (Serial) {
        Serial.print(">>> ArduinoBLE Initialized & Advertising as: ");
        Serial.println(savedDeviceName);
    }
}

bool isBleClientConnected() {
    BLEDevice central = BLE.central();
    bool connected = central && central.connected();

    if (connected != bleConnected) {
        bleConnected = connected;
        if (bleConnected) {
            if (Serial) Serial.println(">>> App Connected!");
        } else {
            if (Serial) Serial.println(">>> App Disconnected! Restarting advertising.");
            BLE.advertise();
        }
    }
    return bleConnected;
}

void updateBleAdvertisingState() {
    isBleClientConnected(); // Refresh connection status check

    if (bleConnected) {
        if (customCharacteristic.written()) {
            handleIncomingCommand(customCharacteristic.value(), customCharacteristic.valueLength());
        }
    }
}

void transmitBatteryTelemetry(uint16_t mvPayload) {
    if (!bleConnected) return;

    uint8_t payloadBuffer[2];
    payloadBuffer[0] = (mvPayload >> 8) & 0xFF; // High Byte
    payloadBuffer[1] = mvPayload & 0xFF;        // Low Byte

    customCharacteristic.writeValue(payloadBuffer, 2);
}

void transmitEngineScheduleTime(unsigned long epochPayload) {
    if (!bleConnected) return;

    String response = "SCHED_IS:" + String(epochPayload);
    customCharacteristic.writeValue((const uint8_t*)response.c_str(), response.length());

    if (Serial) {
        Serial.print("Transmitted active schedule to phone: ");
        Serial.println(response);
    }
}

void setDeviceName(String newName) {
    if (Serial) {
        Serial.print(">>> New Device Name Requested (RAM-only active): ");
        Serial.println(newName);
    }
}