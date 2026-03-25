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
        android.util.Log.d("CircleOneDebug", "processChar('" + c + "') enabled=" + enabled + " cvMap.size=" + cvMap.size() + " buffer='" + buffer + "'");
        if (!enabled || cvMap.isEmpty()) {
            android.util.Log.d("CircleOneDebug", "passThrough: enabled=" + enabled + " cvMap.empty=" + cvMap.isEmpty());
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

            // No match — flush buffer, pass through
            buffer.setLength(0);
            return Result.passThrough();
        }

        // Consonant — try to extend buffer
        String extended = buffer.toString() + c;
        if (validPrefixes.contains(extended)) {
            buffer.append(c);
            return Result.buffered();
        }

        // Not a valid extension — reset buffer, try as new prefix
        buffer.setLength(0);
        if (validPrefixes.contains(String.valueOf(c))) {
            buffer.append(c);
            return Result.buffered();
        }

        // Not a valid prefix at all — pass through
        return Result.passThrough();
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
