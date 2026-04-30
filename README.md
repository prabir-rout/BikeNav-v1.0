# BikeNav-v1.0

---

# Bike Navigation Assistant

The Bike Navigation Assistant is a compact and smart embedded system designed to provide real-time navigation guidance to riders in a minimal and distraction-free way. It combines an ESP32 microcontroller, an OLED display, and a custom-built Android application to deliver turn-by-turn navigation directly from a smartphone to a handlebar-mounted device.

---

## How It Works

1. The user starts navigation on Google Maps.
2. The Android app listens to navigation notifications in the background.
3. It extracts key information such as:

   * Direction (LEFT, RIGHT, STRAIGHT, U-TURN)
   * Distance (e.g., 200m, 1.2km)
4. The app formats the data into a simple structure:

   ```
   DIRECTION|DISTANCE|
   ```
5. This data is sent to the ESP32 via Bluetooth Low Energy (BLE).
6. The ESP32 processes the data and displays it on the OLED screen.

---

## Display Behavior

### Navigation Active

* Shows a bold directional arrow
* Displays distance to the next turn
* Clean and readable UI for riding

### Navigation Inactive

* Switches to an animated eye display
* Provides a dynamic idle interface

---

## Features

* Real-time navigation updates
* BLE communication (low power and stable)
* Works with offline Google Maps navigation
* Minimal and distraction-free UI
* Idle animation for improved user experience
* Low power consumption suitable for battery operation

---

## Hardware Used

* ESP32 Dev Board
* 0.96" OLED Display (SSD1306, I2C)
* Battery (2000–3000 mAh recommended)

---

## Software and Tools

* Arduino / PlatformIO (ESP32 programming)
* Android Studio (mobile app development)
* Libraries:

  * Adafruit SSD1306
  * Adafruit GFX
  * NimBLE-Arduino

---

## System Architecture

```
Google Maps → Notification → Android App → BLE → ESP32 → OLED Display
```

---

## Data Format

The system uses a simple and robust data format:

```
RIGHT|200m|
LEFT|1.2km|
STRAIGHT|--|
```

---

## Power System

* Recommended battery: 2000–3000 mAh
* Expected runtime: 10–15 hours (OLED setup)

---

## Goal

The goal of this project is to create a lightweight, efficient, and rider-friendly navigation system that avoids the complexity of traditional navigation setups while maintaining usability and reliability.
