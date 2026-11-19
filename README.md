# Refocus

A tiny Android TV app that keeps the **Formovie S5** (Xiaomi/Fengmi "Peppa", Android 9) projector in focus — without opening the projector settings.

The projector's lens drifts out of focus as it warms up, and the stock firmware only refocuses on boot or when the projector is moved (which also resets the keystone). Refocus triggers the built-in autofocus:

- **on a schedule** — 15 and 35 minutes after power-on (warm-up), then every hour (configurable: off / 15 / 30 / 60 / 120 min);
- **with a remote button** you don't use (e.g. the voice assistant button);
- **on demand** from the app.

It can also **disable auto keystone on movement**, so a bumped projector keeps your keystone settings.

No root required.

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
- Tested on a Formovie S5 running Android 9 (`Fengmi/peppa/peppa:9/PPR1.180610.011/3.1.1.3775`). Other Fengmi/Formovie models with the same imaging app may work but are untested.

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
