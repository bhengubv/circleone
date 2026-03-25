/*
 * Copyright (c) 2026 The Other Bhengu (Pty) Ltd t/a The Geek
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.thegeek.gesture;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Generates and caches gesture templates for a dictionary of words.
 *
 * <p>A <em>gesture template</em> is the idealized path that a user would swipe when typing a
 * word — formed by connecting the centre point of each key in sequence, resampled to
 * {@link #RESAMPLE_POINTS} equidistant points, and normalized to a unit bounding box so it can
 * be compared directly against normalized user paths.
 *
 * <h2>Duplicate-key handling</h2>
 * Consecutive identical keys (e.g. the two {@code l}s in "hello") contribute only one control
 * point, offset slightly in the perpendicular direction to avoid a zero-length segment that
 * would distort resampling. The offset magnitude is {@link #DWELL_OFFSET_RATIO} × key width.
 *
 * <h2>Caching strategy</h2>
 * <ul>
 *   <li>Templates are generated <em>lazily</em> on first access and stored in a thread-safe map.</li>
 *   <li>Secondary indices group templates by first letter, last letter, and unique-key count to
 *       enable sub-millisecond candidate pruning without iterating the full dictionary.</li>
 *   <li>All indices are invalidated atomically when the layout or dictionary changes and rebuilt
 *       lazily on the next lookup call.</li>
 * </ul>
 *
 * <h2>Thread safety</h2>
 * The primary template store is a {@link ConcurrentHashMap}. Index rebuilds are guarded by
 * a dedicated lock. It is safe to call {@link #getTemplate(String)} from any thread concurrently.
 * {@link #setDictionary(List)} and {@link #invalidate()} should be called from a single writer
 * thread (or under external synchronization) to avoid redundant rebuilds.
 *
 * <h2>Hot-swap</h2>
 * Call {@link #setDictionary(List)} with the new word list when the user switches language.
 * The old cache is discarded immediately; templates for the new language are generated lazily
 * on demand or eagerly via {@link #precomputeAll()}.
 */
public class TemplateCache {

    // -------------------------------------------------------------------------
    // Constants
    // -------------------------------------------------------------------------

    /**
     * Number of equidistant points every resampled template path contains.
     * Must match the value used when normalizing user gesture paths.
     */
    public static final int RESAMPLE_POINTS = 64;

    /**
     * Dwell offset applied to a repeated key (as a fraction of key width) so that consecutive
     * duplicate keys do not produce a zero-length path segment during resampling.
     */
    private static final float DWELL_OFFSET_RATIO = 0.15f;

    // -------------------------------------------------------------------------
    // State
    // -------------------------------------------------------------------------

    /** The keyboard layout used to resolve key centres. Never null after construction. */
    private volatile KeyboardLayout layout;

    /** The current dictionary. Replaced atomically by {@link #setDictionary(List)}. */
    private volatile List<String> dictionary = Collections.emptyList();

    /** Primary cache: word (lowercased) → template.  Thread-safe for concurrent access. */
    private final ConcurrentHashMap<String, GestureTemplate> templateMap = new ConcurrentHashMap<>();

    // Secondary indices — rebuilt lazily, guarded by indexLock.
    private final Object indexLock     = new Object();
    private volatile boolean indexDirty = true;

    /** startKey character → list of templates whose word starts with that key. */
    private final Map<Character, List<GestureTemplate>> byFirstLetter = new HashMap<>();

    /** endKey character → list of templates whose word ends with that key. */
    private final Map<Character, List<GestureTemplate>> byLastLetter  = new HashMap<>();

    /** uniqueKeyCount → list of templates with exactly that many unique keys. */
    private final Map<Integer, List<GestureTemplate>> byKeyCount      = new HashMap<>();

