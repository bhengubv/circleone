# CircleOne isiBheqe soHlamvu -- Keyman Keyboard

A Keyman keyboard for typing the isiBheqe soHlamvu featural syllabary. The script encodes articulatory features and can be used to write any human language.

## Overview

This keyboard maps standard Latin transliteration input to isiBheqe syllable glyphs. You type consonant-vowel sequences in Latin order (e.g., `ba`, `sha`, `nqa`) and the keyboard outputs the corresponding isiBheqe glyph from the CircleOne font.

The font file `one.ttf` must be installed alongside the keyboard for glyphs to render correctly.

## Prerequisites

- **Keyman** (free, open source) installed for your platform
- **CircleOne font** (`one.ttf`) -- included in this package

## Installation by Platform

### Windows

1. Download and install Keyman for Windows from <https://keyman.com/windows/>.
2. Download the `circleone.kmp` package file (or build it from source using Keyman Developer).
3. Double-click the `.kmp` file. Keyman will open and prompt you to install the keyboard.
4. Confirm the installation. The CircleOne font will be installed automatically.
5. Open Keyman Configuration and verify that "CircleOne isiBheqe" appears in your keyboard list.
6. Use the Keyman icon in the system tray to switch to the CircleOne keyboard.

### macOS

1. Download and install Keyman for macOS from <https://keyman.com/mac/>.
2. Open Keyman and use the built-in package installer to add the `circleone.kmp` file.
3. The font will be installed into your user font library automatically.
4. Switch to the CircleOne keyboard using the Keyman menu bar icon or the macOS input source menu.

### Linux

1. Install Keyman for Linux. On Ubuntu/Debian:
   ```
   sudo add-apt-repository ppa:keymanapp/keyman
   sudo apt update
   sudo apt install keyman
   ```
   For other distributions, see <https://keyman.com/linux/>.
2. Install the keyboard package:
   ```
   km-package-install circleone.kmp
   ```
3. Copy `one.ttf` to your local fonts directory if it was not installed automatically:
   ```
   mkdir -p ~/.local/share/fonts
   cp one.ttf ~/.local/share/fonts/
   fc-cache -f
   ```
4. Add the CircleOne keyboard in your IBus or Fcitx input method settings.

### Android

1. Install **Keyman for Android** from the Google Play Store.
2. Open Keyman and tap the keyboard icon to access settings.
3. Tap "Install keyboard" and browse or search for the CircleOne package. Alternatively, download the `.kmp` file and open it with Keyman.
4. Enable "Keyman" as an input method in your device's keyboard settings (Settings > System > Languages & input > On-screen keyboard).
5. Install the `one.ttf` font on your device. Some Android versions support this natively; otherwise use a font manager app.
6. Switch to the Keyman keyboard when typing and select "CircleOne isiBheqe" from the keyboard list.

### iOS

1. Install **Keyman for iPhone/iPad** from the App Store.
2. Open the Keyman app and tap "Settings" then "Install keyboard."
3. Add the CircleOne keyboard package.
4. Go to the iOS Settings app > General > Keyboard > Keyboards > Add New Keyboard and enable "Keyman."
5. The font is embedded within the Keyman app and will render automatically inside Keyman-aware apps. For system-wide rendering, the font must be installed via a configuration profile.
6. When typing, switch to the Keyman keyboard and select "CircleOne isiBheqe."

### Web (KeymanWeb)

1. Visit <https://keymanweb.com/> or host KeymanWeb on your own server.
2. Load the compiled CircleOne keyboard (`.js` file) into the KeymanWeb engine.
3. Ensure the `one.ttf` font is available as a web font. Add a CSS `@font-face` rule:
   ```css
   @font-face {
     font-family: 'CircleOne';
     src: url('one.ttf') format('truetype');
   }
   ```
4. Configure KeymanWeb to use the CircleOne font for the keyboard's output area.
5. Users can then select "CircleOne isiBheqe" from the KeymanWeb keyboard picker.

## Input Method

Type Latin transliteration sequences. The keyboard automatically converts them to isiBheqe glyphs:

| Input | Output Glyph | Description |
|-------|-------------|-------------|
| `a`   | (vowel a)   | Pure vowel a |
| `ba`  | (ba glyph)  | Consonant b + vowel a |
| `sha` | (sha glyph) | Consonant sh + vowel a |
| `nqa` | (nqa glyph) | Nasal palatal click + vowel a |
| `-m`  | (syllabic m) | Syllabic nasal m |

Longer consonant sequences are matched first (e.g., `sh` before `s`, `bh` before `b`, `ng` before `n`). This means you can type naturally without worrying about ambiguity -- the keyboard resolves the longest matching consonant automatically.

### Syllabic Nasals

To type syllabic nasals (m, n, ng as standalone syllables), prefix with a hyphen:

- `-m` for syllabic m
- `-n` for syllabic n
- `-ng` for syllabic ng

## Building from Source

If you have Keyman Developer installed:

1. Open `circleone.kmn` in Keyman Developer.
2. Compile the keyboard (Build > Compile Keyboard).
3. Package it using the `circleone.kps` source file (Build > Package for Distribution).
4. The resulting `circleone.kmp` file can be distributed and installed on any platform.

## PUA Codepoint Range

This keyboard uses Unicode Private Use Area codepoints U+E000 through U+E2A4. These codepoints are only meaningful when paired with the CircleOne font. Text will appear as blank boxes or placeholder glyphs in any other font.

## Languages Supported

All of them. isiBheqe soHlamvu encodes articulatory features, not language-specific orthography. Any human language can be written with it.

## License

See the LICENSE file in the repository root.
