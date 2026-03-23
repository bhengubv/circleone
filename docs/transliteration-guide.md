# Transliteration Guide

How vowel-first input works in CircleOne.

Last updated: 2026-03-23

---

## Two Input Orders, One Output

CircleOne supports two input orders for typing isiBheqe soHlamvu syllables. Both produce the exact same rendered glyph.

| Method       | Engine | Input order     | Example for "ba" | Target user              |
|--------------|--------|-----------------|-------------------|--------------------------|
| Latin order  | Keyman | Consonant first | Type `b` then `a` | Users familiar with Latin typing |
| Script order | Rime   | Vowel first     | Type `a` then `b` | Users thinking in isiBheqe logic |

The distinction exists because isiBheqe soHlamvu is structurally vowel-primary: the vowel determines the base shape of the syllable block, and consonant features are layered onto it. Typing the vowel first mirrors how the script is actually constructed.

---

## Keyman: Latin Order (Consonant-First)

Keyman uses the conventional Latin transliteration order. You type the consonant(s), then the vowel, just as you would when writing "ba" in English.

### Input flow

```
Keystroke sequence:  b  a
                     |  |
                     v  v
Keyman rule:         "b" + "a"  -->  U+E010
                                       |
                                       v
                                  one.ttf renders
                                  syllable glyph "ba"
```

Keyman rules are defined in a .kmn file. Each rule is a simple substitution: a sequence of keystrokes maps to a PUA codepoint. The rules are deterministic and stateless -- each keystroke sequence has exactly one output.

### Multi-character onsets

For consonants written with multiple Latin letters (sh, kh, ng, etc.), you type the full Latin representation before the vowel:

```
"sh" + "a"  -->  U+E080  (sha)
"ng" + "u"  -->  U+E0B4  (ngu)
"nq" + "a"  -->  U+E178  (nqa)
```

### Click consonants

Click consonants follow the same pattern. The Latin convention for clicks is used:

```
"c" + "a"   -->  U+E140  (dental click + a)
"q" + "a"   -->  U+E160  (palatal click + a)
"x" + "a"   -->  U+E180  (lateral click + a)
"gc" + "a"  -->  U+E150  (voiced dental click + a)
```

---

## Rime: Script Order (Vowel-First)

The Rime input method uses vowel-first order, matching how isiBheqe soHlamvu syllables are actually composed. You type the vowel first (selecting the base shape), then the consonant modifier(s).

### Input flow

```
Keystroke sequence:  a  b
                     |  |
                     v  v
Rime speller:        normalise via algebra rules
                     |
                     v
Dictionary lookup:   "ab"  -->  U+E010
                                  |
                                  v
                             one.ttf renders
                             syllable glyph "ba"
```

The Rime schema (defined in `rime/one.schema.yaml`) uses speller algebra to handle input normalisation. The dictionary (`rime/one.dict.yaml`) maps normalised input strings to PUA codepoints.

### Why vowel-first?

In isiBheqe soHlamvu, the vowel is the structural anchor of every syllable block:

1. The vowel determines the outer shape (triangle direction, circle, etc.)
2. Consonant strokes are placed inside or alongside the vowel shape
3. Reading the script, your eye identifies the vowel shape first, then parses the consonant features

Typing vowel-first preserves this cognitive order. After brief adaptation, users report it feels natural because the keystroke order matches the visual composition order.

### Multi-character onsets (vowel-first)

```
"a" + "sh"  -->  U+E080  (sha)
"u" + "ng"  -->  U+E0B4  (ngu)
"a" + "nq"  -->  U+E178  (nqa)
```

### Prenasalised shortcuts

The Rime speller provides shortcuts for prenasalised consonants. Typing uppercase `N` before a stop is equivalent to the full nasal prefix:

```
"a" + "Nb"  is equivalent to  "a" + "mb"  -->  U+E0E8  (mba)
"a" + "Nd"  is equivalent to  "a" + "nd"  -->  U+E0F0  (nda)
```

