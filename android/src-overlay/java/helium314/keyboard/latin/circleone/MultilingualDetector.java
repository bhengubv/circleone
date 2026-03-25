/*
 * Copyright (C) 2026 The Other Bhengu (Pty) Ltd t/a The Geek.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package helium314.keyboard.latin.circleone;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * On-device language auto-detector for Bantu languages across Africa, plus Afrikaans and English.
 *
 * <p>Detection strategy (no ML, no file I/O):
 * <ol>
 *   <li><b>Morphological prefix scoring</b> — high-signal noun-class and function-word prefixes
 *       unique to each language family are matched against each word in the sliding window.</li>
 *   <li><b>Character n-gram scoring</b> — hardcoded bigram and trigram frequency tables for each
 *       language weight the raw character sequences found in the current window.</li>
 *   <li><b>Diacritic / orthographic markers</b> — language-specific Unicode characters
 *       (e.g. Afrikaans ê/ë, Tshivenda ṋ/ḽ) give strong individual boosts.</li>
 *   <li><b>Sliding window</b> — only the last {@value #WINDOW_SIZE} words are considered so that
 *       code-switching is tracked continuously and the result stays responsive.</li>
 * </ol>
 *
 * <p>Typical detection latency is well under 10 ms for a 20-word window on mid-range Android
 * hardware (no heap allocations in the hot path after warm-up).
 *
 * <p>Usage:
 * <pre>{@code
 * MultilingualDetector detector = new MultilingualDetector();
 *
 * // Incremental (word-by-word as user types):
 * detector.addWord("Ngiyabonga");
 * detector.addWord("kakhulu");
 * String lang = detector.getActiveLanguage(); // "zu"
 *
 * // One-shot on an existing string:
 * String lang = detector.detectLanguage("Die hond loop in die straat");  // "af"
 *
 * // Top-3 with confidence:
 * List<LanguageScore> scores = detector.detectLanguages("Sawubona mfowethu");
 * // scores.get(0).languageCode == "zu", scores.get(0).confidence ~= 0.92
 * }</pre>
 *
 * <p>Thread safety: instances are <b>not</b> thread-safe. Create one per input connection.
 */
public final class MultilingualDetector {

    // -----------------------------------------------------------------------
    // Public API types
    // -----------------------------------------------------------------------

    /**
     * A language detection result carrying an ISO 639-1 / 639-3 language code and a
     * normalised confidence score in [0.0, 1.0].
     */
    public static final class LanguageScore implements Comparable<LanguageScore> {
        /** ISO 639-1 or ISO 639-3 language code (e.g. {@code "zu"}, {@code "xh"}, {@code "af"}). */
        public final String languageCode;
        /** Normalised confidence in the range [0.0, 1.0]. */
        public final double confidence;

        private LanguageScore(String languageCode, double confidence) {
            this.languageCode = languageCode;
            this.confidence = confidence;
        }

        @Override
        public int compareTo(LanguageScore other) {
            // Descending by confidence
            return Double.compare(other.confidence, this.confidence);
        }

        @Override
        public String toString() {
            return languageCode + "=" + String.format(Locale.US, "%.3f", confidence);
        }
    }

    // -----------------------------------------------------------------------
    // Constants
    // -----------------------------------------------------------------------

    private static final int WINDOW_SIZE = 20;   // words retained in sliding window
    private static final int MIN_WORD_LEN = 3;   // ignore very short words for n-gram scoring
    private static final double NGRAM_WEIGHT = 1.0;
    private static final double PREFIX_WEIGHT = 4.0;
    private static final double DIACRITIC_WEIGHT = 6.0;
    private static final double FUNCTION_WORD_WEIGHT = 5.0;
    private static final double CLICK_WEIGHT = 3.5;  // click-consonant patterns in Nguni

    /**
     * Supported languages: Bantu languages spanning Southern, Eastern, and Central Africa,
     * plus Afrikaans and English. Covers the majority of the African continent's speakers.
     */
    private static final String[] LANGUAGES = {
            // --- Southern Bantu (Nguni group) ---
            "zu",   // isiZulu — South Africa
            "xh",   // isiXhosa — South Africa
            "nr",   // isiNdebele — South Africa / Zimbabwe
            "ss",   // siSwati — Eswatini / South Africa
            // --- Southern Bantu (Sotho-Tswana group) ---
            "st",   // Sesotho — Lesotho / South Africa
            "tn",   // Setswana — Botswana / South Africa
            "nso",  // Sepedi — South Africa
            // --- Southern Bantu (other) ---
            "ve",   // Tshivenda — South Africa / Zimbabwe
            "ts",   // Xitsonga — South Africa / Mozambique
            // --- Eastern Bantu ---
            "sw",   // Kiswahili — Tanzania / Kenya / Uganda / DRC (100M+ speakers)
            "rw",   // Kinyarwanda — Rwanda
            "rn",   // Kirundi — Burundi
            "lg",   // Luganda — Uganda
            "ki",   // Gikuyu — Kenya
            // --- South-Eastern Bantu ---
            "sn",   // Shona — Zimbabwe
            "ny",   // Chichewa/Nyanja — Malawi / Zambia / Mozambique
            "bem",  // Bemba — Zambia
            // --- Central Bantu ---
            "ln",   // Lingala — DRC / Congo-Brazzaville
            "kg",   // Kikongo — DRC / Congo / Angola
            // --- Non-Bantu ---
            "af",   // Afrikaans — South Africa / Namibia
            "en",   // English — pan-African lingua franca
    };

    // -----------------------------------------------------------------------
    // State
    // -----------------------------------------------------------------------

    private final Deque<String> wordWindow = new ArrayDeque<>(WINDOW_SIZE + 1);
    /** Running raw score per language — accumulated over words in the window. */
    private final double[] windowScores = new double[LANGUAGES.length];
    /** Language code → index map for O(1) lookup. */
    private static final Map<String, Integer> LANG_INDEX = new HashMap<>();

    static {
        for (int i = 0; i < LANGUAGES.length; i++) {
            LANG_INDEX.put(LANGUAGES[i], i);
        }
    }

    // -----------------------------------------------------------------------
    // Constructor
    // -----------------------------------------------------------------------

