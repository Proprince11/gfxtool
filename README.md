# ⚡ GFX Tool Pro - Game Booster

A native Android GFX Tool for BGMI/PUBG Mobile that uses **Shizuku** to bypass Android 11-16 scoped storage restrictions and directly modify game config files.

## Features

### 🎮 GFX Settings
- **FPS Unlock**: 30 / 40 / 60 / 90 / 120 FPS
- **Graphics Quality**: Smooth, Balanced, HD, HDR, Ultra HD
- **Resolution**: 540p, 720p, 1080p, 1440p
- **Shadow**: Off, Low, Medium, High
- **Anti-Aliasing**: Toggle MSAA
- **iPad View**: Wide FOV
- **Vulkan Rendering**: Better GPU performance
- **Performance Preset**: One-tap 60FPS + 720p + Smooth (your optimal config)

### 🚀 Game Booster
- Kill background apps
- Clear RAM
- Drop filesystem caches
- Optimize for gaming

### 🔐 How It Works (Shizuku)
- Shizuku provides ADB-level shell access without root
- This lets us write to `/Android/data/com.pubg.imobile/` which Android 16 blocks
- One-time setup: Install Shizuku → Connect via ADB → Grant permission
- After that, the app works normally every time

## Build Instructions

### Prerequisites
- Android Studio (Arctic Fox or newer)
- JDK 11+
- Android SDK 34

### Steps
1. Open this folder in Android Studio
2. Let Gradle sync
3. Connect your Android device (USB debugging enabled)
4. Run the app (Shift+F10)

### Build APK
```bash
./gradlew assembleRelease
```
APK will be at: `app/build/outputs/apk/release/app-release.apk`

## Setup on Phone

1. Install the APK
2. Install [Shizuku](https://play.google.com/store/apps/details?id=moe.shizuku.privileged.api) from Play Store
3. Open Shizuku → Start via Wireless ADB (or USB ADB)
4. Open GFX Tool Pro → Tap "Connect" → Grant permission
5. Select your settings → Apply → Launch game

## Your Optimal Config
For your device (currently running Balanced/Ultra at 40fps with drops):
- **FPS**: 60
- **Graphics**: Smooth
- **Resolution**: 720p
- **Shadow**: Off
- **Anti-Aliasing**: Off

This will give you stable 60fps with no frame drops.

## Safety
- ✅ Built in-house, no third-party code
- ✅ Only modifies UserCustom.ini (game config file)
- ✅ Does NOT modify game APK or memory
- ✅ No root required
- ✅ Shizuku is open-source and trusted
