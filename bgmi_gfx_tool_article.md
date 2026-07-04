# ⚡ GFX Tool Pro: A Deep-Dive Technical Article

*How a custom-built Android app bypasses Android 11–16's toughest storage restrictions to supercharge your BGMI/PUBG Mobile experience — without root.*

---

## Table of Contents

1. [What Is This App?](#what-is-this-app)
2. [The Problem It Solves](#the-problem-it-solves)
3. [The Big Idea: How It Works (Plain English)](#the-big-idea)
4. [Architecture Overview](#architecture-overview)
5. [File-by-File Breakdown](#file-by-file-breakdown)
6. [The Shizuku Connection: Android's Skeleton Key](#shizuku)
7. [GFX Configuration Engine](#gfx-configuration-engine)
8. [The Shell Execution System](#the-shell-execution-system)
9. [The 6-Method Write Strategy](#the-6-method-write-strategy)
10. [The Game Booster](#the-game-booster)
11. [UI Design & User Experience](#ui-design)
12. [Security & Safety Analysis](#security-and-safety)
13. [Build & Deployment](#build-and-deployment)
14. [Known Limitations & Future Improvements](#limitations)

---

## 1. What Is This App? {#what-is-this-app}

**GFX Tool Pro** is a native Android application (version 2.0.0) built in **Kotlin** that lets players of **BGMI** (Battlegrounds Mobile India) and **PUBG Mobile** directly modify the game's hidden graphics configuration file to unlock performance gains that the game's built-in settings menu doesn't expose.

Think of it like this: your game has a secret settings file hidden deep in your phone's storage. The normal settings menu only shows you a few options, but this file has *dozens* of dials you can turn. GFX Tool Pro reaches in, adjusts those dials, and saves the file — all before you even launch the game.

### What can it control?

| Setting | Options |
|---|---|
| FPS Cap | 30, 40, 60, 90, 120 |
| Graphics Quality | Smooth, Balanced, HD, HDR |
| Render Resolution | 540p (0.5625×), 720p (0.75×), 1080p (1.0×) |
| Shadow Quality | Off, Low, Medium, High |
| Anti-Aliasing (MSAA) | On / Off |
| iPad View (Wide FOV) | On / Off |
| GPU Particles | Disabled (always) |
| Bloom / Motion Blur / Lens Flare | Disabled (always) |
| Texture Memory Pool | Capped at 150 MB (Smooth/Balanced modes) |
| Cloth Physics | Disabled (Smooth/Balanced modes) |

---

## 2. The Problem It Solves {#the-problem-it-solves}

### The Android Storage Wall

Starting with Android 11, Google introduced **Scoped Storage** — a security model that prevents apps from freely reading and writing files belonging to other apps. By Android 13–16, this became even stricter.

The game's config file lives at:
```
/storage/emulated/0/Android/data/com.pubg.imobile/files/
UE4Game/ShadowTrackerExtra/ShadowTrackerExtra/Saved/Config/Android/
UserCustom.ini
```

No normal app — even one with `READ_EXTERNAL_STORAGE` permission — can write to another app's `/Android/data/` folder on Android 11+. The OS flat-out blocks it at the kernel level.

**This is the fundamental challenge this app was built to solve.**

### Why Not Just Root?

Rooting your phone gives you god-level access to everything, but it:
- Voids your warranty
- Triggers **SafetyNet/Play Integrity** (the anti-cheat system) and may get you banned
- Is complex and risky for most users

GFX Tool Pro finds a middle path using **Shizuku** — which grants ADB-level (Android Debug Bridge) shell access without requiring root.

---

## 3. The Big Idea: How It Works (Plain English) {#the-big-idea}

Imagine Android's file system as a **gated apartment building**:
- Each app lives in its own apartment and can only touch its own stuff
- The building security (the OS) stops any app from entering another apartment
- But a **building manager** (Shizuku) has a master key that can open any door

Here's the flow in simple terms:

```
User selects settings in app
         ↓
App builds a config text file (UserCustom.ini)
         ↓
App uses Shizuku's "master key" (ADB shell access)
         ↓
Shell command writes the file directly into BGMI's apartment
         ↓
User launches BGMI — game reads the new settings
```

---

## 4. Architecture Overview {#architecture-overview}

```
bgmi-gfx-tool/
├── app/
│   ├── build.gradle.kts         ← Build configuration & dependencies
│   └── src/main/
│       ├── AndroidManifest.xml  ← App permissions & component declarations
│       ├── aidl/                ← IPC interface definition (AIDL)
│       │   └── IShellService.aidl
│       ├── java/com/bgmigfxtool/pro/
│       │   ├── MainActivity.kt  ← UI controller + orchestration
│       │   ├── GfxConfig.kt     ← INI file generation engine
│       │   ├── Shell.kt         ← Privileged shell executor
│       │   ├── ShellService.kt  ← Shizuku UserService (runs as UID 2000)
│       │   └── AppLog.kt        ← In-memory debug logger
│       └── res/
│           ├── layout/activity_main.xml  ← UI layout
│           └── values/themes.xml         ← Dark amber theme
├── gradle/                      ← Gradle wrapper files
└── README.md
```

### Component Roles at a Glance

| Component | Role | Analogy |
|---|---|---|
| `MainActivity.kt` | The control panel | Dashboard of a car |
| `GfxConfig.kt` | The recipe writer | Chef writing the ingredient list |
| `Shell.kt` | The delivery driver | Courier who actually delivers the package |
| `ShellService.kt` | The privileged worker | Building manager with master key |
| `AppLog.kt` | The black box recorder | Flight recorder for debugging |
| `AndroidManifest.xml` | The ID card | App's official identity document |

---

## 5. File-by-File Breakdown {#file-by-file-breakdown}

### 📄 `AndroidManifest.xml`

This is the app's official declaration to the Android OS. It does three critical things:

1. **Declares one permission**: `KILL_BACKGROUND_PROCESSES` — needed for the Game Booster feature to force-stop other apps.

2. **Declares the Shizuku Provider**: This is how Shizuku knows this app wants to use its services. The `ShizukuProvider` is a special content provider that acts as a handshake point between the app and the Shizuku service.

3. **Declares MainActivity** as the launcher entry point (the screen you see when you open the app).

> [!IMPORTANT]
> The `ShizukuProvider` requires the `INTERACT_ACROSS_USERS_FULL` permission because it needs to talk across process boundaries — this is what allows the elevated shell to work.

---

### 📄 `AppLog.kt` — The Debug Black Box

This is the simplest file but critically important for troubleshooting.

**What it does:**
- Maintains an **in-memory list** of up to 200 log entries
- Each entry has a timestamp (HH:mm:ss format), a tag, and a message
- Entries can be viewed in the app's "Show Debug Log" dialog, copied to clipboard, or saved to a file

**Why it matters:**
When your phone runs privileged shell commands, you can't easily attach a debugger. `AppLog` is the window into what's happening behind the scenes. When something goes wrong (e.g., "Write Failed"), you tap "Show Log" and see the exact sequence of shell commands, their exit codes, and any error messages.

```kotlin
// Example log entries you'd see:
// [22:45:01] SHELL: id result: code=0 out=uid=2000(shell) gid=2000(shell)
// [22:45:01] PM: pm path com.pubg.imobile: code=0 out=package:/data/app/...
// [22:45:02] M1: code=0 out=  err=
// [22:45:02] M1_VERIFY: size check: 1247 /data/data/com.pubg.imobile/...
```

---

### 📄 `GfxConfig.kt` — The INI File Factory

This is the heart of the GFX functionality. It's a Kotlin `object` (a singleton — meaning there's only ever one instance) with two jobs:

1. **Store constants**: Package names for BGMI (`com.pubg.imobile`) and PUBG Mobile (`com.tencent.ig`), and helper functions to compute file paths.

2. **Build the INI file content** from user-selected parameters.

#### How the INI file works

BGMI is built on **Unreal Engine 4 (UE4)**. UE4 games support a configuration system where INI files can override engine console variables (CVars). The `UserCustom.ini` file is the game's mechanism for device-specific overrides.

The `build()` function constructs this file section by section:

**Section 1: `[UserCustom DeviceProfile]`** — Core performance CVars
```ini
[UserCustom DeviceProfile]
+CVars=r.PUBGDeviceFPS.DefaultFrameRateLimit=60
+CVars=r.PUBGQualityLevel=1
+CVars=r.MobileContentScaleFactor=0.75
+CVars=r.ShadowQuality=0
+CVars=r.Shadow.CSM.MaxCascades=0
+CVars=r.Mobile.MSAA=1
+CVars=r.PUBGMaxGPUParticleCount=0
+CVars=r.PUBGEnableGPUParticle=0
+CVars=r.FinishCurrentFrame=0
+CVars=r.OneFrameThreadLag=1
```

**Section 2: `[/Script/Engine.RendererSettings]`** — Eye-candy kill switch
```ini
[/Script/Engine.RendererSettings]
r.DefaultFeature.Bloom=False
r.DefaultFeature.AmbientOcclusion=False
r.DefaultFeature.MotionBlur=False
r.DefaultFeature.LensFlare=False
r.DefaultFeature.AutoExposure=False
```

These visual effects look pretty but cost significant GPU cycles. Disabling them frees up processing power for higher, more stable framerates.

**Section 3 (Smooth/Balanced only): Low-end optimizations**
```ini
[/Script/Engine.StreamingSettings]
s.MinBulkDataSizeForAsyncLoading=0

[UserCustom DeviceProfile]
+CVars=r.PUBGMaxTextureMemory=150
+CVars=r.Streaming.PoolSize=150
+CVars=foliage.LODDistanceScale=0.5
+CVars=grass.DensityScale=0.5
+CVars=p.ClothPhysics=0
```

- **Texture memory cap (150MB)**: Prevents the GPU from hoarding VRAM on lower-end devices
- **Foliage/grass scale (0.5)**: Halves the density of trees and grass — major performance boost in open areas
- **Cloth physics off**: Player clothing won't simulate realistic movement, but saves CPU cycles

---

### 📄 `Shell.kt` — The Privileged Command Runner

This is the most technically sophisticated component. It's a Kotlin singleton that handles executing shell commands with elevated privileges.

#### The Three-Method Fallback Chain

When you call `Shell.exec("some command")`, it tries three methods in order:

```
Method 1: Shizuku.newProcess() via Java Reflection
    ↓ (if fails)
Method 2: UserService IPC call (ShellService running as UID 2000)
    ↓ (if fails)
Method 3: Local Runtime.exec() (no elevation — last resort)
```

**Method 1 — Reflection on `Shizuku.newProcess()`:**
The Shizuku library has a private static method `newProcess()` that spawns a process directly in the Shizuku server's context (shell user, UID 2000). Because it's `private`, the code uses **Java Reflection** to access it anyway:

```kotlin
newProcessMethod = Shizuku::class.java.getDeclaredMethod(
    "newProcess",
    Array<String>::class.java,
    Array<String>::class.java,
    String::class.java
)
newProcessMethod!!.isAccessible = true  // bypass private modifier
val process = newProcessMethod!!.invoke(null, arrayOf("sh", "-c", command), ...)
```

> **Analogy**: It's like finding a locked back door in a building and using a universal key to open it, even though the building manager didn't officially give you that key.

**Method 2 — UserService via AIDL:**
The `ShellService` runs as a Shizuku User Service (a separate process with UID 2000). `Shell.kt` communicates with it via **AIDL** (Android Interface Definition Language) — Android's standard IPC (Inter-Process Communication) mechanism.

**Method 3 — Local fallback:**
Plain `Runtime.getRuntime().exec()` — no elevation, but at least provides diagnostic information.

---

### 📄 `ShellService.kt` — The Privileged Worker Process

This class is launched by Shizuku as a **User Service** — a separate process that runs with shell-level privileges (UID 2000, same as ADB).

It implements the `IShellService.Stub` interface (generated from AIDL) and has one job: run shell commands and return their result.

```kotlin
override fun exec(command: String?): String {
    val proc = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
    val out = proc.inputStream.bufferedReader().readText()
    val err = proc.errorStream.bufferedReader().readText()
    val code = proc.waitFor()
    return "$code|$out|$err"  // packed as a single string
}
```

The result is returned as a `|`-delimited string (`exitCode|stdout|stderr`) which `Shell.kt` then unpacks into a Kotlin `Triple<Int, String, String>`.

**Two constructors exist** to support both old and new Shizuku versions:
- `constructor()` — for Shizuku versions that don't inject a Context
- `constructor(context: Context)` — for Shizuku v13+ which does inject one

---

### 📄 `MainActivity.kt` — The Conductor

At 354 lines, this is the largest file and acts as the orchestrator connecting all other components. Let's walk through its lifecycle:

#### App Startup (`onCreate`)

1. Registers three **Shizuku listeners**:
   - `permListener` — fires when the user grants/denies Shizuku permission
   - `binderListener` — fires when Shizuku service connects (phone restart, app relaunch)
   - `binderDeadListener` — fires when Shizuku service dies

2. If Shizuku permission is already granted (from a previous session), immediately calls `Shell.bind()` to connect the UserService

3. Starts a **connection poller** — a Handler-based loop that checks every 2 seconds until the shell is connected, then stops itself

4. Calls `setupUI()` to wire all the buttons and radio groups

#### Settings Collection (`setupUI`)

Every UI element has a change listener that updates local variables:
```kotlin
var fps = 60          // from rgFps RadioGroup
var quality = 1       // from rgGraphics RadioGroup  
var resolution = "0.75" // from rgRes RadioGroup
var shadow = 0        // from rgShadow RadioGroup
var msaa = 1          // from swMsaa Switch
var ipadView = false  // from swIpad Switch
```

#### Apply Settings (`applyConfig`)

This is the most complex function, running on a **background thread** to avoid freezing the UI:

```
1. Check Shizuku is connected
2. Run `id` command → verify we're running as uid=2000 (shell) or uid=0 (root)
3. Run `pm path <game_package>` → verify game is installed
4. Run `ls /storage/emulated/0/Android/data/<pkg>` → check game data exists
5. Try each of 3 possible config directories, find which exists
6. Generate INI content via GfxConfig.build()
7. Base64-encode the content (avoids shell escaping issues)
8. Write to /data/local/tmp/ as a temp file
9. Try 6 different write methods to move temp file to final destination
10. Verify the file has content (using `wc -c`)
11. Show success or failure dialog
12. Clean up temp file
```

---

## 6. The Shizuku Connection: Android's Skeleton Key {#shizuku}

### What is Shizuku?

[Shizuku](https://shizuku.rikka.app/) is an open-source app that lets other apps use Android system APIs with **ADB-level privileges**. It's not root — it runs at UID 2000 (the `shell` user), which is powerful enough to:

- Write to protected directories
- Force-stop other apps
- Access system settings normally restricted

### How Shizuku is set up (one-time)

```
User installs Shizuku from Play Store
          ↓
User enables Wireless ADB on their phone (no PC needed on Android 11+)
          ↓
User opens Shizuku app → taps "Start" 
          ↓
Shizuku spawns a persistent service with shell privileges
          ↓
User opens GFX Tool Pro → taps "Connect Shizuku" → grants permission
          ↓
From this point forward, GFX Tool Pro has privileged access every session
```

### Permission Flow in Code

```kotlin
// Check if permission is already granted
fun shizukuOk() = Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED

// Request permission (shows system dialog)
Shizuku.requestPermission(REQ_CODE)

// Listen for the answer
val permListener = Shizuku.OnRequestPermissionResultListener { _, result ->
    if (result == PackageManager.PERMISSION_GRANTED) {
        Shell.bind()  // Connect the UserService
    }
}
```

The app also detects if Shizuku isn't even installed:
```kotlin
fun isShizukuInstalled() = 
    packageManager.getPackageInfo("moe.shizuku.privileged.api", 0) != null
```

---

## 7. GFX Configuration Engine — The Config in Detail {#gfx-configuration-engine}

### The UE4 CVar System

Unreal Engine 4 (the engine powering BGMI) has a console variable system where every rendering feature is exposed as a named variable (`CVar`). The `+CVars=` prefix in the INI file tells the engine: "add this override to the current device profile."

Here's what each CVar actually does:

| CVar | Effect | Performance Impact |
|---|---|---|
| `r.PUBGDeviceFPS.DefaultFrameRateLimit` | Sets the FPS cap | High — lower = more stable |
| `r.PUBGQualityLevel` | Master quality preset (1-5) | High |
| `r.MobileContentScaleFactor` | Render resolution multiplier | Very High — this is the #1 performance dial |
| `r.ShadowQuality` | Shadow rendering quality | High — shadows are expensive |
| `r.Shadow.CSM.MaxCascades` | Shadow cascades (distance levels) | Medium |
| `r.Mobile.MSAA` | Multi-Sample Anti-Aliasing (1=off, 4=4x) | Medium |
| `r.PUBGMaxGPUParticleCount` | Max GPU particles (set to 0) | High in particle-heavy areas |
| `r.PUBGEnableGPUParticle` | GPU particle system toggle | High |
| `r.FinishCurrentFrame` | Forces CPU to wait for GPU | Low |
| `r.OneFrameThreadLag` | Allows CPU to run 1 frame ahead | Low |
| `r.PUBGFOVScaling` | FOV scaling system | Low |
| `r.PUBGiPadFOV` | iPad-style wide FOV | Low |

### Resolution Scale Explained

The `r.MobileContentScaleFactor` is a **multiplier**, not a fixed resolution:
- `0.5625` = 540p (56.25% of base resolution)
- `0.75` = 720p (75% of base resolution)  
- `1.0` = 1080p (native resolution)

This means the game renders at a lower resolution internally and then scales up to your screen size. Lower resolution = dramatically less GPU work = higher, more stable FPS.

---

## 8. The Shell Execution System — Deep Dive {#the-shell-execution-system}

### AIDL: The Language of Inter-Process Communication

Android doesn't let two processes talk to each other directly for security reasons. **AIDL** (Android Interface Definition Language) defines a contract that both sides agree to.

The `IShellService.aidl` file (in the `aidl/` directory) defines the interface:
```aidl
interface IShellService {
    String exec(String command);
    void destroy();
}
```

Android's build system (AIDL + Gradle) automatically generates Java/Kotlin code from this file — a stub class (`IShellService.Stub`) that `ShellService` extends, and a proxy that `Shell.kt` uses to call it remotely.

> **Analogy**: AIDL is like a formal contract between a client and a contractor. The contract says exactly what services are available and how to request them, so both parties know the rules.

### The Build System

`app/build.gradle.kts` shows the explicit dependencies:

```kotlin
// Shizuku API library (the client-side SDK)
implementation("dev.rikka.shizuku:api:13.1.5")
// Shizuku provider (the ContentProvider handshake mechanism)
implementation("dev.rikka.shizuku:provider:13.1.5")

// AIDL support must be explicitly enabled
buildFeatures {
    aidl = true
}
```

Target/compile SDK is **34 (Android 14)**, with minimum SDK **24 (Android 7.0)** — so it supports a wide range of devices.

---

## 9. The 6-Method Write Strategy {#the-6-method-write-strategy}

This is perhaps the most innovative part of the entire codebase. The app tries **six different methods** to write the config file, in order, until one succeeds. This is needed because Android 11–16 introduced progressively stricter restrictions and different ROM manufacturers (Samsung, OnePlus, Xiaomi, etc.) handle permissions differently.

### The Setup: Base64 + Temp File

Before trying any method, the app:
1. **Base64-encodes** the INI content — this avoids shell quoting issues with special characters
2. Writes a decoded copy to `/data/local/tmp/gfx_config.ini` — a writable area accessible to the shell user

```kotlin
val encoded = Base64.encodeToString(ini.toByteArray(), Base64.NO_WRAP)
Shell.exec("echo '$encoded' | base64 -d > '/data/local/tmp/gfx_config.ini'")
```

### The 6 Methods

| Method | Command Pattern | Why It Might Work |
|---|---|---|
| **M1** | Write to `/data/data/<pkg>/` internal storage | Shell user can access internal app data on many ROMs |
| **M2** | Write via `content write --uri` (DocumentsUI) | Uses OS's own document provider which has kernel access |
| **M3** | Write to `/data/media/0/Android/data/` (real FS path) | Bypasses the `/storage/emulated/0/` abstraction layer |
| **M4** | Stage in `/sdcard/`, then `mv` to target | Two-step: write to accessible location, then move |
| **M5** | `chmod 777` target dir, write, `chmod 771` restore | Temporarily opens permissions, writes, then closes |
| **M6** | `restorecon -R`, then write | Resets SELinux security context, may allow writes |

After each method that exits with code 0, the app **verifies** the file actually has content using `wc -c` (word count in bytes). A zero-byte file is treated as failure.

> [!NOTE]
> This multi-method approach is a pragmatic engineering response to Android's fragmented security landscape. What works on a stock Pixel might not work on a heavily customized Samsung Galaxy. Rather than targeting one ROM, the app tries all reasonable approaches in sequence.

### The Verification Step

```kotlin
val (vCode, vOut, _) = Shell.exec(
    "head -1 '$path' 2>/dev/null || head -1 /data/data/$gamePkg/files/.../UserCustom.ini 2>/dev/null"
)
// If vOut is non-empty, at least one line was written successfully
if (finalCode == 0 || vOut.isNotEmpty()) {
    // Show success dialog
}
```

The final check reads the first line of the file — if it contains text, the write worked somewhere.

---

## 10. The Game Booster {#the-game-booster}

The "🚀 BOOST" button does two things on a background thread:

### Kill Background Apps
```kotlin
val (_, out, _) = Shell.exec("pm list packages -3 | cut -d: -f2")
```
This lists all third-party packages (the `-3` flag). The app then filters out:
- `com.android.systemui` (the Android UI shell — killing this would crash the phone)
- `moe.shizuku.privileged.api` (Shizuku itself — needed for the app to work)
- `com.bgmigfxtool.pro` (itself — obviously)
- The game package (don't kill the game you're about to play!)

For all remaining apps, it runs:
```kotlin
Shell.exec(pkgs.joinToString(";") { "am force-stop $it" })
```

### Clear System Cache
```kotlin
Shell.exec("pm trim-caches 999G")
```
The `pm trim-caches` command asks the package manager to trim app caches up to the specified size. Using `999G` as the size effectively means "trim everything."

> [!TIP]
> The booster works best when run *after* selecting your settings and *before* launching BGMI. This maximizes available RAM for the game.

---

## 11. UI Design & User Experience {#ui-design}

The app uses a clean **dark gaming aesthetic**:

| Design Element | Value | Effect |
|---|---|---|
| Background color | `#0D0D12` | Near-black with a slight blue-violet tint |
| Primary accent | `#F5A623` | Warm amber/orange — high energy gaming feel |
| Text color | `#DDD` | Soft white — readable without eye strain |
| Theme base | `Material3.Dark.NoActionBar` | Modern Material You foundation |

### Status Indicator System

The `tvStatus` TextView uses emoji-based status indicators with a real-time poller:

```
🔴 Install Shizuku from Play Store    ← Shizuku not installed
🟠 Tap Connect                         ← Installed but not connected
🟢 Shizuku granted — ready            ← Permission granted, direct mode
🟢 Service connected — ready!         ← Full UserService connected
```

This poller checks every 2 seconds until the shell connects, then stops — an efficient approach that avoids wasting resources once connected.

### Layout Structure

The single `activity_main.xml` uses a `ScrollView` wrapping a `LinearLayout`, making the UI work on any screen size. All controls are standard Android widgets — no custom views needed, keeping the code simple and maintainable.

---

## 12. Security & Safety Analysis {#security-and-safety}

### What the app DOES do:
- ✅ Writes to `UserCustom.ini` — the game's official config override file
- ✅ Force-stops third-party background apps
- ✅ Clears system cache via `pm trim-caches`
- ✅ Reads shell identity (`id` command) and verifies game installation

### What the app does NOT do:
- ❌ Does NOT modify the game APK or binary files
- ❌ Does NOT inject code into game memory (no DLL injection, no memory patching)
- ❌ Does NOT intercept network traffic
- ❌ Does NOT require root (no `su` commands)
- ❌ Does NOT contain any ad SDKs, analytics, or tracking code
- ❌ Does NOT phone home (no network requests in the codebase)

### Anti-Detection Risk Assessment

The app only modifies `UserCustom.ini`, which is the game's *intended* mechanism for device customization. Krafton (BGMI's developer) uses this file to apply device-specific graphics profiles. However, using it to unlock FPS beyond the device-default cap (e.g., 120 FPS on a device that defaults to 60 FPS) is technically outside the intended use and *could* be flagged by anti-cheat systems.

**Risk level**: Low-to-medium. Many GFX tools of this type have been in use for years without widespread ban reports, but there's always some risk when modifying game behavior outside official channels.

### Shizuku Trust Model

Shizuku itself is:
- Open source (GitHub: RikkaApps/Shizuku)
- Widely trusted in the Android enthusiast community
- Used by hundreds of legitimate apps (App Manager, Dhizuku, and others)
- Does NOT grant root — only ADB-level shell access (UID 2000)

---

## 13. Build & Deployment {#build-and-deployment}

### Tech Stack Summary

| Layer | Technology |
|---|---|
| Language | Kotlin |
| Min SDK | 24 (Android 7.0) |
| Target/Compile SDK | 34 (Android 14) |
| Build System | Gradle (Kotlin DSL - `.kts`) |
| UI Framework | Android Views (XML layouts) |
| IPC | AIDL |
| Privileged Access | Shizuku 13.1.5 |
| Dependencies | AndroidX Core, AppCompat, Material 3 |

### Build Commands

```bash
# Debug build (for development)
./gradlew assembleDebug

# Release build
./gradlew assembleRelease
# Output: app/build/outputs/apk/release/app-release.apk
```

### Gradle Configuration Notes

- **AIDL is explicitly enabled** in `buildFeatures` — it's disabled by default in modern Android projects
- **`isMinifyEnabled = false`** — no code obfuscation/shrinking in release, keeping the APK straightforward
- JVM target is **Java 1.8** for maximum device compatibility

---

## 14. Known Limitations & Future Improvements {#limitations}

### Current Limitations

1. **Android 16 write restrictions**: The comment in the code explicitly acknowledges that "Android 16 blocks ALL write methods to /Android/data/ from shell." The 6-method approach is a workaround, but Method 1 (writing to `/data/data/`) relies on ROM-specific behavior that may not work on all devices.

2. **Single screen**: The entire UI is one scrollable activity. As features grow, this will become cramped.

3. **No profiles/presets save**: Settings are not persisted between sessions — users must re-select every time.

4. **FPS 90/120 availability**: The game internally gates these framerates by device model. Unlocking them in the config doesn't guarantee the game will honor them on unsupported hardware.

5. **No Vulkan rendering toggle**: The README mentions Vulkan support, but the current `GfxConfig.kt` doesn't include a Vulkan CVar. This may be a planned feature.

### Suggested Improvements

| Improvement | Priority | Benefit |
|---|---|---|
| Save/load named profiles | High | Users can quickly switch between "competitive" and "quality" configs |
| Current config reader | High | Show what settings are currently applied in-game |
| Vulkan rendering toggle | Medium | Better GPU performance on supported devices |
| Performance preset buttons | Medium | One-tap "Smooth 60fps" preset as mentioned in README |
| Widget on home screen | Low | Apply settings without opening the app |
| Game launch integration | Low | Auto-apply settings when the game launches |

---

## Conclusion

GFX Tool Pro is a **well-engineered, focused Android application** that solves a genuinely hard problem: writing to a protected directory on modern Android without root access. 

Its key innovations are:
1. **Smart use of Shizuku** — leveraging an existing, trusted tool rather than rolling a risky custom solution
2. **Reflection-based process spawning** — using Shizuku's internal API for the most reliable privileged execution
3. **The 6-method write cascade** — a pragmatic, resilient approach to Android's storage fragmentation

The codebase is small (5 Kotlin files, ~500 lines of logic), clean, and educational. It's a great example of how deep knowledge of Android internals — storage scoping, SELinux, the shell user model, AIDL, and Shizuku's internals — can produce powerful tools that punch well above their weight in terms of capability.

---

*Article written by analyzing the complete source code of GFX Tool Pro v2.0.0 (package: `com.bgmigfxtool.pro`).*