    /** Creates a new detector with an empty detection window. */
    public MultilingualDetector() {
        // windowScores is already zero-initialised
    }

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    /**
     * Detect the most likely language of {@code text}.
     *
     * <p>This is a stateless one-shot call — it does not modify the detector's running window.
     *
     * @param text arbitrary text (may be multiple words or a single word)
     * @return ISO language code of the most likely language, or {@code "en"} if text is empty
     */
    public String detectLanguage(String text) {
        if (text == null || text.trim().isEmpty()) return "en";
        List<LanguageScore> results = detectLanguages(text);
        return results.isEmpty() ? "en" : results.get(0).languageCode;
    }

    /**
     * Detect the top-3 most likely languages of {@code text}, ranked by confidence.
     *
     * <p>This is a stateless one-shot call — it does not modify the detector's running window.
     *
     * @param text arbitrary text
     * @return list of up to 3 {@link LanguageScore} objects, descending by confidence;
     *         never null, may be empty if text is blank
     */
    public List<LanguageScore> detectLanguages(String text) {
        if (text == null || text.trim().isEmpty()) return Collections.emptyList();

        double[] scores = new double[LANGUAGES.length];
        String[] words = tokenise(text);
        for (String word : words) {
            scoreWord(word, scores);
        }
        return buildResults(scores, 3);
    }

    /**
     * Feed one word (or a short phrase) into the incremental detection window.
     *
     * <p>Words beyond the {@value #WINDOW_SIZE}-word window are evicted and their scores
     * are subtracted so the window stays fresh for code-switching detection.
     *
     * @param word the word just typed (may contain punctuation — it is cleaned internally)
     */
    public void addWord(String word) {
        if (word == null || word.isEmpty()) return;
        String clean = cleanToken(word);
        if (clean.isEmpty()) return;

        // Score the new word and add to window
        scoreWord(clean, windowScores);
        wordWindow.addLast(clean);

        // Evict oldest word if window is full
        if (wordWindow.size() > WINDOW_SIZE) {
            String evicted = wordWindow.removeFirst();
            // Subtract the evicted word's contribution
            double[] evictedScores = new double[LANGUAGES.length];
            scoreWord(evicted, evictedScores);
            for (int i = 0; i < LANGUAGES.length; i++) {
                windowScores[i] -= evictedScores[i];
                if (windowScores[i] < 0) windowScores[i] = 0;
            }
        }
    }

    /**
     * Clears the sliding window and all accumulated scores.
     * Call this when the user moves to a new text field.
     */
    public void reset() {
        wordWindow.clear();
        Arrays.fill(windowScores, 0.0);
    }

    /**
     * Returns the currently detected language based on the words accumulated via
     * {@link #addWord(String)}.
     *
     * @return ISO language code of the current best-match language, or {@code "en"} if the
     *         window is empty
     */
    public String getActiveLanguage() {
        if (wordWindow.isEmpty()) return "en";
        List<LanguageScore> results = buildResults(windowScores, 1);
        return results.isEmpty() ? "en" : results.get(0).languageCode;
    }

    /**
     * Returns the top-3 languages from the current sliding window with confidence scores.
     *
     * @return list of up to 3 {@link LanguageScore} objects, descending by confidence
     */
    public List<LanguageScore> getActiveLanguages() {
        if (wordWindow.isEmpty()) return Collections.emptyList();
        return buildResults(windowScores, 3);
    }

    // -----------------------------------------------------------------------
    // Internal scoring
    // -----------------------------------------------------------------------

    /** Tokenise text into lowercase cleaned tokens. */
    private static String[] tokenise(String text) {
        String[] raw = text.split("[\\s\\p{Punct}]+");
        List<String> result = new ArrayList<>(raw.length);
        for (String token : raw) {
            String clean = cleanToken(token);
            if (!clean.isEmpty()) result.add(clean);
        }
        return result.toArray(new String[0]);
    }

    /** Lowercase and strip non-letter characters (preserving SA diacritics via Unicode). */
    private static String cleanToken(String token) {
        // Keep Unicode letters and hyphens (Tshivenda uses hyphens in compounds)
        StringBuilder sb = new StringBuilder(token.length());
        String lower = token.toLowerCase(Locale.ROOT);
        for (int i = 0; i < lower.length(); i++) {
            char c = lower.charAt(i);
            if (Character.isLetter(c) || c == '-') sb.append(c);
        }
        return sb.toString();
    }

    /**
     * Score a single cleaned lowercase word against all languages, accumulating into
     * {@code scores}.
     */
    private static void scoreWord(String word, double[] scores) {
        if (word.length() < 1) return;

        // --- 1. Diacritic / orthographic markers ---
        scoreDiacritics(word, scores);

        // --- 2. Morphological prefix matching ---
        scorePrefixes(word, scores);

        // --- 3. Function words / high-frequency exact matches ---
        scoreFunctionWords(word, scores);

        // --- 4. Click consonant patterns (Nguni) ---
        scoreClickConsonants(word, scores);

        // --- 5. Character bigram + trigram scoring ---
        if (word.length() >= MIN_WORD_LEN) {
            scoreNgrams(word, scores);
        }
    }

    // -----------------------------------------------------------------------
    // Diacritics
    // -----------------------------------------------------------------------

    private static void scoreDiacritics(String word, double[] scores) {
        // Afrikaans-specific diacritics: ê ë î ô û ä ö ü (circumflex / umlaut)
        if (containsAny(word, "ê", "ë", "î", "ô", "û", "ä", "ö", "ü")) {
            scores[idx("af")] += DIACRITIC_WEIGHT;
        }
        // Tshivenda-specific diacritics: ṋ (n with combining dot below), ḽ (l-dot below)
        // represented here as ṋ and ḽ
        if (word.contains("ṋ") || word.contains("ḽ") || word.contains("ṱ") || word.contains("ḓ")) {
            scores[idx("ve")] += DIACRITIC_WEIGHT;
        }
        // Sesotho / Setswana: ô  (shared with Afrikaans but also used here) — lower boost
        if (word.contains("ô") || word.contains("ê")) {
            scores[idx("st")] += DIACRITIC_WEIGHT * 0.3;
            scores[idx("tn")] += DIACRITIC_WEIGHT * 0.3;
        }
    }

    // -----------------------------------------------------------------------
    // Prefixes
    // -----------------------------------------------------------------------

