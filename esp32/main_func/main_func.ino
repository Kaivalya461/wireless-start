#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include <BLE2902.h>
#include "esp_sleep.h"
#include <sys/time.h>

#define SERVICE_UUID        "4fafc201-1fb5-459e-8fcc-c5c9c331914b"
#define CHARACTERISTIC_UUID "beb5483e-36e1-4688-b7f5-ea07361b26a8"

// Relay Pins
#define START_RELAY_PIN    22
#define CDI_STOP_RELAY_PIN 23

// Night Sleep Window (1:30 AM to 8:30 AM)
#define SLEEP_START_HOUR   1
#define SLEEP_START_MIN    30
#define WAKE_END_HOUR      8
#define WAKE_END_MIN       30

// Convert sleep windows to total minutes from midnight for easy math
const int SLEEP_START_MINUTES_TOTAL = (SLEEP_START_HOUR * 60) + SLEEP_START_MIN; // 90 mins (1:30 AM)
const int WAKE_END_MINUTES_TOTAL    = (WAKE_END_HOUR * 60) + WAKE_END_MIN;    // 510 mins (8:30 AM)

// Relay Safety Constraints (ms)
const unsigned long DEFAULT_PULSE_MS       = 1500;
const unsigned long DEFAULT_START_PULSE_MS = 1700;
const unsigned long DEFAULT_STOP_PULSE_MS  = 4000;
const unsigned long MIN_SAFE_PULSE_MS      = 100;
const unsigned long MAX_SAFE_PULSE_MS      = 5000;

// Hardware Guards
const unsigned long STARTER_COOLDOWN_MS    = 3000;
unsigned long lastStartExecutionTime       = 0;
bool isPulseActive                         = false;

int activeRelayPin                         = -1;
unsigned long pulseStartTime               = 0;
unsigned long activePulseDuration          = 0;

bool deviceConnected = false;
bool timeIsSynced    = false;

// Time Sync Function (Expects current Unix epoch timestamp from phone)
void syncTime(unsigned long epochTime) {
  struct timeval tv;
  tv.tv_sec = epochTime;
  tv.tv_usec = 0;
  settimeofday(&tv, NULL);
  timeIsSynced = true;
  Serial.print(">>> System Time Synced: ");
  Serial.println(epochTime);
}

// Deep Sleep Trigger
void enterDeepSleep(uint64_t sleepDurationSeconds) {
  Serial.println(">>> Entering Scheduled Night Deep Sleep...");

  // Ensure Relays remain turned OFF (HIGH)
  digitalWrite(START_RELAY_PIN, HIGH);
  digitalWrite(CDI_STOP_RELAY_PIN, HIGH);

  // Lock pin states during deep sleep so relays don't switch on boot glitches
  gpio_hold_en((gpio_num_t)START_RELAY_PIN);
  gpio_hold_en((gpio_num_t)CDI_STOP_RELAY_PIN);
  gpio_deep_sleep_hold_en();

  // Set Sleep Timer
  esp_sleep_enable_timer_wakeup(sleepDurationSeconds * 1000000ULL);

  Serial.flush();
  esp_deep_sleep_start();
}

// Check if current time falls within 1:30 AM - 8:30 AM window
void checkScheduledNightSleep() {
  if (!timeIsSynced || deviceConnected || isPulseActive) return;

  time_t now;
  struct tm timeinfo;
  time(&now);
  localtime_r(&now, &timeinfo);

  int currentTotalMinutes = (timeinfo.tm_hour * 60) + timeinfo.tm_min;

  // Check if current minute falls inside the range [1:30 AM, 8:30 AM)
  if (currentTotalMinutes >= SLEEP_START_MINUTES_TOTAL && currentTotalMinutes < WAKE_END_MINUTES_TOTAL) {

    // Calculate total seconds remaining until target wake time (8:30 AM)
    int minutesToWait = WAKE_END_MINUTES_TOTAL - currentTotalMinutes - 1;
    int secondsToWait = 59 - timeinfo.tm_sec;

    uint64_t totalSleepSeconds = (minutesToWait * 60) + secondsToWait;

    Serial.print(">>> Night Window Deep Sleep Active. Sleeping for ");
    Serial.print(totalSleepSeconds / 3600.0);
    Serial.println(" hours until 8:30 AM.");

    enterDeepSleep(totalSleepSeconds);
  }
}

class MyServerCallbacks: public BLEServerCallbacks {
    void onConnect(BLEServer* pServer) override {
      deviceConnected = true;
      Serial.println(">>> App Connected!");
    }

    void onDisconnect(BLEServer* pServer) override {
      deviceConnected = false;
      Serial.println(">>> App Disconnected!");
      pServer->getAdvertising()->start();
    }
};

unsigned long getValidatedDuration(unsigned long customMs) {
  if (customMs < MIN_SAFE_PULSE_MS || customMs > MAX_SAFE_PULSE_MS) {
    return DEFAULT_PULSE_MS;
  }
  return customMs;
}

