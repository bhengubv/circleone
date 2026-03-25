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
import java.util.List;

/**
 * Fast, multi-stage pre-filter that reduces a 50 000-word dictionary down to a small set of
 * plausible candidates before the expensive shape-matching stage.
 *
 * <h2>Pruning pipeline</h2>
 * Stages are applied in order of increasing cost and decreasing selectivity so that each stage
 * operates on the already-reduced output of the previous one.
 *
 * <ol>
 *   <li><b>Start-key</b> — Discard words whose first key centre is more than
 *       {@code radiusMultiplier × keyWidth} pixels from the first touch point.</li>
 *   <li><b>End-key</b> — Same test against the last touch point.</li>
 *   <li><b>Path length</b> — Discard words whose template arc length is outside
 *       [{@code minLengthRatio}, {@code maxLengthRatio}] × user path length.</li>
 *   <li><b>Unique key count</b> — Discard single-key words (cannot be swiped) and words whose
 *       unique-key count differs from the estimated swipe key count by more than
 *       {@link #MAX_KEY_COUNT_DELTA}.</li>
 *   <li><b>Bounding-box overlap</b> — Discard words whose template bounding box shares less than
 *       {@link #MIN_BBOX_OVERLAP_RATIO} IoU overlap with the user path's bounding box.</li>
 * </ol>
 *
 * <p>Each {@link #prune(GesturePath)} call returns a {@link PruningResult} containing the
 * surviving candidates and per-stage elimination counts, making it straightforward to tune
 * thresholds with integration tests.
 *
 * <h2>Thread safety</h2>
 * Instances of this class are <em>not</em> thread-safe. Create one per decoding thread or
 * synchronize externally. The underlying {@link TemplateCache} and {@link KeyboardLayout} are
 * shared and are themselves thread-safe (per their own contracts).
 */
public class CandidatePruner {

    // -------------------------------------------------------------------------
    // Default thresholds (all are tunable at runtime)
    // -------------------------------------------------------------------------

    /** Default multiplier applied to key width to compute the start/end key match radius. */
    public static final float DEFAULT_RADIUS_MULTIPLIER = 1.5f;

    /** Default minimum ratio: reject templates shorter than this fraction of user path length. */
    public static final float DEFAULT_MIN_LENGTH_RATIO = 0.4f;

    /** Default maximum ratio: reject templates longer than this multiple of user path length. */
    public static final float DEFAULT_MAX_LENGTH_RATIO = 2.5f;

    /**
     * Minimum intersection-over-union bounding-box overlap required for a template to survive
     * stage 5.
     */
    public static final float MIN_BBOX_OVERLAP_RATIO = 0.30f;

    /**
     * Maximum absolute difference between the user's estimated swipe key count and a template's
     * {@link TemplateCache.GestureTemplate#uniqueKeyCount} to survive stage 4.
     */
    public static final int MAX_KEY_COUNT_DELTA = 3;

    /**
     * Minimum number of unique keys a word must have.  Single-key words cannot be produced by a
     * meaningful swipe gesture.
     */
    public static final int MIN_UNIQUE_KEYS = 2;

    // -------------------------------------------------------------------------
    // Instance state
    // -------------------------------------------------------------------------

    private final KeyboardLayout layout;
    private final TemplateCache  cache;

    private float radiusMultiplier = DEFAULT_RADIUS_MULTIPLIER;
    private float minLengthRatio   = DEFAULT_MIN_LENGTH_RATIO;
    private float maxLengthRatio   = DEFAULT_MAX_LENGTH_RATIO;

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    /**
     * Creates a {@code CandidatePruner} backed by the given layout and template cache.
     *
     * @param layout the keyboard layout; used to resolve key centres and key width
     * @param cache  the template cache; provides secondary indices for fast start/end lookup
     * @throws IllegalArgumentException if either argument is null
     */
    public CandidatePruner(KeyboardLayout layout, TemplateCache cache) {
        if (layout == null) throw new IllegalArgumentException("layout must not be null");
        if (cache  == null) throw new IllegalArgumentException("cache must not be null");
        this.layout = layout;
        this.cache  = cache;
    }

