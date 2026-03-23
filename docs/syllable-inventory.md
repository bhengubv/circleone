# Syllable Inventory

Status: Living document -- isiZulu core complete, other languages in progress
Last updated: 2026-03-23

---

## Overview

isiBheqe soHlamvu is a featural syllabary. Each glyph represents a complete syllable (typically CV -- consonant + vowel). This document catalogues the full syllable inventory, organised by phonological category and cross-referenced with the PUA encoding in `font/one-pua-map.csv`.

The inventory is built outward from isiZulu as the core language, with extensions for phonemes unique to other siNtu languages noted in the language-specific sections.

---

## Pure Vowels

The vowel system forms the structural foundation of the script. Each vowel has a distinct geometric shape.

### Core Vowels (5)

| Input | IPA | Name (isiZulu) | Description              | PUA      |
|-------|-----|----------------|--------------------------|----------|
| a     | /a/ | ikhaya         | Open central              | U+E000   |
| e     | /e/ | iphambili      | Close-mid front unrounded | U+E001   |
| i     | /i/ | intombi        | Close front unrounded     | U+E002   |
| o     | /o/ | imuva          | Close-mid back rounded    | U+E003   |
| u     | /u/ | ubuso          | Close back rounded        | U+E004   |

### Extended Vowels (3)

Used in languages with a seven-vowel system (Tshivenda, Xitsonga, some Sotho dialects).

| Input | IPA  | Description               | PUA      |
|-------|------|---------------------------|----------|
| aa    | /a:/ | Long open central          | U+E005   |
| ee    | /e:/ | Long close-mid front       | U+E006   |
| ii    | /i:/ | Long close front           | U+E007   |

Note: The open-mid vowels /E/ and /O/ (as referenced in the README vowel table) are encoded as separate shapes in the font. Their PUA assignments will be added as the seven-vowel system support matures.

---

## CV Syllable Grid: Plosives

Rows = consonant onsets. Columns = vowel nuclei.

### Voiceless Unaspirated

| Onset | +a       | +e       | +i       | +o       | +u       |
|-------|----------|----------|----------|----------|----------|
| p     | pa E018  | pe E019  | pi E01A  | po E01B  | pu E01C  |
| t     | ta E030  | te E031  | ti E032  | to E033  | tu E034  |
| k     | ka E048  | ke E049  | ki E04A  | ko E04B  | ku E04C  |

### Voiceless Aspirated

| Onset | +a       | +e       | +i       | +o       | +u       |
|-------|----------|----------|----------|----------|----------|
| ph    | pha E020 | phe E021 | phi E022 | pho E023 | phu E024 |
| th    | tha E038 | the E039 | thi E03A | tho E03B | thu E03C |
| kh    | kha E050 | khe E051 | khi E052 | kho E053 | khu E054 |

### Voiced

| Onset | +a       | +e       | +i       | +o       | +u       |
|-------|----------|----------|----------|----------|----------|
| b     | ba E010  | be E011  | bi E012  | bo E013  | bu E014  |
| d     | da E028  | de E029  | di E02A  | do E02B  | du E02C  |
| g     | ga E040  | ge E041  | gi E042  | go E043  | gu E044  |

### Implosive

| Onset | +a       | +e       | +i       | +o       | +u       |
|-------|----------|----------|----------|----------|----------|
| bh    | bha E0E0 | bhe E0E1 | bhi E0E2 | bho E0E3 | bhu E0E4 |

---

## CV Syllable Grid: Fricatives

| Onset | +a       | +e       | +i       | +o       | +u       |
|-------|----------|----------|----------|----------|----------|
| f     | fa E060  | fe E061  | fi E062  | fo E063  | fu E064  |
| v     | va E068  | ve E069  | vi E06A  | vo E06B  | vu E06C  |
| s     | sa E070  | se E071  | si E072  | so E073  | su E074  |
| z     | za E078  | ze E079  | zi E07A  | zo E07B  | zu E07C  |
| sh    | sha E080 | she E081 | shi E082 | sho E083 | shu E084 |
| h     | ha E088  | he E089  | hi E08A  | ho E08B  | hu E08C  |

