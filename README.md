# BikeNav

BikeNav is a smart motorcycle navigation assistant designed to provide a safer and less distracting navigation experience for riders.

The system consists of an Android mobile application and an ESP32-based embedded display unit. The Android application handles route generation, GPS tracking, rerouting logic, and navigation processing using Google Maps services, while the ESP32 receives navigation instructions over Bluetooth Low Energy (BLE) and displays them on an OLED screen in a rider-friendly format.

BikeNav is designed as a lightweight, modular navigation HUD for motorcycles and serves as the foundation for future upgrades such as TFT graphical interfaces, audio prompts, haptic alerts, and wearable integration.





https://github.com/user-attachments/assets/31c9ed6d-7a9c-4308-89aa-063dcfa40730



---

# Table of Contents

- Overview
- Features
- System Architecture
- Hardware Requirements
- Software Requirements
- Project Structure
- Installation and Setup
  - Android Application Setup
  - ESP32 Firmware Setup
  - Google Cloud API Configuration
- Usage Guide
- Communication Protocol
- OLED Display Interface
- Navigation Workflow
- Configuration Parameters
- Troubleshooting
- Future Roadmap
- License
- Author

---

# Overview

Traditional smartphone-based motorcycle navigation can be distracting and potentially unsafe because riders often need to shift visual attention toward a phone mounted on the handlebar.

BikeNav addresses this problem by introducing a dedicated navigation display unit that shows only essential navigation information in a minimal format optimized for quick visual comprehension.

The Android application acts as the navigation engine and performs:

- destination search
- route generation
- live GPS tracking
- step-by-step maneuver calculation
- rerouting logic
- BLE data transmission

The ESP32 embedded unit acts as the display controller and performs:

- BLE packet reception
- instruction parsing
- OLED rendering
- UI updates
- connection management

---

# Features

## Android Application

### Navigation
- Real-time route generation using Google Directions API
- Turn-by-turn navigation
- Live GPS tracking
- Automatic rerouting when off-route
- Arrival detection

### Maps Integration
- Google Maps integration
- Destination search
- Google Places Autocomplete support
- Route visualization
- Current location tracking

### BLE Communication
- Automatic connection to ESP32
- BLE data packet transmission
- MTU optimization for larger payloads
- Reconnection support

### User Interface
- Interactive map interface
- Start/stop navigation control
- Live OLED preview simulation
- GPS status indicators
- BLE connection status

---

## ESP32 Embedded Unit

### Communication
- Bluetooth Low Energy (BLE) server
- Navigation packet receiver
- Connection monitoring
- Automatic advertising after disconnect

### Display System
- OLED HUD rendering
- Large maneuver direction display
- Large distance display
- Smooth horizontal scrolling ticker for detailed instructions
- Welcome screen

### UI Design
Optimized for motorcycle riding:
- minimal distraction
- large glanceable information
- smooth readable scrolling
- simplified navigation HUD

---

# System Architecture

```text
+-------------------------------------------------------------+
|                        Android Smartphone                   |
|-------------------------------------------------------------|
|                                                             |
|  Google Maps SDK                                            |
|  Google Directions API                                      |
|  Google Places API                                          |
|                                                             |
|  Navigation Engine                                          |
|   - GPS Tracking                                            |
|   - Route Processing                                        |
|   - Maneuver Detection                                      |
|   - Rerouting Logic                                         |
|                                                             |
|  BLE Client                                                 |
|                                                             |
+---------------------------|---------------------------------+
                            |
                            | Bluetooth Low Energy
                            |
+---------------------------v---------------------------------+
|                          ESP32 Unit                         |
|-------------------------------------------------------------|
|                                                             |
|  BLE Server                                                 |
|                                                             |
|  Packet Parser                                              |
|                                                             |
|  OLED UI Renderer                                           |
|   - Direction                                                |
|   - Distance                                                 |
|   - Scrolling Instruction Ticker                             |
|                                                             |
+---------------------------|---------------------------------+
                            |
                            |
+---------------------------v---------------------------------+
|                      OLED Display (128x64)                 |
+-------------------------------------------------------------+
```

---

# Hardware Requirements

Required components:

- ESP32 DevKit V1
- SSD1306 OLED Display (128x64, I2C)
- Push Button (optional)
- USB cable for ESP32 programming
- Android smartphone
- Power bank or regulated power source

Optional future hardware:

- Round TFT display
- vibration motor
- speaker module
- battery management module
- helmet intercom module

---

# Software Requirements

## Android Side

- Android Studio
- Kotlin
- Android SDK
- Google Play Services
- Google Maps SDK
- Google Places SDK

Minimum Android version:
- Android 8.0+

---

## Embedded Side

- Visual Studio Code
- PlatformIO
- ESP32 platform package
- Arduino framework

Required libraries:

- NimBLE-Arduino
- Adafruit SSD1306
- Adafruit GFX
- Wire

---

# Project Structure

