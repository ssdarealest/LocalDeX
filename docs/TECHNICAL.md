---
title: Technical Breakdown
description: Deep dive into LocalDeX's loopback ADB over TLS 1.3 stack, virtual display creation, MediaCodec pipeline, and peripheral input architecture.
---

# LocalDeX — Technical Breakdown

An in-depth technical analysis of how **LocalDeX** runs Samsung DeX natively on a Galaxy device's own screen with no external display, no dock, no PC, no cables, and no root access.

---

## 1. System Architecture

```
LocalDeX App ──(Loopback ADB over TLS 1.3, 127.0.0.1)──▶ adbd Daemon
     │  1. Push bundled scrcpy-server v3.1 to /data/local/tmp/scrcpy-server.jar (SHA-256 verified)
     │  2. app_process / com.genymobile.scrcpy.Server new_display=<WxH/DPI>
     │  3. wm set-display-windowing-mode -d <displayId> 5   (force WINDOWING_MODE_FREEFORM)
     │  4. Bridge localabstract:scrcpy streams via ScrcpyLoopbackBridge (tunnel_forward=true)
     ▼
scrcpy-server ──▶ Creates trusted virtual display ──▶ Samsung One UI triggers DeX desktop
     │  H.264/H.265 + Audio ──▶ MediaCodec (KEY_LOW_LATENCY = 1) / AudioTrack ──▶ Fullscreen SurfaceView
     ◀─ Multi-mode input packets (Trackpad, Touch, S-Pen, Pointer Capture Mouse, Gamepad)
```

The application runs **100% in pure Kotlin** and never shells out to an external `adb` CLI binary. It establishes direct TCP/TLS connections to Android's internal `adbd` on `127.0.0.1`.

---

## 2. The ADB Loopback Layer (`:core:adb`)

### Wire Protocol Framing
ADB communication follows the standard 24-byte binary packet framing (`AdbMessage.kt`):
- **Command:** 4-byte integer (`CNXN`, `AUTH`, `OPEN`, `OKAY`, `CLSE`, `WRTE`).
- **Arg0 & Arg1:** Stream IDs and protocol versions.
- **Data Length:** Length of payload in bytes.
- **Data CRC32:** Unsigned checksum of the payload.
- **Magic:** Bitwise inverted command integer (`command ^ 0xFFFFFFFF`) used for header validation.

### Pure-Kotlin Cryptography (`AdbCrypto.kt`)
Android Wireless Debugging enforces mutual TLS authentication:
1. **RSA-2048 Keypair:** Generated via BouncyCastle (`bcprov-jdk18on`).
2. **Self-Signed X.509 Certificate:** LocalDeX creates a standard self-signed certificate using BouncyCastle's `JcaX509v3CertificateBuilder` with validity of 25 years.
3. **Key Persistence:** The generated keypair and certificate are saved privately in internal storage (`/data/user/0/org.localdex/files/adb/`).

### mDNS Discovery & TLS 1.3 SPAKE2+ Handshake
- **Discovery:** LocalDeX uses Android's `NsdManager` (`AdbDiscoveryManager.kt`) to scan for two mDNS services on localhost:
  - Pairing service: `_adb-tls-pairing._tcp`
  - Connection service: `_adb-tls-connect._tcp`
- **Inline Notification Reply:** During pairing, Android displays a 6-digit PIN on the Wireless Debugging screen. Because navigating away from Developer Options cancels or regenerates the PIN, LocalDeX uses a foreground notification with `RemoteInput` (`PairingReplyReceiver.kt`). The user enters the 6-digit PIN directly in the notification reply field while remaining on the Developer Options screen.
- **TLS 1.3 SPAKE2+ Pairing Client (`AdbPairingClient.kt`, `Spake25519.kt`, `PairingAuth.kt`):** Exports 64-byte TLS 1.3 keying material via Conscrypt (`"adb-label\0"`), performs SPAKE2+ password-authenticated key exchange over Edwards25519, derives HKDF-SHA256 AES-128-GCM keys, and exchanges encrypted `PeerInfo` packets with `adbd`.

### Self-Healing Permission Grant
On Android, Wireless Debugging automatically disables itself whenever the phone reboots or disconnects from Wi-Fi. LocalDeX solves this with automated self-healing:
1. Immediately upon successful pairing, LocalDeX executes:
   ```sh
   pm grant org.localdex android.permission.WRITE_SECURE_SETTINGS
   ```
2. Because `WRITE_SECURE_SETTINGS` is declared with protection level `signature|privileged|development`, Android allows shell to grant it to any installed app.
3. Once held, `AdbPermissionManager.ensureWirelessDebuggingEnabled()` directly updates:
   ```kotlin
   Settings.Global.putInt(context.contentResolver, "adb_wifi_enabled", 1)
   ```
   This silently re-enables Wireless Debugging whenever LocalDeX launches, eliminating manual trips to Developer Options.

---

## 3. Virtual Display Creation & One UI Desktop Mode

