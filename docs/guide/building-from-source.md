---
title: Building from Source & Contributing
description: Build LocalDeX locally, run unit tests across all modules, verify scrcpy-server integrity, and follow FOSS packaging standards.
---

LocalDeX is structured as a multi-module Android Gradle project written in 100% Kotlin with zero proprietary dependencies.

## Project Structure

- **`:app`** (`org.localdex`) — Jetpack Compose Material 3 UI, DataStore profile repository, foreground `DexSessionService`, `DexTileService`, `PairingReplyReceiver`, `FoldablePostureTracker`, and `DexViewerActivity`.
- **`:core:adb`** (`org.localdex.core.adb`) — Pure-Kotlin loopback ADB over TLS 1.3 (`AdbConnection`, `AdbStream`, `AdbMessage`), BouncyCastle RSA-2048/X.509 (`AdbCrypto`), `NsdManager` discovery (`AdbDiscoveryManager`), pairing (`AdbPairingClient`), and `WRITE_SECURE_SETTINGS` automation (`AdbPermissionManager`).
- **`:core:scrcpy`** (`org.localdex.core.scrcpy`) — `scrcpy-server` v3.1 deployment/bridge (`ScrcpyServerManager`, `ScrcpyLoopbackBridge`), display presets (`DisplayProfile`), low-latency `MediaCodec` decoders (`VideoStreamDecoder`, `AudioStreamDecoder`), control framing (`ControlMessage`, `ControlSocketWriter`), and multi-mode `InputEngine`.

## Prerequisites

- **JDK:** OpenJDK 17 (`JAVA_HOME`)
- **Gradle:** Wrapper 8.11.1 (`./gradlew` or `gradle`)
- **Android SDK:** `compileSdk = 36`, `build-tools;36.0.0`, `platforms;android-36`

## Build & Test Commands

```bash
# Run unit tests across :app, :core:adb, and :core:scrcpy
./gradlew test --stacktrace

# Build Universal Debug APK
./gradlew assembleDebug

# Build Universal Release APK (R8 / ProGuard minified)
./gradlew assembleRelease
```

### Output APK Paths

- **Debug:** `app/build/outputs/apk/debug/LocalDeX-v1.0.0-debug-universal.apk`
- **Release:** `app/build/outputs/apk/release/LocalDeX-v1.0.0-universal.apk`

## Bundled `scrcpy-server` Integrity

The binary at `app/src/main/assets/scrcpy-server` corresponds to `scrcpy-server` **v3.1** and is verified at runtime against its SHA-256 checksum inside `ScrcpyServerManager`:

```text
958f0944a62f23b1f33a16e9eb14844c1a04b882ca175a738c16d23cb22b86c0
```

Both the bundled asset and `EXPECTED_SERVER_SHA256` must remain aligned if upgrading `scrcpy-server`.

## FOSS & CI Standards

- **License:** GNU General Public License v3.0 (`LICENSE`).
- **Reproducible FOSS Builds:** Zero Google Play Services (GMS), Firebase, or closed-source telemetry. Metadata and build recipes live in `fastlane/metadata/android/en-US/` and `metadata/org.localdex.yml`.
- **CI Workflows:** `.github/workflows/ci.yml` runs `./gradlew test --stacktrace`, `./gradlew lintDebug --stacktrace`, and `website/` sync/typecheck/build on `main` pushes and pull requests; `.github/workflows/build-apk.yml` builds and publishes release APKs when commit messages include `release: vX.Y.Z`.
- **Commit Convention:** Conventional Commits (`<type>(<scope>): <short description>`) as specified in `AGENTS.md`.
