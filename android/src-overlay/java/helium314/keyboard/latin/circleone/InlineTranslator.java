/*
 * Copyright (C) 2026 The Other Bhengu (Pty) Ltd t/a The Geek.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package helium314.keyboard.latin.circleone;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Provides inline translation between African languages using the free
 * MyMemory Translation API ({@code https://api.mymemory.translated.net}).
 *
 * <p>Usage:
 * <pre>
 *   InlineTranslator translator = new InlineTranslator();
 *   translator.translate("Sawubona", "zu", "en", new InlineTranslator.TranslationCallback() {
 *       &#64;Override public void onTranslated(String result) { /* post to UI *&#47; }
 *       &#64;Override public void onError(String message)    { /* show error  *&#47; }
 *   });
 * </pre>
 *
 * <p>Features:
 * <ul>
 *   <li>Async HTTP via single-thread {@link Executor} — never blocks the keyboard thread.</li>
 *   <li>Results are posted back to the Android main thread via {@link Handler}.</li>
 *   <li>LRU cache (max 100 entries) prevents redundant network calls.</li>
 *   <li>Rate limiting: at most 1 API request per second.</li>
 *   <li>Graceful offline fallback — {@link TranslationCallback#onError} is called
 *       with a user-friendly message instead of throwing.</li>
 * </ul>
 *
 * <p>Supported ISO 639-1 language codes:
 * {@code zu} (isiZulu), {@code xh} (isiXhosa), {@code nr} (isiNdebele),
 * {@code ss} (siSwati), {@code st} (Sesotho), {@code tn} (Setswana),
 * {@code nso} (Sesotho sa Leboa), {@code ve} (Tshivenda), {@code ts} (Xitsonga),
 * {@code af} (Afrikaans), {@code en} (English).
 */
public class InlineTranslator {

    private static final String TAG = "InlineTranslator";

    /** MyMemory free endpoint — no API key required for &lt;10 000 words/day. */
    private static final String API_BASE =
            "https://api.mymemory.translated.net/get?q=%s&langpair=%s|%s";

    /** Connection and read timeouts in milliseconds. */
    private static final int CONNECT_TIMEOUT_MS = 5_000;
    private static final int READ_TIMEOUT_MS    = 8_000;

    /** Maximum number of entries held in the LRU translation cache. */
    private static final int CACHE_MAX_ENTRIES = 100;

    /** Minimum gap between outbound API requests (milliseconds). */
    private static final long RATE_LIMIT_MS = 1_000L;

    // -------------------------------------------------------------------------
    // Language metadata
    // -------------------------------------------------------------------------

    /**
     * Display names for each supported ISO 639-1 code.
     * Keys are the codes accepted by {@link #translate} and {@link #getLanguageDisplayName}.
     */
    private static final Map<String, String> DISPLAY_NAMES;

    /**
     * Set of language codes that MyMemory supports well enough to use.
     * Codes absent from this set will receive a "not available" message.
     */
    private static final List<String> SUPPORTED_CODES = Arrays.asList(
            "zu", "xh", "ss", "st", "tn", "af", "en",
            "sw", "sn", "ny", "rw", "lg", "ln"
            // nr, nso, ve, ts, rn, ki, bem, kg — MyMemory support is poor; handled gracefully
    );

    static {
        Map<String, String> names = new LinkedHashMap<>();
        // Southern Bantu
        names.put("zu",  "isiZulu");
        names.put("xh",  "isiXhosa");
        names.put("nr",  "isiNdebele");
        names.put("ss",  "siSwati");
        names.put("st",  "Sesotho");
        names.put("tn",  "Setswana");
        names.put("nso", "Sesotho sa Leboa");
        names.put("ve",  "Tshivenda");
        names.put("ts",  "Xitsonga");
        // Eastern Bantu
        names.put("sw",  "Kiswahili");
        names.put("rw",  "Kinyarwanda");
        names.put("rn",  "Kirundi");
        names.put("lg",  "Luganda");
        names.put("ki",  "Gikuyu");
        // South-Eastern Bantu
        names.put("sn",  "Shona");
        names.put("ny",  "Chichewa");
        names.put("bem", "Bemba");
        // Central Bantu
        names.put("ln",  "Lingala");
        names.put("kg",  "Kikongo");
        // Non-Bantu
        names.put("af",  "Afrikaans");
        names.put("en",  "English");
        DISPLAY_NAMES = Collections.unmodifiableMap(names);
    }

    // -------------------------------------------------------------------------
    // Internal state
    // -------------------------------------------------------------------------

