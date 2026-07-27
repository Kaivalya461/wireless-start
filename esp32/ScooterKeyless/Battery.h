#ifndef BATTERY_H
#define BATTERY_H

#include <Arduino.h>

void initBattery();
void updateBatteryFilter();
float getBatteryVoltage();
uint16_t getBatteryMilliVolts(); // For optimized 2-byte BLE notifications

#endif
