#!/usr/bin/env python3
"""Build CircleOne's main dictionaries for South Africa's official languages.

    python -I build_dicts.py <dir of corpora.nchlt.<lang>.zip> <dicttool_aosp.jar> <java>
    python -I build_dicts.py --learn <dir of corpora.nchlt.<lang>.zip>   (Learn word lists only)

Writes android/assets/dictionaries/main_<lang>.dict, which build-apk.sh copies
to assets/dicts/ where HeliBoard loads bundled dictionaries.

THE SOURCE
NCHLT Text Corpora, Centre for Text Technology (CTexT), North-West
University, for the South African Department of Arts and Culture. Creative
Commons Attribution 2.5 South Africa. Downloaded from repo.sadilar.org on
2026-10-17 and checked against the repository's MD5 for every archive.
Attribution ships in the app - see ATTRIBUTION.md.

WHICH LANGUAGES
isibheqe.org.za names the script's languages: the siNtu languages, Nguni,
Sotho-Tswana, Venda and Tsonga. Nine of South Africa's eleven official
languages are among them and NCHLT has a corpus for each; Afrikaans is
official but not siNtu and is built too. English is not built from NCHLT:
its corpus is government text, South African spelling is British, and
CircleOne already ships a broad en-GB dictionary for English to use.

WHAT A DICTIONARY IS MADE OF
  words    the corpus's own frequency lexicon (3.Lexica/FREQ.LEX), case
           variants merged into the lower-case form where one exists so a
           sentence-initial capital does not make two words of one, proper
           nouns kept as written. A word seen once is dropped as a likely typo.
  f        log-scaled 1..254 against the most frequent word, the AOSP rule
  bigrams  the next words, counted in the clean corpus (2.Corpora), up to
           three per word and only between words the dictionary holds, so the
           strip can offer the next word before a letter is typed

Nothing typed by anybody is in here: the corpora are public government
documents.

WHAT THE SOURCE LACKS, measured 2026-10-17 by counting every non-ASCII
character in each lexicon: Sepedi, Setswana, Sesotho and isiZulu carry none,
so Sepedi s-caron and the Sotho-Tswana circumflexes are absent - "soma" for
"soma/šoma" alike; Tshivenda has t- and l-circumflex-below but no d- or
n-circumflex-below at all. The dictionaries are as good as the corpus. A
better source for those letters improves them with no change here.
"""
import collections
import math
import os
import re
import subprocess
import sys
import time
import unicodedata
import zipfile
import zlib

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.normpath(os.path.join(HERE, "..", "..", "assets", "dictionaries"))

LANGS = {
    "zu": "isiZulu", "xh": "isiXhosa", "nr": "isiNdebele", "ss": "siSwati",
    "st": "Sesotho", "tn": "Setswana", "nso": "Sepedi", "ve": "Tshivenda",
    "ts": "Xitsonga", "af": "Afrikaans",
}

MAX_WORD = 48          # DecoderSpecificConstants.DICTIONARY_MAX_WORD_LENGTH
MIN_COUNT = 2
BIGRAMS_PER_WORD = 3
MIN_BIGRAM = 3

# A word: letters (any script, with their diacritics), joined by an apostrophe
# or hyphen inside the word - Xitsonga n'wana, Afrikaans 'n is handled by the
# leading apostrophe rule below.
WORD = re.compile(r"[^\W\d_]+(?:['’\-][^\W\d_]+)*")


def norm(w):
    # NFC first: a corpus that writes Tshivenda d-circumflex-below as d plus a
    # combining mark would otherwise be split at the mark, because a combining
    # mark is not a word character. NCHLT uses the precomposed letters, so
    # this changes nothing today; it is here so another source cannot break
    # words silently.
    return unicodedata.normalize("NFC", w).replace("’", "'")


def member(z, lang, folder, prefix):
    for info in z.infolist():
        parts = info.filename.split("/")
        if len(parts) == 3 and parts[1] == folder and parts[2].startswith(prefix) and parts[2]:
            return info.filename
    return None