    // Performance counters
    private final AtomicLong cacheHits   = new AtomicLong();
    private final AtomicLong cacheMisses = new AtomicLong();

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    /**
     * Creates a new {@code TemplateCache} for the given keyboard layout.
     *
     * @param layout the keyboard layout that maps characters to key centres; must not be null
     */
    public TemplateCache(KeyboardLayout layout) {
        if (layout == null) throw new IllegalArgumentException("layout must not be null");
        this.layout = layout;
    }

    // -------------------------------------------------------------------------
    // Dictionary / layout management
    // -------------------------------------------------------------------------

    /**
     * Replaces the current dictionary and invalidates all cached templates.
     *
     * <p>This is the primary hot-swap entry point. After this call any subsequent
     * {@link #getTemplate(String)} call will re-generate templates lazily from the new word list.
     *
     * @param words the new word list; {@code null} is treated as an empty list
     */
    public void setDictionary(List<String> words) {
        this.dictionary = (words != null)
                ? Collections.unmodifiableList(new ArrayList<>(words))
                : Collections.emptyList();
        invalidate();
    }

    /**
     * Replaces the keyboard layout and invalidates all cached templates.
     *
     * <p>Use this when the physical key positions change (e.g. the user switches between portrait
     * and landscape, or changes the keyboard height).
     *
     * @param layout the new layout; must not be null
     */
    public void setLayout(KeyboardLayout layout) {
        if (layout == null) throw new IllegalArgumentException("layout must not be null");
        this.layout = layout;
        invalidate();
    }

    // -------------------------------------------------------------------------
    // Core public API
    // -------------------------------------------------------------------------

    /**
     * Returns the {@link GestureTemplate} for {@code word}, generating and caching it on first
     * access.
     *
     * <p>Returns {@code null} if any character in the word has no registered key in the current
     * layout. Nothing is cached for unmappable words.
     *
     * @param word the dictionary word; case-insensitive (lowercased internally)
     * @return the template, or {@code null} if the word cannot be represented on this layout
     */
    public GestureTemplate getTemplate(String word) {
        if (word == null || word.isEmpty()) return null;
        final String key = word.toLowerCase();

        GestureTemplate cached = templateMap.get(key);
        if (cached != null) {
            cacheHits.incrementAndGet();
            return cached;
        }

        cacheMisses.incrementAndGet();
        GestureTemplate generated = buildTemplate(key);
        if (generated != null) {
            GestureTemplate existing = templateMap.putIfAbsent(key, generated);
            return (existing != null) ? existing : generated;
        }
        return null;
    }

    /**
     * Returns all cached templates whose word starts with {@code startKey} AND ends with
     * {@code endKey}.
     *
     * <p>Uses the secondary index built from the current dictionary. The index is rebuilt lazily
     * the first time this method is called after a {@link #setDictionary(List)} or
     * {@link #invalidate()} call.
     *
     * @param startKey first character of the target word (case-insensitive)
     * @param endKey   last  character of the target word (case-insensitive)
     * @return an unmodifiable list; never null, may be empty
     */
    public List<GestureTemplate> getCandidatesByStartEnd(char startKey, char endKey) {
        ensureIndexBuilt();
        char s = Character.toLowerCase(startKey);
        char e = Character.toLowerCase(endKey);

        synchronized (indexLock) {
            List<GestureTemplate> startList = byFirstLetter.getOrDefault(s, Collections.emptyList());
            List<GestureTemplate> endList   = byLastLetter .getOrDefault(e, Collections.emptyList());

            // Intersect: iterate the smaller set, test against a hash-set of the larger.
            if (startList.size() <= endList.size()) {
                Set<GestureTemplate> endSet = new HashSet<>(endList);
                List<GestureTemplate> result = new ArrayList<>();
                for (GestureTemplate t : startList) {
                    if (endSet.contains(t)) result.add(t);
                }
                return Collections.unmodifiableList(result);
            } else {
                Set<GestureTemplate> startSet = new HashSet<>(startList);
                List<GestureTemplate> result = new ArrayList<>();
                for (GestureTemplate t : endList) {
                    if (startSet.contains(t)) result.add(t);
                }
                return Collections.unmodifiableList(result);
            }
        }
    }

