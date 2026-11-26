---
title: Requirements & Installation
description: Supported Samsung Galaxy devices, Android version requirements, permissions, and how to install LocalDeX.
---

Before pairing LocalDeX, verify that your device meets the OS, firmware, and network prerequisites.

## System Requirements

| Requirement | Details |
| :--- | :--- |
| **Android Version** | **Android 11+** (`minSdk = 30`, target SDK 36) with Wireless Debugging support. |
| **Device / Firmware** | Samsung Galaxy smartphone or tablet with Samsung DeX firmware (One UI 8+ for virtual-display desktop mode, or One UI 6 / 7). |
| **Network** | Connected to an active **Wi-Fi network**. Android's internal `adb_wifi` service will not initialize without Wi-Fi, even though LocalDeX connects strictly to `127.0.0.1`. |
| **Permissions** | Notification permission (`POST_NOTIFICATIONS`, Android 13+) for entering the 6-digit pairing PIN via inline notification reply. |

## Device Compatibility

### Full Samsung DeX Support

Devices with official Samsung DeX firmware in One UI launch the native Samsung DeX desktop on LocalDeX's virtual display:

- **Galaxy S Series:** Galaxy S10 / S20 / S21 / S22 / S23 / S24 / S25 families (including Plus, Ultra, and FE models).
- **Galaxy Z Fold Series:** Galaxy Z Fold 2 through Z Fold 6 and Z Fold Special Edition (includes Jetpack WindowManager posture detection and Tabletop Flex Mode).
- **Galaxy Note Series:** Galaxy Note 8, Note 9, Note 10 / 10+ / Lite, and Note 20 / 20 Ultra (includes S-Pen pressure and air hover forwarding).
- **Galaxy Tab S Series:** Galaxy Tab S6 through Tab S10+ / Ultra, Tab S7/S9 FE, and Tab Active 4 Pro / 5.

### Experimental / AOSP Desktop Mode Support

- **Galaxy Z Flip Series (Z Flip 3–6) & Galaxy A / M / F Series:** Most Flip and midrange models omit Samsung DeX binaries from One UI firmware. Enabling **Force Desktop Windowing** (`force_desktop_mode_on_external_displays`) in the Setup Wizard lets them launch AOSP's secondary Freeform Desktop instead.
- **Non-Samsung Devices (Pixel, OnePlus, Xiaomi, Motorola, etc.):** On Android 11+ devices without Samsung DeX, LocalDeX spawns Android's stock secondary desktop environment rather than Samsung DeX.

## Installing LocalDeX

### 1. Download the APK

Download the universal APK from GitHub Releases or build it from source:

- **Release build (`org.localdex`):** `LocalDeX-v1.0.0-universal.apk` (R8-shrunk universal release).
- **Debug build (`org.localdex.debug`):** `LocalDeX-v1.0.0-debug-universal.apk` (installable alongside release builds due to the `.debug` suffix).

### 2. Sideload on Device or via ADB

On your phone or tablet, open the downloaded APK and allow installation from your browser or file manager if prompted.

If installing from a computer over USB ADB and Android blocks the install with `INSTALL_FAILED_VERIFICATION_FAILURE`, temporarily disable ADB install verification while installing:

```bash
adb shell settings put global verifier_verify_adb_installs 0
adb install -r LocalDeX-v1.0.0-universal.apk
adb shell settings put global verifier_verify_adb_installs 1
```

## Permissions Used by LocalDeX

Declared in `AndroidManifest.xml`:

- `INTERNET`, `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE`: Loopback TCP/TLS communication with `127.0.0.1` (`adbd` and `scrcpy-server`) and Wi-Fi lock maintenance during active sessions.
- `POST_NOTIFICATIONS`: Shows the inline-reply pairing prompt and active session controls.
- `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_CONNECTED_DEVICE`, `FOREGROUND_SERVICE_SPECIAL_USE`, `WAKE_LOCK`: Keeps the virtual display session and loopback stream alive when the viewer is minimized.
- `WRITE_SECURE_SETTINGS`: Protected system permission granted locally via loopback ADB (`pm grant`) after pairing, used only for self-healing `adb_wifi_enabled` and toggling One UI desktop windowing flags.
