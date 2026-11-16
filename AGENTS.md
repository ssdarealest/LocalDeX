# AGENTS.md: LocalDeX Contributor & AI Agent Guide

Welcome to the **LocalDeX** codebase. This guide provides AI agents and human contributors with essential context, architectural invariants, code conventions, and workflows required to maintain and develop LocalDeX safely and effectively.

---

## 1. Project Overview & Mission

**LocalDeX** is a Free and Open Source Software (FOSS) Android application written in **100% Kotlin**. It enables **Samsung DeX to run natively on a Galaxy device's own screen**—with **no external monitor, no dock, no USB cable, no PC, and no root access**.

### Core Mechanism Invariants
1. **Loopback ADB over TLS (`127.0.0.1`):** Uses Android 11+ Wireless Debugging and mDNS (`NsdManager`) to pair and connect directly to the device's own ADB daemon over localhost. No data ever leaves the device.
2. **Self-Healing Permission Grant:** Upon pairing, LocalDeX runs `pm grant org.localdex android.permission.WRITE_SECURE_SETTINGS` via loopback ADB. Because Android disables Wireless Debugging across reboots or Wi-Fi network changes, holding this permission lets LocalDeX automatically re-enable `adb_wifi_enabled = 1` via `Settings.Global`.
3. **Virtual Display Creation:** Pushes bundled `scrcpy-server` (v3.1) to `/data/local/tmp/scrcpy-server.jar` and launches it via `app_process` with `--new-display=<WIDTH>x<HEIGHT>/<DPI>`. Samsung One UI detects this virtual display as an external monitor and launches DeX on Display ID `N`.
4. **Native Low-Latency Video Pipeline:** Connects to local scrcpy video and control sockets over loopback. Decodes H.264/H.265 using hardware `MediaCodec` (`KEY_LOW_LATENCY = 1`) directly onto a `SurfaceView`. Translates touch gestures and hardware peripherals into scrcpy control packets targeting Display ID `N`.
5. **FOSS & Privacy Guarantee:** Licensed under **GPL-3.0**. Zero proprietary trackers/analytics. Zero Google Play Services (GMS) or Firebase dependencies so it builds reproducible APKs that pass **F-Droid** and **IzzyOnDroid** inclusion standards.

---

## 2. Architecture & Module Structure

The project follows Clean Architecture with Unidirectional Data Flow (UDF / MVI):

```
LocalDeX/
├── app/                       # Android application module
│   ├── src/main/assets/       # Bundled scrcpy-server binary (SHA-256 verified)
│   ├── src/main/java/org/localdex/
│   │   ├── data/              # DataStore preferences & DisplayProfileRepository
│   │   ├── service/           # DexSessionService, PairingReplyReceiver, DexTileService
│   │   ├── ui/                # MainActivity, HomeScreen, SetupWizardSheet, ProfileEditorSheet
│   │   │   ├── theme/         # Material 3 Theme (DeX Cobalt seed #3B66F5, OLED dark mode)
│   │   │   └── viewer/        # DexViewerActivity, SessionPillToolbar
│   │   └── LocalDeXApp.kt     # Application class (Notification channels & BouncyCastle setup)
│   └── AndroidManifest.xml
├── core/
│   ├── adb/                   # Loopback ADB over TLS
│   │   └── src/main/java/org/localdex/core/adb/
│   │       ├── connection/    # AdbConnection, AdbStream (multiplexed channels)
│   │       ├── crypto/        # AdbCrypto (RSA-2048 keypair, self-signed X.509 cert)
│   │       ├── discovery/     # AdbDiscoveryManager (mDNS pairing and connect listener)
│   │       ├── pairing/       # AdbPairingClient (TLS 1.3 SPAKE2+ 6-digit handshake)
│   │       ├── permission/    # AdbPermissionManager (WRITE_SECURE_SETTINGS automation)
│   │       └── protocol/      # AdbMessage (24-byte binary packet framing & checksums)
│   └── scrcpy/                # Streaming demuxer & desktop input engine
│       └── src/main/java/org/localdex/core/scrcpy/
│           ├── control/       # ControlMessage, ControlSocketWriter
│           ├── decoder/       # VideoStreamDecoder (low-latency MediaCodec pipeline)
│           ├── input/         # InputEngine (Trackpad, Direct Touch, Hardware Mouse/Keyboard)
│           ├── model/         # DisplayProfile (presets, DPI, and logical dp viewport math)
│           └── server/        # ScrcpyServerManager (binary verification & process spawner)
├── gradle/
│   ├── libs.versions.toml     # Gradle Version Catalog
│   └── wrapper/               # Gradle wrapper properties (Gradle 8.11.1)
├── .github/
│   ├── dependabot.yml         # Automated dependency bump configuration
│   └── workflows/             # CI workflow (build-apk.yml)
├── build.gradle.kts           # Root Gradle build script
├── settings.gradle.kts        # Root settings and module inclusion
└── LICENSE                    # GNU General Public License v3.0
```

