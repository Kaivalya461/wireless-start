
# Component List

## 1. Power Input & Protection Rail
* **Fuse:** 1A fuse with in-line holder (rated for at least 12V DC)
* **TVS Diode:** 1.5KE18A (Transient Voltage Suppressor, 18V standoff)
* **Bulk Capacitor:** 100 µF electrolytic capacitor (rated for 25V or higher)

## 2. 3.3V Power Regulation for nRF52
* **Microcontroller:** Seeed Studio XIAO BLE nRF52840
* **LDO Voltage Regulator:** 3.3V LDO regulator capable of handling 12V input (e.g., LP2950-33LPRE3)
* **Decoupling Capacitor:** 1-10 µF electrolytic capacitor (placed close to the LDO's 3.3V output and GND)

## 3. D5 Pin Start Setup (P-MOSFET & NPN Driver)
* **P-Channel MOSFET:** IRF9540 (or equivalent power P-MOSFET rated for 12V+ and required drain current)
* **NPN Transistor:** 2N2222
* **Resistors:**
    * 1 x 10 kΩ resistor (Pull-up for MOSFET Gate to 12V)
    * 1 x 1 kΩ resistor (Base current-limiting resistor for nRF52 GPIO D5)
* **Flyback Diode:** 1N4007 rectifier diode (placed across the starter relay coil with the cathode facing UP/toward 12V)
* **Relay:** Starter relay coil (load)

## 4. D6 Pin Stop Setup (Relay Module & NPN Driver)
* **Relay Module:** 12V Relay Module (single-channel board)
* **NPN Transistor:** 2N2222 for driving the relay input of the 12V Relay Module
* **Resistors (1/4W):**
    * 1 x 1 kΩ resistor (Base current-limiting resistor for nRF52 GPIO D6)
    * 1 x 10 kΩ resistor *(Optional)*: Pull-down resistor between Base and Emitter of the 2N2222

---
# Hardware Connection Diagram:

### Back Surge Protection Circuit:
```
[Batt 12V] ---> (1A Fuse) ---> [1N4007] ------------┬------------┬------> (IN) [LDO] (OUT) ---┬---> [nRF52 3V3]
                                                    │            │               │            │
                                               [1.5KE18A TVS]  (100µF Cap)     (GND)    (1-10µF Cap)
                                             (Verify Polarity)   │               │            │
                                                    │            └---------------┼------------┘
                                                    │                            │
[Batt GND] -----------------------------------------┴----------------------------┴----------------> [nRF52 GND]
```

### Full Integrated Diagram
```
=== 1. POWER INPUT & PROTECTION RAIL ===
[ 12V Battery (+)] ---> [ 1A Fuse ] ---> [ 12V Main Rail ] ──┬── [ 1.5KE18A TVS ] ──> [ Common GND ]
                                                             └── [ 100µF Bulk Cap] ──> [ Common GND ]


=== 2. 3.3V POWER REGULATION for nRF52 ===
[ 12V Main Rail ] ──> [ LDO (IN) ] ──┬── [ LDO (OUT) 3.3V ] ──> [ nRF52 3V3 Rail ]
                                     └── [ LDO (GND)      ] ──> [ Common GND ]
                                   (Note: Place 1-10µF Cap between 3V3 and GND near LDO)


=== 3. D5 pin Start Setup (2 Stage Driver using P-MOSFET LOAD SWITCH & RELAY DRIVER) ===
[ 12V Main Rail ] ───┬──> [ Source of P-MOSFET (IRF9540) ]
                     │          │
                     │          ├── [ Gate ] ──┬── [ 10k Pull-up ] ──> [ 12V Main Rail ]
                     │          │              └── Collector of NPN (2N2222)
                     │          │                                │
                     │          │                                ├── Emitter ──> [ Common GND ]
                     │          │                                └── Base <── [ 1k Resistor ] <── [ nRF52 GPIO D5 ]
                     │          │
                     │          └── [ Drain ] ─────────────────────────────────┬──> [ Starter Relay Coil (+) ]
                     │                                                         │
                     │                                                [ 1N4007 Diode ] (Cathode UP)
                     │                                                         │
[ Common GND ] <─────┴─────────────────────────────────────────────────────────┴──> [ Starter Relay Coil (-) ]


=== 4. D6 Pin Stop Setup (12V Relay Module using 2N2222 Driver) ===
# POWER & CONTROL INPUT (Relay Module Input Side)
[ 12V Main Rail ] ───────────────────────────────> [ RELAY IP Side Power (+) ]
[ Common GND    ] ───────────────────────────────> [ RELAY IP Side GND ]
[ RELAY IP Side IN ] ──────────────────────────────> Collector of 2N2222 NPN Transistor
                                                        └─ (When 2N2222 is cut off, the relay module's IN pin floats high until supplied a Gate pulse)

# 2N2222 NPN DRIVER CIRCUIT
[ nRF52 GPIO D6 ] ─── [ 1k Resistor ] ───────────> Base of 2N2222
                                                   Emitter of 2N2222 ──> [ Common GND ]
                                                   *(Optional: Add a 10k resistor between Base & Emitter to keep transistor firmly OFF during boot)*

# RELAY OUTPUT (CDI Kill Switch Side)
[ Common GND ] ──────────────────────────────────> [ RELAY OUT - COM ]
[ Scooter CDI Kill Wire ] ───────────────────────> [ RELAY OUT - NC (Normally Closed) ]
```