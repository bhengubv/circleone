# CircleOne Font -- isiBheqe SoNtu Script

## Overview

The CircleOne font encodes the isiBheqe SoNtu writing system into a TrueType font
using Unicode Private Use Area (PUA) codepoints. Each glyph represents a complete
CV (consonant-vowel) syllable, a pure vowel, or a syllabic nasal, rendered in
the geometric circle-and-line style of isiBheqe.

The keyboard name is CircleOne, written symbolically as: triangle-right, circle, triangle-left.

## PUA Strategy

All glyphs are assigned codepoints in the Basic Multilingual Plane PUA range
(U+E000 through U+E1FF). The allocation is:

| Range         | Content                          | Count |
|---------------|----------------------------------|-------|
| E000 - E007   | Pure vowels + long vowels        | 8     |
| E010 - E13F   | CV syllables (plosives thru affricates) | ~160 |
| E140 - E19F   | CV syllables (clicks: c, q, x families) | ~60  |
| E1B0 - E1B2   | Syllabic nasals (amaQanda)       | 3     |

Gaps of 2-4 codepoints are left between consonant groups to allow future
expansion (e.g., additional allophonic variants or tone markers).

The canonical mapping is stored in `one-pua-map.csv`.

## Tools Required

- **FontForge** (version 20230101 or later) -- https://fontforge.org
- **Python 3** (bundled with FontForge's embedded interpreter)

## Build Workflow

1. Install FontForge with Python scripting support.
2. Run the generator script:

```
fontforge -script generate_glyphs.py
```

3. Output files:
   - `one.ttf` -- the compiled TrueType font
   - `one-pua-map.csv` -- the PUA codepoint-to-glyph mapping (also regenerated)

4. Install `one.ttf` on your system or load it into the CircleOne keyboard engine.

## Glyph Architecture

Each glyph is a composite of two base components:

- **Vowel shape** -- the outer form (circle, half-circle, triangle, etc.)
  positioned according to isiBheqe conventions:
  - isoka (a) -- full circle
  - iphambili (e) -- right half-circle
  - intombi (i) -- downward triangle
  - imuva (o) -- left half-circle
  - umkhonto (u) -- upward triangle

- **Consonant grapheme** -- the inner mark or line pattern that identifies the
  consonant. For pure vowels, no consonant component is added.

- **Syllabic nasals (amaQanda)** -- standalone circles with nasal markers,
  used for syllable-initial m, n, ng without a following vowel.

## File Inventory

| File                  | Purpose                                    |
|-----------------------|--------------------------------------------|
| generate_glyphs.py    | FontForge Python script to build the font  |
| one-pua-map.csv       | PUA codepoint mapping (input, IPA, name)   |
| one.ttf               | Generated font (after running the script)  |
| README.md             | This file                                  |