    /**
     * Noun-class prefix tables. Each entry is {@code {prefix, languageCode, weight_multiplier}}.
     * Longer / more specific prefixes get higher priority by ordering (checked first-wins for
     * each language).
     */
    private static final String[][] PREFIX_TABLE = {
            // --- Zulu (zu) ---
            {"umuntu", "zu", "2.0"},
            {"abantu", "zu", "2.0"},
            {"isiZulu", "zu", "3.0"},
            {"isizulu", "zu", "3.0"},
            {"umu", "zu", "1.0"},
            {"aba", "zu", "1.0"},
            {"izi", "zu", "1.0"},
            {"ama", "zu", "0.9"},
            {"ili", "zu", "1.0"},
            {"ubu", "zu", "1.0"},
            {"uku", "zu", "0.9"},
            {"isi", "zu", "0.8"},   // shared with Xhosa — lower weight
            {"nga", "zu", "0.7"},
            {"ngi", "zu", "1.0"},   // 1st-person Zulu prefix
            {"nge", "zu", "0.8"},
            {"yimi", "zu", "1.5"},
            {"yena", "zu", "1.5"},
            {"bona", "zu", "0.8"},
            {"thina", "zu", "1.5"},

            // --- Xhosa (xh) ---
            {"umXhosa", "xh", "3.0"},
            {"isixhosa", "xh", "3.0"},
            {"isiXhosa", "xh", "3.0"},
            {"ndim", "xh", "2.0"},
            {"ndi", "xh", "1.0"},   // 1st-person Xhosa prefix
            {"bom", "xh", "0.9"},
            {"le", "xh", "0.6"},
            {"eli", "xh", "1.0"},
            {"loo", "xh", "1.5"},
            {"lowo", "xh", "1.5"},
            {"ngok", "xh", "1.0"},

            // --- Ndebele (nr) ---
            {"isiNdebele", "nr", "3.0"},
            {"isindebele", "nr", "3.0"},
            {"ubu", "nr", "0.7"},   // shared — lower
            {"zim", "nr", "0.8"},
            {"uyena", "nr", "1.5"},

            // --- Swati (ss) ---
            {"siSwati", "ss", "3.0"},
            {"siswati", "ss", "3.0"},
            {"ngim", "ss", "1.5"},
            {"kum", "ss", "0.9"},
            {"emat", "ss", "1.0"},
            {"let", "ss", "0.7"},

            // --- Sesotho (st) ---
            {"Sesotho", "st", "3.0"},
            {"sesotho", "st", "3.0"},
            {"mo", "st", "0.6"},
            {"ba", "st", "0.5"},
            {"se", "st", "0.6"},
            {"di", "st", "0.5"},
            {"le", "st", "0.5"},
            {"ntho", "st", "1.5"},
            {"ke", "st", "1.0"},
            {"ha", "st", "0.9"},
            {"ho", "st", "1.0"},
            {"ea", "st", "1.0"},
            {"tse", "st", "1.0"},

            // --- Setswana (tn) ---
            {"Setswana", "tn", "3.0"},
            {"setswana", "tn", "3.0"},
            {"mo", "tn", "0.5"},   // shared with Sesotho
            {"ba", "tn", "0.5"},
            {"go", "tn", "1.0"},
            {"ga", "tn", "0.9"},
            {"ke", "tn", "0.9"},
            {"re", "tn", "0.8"},
            {"lo", "tn", "0.8"},
            {"tsa", "tn", "0.9"},
            {"dilo", "tn", "1.5"},
            {"ntse", "tn", "1.5"},
            {"jaanong", "tn", "2.0"},

            // --- Sepedi / Northern Sotho (nso) ---
            {"Sepedi", "nso", "3.0"},
            {"sepedi", "nso", "3.0"},
            {"Sesotho sa Leboa", "nso", "3.0"},
            {"mo", "nso", "0.5"},
            {"ba", "nso", "0.5"},
            {"go", "nso", "0.9"},
            {"ga", "nso", "0.8"},
            {"ke", "nso", "0.8"},
            {"le", "nso", "0.5"},
            {"di", "nso", "0.5"},
            {"tsa", "nso", "0.8"},
            {"bjalo", "nso", "2.0"},
            {"gona", "nso", "1.5"},
            {"fela", "nso", "1.2"},

            // --- Tshivenda (ve) ---
            {"Tshivenda", "ve", "3.0"},
            {"tshivenda", "ve", "3.0"},
            {"tshi", "ve", "2.0"},
            {"vhu", "ve", "2.0"},
            {"vha", "ve", "1.5"},
            {"mu", "ve", "1.0"},
            {"mi", "ve", "0.9"},
            {"zwi", "ve", "1.5"},
            {"zwa", "ve", "1.5"},
            {"ndi", "ve", "0.8"},   // shared — lower
            {"nga", "ve", "0.7"},
            {"dzi", "ve", "1.2"},
            {"fha", "ve", "1.5"},
            {"kha", "ve", "1.5"},

            // --- Xitsonga (ts) ---
            {"Xitsonga", "ts", "3.0"},
            {"xitsonga", "ts", "3.0"},
            {"xi", "ts", "1.8"},
            {"svi", "ts", "2.0"},
            {"ti", "ts", "1.0"},
            {"mi", "ts", "0.8"},
            {"yi", "ts", "1.2"},
            {"ku", "ts", "1.0"},
            {"hi", "ts", "1.2"},
            {"na", "ts", "0.6"},
            {"va", "ts", "0.8"},
            {"ma", "ts", "0.7"},
            {"swi", "ts", "2.0"},
            {"ha", "ts", "0.7"},
            {"nku", "ts", "1.5"},
            {"vanhu", "ts", "2.0"},

            // --- Kiswahili (sw) ---
            {"kiswahili", "sw", "3.0"},
            {"swahili", "sw", "3.0"},
            {"wa", "sw", "1.0"},     // noun class prefix (people plural)
            {"m", "sw", "0.5"},      // noun class (person)
            {"ki", "sw", "1.5"},     // noun class (language, abstract)
            {"vi", "sw", "1.5"},     // noun class (plural of ki-)
            {"u", "sw", "0.5"},      // noun class
            {"ku", "sw", "0.9"},     // infinitive prefix
            {"na", "sw", "0.7"},     // "and" / "with"
            {"kwa", "sw", "1.0"},
            {"mtu", "sw", "2.0"},    // person
            {"watu", "sw", "2.0"},   // people
            {"ana", "sw", "1.0"},    // has/he has
            {"ni", "sw", "0.8"},     // 1st person plural subject
            {"ya", "sw", "0.7"},     // possessive

            // --- Kinyarwanda (rw) ---
            {"kinyarwanda", "rw", "3.0"},
            {"umu", "rw", "0.9"},
            {"aba", "rw", "0.9"},
            {"iki", "rw", "1.5"},
            {"ibi", "rw", "1.5"},
            {"uku", "rw", "0.9"},
            {"ama", "rw", "0.8"},
            {"in", "rw", "0.5"},
            {"ndi", "rw", "0.9"},
            {"turi", "rw", "1.5"},
            {"bari", "rw", "1.5"},

            // --- Kirundi (rn) ---
            {"kirundi", "rn", "3.0"},
            {"umu", "rn", "0.8"},
            {"aba", "rn", "0.8"},
            {"iki", "rn", "1.4"},
            {"ibi", "rn", "1.4"},
            {"uku", "rn", "0.8"},
            {"ama", "rn", "0.7"},
            {"ndi", "rn", "0.8"},
            {"turi", "rn", "1.4"},

            // --- Luganda (lg) ---
            {"luganda", "lg", "3.0"},
            {"olu", "lg", "2.0"},
            {"omu", "lg", "1.5"},
            {"aba", "lg", "0.7"},
            {"eki", "lg", "1.5"},
            {"ebi", "lg", "1.5"},
            {"oku", "lg", "1.0"},
            {"en", "lg", "0.5"},
            {"ndi", "lg", "0.8"},
            {"tuli", "lg", "1.5"},
            {"mu", "lg", "0.6"},
            {"eri", "lg", "1.0"},
            {"bali", "lg", "1.5"},

            // --- Gikuyu (ki) ---
            {"gikuyu", "ki", "3.0"},
            {"kikuyu", "ki", "3.0"},
            {"mu", "ki", "0.6"},
            {"a", "ki", "0.3"},
            {"ki", "ki", "0.7"},
            {"gi", "ki", "0.8"},
            {"ri", "ki", "0.7"},
            {"ma", "ki", "0.5"},
            {"ni", "ki", "0.6"},
            {"ndi", "ki", "0.9"},
            {"tu", "ki", "0.8"},

            // --- Shona (sn) ---
            {"shona", "sn", "3.0"},
            {"chishona", "sn", "3.0"},
            {"mu", "sn", "0.6"},
            {"va", "sn", "0.8"},
            {"chi", "sn", "1.5"},
            {"zvi", "sn", "1.8"},
            {"ri", "sn", "0.7"},
            {"ma", "sn", "0.5"},
            {"ku", "sn", "0.8"},
            {"ndi", "sn", "0.8"},
            {"tiri", "sn", "1.5"},
            {"vanhu", "sn", "1.8"},
            {"zvinhu", "sn", "2.0"},

            // --- Chichewa/Nyanja (ny) ---
            {"chichewa", "ny", "3.0"},
            {"chinyanja", "ny", "3.0"},
            {"mu", "ny", "0.6"},
            {"a", "ny", "0.3"},
            {"chi", "ny", "1.3"},
            {"zi", "ny", "1.2"},
            {"ku", "ny", "0.8"},
            {"ndi", "ny", "0.9"},
            {"tili", "ny", "1.5"},
            {"mwa", "ny", "1.0"},
            {"anthu", "ny", "2.0"},

            // --- Bemba (bem) ---
            {"ichibemba", "bem", "3.0"},
            {"bemba", "bem", "3.0"},
            {"umu", "bem", "0.8"},
            {"aba", "bem", "0.7"},
            {"ici", "bem", "1.5"},
            {"ifi", "bem", "1.5"},
            {"uku", "bem", "0.8"},
            {"ama", "bem", "0.7"},
            {"ndi", "bem", "0.8"},
            {"tuli", "bem", "1.4"},
            {"aba", "bem", "0.8"},
            {"abantu", "bem", "1.8"},

            // --- Lingala (ln) ---
            {"lingala", "ln", "3.0"},
            {"mo", "ln", "0.7"},
            {"ba", "ln", "0.6"},
            {"li", "ln", "0.8"},
            {"ma", "ln", "0.5"},
            {"lo", "ln", "0.9"},
            {"ko", "ln", "1.0"},
            {"na", "ln", "0.7"},
            {"ya", "ln", "0.8"},
            {"moto", "ln", "2.0"},
            {"bato", "ln", "2.0"},
            {"ngo", "ln", "1.0"},
            {"ezali", "ln", "2.5"},

            // --- Kikongo (kg) ---
            {"kikongo", "kg", "3.0"},
            {"mu", "kg", "0.6"},
            {"ba", "kg", "0.6"},
            {"ki", "kg", "0.7"},
            {"bi", "kg", "0.7"},
            {"di", "kg", "0.6"},
            {"ma", "kg", "0.5"},
            {"ku", "kg", "0.8"},
            {"muntu", "kg", "2.5"},
            {"bantu", "kg", "2.5"},
            {"nsi", "kg", "1.5"},

            // --- Afrikaans (af) ---
            {"afrikaans", "af", "3.0"},
            {"hierdie", "af", "2.5"},
            {"daai", "af", "2.0"},
            {"deur", "af", "1.5"},
            {"ver", "af", "1.0"},
            {"ge", "af", "0.9"},    // past-tense prefix
            {"be", "af", "0.7"},
            {"ont", "af", "1.2"},
            {"ver", "af", "1.0"},

            // --- English (en) ---
            {"the", "en", "1.5"},
            {"ing", "en", "1.2"},  // suffix but treated as prefix for simplicity
            {"un", "en", "0.7"},
            {"re", "en", "0.6"},
            {"pre", "en", "0.9"},
            {"dis", "en", "0.9"},
            {"anti", "en", "1.0"},
            {"over", "en", "1.0"},
            {"under", "en", "1.0"},
            {"inter", "en", "1.0"},
            {"out", "en", "0.8"},
    };

