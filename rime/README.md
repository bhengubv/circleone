# CircleOne Rime Schema -- isiBheqe soHlamvu Input Method

This directory contains a [Rime](https://rime.im/) input method schema for
isiBheqe soHlamvu, a featural script for siNtu (Bantu) languages.

The schema uses **vowel-first input order**: you type the vowel key first,
then the consonant key(s). For example, to produce the syllable "ba", you
type `ab` (vowel /a/ followed by consonant /b/).

## Files

| File | Purpose |
|------|---------|
| `one.schema.yaml` | Schema definition (engine, speller, translator, key bindings) |
| `one.dict.yaml` | Character dictionary mapping input sequences to isiBheqe glyphs |
| `default.yaml` | Deployment configuration referencing the `one` schema |

## Prerequisites

You need an isiBheqe soHlamvu font installed on your system for the output
characters to render correctly. See the `font/` directory in this repository.

## Installation

### Windows (Weasel / Weasel)

1. Install [Weasel](https://rime.im/download/) if you have not already.
2. Copy all three YAML files from this directory into Weasel's user data folder.
   The default location is:
   ```
   %APPDATA%\Rime
   ```
3. Right-click the Weasel tray icon and select "Deploy" (or press
   Ctrl+grave accent while Weasel is active).
4. Select the input schema from the Weasel schema menu. It will appear as
   "isiBheqe".

### macOS (Squirrel)

1. Install [Squirrel](https://rime.im/download/) if you have not already.
2. Copy all three YAML files into Squirrel's user data folder:
   ```
   ~/Library/Rime/
   ```
3. Open Squirrel preferences or run "Deploy" from the input method menu bar
   icon.
4. Select "isiBheqe" from the schema list.

### Linux (ibus-rime / fcitx-rime)

1. Install `ibus-rime` or `fcitx-rime` from your distribution's package
   manager. For example:
   ```bash
   # Debian / Ubuntu
   sudo apt install ibus-rime

   # Arch Linux
   sudo pacman -S ibus-rime

   # Fedora
   sudo dnf install ibus-rime
   ```
2. Copy all three YAML files into the Rime user data directory:
   ```
   ~/.local/share/fcitx5/rime/    # fcitx5-rime
   ~/.config/ibus/rime/           # ibus-rime
   ```
3. Run the deploy command:
   ```bash
   rime_deployer --build ~/.config/ibus/rime/
   ```
   Or restart the input method framework and select "Deploy" from the Rime
   menu.
4. Add "isiBheqe" to your active input schemas.

### iOS (Hamster)

1. Install [Hamster](https://apps.apple.com/app/id6446617683) from the App
   Store.
2. In Hamster's settings, navigate to the schema import section.
3. Import the three YAML files (you can share them via AirDrop, Files, or
   iCloud Drive).
4. Deploy the configuration within Hamster.
5. Switch to the "isiBheqe" schema from the keyboard's schema selector.

### Android (Trime)

1. Install [Trime](https://github.com/osfans/trime/releases) from GitHub
   releases or F-Droid.
2. Copy all three YAML files into Trime's Rime user data directory. The
   default location is:
   ```
   /sdcard/rime/
   ```
   You can also use Trime's built-in file manager to import schemas.
3. Open Trime settings and run "Deploy".
4. Select "isiBheqe" from the available schemas.

## Usage

Once deployed, switch to the isiBheqe input method. Type vowel-first
sequences and the candidate window will show the corresponding isiBheqe
soHlamvu characters. Press Space or the number key next to a candidate to
confirm it.

Toggle between isiBheqe and ASCII mode with Shift (left Shift for inline
ASCII, right Shift to commit current text).

## Troubleshooting

- **Characters appear as boxes or question marks**: Ensure the isiBheqe
  soHlamvu font is installed and active. The font must cover the Private Use
  Area codepoints used by this schema.
- **Schema does not appear after deploy**: Verify that `default.yaml`
  includes `one` in the `schema_list`. Check the Rime log files for error
  messages.
- **No candidates shown**: Confirm that `one.dict.yaml` is in the same
  directory as the schema file and that the `name` field in the dictionary
  matches the `dictionary` field in the schema.

## Learn More

- isiBheqe soHlamvu: https://isibheqe.org.za
- Rime documentation: https://github.com/rime/home/wiki
- CircleOne project: https://github.com/bhengubv/circleone
