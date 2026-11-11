#ifndef BATTERY_MANAGER_H
#define BATTERY_MANAGER_H

void initBatteryMonitor();
float readBatteryVoltage();
void setTelemetryActive(bool active);
bool isTelemetryEnabled();
uint16_t getBatteryMilliVolts(); // For optimized 2-byte BLE notifications
//void updateBatteryFilter();
//void setCalibration(float mult, float offset);

void updateBatteryTask();

#endif