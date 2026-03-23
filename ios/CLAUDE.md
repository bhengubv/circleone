# CLAUDE.md — CircleOne iOS Build (Mac Session)

## What This Is

CircleOne is a keyboard app for the isiBheqe soHlamvu featural syllabary. This iOS target produces a custom keyboard extension that converts Latin transliteration input into isiBheqe PUA glyphs.

The font, PUA mapping, and keyboard rules were built on a Windows machine. This Mac session handles the Xcode build, signing, and App Store submission.

## Project Structure

```
ios/
  CircleOne/
    project.yml                    -- XcodeGen project definition
    generate-xcodeproj.sh          -- Run this first to create .xcodeproj
    CircleOneApp/                  -- Host app (SwiftUI, onboarding)
      CircleOneApp.swift           -- @main entry point
      ContentView.swift            -- Settings, setup instructions, about
      Info.plist
    CircleOneKeyboard/             -- Keyboard Extension target
      KeyboardViewController.swift -- UIInputViewController (the keyboard)
      Info.plist                   -- Extension config (RequestsOpenAccess=false)
      CircleOneKeyboard.entitlements
      Views/
        KeyboardView.swift         -- QWERTY layout with globe key
      Engine/
        TransliterationEngine.swift -- Latin → PUA transliteration
```

## First-Time Setup

```bash
# 1. Install XcodeGen
brew install xcodegen

# 2. Generate the Xcode project
cd ios/CircleOne
chmod +x generate-xcodeproj.sh
./generate-xcodeproj.sh

# 3. Open in Xcode
open CircleOne.xcodeproj
```

## Before Building

1. **Set your Apple Development Team** in Xcode → CircleOneApp target → Signing & Capabilities
2. **Bundle ID**: `za.co.thegeek.circleone` (host app), `za.co.thegeek.circleone.keyboard` (extension)
3. **Font**: `font/one.ttf` must exist. If not, generate it:
   ```bash
   brew install fontforge
   cd font && fontforge -script generate_glyphs.py
   ```

## Key Files from the Windows Build

| File | Purpose |
|------|---------|
| `font/one.ttf` | The isiBheqe font (471 PUA glyphs) |
| `font/one-pua-map.csv` | Codepoint ↔ Latin input mapping |
| `font/generate_glyphs.py` | Font generator (FontForge script) |
| `font/samples/` | Reference PDF link for glyph shapes |

## How the Keyboard Works

1. User types Latin keys (e.g. `b`, `a`)
2. `TransliterationEngine` buffers consonant keys and waits for a vowel
3. When a vowel completes a syllable, the engine:
   - Deletes the buffered Latin characters from the text proxy
   - Inserts the corresponding PUA character (rendered by one.ttf)
4. The CSV mapping (`one-pua-map.csv`) drives all lookups

## What Needs Work

### Must Fix Before Submission
- [ ] **Long-press popups** for click consonants (c/q/x → ch/gc/nc etc.)
- [ ] **Shift/caps** support
- [ ] **Number/symbol** layer
- [ ] **isiBheqe glyph preview** — show the glyph being formed as user types consonants
- [ ] **App icon** (1024x1024, no alpha)
- [ ] **Screenshots** on real device for App Store listing

### Nice to Have
- [ ] Haptic feedback on key press
- [ ] Key press sound (optional)
- [ ] Dark mode support for keyboard
- [ ] iPad layout
- [ ] Autocomplete/word prediction using bundled dictionary

## App Store Submission

Full guide at `docs/APP_STORE_SUBMISSION.md`. Key points:

1. Apple Developer Program required ($99/year)
2. Must have globe key (Next Keyboard) — already implemented
3. `RequestsOpenAccess = false` — no network, no scary permission prompt
4. Host app must have meaningful content — onboarding and setup instructions are included
5. Archive → Distribute → App Store Connect → Submit for Review

## Copyright

(c) 2026 The Other Bhengu (Pty) Ltd t/a The Geek.
isiBheqe soHlamvu script created by Mqondisi Bhebhe.

## Privacy

No keystrokes transmitted. No analytics. No ads. No network access from keyboard extension.