    /**
     * Returns all templates whose {@link GestureTemplate#uniqueKeyCount} falls in
     * [{@code minKeys}, {@code maxKeys}] inclusive.
     *
     * @param minKeys minimum unique key count (inclusive)
     * @param maxKeys maximum unique key count (inclusive)
     * @return an unmodifiable list; never null, may be empty
     */
    public List<GestureTemplate> getCandidatesByLength(int minKeys, int maxKeys) {
        ensureIndexBuilt();
        List<GestureTemplate> result = new ArrayList<>();
        synchronized (indexLock) {
            for (int k = minKeys; k <= maxKeys; k++) {
                List<GestureTemplate> bucket = byKeyCount.get(k);
                if (bucket != null) result.addAll(bucket);
            }
        }
        return Collections.unmodifiableList(result);
    }

    /**
     * Pre-generates templates for every word in the current dictionary.
     *
     * <p>This is optional — templates are built lazily on demand if you skip this call.
     * Invoke it on a background thread immediately after {@link #setDictionary(List)} to
     * eliminate first-swipe latency. Progress can be monitored via {@link #getStats()}.
     *
     * <p>Words whose characters are not all present in the current layout are silently skipped.
     */
    public void precomputeAll() {
        for (String word : dictionary) {
            getTemplate(word);
        }
        ensureIndexBuilt(); // rebuild index now so first prune call is instant
    }

    /**
     * Clears all cached templates and marks the secondary indices as dirty.
     *
     * <p>The dictionary is retained; templates will be re-generated lazily on next access.
     * Call this when the layout changes or when you need to reclaim memory.
     */
    public void invalidate() {
        templateMap.clear();
        synchronized (indexLock) {
            byFirstLetter.clear();
            byLastLetter.clear();
            byKeyCount.clear();
            indexDirty = true;
        }
        cacheHits.set(0);
        cacheMisses.set(0);
    }

    /**
     * Returns a point-in-time snapshot of cache performance metrics.
     *
     * @return a {@link CacheStats} value object; never null
     */
    public CacheStats getStats() {
        return new CacheStats(
                templateMap.size(),
                dictionary.size(),
                cacheHits.get(),
                cacheMisses.get()
        );
    }

    // -------------------------------------------------------------------------
    // Template generation
    // -------------------------------------------------------------------------

