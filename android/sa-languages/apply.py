#!/usr/bin/env python3
"""All eleven South African languages, on by default, and isiBheqe as a choice.

Called by patch-heliboard.sh against a fresh HeliBoard clone:

    python sa-languages/apply.py <HeliBoard dir>

WHY
---
CircleOne is a South African keyboard and shipped knowing four of the
country's eleven official languages - English, Afrikaans, and isiZulu and
Sesotho both marked "preliminary" by upstream. isiXhosa, isiNdebele, Sepedi,
Setswana, siSwati, Tshivenda and Xitsonga were not offered at all, and nothing
was on by default: a new install wore whatever the phone's language was.

And isiBheqe was not a choice. CircleOneTransliterator starts enabled and
nothing ever disables it, so every Latin syllable typed in CircleOne became an
isiBheqe glyph - measured on AngelDust on 2026-10-17: "ba" typed, one glyph
committed. Nobody could type English, or isiZulu in Latin letters, in the
keyboard built for them. Meanwhile heliboard-config/one_layout.json, the
layout meant to carry the script, is copied to assets/layouts/ where nothing
looks for a main layout, and is not in HeliBoard's layout format anyway.

WHAT IT DOES
  1. method.xml       seven new languages, en-ZA, and "isiZulu (isiBheqe)" -
                      a second isiZulu entry whose main layout is "isibheqe"
  2. layouts/main     isibheqe.txt, the QWERTY keys the transliterator reads
  3. locale_key_texts per-language popups: the click clusters on c, q and x
                      for the Nguni languages (from one_layout.json's
                      click_consonants), s-caron, e- and o-circumflex for
                      Sepedi and Setswana, the Tshivenda dotted letters, and
                      .za on every one
  4. names            the names South Africans use - isiZulu, Sepedi,
                      siSwati - not Android's "Zulu", "Northern Sotho", "Swati"
  5. defaults         all eleven, plus isiBheqe, enabled on a fresh install
  6. first language   the one that matches the phone's language, not the
                      alphabetically first enabled one (which would be
                      Afrikaans on every phone)

The transliterator gate - isiBheqe only when the isiBheqe layout is chosen -
lives in patch-heliboard.sh beside the transliteration block it guards.

DICTIONARIES ARE NOT HERE. Suggestions need a word list per language and
none exists in HeliBoard's dictionary repository for any of these but an
experimental Afrikaans one. See dictionaries/README.md.

Idempotent: every edit is skipped on its marker and refuses on an ambiguous
anchor rather than guessing.
"""
import io
import os
import re
import shutil
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
MARK = "CircleOne: South African languages"


def fail(msg):
    sys.stderr.write("sa-languages: %s\n" % msg)
    raise SystemExit(1)


def read(p):
    with io.open(p, encoding="utf-8") as f:
        return f.read()


def write(p, text):
    with io.open(p, "w", encoding="utf-8", newline="\n") as f:
        f.write(text)


def replace_once(path, old, new, marker, what, done):
    text = read(path)
    if marker in text:
        return
    n = text.count(old)
    if n != 1:
        fail("%s: anchor for %s found %d time(s) - nothing written" % (path, what, n))
    write(path, text.replace(old, new, 1))
    done.append(what)


# --------------------------------------------------------------- languages

# (locale, languageTag, subtypeId, label, KeyboardLayoutSet or None, note)
SUBTYPES = [
    ("en_ZA", "en-ZA", "0x5a410001", "@string/subtype_generic", None, "English (South Africa)"),
    ("xh", "xh", "0x5a410002", "@string/subtype_generic", None, "isiXhosa"),
    ("nr", "nr", "0x5a410003", "@string/subtype_generic", None, "isiNdebele"),
    ("nso", "nso", "0x5a410004", "@string/subtype_generic", None, "Sepedi"),
    ("tn", "tn", "0x5a410005", "@string/subtype_generic", None, "Setswana"),
    ("ss", "ss", "0x5a410006", "@string/subtype_generic", None, "siSwati"),
    ("ve", "ve", "0x5a410007", "@string/subtype_generic", None, "Tshivenda"),
    ("ts", "ts", "0x5a410008", "@string/subtype_generic", None, "Xitsonga"),
    ("zu", "zu", "0x5a410009", "@string/subtype_zu_isibheqe", "MAIN:isibheqe", "isiZulu in isiBheqe soHlamvu"),
]

