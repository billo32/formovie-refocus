# Refocus: automatic refocus for Formovie / Xiaomi projectors

**Your projector's picture goes blurry after a while, and you keep digging through Settings → Projector → Focus to fix it? Refocus does it for you.**

Refocus is a small Android TV app for **Formovie / Xiaomi (Fengmi)** projectors that re-runs the projector's built-in **autofocus** automatically, or with one press of a remote button. Built and tested on the **Formovie S5** (Android 9). No root required.

## The problem: the image slowly drifts out of focus

Many Formovie / Xiaomi projectors focus once at power-on and then go soft:

- **Thermal drift.** The lens and focus motor heat up during the first 20–40 minutes, and a picture that was sharp at startup becomes blurry.
- **Focus motor drift.** The focus stepper can lose its position over a long session or after vibration.
- **No quick way to refocus.** The stock firmware only autofocuses on boot or when the projector is physically moved. The only manual fix is a trip into the settings menu.
- **Shaking it resets the keystone.** Nudging the projector does trigger autofocus, but it also recalculates the keystone and throws away your geometry.

## What Refocus does

Refocus triggers the projector's own autofocus routine, the same one the firmware runs at boot:

- **🕒 After warm-up.** 15 and 35 minutes after power-on, when thermal drift is worst.
- **🔁 Periodically.** Every hour by default (off / 15 / 30 / 60 / 120 min). Skipped while the projector is in standby.
- **🎮 From the remote.** Assign any unused button (e.g. the voice assistant button) to refocus instantly.
- **👆 On demand.** "Focus now" in the app.
- **📐 Keeps your keystone.** Optionally disables auto keystone on movement, so a bumped projector keeps your keystone settings.

Refocus only re-triggers the projector's own autofocus. It doesn't move the lens itself, so focus is as accurate as the stock routine.

<!-- SCREENSHOTS -->

## Compatible projectors

Refocus relies on the Fengmi imaging app (`com.fengmi.projector.imaging`) that ships on projectors made by Fengmi/Formovie, including several sold under the Xiaomi brand. Only the first model has been tested.

| Projector | Codename | OS | Status |
|---|---|---|---|
| Formovie S5 (ALPD laser) | `peppa` | Android TV 9 | ✅ Tested |
| Mi Smart Compact Projector / Mi Smart Projector 2 | `MiProjM05`, `eva` | Android TV 9 | 🟡 Likely: same imaging and autofocus packages |
| Mi Smart Projector 2 Pro | — | Android TV 9 | 🟡 Likely: Fengmi-made, ToF + camera autofocus |
| Formovie Dice | — | Android TV 9 | 🟡 Possible: Fengmi-made, autofocus |
| Xiaomi Smart Projector L1 / L1 Pro | `MiProjL1` | Android TV 11+ / Google TV | ⚪ Unknown: ships the imaging app, newer firmware may differ |
| Formovie Theater, Cinema Edge (UST) | — | Android TV 11 / Google TV | ❌ Not useful: manual focus |

Check your projector in one command. If it prints `fengos.intent.action.START_FOCUS`, Refocus should work:

```bash
adb shell dumpsys package com.fengmi.projector.imaging | grep -m1 "fengos.intent.action.START_FOCUS"
```

Then test the autofocus trigger directly:

```bash
adb shell am broadcast -n com.fengmi.projector.imaging/.persist.BootReceiver \
  -a fengos.intent.action.START_FOCUS --ez fromIot true
```

Tried it on another model? Please open an issue with the model name, `adb shell getprop ro.build.fingerprint` and whether it worked.

## How it works

The firmware's imaging app (`com.fengmi.projector.imaging`) exposes an unprotected broadcast receiver. Refocus sends it an explicit broadcast:

```
action:    fengos.intent.action.START_FOCUS
component: com.fengmi.projector.imaging/.persist.BootReceiver
extra:     fromIot=true   (forces autofocus even if "autofocus on boot" is off)
```

This runs the projector's own camera-based autofocus (the white screen with markers for ~2 s). It does not touch the keystone.

| Feature | Mechanism |
|---|---|
| Schedule | `AlarmManager` (non-wakeup) + `BOOT_COMPLETED` / vendor fast-boot broadcasts; skipped while the screen is off |
| Remote button | `AccessibilityService` with key-event filtering; the assigned key is consumed |
| Auto keystone toggle | `Settings.Global close_keystone_from_tvinput` (needs `WRITE_SECURE_SETTINGS`, granted via ADB) |

### Limitations

- **Silent ToF focus is not possible without root.** The projector has a ToF sensor and the firmware uses it inside auto keystone, but the motor and ToF HAL services (`vendor.fengmi.hardware.motor@1.0`, `vendor.fengmi.hardware.tof@1.0`) are blocked by SELinux for shell and regular apps, and the "Auto Focus Method" setting is hidden on this model. So refocusing always shows the camera focus pattern.
- With auto keystone on movement disabled, moving the projector no longer triggers autofocus either. Use the remote button or the schedule.
- Tested on a Formovie S5 running Android 9 (`Fengmi/peppa/peppa:9/PPR1.180610.011/3.1.1.3775`). See [Compatible projectors](#compatible-projectors) for other models.

## Install

Enable **Developer options → USB debugging / network debugging** on the projector, then from your computer:

```bash
adb connect <projector-ip>:5555
adb install -r Refocus.apk

# allow the "Auto keystone on movement" toggle
adb shell pm grant dev.tls1.refocus android.permission.WRITE_SECURE_SETTINGS

# enable the remote-button service (the accessibility menu may be hidden on the projector)
adb shell settings put secure enabled_accessibility_services dev.tls1.refocus/dev.tls1.refocus.KeyService
adb shell settings put secure accessibility_enabled 1

# open the app once so Android allows it to start on boot
adb shell am start -n dev.tls1.refocus/.MainActivity
```

Download `Refocus.apk` from [Releases](../../releases).

## Usage

| Item | Action |
|---|---|
| **Focus now** | Runs autofocus immediately |
| **Periodic** | Cycles off / 15 / 30 / 60 / 120 min |
| **After power-on** | Toggles the 15 and 35 min warm-up refocus |
| **Remote button** | Select, then press the remote button to assign. Back cancels, long-press clears. D-pad and OK can't be assigned |
| **Auto keystone on movement** | Toggles keystone recalculation when the projector is moved |

### Using the voice assistant button

The voice button on Android TV remotes also opens Google voice search through a separate path that an accessibility service can't block. If you don't use voice search, disable it:

```bash
adb shell pm disable-user --user 0 com.google.android.katniss
# undo: adb shell pm enable com.google.android.katniss
```

### Debugging

```bash
adb logcat -s Refocus
```

Trigger autofocus manually without the app:

```bash
adb shell am broadcast -n com.fengmi.projector.imaging/.persist.BootReceiver \
  -a fengos.intent.action.START_FOCUS --ez fromIot true
```

## Build

No Gradle or Android Studio needed. The build uses Debian/Ubuntu packages:

```bash
sudo apt install openjdk-17-jdk-headless aapt dalvik-exchange apksigner zipalign android-sdk-platform-23
./build.sh   # -> build/Refocus.apk
```

`build.sh` generates a local `debug.keystore` on first run. Keep it if you want to install updates over an existing install (`adb install -r` requires the same signing key).

## License

MIT