    // -------------------------------------------------------------------------
    // Threshold configuration
    // -------------------------------------------------------------------------

    /**
     * Adjusts the radius multiplier used for start-key and end-key pruning (stages 1 and 2).
     *
     * <p>The match radius is {@code radiusMultiplier × keyWidth}. Increasing this value is more
     * permissive (fewer false negatives); decreasing it is more aggressive (fewer candidates,
     * but risk of missing the correct word if the user overshoots a key).
     *
     * @param radiusMultiplier positive multiplier; values below 1.0 are unusually strict
     * @throws IllegalArgumentException if {@code radiusMultiplier} is not positive
     */
    public void setPruningRadius(float radiusMultiplier) {
        if (radiusMultiplier <= 0)
            throw new IllegalArgumentException(
                    "radiusMultiplier must be positive, got " + radiusMultiplier);
        this.radiusMultiplier = radiusMultiplier;
    }

    /**
     * Adjusts the path-length ratio tolerances used in stage 3.
     *
     * <p>A template survives stage 3 if its arc length falls in
     * [{@code minRatio × userPathLength}, {@code maxRatio × userPathLength}].
     *
     * @param minRatio lower bound ratio; typically 0.3–0.5
     * @param maxRatio upper bound ratio; typically 2.0–3.0
     * @throws IllegalArgumentException if {@code minRatio ≤ 0} or {@code maxRatio ≤ minRatio}
     */
    public void setLengthTolerance(float minRatio, float maxRatio) {
        if (minRatio <= 0)
            throw new IllegalArgumentException("minRatio must be positive, got " + minRatio);
        if (maxRatio <= minRatio)
            throw new IllegalArgumentException(
                    "maxRatio must exceed minRatio (got " + minRatio + ", " + maxRatio + ")");
        this.minLengthRatio = minRatio;
        this.maxLengthRatio = maxRatio;
    }

    // -------------------------------------------------------------------------
    // Primary API
    // -------------------------------------------------------------------------