---

## 3. Technology Stack & Dependencies

- **Language:** Kotlin 2.1.0 (100% Kotlin Coroutines + `StateFlow` / `Channel`).
- **Target SDK:** `compileSdk = 36`, `targetSdk = 36`, `minSdk = 30` (Android 11+).
- **JVM Toolchain:** Java 17 (`sourceCompatibility` & `targetCompatibility`).
- **UI Framework:** Jetpack Compose with Material 3 Expressive (`androidx.compose.material3`).
- **Build System:** Gradle 8.11.1 with Kotlin DSL (`*.gradle.kts`) and Version Catalog (`gradle/libs.versions.toml`).
- **Loopback Cryptography:** BouncyCastle (`bcprov-jdk18on`, `bcpkix-jdk18on`) & Conscrypt.
- **Persistence:** Jetpack DataStore Preferences.
- **Testing:** JUnit 4, Google Truth, MockK, Kotlinx Coroutines Test.

---

## 4. Development Workflow & Commands

### CLI Commands (Local / Codespaces / CI)
```bash
# Run all unit tests across modules
gradle test --stacktrace

# Build Debug APK
gradle assembleDebug

# Build Universal Release APK
gradle assembleRelease

# Clean build cache
gradle clean
```

### APK Output Paths
- Debug: `app/build/outputs/apk/debug/LocalDeX-v1.0.0-debug-universal.apk`
- Release: `app/build/outputs/apk/release/LocalDeX-v1.0.0-universal.apk`

---

## 5. Coding Standards & Agent Invariants

### 1. Conventional Commits (Mandatory)
Every commit **must** follow the Conventional Commits specification:
```
<type>(<scope>): <short description>
```
- **Allowed Types:** `feat`, `fix`, `chore`, `docs`, `test`, `ci`, `refactor`, `style`, `perf`.
- **Allowed Scopes:** `adb`, `scrcpy`, `service`, `ui`, `ui/theme`, `ui/viewer`, `ui/setup`, `ui/home`, `ui/profile`, `deps`, `build`, `git`, `license`.
- **Granularity Rule:** Commit each logical part or file separately. Never commit uncoordinated bulk changes.

### 2. Gradle Version Catalog References
- In `gradle/libs.versions.toml`, kebab-case aliases (`a-b-c`) are translated by Gradle into dot-separated accessors (`libs.a.b.c`).
- **Never** write hyphens in Kotlin DSL references (e.g. write `libs.androidx.compose.material.icons.extended`, NOT `libs.androidx.compose.material-icons-extended`).

### 3. FOSS & Dependency Rules
- **No Proprietary SDKs:** Never introduce Google Play Services (GMS), Firebase, crashlytics, or closed-source telemetry.
- **Zero JNI/Native C Bloat:** Keep ADB pairing and stream handling in pure Kotlin to guarantee simple cross-compilation and full reproducibility.

### 4. Background & Lifecycle Discipline
- All socket networking, ADB framing, and crypto signing must occur off the main thread (`Dispatchers.IO`).
- `DexSessionService` must maintain `connectedDevice` and `specialUse` foreground service types declared in `AndroidManifest.xml` for Android 14+ compliance.
- System Back gestures inside `DexViewerActivity` must be forwarded to Samsung DeX Display ID `N` via `onBackPressedDispatcher.addCallback` rather than closing the viewer prematurely.

### 5. `scrcpy-server` Binary Invariants
- The bundled binary in `app/src/main/assets/scrcpy-server` must match `scrcpy-server` v3.1.
- Runtime SHA-256 verification is enforced in `ScrcpyServerManager` (`958f0944a62f23b1f33a16e9eb14844c1a04b882ca175a738c16d23cb22b86c0`). Do not modify this without updating both the asset and the verification hash.