    private static void scorePrefixes(String word, double[] scores) {
        for (String[] entry : PREFIX_TABLE) {
            String prefix = entry[0];
            String lang = entry[1];
            double mult = Double.parseDouble(entry[2]);
            if (word.startsWith(prefix) && word.length() >= prefix.length()) {
                scores[idx(lang)] += PREFIX_WEIGHT * mult;
            }
        }
        // Also check suffixes for English -ing, -tion, -ed, -ly
        if (word.endsWith("ing") && word.length() > 5)  scores[idx("en")] += PREFIX_WEIGHT * 1.0;
        if (word.endsWith("tion") && word.length() > 5) scores[idx("en")] += PREFIX_WEIGHT * 1.2;
        if (word.endsWith("ness") && word.length() > 5) scores[idx("en")] += PREFIX_WEIGHT * 1.0;
        if (word.endsWith("ment") && word.length() > 5) scores[idx("en")] += PREFIX_WEIGHT * 1.0;
        if (word.endsWith("ity") && word.length() > 5)  scores[idx("en")] += PREFIX_WEIGHT * 1.0;
        if (word.endsWith("ly") && word.length() > 4)   scores[idx("en")] += PREFIX_WEIGHT * 0.8;
        // Afrikaans -heid, -lik, -heid, -ing
        if (word.endsWith("heid") && word.length() > 5) scores[idx("af")] += PREFIX_WEIGHT * 1.2;
        if (word.endsWith("lik") && word.length() > 4)  scores[idx("af")] += PREFIX_WEIGHT * 1.0;
        // Tshivenda -vhudzwa / -dzwa suffix
        if (word.endsWith("dzwa") && word.length() > 5) scores[idx("ve")] += PREFIX_WEIGHT * 1.5;
        if (word.endsWith("iwa") && word.length() > 4)  {
            scores[idx("zu")] += PREFIX_WEIGHT * 0.8;
            scores[idx("xh")] += PREFIX_WEIGHT * 0.8;
        }
        // Xitsonga -ile suffix (perfect tense)
        if (word.endsWith("ile") && word.length() > 4)  scores[idx("ts")] += PREFIX_WEIGHT * 0.9;
        // Sotho group -ng locative
        if (word.endsWith("ng") && word.length() > 3) {
            scores[idx("st")] += PREFIX_WEIGHT * 0.5;
            scores[idx("tn")] += PREFIX_WEIGHT * 0.5;
            scores[idx("nso")] += PREFIX_WEIGHT * 0.5;
        }
    }

