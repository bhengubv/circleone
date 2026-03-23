# Unicode Proposal: isiBheqe soHlamvu (Ditema tsa Dinoko)

**STATUS: DRAFT -- Not yet submitted**

This document is a working scaffold for a formal Unicode L2 submission. It will be refined with input from linguists, the script creator (Mqondisi Bhebhe), and the Script Encoding Initiative at UC Berkeley before submission.

---

## 1. Script Identification

**Script name**: isiBheqe soHlamvu
**International name**: Ditema tsa Dinoko
**ISO 15924 code**: (to be assigned)
**Script type**: Featural syllabary
**Directionality**: Left to right
**Creator**: Mqondisi Bhebhe
**Region of origin**: Southern Africa

---

## 2. Script Type and Structure

isiBheqe soHlamvu is a featural syllabary designed for the siNtu (Bantu) languages of Southern Africa. Each character represents a complete syllable (typically consonant + vowel). The visual form of each character encodes articulatory phonetic features:

- **Vowel shapes** are geometric primitives encoding tongue height, frontness/backness, and lip rounding
- **Consonant strokes** indicate place and manner of articulation
- The vowel shape forms the structural base of each syllable block, with consonant features layered onto it

This is analogous in principle to Korean Hangul, where individual jamo (feature-encoding letters) compose into syllable blocks. The key difference is that isiBheqe soHlamvu characters are designed as unified visual forms rather than spatially arranged component letters.

---

## 3. Languages Served

isiBheqe soHlamvu is designed to write all siNtu (Bantu) languages of Southern Africa. The script's featural design means that any siNtu language can be written by mapping its phonemes to the existing character components.

Primary target languages:

| Language    | ISO 639 | Speakers (approx.) | Country        |
|-------------|---------|---------------------|----------------|
| isiZulu     | zu      | 12 million          | South Africa   |
| isiXhosa    | xh      | 8 million           | South Africa   |
| Sesotho     | st      | 6 million           | South Africa, Lesotho |
| Setswana    | tn      | 5 million           | South Africa, Botswana |
| Xitsonga    | ts      | 4 million           | South Africa, Mozambique |
| siSwati     | ss      | 2.5 million         | Eswatini, South Africa |
| Tshivenda   | ve      | 1.3 million         | South Africa   |
| isiNdebele  | nr      | 1.1 million         | South Africa   |
| Sepedi      | nso     | 4.6 million         | South Africa   |

Total speaker population for primary languages: approximately 44.5 million.

The script is extensible to additional Bantu languages beyond Southern Africa, including Shona, Chichewa, Swahili, and others, though these are not the initial focus.

---

## 4. Character Inventory

The complete character inventory is provided in the accompanying file `character-inventory.csv`. A summary:

| Category              | Count | Description                                    |
|-----------------------|-------|------------------------------------------------|
| Vowels                | 8     | 5 core + 3 extended (long vowels)             |
| Plosive syllables     | 60    | Voiced, voiceless, aspirated, implosive        |
| Fricative syllables   | 40    | Including lateral fricatives                   |
| Nasal syllables       | 20    | Bilabial, alveolar, velar, palatal nasals     |
| Liquid/Approximant    | 20    | l, r, w, y                                     |
| Affricate syllables   | 10    | j (dz), tsh                                    |
| Prenasalised syllables| 30    | mb, nd, nt, nk, nj, ntsh                      |
| Click syllables       | 60    | Dental, palatal, lateral (plain/asp/vcd/nasal)|
| Syllabic nasals       | 3     | m, n, ng                                       |
| **Total**             | **~251** |                                             |

Note: The composite encoding used in the current PUA implementation assigns one codepoint per syllable. A component-based encoding (separate codepoints for vowel bases and consonant modifiers with composition rules) would result in a smaller character set. The optimal encoding strategy for the Unicode submission is under discussion.

---

## 5. Encoding Strategy Options

Two encoding approaches are under consideration for the formal proposal:

### Option A: Composite (one codepoint per syllable)

- Each CV syllable receives its own codepoint
- Simple, no composition rules needed
- Approximately 250-400 codepoints depending on language coverage
- Precedent: Yi syllabary (U+A000-A4CF, 1,165 characters)

### Option B: Component (separate vowel + consonant codepoints with composition)

- Vowel bases and consonant modifiers encoded separately
- OpenType or Unicode composition rules combine them into syllable blocks
- Approximately 30-50 base codepoints + composition rules
- Precedent: Korean Hangul jamo (U+1100-11FF) with composition to syllable blocks
- More extensible but requires robust composition support

The preferred approach will be determined in consultation with the Unicode Technical Committee and the Script Encoding Initiative.

---

## 6. Proposed Codepoint Range

**Requested block**: (to be determined by UTC)
**Size required**: 256-512 codepoints (Option A) or 64-128 codepoints (Option B)
**Preferred plane**: Supplementary Multilingual Plane (SMP, Plane 1) or Basic Multilingual Plane (BMP) if space is available

---

## 7. Evidence of Use

Evidence supporting the encoding of isiBheqe soHlamvu:

### 7.1 Script Documentation
- Formal script description by Mqondisi Bhebhe (see `evidence/` directory)
- Published articles on the script's design and phonological basis
- isibheqe.org.za -- official website

### 7.2 Active Development
- Open-source keyboard implementation (this repository)
- Font development in progress
- Integration with Keyman, Rime, HeliBoard, and Hamster input platforms

### 7.3 Community Interest
- (To be documented: workshops, educational programmes, social media adoption)

### 7.4 Academic Engagement
- Script Encoding Initiative at UC Berkeley is aware of the script
- (To be documented: formal letters of support from linguists and institutions)

### 7.5 Precedent
- Other recently encoded African scripts: Adlam (U+1E900, encoded 2016), Osage (U+104B0, encoded 2016), Garay (encoded 2024)
- These demonstrate the UTC's willingness to encode modern scripts with active communities

---

## 8. Relationship to Existing Scripts

isiBheqe soHlamvu is an independent script creation. It is not derived from or related to:
- Latin script
- Arabic script
- Any existing African script (N'Ko, Vai, Bamum, Adlam, etc.)

The script draws on indigenous Southern African geometric artistic traditions but is a wholly original writing system designed from first principles around siNtu phonology.

---

## 9. Contact Information

**Script creator**: Mqondisi Bhebhe
**Technical contact**: The Geek Network (dev@thegeeknetwork.co.za)
**Repository**: github.com/bhengubv/circleone
**Website**: isibheqe.org.za

---

## 10. Required Attachments (To Be Completed)

- [ ] Character inventory table (see `character-inventory.csv`)
- [ ] Representative font showing all proposed characters
- [ ] Text samples in at least 3 target languages
- [ ] Letters of support from linguists
- [ ] Letters of support from language communities
- [ ] Formal description of script rules and composition
- [ ] Bibliography of published references

---

## References

1. Bhebhe, M. isiBheqe soHlamvu script documentation. isibheqe.org.za.
2. The Unicode Standard, Version 15.1. Unicode Consortium, 2023.
3. Script Encoding Initiative. UC Berkeley Department of Linguistics.
4. Everson, M. & Bhebhe, M. (anticipated). Proposal to encode isiBheqe soHlamvu in the Unicode Standard.

---

*This document is a DRAFT scaffold. It will be substantially revised before formal submission to the Unicode Technical Committee.*
