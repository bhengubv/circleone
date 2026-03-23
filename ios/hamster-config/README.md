# CircleOne on iOS via Hamster

## What is Hamster?

[Hamster](https://github.com/imfuxiao/Hamster) is a free, open-source iOS
keyboard app built on the RIME input method engine. It allows users to import
custom keyboard schemas, which is how CircleOne delivers its layout and
dictionaries on iOS without a standalone App Store submission.

---

## Installation Instructions

### Step 1: Install Hamster

1. Open the App Store on your iPhone or iPad.
2. Search for "Hamster" (by imfuxiao).
3. Install the app.

### Step 2: Enable the Hamster Keyboard

1. Open **Settings** on your device.
2. Go to **General > Keyboard > Keyboards > Add New Keyboard**.
3. Select **Hamster** from the list of third-party keyboards.
4. You do NOT need to enable "Allow Full Access". CircleOne does not require
   network access.

### Step 3: Import the CircleOne Schema Package

1. Download the CircleOne schema package. You will receive a `.zip` file
   containing:
   - `circleone.schema.yaml` -- the keyboard layout definition
   - `circleone.dict.yaml` -- dictionary files for Southern African languages
   - `circleone.custom.yaml` -- appearance and behaviour overrides

2. Open the **Hamster** app (the host app, not the keyboard).

3. Tap **Input Schema** (or the equivalent menu item for importing schemas).

4. Tap **Import** and select the CircleOne `.zip` file from your Files app.

5. Wait for the import to complete. Hamster will compile the schema and
   dictionary -- this may take 10-30 seconds depending on dictionary size.

6. Once imported, tap **CircleOne** in the schema list to activate it.

### Step 4: Switch to CircleOne

1. Open any app where you can type (Messages, Notes, etc.).
2. Tap the globe icon on the keyboard to cycle through your enabled keyboards.
3. Select **Hamster**. The CircleOne layout will appear.

---

## Schema Package Contents

```
circleone-hamster.zip/
  circleone.schema.yaml       -- Layout definition (QWERTY + click consonants)
  circleone.dict.yaml         -- Combined dictionary for all supported languages
  circleone.custom.yaml       -- Key appearance, popup behaviour, theme
```

### circleone.schema.yaml

Defines the QWERTY key layout with long-press popups for click consonants:

- **c** long-press: c, ch, gc, nc, nch, ngc (dental clicks)
- **q** long-press: q, qh, gq, nq, nqh, ngq (alveolar clicks)
- **x** long-press: x, xh, gx, nx, nxh, ngx (lateral clicks)

### circleone.dict.yaml

Word lists for: isiZulu, isiXhosa, isiNdebele, siSwati, Sesotho, Setswana,
Sepedi, Tshivenda, Xitsonga, Afrikaans, and South African English.

### circleone.custom.yaml

Overrides for visual appearance to match CircleOne branding (key colours, font
size, popup style).

---

## Updating the Schema

When a new version of the CircleOne schema is released:

1. Download the updated `.zip` file.
2. Open Hamster and re-import. The existing schema will be replaced.
3. The dictionary will be recompiled automatically.

---

## Troubleshooting

**Keyboard does not appear after adding Hamster.**
Restart your device. iOS occasionally requires a restart before third-party
keyboards appear in the keyboard switcher.

**Click consonant popups are not showing.**
Ensure the CircleOne schema is the active schema inside the Hamster app. Open
Hamster and verify that "CircleOne" is selected (not the default Hamster
schema).

**Dictionary suggestions are not appearing.**
After importing, Hamster needs to compile the dictionary. Open Hamster, go to
the schema list, and check that compilation is complete (no spinner or progress
indicator).

---

## Privacy

Hamster is open source and does not transmit keystrokes. The CircleOne schema
runs entirely within Hamster's local process. No data leaves your device.
Do not enable "Allow Full Access" -- it is not needed and CircleOne is designed
to work without it.