    /**
     * Runs all five pruning stages against {@code userPath} and returns the surviving candidates
     * together with per-stage elimination statistics.
     *
     * <p>For a 50 000-word dictionary, the start/end-key index lookup (stages 1+2) typically
     * reduces the candidate set to under 500 words before the linear stages 3–5 run. Total
     * wall-clock time for all five stages is well under 5 ms on modern hardware.
     *
     * @param userPath the gesture path drawn by the user; must contain at least 2 points.
     *                 The path does <em>not</em> need to be pre-normalized — raw pixel
     *                 coordinates work fine for stages 1–4. For stage 5 (bounding-box overlap)
     *                 both the user path and the template normalized path are compared in their
     *                 respective normalized spaces so scale differences are accounted for.
     * @return a {@link PruningResult} containing surviving templates and stage statistics
     * @throws IllegalArgumentException if {@code userPath} is null or has fewer than 2 points
     */
    public PruningResult prune(GesturePath userPath) {
        if (userPath == null)
            throw new IllegalArgumentException("userPath must not be null");
        if (userPath.size() < 2)
            throw new IllegalArgumentException("userPath must contain at least 2 points");

        PruningStats stats = new PruningStats();

        // ---- Pre-compute user-path properties (cheap, done once per call) ----
        List<GesturePath.Point> pts = userPath.getPoints();
        GesturePath.Point firstPt   = pts.get(0);
        GesturePath.Point lastPt    = pts.get(pts.size() - 1);
        float userLength            = userPath.totalLength();
        float keyWidth              = representativeKeyWidth();
        float radius                = radiusMultiplier * keyWidth;
        BoundingBox userBBox        = computeBoundingBox(userPath);
        int estimatedKeyCount       = estimateSwipeKeyCount(userPath, keyWidth);

        // =========================================================
        // Stages 1 + 2: Start-key and end-key pruning (index lookup)
        // =========================================================
        //
        // Resolve the nearest registered key to the user's first and last touch points,
        // then retrieve the intersection of those two index buckets from the TemplateCache.
        //
        char startKey = layout.getClosestKey(firstPt.x, firstPt.y);
        char endKey   = layout.getClosestKey(lastPt.x,  lastPt.y);

        List<TemplateCache.GestureTemplate> indexCandidates =
                cache.getCandidatesByStartEnd(startKey, endKey);

        // Refine: verify that the template's actual start/end key centres are within 'radius'
        // of the user's first/last touch points. This handles edge-key ambiguity where two
        // keys are nearly equidistant from a touch point.
        List<TemplateCache.GestureTemplate> afterStartEnd =
                new ArrayList<>(indexCandidates.size());

        for (TemplateCache.GestureTemplate t : indexCandidates) {
            GesturePath.Point tStart = layout.getKeyCenter(t.startKey);
            GesturePath.Point tEnd   = layout.getKeyCenter(t.endKey);

            boolean startOk = tStart != null
                    && distance(firstPt.x, firstPt.y, tStart.x, tStart.y) <= radius;
            boolean endOk   = tEnd != null
                    && distance(lastPt.x,  lastPt.y,  tEnd.x,   tEnd.y)   <= radius;

            if (startOk && endOk) afterStartEnd.add(t);
        }

        stats.eliminatedByStartEnd = indexCandidates.size() - afterStartEnd.size();
        stats.afterStartEndCount   = afterStartEnd.size();

        // =========================================================
        // Stage 3: Path length pruning
        // =========================================================
        List<TemplateCache.GestureTemplate> afterLength = new ArrayList<>(afterStartEnd.size());
        float minLen = minLengthRatio * userLength;
        float maxLen = maxLengthRatio * userLength;

        for (TemplateCache.GestureTemplate t : afterStartEnd) {
            if (t.pathLength >= minLen && t.pathLength <= maxLen) afterLength.add(t);
        }

        stats.eliminatedByLength = afterStartEnd.size() - afterLength.size();
        stats.afterLengthCount   = afterLength.size();

        // =========================================================
        // Stage 4: Unique key count pruning
        // =========================================================
        List<TemplateCache.GestureTemplate> afterKeyCount = new ArrayList<>(afterLength.size());

        for (TemplateCache.GestureTemplate t : afterLength) {
            if (t.uniqueKeyCount < MIN_UNIQUE_KEYS) continue;
            if (Math.abs(t.uniqueKeyCount - estimatedKeyCount) > MAX_KEY_COUNT_DELTA) continue;
            afterKeyCount.add(t);
        }

        stats.eliminatedByKeyCount = afterLength.size() - afterKeyCount.size();
        stats.afterKeyCountCount   = afterKeyCount.size();

        // =========================================================
        // Stage 5: Bounding-box overlap pruning
        // =========================================================
        // Both the user path and the template's normalizedPath are in unit-box space,
        // so the comparison is scale-invariant. We normalize the user path here so that
        // it matches the coordinate space of the stored normalized templates.
        GesturePath normalizedUser = userPath.normalizeToUnitBox();
        BoundingBox normalizedUserBBox = computeBoundingBox(normalizedUser);

        List<TemplateCache.GestureTemplate> survivors = new ArrayList<>(afterKeyCount.size());

        for (TemplateCache.GestureTemplate t : afterKeyCount) {
            BoundingBox tBBox   = computeBoundingBox(t.normalizedPath);
            float overlap       = boundingBoxOverlapRatio(normalizedUserBBox, tBBox);
            if (overlap >= MIN_BBOX_OVERLAP_RATIO) survivors.add(t);
        }

        stats.eliminatedByBoundingBox = afterKeyCount.size() - survivors.size();
        stats.afterBoundingBoxCount   = survivors.size();

        return new PruningResult(Collections.unmodifiableList(survivors), stats);
    }

    // -------------------------------------------------------------------------
    // Geometry helpers
    // -------------------------------------------------------------------------

