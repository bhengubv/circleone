# CircleOne Keyboard -- iOS

## Overview

CircleOne on iOS is delivered in two phases. Phase 1 uses an existing
open-source keyboard host (Hamster) to get CircleOne into users' hands quickly.
Phase 2 is a standalone native iOS Keyboard Extension.

---

## Phase 1: Hamster Schema Package

[Hamster](https://github.com/imfuxiao/Hamster) is an open-source iOS keyboard
app built on the RIME input engine. It supports custom schemas, making it
possible to package CircleOne's layout and dictionaries as a schema that users
import into Hamster.

This phase requires **no App Store submission** for CircleOne itself. Users
install Hamster from the App Store and then import the CircleOne schema package.

### Deliverables

- RIME schema file (`circleone.schema.yaml`) defining the QWERTY layout with
  click consonant popups.
- Dictionary files for Southern African languages.
- Installation instructions (see `hamster-config/README.md`).

### Limitations

- Depends on Hamster being available on the App Store.
- Limited control over keyboard appearance and behaviour.
- Cannot ship a standalone branded icon or App Store listing.

---

## Phase 2: Standalone iOS Keyboard Extension

A native iOS Keyboard Extension written in Swift, using `UIInputViewController`
as the entry point. This gives full control over the keyboard UI, prediction
engine, and branding.

### Architecture

```
CircleOne/
  CircleOneApp/              -- Host app (settings, onboarding)
    Info.plist
    CircleOneApp.swift
  CircleOneKeyboard/         -- Keyboard Extension target
    Info.plist
    KeyboardViewController.swift   -- UIInputViewController subclass
    Views/
      KeyboardView.swift           -- SwiftUI or UIKit key layout
      KeyRow.swift
      ClickConsonantPopup.swift    -- Long-press popup for c, q, x
    Engine/
      PredictionEngine.swift       -- Word prediction and autocorrect
      DictionaryLoader.swift       -- Loads language-specific dictionaries
    Assets/
      Dictionaries/                -- .dict files per language
      Fonts/                       -- Custom font if needed
```

### Key Technical Decisions

- **UIInputViewController** is the only Apple-supported way to build a custom
  keyboard. It runs in a separate process with restricted memory (approx 50 MB).
- **No Full Access required.** CircleOne will not request the "Allow Full
  Access" permission because it does not need network access. This reinforces
  the privacy promise.
- **SwiftUI for key layout** where possible, falling back to UIKit for
  performance-critical rendering (key press response time must stay under 16ms).
- **Shared App Group** between the host app and the keyboard extension for
  storing user dictionary and preferences.

### Requirements

- Xcode 16+
- iOS 17+ deployment target
- Swift 6
- Apple Developer Program membership (for App Store distribution)

### Build

```bash
xcodebuild -workspace CircleOne.xcworkspace \
  -scheme CircleOneKeyboard \
  -configuration Release \
  -sdk iphoneos \
  build
```

### Privacy

Same policy as Android: no keystrokes transmitted, no analytics, no ads, no
network access from the keyboard extension. Apple's App Tracking Transparency
framework is not needed because there is no tracking.

---

## Timeline

| Phase | Status | Target |
|-------|--------|--------|
| Phase 1 -- Hamster schema | In progress | Q2 2026 |
| Phase 2 -- Native extension | Planning | Q4 2026 |