def read_lexicon(z, lang):
    name = member(z, lang, "3.Lexica", "FREQ.LEX")
    if not name:
        raise SystemExit("%s: no FREQ.LEX in the archive" % lang)
    cased = collections.Counter()
    for line in z.read(name).decode("utf-8").splitlines():
        if "\t" not in line:
            continue
        w, c = line.rsplit("\t", 1)
        w = norm(w.strip())
        try:
            n = int(c)
        except ValueError:
            continue
        if not w or len(w) > MAX_WORD or not WORD.fullmatch(w):
            continue
        cased[w] += n
    # Merge case: "Ngiyabonga" at the start of a sentence is "ngiyabonga".
    # Where a lower-case form exists it owns the count of all its spellings;
    # a word only ever written capitalised is a name and keeps its most
    # common spelling.
    total = collections.Counter()
    forms = collections.defaultdict(list)
    for w, n in cased.items():
        total[w.lower()] += n
        forms[w.lower()].append(w)
    words = {}
    for lw, n in total.items():
        if n < MIN_COUNT:
            continue
        words[lw if lw in cased else max(forms[lw], key=cased.__getitem__)] = n
    return words


def read_bigrams(z, lang, vocab):
    name = member(z, lang, "2.Corpora", "CORP.NCHLT.%s.CLEAN" % lang)
    if not name:
        return {}
    index = {w.lower(): w for w in vocab}
    pairs = collections.Counter()
    with z.open(name) as f:
        for raw in f:
            line = raw.decode("utf-8", "replace")
            toks = [index.get(norm(t).lower()) for t in WORD.findall(line)]
            for a, b in zip(toks, toks[1:]):
                if a and b:
                    pairs[(a, b)] += 1
    nexts = collections.defaultdict(list)
    for (a, b), n in pairs.most_common():
        if n < MIN_BIGRAM:
            break
        if len(nexts[a]) < BIGRAMS_PER_WORD:
            nexts[a].append((b, n))
    return nexts


def scale(n, top):
    return max(1, min(254, int(round(254 * math.log(n) / math.log(top))))) if top > 1 else 128


def write_combined(lang, words, nexts, path):
    top = max(words.values())
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        f.write("dictionary=main:%s,locale=%s,description=%s (NCHLT),date=%d,version=20\n"
                % (lang, lang, LANGS[lang], int(time.time())))
        for w, n in sorted(words.items(), key=lambda kv: (-kv[1], kv[0])):
            wf = scale(n, top)
            f.write(" word=%s,f=%d\n" % (w, wf))
            for b, bn in nexts.get(w, []):
                f.write("  bigram=%s,f=%d\n" % (b, min(255, max(wf + 1, scale(bn, top)))))


# The isiBheqe languages - isibheqe.org.za's siNtu languages - get Hangman words.
LEARN = os.path.normpath(os.path.join(HERE, "..", "learn"))
LEARN_LANGS = ("zu", "xh", "nr", "ss", "st", "tn", "nso", "ve", "ts")
LEARN_WORDS = 500
LEARN_POOL = 3000      # commonest words considered; those with a clue are kept
CLUES_PER_WORD = 3     # so a word met twice is not met with the same clue

# A clue is an incomplete sentence: a real sentence from the corpus with the
# word left out. The player speaks the language, reads the sentence, knows
# the word, and types it - and sees what a word they know looks like in
# isiBheqe. Only short, plain sentences make good clues.
CLUE_MIN, CLUE_MAX = 4, 12
SENTENCE = re.compile(r"(?<=[.?!])\s+")
NOT_A_CLUE = re.compile(r"[\d()\[\]/:;\"“”=*_<>@#&%+|\\-]|[A-Z]{2,}")
BLANK = "____"


