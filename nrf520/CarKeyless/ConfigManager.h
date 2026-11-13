#ifndef CONFIG_MANAGER_H
#define CONFIG_MANAGER_H

#include <Arduino.h>
#include <Adafruit_LittleFS.h>
#include <InternalFileSystem.h>

// Bring LittleFS classes into scope so 'File' and flags are recognized directly
using namespace Adafruit_LittleFS_Namespace;

// Define your updated persistent configuration structure
struct DeviceConfig {
    char deviceName[32];
    bool isUnlockEnabled;
    bool isLockEnabled;
    bool liveBatteryVoltageEnabled;
    bool isPowerSwitchEnabled;
    bool ackNotificationsEnabled; // <-- NEW FLAG: Controls whether this unit emits ACK notifications
    uint32_t magic_header;        // Updated magic header to handle struct size migration
};

// Updated Magic Header to invalidate old flash layouts cleanly
const uint32_t CONFIG_MAGIC_HEADER = 0xCAFEB001;

// Default configuration values
const DeviceConfig DEFAULT_CONFIG = {
    "CarKeyless",                 // Default BLE Name
    true,                         // isUnlockEnabled
    true,                         // isLockEnabled
    true,                         // liveBatteryVoltageEnabled
    true,                         // isPowerSwitchEnabled
    true,                         // ackNotificationsEnabled (Default to true for new builds)
    CONFIG_MAGIC_HEADER           // Magic number verification
};

// Global instance declaration compatible with older C++ standards
extern DeviceConfig currentConfig;

// 1. Define saveConfig() FIRST so loadConfig() can see it
inline void saveConfig() {
    InternalFS.begin();
    // Always remove the old file first to guarantee a clean overwrite in LittleFS
    if (InternalFS.exists("/config.dat")) {
        InternalFS.remove("/config.dat");
    }

    File writeFile = InternalFS.open("/config.dat", FILE_O_WRITE);
    if (writeFile) {
        writeFile.write((uint8_t*)&currentConfig, sizeof(DeviceConfig));
        writeFile.close();
    }
}

// 2. Now loadConfig() can safely call saveConfig()
inline void loadConfig() {
    InternalFS.begin();

    File file = InternalFS.open("/config.dat", FILE_O_READ);
    if (file) {
        // Ensure file size matches current struct size to prevent partial reads
        if (file.size() == sizeof(DeviceConfig)) {
            file.read((uint8_t*)&currentConfig, sizeof(DeviceConfig));
        } else {
            // Size mismatch (old struct version) -> force defaults reset
            currentConfig.magic_header = 0;
        }
        file.close();

        // Check if magic header matches the new version
        if (currentConfig.magic_header != CONFIG_MAGIC_HEADER) {
            currentConfig = DEFAULT_CONFIG;
            saveConfig(); // Uses the safe saveConfig() with remove()
        }
    } else {
        // File doesn't exist yet (first boot)
        currentConfig = DEFAULT_CONFIG;
        saveConfig();
    }
}

inline void burnVehicleConfig(const char* name, bool unlockEn, bool lockEn, bool battEn, bool powerEn, bool ackEn) {
    InternalFS.begin();

    // Populate the global config struct
    memset(&currentConfig, 0, sizeof(DeviceConfig));
    strncpy(currentConfig.deviceName, name, sizeof(currentConfig.deviceName) - 1);
    currentConfig.isUnlockEnabled = unlockEn;
    currentConfig.isLockEnabled = lockEn;
    currentConfig.liveBatteryVoltageEnabled = battEn;
    currentConfig.isPowerSwitchEnabled = powerEn;
    currentConfig.ackNotificationsEnabled = ackEn;
    currentConfig.magic_header = CONFIG_MAGIC_HEADER;

    saveConfig(); // Uses the safe saveConfig() with remove()

    if (Serial) {
        Serial.println("----------------------------------------");
        Serial.println(">>> VEHICLE CONFIG BURNED SUCCESSFULLY!");
        Serial.print("    Name   : "); Serial.println(currentConfig.deviceName);
        Serial.print("    Unlock : "); Serial.println(currentConfig.isUnlockEnabled ? "ENABLED" : "DISABLED");
        Serial.print("    Lock   : "); Serial.println(currentConfig.isLockEnabled ? "ENABLED" : "DISABLED");
        Serial.print("    Batt   : "); Serial.println(currentConfig.liveBatteryVoltageEnabled ? "ENABLED" : "DISABLED");
        Serial.print("    Power  : "); Serial.println(currentConfig.isPowerSwitchEnabled ? "ENABLED" : "DISABLED");
        Serial.print("    ACK Notif: "); Serial.println(currentConfig.ackNotificationsEnabled ? "ENABLED" : "DISABLED");
        Serial.println("----------------------------------------");
    }
}

#endif