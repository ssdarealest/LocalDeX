# PENDING.md: LocalDeX Roadmap & Current Progress

This document tracks completed milestones, active work, and pending features for **LocalDeX**.

---

## 1. Current Progress Snapshot

### Completed Milestones

- [x] **Project Scaffolding & Build System**
  - Gradle multi-module architecture (`:app`, `:core:adb`, `:core:scrcpy`).
  - Version Catalog (`gradle/libs.versions.toml`) configured for Kotlin 2.1.0, AGP 8.8.0, and Android 16 (`compileSdk = 36`).
  - GitHub Actions CI workflow (`.github/workflows/build-apk.yml`) for automated test execution and universal APK artifact builds.
  - Dependabot automated dependency bumps for Gradle and GitHub Actions (`.github/dependabot.yml`).
  - FOSS GPL-3.0 licensing and standard Android `.gitignore`.
  - Comprehensive contributor and AI agent guide (`AGENTS.md`).

- [x] **Core ADB Loopback Engine (`:core:adb`)**
  - Pure-Kotlin RSA-2048 keypair generation and self-signed X.509 certificate creation (`AdbCrypto.kt`).
  - mDNS Wireless Debugging discovery for `_adb-tls-pairing._tcp` and `_adb-tls-connect._tcp` (`AdbDiscoveryManager.kt`).
  - TLS 1.3 SPAKE2+ 6-digit PIN pairing client (`AdbPairingClient.kt`).
  - Multiplexed 24-byte packet framing and streams (`AdbConnection.kt`, `AdbStream.kt`, `AdbMessage.kt`).
  - ADB file push sync protocol (`pushFile`) for binary deployment.
  - Self-healing `WRITE_SECURE_SETTINGS` automation and `adb_wifi_enabled = 1` toggle (`AdbPermissionManager.kt`).
  - Unit tests for ADB message serialization and checksum validation (`AdbMessageTest.kt`).

- [x] **Core Scrcpy Engine (`:core:scrcpy`)**
  - Bundled official `scrcpy-server` v3.1 JAR (`app/src/main/assets/scrcpy-server`) with runtime SHA-256 validation.
  - Process spawner and virtual display launcher via loopback `app_process` (`ScrcpyServerManager.kt`).
  - Low-latency `MediaCodec` H.264/H.265 video decoder rendering directly to a `Surface` (`VideoStreamDecoder.kt`).
  - Binary control socket writer (`ControlSocketWriter.kt`, `ControlMessage.kt`).
  - Multi-mode desktop input engine: Virtual Trackpad mode, Direct Touch mode, and hardware peripheral pass-through (`InputEngine.kt`).
  - Display profile repository and logical dp viewport calculator (`DisplayProfile.kt`, `DisplayProfileRepository.kt`).
  - Unit tests for scrcpy control packets and profile math (`ControlMessageTest.kt`, `DisplayProfileTest.kt`).

- [x] **Session Service & System Integration**
  - Persistent foreground service maintaining virtual display and wake-locks (`DexSessionService.kt`).
  - Notification `RemoteInput` receiver for entering pairing PINs directly in Android Settings (`PairingReplyReceiver.kt`).
  - Android Quick Settings tile service (`DexTileService.kt`).

- [x] **Material 3 Expressive UI (`:app`)**
  - Dynamic Color theme with OLED dark surfaces and DeX Cobalt fallback (`#3B66F5`).
  - Hero Status Card and one-tap launch FAB (`HomeScreen.kt`).
  - 4-step guided onboarding bottom sheet (`SetupWizardSheet.kt`).
  - Display profile editor with live aspect ratio wireframe preview (`ProfileEditorSheet.kt`).
  - Fullscreen immersive viewer activity (`DexViewerActivity.kt`).
  - Draggable translucent floating pill toolbar (`SessionPillToolbar.kt`).
  - System Back gesture forwarding to Samsung DeX Display ID `N`.

---

## 2. Pending Tasks & Roadmap

### High Priority (Immediate Next Steps)
- [ ] **Codespaces Compilation & Test Validation**
  - Run `./gradlew test --stacktrace` on Linux in GitHub Codespaces to verify all unit test suites.
  - Run `./gradlew assembleDebug` to produce the first debug universal APK.
  - Verify ProGuard rules and resource shrinking during `./gradlew assembleRelease`.

- [ ] **On-Device Hardware Verification**
  - Test loopback pairing against physical Samsung Galaxy devices (One UI 6, One UI 7, and One UI 8).
  - Verify Samsung One UI detects `--new-display` virtual display and triggers desktop mode.
  - Measure hardware `MediaCodec` decode latency on Snapdragon and Exynos chipsets (`KEY_LOW_LATENCY = 1`).

### Medium Priority (Power-User & Experience Enhancements)
- [ ] **Foldable Hinge & Posture Awareness**
  - Integrate Jetpack `androidx.window:window` `FoldingFeature`.
  - Automatically detect when a Galaxy Z Fold is opened or closed, switching between Cover profile and Inner profile seamlessly.

- [ ] **Samsung DeX Audio Loopback Forwarding**
  - Explore audio stream capture via `scrcpy-server` audio forwarding options (`audio=true`, `audio_codec=raw/opus`) for complete sound pass-through.

- [ ] **Advanced One UI Desktop Flags**
  - Expose UI toggles in Settings for `enable_freeform_support`, `force_resizable_activities`, and desktop mode debug flags.

### Low Priority (FOSS Distribution & Packaging)
- [ ] **F-Droid Metadata & Fastlane Layout**
  - Create `fastlane/metadata/android/en-US/` directory with app descriptions, changelogs, and screenshots.
  - Validate reproducible build compliance for F-Droid submission.
- [ ] **IzzyOnDroid Repository Submission**
  - Package signed release APK tags (`v1.0.0`) via GitHub Actions release workflow.
