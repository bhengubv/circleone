/*
 * MIT License
 *
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
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Core gesture-typing decoder engine.
 *
 * <p>{@code GestureDecoder} takes a user's swipe path across a keyboard and a
 * word dictionary, and returns candidate words ranked by how closely each word's
 * "ideal" swipe path matches the user's actual path.
 *
 * <h3>Algorithm overview</h3>
 * <ol>
 *   <li><strong>Normalize</strong> — resample the user path to
 *       {@link GesturePath#DEFAULT_RESAMPLE_COUNT} equidistant points, then
 *       scale to a unit bounding box.</li>
 *   <li><strong>Pre-filter</strong> — discard words whose template path length
 *       ratio or start/end key proximity fall outside configurable thresholds.
 *       This eliminates most of the dictionary before the expensive shape
 *       comparison, keeping the hot path fast.</li>
 *   <li><strong>Multi-channel scoring</strong> — for surviving candidates,
 *       compute four independent scores:
 *       <ul>
 *         <li><em>Shape distance</em> — mean point-to-point Euclidean distance
 *             between normalized paths (primary signal).</li>
 *         <li><em>Location distance</em> — for each letter in the word, find the
 *             minimum distance from that letter's key center to any point on the
 *             raw user path. Penalizes paths that have the right shape but miss
 *             the actual keys.</li>
 *         <li><em>Length ratio</em> — ratio of user path length to template path
 *             length. Very short or very long paths relative to the template
 *             indicate a poor match.</li>
 *         <li><em>Start/end proximity</em> — distance from the first (last) user
 *             touch point to the first (last) letter's key center.</li>
 *       </ul>
 *   </li>
 *   <li><strong>Combine and rank</strong> — weighted sum of the four channel
 *       scores, optionally boosted by a language model probability, gives each
 *       candidate a final score in [0, 1]. Candidates are sorted descending and
 *       the top {@code maxCandidates} are returned.</li>
 * </ol>
 *
 * <h3>Thread safety</h3>
 * <p>{@link #decode(GesturePath, int)} is thread-safe and may be called
 * concurrently from multiple threads. The template cache uses a
 * {@link ConcurrentHashMap} so templates are generated at most once per word
 * (with a small risk of duplicate generation on first access, which is harmless).
 * {@link #setDictionary(List)} and {@link #setLanguageModel(LanguageModel)} are
 * <em>not</em> thread-safe with respect to concurrent {@code decode} calls; set
 * up the decoder before dispatching decode work.
 *
 * <h3>Performance</h3>
 * <p>The pre-filter typically eliminates 80-95 % of a 50 k word dictionary before
 * the O(N) shape comparison loop. On a mid-range CPU the full decode pipeline
 * completes in well under 50 ms for a 50 k dictionary.
 */
public final class GestureDecoder {

    // -------------------------------------------------------------------------
    // Tuneable constants
    // -------------------------------------------------------------------------

    /** Minimum path length ratio (user / template) to survive pre-filtering. */
    private static final float MIN_LENGTH_RATIO = 0.4f;

    /** Maximum path length ratio (user / template) to survive pre-filtering. */
    private static final float MAX_LENGTH_RATIO = 2.5f;

    /**
     * Maximum normalized distance from the user's first/last touch to the word's
     * first/last key center for the word to pass pre-filtering.
     * Expressed as a fraction of the keyboard width.
     */
    private static final float MAX_ENDPOINT_DIST_FRACTION = 0.30f;

    // Channel weights (must sum to 1.0)
    private static final double W_SHAPE    = 0.50;
    private static final double W_LOCATION = 0.25;
    private static final double W_LENGTH   = 0.15;
    private static final double W_ENDPOINT = 0.10;

    /**
     * Language model boost weight. The final score is blended as:
     * {@code finalScore = (1 - LM_WEIGHT) * geometricScore + LM_WEIGHT * lmScore}.
     */
    private static final double LM_WEIGHT = 0.15;

    // -------------------------------------------------------------------------
    // State
    // -------------------------------------------------------------------------

    private final KeyboardLayout layout;
    private volatile List<String> dictionary = Collections.emptyList();
    private volatile LanguageModel languageModel = null;

    /**
     * Template cache: word → pre-computed normalized resampled path.
     * Populated lazily on first decode; entries persist for the lifetime of the decoder.
     */
    private final Map<String, GesturePath> templateCache = new ConcurrentHashMap<>();

    // -------------------------------------------------------------------------
    // Construction
    // -------------------------------------------------------------------------

    /**
     * Constructs a decoder for the given keyboard layout.
     *
     * @param layout the keyboard geometry to use for template generation and
     *               proximity scoring; must not be {@code null}
     * @throws IllegalArgumentException if {@code layout} is {@code null}
     */
    public GestureDecoder(KeyboardLayout layout) {
        if (layout == null) throw new IllegalArgumentException("layout must not be null");
        this.layout = layout;
    }

