# healthy-launcher

An Android home screen with two modes.

**Normal mode** shows only the apps you pick. Nothing is selected for you.

**Social mode** shows every app. It starts after a challenge and lasts 30 minutes.
You can end it early. At 5 minutes and at 1 minute the phone warns you. When the
time is up, other apps close and normal mode returns.

If the phone stays locked or the screen stays off for 5 minutes during social mode,
social mode ends the same way.

## Challenges

Pick one in Settings. The default is **Drills**: a fixed set of math, memory, and
a line to retype. That set is about two minutes of work. Finishing it faster still
unlocks social mode.

The other choices are a minute of breathing plus a short prompt, a journal with a
two-minute wait, or two minutes of breathing on its own.

## Appearance

Four settings: text in grayscale, text in color, mono icons, and color icons.
Normal and social use the same layout. Social mode is a warmer background and the
word Social with a timer. Grayscale is a system color filter when the app is
allowed to change secure settings.

## Blocking

The strongest available method is used.

- **Device owner** suspends apps that are not on the normal-mode list. This is the
  block that actually stops a package from starting. On a personal Poco X7 Pro this
  requires a device with no accounts, which is not how a daily phone is set up.
- **Shizuku** runs `pm suspend` and `am force-stop` without device owner. Install
  Shizuku, start it, then allow healthy-launcher.
- **Accessibility** sends you home if a blocked app opens.
- **Notification access** removes notifications from blocked apps. Calls and alarms
  still come through.
- **Usage access** is a second check on which app is in front.

Blocking turns on once healthy-launcher is the home screen. Phone, Settings, the
keyboard, Play services, and emergency calling are never suspended, so the phone
can still be recovered. They do not appear on the home screen unless you pick them.

On a Poco X7 Pro running HyperOS, also set autostart, unrestricted battery, and
background windows. Settings has buttons for the HyperOS screens. If a screen is
missing, the button says so.

## Build

Android SDK, JDK 17, and an emulator or a phone.

```bash
./gradlew assembleDebug
./gradlew testDebugUnitTest
```

The full device walk is `scripts/run-checks.sh`. See [docs/testing.md](docs/testing.md).
