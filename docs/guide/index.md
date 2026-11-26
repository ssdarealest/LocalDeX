---
title: Introduction
description: Run Samsung DeX natively on your Galaxy device's own screen with no external monitor, dock, USB cable, PC, or root access.
---

**LocalDeX** is a Free and Open Source Software (FOSS) Android application written in **100% Kotlin** and licensed under **GPL-3.0**. It launches Samsung's native DeX desktop environment onto an on-device virtual display and renders it fullscreen directly on your phone or tablet.

## Why LocalDeX?

Normally, Samsung DeX on Galaxy smartphones requires plugging into an external HDMI monitor, USB-C hub, or wireless Miracast receiver. LocalDeX removes that hardware requirement by running the entire virtual display and video/input loop locally on `127.0.0.1`.

- **100% On-Device:** Connects directly to Android's own `adbd` daemon over loopback (`127.0.0.1`). Zero data leaves your device.
- **No Root Required:** Uses standard Android 11+ Wireless Debugging pairing and ADB shell permissions (`WRITE_SECURE_SETTINGS`).
- **Low-Latency Hardware Video:** Decodes H.264 or H.265 video from bundled `scrcpy-server` v3.1 using `MediaCodec` (`KEY_LOW_LATENCY = 1`) straight to a `SurfaceView`.
- **Desktop-Class Input:** Supports Virtual Trackpad gestures, Direct Touch, S-Pen / Stylus with air hover and pressure sensitivity, physical keyboards and mice (with Pointer Capture lock), and gamepads.
- **Galaxy Z Fold Ready:** Uses Jetpack WindowManager to detect hinge posture, suggest Cover vs. Inner screen profiles, and adapt the viewer layout for Tabletop Flex Mode.
- **Strictly FOSS:** Zero Google Play Services (GMS), Firebase, ads, telemetry, or analytics.

## How a Session Works

1. **Loopback Pairing (One-Time):** LocalDeX discovers your device's Wireless Debugging mDNS service (`_adb-tls-pairing._tcp.`), pairs over TLS 1.3 using the 6-digit pairing code, and grants itself `WRITE_SECURE_SETTINGS`.
2. **Self-Healing Reconnect:** Whenever LocalDeX resumes (`MainActivity.onResume`), it automatically re-enables Wireless Debugging (`adb_wifi_enabled = 1`) if Android turned it off after a reboot or Wi-Fi network change.
3. **Virtual Display Launch:** LocalDeX verifies and pushes `scrcpy-server` v3.1 to `/data/local/tmp/scrcpy-server.jar`, launches it via `app_process` with `new_display=<width>x<height>/<dpi>`, and binds loopback sockets on port `27183`.
4. **Fullscreen DeX Viewer:** Samsung One UI detects the trusted virtual display and starts Samsung DeX on that Display ID while `DexViewerActivity` streams the desktop video and forwards your inputs.

## Documentation Map

- **[Requirements & Installation](./installation.md):** Check Android/One UI compatibility, device support, and APK installation steps.
- **[Setup & Wireless Pairing](./setup-pairing.md):** Walk through the 4-step Guided Setup Wizard and inline notification pairing.
- **[Display Profiles & Video/Audio](./display-profiles.md):** Choose built-in presets or tune custom resolution, DPI, FPS, bitrate, codec, and audio forwarding.
- **[Viewer, Pill Toolbar & Controls](./viewer-and-controls.md):** Master the Floating Session Pill, Virtual Trackpad, Direct Touch, S-Pen, mouse Pointer Capture, and gamepad controls.
- **[Foldables & Quick Settings](./foldables-and-quick-settings.md):** Use Galaxy Z Fold posture detection, Tabletop Flex Mode, persistent notifications, and the Quick Settings tile.
- **[Troubleshooting](./troubleshooting.md):** Fix Wi-Fi/mDNS discovery, pairing code invalidation, window resizing, and video/audio quirks.
- **[Technical Breakdown](../TECHNICAL.md):** Inspect the underlying ADB wire protocol, TLS/crypto implementation, and scrcpy integration.
- **[Building from Source](./building-from-source.md):** Compile, test, and verify reproducible builds locally.
