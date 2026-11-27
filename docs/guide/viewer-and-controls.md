---
title: DeX Viewer, Floating Pill & Input Controls
description: Managing active sessions, using the draggable Session Pill Toolbar, and navigating with Virtual Trackpad, Direct Touch, S-Pen, mouse, keyboard, and gamepad.
---

When you tap **Start DeX**, `DexSessionService` connects to loopback ADB, verifies and deploys `scrcpy-server` v3.1, launches the virtual display via `ScrcpyLoopbackBridge` (`localabstract:scrcpy`), forces `WINDOWING_MODE_FREEFORM` on the detected Display ID, and opens **DexViewerActivity** in immersive landscape fullscreen.

## Session Lifecycle

- **Minimize without stopping:** Pressing your device's Home button minimizes the viewer while keeping the foreground service, virtual display, wake lock, and Wi-Fi lock active.
- **Resume Viewer:** Tap **Resume Viewer** on the Home screen's floating action buttons or tap the persistent notification (`DeX Active on Display #N`).
- **Stop Session:** Tap **Stop DeX** on the Home screen, **Stop Session** in the notification, the red **Close** button in the Floating Pill, or tap the active Quick Settings tile.

## Floating Session Pill Toolbar

Overlaid on top of the DeX stream is a translucent, draggable **Session Pill Toolbar**:

- **Move anywhere:** Press and drag the pill to reposition it away from taskbars or window controls.
- **Auto-dimming:** After **3 seconds** of inactivity while collapsed and with the soft keyboard closed, the pill automatically dims to **35% opacity** so it stays out of your way. Touching it, expanding it, or opening the soft keyboard restores **95% opacity**.
- **Expand / Collapse:** Tap the handle icon (`DragHandle` / `Close`) to toggle the 4 quick controls:

| Icon | Action | Behavior |
| :--- | :--- | :--- |
| **Mouse / Touch** | **Toggle Input Mode** | Switches dynamically between **Virtual Trackpad** and **Direct Touch**, and persists your choice across sessions. |
| **Hand / Pan** | **Lock / Release Mouse Pointer** | Toggles Android Pointer Capture (`requestPointerCapture` / `releasePointerCapture`) for hardware mice. Highlighted when pointer lock is active. |
| **Keyboard** | **Show / Hide Soft Keyboard** | Toggles Android's on-screen IME keyboard using `WindowInsetsCompat.Type.ime()`. |
| **Close (Red)** | **Stop DeX** | Stops `DexSessionService`, closes sockets/decoder, and exits `DexViewerActivity`. |

## Input Modes & Peripherals

`InputEngine` translates Android motion and key events into scrcpy binary control packets targeting your virtual display resolution.

### 1. Virtual Trackpad Mode (Default)

Turns the screen into a laptop-style relative touchpad controlling a virtual DeX pointer:

| Gesture | Action |
| :--- | :--- |
| **1-finger glide** | Moves the virtual desktop cursor (`1.35x` sensitivity multiplier). |
| **1-finger tap** (`< 200 ms`) | Left click (`BUTTON_PRIMARY` down + up at cursor position). |
| **Double-tap & hold + move** | Enters drag mode (`BUTTON_PRIMARY` held) for dragging windows and selecting text. |
| **2-finger swipe** | Horizontal and vertical two-axis scrolling at the cursor position. |
| **2-finger tap** (`< 200 ms`) | Right click (`BUTTON_SECONDARY` down + up at cursor position). |

### 2. Direct Touch Mode

Maps finger touches directly to desktop coordinates (`viewWidth × viewHeight` scaled to `displayWidth × displayHeight`), preserving multi-touch pointer IDs and touch pressure for tapping buttons, scrolling lists, and pinching/dragging directly on DeX windows.

### 3. S-Pen & Stylus (Automatic)

Whenever Android reports `TOOL_TYPE_STYLUS` or `TOOL_TYPE_ERASER`, `InputEngine` automatically bypasses Virtual Trackpad mode and routes direct precision coordinates—even while the pill is set to Trackpad mode:

- **Tip contact & drag:** Left click (`BUTTON_PRIMARY`) with hardware pressure pass-through (`event.getPressure`).
- **Air hover (`ACTION_HOVER_MOVE`):** Moves the DeX cursor smoothly in mid-air before touching the glass.
- **S-Pen side button or Eraser tip:** Sends right-click (`BUTTON_SECONDARY`).

### 4. Hardware Mouse & Pointer Capture

Connect any Bluetooth or USB mouse:

- **Standard mode:** Tracks absolute cursor position across the viewer surface, including hover movement, left/right/middle buttons (`BUTTON_PRIMARY`, `BUTTON_SECONDARY`, `BUTTON_TERTIARY`), and horizontal/vertical wheel scrolling (`AXIS_HSCROLL`, `AXIS_VSCROLL`).
- **Pointer Capture (Mouse Lock):** Tap the **Lock Mouse Pointer** button in the Floating Pill to capture raw relative mouse deltas (`AXIS_RELATIVE_X`, `AXIS_RELATIVE_Y`). LocalDeX accumulates these deltas inside the virtual display bounds so Android's host system edge or status bar never traps your mouse cursor.
- **Releasing Pointer Capture:** Tap the pill button again or press the Android **Back** button/gesture once.

### 5. Physical Keyboard & System Back Navigation

- **Keyboard pass-through:** Hardware keyboard `KeyEvent` presses and releases (`keyCode`, `repeatCount`, `metaState`) are forwarded directly to DeX.
- **System Back forwarding:** Pressing Android's Back button or swipe-back gesture does **not** close the viewer:
  1. If **Pointer Capture** is currently active, Back releases mouse pointer capture first.
  2. Otherwise, Back sends `ControlMessage.BackOrScreenOn` directly into Samsung DeX.

### 6. Gamepad / Controller Mapping

Any connected controller reporting `SOURCE_JOYSTICK` or `SOURCE_GAMEPAD` is mapped to desktop controls:

| Input | Behavior |
| :--- | :--- |
| **Left Analog Stick** (`AXIS_X`, `AXIS_Y`) | Smooth desktop cursor movement (`0.15` deadzone). |
| **Right Analog Stick** (`AXIS_Z`, `AXIS_RZ`) | Horizontal and vertical scrolling at cursor position (`0.15` deadzone). |
| **D-Pad Hat Switch** (`AXIS_HAT_X`, `AXIS_HAT_Y`) | Injects `DPAD_LEFT`, `DPAD_RIGHT`, `DPAD_UP`, and `DPAD_DOWN` key events. |
| **Button A** (`KEYCODE_BUTTON_A`) | Injects primary left click at the virtual cursor when in **Trackpad** mode (or forwards `KEYCODE_BUTTON_A` in **Direct Touch** mode). |

## Viewing the Active Session from a Computer

Because Samsung DeX runs on a standard Android virtual display, a computer connected via USB or wireless ADB can mirror or control the same live desktop:

```bash
scrcpy --display-id=<N>
```

Check `<N>` on the Home screen's **Hero Status Card**, in the persistent notification (`DeX Active on Display #N`), or on the **Quick Settings tile**.
