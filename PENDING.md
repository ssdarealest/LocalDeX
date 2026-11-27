# PENDING.md: LocalDeX Engineering Backlog & Execution Plan

This document tracks **everything remaining to make LocalDeX production-ready end-to-end on physical Samsung Galaxy hardware**, along with code fixes, test coverage, website/docs polish, CI improvements, and FOSS store submission steps.

---

## 1. Critical Runtime & Protocol Blockers

These gaps were identified directly in the codebase and must be resolved before end-to-end pairing, streaming, and desktop interaction will work reliably on real devices.

### 1.1 Implement Real Android 11+ SPAKE2+ Wireless Debugging Pairing (`:core:adb`) — ✅ Implemented (Unit-Tested; Hardware Validation in 4.1)
- **Completed:**
  - [x] Enabled Conscrypt TLS 1.3 on the pairing `SSLSocket` and exported 64-byte keying material (`Conscrypt.exportKeyingMaterial(sslSocket, "adb-label\u0000", null, 64)`).
  - [x] Implemented pure-Kotlin BoringSSL-compatible SPAKE2+ over Edwards25519 (`Spake25519.kt`) and AOSP `PairingAuth.kt` / `PairingPacket.kt` (HKDF-SHA256 + sequenced 12-byte-nonce AES-128-GCM `PeerInfo` exchange carrying `adbCrypto.getAdbPublicKeyString()`).
  - [x] Verified against local TLS 1.3 mock pairing server tests (`Spake25519Test.kt`, `AdbPairingClientTest.kt`).

### 1.2 Align `scrcpy-server` Socket Transport & Connection Order (`:core:scrcpy`, `:core:adb`, `:app`) — ✅ Implemented
- **Completed:**
  - [x] Updated `ScrcpyServerManager.buildServerCommand()` to pass `scid`, `tunnel_forward=true`, `send_dummy_byte=true` (removing invalid `port=27183`) and added `ScrcpyServerManager.socketDestination()`.
  - [x] Added `ScrcpyLoopbackBridge.kt` listening on an ephemeral `127.0.0.1` TCP port and forwarding bidirectionally in FIFO accept order to `AdbConnection.openStream("localabstract:scrcpy...")` (`ScrcpyLoopbackBridgeTest.kt`).
  - [x] Fixed `AdbConnection` to fail pending `openStream()` calls immediately on `A_CLSE` rejection and always clean up sockets in `close()` (`AdbConnectionTest.kt`).
  - [x] Updated `VideoStreamDecoder` to split `connect()` (dummy byte consumption) from `startStreaming()`, fixed resource cleanup on startup failure, and updated `DexViewerActivity.surfaceCreated()` to open Video -> Audio (if enabled) -> Control sockets in strict `DesktopConnection.open()` order before decoding headers concurrently (`VideoStreamDecoderTest.kt`).
  - [x] Enforced `EXPECTED_SERVER_SHA256` verification in `ScrcpyServerManager.ensureServerDeployed()` (`ScrcpyServerManagerTest.kt`).

### 1.3 Fix `ControlMessage.Scroll` Wire Encoding (`:core:scrcpy`) — ✅ Implemented
- **Completed:**
  - [x] Updated `ControlMessage.Scroll.toByteArray()` to write a `21`-byte `ByteBuffer` with 16-bit signed fixed-point `hScroll` / `vScroll` clamped to `[-1f, 1f]`.
  - [x] Added serialization and clamping unit tests in `ControlMessageTest.kt`.

### 1.4 Enforce Freeform Windowing & Display ID Detection on Launch (`:core:adb`, `:core:scrcpy`, `:app`) — ✅ Implemented
- **Completed:**
  - [x] Improved `ScrcpyServerManager.monitorServerStdout()` display ID parsing (avoiding false matches against `WIDTHxHEIGHT`) with `dumpsys display` fallback and live `DexSessionService` state/notification updates.
  - [x] Executed `wm set-display-windowing-mode -d <displayId> 5` (`WINDOWING_MODE_FREEFORM`) automatically once the virtual display is created (`ScrcpyServerManagerTest.kt`).
  - [x] Wired `AdbPermissionManager.setFreeformSupport`, `setForceResizableActivities`, and `setDesktopModeDebug` into `SetupWizardSheet` / `MainActivity` off the main thread (`Dispatchers.IO`) with `SecurityException` guards and post-write state refreshes.

### 1.5 Implement Loopback Audio Stream Consumer (`:core:scrcpy`, `:app`) — ✅ Implemented
- **Completed:**
  - [x] Created `AudioStreamDecoder` in `:core:scrcpy` reading the 4-byte scrcpy audio codec ID (`OPUS`, `AAC`, `FLAC`, `RAW`, plus disabled/error handling), decoding compressed streams via `MediaCodec` and writing low-latency PCM (48 kHz stereo) to `AudioTrack` (`AudioStreamDecoderTest.kt`).
  - [x] Wired `AudioStreamDecoder` connect/stream/teardown lifecycle cleanly into `DexViewerActivity` whenever `activeProfile.audioEnabled == true`.

---

## 2. Input Engine, Lifecycle & UI Fixes

### 2.1 Complete Trackpad Gestures & Button Metadata (`:core:scrcpy`) — ✅ Implemented
- **Completed:**
  - [x] Added double-tap-and-hold drag transition (`isDragging = true`, `BUTTON_PRIMARY` press/move/release) and multi-finger tap suppression in `InputEngine.handleTrackpadTouch()`.
  - [x] Enabled simultaneous two-finger horizontal (`hScroll`) and vertical (`vScroll`) scroll deltas on swipe.
  - [x] Populated `ControlMessage.Touch.actionButton` across trackpad clicks, stylus presses/releases, hardware mouse buttons, and gamepad Button A (`InputEngineTest.kt`).

