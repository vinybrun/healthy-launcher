# Testing

A change is not done until every layer that applies is green on the same revision.
Do not weaken a check to make a red run pass. Fix the product, or change the check
only when the check itself does not describe the product.

## Layers

| Layer | What it proves | Command |
| --- | --- | --- |
| 1. Unit | Session timing, challenges, and blocking rules. No device. | `./gradlew testDebugUnitTest` |
| 2. UI walk | Clicks, typing, and navigation on an emulator. | `./gradlew connectedDebugAndroidTest` |
| 3. Device | Device-owner suspend, alarms, the accessibility guard, and the lock timeout. Same run as layer 2. | `./gradlew connectedDebugAndroidTest` |

Layer 3 is not a screenshot of the home screen. It asks Android whether a package is
suspended, whether an alarm is scheduled, and whether a blocked app stays open.

`scripts/run-checks.sh` runs layer 1, builds debug and release, then layers 2 and 3.
It exits non-zero on the first failure. There is no skip flag.

## Emulator

The device walk needs a fresh Android 15 image with no accounts, this app as device
owner, and this app holding the home role. `scripts/run-checks.sh` does that setup.
Reinstall with `adb install -r`. Uninstalling a device-owner app will fail until the
owner is cleared.

## What the checks lock

- Normal mode lists only apps the user picked. The list starts empty.
- Social mode lists every launchable app for 30 minutes. The user can end it early.
- Warnings exist at 5 minutes and at 1 minute.
- A phone that stays locked or asleep for 5 minutes leaves social mode.
- The default challenge is a fixed drill set with no countdown. Other challenges are
  selectable and cannot be skipped.
- Appearance has four settings. Mode changes the apps and the background, not the layout.
- Device owner suspends disallowed apps. Settings, the keyboard, phone, and this app
  are never suspended.
- When suspend is off, the accessibility guard still sends a blocked app home.

Release builds do not include the debug clock receiver. Production durations are
30 minutes and 5 minutes in `SessionTiming`.
