# Project Specification & Engineering Prompt: LocalDeX (FOSS)

Act as a Senior Android Systems Engineer and Material 3 UI/UX Architect. Build **LocalDeX**, a Free and Open Source Software (FOSS) Android application written in **100% Kotlin** that runs **Samsung DeX natively on a Samsung Galaxy device's own screen**—requiring **no external monitor, no dock, no USB cable, no PC, and no root access**.

---

## 1. Project Overview & Core Mechanism

**LocalDeX** turns a Samsung Galaxy phone, foldable, or tablet into a self-contained desktop workstation. Instead of requiring physical HDMI/DisplayPort output or a Windows PC, LocalDeX creates a hidden virtual display on the device itself, triggers Samsung DeX on that virtual display, and renders it fullscreen with low-latency hardware decoding and desktop-class pointer/keyboard input.

### How It Works Under the Hood
1. **Loopback ADB over TLS (`127.0.0.1`):** Uses Android 11+ Wireless Debugging and mDNS (`NsdManager`) to pair and connect the app to the device's own ADB daemon over localhost (no data ever leaves the device).
2. **Self-Healing Permission Grant:** Immediately after the first successful ADB connection, LocalDeX executes `pm grant <packageName> android.permission.WRITE_SECURE_SETTINGS` via its own ADB shell. Because Android disables Wireless Debugging on reboot or Wi-Fi changes, holding `WRITE_SECURE_SETTINGS` lets LocalDeX automatically re-enable `adb_wifi_enabled` via `Settings.Global` before launching sessions.
3. **Virtual Display Creation via Bundled `scrcpy-server`:** Pushes and launches an embedded `scrcpy-server` binary via `app_process` over the local ADB shell using `--new-display=<WIDTH>x<HEIGHT>/<DPI>`. One UI 8+ automatically recognizes the virtual display as an external screen and starts Samsung DeX on Display ID `N`.
4. **Native On-Device `scrcpy` Client:** Connects directly to the local `scrcpy` video and control sockets, decodes the H.264/H.265 stream onto a fullscreen `SurfaceView` using Android's hardware `MediaCodec`, and translates touch gestures and hardware peripherals into virtual mouse/keyboard events injected into Display ID `N`.
5. **FOSS Philosophy:** Licensed under **GPL-3.0** (matching `scrcpy` ecosystem compatibility), zero proprietary analytics/trackers, zero network permissions beyond local loopback/LAN ADB, and structured for clean community contributions and reproducible APK builds (F-Droid / GitHub Releases).

---

## 2. Core & Advanced Features

### Phase 1: MVP (Essential Functionality)
* **Guided One-Time ADB Pairing Flow:**
  * Detects Developer Options and Wireless Debugging state automatically.
  * Launches Android's Wireless Debugging settings screen while posting a persistent notification with an **Inline Reply (`RemoteInput`)** field so the user can enter the 6-digit pairing code without leaving Settings.
  * Automatically discovers `_adb-tls-pairing._tcp` and `_adb-tls-connect._tcp` ports via `NsdManager`.
* **Display Profile Manager (`WIDTHxHEIGHT/DPI`):**
  * Built-in presets: *Native Aspect (Foldable Inner / Slab)*, *1080p Standard (`1920x1080/240`)*, *4:3 Productivity (`1920x1440/240`)*, *Ultrawide (`2560x1080/280`)*, and *Custom Resolution/DPI*.
  * Live preview of effective logical viewport (`dp`) and aspect ratio fit vs. fill scaling.
* **Fullscreen Hardware-Accelerated DeX Viewer:**
  * Immersive edge-to-edge `SurfaceView` backed by low-latency `MediaCodec` (`KEY_LOW_LATENCY = 1`).
  * **Multi-Mode Input Engine:**
    * *Virtual Trackpad Mode (Default):* One-finger drag moves cursor; tap = left-click; tap-and-hold + drag = drag window handles/select text; two-finger tap = right-click; two-finger drag = smooth scroll wheel.
    * *Direct Touch Mode:* Maps finger coordinates directly to virtual display coordinates.
    * *Hardware Peripheral Pass-Through:* Full support for Bluetooth/USB mice (including right-click, middle-click, scroll wheel, cursor capture) and physical keyboards (forwarding `Alt+Tab`, `Meta/Super`, `Esc`, and system shortcuts).
  * **System Navigation Forwarding:** Forwards Android Back gestures/buttons, Home, and Recents specifically to the DeX display (`displayId = N`) rather than exiting the viewer unintentionally.
