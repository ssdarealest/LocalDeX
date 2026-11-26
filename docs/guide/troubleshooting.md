---
title: Troubleshooting & FAQ
description: Solutions for Wi-Fi requirements, mDNS pairing detection, expired pairing codes, reboot reconnects, freeform windows, and video/audio quirks.
---

## Wireless Debugging & Pairing

### Why does LocalDeX require Wi-Fi if everything stays on my phone?

Android's internal `adb_wifi` daemon refuses to start or stay active unless the device is connected to an active Wi-Fi network. LocalDeX connects exclusively to `127.0.0.1` (loopback)—no traffic is sent across your Wi-Fi router or the internet.

### Pairing port not detected / "Open Wireless Debugging" never changes

- Verify **Wi-Fi** is connected and **Wireless debugging** is switched ON inside **Settings → Developer options**.
- Stay inside **Wireless debugging → Pair device with pairing code** long enough for `NsdManager` (`_adb-tls-pairing._tcp.`) to resolve the service, then open or return to LocalDeX's Setup Wizard (or switch via recent apps while keeping the pairing dialog visible in split/recent view if needed).
- If mDNS discovery stalls, toggle **Wireless debugging** OFF and ON again while LocalDeX's main screen is active (`MainActivity.onResume` starts discovery; `onPause` stops it).

### Pairing fails with "Pairing failed" or wrong code

Android invalidates and regenerates the 6-digit pairing PIN if you navigate away from the **Pair device with pairing code** dialog. Always enter the PIN using the **Wireless Debugging Pairing** notification's **Enter Code** inline reply while the pairing dialog remains open.

### Wireless Debugging turns off after rebooting or switching Wi-Fi

Android resets `adb_wifi_enabled` to `0` on reboots and network transitions.

- If Step 4 (**Auto-Reconnect Permission** / `WRITE_SECURE_SETTINGS`) is complete, simply opening LocalDeX once automatically sets `adb_wifi_enabled = 1` in `MainActivity.onResume()`.
- If `Ready to Connect` or `Pairing Required` still appears without `WRITE_SECURE_SETTINGS`, re-run Step 3 pairing so `PairingReplyReceiver` can execute `pm grant org.localdex android.permission.WRITE_SECURE_SETTINGS` over loopback.

## Display & Window Behavior

### Apps open fullscreen inside DeX instead of resizable windows

1. Open the **Guided Setup Wizard** (gear icon) and verify **Movable Freeform Windows** (`enable_freeform_support`) and **Force Desktop Windowing** (`force_desktop_mode_on_external_displays`) are enabled.
2. On Android 16+ / One UI 8.5+, virtual displays may default to fullscreen windowing unless `wm set-display-windowing-mode -d <displayId> 5` (`WINDOWING_MODE_FREEFORM`) is applied. See [Technical Breakdown](../TECHNICAL.md) for the full explanation.

### Galaxy Z Flip, Galaxy A/M/F, or non-Samsung phones don't show Samsung DeX

Those devices do not ship Samsung's proprietary DeX firmware binaries. Enable **Force Desktop Windowing** in the Setup Wizard to launch stock Android / AOSP Freeform Desktop on the virtual display instead.

### Selecting "Split-screen" from a window handle moves the app to the phone screen

This is a Samsung One UI WindowManager quirk: choosing the native **Split-screen** handle option moves the app onto the primary phone display rather than splitting inside the virtual DeX display. Resize and arrange windows manually by dragging borders/title handles instead.

## Video, Audio & Input

### Black screen, stutter, or decoder failure on launch

- Open **Display Profile Editor** and switch **Video Codec** from `H.265` to **`H.264 (Low Latency)`** (or vice versa depending on your Exynos/Snapdragon hardware decoder).
- Lower **Refresh Rate** from `120 FPS` to **`60 FPS`** or reduce **Bitrate** (e.g., from `24–32 Mbps` down to `16 Mbps`).
- Ensure your device didn't disable Wireless Debugging mid-launch (`Start DeX` falls back to port `5555` if no `_adb-tls-connect._tcp.` endpoint was resolved).

### Audio is silent or echoes

By design, **Audio Forwarding** is **off by default** so Samsung DeX outputs audio straight through your phone/tablet speakers with zero latency. Only enable **Audio Forwarding** in the Profile Editor (`OPUS` / `AAC` / `RAW`) if you specifically need scrcpy audio streaming.

### Mouse cursor hits an invisible screen boundary

Tap the Floating Pill handle and press the **Hand / Pan** button (**Lock Mouse Pointer**) to activate Android Pointer Capture. When locked, raw relative deltas keep moving the DeX cursor freely across the entire virtual display. Press **Back** or tap the button again to release capture.

### Back gesture doesn't exit the viewer

This is intentional: Android Back gestures and hardware Back buttons are intercepted in `DexViewerActivity` so they release Pointer Capture (if active) or navigate **back inside Samsung DeX**. To minimize the viewer, swipe up to Home; to close the session, expand the Floating Pill and tap the red **Stop DeX** icon (or stop via the notification / Quick Settings tile).

## Installation Issues

### `INSTALL_FAILED_VERIFICATION_FAILURE` when installing via ADB

Temporarily bypass Play Protect / ADB package verification during sideloading:

```bash
adb shell settings put global verifier_verify_adb_installs 0
adb install -r LocalDeX-v1.0.0-universal.apk
adb shell settings put global verifier_verify_adb_installs 1
```