### Lateral Fricatives / Affricates

| Onset | +a       | +e       | +i       | +o       | +u       |
|-------|----------|----------|----------|----------|----------|
| hl    | hla E090 | hle E091 | hli E092 | hlo E093 | hlu E094 |
| dl    | dla E098 | dle E099 | dli E09A | dlo E09B | dlu E09C |

---

## CV Syllable Grid: Nasals

| Onset | +a       | +e       | +i       | +o       | +u       |
|-------|----------|----------|----------|----------|----------|
| m     | ma E0A0  | me E0A1  | mi E0A2  | mo E0A3  | mu E0A4  |
| n     | na E0A8  | ne E0A9  | ni E0AA  | no E0AB  | nu E0AC  |
| ng    | nga E0B0 | nge E0B1 | ngi E0B2 | ngo E0B3 | ngu E0B4 |
| ny    | nya E0B8 | nye E0B9 | nyi E0BA | nyo E0BB | nyu E0BC |

---

## CV Syllable Grid: Liquids and Approximants

| Onset | +a       | +e       | +i       | +o       | +u       |
|-------|----------|----------|----------|----------|----------|
| l     | la E0C0  | le E0C1  | li E0C2  | lo E0C3  | lu E0C4  |
| r     | ra E0C8  | re E0C9  | ri E0CA  | ro E0CB  | ru E0CC  |
| w     | wa E0D0  | we E0D1  | wi E0D2  | wo E0D3  | wu E0D4  |
| y     | ya E0D8  | ye E0D9  | yi E0DA  | yo E0DB  | yu E0DC  |

---

## CV Syllable Grid: Affricates

| Onset | +a       | +e       | +i       | +o       | +u       |
|-------|----------|----------|----------|----------|----------|
| j     | ja E120  | je E121  | ji E122  | jo E123  | ju E124  |
| tsh   | tsha E128| tshe E129| tshi E12A| tsho E12B| tshu E12C|

---

## Prenasalised / Complex Onsets

These are single phonological units in siNtu languages, not consonant clusters.

| Onset | +a       | +e       | +i       | +o       | +u       |
|-------|----------|----------|----------|----------|----------|
| mb    | mba E0E8 | mbe E0E9 | mbi E0EA | mbo E0EB | mbu E0EC |
| nd    | nda E0F0 | nde E0F1 | ndi E0F2 | ndo E0F3 | ndu E0F4 |
| nt    | nta E0F8 | nte E0F9 | nti E0FA | nto E0FB | ntu E0FC |
| nk    | nka E100 | nke E101 | nki E102 | nko E103 | nku E104 |
| nj    | nja E108 | nje E109 | nji E10A | njo E10B | nju E10C |
| ntsh  | ntsha E110| ntshe E111| ntshi E112| ntsho E113| ntshu E114|

---

## Click Consonants

Click consonants are a defining feature of the Nguni languages (isiZulu, isiXhosa, siSwati, isiNdebele). Each click type has plain, aspirated, voiced, and nasal variants.

### Dental Click (c)

| Variant   | +a       | +e       | +i       | +o       | +u       | IPA base |
|-----------|----------|----------|----------|----------|----------|----------|
| c (plain) | ca E140  | ce E141  | ci E142  | co E143  | cu E144  | !        |
| ch (asp.) | cha E148 | che E149 | chi E14A | cho E14B | chu E14C | !h       |
| gc (vcd.) | gca E150 | gce E151 | gci E152 | gco E153 | gcu E154 | g!       |
| nc (nas.) | nca E158 | nce E159 | nci E15A | nco E15B | ncu E15C | n!       |

### Palatal Click (q)

| Variant   | +a       | +e       | +i       | +o       | +u       | IPA base |
|-----------|----------|----------|----------|----------|----------|----------|
| q (plain) | qa E160  | qe E161  | qi E162  | qo E163  | qu E164  | ||       |
| qh (asp.) | qha E168 | qhe E169 | qhi E16A | qho E16B | qhu E16C | ||h      |
| gq (vcd.) | gqa E170 | gqe E171 | gqi E172 | gqo E173 | gqu E174 | g||      |
| nq (nas.) | nqa E178 | nqe E179 | nqi E17A | nqo E17B | nqu E17C | n||      |