* **Persistent Foreground Session Service:**
  * Keeps the virtual display alive when the user presses Home to check a phone app.
  * Ongoing notification displays active resolution, Display ID `N` (enabling optional PC connection via `scrcpy --display-id=N`), and quick actions: **Resume Viewer** and **Stop DeX**.
* **Draggable Translucent Session Pill:**
  * A subtle, draggable floating pill in the corner of the DeX viewer providing quick access to: *Stop Session (with confirmation)*, *Toggle Touch/Trackpad Mode*, *Soft Keyboard Trigger*, and *Display Zoom/Pan*.

### Phase 2: Power-User & One UI Enhancements
* **One UI 8.5+ Resizable Window Restoration:** Optional ADB shell toggle (`enable_freeform_support` / `force_resizable_activities` / `persist.wm.debug.desktop_mode` flags) with safe restore-on-exit to ensure apps open in movable, edge-resizable floating windows.
* **Foldable Hinge & Posture Awareness:** Automatically switches display profiles when a Galaxy Z Fold is unfolded vs. folded (using Jetpack `WindowManager` `FoldingFeature`).
* **Bitrate, Codec & Frame Rate Controls:** Configurable H.264/H.265 codec, max FPS (`60` / `120 Hz`), and bitrate (`8–32 Mbps`) for crisp text clarity on high-DPI OLED screens.
* **Quick Settings Tile:** Android Quick Settings tile (`TileService`) to launch or stop the default LocalDeX profile in one tap.

---

## 3. Technical Architecture & Stack

### Language & Build System
* **Language:** 100% **Kotlin** (Coroutines + `StateFlow` / `SharedFlow` for asynchronous socket I/O and UI state).
* **UI Toolkit:** **Jetpack Compose** with **Material 3 (`androidx.compose.material3`)**.
* **Build System:** Gradle with **Kotlin DSL (`build.gradle.kts`)** and Version Catalog (`libs.versions.toml`).
* **SDK Targets:**
  * `minSdk = 30` (Android 11 — required for native Wireless Debugging)
  * `targetSdk = 36` (Android 16 — full One UI 8+ desktop windowing support)
  * `compileSdk = 36`
  * **JDK:** Java 17+

### Key Libraries & Low-Level Modules
* **ADB Loopback Layer:** Port/integrate `libadb-android` (or `dadadada` / `Conscrypt` + `BouncyCastle` TLS pairing) to handle RSA key generation, TLS 1.3 SPAKE2+ pairing, and multiplexed ADB streams (`shell:`, `sync:`) natively in Kotlin without external binaries.
* **Streaming Core:** Bundle the official FOSS `scrcpy-server` binary in `app/src/main/assets/` and implement a clean, modular Kotlin `scrcpy` client (`VideoStreamDecoder`, `ControlSocketWriter`, `DeviceMessageReader`).
* **Architecture Pattern:** Clean Architecture + **MVVM / MVI (Unidirectional Data Flow)** separated into:
  * `:core:adb` — mDNS discovery, TLS pairing, ADB shell command executor, `WRITE_SECURE_SETTINGS` manager.
  * `:core:scrcpy` — Server deployment, socket protocol framing, `MediaCodec` surface pipeline, input event serializer.
  * `:core:service` — `DexSessionService` foreground service, wake-locks, notification inline-reply receiver, lifecycle cleanup.
  * `:app` — Jetpack Compose Material 3 UI, navigation, DataStore preferences, Quick Settings Tile.

---

## 4. UI/UX Design Approach (Material 3 / Material You)

Design the interface strictly following **Material 3 (M3) Expressive Guidelines**, balancing clean consumer polish with power-user transparency:

1. **Dynamic Color & OLED Dark Mode:**
   * Support Android 12+ **Dynamic Color** (`dynamicDarkColorScheme` / `dynamicLightColorScheme`) with a refined **Samsung DeX-inspired Indigo/Cobalt** fallback seed palette (`#3B66F5`).
   * True dark surface hierarchy (`surfaceContainerLowest` to `surfaceContainerHigh`) to blend seamlessly into bezels during active sessions.
2. **Adaptive Screen Layouts (`NavigationBar` / `NavigationRail`):**
   * Use `WindowSizeClass` so the launcher UI adapts smoothly between narrow cover screens, standard slab phones, and wide unfolded Galaxy Z Fold / Galaxy Tab screens.