---

## Worked Examples

### Example 1: "ubuntu" (humanity)

**Keyman (Latin order):**
```
u -> (waiting)
b -> (waiting)
u -> U+E014 "bu" is not right -- need to decompose into syllables

Correct decomposition: u-bu-ntu

u     -->  U+E004  (vowel "u")
b + u -->  U+E014  (syllable "bu")
n + t + u --> U+E0FC (syllable "ntu")

Keystrokes: u . b u . n t u
Output:     [u] [bu] [ntu]
```

**Rime (vowel-first):**
```
u     -->  U+E004  (vowel "u")
u + b -->  U+E014  (syllable "bu")
u + nt --> U+E0FC  (syllable "ntu")

Keystrokes: u . u b . u n t
Output:     [u] [bu] [ntu]
```

### Example 2: "iqaqa" (polecat -- three clicks)

**Keyman (Latin order):**
```
Decomposition: i-qa-qa

i       -->  U+E002  (vowel "i")
q + a   -->  U+E160  (palatal click + a)
q + a   -->  U+E160  (palatal click + a)

Keystrokes: i . q a . q a
```

**Rime (vowel-first):**
```
i       -->  U+E002  (vowel "i")
a + q   -->  U+E160  (palatal click + a)
a + q   -->  U+E160  (palatal click + a)

Keystrokes: i . a q . a q
```

### Example 3: "ngiyabonga" (I am thankful)

**Keyman (Latin order):**
```
Decomposition: ngi-ya-bo-nga

ng + i  -->  U+E0B2  (ngi)
y + a   -->  U+E0D8  (ya)
b + o   -->  U+E013  (bo)
ng + a  -->  U+E0B0  (nga)

Keystrokes: n g i . y a . b o . n g a
```

**Rime (vowel-first):**
```
i + ng  -->  U+E0B2  (ngi)
a + y   -->  U+E0D8  (ya)
o + b   -->  U+E013  (bo)
a + ng  -->  U+E0B0  (nga)

Keystrokes: i n g . a y . o b . a n g
```

---

## Comparison to Other Composition Systems

### Korean Hangul Jamo Composition

Korean Hangul is the closest analogue to isiBheqe soHlamvu input. Hangul syllable blocks are composed from:
- Leading consonant (choseong)
- Medial vowel (jungseong)
- Optional trailing consonant (jongseong)

The system types consonant-vowel-consonant in sequence, and the rendering engine composes them into a syllable block. Similarly, CircleOne types component keystrokes that compose into a syllable block.

Key difference: Hangul uses Unicode composition (combining jamo codepoints at U+1100+). CircleOne currently uses pre-composed PUA codepoints. The future Phase 2 OpenType composition approach will be closer to how Hangul rendering works.

### Japanese Romaji to Kana

Japanese input methods convert Latin "romaji" sequences to kana characters:
- "ka" becomes the single character ka
- "shi" becomes the single character shi

This is analogous to Keyman's Latin-order input: a sequence of Latin keystrokes maps to a single script character. The difference is that Japanese kana are in Unicode, while isiBheqe soHlamvu currently uses PUA.

### Tibetan Input

Tibetan input methods compose syllable stacks from individual components, similar to how isiBheqe soHlamvu layers consonant strokes onto vowel bases. Tibetan uses Unicode combining marks and complex OpenType rules. CircleOne's Phase 2 composition strategy draws on similar principles.

---

## Choosing an Input Method

| If you...                                      | Use...  |
|------------------------------------------------|---------|
| Are new to isiBheqe soHlamvu                   | Keyman  |
| Think in Latin transliteration                 | Keyman  |
| Want to match the script's visual logic        | Rime    |
| Are a fluent reader of isiBheqe soHlamvu       | Rime    |
| Need cross-platform support immediately        | Keyman  |
| Use Hamster on iOS                             | Rime    |

Both methods are fully supported. Neither is "correct" -- they are different paths to the same destination.
