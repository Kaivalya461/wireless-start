#ifndef CONFIG_MANAGER_H
#define CONFIG_MANAGER_H

#include <Arduino.h>
#include <Adafruit_LittleFS.h>
#include <InternalFileSystem.h>

// Bring LittleFS classes into scope so 'File' and flags are recognized directly
using namespace Adafruit_LittleFS_Namespace;

// Define your persistent configuration structure
struct DeviceConfig {
    char deviceName[32];
    bool isStartEngineEnabled;
    bool isStopEngineEnabled;
    bool liveBatteryVoltageEnabled;
    uint32_t magic_header; // Used to check if flash has been initialized before
};

// Default configuration values
const DeviceConfig DEFAULT_CONFIG = {
    "ScooterKeyless",            // Default BLE Name
    true,                // isStartEngineEnabled
    false,                // isStopEngineEnabled
    true,                // liveBatteryVoltageEnabled
    0xDEADBEEF           // Magic number verification
};

// Global instance declaration compatible with older C++ standards
extern DeviceConfig currentConfig;
inline DeviceConfig currentConfig;

inline void loadConfig() {
    // Initialize the built-in LittleFS flash file system
    InternalFS.begin();

    // Open file using Adafruit's byte mode flags
    File file = InternalFS.open("/config.dat", FILE_O_READ);
    if (file) {
        file.read((uint8_t*)&currentConfig, sizeof(DeviceConfig));
        file.close();

        // Check if magic header matches (verifies flash wasn't blank/corrupted)
        if (currentConfig.magic_header != 0xDEADBEEF) {
            currentConfig = DEFAULT_CONFIG;

            // Save defaults back to flash file using FILE_O_WRITE
            File writeFile = InternalFS.open("/config.dat", FILE_O_WRITE);
            if (writeFile) {
                writeFile.write((uint8_t*)&currentConfig, sizeof(DeviceConfig));
                writeFile.close();
            }
        }
    } else {
        // File doesn't exist yet (first boot): create and save defaults
        currentConfig = DEFAULT_CONFIG;
        File writeFile = InternalFS.open("/config.dat", FILE_O_WRITE);
        if (writeFile) {
            writeFile.write((uint8_t*)&currentConfig, sizeof(DeviceConfig));
            writeFile.close();
        }
    }
}

inline void saveConfig() {
    InternalFS.begin();
    File writeFile = InternalFS.open("/config.dat", FILE_O_WRITE);
    if (writeFile) {
        writeFile.write((uint8_t*)&currentConfig, sizeof(DeviceConfig));
        writeFile.close();
    }
}

inline void burnVehicleConfig(const char* name, bool startEn, bool stopEn, bool battEn) {
    InternalFS.begin();

    // Populate the global config struct
    memset(&currentConfig, 0, sizeof(DeviceConfig));
    strncpy(currentConfig.deviceName, name, sizeof(currentConfig.deviceName) - 1);
    currentConfig.isStartEngineEnabled = startEn;
    currentConfig.isStopEngineEnabled = stopEn;
    currentConfig.liveBatteryVoltageEnabled = battEn;
    currentConfig.magic_header = 0xDEADBEEF; // Stamp magic number

    // Force-write to internal flash file system
    File writeFile = InternalFS.open("/config.dat", FILE_O_WRITE);
    if (writeFile) {
        writeFile.write((uint8_t*)&currentConfig, sizeof(DeviceConfig));
        writeFile.close();
        if (Serial) {
            Serial.println("----------------------------------------");
            Serial.println(">>> VEHICLE CONFIG BURNED SUCCESSFULLY!");
            Serial.print("    Name : "); Serial.println(currentConfig.deviceName);
            Serial.print("    Start: "); Serial.println(currentConfig.isStartEngineEnabled ? "ENABLED" : "DISABLED");
            Serial.print("    Stop : "); Serial.println(currentConfig.isStopEngineEnabled ? "ENABLED" : "DISABLED");
            Serial.print("    Batt : "); Serial.println(currentConfig.liveBatteryVoltageEnabled ? "ENABLED" : "DISABLED");
            Serial.println("----------------------------------------");
        }
    } else {
        if (Serial) {
            Serial.println(">>> ERROR: Failed to burn configuration to flash!");
        }
    }
}

#endif