    /** Single background thread for all HTTP requests. */
    private final Executor executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "InlineTranslator-bg");
        t.setDaemon(true);
        return t;
    });

    /** Posts results back to the Android UI/main thread. */
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    /**
     * LRU cache keyed by {@code "sourceLang|targetLang|text"}.
     * Access-order LinkedHashMap gives O(1) LRU eviction.
     */
    private final Map<String, String> cache = Collections.synchronizedMap(
            new LinkedHashMap<String, String>(CACHE_MAX_ENTRIES, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
                    return size() > CACHE_MAX_ENTRIES;
                }
            });

    /** Timestamp (ms) of the last API call, used for rate limiting. */
    private final AtomicLong lastRequestTime = new AtomicLong(0L);

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Callback interface for asynchronous translation results.
     *
     * <p>Both methods are always invoked on the Android main thread.
     */
    public interface TranslationCallback {

        /**
         * Called when translation succeeds.
         *
         * @param result the translated text, never {@code null} or empty.
         */
        void onTranslated(String result);

        /**
         * Called when translation fails (network error, unsupported pair, etc.).
         *
         * @param message a user-displayable error description, never {@code null}.
         */
        void onError(String message);
    }

    /**
     * Translates {@code text} from {@code sourceLang} to {@code targetLang} asynchronously.
     *
     * <p>The callback is always delivered on the Android main thread. The method
     * returns immediately; do not block on it.
     *
     * @param text       the text to translate; must be non-null and non-empty.
     * @param sourceLang ISO 639-1 source language code (e.g. {@code "zu"}).
     * @param targetLang ISO 639-1 target language code (e.g. {@code "en"}).
     * @param callback   receives the result or error; must be non-null.
     * @throws IllegalArgumentException if {@code text} or {@code callback} is null/empty.
     */
    public void translate(
            final String text,
            final String sourceLang,
            final String targetLang,
            final TranslationCallback callback) {

        if (text == null || text.trim().isEmpty()) {
            throw new IllegalArgumentException("text must be non-null and non-empty");
        }
        if (callback == null) {
            throw new IllegalArgumentException("callback must not be null");
        }

        final String src = sourceLang != null ? sourceLang.toLowerCase().trim() : "en";
        final String tgt = targetLang != null ? targetLang.toLowerCase().trim() : "en";

        // Trivial case: same language
        if (src.equals(tgt)) {
            postResult(callback, text, null);
            return;
        }

        // Validate both languages are known
        if (!DISPLAY_NAMES.containsKey(src)) {
            postResult(callback, null, "Unknown source language code: " + src);
            return;
        }
        if (!DISPLAY_NAMES.containsKey(tgt)) {
            postResult(callback, null, "Unknown target language code: " + tgt);
            return;
        }

        // Check if the pair is supported by MyMemory
        if (!isPairSupported(src, tgt)) {
            postResult(callback, null,
                    "Translation not available for this language pair: "
                    + getLanguageDisplayName(src) + " → " + getLanguageDisplayName(tgt));
            return;
        }

        // Check LRU cache
        final String cacheKey = src + "|" + tgt + "|" + text;
        final String cached = cache.get(cacheKey);
        if (cached != null) {
            Log.d(TAG, "Cache hit for key length=" + cacheKey.length());
            postResult(callback, cached, null);
            return;
        }

        // Dispatch to background thread
        executor.execute(() -> performTranslation(text, src, tgt, cacheKey, callback));
    }

    /**
     * Returns a list of language pair strings ({@code "sourceLang|targetLang"}) that
     * this translator can reliably serve.
     *
     * <p>Pairs whose source or target code falls outside {@link #SUPPORTED_CODES} are
     * excluded because MyMemory's coverage for those languages is insufficient.
     *
     * @return immutable list of pair strings, e.g. {@code ["zu|en", "en|zu", "af|en", …]}.
     */
    public List<String> getSupportedLanguagePairs() {
        List<String> pairs = new ArrayList<>();
        for (String src : SUPPORTED_CODES) {
            for (String tgt : SUPPORTED_CODES) {
                if (!src.equals(tgt)) {
                    pairs.add(src + "|" + tgt);
                }
            }
        }
        return Collections.unmodifiableList(pairs);
    }

    /**
     * Returns the human-readable display name for a language code.
     *
     * @param code ISO 639-1 code (e.g. {@code "zu"}).
     * @return display name (e.g. {@code "isiZulu"}), or the code itself if unknown.
     */
    public String getLanguageDisplayName(String code) {
        if (code == null) return "Unknown";
        final String name = DISPLAY_NAMES.get(code.toLowerCase().trim());
        return name != null ? name : code;
    }

    /**
     * Returns all language codes supported by this translator.
     *
     * @return immutable list of ISO 639-1 codes.
     */
    public List<String> getAllLanguageCodes() {
        return new ArrayList<>(DISPLAY_NAMES.keySet());
    }

    /**
     * Evicts all entries from the in-memory translation cache.
     *
     * <p>Useful when the user switches keyboard language or input context.
     */
    public void clearCache() {
        cache.clear();
        Log.d(TAG, "Translation cache cleared");
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    /**
     * Returns {@code true} if both {@code src} and {@code tgt} are in
     * {@link #SUPPORTED_CODES}, meaning MyMemory handles the pair reliably.
     */
    private boolean isPairSupported(String src, String tgt) {
        return SUPPORTED_CODES.contains(src) && SUPPORTED_CODES.contains(tgt);
    }

    /**
     * Performs the blocking HTTP call on the background executor thread.
     * Rate-limiting sleep happens here. Results are posted to the main thread.
     */
    private void performTranslation(
            String text,
            String src,
            String tgt,
            String cacheKey,
            TranslationCallback callback) {

        // Rate limiting: wait until at least RATE_LIMIT_MS has elapsed since the
        // last outbound request.
        enforceRateLimit();

        HttpURLConnection connection = null;
        try {
            final String encoded = URLEncoder.encode(text, StandardCharsets.UTF_8.name());
            final String urlString = String.format(API_BASE, encoded, src, tgt);
            Log.d(TAG, "Requesting translation: " + src + " -> " + tgt
                    + " | text length=" + text.length());

            final URL url = new URL(urlString);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("User-Agent", "CircleOne-Keyboard/1.0");

            final int status = connection.getResponseCode();
            if (status != HttpURLConnection.HTTP_OK) {
                Log.w(TAG, "HTTP error " + status);
                postResult(callback, null,
                        "Translation unavailable (HTTP " + status + ")");
                return;
            }

            // Read response body
            final StringBuilder sb = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(connection.getInputStream(),
                            StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
            }

            final String translated = parseMyMemoryResponse(sb.toString(), text);
            if (translated != null) {
                cache.put(cacheKey, translated);
                postResult(callback, translated, null);
            } else {
                postResult(callback, null,
                        "Translation not available for this language pair.");
            }

        } catch (java.net.UnknownHostException e) {
            Log.w(TAG, "No network: " + e.getMessage());
            postResult(callback, null, "Translation unavailable (no internet connection).");
        } catch (java.net.SocketTimeoutException e) {
            Log.w(TAG, "Request timed out: " + e.getMessage());
            postResult(callback, null, "Translation unavailable (request timed out).");
        } catch (Exception e) {
            Log.e(TAG, "Translation error", e);
            postResult(callback, null, "Translation unavailable.");
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    /**
     * Parses the MyMemory JSON response and returns the translated string,
     * or {@code null} if the response indicates failure or low confidence.
     *
     * <p>MyMemory response shape:
     * <pre>
     * {
     *   "responseStatus": 200,
     *   "responseData": {
     *     "translatedText": "Hello",
     *     "match": 1.0
     *   }
     * }
     * </pre>
     *
     * @param json     raw JSON string from the API.
     * @param original original input text (returned as-is on parse failure).
     * @return translated text, or {@code null} if unusable.
     */
    private String parseMyMemoryResponse(String json, String original) {
        try {
            final JSONObject root = new JSONObject(json);
            final int status = root.optInt("responseStatus", 0);

            if (status != 200) {
                Log.w(TAG, "MyMemory status=" + status);
                return null;
            }

            final JSONObject data = root.optJSONObject("responseData");
            if (data == null) {
                Log.w(TAG, "Missing responseData");
                return null;
            }

            final String translated = data.optString("translatedText", "").trim();
            if (translated.isEmpty()) {
                return null;
            }

            // MyMemory returns the original text in upper-case when it has no translation
            if (translated.equalsIgnoreCase(original)) {
                Log.d(TAG, "MyMemory returned source text unchanged — treating as no translation");
                return null;
            }

            // Reject placeholder responses the API sometimes returns
            if (translated.startsWith("PLEASE SELECT") || translated.startsWith("NO QUERY")) {
                return null;
            }

            return translated;

        } catch (Exception e) {
            Log.e(TAG, "JSON parse error", e);
            return null;
        }
    }

    /**
     * Blocks the calling thread (background executor) if the last request was made
     * less than {@link #RATE_LIMIT_MS} ago.
     */
    private void enforceRateLimit() {
        final long now = System.currentTimeMillis();
        final long last = lastRequestTime.get();
        final long elapsed = now - last;

        if (last > 0 && elapsed < RATE_LIMIT_MS) {
            final long sleepMs = RATE_LIMIT_MS - elapsed;
            Log.d(TAG, "Rate limiting: sleeping " + sleepMs + "ms");
            try {
                Thread.sleep(sleepMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        lastRequestTime.set(System.currentTimeMillis());
    }

    /**
     * Posts either a success or error callback to the Android main thread.
     *
     * <p>Exactly one of {@code result} and {@code error} must be non-null.
     *
     * @param callback the callback to invoke.
     * @param result   translated text (non-null on success).
     * @param error    error message (non-null on failure).
     */
    private void postResult(
            final TranslationCallback callback,
            final String result,
            final String error) {

        mainHandler.post(() -> {
            if (result != null) {
                callback.onTranslated(result);
            } else {
                callback.onError(error != null ? error : "Translation unavailable.");
            }
        });
    }
}