# What a fresh install has on: the eleven, and isiBheqe. Pref form is
# languageTag SET extraValues, joined by SETS - SettingsSubtype.toPref().
DEFAULT_ENABLED = [
    ("af", ""), ("en-ZA", ""), ("nr", ""), ("nso", ""), ("ss", ""),
    ("st", "KeyboardLayoutSet=MAIN:sesotho"), ("tn", ""), ("ts", ""),
    ("ve", ""), ("xh", ""), ("zu", ""), ("zu", "KeyboardLayoutSet=MAIN:isibheqe"),
]

NAMES = {
    "zu": "isiZulu", "xh": "isiXhosa", "nr": "isiNdebele", "nso": "Sepedi",
    "tn": "Setswana", "ss": "siSwati", "ve": "Tshivenda", "ts": "Xitsonga",
}


def subtype_xml(locale, tag, sid, label, layout, note):
    extra = ("KeyboardLayoutSet=%s," % layout if layout else "") + "AsciiCapable,EmojiCapable"
    return ("    <!-- %s -->\n"
            "    <subtype android:icon=\"@drawable/ic_ime_switcher\"\n"
            "        android:label=\"%s\"\n"
            "        android:subtypeId=\"%s\"\n"
            "        android:imeSubtypeLocale=\"%s\"\n"
            "        android:languageTag=\"%s\"\n"
            "        android:imeSubtypeMode=\"keyboard\"\n"
            "        android:imeSubtypeExtraValue=\"%s\"\n"
            "        android:isAsciiCapable=\"true\"\n"
            "    />\n") % (note, label, sid, locale, tag, extra)


def step_method(main, done):
    path = os.path.join(main, "res", "xml", "method.xml")
    text = read(path)
    if MARK in text:
        return
    for _l, _t, sid, _lb, _ly, _n in SUBTYPES:
        if sid in text:
            fail("method.xml already uses subtypeId %s - choose another" % sid)
    for loc in ("xh", "nr", "nso", "tn", "ss", "ve", "ts", "en_ZA"):
        if 'android:imeSubtypeLocale="%s"' % loc in text:
            fail("method.xml already declares %s - upstream added it; review before merging" % loc)
    anchor = ('    <subtype android:icon="@drawable/ic_ime_switcher"\n'
              '        android:label="@string/subtype_no_language"')
    block = ("    <!-- %s: the seven official languages upstream lacks, South African\n"
             "         English, and isiBheqe as its own choice. See sa-languages/apply.py. -->\n"
             % MARK) + "".join(subtype_xml(*s) for s in SUBTYPES)
    replace_once(path, anchor, block + anchor, MARK, "method.xml: %d subtypes" % len(SUBTYPES), done)


def step_layout(main, done):
    dst = os.path.join(main, "assets", "layouts", "main", "isibheqe.txt")
    shutil.copyfile(os.path.join(HERE, "layouts", "isibheqe.txt"), dst)
    done.append("layouts/main/isibheqe.txt")


EXPECTED = {
    "zu.txt": ["ɓ", "ngc", "ngq", "ngx"],
    "xh.txt": ["ngc", "nxh"], "nr.txt": ["ngq"], "ss.txt": ["nqh"],
    "nso.txt": ["š", "ê", "ô"], "tn.txt": ["š", "ê", "ô"],
    "ve.txt": ["ḓ", "ḽ", "ṋ", "ṅ", "ṱ"],
    "ts.txt": ["za"], "st.txt": ["za"],
}


def step_popups(main, done):
    src = os.path.join(HERE, "locale_key_texts")
    dst = os.path.join(main, "assets", "locale_key_texts")
    for name, must in sorted(EXPECTED.items()):
        text = read(os.path.join(src, name))
        # Encoded wrong once and these letters arrive as mojibake - checked, not trusted.
        for ch in must:
            if ch not in text:
                fail("%s lacks %r - the file is not the one this patch was written for" % (name, ch))
        if "za" not in text.split("[tlds]")[-1]:
            fail("%s has no .za tld" % name)
        write(os.path.join(dst, name), text)
    done.append("locale_key_texts: %d languages" % len(EXPECTED))


def step_names(main, done):
    strings = os.path.join(main, "res", "values", "donottranslate.xml")
    lines = "".join('    <string name="subtype_%s" translatable="false">%s</string>\n' % (k, v)
                    for k, v in sorted(NAMES.items()))
    lines += '    <string name="subtype_zu_isibheqe" translatable="false">%s (isiBheqe)</string>\n'
    replace_once(strings, "</resources>",
                 "    <!-- %s: the names South Africans use for their languages. -->\n%s</resources>"
                 % (MARK, lines), MARK, "donottranslate.xml: names", done)

    utils = os.path.join(main, "java", "helium314", "keyboard", "latin", "common", "LocaleUtils.kt")
    old = '            "st" -> R.string.subtype_st\n'
    new = old + "".join('            "%s" -> R.string.subtype_%s // %s\n' % (k, k, MARK)
                        for k in sorted(NAMES))
    replace_once(utils, old, new, MARK, "LocaleUtils.kt: name overrides", done)