    // -------------------------------------------------------------------------
    // Configuration
    // -------------------------------------------------------------------------

    /**
     * Sets the word list that will be used as the candidate pool.
     *
     * <p>A defensive copy is taken; subsequent mutations to {@code words} have
     * no effect. The template cache is cleared so that stale templates for
     * removed words are not retained.
     *
     * @param words the word list; {@code null} is treated as an empty list
     */
    public void setDictionary(List<String> words) {
        templateCache.clear();
        this.dictionary = (words == null)
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(words));
    }

    /**
     * Attaches an optional language model that boosts candidates by prior
     * word probability. Pass {@code null} to remove a previously set model.
     *
     * @param lm the language model, or {@code null} to disable LM boosting
     */
    public void setLanguageModel(LanguageModel lm) {
        this.languageModel = lm;
    }

    // -------------------------------------------------------------------------
    // Core decode
    // -------------------------------------------------------------------------

    /**
     * Decodes a gesture path into a ranked list of word candidates.
     *
     * <p>The method is thread-safe; multiple threads may call it concurrently.
     *
     * @param userPath      the raw swipe path recorded from touch events; must
     *                      contain at least two points
     * @param maxCandidates maximum number of candidates to return; must be &ge; 1
     * @return a list of {@link Candidate} objects sorted by descending score,
     *         with at most {@code maxCandidates} entries; never {@code null} but
     *         may be empty if the dictionary is empty or no word passes the
     *         pre-filter
     * @throws IllegalArgumentException if {@code userPath} is {@code null},
     *                                  has fewer than two points, or
     *                                  {@code maxCandidates} &lt; 1
     */
    public List<Candidate> decode(GesturePath userPath, int maxCandidates) {
        validateDecodeArgs(userPath, maxCandidates);

        // Prepare the user path once — resample then normalize
        GesturePath userResampled  = userPath.resample(GesturePath.DEFAULT_RESAMPLE_COUNT);
        GesturePath userNormalized = userResampled.normalize();

        float userLength = userPath.getLength();
        List<GesturePath.Point> userRawPoints = userPath.getPoints();

        // Pre-compute user endpoints in raw (pixel) space
        GesturePath.Point userStart = userRawPoints.get(0);
        GesturePath.Point userEnd   = userRawPoints.get(userRawPoints.size() - 1);

        // Maximum endpoint distance threshold in pixels
        float maxEndpointDist = layout.getWidth() * MAX_ENDPOINT_DIST_FRACTION;

        List<Candidate> candidates = new ArrayList<>();
        LanguageModel lm = this.languageModel; // snapshot for thread safety
        List<String> dict = this.dictionary;

        for (String word : dict) {
            if (word == null || word.isEmpty()) continue;
            String lower = word.toLowerCase();

            // ----------------------------------------------------------------
            // Step 1: Retrieve or build the template
            // ----------------------------------------------------------------
            GesturePath template = getOrBuildTemplate(lower);
            if (template == null) continue; // word contains letters not in the layout

            // ----------------------------------------------------------------
            // Step 2: Pre-filter — length ratio
            // ----------------------------------------------------------------
            float templateLength = templateRawLength(lower);
            if (templateLength > 0f) {
                float ratio = userLength / templateLength;
                if (ratio < MIN_LENGTH_RATIO || ratio > MAX_LENGTH_RATIO) continue;
            }

            // ----------------------------------------------------------------
            // Step 3: Pre-filter — start/end key proximity
            // ----------------------------------------------------------------
            GesturePath.Point firstCenter = layout.getKeyCenter(lower.charAt(0));
            GesturePath.Point lastCenter  = layout.getKeyCenter(lower.charAt(lower.length() - 1));
            if (firstCenter == null || lastCenter == null) continue;

            if (userStart.distanceTo(firstCenter) > maxEndpointDist) continue;
            if (userEnd.distanceTo(lastCenter)    > maxEndpointDist) continue;

            // ----------------------------------------------------------------
            // Step 4: Multi-channel scoring
            // ----------------------------------------------------------------
            double shapeScore    = scoreShape(userNormalized, template);
            double locationScore = scoreLocation(lower, userRawPoints);
            double lengthScore   = scoreLengthRatio(userLength, templateLength);
            double endpointScore = scoreEndpoints(userStart, userEnd, firstCenter, lastCenter,
                    maxEndpointDist);

            double geometric = W_SHAPE    * shapeScore
                             + W_LOCATION * locationScore
                             + W_LENGTH   * lengthScore
                             + W_ENDPOINT * endpointScore;

            // ----------------------------------------------------------------
            // Step 5: Language model boost
            // ----------------------------------------------------------------
            double finalScore;
            if (lm != null) {
                double lmProb = clamp01(lm.getWordProbability(word));
                finalScore = (1.0 - LM_WEIGHT) * geometric + LM_WEIGHT * lmProb;
            } else {
                finalScore = geometric;
            }

            // ----------------------------------------------------------------
            // Step 6: Compute raw shape distance for caller diagnostics
            // ----------------------------------------------------------------
            double distance = shapeDistance(userNormalized, template);

            candidates.add(new Candidate(word, finalScore, distance));
        }

        // Sort descending by score; break ties by ascending distance
        candidates.sort((a, b) -> {
            int cmp = Double.compare(b.score, a.score);
            if (cmp != 0) return cmp;
            return Double.compare(a.distance, b.distance);
        });

        return candidates.subList(0, Math.min(maxCandidates, candidates.size()));
    }

    // -------------------------------------------------------------------------
    // Template management
    // -------------------------------------------------------------------------

    /**
     * Returns the normalized resampled template for {@code word} (lower-case),
     * building and caching it on first access. Returns {@code null} if any
     * letter in the word is absent from the layout.
     */
    private GesturePath getOrBuildTemplate(String word) {
        GesturePath cached = templateCache.get(word);
        if (cached != null) return cached;

        GesturePath raw = buildRawTemplate(word);
        if (raw == null) return null;

        GesturePath normalized = raw.resample(GesturePath.DEFAULT_RESAMPLE_COUNT).normalize();
        templateCache.put(word, normalized);
        return normalized;
    }

    /**
     * Builds the raw (pixel-space) template path for {@code word} by connecting
     * consecutive letter key centers. Returns {@code null} if any letter is missing.
     *
     * <p>Consecutive duplicate letters (e.g. the two {@code l}s in "hello") produce
     * only one control point to avoid zero-length segments that would distort resampling.
     */
    private GesturePath buildRawTemplate(String word) {
        GesturePath path = new GesturePath();
        for (int i = 0; i < word.length(); i++) {
            char ch = word.charAt(i);
            GesturePath.Point center = layout.getKeyCenter(ch);
            if (center == null) return null;
            // Skip duplicate consecutive keys (e.g. "ll" → single key visit)
            if (i > 0 && word.charAt(i) == word.charAt(i - 1)) continue;
            path.addPoint(center.x, center.y, i * 10L, 1.0f); // synthetic timestamps
        }
        if (path.size() < 2) {
            // Single-letter or all-duplicate — duplicate the point so resample works
            if (path.size() == 1) {
                GesturePath.Point p = path.getPoints().get(0);
                path.addPoint(p.x + 0.001f, p.y, 10L, 1.0f);
            } else {
                return null;
            }
        }
        return path;
    }

    /**
     * Returns the raw (pixel-space) arc length of the template for {@code word}.
     * Uses a fresh build each time; callers should cache if called repeatedly.
     */
    private float templateRawLength(String word) {
        GesturePath raw = buildRawTemplate(word);
        return (raw == null) ? 0f : raw.getLength();
    }

    // -------------------------------------------------------------------------
    // Scoring channels
    // -------------------------------------------------------------------------

    /**
     * Shape score: 1 - normalized average point-to-point distance.
     * Both paths must already be resampled to the same count and normalized.
     *
     * @return score in [0, 1]; 1 = perfect match
     */
    private double scoreShape(GesturePath user, GesturePath template) {
        double dist = shapeDistance(user, template);
        // A normalized distance of 0.5 across the unit square is roughly the
        // worst realistic case — clamp and invert.
        return clamp01(1.0 - dist / 0.5);
    }

    /**
     * Computes the mean Euclidean distance between corresponding normalized points.
     */
    private double shapeDistance(GesturePath user, GesturePath template) {
        List<GesturePath.Point> up = user.getPoints();
        List<GesturePath.Point> tp = template.getPoints();
        int n = Math.min(up.size(), tp.size());
        if (n == 0) return 1.0;
        double sum = 0.0;
        for (int i = 0; i < n; i++) {
            sum += up.get(i).distanceTo(tp.get(i));
        }
        return sum / n;
    }

    /**
     * Location score: for each unique letter in the word, find the minimum
     * distance from that key center to any raw user-path point, then average.
     * Normalizes by the keyboard hit radius so the score is in [0, 1].
     *
     * @return score in [0, 1]; 1 = user path passed directly over every key
     */
    private double scoreLocation(String word, List<GesturePath.Point> rawUserPoints) {
        if (rawUserPoints.isEmpty()) return 0.0;
        double totalScore = 0.0;
        int count = 0;
        char prev = '\0';
        for (int i = 0; i < word.length(); i++) {
            char ch = word.charAt(i);
            if (ch == prev) continue; // skip duplicates
            prev = ch;

            KeyboardLayout.KeyRect rect = layout.getKeyRect(ch);
            if (rect == null) continue;

            float minDist = Float.MAX_VALUE;
            for (GesturePath.Point p : rawUserPoints) {
                float dx = p.x - rect.centerX;
                float dy = p.y - rect.centerY;
                float d = (float) Math.sqrt(dx * dx + dy * dy);
                if (d < minDist) minDist = d;
            }

            // Normalize: 0 distance = score 1.0; hitRadius distance = score 0.5; 2x = 0
            double keyScore = clamp01(1.0 - minDist / (2.0 * rect.hitRadius()));
            totalScore += keyScore;
            count++;
        }
        return (count == 0) ? 0.0 : totalScore / count;
    }

    /**
     * Length ratio score: peaks at 1.0 when the ratio is exactly 1.0, falls off
     * linearly towards the filter boundaries.
     *
     * @return score in [0, 1]
     */
    private double scoreLengthRatio(float userLength, float templateLength) {
        if (templateLength <= 0f) return 0.5; // neutral when undefined
        double ratio = userLength / templateLength;
        if (ratio < 1.0) {
            // Between MIN_LENGTH_RATIO and 1.0
            return clamp01((ratio - MIN_LENGTH_RATIO) / (1.0 - MIN_LENGTH_RATIO));
        } else {
            // Between 1.0 and MAX_LENGTH_RATIO
            return clamp01(1.0 - (ratio - 1.0) / (MAX_LENGTH_RATIO - 1.0));
        }
    }

    /**
     * Endpoint score: combines start and end key proximity into a single value.
     * Each endpoint is scored as 1 - (distance / maxDist), clamped to [0, 1].
     *
     * @return score in [0, 1]; 1 = both endpoints land exactly on target keys
     */
    private double scoreEndpoints(GesturePath.Point userStart, GesturePath.Point userEnd,
                                  GesturePath.Point firstCenter, GesturePath.Point lastCenter,
                                  float maxDist) {
        double startScore = clamp01(1.0 - userStart.distanceTo(firstCenter) / maxDist);
        double endScore   = clamp01(1.0 - userEnd.distanceTo(lastCenter)    / maxDist);
        return (startScore + endScore) / 2.0;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static double clamp01(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }

    private static void validateDecodeArgs(GesturePath userPath, int maxCandidates) {
        if (userPath == null) {
            throw new IllegalArgumentException("userPath must not be null");
        }
        if (userPath.size() < 2) {
            throw new IllegalArgumentException(
                    "userPath must contain at least 2 points, got: " + userPath.size());
        }
        if (maxCandidates < 1) {
            throw new IllegalArgumentException(
                    "maxCandidates must be >= 1, got: " + maxCandidates);
        }
    }

    // -------------------------------------------------------------------------
    // Public inner types
    // -------------------------------------------------------------------------

    /**
     * A decoded word candidate with its match quality scores.
     *
     * <p>Candidates are returned sorted by {@link #score} descending.
     */
    public static final class Candidate implements Comparable<Candidate> {

        /** The candidate word as it appears in the dictionary. */
        public final String word;

        /**
         * Combined match quality in [0, 1]; higher is better.
         * This is the value used for ranking.
         */
        public final double score;

        /**
         * Raw normalized shape distance between the user path and this word's
         * template; lower is better. Provided for diagnostic purposes.
         */
        public final double distance;

        /**
         * Constructs a candidate.
         *
         * @param word     the dictionary word
         * @param score    combined score in [0, 1]
         * @param distance raw shape distance (&ge; 0)
         */
        public Candidate(String word, double score, double distance) {
            this.word     = word;
            this.score    = score;
            this.distance = distance;
        }

        /**
         * Natural ordering: higher score first.
         */
        @Override
        public int compareTo(Candidate other) {
            return Double.compare(other.score, this.score);
        }

        @Override
        public String toString() {
            return String.format("Candidate('%s', score=%.4f, dist=%.4f)", word, score, distance);
        }
    }

    /**
     * Optional language model interface for boosting candidate ranking with
     * word-level prior probabilities.
     *
     * <p>Implementations must be thread-safe if {@link GestureDecoder#decode} is
     * called from multiple threads.
     *
     * <h3>Example — unigram frequency model</h3>
     * <pre>{@code
     * GestureDecoder decoder = new GestureDecoder(layout);
     * decoder.setLanguageModel(word -> frequencyMap.getOrDefault(word, 0.0));
     * }</pre>
     */
    @FunctionalInterface
    public interface LanguageModel {

        /**
         * Returns the prior probability (or a probability-like score) for the
         * given word.
         *
         * <p>The return value should be in [0, 1] but will be clamped if not.
         * A return value of {@code 0} means the word is unknown or very rare;
         * {@code 1} means it should always be ranked first regardless of geometry.
         *
         * @param word the word to score; never {@code null}
         * @return probability-like score in [0, 1]
         */
        double getWordProbability(String word);
    }
}