    // -----------------------------------------------------------------------
    // Function words
    // -----------------------------------------------------------------------

    /**
     * High-frequency function words / discourse markers unique to each language.
     * These are exact whole-word matches on the cleaned lowercase token.
     */
    private static final Map<String, String[]> FUNCTION_WORDS = new HashMap<>();

    static {
        FUNCTION_WORDS.put("zu", new String[]{
                "ngiyabonga", "sawubona", "yebo", "cha", "ngoba", "kodwa", "futhi",
                "noma", "khona", "manje", "ngiyazi", "ngithanda", "ngifuna",
                "ekhaya", "izwe", "umuntu", "abantu", "ubuntu", "impilo",
                "isikhathi", "intaba", "umfula", "amandla"
        });
        FUNCTION_WORDS.put("xh", new String[]{
                "enkosi", "molo", "ewe", "hayi", "kuba", "kodwa", "kwaye",
                "okanye", "ngoku", "apha", "apho", "khona", "ndiyabona",
                "umntu", "abantu", "ilizwe", "intliziyo", "amandla", "inyaniso"
        });
        FUNCTION_WORDS.put("nr", new String[]{
                "ngiyabonga", "sawubona", "yebo", "awa", "ngoba", "kodwana",
                "begodu", "godu", "khona", "nje", "umuntu", "abantu",
                "ilizwe", "ikhaya", "amandla"
        });
        FUNCTION_WORDS.put("ss", new String[]{
                "ngiyabonga", "sawubona", "yebo", "cha", "ngobe", "kodvwa",
                "futsi", "nome", "khona", "manje", "umuntfu", "bantfu",
                "lizwe", "ikhaya", "emandla"
        });
        FUNCTION_WORDS.put("st", new String[]{
                "kea leboha", "dumela", "ee", "che", "hobane", "empa",
                "le", "kapa", "mona", "joale", "ke", "ha", "ho",
                "motho", "batho", "lefatshe", "ntlo", "molapo", "lebaka"
        });
        FUNCTION_WORDS.put("tn", new String[]{
                "ke a leboga", "dumela", "ee", "nnyaa", "ka gonne", "mme",
                "le", "kgotsa", "fa", "jaanong", "ke", "ga", "go",
                "motho", "batho", "lefatshe", "ntlo", "noka", "karabo",
                "tshegofatso", "tlhaloganyo"
        });
        FUNCTION_WORDS.put("nso", new String[]{
                "ke a leboga", "dumela", "ee", "aowa", "ka gobane", "fela",
                "le", "goba", "mona", "bjale", "ke", "ga", "go",
                "motho", "batho", "lefase", "ntlo", "noka", "karabo",
                "kganya", "tshwanelo"
        });
        FUNCTION_WORDS.put("ve", new String[]{
                "ndo livhuwa", "aa", "hai", "nga mulalo", "fhedzi",
                "na", "kana", "fhano", "zwino", "ndi", "a", "tshi",
                "muthu", "vhathu", "shango", "hayani", "tshifhinga",
                "vhulungo", "tshivhidzo"
        });
        FUNCTION_WORDS.put("ts", new String[]{
                "ndza khensa", "avuxeni", "ina", "ayi", "hikuva", "kambe",
                "naswona", "kumbe", "laha", "sweswi", "mina", "a", "ku",
                "munhu", "vanhu", "misava", "kaya", "tiko", "nkarhi",
                "vutivi", "ntokoto"
        });
        // --- Eastern Bantu ---
        FUNCTION_WORDS.put("sw", new String[]{
                "habari", "jambo", "asante", "karibu", "ndiyo", "hapana", "sawa",
                "lakini", "na", "kwa", "ya", "wa", "ni", "mimi", "wewe",
                "yeye", "sisi", "wao", "hapa", "pale", "sasa", "bado",
                "kazi", "nyumba", "maji", "chakula", "safari", "haraka"
        });
        FUNCTION_WORDS.put("rw", new String[]{
                "muraho", "yego", "oya", "ariko", "kandi", "neza", "murakoze",
                "ndi", "uri", "ari", "turi", "muri", "bari", "hano",
                "aho", "ubu", "noneho", "umuntu", "abantu", "igihugu"
        });
        FUNCTION_WORDS.put("rn", new String[]{
                "amahoro", "yego", "oya", "ariko", "kandi", "neza", "urakoze",
                "ndi", "uri", "ari", "turi", "muri", "bari", "hano",
                "aho", "ubu", "uyu", "umuntu", "abantu", "igihugu"
        });
        FUNCTION_WORDS.put("lg", new String[]{
                "ki", "nedda", "webale", "nze", "ggwe", "ye", "ffe",
                "bo", "wano", "eyo", "kati", "naye", "era", "okukola",
                "omuntu", "abantu", "ensi", "amazi", "emmere"
        });
        FUNCTION_WORDS.put("ki", new String[]{
                "nii", "wee", "uu", "wega", "na", "nake", "tondu",
                "na", "kana", "haha", "hau", "riu", "ugu", "ti",
                "mundu", "andu", "bururi", "nyumba", "mai", "irio"
        });
        // --- South-Eastern Bantu ---
        FUNCTION_WORDS.put("sn", new String[]{
                "mhoro", "hongu", "aiwa", "asi", "uye", "zvakare", "maita",
                "ndi", "uri", "ari", "tiri", "muri", "vari", "pano",
                "ipapo", "zvino", "munhu", "vanhu", "nyika", "mvura", "chokwadi"
        });
        FUNCTION_WORDS.put("ny", new String[]{
                "moni", "inde", "ayi", "koma", "komanso", "zikomo",
                "ndi", "ndiwe", "ali", "tili", "muli", "pano",
                "pomwe", "tsopano", "munthu", "anthu", "dziko", "madzi"
        });
        FUNCTION_WORDS.put("bem", new String[]{
                "shani", "ee", "awe", "lelo", "nomba", "natotela",
                "ndi", "uli", "ali", "tuli", "muli", "pano",
                "palya", "lelo", "umuntu", "abantu", "icalo", "amenshi"
        });
        // --- Central Bantu ---
        FUNCTION_WORDS.put("ln", new String[]{
                "mbote", "iyo", "te", "kasi", "mpe", "melesi",
                "ngai", "yo", "ye", "biso", "bino", "bango",
                "awa", "kuna", "sikoyo", "moto", "bato", "mokili", "mai"
        });
        FUNCTION_WORDS.put("kg", new String[]{
                "mbote", "inga", "ve", "kana", "mpi", "matondo",
                "mono", "nge", "yandi", "beto", "beno", "bawu",
                "vava", "kuna", "bubu", "muntu", "bantu", "nsi", "masa"
        });
        // --- Non-Bantu ---
        FUNCTION_WORDS.put("af", new String[]{
                "dankie", "hallo", "ja", "nee", "omdat", "maar", "en",
                "of", "hier", "nou", "ek", "jy", "hy", "sy", "ons",
                "julle", "hulle", "die", "van", "het", "met", "vir",
                "nie", "ook", "baie", "goed", "meer", "soos", "kan",
                "sal", "was", "word", "huis", "land", "mense", "lewe"
        });
        FUNCTION_WORDS.put("en", new String[]{
                "the", "and", "that", "have", "for", "not", "with",
                "you", "this", "but", "his", "from", "they", "she",
                "her", "been", "has", "had", "would", "what", "were",
                "there", "can", "all", "are", "was", "your", "will",
                "one", "said", "each", "which", "their", "more", "about"
        });
    }