### Lateral Click (x)

| Variant   | +a       | +e       | +i       | +o       | +u       | IPA base |
|-----------|----------|----------|----------|----------|----------|----------|
| x (plain) | xa E180  | xe E181  | xi E182  | xo E183  | xu E184  | ||x      |
| xh (asp.) | xha E188 | xhe E189 | xhi E18A | xho E18B | xhu E18C | ||xh     |
| gx (vcd.) | gxa E190 | gxe E191 | gxi E192 | gxo E193 | gxu E194 | g||x     |
| nx (nas.) | nxa E198 | nxe E199 | nxi E19A | nxo E19B | nxu E19C | n||x     |

---

## Syllabic Nasals

Syllabic nasals function as complete syllables on their own, without a vowel nucleus. Common in words like "umuntu" (u-mu-ntu) where "m" carries syllabic weight.

| Input  | IPA | Description          | PUA    |
|--------|-----|----------------------|--------|
| m_syl  | /m/ | Syllabic bilabial    | U+E1B0 |
| n_syl  | /n/ | Syllabic alveolar    | U+E1B1 |
| ng_syl | /ng/| Syllabic velar       | U+E1B2 |

---

## Current Inventory Summary

| Category              | Syllable count |
|-----------------------|----------------|
| Pure vowels           | 8              |
| Plosive CV            | 60             |
| Fricative CV          | 40             |
| Nasal CV              | 20             |
| Liquid/Approximant CV | 20             |
| Affricate CV          | 10             |
| Prenasalised CV       | 30             |
| Click CV              | 60             |
| Syllabic nasals       | 3              |
| **Total**             | **~251**       |

---

## Language-Specific Phoneme Notes

### isiZulu (zu) -- Core language

The tables above represent the isiZulu phoneme inventory comprehensively. isiZulu has all three click series (dental, palatal, lateral), extensive prenasalisation, and the implosive /bh/.

### isiXhosa (xh)

Shares the isiZulu click inventory but adds:
- Nasal-aspirated clicks: nch, nqh, nxh (not yet in PUA map)
- Ejective stops in some dialects
- Greater frequency of lateral clicks

### Sesotho (st) / Setswana (tn)

- No click consonants (clicks are a Nguni feature)
- Ejective stops: p', t', k', ts' (to be encoded)
- Lateral affricate /tl/ is phonemic (to be encoded)
- Seven-vowel system in some dialects: /e/, /E/, /o/, /O/ distinguished

### siSwati (ss)

- Shares Nguni click inventory but with lower frequency than isiZulu/isiXhosa
- Labio-velar approximant /w/ more prominent
- Distinctive breathy-voiced series in some dialects

### Tshivenda (ve)

- No clicks
- Retroflex consonants: retroflex /d/, retroflex /t/ (unique to Tshivenda among SA languages)
- Seven-vowel system: /e/ vs /E/, /o/ vs /O/ fully distinguished
- Dental fricative /th/ (as in English "think"), rare in other siNtu languages
- Labiodental approximant present
- PUA slots needed: retroflex series, dental fricative series

### Xitsonga (ts)

- No clicks
- Breathy-voiced (murmured) consonants: bh, dh, gh (distinct from aspiration)
- Affricate-heavy inventory: /pf/, /bv/, /sv/, /zv/
- Seven-vowel system
- Nasal+fricative clusters: mf, nz, etc.
- PUA slots needed: breathy-voiced series, labial affricate series

### isiNdebele (nr)

- Full Nguni click inventory (similar to isiZulu)
- Some unique lexical items but phoneme inventory largely overlaps with isiZulu

---

## Expansion Notes

To add a new language:

1. Identify phonemes not already in the inventory
2. Assign PUA codepoints in the next available range
3. Add rows to `font/one-pua-map.csv`
4. Run `scripts/generate_dict.py` to regenerate the Rime dictionary
5. Design glyphs following the featural composition rules
6. Update this document with the language-specific section