def read_clues(z, lang, wanted):
    """Up to CLUES_PER_WORD incomplete sentences for each wanted word."""
    name = member(z, lang, "2.Corpora", "CORP.NCHLT.%s.CLEAN" % lang)
    if not name:
        return {}
    best = collections.defaultdict(list)   # word -> [(rank, clue)]
    seen = set()
    with z.open(name) as f:
        for raw in f:
            for s in SENTENCE.split(norm(raw.decode("utf-8", "replace")).strip()):
                s = s.strip()
                # A whole sentence - begun with a capital, ended, plain - not a
                # fragment the paragraph split left behind.
                if not s or not s[0].isupper() or s[-1] not in ".?!" or s in seen or NOT_A_CLUE.search(s):
                    continue
                seen.add(s)
                toks = WORD.findall(s)
                if not CLUE_MIN <= len(toks) <= CLUE_MAX or len(toks[-1]) < 3:
                    continue  # "isb." and the like: an abbreviation, not an ending
                low = [t.lower() for t in toks]
                for i, t in enumerate(low):
                    if t not in wanted or low.count(t) != 1:
                        continue
                    # Rank: not the first word (the sentence leads into it),
                    # then short - the easiest clue is a short everyday
                    # sentence - then a fixed shuffle, not the alphabet, so a
                    # word's clues come from all over the corpus.
                    rank = (i == 0, max(len(toks), 6), zlib.crc32(s.encode("utf-8")))
                    clue = re.sub(r"(?<![^\W\d_])" + re.escape(toks[i]) + r"(?![^\W\d_])", BLANK, s, count=1)
                    if BLANK not in clue:
                        continue
                    lst = best[t]
                    lst.append((rank, clue))
                    lst.sort()
                    del lst[CLUES_PER_WORD:]
    return {w: [c for _r, c in lst] for w, lst in best.items()}


def write_learn(lang, words, z):
    """The commonest plain words that have a clue, for CircleOne Learn.

    One line per word: the word, then its clues, tab-separated. The game
    itself deals only the words whose every syllable has a glyph, so this
    list does not need to know the script.
    """
    plain = [w for w, _n in sorted(words.items(), key=lambda kv: -kv[1])
             if w.islower() and w.isalpha() and 4 <= len(w) <= 12][:LEARN_POOL]
    clues = read_clues(z, lang, set(plain))
    lines = ["\t".join([w] + clues[w]) for w in plain if w in clues][:LEARN_WORDS]
    os.makedirs(LEARN, exist_ok=True)
    with open(os.path.join(LEARN, "words_%s.txt" % lang), "w", encoding="utf-8", newline="\n") as f:
        f.write("\n".join(lines) + "\n")
    return len(lines)


def main():
    if len(sys.argv) == 3 and sys.argv[1] == "--learn":
        # Only the Learn word lists, the dictionaries untouched.
        zips = sys.argv[2]
        for lang in LEARN_LANGS:
            z = zipfile.ZipFile(os.path.join(zips, "corpora.nchlt.%s.zip" % lang))
            print("%-4s %-11s %4d words with clues" % (lang, LANGS[lang], write_learn(lang, read_lexicon(z, lang), z)))
        return
    if len(sys.argv) != 4:
        raise SystemExit(__doc__)
    zips, jar, java = sys.argv[1:]
    os.makedirs(OUT, exist_ok=True)
    work = os.path.join(zips, "..", "combined")
    os.makedirs(work, exist_ok=True)
    for lang in LANGS:
        z = zipfile.ZipFile(os.path.join(zips, "corpora.nchlt.%s.zip" % lang))
        words = read_lexicon(z, lang)
        if lang in LEARN_LANGS:
            write_learn(lang, words, z)
        nexts = read_bigrams(z, lang, words)
        combined = os.path.join(work, "main_%s.combined" % lang)
        write_combined(lang, words, nexts, combined)
        out = os.path.join(OUT, "main_%s.dict" % lang)
        r = subprocess.run([java, "-jar", jar, "makedict", "-s", combined, "-d", out],
                           capture_output=True, text=True)
        if r.returncode != 0 or not os.path.isfile(out):
            raise SystemExit("%s: dicttool failed\n%s%s" % (lang, r.stdout, r.stderr))
        print("%-4s %-11s %7d words %6d with next words  %8d bytes"
              % (lang, LANGS[lang], len(words), len(nexts), os.path.getsize(out)))


if __name__ == "__main__":
    main()
