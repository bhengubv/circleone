package helium314.keyboard.latin.circleone.learn;

import android.content.Context;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Words as isiBheqe syllables, from the same map the keyboard types with.
 *
 * <p>one-pua-map.csv is the single source: CircleOneTransliterator turns
 * keystrokes into glyphs with it, and this turns a word into the syllables
 * the game hides. Reading the same file means the game can never teach a
 * syllable the keyboard does not type.
 *
 * <p>A word is split greedily on the longest syllable the map knows, up to
 * five letters ("ntshi"), which is how the transliterator buffers consonants;
 * a nasal no syllable takes is the syllabic nasal, as the transliterator
 * writes it.
 */
final class Syllables {

    static final int LONGEST = 5;

    /** One piece of a word: its letters, and its glyph if the script has one. */
    static final class Unit {
        final String latin;
        final String glyph;   // null when the map has no glyph for it

        Unit(String latin, String glyph) {
            this.latin = latin;
            this.glyph = glyph;
        }
    }

    private final Map<String, String> mGlyphs = new HashMap<>();
    private final Map<String, String> mLatin = new HashMap<>();
    /** The syllabic nasals - the map's "m_syl", "n_syl", "ng_syl" rows - by their letters. */
    private final Map<String, String> mNasals = new HashMap<>();

    Syllables(Context c) throws IOException {
        try (BufferedReader r = new BufferedReader(new InputStreamReader(
                c.getAssets().open("one-pua-map.csv"), StandardCharsets.UTF_8))) {
            String line = r.readLine(); // header: codepoint,input,ipa,glyph_name
            while ((line = r.readLine()) != null) {
                final String[] cols = line.split(",");
                if (cols.length < 2) continue;
                final String input = cols[1].trim();
                final boolean nasal = input.endsWith("_syl") && allLetters(input.substring(0, input.length() - 4));
                if (!nasal && !allLetters(input)) continue;
                try {
                    final int cp = Integer.parseInt(cols[0].trim().replace("0x", ""), 16);
                    final String glyph = new String(Character.toChars(cp));
                    if (nasal) {
                        final String letters = input.substring(0, input.length() - 4);
                        mNasals.put(letters, glyph);
                        mLatin.put(glyph, letters);
                    } else {
                        mGlyphs.put(input, glyph);
                        mLatin.put(glyph, input);
                    }
                } catch (NumberFormatException ignored) {
                    // a row the keyboard skips too
                }
            }
        }
    }

    boolean isEmpty() {
        return mGlyphs.isEmpty();
    }

    /** Letters only - "ng_syl" and the other named rows are not syllables a word is made of. */
    private static boolean allLetters(String s) {
        if (s.isEmpty()) return false;
        for (int i = 0; i < s.length(); i++) if (!Character.isLetter(s.charAt(i))) return false;
        return true;
    }

    /** The word, split into the syllables the script writes. */
    List<Unit> split(String word) {
        final String w = word.toLowerCase(Locale.ROOT);
        final List<Unit> out = new ArrayList<>();
        int i = 0;
        while (i < w.length()) {
            int len = Math.min(LONGEST, w.length() - i);
            String found = null;
            for (; len > 0; len--) {
                final String s = w.substring(i, i + len);
                if (mGlyphs.containsKey(s)) {
                    found = s;
                    break;
                }
            }
            if (found == null) {
                // No syllable starts here. A nasal that no syllable takes - the
                // m of "emzimbeni" - is the script's syllabic nasal, as the
                // keyboard writes it (CircleOneTransliterator.flush); ng first.
                final String nasal = w.startsWith("ng", i) && mNasals.containsKey("ng") ? "ng"
                        : mNasals.containsKey(w.substring(i, i + 1)) ? w.substring(i, i + 1) : null;
                if (nasal != null) {
                    out.add(new Unit(nasal, mNasals.get(nasal)));
                    i += nasal.length();
                } else {
                    out.add(new Unit(w.substring(i, i + 1), null));
                    i++;
                }
            } else {
                out.add(new Unit(found, mGlyphs.get(found)));
                i += found.length();
            }
        }
        return out;
    }

    /** Whether every piece of the word has a glyph - only those are dealt. */
    boolean writable(List<Unit> units) {
        for (Unit u : units) if (u.glyph == null) return false;
        return true;
    }

    /**
     * What a guess means. Latin letters are taken as typed; isiBheqe glyphs -
     * typed on the "isiZulu (isiBheqe)" layout - are read back to their
     * letters, so a person playing in the script is understood too.
     */
    String readGuess(CharSequence typed) {
        final StringBuilder latin = new StringBuilder();
        final String t = typed.toString().trim();
        for (int i = 0; i < t.length(); ) {
            final int cp = t.codePointAt(i);
            final String ch = new String(Character.toChars(cp));
            final String back = mLatin.get(ch);
            latin.append(back != null ? back : ch.toLowerCase(Locale.ROOT));
            i += Character.charCount(cp);
        }
        return latin.toString().replaceAll("[^\\p{L}]", "");
    }

    String glyphOf(String latin) {
        return mGlyphs.get(latin);
    }
}
