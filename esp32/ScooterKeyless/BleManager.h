#ifndef BLE_MANAGER_H
#define BLE_MANAGER_H

#include <Arduino.h>

void initBle();
void transmitBatteryTelemetry(uint16_t mvPayload);
void transmitEngineScheduleTime(unsigned long epochPayload);
bool isBleClientConnected();
void updateBleAdvertisingState();
unsigned long getDisconnectionTime();
void setDeviceName(String newName);

#endif