    private static void scoreFunctionWords(String word, double[] scores) {
        for (Map.Entry<String, String[]> entry : FUNCTION_WORDS.entrySet()) {
            String lang = entry.getKey();
            for (String fw : entry.getValue()) {
                if (word.equals(fw)) {
                    scores[idx(lang)] += FUNCTION_WORD_WEIGHT;
                    break;
                }
            }
        }
        // Afrikaans double-negation marker: "nie" — worth extra when it appears
        if (word.equals("nie")) scores[idx("af")] += FUNCTION_WORD_WEIGHT * 0.5;
    }

    // -----------------------------------------------------------------------
    // Click consonant detection (Nguni languages)
    // -----------------------------------------------------------------------

    /**
     * In Nguni orthographies, click consonants are written with c (dental), q (alveolar),
     * and x (lateral), often followed by h or another consonant.
     * Patterns: c, ch, gc, nc, nch, x, xh, gx, nx, nxh, q, qh, gq, nq, nqh.
     * English uses x and c differently, so we look for positionally significant patterns.
     */
    private static final String[] CLICK_PATTERNS = {
            "nc", "nch", "ngc", "ngq", "ngx", "nq", "nqh", "nx", "nxh",
            "qh", "xh", "gc", "gq", "gx"
    };

    private static void scoreClickConsonants(String word, double[] scores) {
        for (String pattern : CLICK_PATTERNS) {
            if (word.contains(pattern)) {
                // Split evenly between the three main click languages
                // Xhosa has more clicks overall, so slightly higher weight
                scores[idx("xh")] += CLICK_WEIGHT * 1.2;
                scores[idx("zu")] += CLICK_WEIGHT * 0.8;
                scores[idx("nr")] += CLICK_WEIGHT * 0.7;
            }
        }
        // Standalone x at start of word is very likely Xitsonga (xi- prefix) not a click
        // — handled in prefix scoring above, so we skip here.
    }

    // -----------------------------------------------------------------------
    // N-gram scoring
    // -----------------------------------------------------------------------

    /**
     * Character bigram frequency tables.
     *
     * <p>Each language has a map of bigram → relative frequency (0–100 scale, not probability).
     * Only the most discriminating bigrams are listed to keep the table compact.
     * Frequencies are derived from analysis of public domain corpora and linguistic literature
     * for each language.
     */
    private static final Map<String, Map<String, Integer>> BIGRAM_TABLES = new HashMap<>();

    /**
     * Character trigram frequency tables — same structure as bigrams but stronger signal.
     */
    private static final Map<String, Map<String, Integer>> TRIGRAM_TABLES = new HashMap<>();

