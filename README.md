# AirPods Companion (Android)

An experimental Android app that detects Apple AirPods using Bluetooth LE
and provides basic connection awareness and media control.

> Built and tested on Android 12+ devices.

## Features
- Bluetooth LE scanning for Apple devices
- Foreground service for persistent detection
- Auto play / pause on connect & disconnect
- Material 3 UI (Jetpack Compose)

## Requirements
- Android 12 or higher
- Bluetooth LE enabled
- Nearby Devices permission

## Build Instructions
```bash
./gradlew assembleDebug
