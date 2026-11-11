#ifndef RELAY_H
#define RELAY_H

#include <Arduino.h>

// Define pins using Seeed XIAO nRF52840 silkscreen labels (D0 - D10)
#define UNLOCK_RELAY_PIN D5 // Connect to Unlock relay module
#define LOCK_RELAY_PIN   D6 // Connect to Lock relay module
#define POWER_RELAY_PIN  D7 // Connect to Power relay module

// Accessor flags used by state checks
bool getIsPulseActive();

void initRelays();
void updateRelayPulses();
void syncTime(unsigned long epochTime);
bool requestRelayPulse(int pin, unsigned long durationMs);
void setRelayState(int pin, bool state);
unsigned long getValidatedDuration(unsigned long customMs);

#endif