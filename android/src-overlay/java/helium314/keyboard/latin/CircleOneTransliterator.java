package helium314.keyboard.latin;

import android.content.Context;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Transliterates Latin keystroke sequences into isiBheqe soHlamvu PUA codepoints.
 *
 * Buffers consonant keys and emits a PUA glyph when a vowel completes a CV syllable.
 * The mapping is loaded from the bundled one-pua-map.csv asset.
 */
public class CircleOneTransliterator {
    private static final String TAG = "CircleOneTransliterator";
    private static final String CSV_ASSET = "one-pua-map.csv";

    /** Maps Latin input (e.g. "ba", "ngu") to PUA codepoint. */
    private final Map<String, Integer> cvMap = new HashMap<>();

    /** Valid consonant prefixes for buffering. */
    private final Set<String> validPrefixes = new HashSet<>();

    /** Vowel characters. */
    private static final Set<Character> VOWELS = new HashSet<>();
    static {
        VOWELS.add('a');
        VOWELS.add('e');
        VOWELS.add('i');
        VOWELS.add('o');
        VOWELS.add('u');
    }

    /** Current consonant buffer. */
    private StringBuilder buffer = new StringBuilder();

    /** Tracks the Latin syllable for each completed PUA glyph (for composing display + backspace). */
    private final java.util.ArrayList<String> composedSyllables = new java.util.ArrayList<>();

    /** Whether transliteration is enabled. */
    private boolean enabled = true;

    /** So the empty-map warning is said once, not on every keystroke. */
    private boolean warnedMapEmpty = false;

    /**
     * The failure that is otherwise silent.
     *
     * If the CSV is missing or malformed, loadMap() swallows it and cvMap
     * stays empty. processChar then falls through to pass-through forever:
     * the keyboard types plain Latin, the script never appears, and nothing
     * anywhere says why. This says it - once, with no user input in it.
     */
    private void warnOnceIfMapEmpty() {
        if (cvMap.isEmpty() && !warnedMapEmpty) {
            warnedMapEmpty = true;
            Log.w(TAG, "no CV mappings loaded from " + CSV_ASSET
                    + " - transliteration is off and input is passing through "
                    + "unchanged");
        }
    }

    public CircleOneTransliterator(Context context) {
        loadMap(context);
    }

    private void loadMap(Context context) {
        try {
            InputStream is = context.getAssets().open(CSV_ASSET);
            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            String line;
            boolean header = true;
            while ((line = reader.readLine()) != null) {
                if (header) { header = false; continue; }
                String[] cols = line.split(",");
                if (cols.length < 3) continue;
                String hex = cols[0].trim().replace("0x", "");
                String input = cols[1].trim();
                try {
                    int codepoint = Integer.parseInt(hex, 16);
                    cvMap.put(input, codepoint);
                } catch (NumberFormatException e) {
                    // skip
                }
            }
            reader.close();

            // Build valid prefix set
            for (String key : cvMap.keySet()) {
                if (key.length() > 0) {
                    char last = key.charAt(key.length() - 1);
                    if (VOWELS.contains(last)) {
                        String consonant = key.substring(0, key.length() - 1);
                        if (!consonant.isEmpty()) {
                            for (int i = 1; i <= consonant.length(); i++) {
                                validPrefixes.add(consonant.substring(0, i));
                            }
                        }
                    }
                }
            }

            Log.i(TAG, "Loaded " + cvMap.size() + " mappings, " + validPrefixes.size() + " prefixes");
        } catch (IOException e) {
            Log.e(TAG, "Failed to load " + CSV_ASSET, e);
        }
    }

    /**
     * Result of processing a key.
     */
    public static class Result {
        /** Number of buffered characters to delete before inserting. */
        public final int deleteCount;
        /** The string to commit (PUA character), or null if key was buffered. */
        public final String output;
        /** Whether this result should be handled (true) or fall through to default (false). */
        public final boolean handled;

        private Result(int deleteCount, String output, boolean handled) {
            this.deleteCount = deleteCount;
            this.output = output;
            this.handled = handled;
        }

        static Result buffered() {
            return new Result(0, null, true);
        }

        static Result commit(int deleteCount, String output) {
            return new Result(deleteCount, output, true);
        }

        static Result passThrough() {
            return new Result(0, null, false);
        }
    }