    /**
     * Euclidean distance between two (x, y) points.
     */
    private static float distance(float x1, float y1, float x2, float y2) {
        float dx = x1 - x2;
        float dy = y1 - y2;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    /**
     * Computes the axis-aligned bounding box of a {@link GesturePath}.
     */
    private static BoundingBox computeBoundingBox(GesturePath path) {
        float minX = Float.MAX_VALUE,  minY = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        for (GesturePath.Point p : path.getPoints()) {
            if (p.x < minX) minX = p.x;
            if (p.y < minY) minY = p.y;
            if (p.x > maxX) maxX = p.x;
            if (p.y > maxY) maxY = p.y;
        }
        return new BoundingBox(minX, minY, maxX, maxY);
    }

    /**
     * Returns the intersection-over-union (IoU) overlap ratio of two bounding boxes, in [0, 1].
     *
     * <p>Returns 0 if either box is degenerate (zero area) or if the boxes do not intersect.
     */
    static float boundingBoxOverlapRatio(BoundingBox a, BoundingBox b) {
        float interMinX = Math.max(a.minX, b.minX);
        float interMinY = Math.max(a.minY, b.minY);
        float interMaxX = Math.min(a.maxX, b.maxX);
        float interMaxY = Math.min(a.maxY, b.maxY);

        if (interMaxX <= interMinX || interMaxY <= interMinY) return 0f;

        float interArea = (interMaxX - interMinX) * (interMaxY - interMinY);
        float aArea     = (a.maxX - a.minX) * (a.maxY - a.minY);
        float bArea     = (b.maxX - b.minX) * (b.maxY - b.minY);
        float unionArea = aArea + bArea - interArea;

        return (unionArea <= 0f) ? 0f : interArea / unionArea;
    }

    /**
     * Estimates the number of distinct keys visited during the swipe by counting how many
     * key-width-sized steps fit along the path, augmented by direction-change events.
     *
     * <p>This is a heuristic — exactness is not required. It is used only for the
     * ±{@link #MAX_KEY_COUNT_DELTA} window in stage 4.
     *
     * @param path     the raw user swipe path
     * @param keyWidth key width in the same coordinate space as the path
     * @return estimated unique key count, always at least 1
     */
    static int estimateSwipeKeyCount(GesturePath path, float keyWidth) {
        List<GesturePath.Point> pts = path.getPoints();
        if (pts.size() < 2) return 1;

        int   keyCount         = 1;
        float accumulatedDist  = 0f;
        float prevAngle        = Float.NaN;
        final float keyStepDist        = keyWidth * 0.9f;     // ~90 % of a key = new key
        final float angleChangeThresh  = (float)(Math.PI / 4); // 45 degrees

        for (int i = 1; i < pts.size(); i++) {
            GesturePath.Point prev = pts.get(i - 1);
            GesturePath.Point curr = pts.get(i);
            float dx     = curr.x - prev.x;
            float dy     = curr.y - prev.y;
            float segLen = (float) Math.sqrt(dx * dx + dy * dy);

            accumulatedDist += segLen;

            // Count a new key every time we travel an additional key-width of distance.
            if (accumulatedDist >= keyStepDist) {
                keyCount++;
                accumulatedDist = 0f;
            }

            // Also count a new key on a significant direction change.
            if (segLen > 0.001f) {
                float angle = (float) Math.atan2(dy, dx);
                if (!Float.isNaN(prevAngle)) {
                    float delta = Math.abs(angleDiff(angle, prevAngle));
                    if (delta > angleChangeThresh) {
                        keyCount++;
                        accumulatedDist = 0f;
                    }
                }
                prevAngle = angle;
            }
        }

        return Math.max(1, keyCount);
    }

    /**
     * Returns the smallest signed angular difference between two angles in radians,
     * normalized to (−π, π].
     */
    private static float angleDiff(float a, float b) {
        float diff = a - b;
        while (diff >  (float) Math.PI)  diff -= 2f * (float) Math.PI;
        while (diff < -(float) Math.PI)  diff += 2f * (float) Math.PI;
        return diff;
    }

    /**
     * Returns the representative key width from the current layout.
     * Delegates to {@link KeyboardLayout#getKeyWidth()}, which returns the average key width
     * across all registered keys (or a sane fallback if no keys are present).
     */
    private float representativeKeyWidth() {
        return layout.getKeyWidth();
    }

    // =========================================================================
    // Inner class: BoundingBox
    // =========================================================================

    /**
     * Lightweight axis-aligned bounding box. Used internally by stage 5.
     */
    static final class BoundingBox {
        final float minX, minY, maxX, maxY;

        BoundingBox(float minX, float minY, float maxX, float maxY) {
            this.minX = minX;
            this.minY = minY;
            this.maxX = maxX;
            this.maxY = maxY;
        }

        float width()  { return maxX - minX; }
        float height() { return maxY - minY; }
        float area()   { return width() * height(); }

        @Override
        public String toString() {
            return String.format("BBox[%.2f,%.2f → %.2f,%.2f]", minX, minY, maxX, maxY);
        }
    }

    // =========================================================================
    // Inner class: PruningStats
    // =========================================================================

    /**
     * Per-stage elimination counts from a single {@link CandidatePruner#prune(GesturePath)} call.
     *
     * <p>All {@code eliminatedBy*} fields record how many candidates were <em>removed</em> by
     * that stage; the corresponding {@code after*Count} fields record how many survived.
     *
     * <p>Typical use — log in tests to verify that thresholds are tuned correctly:
     * <pre>{@code
     * PruningResult result = pruner.prune(userPath);
     * PruningStats  stats  = result.stats;
     * System.out.println("After start/end: " + stats.afterStartEndCount);
     * System.out.println("After length:    " + stats.afterLengthCount);
     * System.out.println("After keycount:  " + stats.afterKeyCountCount);
     * System.out.println("Final survivors: " + stats.afterBoundingBoxCount);
     * }</pre>
     */
    public static final class PruningStats {

        /** Candidates eliminated by stage 1+2 (start/end key proximity). */
        public int eliminatedByStartEnd;
        /** Candidates remaining after stage 1+2. */
        public int afterStartEndCount;

        /** Candidates eliminated by stage 3 (path length ratio). */
        public int eliminatedByLength;
        /** Candidates remaining after stage 3. */
        public int afterLengthCount;

        /** Candidates eliminated by stage 4 (unique key count). */
        public int eliminatedByKeyCount;
        /** Candidates remaining after stage 4. */
        public int afterKeyCountCount;

        /** Candidates eliminated by stage 5 (bounding-box IoU overlap). */
        public int eliminatedByBoundingBox;
        /** Candidates remaining after stage 5 (= final output count). */
        public int afterBoundingBoxCount;

        @Override
        public String toString() {
            return "PruningStats{"
                    + "startEnd=" + afterStartEndCount   + "(-" + eliminatedByStartEnd + "), "
                    + "length="   + afterLengthCount     + "(-" + eliminatedByLength   + "), "
                    + "keyCount=" + afterKeyCountCount   + "(-" + eliminatedByKeyCount + "), "
                    + "bbox="     + afterBoundingBoxCount + "(-" + eliminatedByBoundingBox + ")"
                    + '}';
        }
    }

    // =========================================================================
    // Inner class: PruningResult
    // =========================================================================

    /**
     * The output of a single {@link CandidatePruner#prune(GesturePath)} call.
     *
     * <p>Contains the final candidate list, ready for shape-matching, and the per-stage
     * statistics that explain how many words were eliminated at each step.
     */
    public static final class PruningResult {

        /**
         * Surviving candidates after all five pruning stages, in dictionary index order.
         * This list is unmodifiable.
         */
        public final List<TemplateCache.GestureTemplate> candidates;

        /** Per-stage elimination counts; never null. */
        public final PruningStats stats;

        PruningResult(List<TemplateCache.GestureTemplate> candidates, PruningStats stats) {
            this.candidates = candidates;
            this.stats      = stats;
        }

        /** Number of candidates that survived all five pruning stages. */
        public int size() { return candidates.size(); }

        @Override
        public String toString() {
            return "PruningResult{survivors=" + candidates.size() + ", " + stats + '}';
        }
    }
}