### 2.2 Harden `PairingReplyReceiver`, `DexSessionService` & Port Discovery (`:app`) — ✅ Implemented
- **Completed:**
  - [x] Wrapped `PairingReplyReceiver.onReceive()` in `goAsync()` with `pendingResult.finish()` inside `finally`.
  - [x] Guarded `SessionState.Starting` duplicate starts in `DexSessionService`, switched to `ServiceCompat.startForeground(...)` with explicit `connectedDevice | specialUse` types on API 34+, cleaned up foreground state on startup failures, and moved `DexViewerActivity` launch to `MainActivity` observing `SessionState.Running` via `LaunchedEffect` (plus `ContextCompat.startForegroundService`).
  - [x] Replaced the hardcoded `5555` port fallback in `MainActivity` with resolved mDNS `connectEndpoint?.port` lookup (3s discovery wait + Toast/Setup Wizard fallback if unavailable).

### 2.3 Fix Home/Viewer UI State Gaps, Icons & Dead Code (`:app`) — ✅ Implemented
- **Completed:**
  - [x] Added dedicated `SessionState.Error` UI rendering and setup/retry trigger in `HomeScreen.HeroStatusCard`.
  - [x] Added a `LifecycleEventObserver` (`ON_RESUME`) in `MainActivity` to refresh notification permission, Developer Options, Wireless Debugging, `WRITE_SECURE_SETTINGS`, and desktop flag toggles cleanly.
  - [x] Exposed saved custom profiles in `DisplayProfileRepository.customProfileFlow` and included them in `HomeScreen`'s `allProfiles` list.
  - [x] Persisted `InputMode` across viewer sessions via `DisplayProfileRepository.inputModeFlow` / `saveInputMode` and implemented `DexViewerActivity.surfaceChanged()` to sync dimensions and keep immersive system bars hidden.
  - [x] Removed unused `Navigation.kt` and added custom adaptive vector launcher icons (`mipmap-anydpi-v26/ic_launcher*.xml`) plus monochrome `@drawable/ic_dex_notification`.

---

## 3. Testing, CI/CD & Documentation Sync

### 3.1 Expand Automated Test Suite (`:core:adb`, `:core:scrcpy`, `:app`) — ✅ Implemented
- **Completed:**
  - [x] Added loopback socket / mock stream tests across `:core:adb` and `:core:scrcpy`: `Spake25519Test`, `AdbPairingClientTest`, `AdbConnectionTest`, `ScrcpyLoopbackBridgeTest`, `ScrcpyServerManagerTest`, `VideoStreamDecoderTest`, `AudioStreamDecoderTest`, `ControlSocketWriterTest`, and `InputEngineTest`.
  - [x] Added `:app` tests for `DisplayProfileRepository` (`PreferenceDataStoreFactory` serialization) and Compose UI state rendering (`HeroStatusCardTest` and `SetupWizardSheetTest` with Robolectric).

### 3.2 Add Continuous Integration for PRs & Website (`/.github/workflows/`) — ✅ Implemented
- **Completed:**
  - [x] Added `.github/workflows/ci.yml` running `./gradlew test --stacktrace` + `./gradlew lintDebug --stacktrace` and `website/` `pnpm install --frozen-lockfile && pnpm sync:docs:check && pnpm types:check && pnpm build` on every `push` to `main` and `pull_request`.

### 3.3 Website Assets & Docs Synchronization (`website/`, `docs/`) — 🚧 Partially Completed
- **Completed:**
  - [x] Added `website/scripts/sync-docs.mjs` (`sync:docs`, `sync:docs:check`, `prebuild`) keeping `docs/guide/` + `docs/TECHNICAL.md` synchronized into `website/content/docs/` while skipping cleanly on standalone `website/` builds where `../docs` is absent, enforced via CI.
- **Remaining:**
  - [ ] Replace the hero placeholder in `website/app/(home)/page.tsx` with a `1280 × 1150 px` screenshot/graphic saved at `website/public/hero.png`.

---

## 4. Physical Device Validation & FOSS Distribution

### 4.1 Samsung Galaxy Hardware Verification Matrix
- **Plan / Approach:**
  1. Test on physical Samsung Galaxy devices across **One UI 6 (Android 14)**, **One UI 7 (Android 15)**, and **One UI 8+ (Android 16)**:
     - **Galaxy S Series (S21–S25):** Verify virtual display DeX launch, `WINDOWING_MODE_FREEFORM`, H.264/H.265 `MediaCodec` (`KEY_LOW_LATENCY = 1`) latency on Snapdragon vs. Exynos, and `WRITE_SECURE_SETTINGS` reboot persistence.
     - **Galaxy Z Fold Series (Z Fold 3–6):** Verify `FoldablePostureTracker` Cover/Inner banner switching and `TABLETOP` Flex Mode layout above `hingeBounds.top`.
     - **Galaxy Note / S Ultra / Tab S:** Verify S-Pen 4096-level pressure, air hover, eraser/side button right-click, USB/BT mouse Pointer Capture, and gamepad navigation.
     - **Galaxy Z Flip & Non-DeX Devices:** Verify AOSP Freeform Desktop fallback via `force_desktop_mode_on_external_displays`.

### 4.2 F-Droid & IzzyOnDroid Store Inclusion
- **Plan / Approach:**
  1. Run F-Droid's `fdroid build` / reproducible-build verification container locally against `metadata/org.localdex.yml` and `fastlane/metadata/android/en-US/`.
  2. Submit merge request to upstream `fdroiddata` and register releases (`v*` tags) with IzzyOnDroid.