def step_keep_case(main, done):
    """isiZulu, not IsiZulu.

    HeliBoard capitalises the first letter of every language name, which is
    right for "English" and wrong for every name built on a noun-class
    prefix: AngelDust's language list read IsiNdebele, IsiXhosa, IsiZulu and
    SiSwati on 2026-10-17. The rule kept here is the language's own: a name
    that is a lower-case prefix followed by a capital keeps its case.
    """
    path = os.path.join(main, "java", "helium314", "keyboard", "latin", "utils", "SubtypeLocaleUtils.kt")
    text = read(path)
    if MARK in text:
        return
    calls = [("StringUtils.capitalizeFirstCodePoint(displayName, displayLocale)",
              "keepPrefixCase(displayName, displayLocale)"),
             ("StringUtils.capitalizeFirstCodePoint(name, actualDisplayLocale)",
              "keepPrefixCase(name, actualDisplayLocale)")]
    for old, new in calls:
        if text.count(old) != 1:
            fail("%s: %r found %d time(s) - nothing written" % (path, old, text.count(old)))
        text = text.replace(old, new, 1)
    helper = ('    // %s: isiZulu stays isiZulu - a lower-case noun-class prefix\n'
              '    // followed by a capital is the name as written, not a sentence start.\n'
              '    private fun keepPrefixCase(name: String, locale: Locale): String =\n'
              '        if (Regex("^[a-z]+[A-Z]").containsMatchIn(name)) name\n'
              '        else StringUtils.capitalizeFirstCodePoint(name, locale)\n\n') % MARK
    anchor = "    @JvmStatic\n    fun clearSubtypeDisplayNameCache() {"
    if text.count(anchor) != 1:
        fail("%s: no clearSubtypeDisplayNameCache anchor" % path)
    text = text.replace(anchor, helper + anchor, 1)
    write(path, text)
    done.append("SubtypeLocaleUtils.kt: isiZulu keeps its case")


def step_defaults(main, done):
    path = os.path.join(main, "java", "helium314", "keyboard", "latin", "settings", "Defaults.kt")
    pref = "${Separators.SETS}".join("%s${Separators.SET}%s" % (t, e) for t, e in DEFAULT_ENABLED)
    old = '    const val PREF_ENABLED_SUBTYPES = ""\n'
    new = ("    // %s: all eleven official languages and isiBheqe are on\n"
           "    // from the first start, not only the phone's own language.\n"
           '    const val PREF_ENABLED_SUBTYPES = "%s"\n') % (MARK, pref)
    replace_once(path, old, new, MARK, "Defaults.kt: eleven languages on", done)


def step_first_language(main, done):
    path = os.path.join(main, "java", "helium314", "keyboard", "latin", "utils", "SubtypeSettings.kt")
    old = ("        if (enabledSubtypes.isNotEmpty())\n"
           "            return enabledSubtypes.first()\n")
    new = ("        // %s: with eleven on, first() is Afrikaans on every\n"
           "        // phone - enabledSubtypes is sorted by language tag. Start in the\n"
           "        // phone's own language when one of them matches it.\n"
           "        if (enabledSubtypes.isNotEmpty())\n"
           "            return systemLocales.firstNotNullOfOrNull { system ->\n"
           "                LocaleUtils.getBestMatch(system, enabledSubtypes) { it.locale() }\n"
           "            } ?: enabledSubtypes.first()\n") % MARK
    replace_once(path, old, new, MARK, "SubtypeSettings.kt: start in the phone's language", done)


def main():
    if len(sys.argv) != 2:
        fail("usage: apply.py <HeliBoard dir>")
    main_dir = os.path.join(sys.argv[1], "app", "src", "main")
    if not os.path.isfile(os.path.join(main_dir, "res", "xml", "method.xml")):
        fail("no HeliBoard tree at %s" % sys.argv[1])
    done = []
    step_method(main_dir, done)
    step_layout(main_dir, done)
    step_popups(main_dir, done)
    step_names(main_dir, done)
    step_keep_case(main_dir, done)
    step_defaults(main_dir, done)
    step_first_language(main_dir, done)
    for d in done:
        print("       " + d)


if __name__ == "__main__":
    main()