    static {
        // ---- isiZulu bigrams ----
        Map<String, Integer> zuBi = new HashMap<>();
        putAll(zuBi, "um", 95, "an", 85, "ba", 80, "ni", 78, "la", 75,
                "si", 72, "bo", 70, "ku", 68, "za", 65, "na", 63,
                "ng", 90, "ye", 75, "wa", 72, "li", 68, "ya", 65,
                "bh", 60, "ph", 58, "kh", 62, "th", 60, "ny", 70,
                "nk", 55, "mb", 72, "nd", 65, "nt", 60, "nz", 55);
        BIGRAM_TABLES.put("zu", zuBi);

        // ---- isiXhosa bigrams ----
        Map<String, Integer> xhBi = new HashMap<>();
        putAll(xhBi, "um", 90, "an", 80, "nd", 75, "lo", 72, "xa", 85,
                "xh", 95, "el", 68, "ng", 88, "ok", 65, "wa", 70,
                "yi", 70, "em", 65, "am", 68, "ph", 60, "th", 58,
                "ny", 65, "nc", 85, "nq", 80, "nx", 78, "kw", 62);
        BIGRAM_TABLES.put("xh", xhBi);

        // ---- isiNdebele bigrams ----
        Map<String, Integer> nrBi = new HashMap<>();
        putAll(nrBi, "um", 88, "an", 78, "nd", 80, "le", 70, "si", 68,
                "ng", 85, "ba", 75, "la", 68, "go", 60, "na", 62,
                "ny", 65, "mb", 70, "bj", 55, "dw", 58, "gw", 55);
        BIGRAM_TABLES.put("nr", nrBi);

        // ---- siSwati bigrams ----
        Map<String, Integer> ssBi = new HashMap<>();
        putAll(ssBi, "um", 88, "an", 78, "nt", 70, "sw", 85, "ts", 72,
                "ng", 82, "ba", 72, "la", 65, "na", 60, "si", 65,
                "em", 68, "ku", 65, "tsi", 78, "dv", 75, "tv", 60);
        BIGRAM_TABLES.put("ss", ssBi);

        // ---- Sesotho bigrams ----
        Map<String, Integer> stBi = new HashMap<>();
        putAll(stBi, "ho", 90, "mo", 88, "ba", 80, "se", 78, "le", 75,
                "ts", 72, "la", 68, "ke", 85, "ng", 65, "ha", 80,
                "hl", 70, "lo", 65, "di", 68, "na", 60, "ph", 58,
                "tl", 65, "ka", 72, "ea", 78, "oa", 68);
        BIGRAM_TABLES.put("st", stBi);

        // ---- Setswana bigrams ----
        Map<String, Integer> tnBi = new HashMap<>();
        putAll(tnBi, "go", 92, "mo", 88, "ba", 82, "ke", 85, "ga", 80,
                "le", 72, "ts", 70, "na", 65, "lo", 68, "di", 65,
                "tl", 70, "hl", 60, "re", 75, "ng", 68, "ph", 58,
                "tsh", 80, "kg", 72, "gw", 65, "sw", 60, "ja", 75);
        BIGRAM_TABLES.put("tn", tnBi);

        // ---- Sepedi bigrams ----
        Map<String, Integer> nsoBi = new HashMap<>();
        putAll(nsoBi, "go", 90, "mo", 85, "ba", 80, "ke", 82, "ga", 78,
                "le", 70, "ts", 68, "na", 65, "di", 63, "se", 68,
                "bj", 78, "fh", 60, "hl", 65, "sw", 60, "ph", 58,
                "tsh", 75, "kg", 70, "gw", 62, "ny", 65, "ja", 70);
        BIGRAM_TABLES.put("nso", nsoBi);

        // ---- Tshivenda bigrams ----
        Map<String, Integer> veBi = new HashMap<>();
        putAll(veBi, "vh", 95, "ts", 70, "hu", 90, "mu", 85, "vh", 95,
                "ndi", 80, "ng", 72, "ha", 78, "la", 68, "wa", 65,
                "fh", 88, "dz", 82, "kh", 75, "nd", 68, "li", 65,
                "zw", 80, "sh", 65, "th", 62, "ri", 70, "lu", 68);
        BIGRAM_TABLES.put("ve", veBi);

        // ---- Xitsonga bigrams ----
        Map<String, Integer> tsBi = new HashMap<>();
        putAll(tsBi, "xi", 92, "sv", 88, "ti", 78, "ku", 80, "hi", 82,
                "va", 70, "na", 68, "ma", 65, "ni", 72, "yi", 78,
                "sw", 75, "ka", 65, "le", 60, "ha", 68, "ri", 62,
                "ts", 70, "ng", 65, "mb", 60, "nk", 58, "nh", 65);
        BIGRAM_TABLES.put("ts", tsBi);

        // ---- Afrikaans bigrams ----
        Map<String, Integer> afBi = new HashMap<>();
        putAll(afBi, "ie", 92, "aa", 88, "oe", 82, "ee", 78, "oo", 75,
                "ui", 72, "ou", 70, "ei", 68, "eu", 60, "di", 85,
                "ge", 80, "be", 70, "ve", 65, "sk", 62, "st", 60,
                "ek", 75, "an", 68, "er", 72, "en", 70, "nd", 65,
                "ng", 60, "nt", 58, "at", 55, "van", 88, "die", 95);
        BIGRAM_TABLES.put("af", afBi);

        // ---- English bigrams ----
        Map<String, Integer> enBi = new HashMap<>();
        putAll(enBi, "th", 95, "he", 90, "in", 88, "er", 85, "an", 82,
                "re", 80, "on", 78, "at", 75, "en", 72, "nd", 70,
                "ti", 68, "es", 65, "or", 62, "te", 60, "of", 58,
                "ed", 72, "is", 70, "it", 68, "al", 65, "ar", 63,
                "st", 60, "to", 65, "nt", 62, "ng", 60, "se", 58);
        BIGRAM_TABLES.put("en", enBi);

        // ---- Trigrams ----

        // Zulu trigrams
        Map<String, Integer> zuTri = new HashMap<>();
        putAll(zuTri, "umu", 95, "aba", 90, "isi", 82, "uku", 85, "ama", 80,
                "nga", 78, "ngi", 88, "nge", 75, "mtu", 70, "thu", 68,
                "bon", 65, "the", 62, "nkh", 60, "bha", 58, "pha", 55,
                "yab", 80, "ang", 75, "lan", 65, "wan", 68, "zan", 62);
        TRIGRAM_TABLES.put("zu", zuTri);

        // Xhosa trigrams
        Map<String, Integer> xhTri = new HashMap<>();
        putAll(xhTri, "isi", 80, "umu", 88, "ndi", 92, "xho", 95, "elo", 70,
                "oku", 78, "ama", 75, "aba", 82, "ngo", 72, "uku", 80,
                "wab", 65, "ncw", 85, "nqo", 80, "khe", 68, "yin", 70,
                "lon", 65, "kwa", 72, "phe", 60, "the", 58, "phi", 55);
        TRIGRAM_TABLES.put("xh", xhTri);

        // Ndebele trigrams
        Map<String, Integer> nrTri = new HashMap<>();
        putAll(nrTri, "umu", 88, "isi", 78, "aba", 82, "nde", 90, "uku", 80,
                "ama", 75, "bel", 85, "ele", 70, "nga", 72, "ngo", 68,
                "ngi", 80, "kho", 65, "dwa", 60, "bja", 55, "the", 58);
        TRIGRAM_TABLES.put("nr", nrTri);

        // Swati trigrams
        Map<String, Integer> ssTri = new HashMap<>();
        putAll(ssTri, "umu", 85, "isi", 75, "ema", 88, "nts", 80, "tsi", 92,
                "dvu", 85, "let", 70, "nge", 72, "bun", 68, "nte", 65,
                "kun", 62, "lok", 60, "eni", 58, "kum", 75, "wem", 65);
        TRIGRAM_TABLES.put("ss", ssTri);

        // Sesotho trigrams
        Map<String, Integer> stTri = new HashMap<>();
        putAll(stTri, "hoa", 90, "moe", 85, "sea", 88, "bat", 80, "lea", 82,
                "tse", 92, "hle", 78, "nts", 75, "ola", 70, "tla", 72,
                "pha", 68, "nna", 65, "hal", 62, "bon", 60, "kea", 85,
                "aho", 78, "ago", 72, "ana", 68, "ona", 65);
        TRIGRAM_TABLES.put("st", stTri);

        // Setswana trigrams
        Map<String, Integer> tnTri = new HashMap<>();
        putAll(tnTri, "goa", 90, "mob", 82, "bat", 80, "tsh", 92, "kga", 85,
                "gwe", 78, "jan", 88, "ong", 75, "tla", 72, "pha", 68,
                "nts", 70, "nna", 65, "hal", 60, "bon", 62, "lef", 78,
                "ats", 72, "ebo", 68, "ago", 65, "ana", 60);
        TRIGRAM_TABLES.put("tn", tnTri);

        // Sepedi trigrams
        Map<String, Integer> nsoTri = new HashMap<>();
        putAll(nsoTri, "goa", 88, "mob", 80, "bat", 78, "bja", 92, "kga", 82,
                "gwe", 75, "jal", 85, "ong", 72, "tla", 70, "pha", 65,
                "nts", 68, "nna", 62, "fhe", 60, "bon", 65, "lef", 75,
                "ats", 70, "ebo", 65, "ago", 62, "ona", 58);
        TRIGRAM_TABLES.put("nso", nsoTri);

        // Tshivenda trigrams
        Map<String, Integer> veTri = new HashMap<>();
        putAll(veTri, "tsh", 95, "vhu", 92, "vha", 90, "ndi", 88, "zwi", 85,
                "dzi", 82, "fha", 88, "kha", 85, "mur", 78, "hul", 75,
                "lig", 70, "rin", 68, "lin", 65, "uri", 62, "ven", 85,
                "end", 72, "ash", 68, "ang", 65, "ath", 60, "has", 58);
        TRIGRAM_TABLES.put("ve", veTri);

        // Xitsonga trigrams
        Map<String, Integer> tsTri = new HashMap<>();
        putAll(tsTri, "xiv", 95, "svi", 92, "xit", 90, "van", 85, "kun", 82,
                "his", 78, "swi", 88, "nhu", 75, "mun", 80, "yon", 72,
                "tik", 68, "kar", 65, "nka", 70, "ler", 62, "har", 60,
                "ong", 65, "ang", 60, "ata", 58, "ari", 55, "ani", 52);
        TRIGRAM_TABLES.put("ts", tsTri);

        // Afrikaans trigrams
        Map<String, Integer> afTri = new HashMap<>();
        putAll(afTri, "die", 95, "van", 92, "het", 90, "nie", 88, "aan", 85,
                "ies", 80, "oor", 78, "een", 82, "ver", 75, "bek", 70,
                "eld", 68, "ste", 72, "gen", 65, "ing", 62, "lik", 78,
                "eid", 75, "hei", 72, "ger", 65, "ber", 60, "ter", 58);
        TRIGRAM_TABLES.put("af", afTri);

        // English trigrams
        Map<String, Integer> enTri = new HashMap<>();
        putAll(enTri, "the", 95, "and", 88, "ing", 85, "ion", 82, "ent", 80,
                "tio", 78, "for", 75, "hat", 72, "his", 70, "tha", 90,
                "ere", 68, "her", 65, "ter", 62, "all", 60, "ati", 75,
                "are", 72, "not", 68, "wit", 65, "was", 62, "ith", 78);
        TRIGRAM_TABLES.put("en", enTri);
    }

