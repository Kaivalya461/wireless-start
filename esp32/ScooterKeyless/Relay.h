#ifndef RELAY_H
#define RELAY_H

#include <Arduino.h>

#define START_RELAY_PIN 22

// Accessor flags used by sleep routines and state checks
bool getIsPulseActive();

void initRelays();
void updateRelayPulses();
void syncTime(unsigned long epochTime);
void checkScheduledNightSleep();
bool requestRelayPulse(int pin, unsigned long durationMs);
unsigned long getValidatedDuration(unsigned long customMs);

#endif