    /**
     * Builds a {@link GestureTemplate} for {@code word} (already lowercased).
     *
     * @return the fully constructed template, or {@code null} if any character in the word
     *         has no registered key in the current layout
     */
    private GestureTemplate buildTemplate(String word) {
        KeyboardLayout currentLayout = this.layout;

        float keyWidth = currentLayout.getKeyWidth();

        // ---- Step 1: Resolve key centres, collapsing consecutive duplicates ----
        // Control points are parallel float arrays rather than Point objects so that
        // buildRawPath() can feed them directly into GesturePath.addPoint().
        List<Float> cpX = new ArrayList<>();
        List<Float> cpY = new ArrayList<>();
        char prevKey = 0;

        for (int i = 0; i < word.length(); i++) {
            char ch = word.charAt(i);
            GesturePath.Point centre = currentLayout.getKeyCenter(ch);
            if (centre == null) return null; // unknown key → cannot build template

            if (ch == prevKey) {
                // Consecutive duplicate: add a dwell point offset perpendicular to the
                // incoming path direction so resampling has a non-zero segment to walk.
                float dwellMag = keyWidth * DWELL_OFFSET_RATIO;
                float dx = 0f, dy = -dwellMag; // default: nudge upward

                int cpSize = cpX.size();
                if (cpSize >= 2) {
                    float segDx = cpX.get(cpSize - 1) - cpX.get(cpSize - 2);
                    float segDy = cpY.get(cpSize - 1) - cpY.get(cpSize - 2);
                    float len = (float) Math.sqrt(segDx * segDx + segDy * segDy);
                    if (len > 0.001f) {
                        // Perpendicular unit vector: (−segDy/len, segDx/len)
                        dx = (-segDy / len) * dwellMag;
                        dy = ( segDx / len) * dwellMag;
                    }
                }

                // Alternate the dwell side for every additional repeated letter
                int dupeCount = countConsecutiveDupesBefore(word, i);
                if (dupeCount % 2 != 0) { dx = -dx; dy = -dy; }

                cpX.add(centre.x + dx);
                cpY.add(centre.y + dy);
            } else {
                cpX.add(centre.x);
                cpY.add(centre.y);
            }
            prevKey = ch;
        }

        // Ensure at least two control points so resampling has a segment to walk.
        if (cpX.size() < 2) {
            cpX.add(cpX.get(0));
            cpY.add(cpY.get(0));
        }

        // ---- Step 2: Build raw GesturePath from control points ----
        GesturePath rawPath = new GesturePath();
        for (int i = 0; i < cpX.size(); i++) {
            rawPath.addPoint(cpX.get(i), cpY.get(i), 0L);
        }

        // ---- Step 3: Resample to N equidistant points ----
        GesturePath resampled = rawPath.resample(RESAMPLE_POINTS);

        // ---- Step 4: Normalize to unit bounding box ----
        GesturePath normalizedPath = resampled.normalizeToUnitBox();

        // ---- Step 5: Compute metadata ----
        int  uniqueKeyCount = countUniqueKeys(word);
        char startKey       = word.charAt(0);
        char endKey         = word.charAt(word.length() - 1);
        float pathLength    = rawPath.totalLength();

        return new GestureTemplate(word, normalizedPath, rawPath,
                                   uniqueKeyCount, startKey, endKey, pathLength);
    }

    /** Returns the number of distinct characters in {@code word}. */
    private static int countUniqueKeys(String word) {
        Set<Character> seen = new HashSet<>();
        for (int i = 0; i < word.length(); i++) {
            seen.add(word.charAt(i));
        }
        return seen.size();
    }

    /**
     * Returns how many times the same character appears consecutively immediately before
     * position {@code pos}, used to alternate the dwell offset direction.
     */
    private static int countConsecutiveDupesBefore(String word, int pos) {
        char ch = word.charAt(pos);
        int count = 0;
        for (int i = pos - 1; i >= 0 && word.charAt(i) == ch; i--) {
            count++;
        }
        return count;
    }

    // -------------------------------------------------------------------------
    // Index management
    // -------------------------------------------------------------------------

    /**
     * Ensures the secondary indices are current, rebuilding them if dirty.
     * Uses double-checked locking for efficiency under high concurrency.
     */
    private void ensureIndexBuilt() {
        if (!indexDirty) return;
        synchronized (indexLock) {
            if (!indexDirty) return;
            rebuildIndex();
            indexDirty = false;
        }
    }

    /**
     * Scans the dictionary, generating templates lazily, and populates all three secondary
     * indices. Called only from within {@code synchronized(indexLock)}.
     */
    private void rebuildIndex() {
        byFirstLetter.clear();
        byLastLetter.clear();
        byKeyCount.clear();

        for (String word : dictionary) {
            if (word == null || word.isEmpty()) continue;
            String lower = word.toLowerCase();
            GestureTemplate t = getTemplate(lower);
            if (t == null) continue; // unmappable word — skip

            byFirstLetter
                    .computeIfAbsent(t.startKey, k -> new ArrayList<>())
                    .add(t);
            byLastLetter
                    .computeIfAbsent(t.endKey, k -> new ArrayList<>())
                    .add(t);
            byKeyCount
                    .computeIfAbsent(t.uniqueKeyCount, k -> new ArrayList<>())
                    .add(t);
        }
    }