bool requestRelayPulse(int pin, unsigned long durationMs) {
  unsigned long now = millis();

  // Guard 1: Ignore command if another pulse is currently running
  if (isPulseActive) {
    Serial.println(">>> REJECTED: Hardware pulse already in progress!");
    return false;
  }

  // Millis Rollover Safe Guard Calculation
  if (pin == START_RELAY_PIN && (now - lastStartExecutionTime < STARTER_COOLDOWN_MS)) {
    Serial.print(">>> REJECTED: Starter motor cooling down! Wait ");
    Serial.print((STARTER_COOLDOWN_MS - (now - lastStartExecutionTime)) / 1000.0);
    Serial.println("s.");
    return false;
  }

  activeRelayPin       = pin;
  pulseStartTime       = now;
  activePulseDuration  = durationMs;
  isPulseActive        = true;

  digitalWrite(pin, LOW); // Active-LOW Trigger
  Serial.print("-> Relay PIN ");
  Serial.print(pin);
  Serial.print(" ON for ");
  Serial.print(durationMs);
  Serial.println(" ms");

  if (pin == START_RELAY_PIN) {
    lastStartExecutionTime = now;
  }
  return true;
}

class MyCallbacks: public BLECharacteristicCallbacks {
    void onWrite(BLECharacteristic *pCharacteristic) override {
      String command = pCharacteristic->getValue().c_str();
      command.trim();

      if (command.length() == 0) return;

      Serial.print("Received Command: ");
      Serial.println(command);

      // 1. Time Sync Command (Format: "TIME:1774278544")
      if (command.startsWith("TIME:")) {
        unsigned long epoch = command.substring(5).toInt();

        // Set ESP32 System Timezone to IST (UTC+5:30)
        // POSIX format: "IST-5:30" tells POSIX that IST is +5h 30m ahead of UTC
        setenv("TZ", "IST-5:30", 1);
        tzset();

        // Perform the time sync logic (e.g. settimeofday / internal RTC update)
        syncTime(epoch);

        // Format and print Local (IST) time
        time_t now = epoch;
        struct tm timeinfo;
        localtime_r(&now, &timeinfo);

        char strftime_buf[64];
        strftime(strftime_buf, sizeof(strftime_buf), "%Y-%m-%d %H:%M:%S (%A)", &timeinfo);

        Serial.println("==========================================");
//        Serial.printf("Received Epoch: %lu\n", epoch);
        Serial.printf("Configured Time (IST): %s\n", strftime_buf);
//        Serial.printf("Parsed Hour: %d | Minute: %d\n", timeinfo.tm_hour, timeinfo.tm_min);
        Serial.println("==========================================");
      }
        // 2. Start Scooter Command (Format: "START" or "START:2000")
      else if (command.startsWith("START")) {
        unsigned long duration = DEFAULT_START_PULSE_MS;
        if (command.startsWith("START:")) {
          duration = getValidatedDuration(command.substring(6).toInt());
        }
        requestRelayPulse(START_RELAY_PIN, duration);
      }
        // 3. Stop Scooter Command (Format: "STOP" or "STOP:3000")
      else if (command.startsWith("STOP")) {
        unsigned long duration = DEFAULT_STOP_PULSE_MS;
        if (command.startsWith("STOP:")) {
          duration = getValidatedDuration(command.substring(5).toInt());
        }
        requestRelayPulse(CDI_STOP_RELAY_PIN, duration);
      }
    }
};

void setup() {
  Serial.begin(115200);

// 1. Set pin directions FIRST
  pinMode(START_RELAY_PIN, OUTPUT);
  pinMode(CDI_STOP_RELAY_PIN, OUTPUT);

  // 2. Immediately force output HIGH (Relays OFF)
  digitalWrite(START_RELAY_PIN, HIGH);
  digitalWrite(CDI_STOP_RELAY_PIN, HIGH);

  // 3. Enable internal pull-ups as an extra safeguard
  gpio_set_pull_mode((gpio_num_t)START_RELAY_PIN, GPIO_PULLUP_ONLY);
  gpio_set_pull_mode((gpio_num_t)CDI_STOP_RELAY_PIN, GPIO_PULLUP_ONLY);

  // 4. Release deep sleep locks ONLY AFTER explicit pin drive is active
  gpio_hold_dis((gpio_num_t)START_RELAY_PIN);
  gpio_hold_dis((gpio_num_t)CDI_STOP_RELAY_PIN);
  gpio_deep_sleep_hold_dis();

  BLEDevice::init("Scooter Keyless Target");
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

  // Moderate BLE Advertising Power Saving Configuration (1.0s to 2.0s interval)
  pAdvertising->setMinPreferred(0x06);
  pAdvertising->setMinInterval(0x0640); // 1.0 second advertising interval
  pAdvertising->setMaxInterval(0x0C80); // 2.0 second advertising interval

  BLEDevice::startAdvertising();
  Serial.println(">>> ESP32 Scooter Keyless Target Ready.");
}

void loop() {
  // 1. Non-blocking Relay Pulse Manager (Millis Rollover Safe)
  if (isPulseActive) {
    if (millis() - pulseStartTime >= activePulseDuration) {
      digitalWrite(activeRelayPin, HIGH); // Shut relay OFF
      Serial.println("-> Pulse complete. Relay Pin restored HIGH.");

      isPulseActive = false;
      activeRelayPin = -1;
    }
  }

  // 2. Scheduled Night Sleep Check (1:30 AM to 8:30 AM)
  checkScheduledNightSleep();
}