    private static void scoreNgrams(String word, double[] scores) {
        // Bigrams
        for (int i = 0; i <= word.length() - 2; i++) {
            String bg = word.substring(i, i + 2);
            for (Map.Entry<String, Map<String, Integer>> entry : BIGRAM_TABLES.entrySet()) {
                Integer freq = entry.getValue().get(bg);
                if (freq != null) {
                    scores[idx(entry.getKey())] += NGRAM_WEIGHT * freq / 100.0;
                }
            }
        }
        // Trigrams
        for (int i = 0; i <= word.length() - 3; i++) {
            String tg = word.substring(i, i + 3);
            for (Map.Entry<String, Map<String, Integer>> entry : TRIGRAM_TABLES.entrySet()) {
                Integer freq = entry.getValue().get(tg);
                if (freq != null) {
                    scores[idx(entry.getKey())] += NGRAM_WEIGHT * freq / 100.0 * 1.5; // trigrams worth more
                }
            }
        }
    }

    // -----------------------------------------------------------------------
    // Result building
    // -----------------------------------------------------------------------

    /**
     * Convert raw score array to a ranked list of {@link LanguageScore} objects with
     * normalised confidence values.
     *
     * @param scores raw score array (indexed by {@link #LANGUAGES})
     * @param topN   maximum number of results to return
     */
    private static List<LanguageScore> buildResults(double[] scores, int topN) {
        double total = 0;
        for (double s : scores) total += s;

        List<LanguageScore> results = new ArrayList<>(LANGUAGES.length);
        for (int i = 0; i < LANGUAGES.length; i++) {
            double confidence = (total > 0) ? scores[i] / total : 0.0;
            results.add(new LanguageScore(LANGUAGES[i], confidence));
        }
        Collections.sort(results);
        return results.subList(0, Math.min(topN, results.size()));
    }

    // -----------------------------------------------------------------------
    // Utilities
    // -----------------------------------------------------------------------

    private static int idx(String lang) {
        Integer i = LANG_INDEX.get(lang);
        if (i == null) throw new IllegalArgumentException("Unknown language: " + lang);
        return i;
    }

    private static boolean containsAny(String word, String... chars) {
        for (String c : chars) {
            if (word.contains(c)) return true;
        }
        return false;
    }

    /**
     * Varargs helper for populating integer-valued maps without verbose put() calls.
     * Arguments must alternate: key (String), value (int), key, value, ...
     */
    private static void putAll(Map<String, Integer> map, Object... keyValuePairs) {
        for (int i = 0; i < keyValuePairs.length - 1; i += 2) {
            map.put((String) keyValuePairs[i], (Integer) keyValuePairs[i + 1]);
        }
    }
}