```text
BikeNav/
│
├── AndroidApp/
│   ├── app/
│   ├── gradle/
│   └── Android project files
│
├── ESP32_Firmware/
│   ├── src/
│   │   └── main.cpp
│   ├── platformio.ini
│   └── library dependencies
│
└── README.md
```

---

# Installation and Setup

# Android Application Setup

## 1. Clone repository

```bash
git clone https://github.com/YOUR_USERNAME/BikeNav.git
```

---

## 2. Open Android project

Open:

```text
BikeNav/AndroidApp
```

in Android Studio.

---

## 3. Sync Gradle

Allow Android Studio to sync all dependencies.

---

# Google Cloud API Configuration

Create a Google Cloud project and enable:

- Maps SDK for Android
- Directions API
- Places API

Create an API key.

Add your API key in:

```text
AndroidManifest.xml
```

Example:

```xml
<meta-data
    android:name="com.google.android.geo.API_KEY"
    android:value="YOUR_API_KEY"/>
```

---

# BLE MAC Address Configuration

Update the ESP32 BLE MAC address inside:

```text
BleService.kt
```

Find:

```kotlin
private val ESP32_MAC = "XX:XX:XX:XX:XX:XX"
```

Replace with your actual ESP32 MAC address.

---

# ESP32 Firmware Setup

## 1. Open firmware project

Open:

```text
BikeNav/ESP32_Firmware
```

in Visual Studio Code with PlatformIO.

---

## 2. Install libraries

Ensure:

- NimBLE-Arduino
- Adafruit SSD1306
- Adafruit GFX

are installed.

---

## 3. Wire OLED

Example wiring:

| OLED | ESP32 |
|------|-------|
| VCC  | 3.3V  |
| GND  | GND   |
| SDA  | GPIO 21 |
| SCL  | GPIO 22 |

Adjust pins in code if required.

---

## 4. Build and Upload

```bash
pio run --target upload
```

---

# Usage Guide

## Step 1: Power ESP32

Power the ESP32.

It will:

- initialize BLE
- initialize OLED
- begin advertising

---

## Step 2: Launch Android App

Open BikeNav app.

The app will:

- initialize BLE service
- connect to ESP32
- request larger MTU
- establish communication

---

## Step 3: Verify Connection

Expected:

ESP32 serial monitor:

```text
Phone Connected
```

Android app:

- BLE connected status shown

---

## Step 4: Search Destination

Use the destination search bar.

Google Places autocomplete will suggest destinations.

Select a destination.

---

## Step 5: Start Navigation

Press:

```text
START NAVIGATION
```

The app will:

- fetch route
- process steps
- begin GPS tracking
- send instructions to ESP32

---

## Step 6: Ride

OLED display shows:

Top:
- maneuver direction
- distance to next turn

Bottom:
- scrolling detailed instruction

Example:

```text
STRAIGHT
195 m
--------------------------------
Head north toward Roxy Rd
```

---

## Step 7: Off-route Handling

If rider deviates:

- off-route detection triggers
- cooldown prevents excessive API calls
- new route fetched
- navigation resumes

---

# Communication Protocol

BLE packet format:

```text
DIRECTION|DISTANCE FULL_INSTRUCTION
```

Example:

```text
LEFT|195 m Turn left onto NH16
```

Parsing:

Direction:
```text
LEFT
```

Distance:
```text
195 m
```

Instruction:
```text
Turn left onto NH16
```

---

# OLED Display Interface

Layout:

```text
+----------------------------------+
|                                  |
|            STRAIGHT              |
|                                  |
|             195 m                |
|----------------------------------|
| Head north toward Roxy Rd        |
+----------------------------------+
```

Design principles:

- high readability
- low distraction
- minimal information overload
- fast visual comprehension

---

# Configuration Parameters

Example tunable values:

Navigation thresholds:
- arrival distance threshold
- step reached threshold
- off-route threshold
- reroute cooldown

BLE:
- MTU size
- reconnect delay

Display:
- marquee speed
- font size
- divider position

---

# Troubleshooting

## BLE not connecting

Check:
- correct ESP32 MAC address
- BLE enabled on phone
- ESP32 powered on

---

## OLED blank

Check:
- I2C wiring
- OLED address
- power supply

---

## Google Maps not loading

Check:
- valid API key
- enabled Maps SDK
- billing configuration

---

## Destination search not working

Check:
- Places API enabled
- API restrictions
- internet connection

---

## Frequent rerouting

Adjust:
- off-route threshold
- reroute cooldown

---

# Future Roadmap

Planned upgrades:

- graphical turn arrows
- round TFT display
- audio prompts
- helmet intercom integration
- vibration alerts
- ETA display
- battery monitoring
- speedometer
- offline cached navigation
- day/night themes
- weather awareness
- ride analytics
- wearable integration

---

# License


MIT License

---

# Author

Prabir

Computer Science Engineering (AI & ML)
Embedded Systems Developer
IoT | AI | Robotics | Product Development
