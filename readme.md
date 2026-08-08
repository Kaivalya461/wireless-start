# Wireless Start
### Application used for connecting with a BLE Module (this hardware module will trigger Self Start Motor)

---
App Features:
1. Wireless Start Button
2. Live Battery Voltage Reading
3. Historic Voltage Readings Chart (last 4hours)
4. System Activity Log
5. NO BLE auto-connect


---
Commands:
1. Compile Entire Project (Android + Wear App)
   ```
   .\gradlew.bat clean assembleDebug
   ```

2. Install apk wirelessly on android device
    ```
   adb -s 192.168.1.135:32933 install app/build/outputs/apk/debug/app-debug.apk
    ```
   
3. ADB Wireless Pair And Connect
    ```
   // Pair your phone/watch (using the phone/watch pairing code popup info)
   adb pair 192.168.1.135:33827
   
   // Connect (Note: different port number)
   adb connect 192.168.1.135:32933
   ```
---
## Component List (BOM)

### 1. Power & Protection
* **12V DC Battery Source** (Scooter Battery)
* **1A Fuse** (Inline protection)
* **Diode 1N4007** (Series reverse-polarity protection)
* **1.5KE18A TVS Diode** (Parallel back-surge / transient voltage suppression)
* **100µF Electrolytic Capacitor** (Parallel filtering/smoothing capacitor)

### 2. Power Conversion
* **MP1584EN Fixed 5V Buck Converter** (Steps down 12V DC to 5V DC)

### 3. Microcontroller & Control
* **ESP32 Device** (Main microcontroller, 3.3V logic)

### 4. Relay Module
* **2-Channel Relay Module (Isolation Mode)**
   * Configured with JD-VCC jumper removed (separate high-side 12V relay coil power and low-side 3.3V logic control)

### 5. Voltage Divider Circuit (Battery Monitor)
* **R1 Resistor:** 47 kΩ (Series)
* **R2 Resistor:** 10 kΩ (Parallel)
* **C1 Capacitor:** 0.1 µF (Parallel filtering)
* **Zener Diode:** 3.3V (Parallel overvoltage protection for ESP32 ADC pin)

---
## Hardware Connection Diagram:

### Back Surge Protection Circuit:
```
[Battery 12V (+)] ---> (1A Fuse) ---> [Diode 1N4007] ------------+------> [Buck In +] ----> [Buck Out +] ---------> [ESP32 5V]
                                                    │            │
                                               [1.5KE18A TVS]  (100µF Cap)
                                             (Verify Polarity)   │
                                                    │            │
[Battery GND (-)] ----------------------------------+------------+------> [Buck In -] ----> [Buck Out -] ---------> [ESP32 GND]
```

### Voltage Divider (for Live Voltage Readings):
```
12V Battery (+) [Up to 15V]
       │
      [R1: 47 kΩ Resistor]
       │
       ├─────────────────┬─────────────────┬────────► Connect to ESP32 GPIO
       │                 │                 │
      [R2: 10 kΩ]      [C1: 0.1µF]     ( Zener 3.3V ) Cathode (Side with stripe)
       │                 │                 │
       │                 │                 │ Anode
       ▼                 ▼                 ▼
12V Battery (-) ──────┴─────────────────┴────────► Connect to ESP32 GND
```

### Full Integrated System Diagram:
```
[ DC 12V Battery ]
   ├── (+) [1A Fuse] ──> [1N4007 Diode (Series)] ──┬──> [1.5KE18A TVS (Parallel)] ──┬──> [100uF Cap (Parallel)] ──┬──> [MP1584EN Buck Converter] ──(+5V)──> [ESP32 (5V/VIN)]
   │                                              │                                 │                             │
   │                                              │                                 │                             └──> [Voltage Divider] 
   │                                              │                                 │                                    ├── R1 (47k) Series ──────┬────> GPIO 23
   │                                              │                                 │                                    ├── R2 (10k) Parallel ────┤
   │                                              │                                 │                                    ├── C1 (0.1uF) Parallel ──┤
   │                                              │                                 │                                    └── Zener 3.3V (Parallel) ┘
   │                                              │                                 │
   │                                              │                                 └──> [Relay JD-VCC] (12V Isolation)
   │                                              │
   │                                              └──> [Relay COM] (12V High-Current Path to Scooter Battery)
   │                                                     └── Relay NO ──> [Scooter Starter Motor Relay]
   │
   └── (-) ───────────────────────────────────────┴───────────────────────────────────────────────────────────────────> [Common System GND] 
                                                                                                                               │
 [ESP32 Control Pins]                                                                                                          │
   ├── 3.3V Output ────────────────────────────────────────────────────────────────────────────────────────────────────────────┼──> [Relay VCC]
   ├── GPIO 22 ────────────────────────────────────────────────────────────────────────────────────────────────────────────────┼──> [Relay IN1]
   └── GND ────────────────────────────────────────────────────────────────────────────────────────────────────────────────────┘
```