    /**
     * Process a single character input.
     * @param c the character typed (lowercase)
     * @return Result indicating what to do
     */
    public Result processChar(char c) {
        // NOTHING IS LOGGED HERE, AND THAT IS NOT AN OVERSIGHT.
        //
        // This method previously ran, on every keystroke:
        //
        //   Log.d("CircleOneDebug", "processChar('" + c + "') ... buffer='" + buffer + "'")
        //
        // In a keyboard that is every password, every message and every card
        // number written to logcat, at debug level, with no BuildConfig.DEBUG
        // guard. It printed the buffer as well, so partial words too.
        //
        // Do not add a logging flag here either. A flag that can be turned on
        // is a flag that will be turned on, by somebody debugging something
        // else, on a device that belongs to a person.
        if (!enabled || cvMap.isEmpty()) {
            warnOnceIfMapEmpty();
            return Result.passThrough();
        }

        c = Character.toLowerCase(c);

        // Vowel — try to complete a syllable
        if (VOWELS.contains(c)) {
            String syllable = buffer.toString() + c;
            Integer codepoint = cvMap.get(syllable);
            if (codepoint != null) {
                int deleteCount = buffer.length();
                composedSyllables.add(syllable);
                buffer.setLength(0);
                return Result.commit(deleteCount, new String(Character.toChars(codepoint)));
            }

            // Pure vowel (no buffer)
            if (buffer.length() == 0) {
                codepoint = cvMap.get(String.valueOf(c));
                if (codepoint != null) {
                    composedSyllables.add(String.valueOf(c));
                    return Result.commit(0, new String(Character.toChars(codepoint)));
                }
            }

            // No syllable for these letters and this vowel: write the buffer
            // out - as a syllabic nasal when it is one - then the vowel's own
            // glyph, rather than drop the consonants and let the vowel through.
            final String pending = flush();
            if (pending == null) return Result.passThrough();
            final Integer vowel = cvMap.get(String.valueOf(c));
            composedSyllables.add(String.valueOf(c));
            return Result.commit(0, pending + (vowel != null
                    ? new String(Character.toChars(vowel)) : String.valueOf(c)));
        }

        // Consonant — try to extend buffer
        String extended = buffer.toString() + c;
        if (validPrefixes.contains(extended)) {
            buffer.append(c);
            return Result.buffered();
        }

        // Not a valid extension: what is buffered is written out, never
        // dropped. It used to be cleared here, so "emzimbeni" lost its m - the
        // buffer is not on screen, the letter simply vanished.
        final String pending = flush();
        if (validPrefixes.contains(String.valueOf(c))) {
            buffer.append(c);
            return pending == null ? Result.buffered() : Result.commit(0, pending);
        }

        // Not a valid prefix at all — pass through, after what was pending
        if (pending == null) return Result.passThrough();
        composedSyllables.add(String.valueOf(c));
        return Result.commit(0, pending + c);
    }

    /**
     * Writes out the consonant buffer and empties it: a lone nasal - m, n, ng
     * before a consonant that does not take it into a syllable, the m of
     * "emzimbeni" - is the script's syllabic nasal (the "m_syl" rows of the
     * map); anything else is written as it was typed, so no keystroke is lost.
     * Call before a space, punctuation or Enter ends the word.
     *
     * @return what to append to the composing text, or null when nothing was buffered
     */
    public String flush() {
        if (buffer.length() == 0) return null;
        final String letters = buffer.toString();
        buffer.setLength(0);
        composedSyllables.add(letters);
        final Integer nasal = cvMap.get(letters + "_syl");
        return nasal != null ? new String(Character.toChars(nasal)) : letters;
    }

    /** Reset the consonant buffer (on space, backspace, etc.). Does NOT clear composedLatin. */
    public void reset() {
        buffer.setLength(0);
    }

    /** Reset everything including the Latin composition tracker. Call on full commit. */
    public void resetAll() {
        buffer.setLength(0);
        composedSyllables.clear();
    }

    /** Get current consonant buffer contents (for display). */
    public String getBuffer() {
        return buffer.toString();
    }

    /** Get buffer length. */
    public int getBufferLength() {
        return buffer.length();
    }

    /** Backspace on letters still waiting for a vowel: the last one goes, as typed. */
    public boolean deleteLastBuffered() {
        if (buffer.length() == 0) return false;
        buffer.setLength(buffer.length() - 1);
        return true;
    }

    /** Get the Latin text that produced the completed PUA syllables so far (including spaces/punctuation). */
    public String getComposedLatin() {
        StringBuilder sb = new StringBuilder();
        for (String s : composedSyllables) sb.append(s);
        return sb.toString();
    }

    /** Add a literal character (space, punctuation) to the Latin display tracker. */
    public void addLiteral(char c) {
        composedSyllables.add(String.valueOf(c));
    }

    /** Remove the last composed entry (syllable or literal) for backspace. */
    public void removeLastSyllable() {
        if (!composedSyllables.isEmpty()) {
            composedSyllables.remove(composedSyllables.size() - 1);
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (!enabled) reset();
    }

    public boolean hasMapping() {
        return !cvMap.isEmpty();
    }
}