    // =========================================================================
    // Inner class: GestureTemplate
    // =========================================================================

    /**
     * An immutable, fully-computed template representing the idealized swipe path for a word.
     *
     * <p>Instances are created exclusively by {@link TemplateCache#buildTemplate(String)} and are
     * safe to share across threads once constructed.
     */
    public static final class GestureTemplate {

        /** The dictionary word this template represents (lowercased). */
        public final String word;

        /**
         * The normalized, resampled path ({@link TemplateCache#RESAMPLE_POINTS} points,
         * unit bounding box). Pass this to shape-matching algorithms.
         */
        public final GesturePath normalizedPath;

        /**
         * The raw path through key centres before resampling or normalization.
         * Used for computing {@link #pathLength} and for diagnostics / visualization.
         */
        public final GesturePath rawPath;

        /**
         * Number of <em>distinct</em> characters in the word. For example, "hello" has
         * 4 unique keys (h, e, l, o). Used for length-based candidate pruning.
         */
        public final int uniqueKeyCount;

        /** First character of the word (lowercased). Used for start-key index lookups. */
        public final char startKey;

        /** Last character of the word (lowercased). Used for end-key index lookups. */
        public final char endKey;

        /**
         * Total Euclidean arc length of the raw (pre-normalized) path in pixels.
         * Used for path-length-ratio pruning in {@link CandidatePruner}.
         */
        public final float pathLength;

        GestureTemplate(String word, GesturePath normalizedPath, GesturePath rawPath,
                        int uniqueKeyCount, char startKey, char endKey, float pathLength) {
            this.word           = word;
            this.normalizedPath = normalizedPath;
            this.rawPath        = rawPath;
            this.uniqueKeyCount = uniqueKeyCount;
            this.startKey       = startKey;
            this.endKey         = endKey;
            this.pathLength     = pathLength;
        }

        @Override
        public String toString() {
            return "GestureTemplate{word='" + word
                    + "', uniqueKeys=" + uniqueKeyCount
                    + ", pathLength=" + pathLength + '}';
        }
    }

    // =========================================================================
    // Inner class: CacheStats
    // =========================================================================

    /**
     * Immutable snapshot of cache performance metrics returned by {@link TemplateCache#getStats()}.
     */
    public static final class CacheStats {

        /** Number of templates currently held in the primary cache. */
        public final int cachedTemplateCount;

        /** Total number of words in the active dictionary. */
        public final int dictionarySize;

        /** Number of {@link TemplateCache#getTemplate} calls satisfied from the cache. */
        public final long cacheHits;

        /** Number of {@link TemplateCache#getTemplate} calls that required generation. */
        public final long cacheMisses;

        CacheStats(int cachedTemplateCount, int dictionarySize,
                   long cacheHits, long cacheMisses) {
            this.cachedTemplateCount = cachedTemplateCount;
            this.dictionarySize      = dictionarySize;
            this.cacheHits           = cacheHits;
            this.cacheMisses         = cacheMisses;
        }

        /**
         * Cache hit rate in [0.0, 1.0]. Returns 0.0 before any accesses have occurred.
         */
        public double hitRate() {
            long total = cacheHits + cacheMisses;
            return (total == 0) ? 0.0 : (double) cacheHits / total;
        }

        /**
         * Fraction of the dictionary that has been pre-generated, in [0.0, 1.0].
         */
        public double fillRatio() {
            return (dictionarySize == 0) ? 0.0 : (double) cachedTemplateCount / dictionarySize;
        }

        @Override
        public String toString() {
            return String.format(
                    "CacheStats{cached=%d/%d (%.1f%%), hitRate=%.1f%%, hits=%d, misses=%d}",
                    cachedTemplateCount, dictionarySize, fillRatio() * 100,
                    hitRate() * 100, cacheHits, cacheMisses);
        }
    }
}
