# Apple App Store Submission Guide

## Overview

iOS custom keyboards are delivered as **Keyboard Extensions** — a separate binary target bundled inside a host app. Apple requires both a host app (for settings/onboarding) and the keyboard extension target.

CircleOne iOS has two phases (see `ios/README.md`):
- **Phase 1 (Now):** Hamster RIME schema — users install Hamster from App Store, import CircleOne schema. No App Store submission needed for CircleOne itself.
- **Phase 2 (Q4 2026):** Standalone native keyboard extension — requires App Store submission.

This guide covers **Phase 2** — the standalone App Store submission.

---

## Prerequisites

| Requirement | Details |
|------------|---------|
| Apple Developer Program | $99/year at https://developer.apple.com/programs/ |
| Mac with Xcode 16+ | Cannot build iOS apps on Windows |
| Bundle ID | `za.co.thegeek.circleone` |
| Team ID | From your Apple Developer account |
| iOS deployment target | iOS 17+ |
| Swift version | Swift 6 |

**Important:** You MUST have a Mac to build, sign, and submit iOS apps. There is no workaround — Apple requires Xcode which only runs on macOS.

---

## Step 1: Apple Developer Account

1. Go to https://developer.apple.com/programs/
2. Enrol as an **Organisation** (The Other Bhengu (Pty) Ltd t/a The Geek.)
   - Requires a D-U-N-S number for the company
   - If you don't have one, apply free at https://www.dnb.com/duns.html (takes 5-14 days)
   - Alternatively, enrol as **Individual** (faster, same $99/year)
3. Pay $99/year
4. Once approved, you get access to App Store Connect

---

## Step 2: Register the App

1. Go to https://developer.apple.com/account/resources/identifiers
2. Click **+** → **App IDs** → **App**
3. Description: `CircleOne`
4. Bundle ID (Explicit): `za.co.thegeek.circleone`
5. Enable capabilities:
   - **App Groups** (for shared data between host app and keyboard extension)
6. Click **Continue** → **Register**

Then register the keyboard extension:
1. Click **+** → **App IDs** → **App**
2. Bundle ID: `za.co.thegeek.circleone.keyboard`
3. Enable **App Groups**
4. Register

Create the App Group:
1. Go to **Identifiers** → **App Groups**
2. Click **+**
3. Name: `CircleOne Shared`
4. ID: `group.za.co.thegeek.circleone`

---

## Step 3: Xcode Project Setup

The project structure (from `ios/README.md`):

```
CircleOne/
  CircleOneApp/              -- Host app target
    Info.plist
    CircleOneApp.swift
  CircleOneKeyboard/         -- Keyboard Extension target
    Info.plist
    KeyboardViewController.swift
    Views/
    Engine/
    Assets/
```

### Host App Info.plist — nothing special needed

### Keyboard Extension Info.plist — critical settings:

```xml
<key>NSExtension</key>
<dict>
    <key>NSExtensionAttributes</key>
    <dict>
        <key>IsASCIICapable</key>
        <true/>
        <key>PrefersRightToLeft</key>
        <false/>
        <key>PrimaryLanguage</key>
        <string>zu</string>
        <key>RequestsOpenAccess</key>
        <false/>
    </dict>
    <key>NSExtensionPointIdentifier</key>
    <string>com.apple.keyboard-service</string>
    <key>NSExtensionPrincipalClass</key>
    <string>$(PRODUCT_MODULE_NAME).KeyboardViewController</string>
</dict>
```

**Key decisions:**
- `RequestsOpenAccess` = **false** — no network access, reinforces privacy promise
- `PrimaryLanguage` = `zu` — can list additional languages in App Store Connect
- `IsASCIICapable` = **true** — keyboard can produce ASCII characters

---

## Step 4: Build and Archive

```bash
# In Xcode or command line:
xcodebuild -workspace CircleOne.xcworkspace \
  -scheme CircleOne \
  -configuration Release \
  -sdk iphoneos \
  -archivePath build/CircleOne.xcarchive \
  archive

# Export for App Store upload:
xcodebuild -exportArchive \
  -archivePath build/CircleOne.xcarchive \
  -exportPath build/export \
  -exportOptionsPlist ExportOptions.plist
```

