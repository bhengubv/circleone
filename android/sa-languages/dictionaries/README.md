# Dictionaries for the eleven languages

Suggestions, autocorrect and next-word prediction need a word list per
language. Without one the strip offers English: "Sawubona" typed in CircleOne
on 2026-10-17 suggested "Saw unions" and "Sawbones".

## What exists

HeliBoard's dictionary repository (`assets/dictionaries_in_dict_repo.csv`)
has a main dictionary for **one** of the ten non-English languages - an
experimental Afrikaans one. It has emoji dictionaries for af, nso, tn, xh and
zu. CircleOne strips `INTERNET`, so even those cannot be fetched from inside
the keyboard; whatever ships must be bundled.

| Language | Main dictionary |
|---|---|
| English (South Africa) | en-GB / en-US ship; an en-ZA list is still worth building |
| Afrikaans | experimental, upstream |
| isiZulu, isiXhosa, isiNdebele, Sepedi, Setswana, siSwati, Sesotho, Tshivenda, Xitsonga | none |

## How one is made

1. A word-frequency list from openly licensed text in the language.
2. Converted to the AOSP "combined" word list format that HeliBoard's
   dictionaries are compiled from.
3. Compiled to `main_<tag>.dict`.
4. Placed in `android/assets/dictionaries/`. `build-apk.sh` copies it to
   `assets/dicts/`, where HeliBoard loads bundled dictionaries.

## Sources to evaluate - nothing downloaded yet

Each needs its licence read before a word from it ships:

- the NCHLT text corpora, distributed through SADiLaR - built for exactly
  these languages
- Wikipedia in af, zu, xh, st, tn, ss, ve, ts and nso (isiNdebele has no
  Wikipedia of its own)

Downloading any of these is a decision for the owner of this repository,
not for the build.
