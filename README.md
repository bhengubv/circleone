# &#9655;&#9711;&#9665; CircleOne

**The keyboard that writes every language. Built from Africa. Belonging to everyone.**

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![GitHub](https://img.shields.io/badge/GitHub-bhengubv%2Fcircleone-181717?logo=github)](https://github.com/bhengubv/circleone)

---

## What is isiBheqe soHlamvu?

isiBheqe soHlamvu (also known internationally as Ditema tsa Dinoko) is a **featural syllabary** designed for the siNtu (Bantu) languages of Southern Africa. Unlike the Latin alphabet imposed during colonisation, isiBheqe soHlamvu is built from the phonological structure of these languages themselves. Each symbol encodes articulatory features -- the shape of a character tells you *how* the sound is produced. Consonant strokes indicate place and manner of articulation, while vowel shapes represent tongue position and lip rounding. The result is a writing system where literacy in one siNtu language transfers directly to reading any other.

The script was created by Mqondisi Bhebhe and draws on indigenous Southern African geometric traditions. It is not a decorative novelty -- it is a fully functional writing system with consistent rules, a compact character set, and a deep alignment with the phonology it represents. isiBheqe soHlamvu restores the act of writing to the linguistic logic of the people who speak these languages, rather than forcing their sounds through the constraints of a European alphabet. The script is currently progressing through the Unicode proposal process via the Script Encoding Initiative at UC Berkeley.

---

## How Input Works

isiBheqe soHlamvu input follows a **vowel-first** paradigm. This is the opposite of Latin transliteration, where you typically type a consonant followed by a vowel (e.g., "ba", "zi", "nku").

In this keyboard:

1. **Type the vowel first** -- this selects the base syllable shape.
2. **Then type the consonant modifier(s)** -- these attach to the vowel to form the complete syllable.

This mirrors how the script actually works: the vowel is the structural foundation of each syllable block, and consonant features are layered onto it.

```
Latin order:    b + a  =  "ba"
CircleOne order: a + b  =  [syllable block for "ba"]
```

This approach feels unfamiliar for the first few minutes, then becomes natural -- because it matches the phonological reality of siNtu syllable structure.

---

## The 8 Vowel Shapes

The vowel system is built on geometric primitives that encode tongue height, frontness/backness, and lip rounding.

| Shape | IPA  | Name       | Description                        |
|-------|------|------------|------------------------------------|
| `a`   | /a/  | **ikhaya** (home)     | Open central vowel                |
| `e`   | /e/  | **iphambili** (front)  | Close-mid front unrounded vowel   |
| `E`   | /ɛ/  | **intombi** (maiden)   | Open-mid front unrounded vowel    |
| `i`   | /i/  | **isoka** (suitor)     | Close front unrounded vowel       |
| `o`   | /o/  | **imuva** (back)       | Close-mid back rounded vowel      |
| `O`   | /ɔ/  | **umkhonto** (spear)   | Open-mid back rounded vowel       |
| `u`   | /u/  | **ubuso** (face)       | Close back rounded vowel          |
| `N`   | /n/  | **indilinga** (circle) | Nasal syllabic (syllabic nasal)   |

The geometric shapes are directional: triangles pointing forward or back, circles for nasals, and open or closed forms for vowel height. Each shape is immediately recognisable once learned, and the system is internally consistent across all supported languages.

---

## Supported Languages

**All of them.**

isiBheqe soHlamvu encodes articulatory features -- how sounds are physically produced by the human vocal apparatus. It does not encode language-specific orthography. Any language whose phonology includes the consonant and vowel features represented in the script can be written with it. This includes siNtu (Bantu) languages, Slavic languages, Sinitic languages, Semitic languages, and any other human language.

The script is universal because human speech is universal.

---

## Quick Install

### Keyman (Phase 1 -- All Platforms)

Keyman is the primary deployment target. It runs on Windows, macOS, Linux, Android, and iOS.

1. Install Keyman from [keyman.com](https://keyman.com)
2. Download the CircleOne keyboard package (`.kmp` file) from the [Releases](https://github.com/bhengubv/circleone/releases) page
3. Open the `.kmp` file -- Keyman will install it automatically
4. Select "CircleOne - isiBheqe soHlamvu" from your input method list

```bash
# Or install via Keyman CLI (Linux)
keyman install circleone.kmp
```

### HeliBoard for Android (Phase 2)

[HeliBoard](https://github.com/Helium314/HeliBoard) is an open-source Android keyboard that supports custom layouts.

1. Install HeliBoard from F-Droid or GitHub Releases
2. Copy the CircleOne layout JSON from `android/heliboard/` into HeliBoard's custom layout directory
3. Enable the layout in HeliBoard settings under Languages & Layouts

### Hamster for iOS (Phase 2)

[Hamster](https://github.com/imfuxiao/Hamster) is an open-source iOS keyboard built on the RIME input engine.

1. Install Hamster from the App Store
2. Import the CircleOne RIME schema from `ios/hamster/`
3. Enable the schema in Hamster's input method settings

---

## Repository Structure

```
circleone/
|-- keyman/                 # Keyman keyboard package source
|-- android/                # HeliBoard layout definitions
|-- ios/                    # Hamster / RIME schema for iOS
|-- rime/                   # RIME input method engine schemas
|-- font/                   # isiBheqe soHlamvu font files
|-- unicode-proposal/       # Unicode encoding proposal documents
|-- scripts/                # Build and conversion utilities
|-- docs/                   # Documentation, guides, and references
|-- LICENSE                 # MIT (HeliBoard fork: GPL-3.0)
`-- README.md
```

---

## Contributing

CircleOne is a cross-disciplinary project. We need contributors across linguistics, type design, software engineering, and community building.

| Role                  | Priority   | What You Would Do                                                        |
|-----------------------|------------|--------------------------------------------------------------------------|
| **Linguist**          | Critical   | Validate phoneme mappings, review syllable decomposition rules           |
| **Keyman Developer**  | Critical   | Build and maintain the Keyman keyboard package (Phase 1 target)          |
| **Type Designer**     | High       | Design and refine the isiBheqe soHlamvu font for screen and print        |
| **Android Developer** | High       | Build HeliBoard integration and custom keyboard features                 |
| **iOS Developer**     | High       | Build Hamster/RIME integration for iOS                                   |
| **RIME Developer**    | Medium     | Author and optimise RIME input schemas for desktop platforms             |
| **Web Developer**     | Medium     | Build the web demo, documentation site, and interactive tutorials        |
| **Unicode Specialist**| Medium     | Advance the Unicode encoding proposal through the standards process      |
| **Community Manager** | Medium     | Coordinate with language communities, schools, and cultural organisations|

To get started:

1. Read the docs in `docs/` for technical context
2. Open an issue describing what you want to work on
3. Fork the repository and submit a pull request

---

## The Name: &#9655;&#9711;&#9665;

The project name **CircleOne** is encoded in isiBheqe soHlamvu itself:

| Symbol | Vowel | IPA  | Letter |
|--------|-------|------|--------|
| &#9655; (rightward triangle) | umkhonto | /ɔ/ | **O** |
| &#9711; (circle)             | indilinga | /n/ | **N** |
| &#9665; (leftward triangle)  | intombi  | /ɛ/ | **E** |

**&#9655;&#9711;&#9665;** spells **ONE**.

The name is a statement: one script, one keyboard, one system -- for every siNtu language. The geometric symbols are not decoration. They are the writing system working as intended.

---

## Links

- **isiBheqe soHlamvu**: [isibheqe.org.za](https://isibheqe.org.za)
- **Keyman**: [keyman.com](https://keyman.com)
- **HeliBoard**: [github.com/Helium314/HeliBoard](https://github.com/Helium314/HeliBoard)
- **Hamster**: [github.com/imfuxiao/Hamster](https://github.com/imfuxiao/Hamster)
- **Script Encoding Initiative**: [linguistics.berkeley.edu/sei](https://linguistics.berkeley.edu/sei/)
- **This Repository**: [github.com/bhengubv/circleone](https://github.com/bhengubv/circleone)

---

## License

This project is licensed under the **MIT License**. You are free to use, modify, and distribute this software without restriction.

Note: The Android app (Phase 2) incorporates HeliBoard, which is GPL-3.0. HeliBoard-derived components in android/ are subject to GPL-3.0 terms.

See [LICENSE](LICENSE) for the full text.

---

*isiBheqe soHlamvu belongs to its people. This keyboard is how we give it to the world.*