### Trusted Virtual Display (`scrcpy-server`)
LocalDeX launches `scrcpy-server` via loopback ADB shell and exposes a local FIFO TCP relay (`ScrcpyLoopbackBridge.kt`) backed by `localabstract:scrcpy`:
```sh
CLASSPATH=/data/local/tmp/scrcpy-server.jar app_process / com.genymobile.scrcpy.Server \
    3.1 scid=-1 log_level=info tunnel_forward=true send_dummy_byte=true \
    new_display=1920x1080/240 video_codec=h264 video_bit_rate=16000000 max_fps=60 \
    audio=false control=true stay_awake=true power_off_on_close=false cleanup=true
```
When `scrcpy-server` runs inside `app_process` with shell UID (2000), it has permission to call `DisplayManager.createVirtualDisplay()` with private/trusted flags. Samsung One UI detects this new virtual display and launches Samsung DeX on Display ID `N`.

### Forcing Freeform Windowing on Android 16+ (One UI 8.5/9)
On newer Android versions, app-created virtual displays default to fullscreen windowing ([scrcpy issue #6143](https://github.com/Genymobile/scrcpy/issues/6143)). LocalDeX bypasses this limitation using AOSP's internal `DisplayWindowSettings` hierarchy:
```sh
wm set-display-windowing-mode -d <displayId> 5   # 5 = WINDOWING_MODE_FREEFORM
```
When set to 5 (`WINDOWING_MODE_FREEFORM`), Android's window manager immediately treats the virtual display as a floating desktop surface, giving all apps movable title bar drag handles and edge-resizable borders.

LocalDeX also configures global desktop mode settings:
- `settings put global enable_freeform_support 1`
- `settings put global force_resizable_activities 1`
- `settings put global force_desktop_mode_on_external_displays 1`

### Why Virtual Displays Beat Overlay Displays
Android has a developer option for simulated secondary displays (`settings put global overlay_display_devices "WxH/DPI"`). However, overlay displays have critical dealbreakers on real devices:
1. **Un-hideable Preview Window:** Android's internal `OverlayDisplayWindow` renders a persistent 0.5x preview box floating on the phone's main screen. Its position is hard-clamped on-screen (`x = max(0, min(x, screenWidth - width))`) with no flag to hide it.
2. **SystemUI Caption Crash:** On One UI 8/9, dragging any window by its caption on an overlay display crashes the `wmshell.main` thread with a NullPointerException (`DesktopModeVisualIndicator` constructed with a null display context). Virtual displays created via `scrcpy-server` bypass this crash path entirely.

---

## 4. Hardware-Accelerated Video & Audio Pipeline (`:core:scrcpy`)

### Low-Latency MediaCodec Decoder & Loopback Bridge (`ScrcpyLoopbackBridge.kt`, `VideoStreamDecoder.kt`, `AudioStreamDecoder.kt`)
- **Forward-Tunnel Loopback Bridge:** `ScrcpyLoopbackBridge` binds an ephemeral `127.0.0.1` TCP port and multiplexes accepted clients over `AdbConnection.openStream("localabstract:scrcpy")` in strict FIFO socket order (`Video -> Audio -> Control`). `VideoStreamDecoder` consumes the initial dummy byte before headers, then video and audio streams decode concurrently so `DesktopConnection.open()` never deadlocks.
- **Hardware Zero-Copy Rendering:** Decodes H.264 (AVC) and H.265 (HEVC) video streams using Android's hardware `MediaCodec` configured with `KEY_LOW_LATENCY = 1` and maximum operating rate directly onto a `SurfaceView` without copying bitmap bytes into JVM memory.
- **Loopback Audio (`AudioStreamDecoder.kt`):** When enabled per profile (`audio=true`), decodes `OPUS`, `AAC`, and `FLAC` packets via `MediaCodec` (or passes `RAW` 48 kHz stereo PCM directly) into a low-latency `AudioTrack`.
- **Framing Protocol:** LocalDeX demuxes scrcpy packet headers (`PTS`, config/keyframe flags, SPS/PPS configuration frames, and 21-byte fixed-point `Scroll` packets) to keep video, audio, and control streams synchronized.

---

## 5. Input Engine & Peripheral Architecture

LocalDeX translates Android motion and key events into binary `ControlMessage` packets targeting the DeX virtual display:

### 1. Virtual Trackpad Mode
- **1-finger glide:** Updates internal virtual coordinates (`cursorX`, `cursorY`) with a sensitivity multiplier (`1.35x`).
- **1-finger tap:** Injects `ACTION_DOWN` followed immediately by `ACTION_UP` with `BUTTON_PRIMARY` (left click).
- **1-finger drag:** Double-tap-and-hold transitions into drag mode (`isDragging`), holding `BUTTON_PRIMARY` during `ACTION_MOVE` until release.
- **2-finger scroll:** Measures relative horizontal and vertical touch deltas to inject 21-byte 16-bit fixed-point `ControlMessage.Scroll` packets.
- **2-finger tap:** Injects `BUTTON_SECONDARY` (right click).

### 2. Direct Touch Mode
- Scales physical touch coordinates (`viewWidth`, `viewHeight`) into virtual display space (`displayWidth`, `displayHeight`).
- Retains multi-touch pointer IDs for multi-finger gestures.

### 3. S-Pen / Stylus Precision Pass-Through
- Detects `TOOL_TYPE_STYLUS` and `TOOL_TYPE_ERASER`.
- Bypasses trackpad emulation to map stylus coordinates directly onto the DeX screen.
- Passes through 4096 levels of hardware pressure sensitivity (`event.getPressure(0)`).
- Maps S-Pen button presses (`BUTTON_STYLUS_PRIMARY`) and eraser tips to `BUTTON_SECONDARY`.
- Injects air hover events (`ACTION_HOVER_MOVE`) so DeX draws mid-air cursor previews.

### 4. Hardware Mouse & Pointer Capture Lock
- Connects physical USB or Bluetooth mice without Android cursor boundary clipping.
- Uses Android's pointer capture API (`surfaceView.requestPointerCapture()`):
  - In pointer capture mode, Android routes raw relative deltas (`AXIS_RELATIVE_X`, `AXIS_RELATIVE_Y`) directly to `surfaceView.setOnCapturedPointerListener`.
  - LocalDeX accumulates relative deltas into virtual cursor space, enabling true PC-like desktop navigation.
  - Native mouse wheel scrolling via `AXIS_VSCROLL` and `AXIS_HSCROLL`.

### 5. Gamepad & Controller Mapping
- Reads analog joystick inputs (`InputDevice.SOURCE_JOYSTICK`):
  - **Left Analog Stick (`AXIS_X`, `AXIS_Y`):** Smooth analog cursor gliding with deadzone filtering (`0.15f`).
  - **Right Analog Stick (`AXIS_Z`, `AXIS_RZ`):** Two-axis viewport scrolling.
  - **D-Pad Hat Switch (`AXIS_HAT_X`, `AXIS_HAT_Y`):** Injects directional D-pad key events.
  - **Button A (`KEYCODE_BUTTON_A`):** Acts as Primary Click at cursor position in Trackpad mode.

---

## 6. Foldable Posture Awareness (`FoldablePostureTracker.kt`)

Built with Jetpack `androidx.window:window`:
- Subscribes to `WindowInfoTracker.windowLayoutInfo()` to track `FoldingFeature` hinge state.
- Classifies posture in real time:
  - `NORMAL`: Standard flat slate smartphone.
  - `TABLETOP`: Half-folded horizontally (hinge angle 80°–140°). LocalDeX shrinks the viewer surface above the hinge line, turning the bottom half of the phone into a dedicated laptop trackpad.
  - `BOOK`: Half-folded vertically.
  - `UNFOLDED`: Fully opened flat foldable screen.
- Adapts display profiles dynamically between Galaxy Z Fold Cover screen (`2316x904/380`) and Inner screen (`2176x1812/360`).

---

## 7. ADB Equivalents Quick Reference

Every internal action performed by LocalDeX maps to standard ADB shell commands for debugging from a PC:

| LocalDeX Operation | ADB Shell Equivalent |
| :--- | :--- |
| **Pairing** | `adb pair 127.0.0.1:<pairing_port> <pin>` |
| **Connecting** | `adb connect 127.0.0.1:<connect_port>` |
| **Push Server** | `adb push scrcpy-server /data/local/tmp/scrcpy-server.jar` |
| **Grant Permission** | `adb shell pm grant org.localdex android.permission.WRITE_SECURE_SETTINGS` |
| **Launch Server** | `adb shell CLASSPATH=/data/local/tmp/scrcpy-server.jar app_process / com.genymobile.scrcpy.Server 3.1 ... new_display=1920x1080/240` |
| **Force Freeform** | `adb shell wm set-display-windowing-mode -d <displayId> 5` |
| **PC DeX Mirroring**| `scrcpy --display-id=<displayId>` |
| **Enable Wireless ADB**| `adb shell settings put global adb_wifi_enabled 1` |

---

## 8. Known Platform Quirks & Solutions

1. **Split-Screen Menu Option:** In Samsung One UI, selecting the *Split-screen* option from a window's title bar handle moves that app to split-screen on the phone's primary display rather than within the DeX desktop. This is an internal One UI WindowManager limitation.
2. **DeX Decor vs AOSP Freeform Decor:** Virtual displays use AOSP's drag-handle caption decor rather than DeX's legacy desktop title bar decor. This is intentional: DeX's legacy title bar decor triggers the fatal SystemUI caption drag crash on One UI 8.5/9.
3. **Reboot Persistence:** Because Android resets Wireless Debugging on device reboot, LocalDeX automatically checks `WRITE_SECURE_SETTINGS` on launch and silently re-enables `adb_wifi_enabled = 1`.
