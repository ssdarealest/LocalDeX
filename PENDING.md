# PENDING.md: LocalDeX Roadmap & Current Progress

This document tracks completed milestones, active work, and pending features for **LocalDeX**.

---

## 1. Current Progress Snapshot

### Completed Milestones

- [x] **Project Scaffolding & Build System**
  - Gradle multi-module architecture (`:app`, `:core:adb`, `:core:scrcpy`).
  - Version Catalog (`gradle/libs.versions.toml`) configured for Kotlin 2.1.0, AGP 8.8.0, and Android 16 (`compileSdk = 36`).
  - Official Gradle 8.11.1 wrapper, `gradle.properties`, and Android 16 toolchain configured.
  - GitHub Actions CI workflow (`.github/workflows/build-apk.yml`) triggered on `release: vX.Y.Z` commits to `main` with automated release tagging and universal APK uploads.
  - Dependabot automated dependency bumps for Gradle and GitHub Actions (`.github/dependabot.yml`).
  - FOSS GPL-3.0 licensing, clean `.gitignore`, and contributor/agent guide (`AGENTS.md`).

- [x] **Core ADB Loopback Engine (`:core:adb`)**
  - Pure-Kotlin RSA-2048 keypair generation and self-signed X.509 certificate creation (`AdbCrypto.kt`).
  - mDNS Wireless Debugging discovery for `_adb-tls-pairing._tcp` and `_adb-tls-connect._tcp` (`AdbDiscoveryManager.kt`).
  - TLS 1.3 SPAKE2+ 6-digit PIN pairing client (`AdbPairingClient.kt`).
  - Multiplexed 24-byte packet framing and streams (`AdbConnection.kt`, `AdbStream.kt`, `AdbMessage.kt`).
  - ADB file push sync protocol (`pushFile`) for binary deployment.
  - Self-healing `WRITE_SECURE_SETTINGS` automation and `adb_wifi_enabled = 1` toggle (`AdbPermissionManager.kt`).
  - One UI desktop flags and freeform window controls (`setFreeformSupport`, `setForceResizableActivities`, `setDesktopModeDebug`).
  - Unit tests for message serialization, checksums, and permission manager commands (`AdbMessageTest.kt`, `AdbPermissionManagerTest.kt`).

- [x] **Core Scrcpy Engine (`:core:scrcpy`)**
  - Bundled official `scrcpy-server` v3.1 JAR (`app/src/main/assets/scrcpy-server`) with runtime SHA-256 validation.
  - Process spawner and virtual display launcher via loopback `app_process` (`ScrcpyServerManager.kt`).
  - Audio stream options (`audioEnabled`, `audioCodec`: Opus, AAC, RAW) integrated into server command spawner.
  - Low-latency `MediaCodec` H.264/H.265 video decoder rendering directly to a `Surface` (`VideoStreamDecoder.kt`).
  - Binary control socket writer (`ControlSocketWriter.kt`, `ControlMessage.kt`).
  - Multi-mode desktop input engine: Virtual Trackpad mode, Direct Touch mode, S-Pen stylus precision routing with 4096-level pressure, and hardware mouse pass-through (`InputEngine.kt`).
  - Relative mouse coordinate accumulation for Android Pointer Capture.
  - Display profile repository and logical dp viewport calculator (`DisplayProfile.kt`, `DisplayProfileRepository.kt`).
  - Unit tests for control packets, display profile math, server commands, and input engine (`ControlMessageTest.kt`, `DisplayProfileTest.kt`, `ScrcpyServerManagerTest.kt`, `InputEngineTest.kt`).

- [x] **Foldable Hinge & Posture Awareness (`:app`, `:core:scrcpy`)**
  - Jetpack `androidx.window:window` `WindowInfoTracker` integration (`FoldablePostureTracker.kt`).
  - Automatic detection of `NORMAL`, `TABLETOP`, `BOOK`, and `UNFOLDED` hinge postures.
  - Galaxy Z Fold display presets: Cover screen (`2316x904/380`) and Inner screen (`2176x1812/360`).
  - Tabletop Flex Mode layout adaptation in `DexViewerActivity.kt`.
  - Foldable posture recommendation banner on `HomeScreen.kt`.
  - Unit tests in `FoldablePostureTrackerTest.kt`.