Or in Xcode: **Product → Archive** → **Distribute App** → **App Store Connect**

---

## Step 5: App Store Connect

1. Go to https://appstoreconnect.apple.com
2. Click **My Apps** → **+** → **New App**
3. Fill in:
   - Platform: **iOS**
   - Name: **CircleOne**
   - Primary language: **English (U.K.)**
   - Bundle ID: `za.co.thegeek.circleone`
   - SKU: `circleone-001`
4. Click **Create**

### App Information
- Category: **Utilities**
- Subcategory: **Productivity** (optional)
- Content Rights: **Does not contain third-party content** (HeliBoard is open source but the iOS version is native)
- Age Rating: **4+** (fill out questionnaire — all answers "No")

### Pricing
- Price: **Free**

### App Privacy
- Data collection: **None**
- No data linked to user, no data used to track
- Privacy policy URL: `https://thegeek.co.za/circleone/privacy`

### Store Listing
- Screenshots: iPhone 6.7" (1290x2796), iPhone 6.5" (1284x2778), iPad (optional)
- App icon: 1024x1024 PNG (no alpha, no rounded corners — Apple rounds them)
- Description: adapt from `android/store-listing/description-en.txt`
- Keywords: `isiBheqe, keyboard, African languages, syllabary, featural, isiZulu, writing system`
- Support URL: `https://github.com/bhengubv/circleone`
- Marketing URL: `https://isibheqe.org.za`

---

## Step 6: Upload Build

1. In Xcode: **Product → Archive → Distribute App → App Store Connect → Upload**
2. Or use `xcrun altool` / Transporter app
3. Wait ~15 minutes for processing
4. In App Store Connect, select the build under your app version

---

## Step 7: Submit for Review

1. Fill in all required metadata (screenshots, description, contact info)
2. Click **Add for Review**
3. Click **Submit to App Review**

### Review Timeline
- Typical: **24-48 hours** for new apps
- Keyboard extensions may take longer (sensitive permission category)
- First submission of a new developer account often takes longer

### Common Rejection Reasons for Keyboards
1. **No host app functionality** — the host app must do something (settings, tutorial, onboarding)
2. **Misleading privacy claims** — if you request Open Access, you must justify it
3. **Missing keyboard switcher** — must include a globe key to switch keyboards
4. **Crashes on enable** — test the full flow: Settings → Keyboards → Add → CircleOne
5. **Metadata mismatch** — screenshots must show the actual keyboard

---

## Apple-Specific Requirements for Keyboard Extensions

1. **Globe key (Next Keyboard button)** — MANDATORY. Must call `advanceToNextInputMode()` when tapped. Apple will reject without this.
2. **Memory limit** — keyboard extension gets ~50 MB. Keep dictionaries and assets small.
3. **No network without Open Access** — since we set `RequestsOpenAccess = false`, no URLSession calls allowed.
4. **App Group for shared data** — host app and keyboard extension share data via `group.za.co.thegeek.circleone`.
5. **No full keyboard access prompt** — since we don't request it, users never see the scary "allows full access" warning. This is a privacy advantage.

---

## Phase 1 (Immediate — No Submission Needed)

While Phase 2 is being built, users can get CircleOne on iOS today:

1. Install **Hamster** from App Store (free): https://apps.apple.com/app/hamster-keyboard/id6446988714
2. Import the CircleOne RIME schema from `ios/hamster-config/`
3. Follow instructions in `ios/hamster-config/README.md`

This gets isiBheqe input on iOS immediately with no Apple review process.

---

## Checklist

### Before Submission
- [ ] Apple Developer Program membership active
- [ ] Bundle ID registered (`za.co.thegeek.circleone` + `.keyboard`)
- [ ] App Group created (`group.za.co.thegeek.circleone`)
- [ ] Host app has meaningful content (onboarding, settings, tutorial)
- [ ] Keyboard extension includes globe key (Next Keyboard)
- [ ] Tested on real device (not just simulator)
- [ ] Privacy policy hosted publicly
- [ ] Screenshots captured on actual device
- [ ] App icon 1024x1024 (no alpha channel)

### After Approval
- App live at App Store
- Update README with App Store link
- Add App Store badge to marketing materials
