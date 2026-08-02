#ifndef RELAY_H
#define RELAY_H

#include <Arduino.h>

#if defined(ARDUINO_ESP32C3_DEV) || defined(ARDUINO_ESP32_C3_SUPER_MINI)
#define START_RELAY_PIN 10  // Use GPIO 3 for C3 Super Mini
#else
#define START_RELAY_PIN 22 // Default to GPIO 22 for standard ESP32 Dev Board
#endif

// Accessor flags used by sleep routines and state checks
bool getIsPulseActive();

void initRelays();
void updateRelayPulses();
void syncTime(unsigned long epochTime);
void checkScheduledNightSleep();
bool requestRelayPulse(int pin, unsigned long durationMs);
unsigned long getValidatedDuration(unsigned long customMs);

#endif
