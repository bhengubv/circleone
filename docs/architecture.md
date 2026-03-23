# CircleOne System Architecture

Status: Living document
Last updated: 2026-03-23

---

## Overview

CircleOne is a keyboard and input method system for isiBheqe soHlamvu, a featural syllabary designed for siNtu (Bantu) languages. The project uses a hybrid deployment strategy that prioritises universal reach first, then builds toward a branded native experience.

The system has three core components that all implementations share:

1. **one.ttf** -- the isiBheqe soHlamvu font, using PUA codepoints
2. **Transliteration logic** -- mapping Latin keystrokes to PUA glyphs
3. **Syllable dictionary** -- the complete inventory of valid syllables

Every keyboard platform (Keyman, HeliBoard, Hamster) consumes these same shared assets, ensuring consistency across all devices and operating systems.

---

## Phased Deployment

### Phase 1: Keyman (Universal)

Keyman is the primary deployment target. It runs on Windows, macOS, Linux, Android, and iOS from a single keyboard package (.kmp file).

Advantages:
- Single codebase covers all platforms
- Established distribution channel (keyman.com)
- Active community and support infrastructure
- No app store approval required for keyboard updates

Limitations:
- Limited visual customisation
- No control over keyboard chrome or theme
- Dependent on Keyman runtime being installed

### Phase 2: HeliBoard (Android) + Hamster (iOS)

Native open-source keyboards provide a branded experience with full control over layout, themes, and interaction patterns.

**HeliBoard** (Android):
- Open-source AOSP keyboard fork
- Custom layout definitions via JSON
- Supports popup keys for click consonant clusters
- Source: github.com/Helium314/HeliBoard

**Hamster** (iOS):
- Open-source iOS keyboard built on the Rime input engine
- Shares the same Rime schema used for desktop input
- Source: github.com/imfuxiao/Hamster

---

## Input Flow

The input pipeline transforms Latin keystrokes into rendered isiBheqe soHlamvu glyphs. Two input orders are supported, both producing identical output.

### Keyman Path (consonant-first / Latin order)

```
User types "ba" (Latin order)
    |
    v
Keyman rule engine
    |  Matches rule: "b" + "a" -> U+E010
    v
PUA codepoint U+E010 inserted into text buffer
    |
    v
one.ttf renders the composite syllable glyph for "ba"
```

### Rime Path (vowel-first / isiBheqe order)

```
User types "ab" (vowel-first order)
    |
    v
Rime speller (algebra rules normalise input)
    |  Looks up "ab" in one.dict.yaml
    v
PUA codepoint U+E010 inserted into text buffer
    |
    v
one.ttf renders the composite syllable glyph for "ba"
```

Both paths produce the same PUA codepoint and the same rendered glyph. The difference is purely in keystroke order.

---

## Font Strategy

### Phase 1: Composite Glyphs

Each CV (consonant-vowel) syllable maps to a single PUA codepoint. The font contains a pre-composed glyph for every syllable in the inventory.

- Simple to implement and debug
- Every syllable = one codepoint = one glyph
- No OpenType layout rules required
- Trade-off: larger glyph count (currently ~290 glyphs)

### Phase 2: OpenType Composition

Consonant and vowel components are stored as separate glyphs. OpenType GSUB/GPOS rules compose them at render time.

- Smaller glyph count (vowel shapes + consonant strokes)
- New syllables can be formed without adding new glyphs
- Requires OpenType layout table authoring
- More complex to debug across rendering engines

The Phase 1 approach is used initially because it is reliable across all platforms and rendering engines. Phase 2 composition will be introduced once the glyph inventory stabilises and cross-platform OpenType support is verified.

---

## PUA Encoding

All glyphs are mapped to the Unicode Private Use Area (PUA), range U+E000 to U+F8FF (6400 available slots).

Current allocation:

| Range         | Category                  | Count |
|---------------|---------------------------|-------|
| U+E000-E007   | Pure vowels               | 8     |
| U+E010-E0EC   | CV syllables (simple)     | ~180  |
| U+E0F0-E114   | Prenasalised syllables    | ~30   |
| U+E120-E12C   | Affricates                | 10    |
| U+E140-E19C   | Click consonants          | ~75   |
| U+E1B0-E1B2   | Syllabic nasals           | 3     |

The full mapping is defined in `font/one-pua-map.csv`.

See `docs/pua-encoding.md` for the complete encoding strategy and Unicode migration path.

---

## Component Diagram

```
+------------------------------------------------------------------+
|                        Shared Assets                              |
|                                                                   |
|  +------------------+  +-------------------+  +----------------+  |
|  | font/one.ttf     |  | font/one-pua-map  |  | Syllable       |  |
|  | (PUA glyphs)     |  | .csv              |  | Inventory      |  |
|  +--------+---------+  +--------+----------+  +-------+--------+  |
|           |                     |                      |          |
+------------------------------------------------------------------+
            |                     |                      |
   +--------v---------+  +-------v--------+  +-----------v--------+
   |                  |  |                |  |                    |
   |  Keyman          |  |  Rime Engine   |  |  HeliBoard         |
   |  (.kmn rules)    |  |  (.schema.yaml |  |  (layout JSON      |
   |                  |  |   + .dict.yaml)|  |   + word list)     |
   |  Latin order     |  |  Vowel-first   |  |  Latin order       |
   |  input           |  |  input         |  |  input             |
   |                  |  |                |  |                    |
   +------------------+  +-------+--------+  +--------------------+
                                 |
                         +-------v--------+
                         |                |
                         |  Hamster (iOS) |
                         |  (uses Rime    |
                         |   schema)      |
                         |                |
                         +----------------+
```

### Data Flow Between Components

1. **one-pua-map.csv** is the single source of truth for all syllable-to-codepoint mappings
2. **generate_dict.py** reads the CSV and produces `rime/one.dict.yaml`
3. **rime_to_heliboard_dict.py** converts the Rime dictionary to HeliBoard word list format
4. The Keyman keyboard rules are authored manually from the same CSV reference
5. **one.ttf** contains glyphs at codepoints defined in the CSV

### Build Pipeline

```
font/one-pua-map.csv
    |
    +---> scripts/generate_dict.py ---> rime/one.dict.yaml
    |                                       |
    |                                       +---> Hamster (iOS)
    |                                       +---> Rime (desktop)
    |
    +---> scripts/rime_to_heliboard_dict.py ---> android/heliboard word list
    |
    +---> Manual reference for keyman/*.kmn
    |
    +---> Font designer reference for font/one.ttf glyph mapping
```

---

## Technology Stack

| Component       | Technology              | License     |
|-----------------|-------------------------|-------------|
| Font            | OpenType/TrueType (.ttf)| MIT         |
| Keyman keyboard | Keyman Developer (.kmn) | MIT         |
| Rime schema     | YAML + librime          | MIT         |
| HeliBoard       | Android / Java+Kotlin   | GPL-3.0     |
| Hamster         | iOS / Swift             | GPL-3.0     |
| Build scripts   | Python 3.11+            | MIT         |
| CI/CD           | GitHub Actions          | --          |

---

## Design Principles

1. **One source of truth**: The PUA map CSV drives all downstream artefacts.
2. **Platform parity**: Every platform renders the same glyphs for the same input.
3. **Vowel-first is native**: The Rime path preserves the script's natural input order. Keyman supports Latin order for accessibility.
4. **Incremental extension**: Adding a new language means adding phoneme rows to the CSV and regenerating dictionaries. No structural changes required.
5. **Unicode-ready**: PUA encoding is a stepping stone. The codepoint mapping is designed for straightforward migration when Unicode codepoints are assigned.
