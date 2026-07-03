# BGMI GFX Tool Pro 🎮

A powerful, root-optional GFX utility designed to unlock maximum performance and high frame rates (90 FPS / 60 FPS) in BGMI/PUBGM. 

## Features 🚀
- **Modern OS Support:** Full support for Android 14, 15, and 16 (HyperOS 3) using advanced `/mnt/pass_through/0/` filesystem bypasses. No more permission denied errors on modern phones!
- **Dynamic Injection:** Safely injects `UserCustom.ini`, `Engine.ini`, and `Active.sav` files into the restricted Android/data directories.
- **Fail-Safe Launcher:** Automatically wraps game launch intents to prevent SecurityExceptions and crashes on custom ROMs.
- **Shizuku Integration:** Optional Shizuku backend for advanced shell execution when standard DocumentsUI access is blocked.
- **Resolution & Quality Control:** Customize HDR, smooth graphics, and unlock extreme frame rates.

## How it Works 🛠️
On older versions of Android, the app uses standard `MediaStore` or `DocumentsUI` content providers to overwrite game configuration files. For Android 14+, Google strictly blocked access to `Android/data`. This tool implements a sophisticated fallback using the `pass_through` directory and local shell commands (via Shizuku/Sui) to ensure your settings are applied flawlessly.

## Getting Started
1. Install the APK.
2. Grant the necessary permissions (Storage / Shizuku).
3. Select your desired FPS and resolution.
4. Hit **Apply** and tap **Launch Game**.
