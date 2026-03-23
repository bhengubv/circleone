# PUA Encoding Strategy

How CircleOne maps isiBheqe soHlamvu glyphs to Unicode Private Use Area codepoints.

Last updated: 2026-03-23

---

## Why PUA?

isiBheqe soHlamvu does not yet have assigned Unicode codepoints. The script is progressing through the Unicode proposal process via the Script Encoding Initiative at UC Berkeley, but this process takes years.

In the meantime, the Private Use Area (PUA) provides a standards-compliant way to encode characters that are not yet in Unicode. PUA codepoints are explicitly reserved by the Unicode Standard for private, application-specific use. They will never be assigned to other characters by the Unicode Consortium.

This means:
- Text encoded with PUA codepoints is valid Unicode
- It renders correctly with any font that maps those codepoints to glyphs
- It can be stored, transmitted, and processed by any Unicode-aware system
- It will NOT render correctly without the CircleOne font installed

---

## Codepoint Range

CircleOne uses the Basic Multilingual Plane PUA range:

```
U+E000 to U+F8FF  (6,400 codepoints available)
```

Current usage: approximately 251 codepoints (U+E000 to U+E1B2).

This leaves over 6,000 codepoints available for:
- Additional languages and their unique phonemes
- Punctuation and numerals specific to the script
- Diacritical marks and tone indicators
- Reserved space for unforeseen needs

The Supplementary Private Use Areas (U+F0000-FFFFD and U+100000-10FFFD) are available as overflow if the BMP PUA is ever exhausted, though this is extremely unlikely given the script's structure.

---

## Allocation Map

Codepoints are allocated in blocks of 8, with gaps reserved for future expansion within each category.

| Range           | Category                | Status    |
|-----------------|-------------------------|-----------|
| U+E000 - U+E00F | Pure vowels            | Assigned  |
| U+E010 - U+E05F | Plosive CV syllables   | Assigned  |
| U+E060 - U+E09F | Fricative CV syllables | Assigned  |
| U+E0A0 - U+E0BF | Nasal CV syllables     | Assigned  |
| U+E0C0 - U+E0DF | Liquid/Approximant CV  | Assigned  |
| U+E0E0 - U+E0EF | Implosive + prenasalised (mb) | Assigned |
| U+E0F0 - U+E11F | Prenasalised CV        | Assigned  |
| U+E120 - U+E13F | Affricates             | Assigned  |
| U+E140 - U+E19F | Click consonants       | Assigned  |
| U+E1A0 - U+E1AF | (reserved)             | Free      |
| U+E1B0 - U+E1BF | Syllabic nasals        | Assigned  |
| U+E1C0 - U+E1FF | (reserved for expansion)| Free     |
| U+E200 - U+E2FF | (future: Tshivenda retroflex, Xitsonga breathy) | Free |
| U+E300 - U+E3FF | (future: ejectives, Sotho-Tswana specific) | Free |
| U+E400 - U+E4FF | (future: punctuation, numerals) | Free |
| U+E500 - U+F8FF | (unallocated)          | Free      |

The full mapping of assigned codepoints is in `font/one-pua-map.csv`.

---

## Encoding Strategy: Composite vs Component

### Phase 1: Composite Encoding (Current)

Each syllable is assigned its own PUA codepoint. The font contains a pre-composed glyph for each codepoint.

```
U+E010  =  "ba"  (single glyph containing the full syllable)
U+E070  =  "sa"  (single glyph containing the full syllable)
U+E160  =  "qa"  (single glyph containing the full syllable)
```

Advantages:
- Simple, predictable, easy to debug
- Works identically across all rendering engines
- No dependency on OpenType feature support
- One codepoint = one glyph = one syllable

Disadvantages:
- Glyph count scales as (consonants x vowels)
- Adding a new consonant requires 5+ new glyphs
- Cannot represent novel combinations without new codepoints

### Phase 2: Component Encoding (Future)

Vowel bases and consonant modifiers are encoded separately. OpenType GSUB rules compose them into syllable blocks at render time.

```
U+E000 (vowel "a") + U+F000 (consonant modifier "b")
    --> GSUB ligature rule composes into syllable block "ba"
```

Advantages:
- Dramatically fewer base glyphs needed
- New consonant-vowel combinations work automatically
- Closer to how the script actually works (vowel shape + consonant features)
- Better aligned with eventual Unicode encoding

Disadvantages:
- Requires robust OpenType support in the rendering engine
- More complex to author and debug
- Some platforms may not support the required GSUB/GPOS features

The transition from composite to component encoding is planned but not yet scheduled. Both approaches can coexist during the transition period.

---

## Migration Path to Unicode

When the Unicode Consortium assigns official codepoints to isiBheqe soHlamvu, the migration process will be:

### Step 1: Dual Encoding

The font will map both PUA and official codepoints to the same glyphs. This allows a transition period where both old and new text render correctly.

### Step 2: Codepoint Translation Table

A mapping file (`pua-to-unicode.csv`) will be published with columns:
- PUA codepoint (old)
- Unicode codepoint (new)
- Character name

### Step 3: Text Conversion Tool

A script will convert existing PUA-encoded text to official Unicode codepoints. This is a simple find-and-replace operation since the mapping is 1:1.

### Step 4: Keyboard Update

Keyman and Rime configurations will be updated to output official Unicode codepoints instead of PUA codepoints. Users update their keyboard package; no other changes needed.

### Step 5: PUA Deprecation

After a reasonable transition period (suggested: 2 years), PUA mappings will be removed from new font releases. The conversion tool will remain available indefinitely.

### What Stays the Same

- Input sequences (keystrokes) do not change
- Glyph designs do not change
- Keyboard layouts do not change
- Only the underlying codepoint values change

This is why the PUA approach is sound: the mapping from input to codepoint is an abstraction layer. Changing the codepoint value underneath is transparent to the user.

---

## Reference Files

| File                        | Purpose                                     |
|-----------------------------|---------------------------------------------|
| `font/one-pua-map.csv`     | Complete codepoint-to-syllable mapping       |
| `rime/one.dict.yaml`       | Rime dictionary (generated from CSV)         |
| `rime/one.schema.yaml`     | Rime input schema                            |
| `unicode-proposal/`        | Unicode proposal draft and evidence           |

---

## Guidelines for Adding New Codepoints

1. Check `font/one-pua-map.csv` for the current highest assigned codepoint
2. Find the appropriate category block (see Allocation Map above)
3. Assign the next available codepoint within that block
4. If the block is full, use the next reserved range
5. Add the entry to `font/one-pua-map.csv` with: codepoint, input, IPA, glyph name
6. Run `scripts/generate_dict.py` to regenerate the Rime dictionary
7. Design the glyph in the font
8. Update `docs/syllable-inventory.md`

Never reuse or reassign a codepoint that has been previously allocated, even if the syllable it represented has been removed. This prevents data corruption in existing documents.
