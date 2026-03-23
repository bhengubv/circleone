# CircleOne Keyboard -- Android Build (HeliBoard-based)

## Overview

CircleOne is an Android keyboard for African languages, built on top of
[HeliBoard](https://github.com/Helium314/HeliBoard), an open-source keyboard
forked from OpenBoard. The fork replaces HeliBoard's identity with CircleOne
branding and ships custom layouts, dictionaries, and fonts optimised for
Southern African languages including isiZulu, isiXhosa, isiNdebele, siSwati,
Sesotho, Setswana, Sepedi, Tshivenda, Xitsonga, and Afrikaans.

## Prerequisites

- Java 17+ (JDK, not JRE)
- Android SDK with Build-Tools 34+, Platform API 34
- Android NDK (r25c or later)
- Git
- Bash shell (Linux/macOS/WSL)

## Quick Start

```bash
# One-step build -- clones HeliBoard, patches identity, builds APK
./build-apk.sh
```

The signed (or debug) APK will be written to `output/circleone-release.apk`.

## What the Build Script Does

1. Clones the HeliBoard repository at a pinned tag.
2. Copies `heliboard-config/one_layout.json` into the appropriate assets
   directory.
3. Patches `applicationId` to `africa.one.keyboard` and the display name to
   "CircleOne".
4. Copies dictionary and font assets (when present in `assets/`).
5. Runs `./gradlew assembleRelease`.

## Project Structure

```
android/
  README.md                  -- this file
  build-apk.sh               -- automated build script
  heliboard-config/
    one_layout.json           -- QWERTY layout with click-consonant extras
  store-listing/
    description-en.txt        -- Play Store full description
    short-description-en.txt  -- Play Store short description (< 80 chars)
    privacy-policy.md         -- privacy policy (required by Play Store)
    content-rating.md         -- content rating declaration
  assets/                     -- (optional) dictionaries and fonts
```

## Signing

For release builds, place your keystore at `android/keystore.jks` and create
`android/keystore.properties`:

```
storeFile=../keystore.jks
storePassword=<password>
keyAlias=circleone
keyPassword=<password>
```

The build script will detect this file and sign the APK automatically.

## Testing

Install the APK on a device or emulator:

```bash
adb install output/circleone-release.apk
```

Then go to Settings > System > Languages & Input > On-screen keyboard and
enable "CircleOne".
