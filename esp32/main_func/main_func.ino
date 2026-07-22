#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include <BLE2902.h>

#define SERVICE_UUID        "4fafc201-1fb5-459e-8fcc-c5c9c331914b"
#define CHARACTERISTIC_UUID "beb5483e-36e1-4688-b7f5-ea07361b26a8"

#define START_RELAY_PIN    22
#define CDI_STOP_RELAY_PIN 23

// Relay Safety Constraints (in milliseconds)
const unsigned long DEFAULT_PULSE_MS = 1500;
const unsigned long DEFAULT_START_PULSE_MS = 1700;
const unsigned long DEFAULT_STOP_PULSE_MS = 4000;
const unsigned long MIN_SAFE_PULSE_MS = 100;
const unsigned long MAX_SAFE_PULSE_MS = 5000;

// Safety Guard Timers
const unsigned long STARTER_COOLDOWN_MS = 3000; // 3-second mandatory resting delay for starter motor
unsigned long lastStartExecutionTime = 0;
bool isPulseActive = false;

// Relay Tracking State
int activeRelayPin = -1;
unsigned long pulseStartTime = 0;
unsigned long activePulseDuration = 0;

bool deviceConnected = false;

class MyServerCallbacks: public BLEServerCallbacks {
    void onConnect(BLEServer* pServer) override {
      deviceConnected = true;
      Serial.println(">>> Client Connected!");
    }

    void onDisconnect(BLEServer* pServer) override {
      deviceConnected = false;
      Serial.println(">>> Client Disconnected!");
      BLEDevice::startAdvertising();
      Serial.println(">>> Advertising restarted...");
    }
};

unsigned long getValidatedDuration(unsigned long customMs) {
  if (customMs < MIN_SAFE_PULSE_MS || customMs > MAX_SAFE_PULSE_MS) {
    return DEFAULT_PULSE_MS;
  }
  return customMs;
}

// Request execution with hardware guard protection
bool requestRelayPulse(int pin, unsigned long durationMs) {
  unsigned long now = millis();

  // Guard 1: Ignore command if another pulse is currently running
  if (isPulseActive) {
    Serial.println(">>> REJECTED: Hardware pulse already in progress!");
    return false;
  }

  // Guard 2: Enforce mandatory rest period for the starter motor
  if (pin == START_RELAY_PIN && (now - lastStartExecutionTime < STARTER_COOLDOWN_MS)) {
    Serial.print(">>> REJECTED: Starter motor cooling down! Wait ");
    Serial.print((STARTER_COOLDOWN_MS - (now - lastStartExecutionTime)) / 1000.0);
    Serial.println("s.");
    return false;
  }

  // Activate Relay (Active-LOW)
  activeRelayPin = pin;
  pulseStartTime = now;
  activePulseDuration = durationMs;
  isPulseActive = true;

  digitalWrite(pin, LOW);
  Serial.print("-> Relay PIN ");
  Serial.print(pin);
  Serial.print(" ON for ");
  Serial.print(durationMs);
  Serial.println(" ms");

  if (pin == START_RELAY_PIN) {
    lastStartExecutionTime = now + durationMs;
  }

  return true;
}

class MyCallbacks: public BLECharacteristicCallbacks {
    void onWrite(BLECharacteristic *pCharacteristic) override {
      String value = pCharacteristic->getValue().c_str();
      value.trim();

      if (value.length() > 0) {
        Serial.print("Received Command: ");
        Serial.println(value);

        if (value.startsWith("START")) {
          unsigned long duration = DEFAULT_START_PULSE_MS;
          if (value.startsWith("START:")) {
            duration = getValidatedDuration(value.substring(6).toInt());
          }
          requestRelayPulse(START_RELAY_PIN, duration);
        }
        else if (value.startsWith("STOP")) {
          unsigned long duration = DEFAULT_STOP_PULSE_MS;
          if (value.startsWith("STOP:")) {
            duration = getValidatedDuration(value.substring(5).toInt());
          }
          requestRelayPulse(CDI_STOP_RELAY_PIN, duration);
        }
      }
    }
};

void setup() {
  Serial.begin(115200);

  pinMode(START_RELAY_PIN, OUTPUT);
  pinMode(CDI_STOP_RELAY_PIN, OUTPUT);
  digitalWrite(START_RELAY_PIN, HIGH);
  digitalWrite(CDI_STOP_RELAY_PIN, HIGH);

  BLEDevice::init("Dio Hardware Target");
  BLEServer *pServer = BLEDevice::createServer();
  pServer->setCallbacks(new MyServerCallbacks());

  BLEService *pService = pServer->createService(SERVICE_UUID);
  BLECharacteristic *pCharacteristic = pService->createCharacteristic(
          CHARACTERISTIC_UUID,
          BLECharacteristic::PROPERTY_WRITE | BLECharacteristic::PROPERTY_WRITE_NR
  );

  pCharacteristic->setCallbacks(new MyCallbacks());
  pService->start();

  BLEAdvertising *pAdvertising = BLEDevice::getAdvertising();
  pAdvertising->addServiceUUID(SERVICE_UUID);
  pAdvertising->setScanResponse(true);
  pAdvertising->setMinPreferred(0x06);
  pAdvertising->setMinPreferred(0x12);

  BLEDevice::startAdvertising();
  Serial.println(">>> ESP32 BLE Target Ready.");
}

void loop() {
  // Non-blocking Relay Pulse Manager
  if (isPulseActive) {
    if (millis() - pulseStartTime >= activePulseDuration) {
      digitalWrite(activeRelayPin, HIGH); // Shut relay OFF
      Serial.println("-> Pulse complete. Relay Pin restored HIGH.");

      isPulseActive = false;
      activeRelayPin = -1;
    }
  }
}