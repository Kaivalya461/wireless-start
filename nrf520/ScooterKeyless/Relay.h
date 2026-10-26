#ifndef RELAY_H
#define RELAY_H

#include <Arduino.h>

// Define pins using Seeed XIAO nRF52840 silkscreen labels (D0 - D10)
#define START_RELAY_PIN D2  // Connect to Start relay module
#define STOP_RELAY_PIN  D3  // Connect to Stop relay module

// Accessor flags used by state checks
bool getIsPulseActive();

void initRelays();
void updateRelayPulses();
void updateStopRelayFailSafe();
void syncTime(unsigned long epochTime);
bool requestRelayPulse(int pin, unsigned long durationMs);
unsigned long getValidatedDuration(unsigned long customMs);
void setStopFailSafeActive(bool active);
void checkScheduledEngineTask();
unsigned long getScheduledTaskEpoch();
void setScheduledTaskEpoch(unsigned long epochTime);

#endif