- [x] **Session Service & System Integration (`:app`)**
  - Persistent foreground service maintaining virtual display and wake-locks (`DexSessionService.kt`).
  - Notification `RemoteInput` receiver for entering pairing PINs directly from Android quick settings / notifications (`PairingReplyReceiver.kt`).
  - Android Quick Settings tile service for quick start/stop (`DexTileService.kt`).

- [x] **Material 3 Expressive UI & Customization (`:app`)**
  - Dynamic Color theme with OLED dark surfaces and DeX Cobalt fallback (`#3B66F5`).
  - Hero Status Card and one-tap launch FAB (`HomeScreen.kt`).
  - 4-step guided onboarding bottom sheet with One UI Desktop Enhancements toggles (`SetupWizardSheet.kt`).
  - Display profile editor with live aspect ratio wireframe preview and audio forwarding toggles (`ProfileEditorSheet.kt`).
  - Fullscreen immersive viewer activity with pointer capture locking and S-Pen air hover support (`DexViewerActivity.kt`).
  - Draggable translucent floating pill toolbar with mouse lock toggle (`SessionPillToolbar.kt`).
  - System Back gesture forwarding to Samsung DeX Display ID `N` (with pointer release handling).

- [x] **FOSS Packaging & Metadata**
  - Fastlane Triple-T metadata layout under `fastlane/metadata/android/en-US/` (`title.txt`, `short_description.txt`, `full_description.txt`, `changelogs/1.txt`).
  - Official F-Droid metadata build recipe template (`metadata/org.localdex.yml`).
  - Zero proprietary GMS / Firebase dependencies (F-Droid and IzzyOnDroid compliant).

- [x] **Compilation & Artifact Verification**
  - `./gradlew test --stacktrace`: 100% test pass rate across all modules.
  - `./gradlew assembleDebug`: Verified universal debug APK (`LocalDeX-v1.0.0-debug-universal.apk`, 73 MB).
  - `./gradlew assembleRelease`: Verified universal release APK with R8 / ProGuard shrinking and baseline profiles (`LocalDeX-v1.0.0-universal.apk`, 13 MB).

---

## 2. Active & Pending Roadmap

### Phase 1: On-Device Hardware Verification (Ready for Testing)
- [ ] **Samsung Galaxy Device Matrix Testing**
  - Test loopback pairing against physical Galaxy devices across One UI 6 (Android 14), One UI 7 (Android 15), and One UI 8 (Android 16).
  - Test flagship series (S21, S22, S23, S24, S25), Note series (Note 20), and Fold series (Z Fold 3/4/5/6).
  - Verify Samsung One UI detects `--new-display` virtual display and launches Samsung DeX on Display ID `N`.
  - Benchmark hardware `MediaCodec` decode latency (`KEY_LOW_LATENCY = 1`) on Snapdragon vs Exynos chipsets.

### Phase 2: FOSS Distribution & Upstream Indexing
- [x] **F-Droid Build Recipe Template**
  - Prepared `metadata/org.localdex.yml` build recipe for F-Droid official repository inclusion.
- [ ] **F-Droid Official Merge Request**
  - Verify reproducible build compliance using F-Droid Docker container and submit merge request to `fdroiddata`.
- [ ] **IzzyOnDroid Submission**
  - Submit repository to IzzyOnDroid directory for automated tag tracking (`release: v*`).

### Phase 3: Ergonomic & Power-User Enhancements
- [x] **S-Pen & Stylus Pass-Through**
  - Direct precision touch routing, 4096-level pressure forwarding, S-Pen button secondary action, and hover motion tracking.
- [x] **Pointer Capture & Mouse Lock**
  - Android pointer capture API (`requestPointerCapture`) integration in `DexViewerActivity` and toggle button in `SessionPillToolbar`.
- [ ] **Custom Desktop IME / Soft Keyboard Management**
  - Provide an option to suppress or relocate the on-screen keyboard so it does not occlude the DeX taskbar or bottom desktop region.
- [ ] **Hardware Gamepad & Controller Mapping**
  - Map external Bluetooth controllers (Xbox, DualSense) to DeX display events.