3. **Hero Status Card & One-Tap Launch:**
   * Top **M3 Elevated Card** displaying real-time readiness status (*Ready*, *Pairing Required*, *Session Active on Display #N*) with an expressive **Extended FAB** (`Start DeX` / `Resume DeX`).
4. **Interactive Setup Stepper:**
   * Clean vertical M3 stepper cards for first-time setup:
     1. *Notification Permission* (with inline explanation of the pairing reply box)
     2. *Developer Options* (deep-link button to Settings)
     3. *Wireless Debugging Pairing* (triggers mDNS listener + pairing notification)
     4. *Auto-Reconnect Permission (`WRITE_SECURE_SETTINGS`)* (verified badge once granted)
5. **Display Profile Selector:**
   * Interactive **M3 FilterChips** and custom preset cards with visual aspect-ratio wireframes, slider controls for DPI (`120` to `480`), and calculated effective workspace dimensions.
6. **Non-Intrusive Viewer Overlay:**
   * During an active DeX session, hide all system bars (`WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE`).
   * Render a compact, magnetic-edge translucent **M3 Floating Toolbar Pill** (alpha `0.35` when idle, `0.95` on touch) that expands into a compact M3 bottom/side sheet for input mode switching, soft keyboard, and session termination.

---

## 5. APK Build Pipeline & FOSS Repository Structure

Generate a complete, ready-to-build repository layout and build configuration:

```
LocalDeX/
├── .github/workflows/
│   └── build-apk.yml          # CI workflow building debug & unsigned/signed release APKs
├── app/
│   ├── build.gradle.kts       # Compose, M3, MediaCodec, DataStore, ABI splits / universal APK
│   └── src/main/
│       ├── assets/            # Bundled scrcpy-server + checksum verification
│       ├── java/org/localdex/
│       │   ├── ui/            # M3 Theme, SetupWizard, HomeScreen, ProfileEditor, ViewerActivity
│       │   ├── service/       # DexSessionService, PairingReplyReceiver, DexTileService
│       │   └── data/          # DataStore DisplayProfileRepository
│       └── AndroidManifest.xml
├── core/
│   ├── adb/                   # Loopback TLS pairing, mDNS discovery, ADB shell client
│   └── scrcpy/                # Protocol framing, MediaCodec Surface decoder, input injector
├── gradle/libs.versions.toml
├── build.gradle.kts
├── settings.gradle.kts
├── LICENSE                    # GNU General Public License v3.0
└── README.md                  # Architecture diagram, setup guide, CLI build instructions
```

### APK Packaging & CI Requirements
- Configure `app/build.gradle.kts` to output clean, versioned APKs (`LocalDeX-vX.Y.Z-universal.apk`) via `./gradlew assembleDebug` and `./gradlew assembleRelease`.

- Disable proprietary Google Play dependencies (no GMS/Firebase) so the APK passes **F-Droid** and **IzzyOnDroid** inclusion checks cleanly.

- Include a GitHub Actions workflow (`.github/workflows/build-apk.yml`) using JDK 17 that compiles the project, runs unit tests on the ADB/scrcpy protocol parsers, and uploads the built APK artifact on every push and release tag (`release: vX.Y.Z`).

## 6. Deliverable Instructions
Start by scaffolding the complete Gradle project (`libs.versions.toml`, `build.gradle.kts`, `AndroidManifest.xml`), then implement the modules in clean, production-grade Kotlin without placeholders or `TODO` stubs:

- **First:** Output the full Gradle configuration, Manifest, and Material 3 Theme/Navigation structure.

- **Second:** Implement `:core:adb` (mDNS discovery, SPAKE2+ TLS pairing, local ADB shell, `WRITE_SECURE_SETTINGS` grant).

- **Third:** Implement `:core:scrcpy` (scrcpy-server launcher, MediaCodec SurfaceView renderer, virtual mouse/trackpad & keyboard event injector).

- **Fourth:** Implement `DexSessionService`, the Notification Inline-Reply pairing handler, and the Jetpack Compose Material 3 screens (`HomeScreen`, `SetupSheet`, `ProfileEditorSheet`, `DexViewerActivity`).

---

### Key Technical Notes When Running This Prompt
* **Bundling `scrcpy-server`:** Make sure to drop the official prebuilt `scrcpy-server` JAR (v3.x+) into `app/src/main/assets/scrcpy-server` and verify its SHA-256 hash at runtime before pushing it to `/data/local/tmp/` over the loopback ADB shell.
* **Samsung Install Verification Quirk:** If sideloading debug APKs directly via `adb install` on newer One UI builds triggers `INSTALL_FAILED_VERIFICATION_FAILURE`, installing via the on-device file manager or temporarily toggling `verifier_verify_adb_installs` resolves it cleanly.