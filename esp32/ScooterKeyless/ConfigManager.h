// ConfigManager.h (ESP32 Version)
#ifndef CONFIG_MANAGER_H
#define CONFIG_MANAGER_H

#include <Arduino.h>
#include <Preferences.h>

// Define your persistent configuration structure (matches nRF52 layout)
struct DeviceConfig {
    char deviceName[32];
    bool isStartEnabled;
    bool isStopEnabled;
    bool liveBatteryVoltageEnabled;
    bool isPowerSwitchEnabled;
    bool ackNotificationsEnabled;
    uint32_t magic_header;
};

// Magic Header to invalidate old flash layouts cleanly
const uint32_t CONFIG_MAGIC_HEADER = 0xCAFEB001;

// Default configuration values
const DeviceConfig DEFAULT_CONFIG = {
        "Bike",   // Default BLE Name
        true,                       // isStartEnabled
        true,                       // isStopEnabled
        true,                       // liveBatteryVoltageEnabled
        false,                      // isPowerSwitchEnabled
        true,                       // ackNotificationsEnabled
        CONFIG_MAGIC_HEADER         // Magic number verification
};

// Global instance declaration safely defined for header-only inclusion
extern DeviceConfig currentConfig;
inline DeviceConfig currentConfig;

// Save configuration binary blob to ESP32 NVS flash
inline void saveConfig() {
    Preferences prefs; // Local instance avoids multiple definition linker errors
    prefs.begin("ble_cfg", false);
    prefs.putBytes("cfg_blob", &currentConfig, sizeof(DeviceConfig));
    prefs.end();
}

// Load configuration from ESP32 NVS flash with validation
inline void loadConfig() {
    Preferences prefs; // Local instance avoids multiple definition linker errors
    prefs.begin("ble_cfg", true);
    size_t len = prefs.getBytesLength("cfg_blob");

    if (len == sizeof(DeviceConfig)) {
        prefs.getBytes("cfg_blob", &currentConfig, sizeof(DeviceConfig));
        prefs.end();
    } else {
        prefs.end();
        // Size mismatch or first boot -> load and save defaults
        currentConfig = DEFAULT_CONFIG;
        saveConfig();
    }

    // Verify magic header
    if (currentConfig.magic_header != CONFIG_MAGIC_HEADER) {
        currentConfig = DEFAULT_CONFIG;
        saveConfig();
    }
}

// Function to burn/reset configuration into ESP32 flash
inline void burnVehicleConfig(const char* name, bool startEn, bool stopEn, bool battEn, bool powerEn, bool ackEn) {
    memset(&currentConfig, 0, sizeof(DeviceConfig));
    strncpy(currentConfig.deviceName, name, sizeof(currentConfig.deviceName) - 1);
    currentConfig.isStartEnabled = startEn;
    currentConfig.isStopEnabled = stopEn;
    currentConfig.liveBatteryVoltageEnabled = battEn;
    currentConfig.isPowerSwitchEnabled = powerEn;
    currentConfig.ackNotificationsEnabled = ackEn;
    currentConfig.magic_header = CONFIG_MAGIC_HEADER;

    saveConfig();

    if (Serial) {
        Serial.println("----------------------------------------");
        Serial.println(">>> ESP32 VEHICLE CONFIG BURNED SUCCESSFULLY!");
        Serial.print("    Name      : "); Serial.println(currentConfig.deviceName);
        Serial.print("    Start     : "); Serial.println(currentConfig.isStartEnabled ? "ENABLED" : "DISABLED");
        Serial.print("    Stop      : "); Serial.println(currentConfig.isStopEnabled ? "ENABLED" : "DISABLED");
        Serial.print("    Batt      : "); Serial.println(currentConfig.liveBatteryVoltageEnabled ? "ENABLED" : "DISABLED");
        Serial.print("    Power     : "); Serial.println(currentConfig.isPowerSwitchEnabled ? "ENABLED" : "DISABLED");
        Serial.print("    ACK Notif : "); Serial.println(currentConfig.ackNotificationsEnabled ? "ENABLED" : "DISABLED");
        Serial.println("----------------------------------------");
    }
}

#endif