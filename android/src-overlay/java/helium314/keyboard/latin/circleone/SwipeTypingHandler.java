/*
 * Copyright (C) 2026 The Other Bhengu (Pty) Ltd t/a The Geek.
 * SPDX-License-Identifier: GPL-3.0-only
 *
 * This file is part of CircleOne Keyboard.
 *
 * CircleOne Keyboard is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3 of the License only.
 *
 * CircleOne Keyboard is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with CircleOne Keyboard. If not, see <https://www.gnu.org/licenses/>.
 */

package helium314.keyboard.latin.circleone;

import android.inputmethodservice.InputMethodService;
import android.view.MotionEvent;

import com.thegeek.gesture.GestureDecoder;
import com.thegeek.gesture.GesturePath;
import com.thegeek.gesture.KeyboardLayout;
// KeyboardLayout.addKey takes primitives directly — no KeyCell class needed

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * SwipeTypingHandler — CircleOne's bridge between HeliBoard touch events and the Open Gesture
 * Decoder library ({@code com.thegeek.gesture}).
 *
 * <h2>Responsibilities</h2>
 * <ul>
 *   <li>Captures {@link MotionEvent#ACTION_DOWN}, {@link MotionEvent#ACTION_MOVE} and
 *       {@link MotionEvent#ACTION_UP} from the keyboard view.</li>
 *   <li>Distinguishes swipe gestures from normal taps by comparing total path length against a
 *       threshold of <em>3 × average key width</em>.</li>
 *   <li>Feeds sampled touch points into a {@link GesturePath} instance.</li>
 *   <li>On gesture completion ({@code ACTION_UP}), calls {@link GestureDecoder#decode(GesturePath)}
 *       and returns ranked word candidates to the caller via {@link SwipeListener}.</li>
 *   <li>Maintains the {@link GestureDecoder} lifecycle: initialises it with a
 *       {@link KeyboardLayout}, and reloads the word list whenever {@link #setDictionary} is
 *       called.</li>
 *   <li>Publishes swipe trail co-ordinates so the keyboard view can draw a visual path.</li>
 * </ul>
 *
 * <h2>Minimum swipe distance</h2>
 * <p>Any gesture whose accumulated Euclidean path length is shorter than
 * {@code MIN_SWIPE_DISTANCE_KEYS × keyWidth} is treated as a tap and <em>not</em> consumed —
 * {@link #onTouchEvent} returns {@code false} so HeliBoard processes the tap normally.</p>
 *
 * <h2>Thread safety</h2>
 * <p>All methods must be called from the main thread. {@link GestureDecoder#decode} is
 * intentionally invoked synchronously on the main thread because the library's pruning algorithm
 * completes well under 50 ms even for 50 k-word dictionaries (see library README for benchmarks).
 * If empirical profiling reveals frame-budget issues on low-end devices, move the decode call to
 * a background thread and post the result back via a {@link android.os.Handler}.</p>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * // In LatinIME (or wherever CircleOne hooks the keyboard view):
 * SwipeTypingHandler swipeHandler = new SwipeTypingHandler(this);
 * swipeHandler.setDictionary(myWordList);
 * swipeHandler.setSwipeListener(new SwipeTypingHandler.SwipeListener() {
 *     public void onSwipeCandidates(List<String> candidates) {
 *         // push candidates into the suggestion strip
 *     }
 *     public void onSwipeStarted() { /* show trail view *\/ }
 *     public void onSwipeTrailUpdate(float[] x, float[] y, int count) { /* draw trail *\/ }
 *     public void onSwipeEnded()   { /* hide trail view *\/ }
 * });
 *
 * // In MainKeyboardView.onTouchEvent:
 * if (swipeHandler.onTouchEvent(event, keyboardWidth, keyboardHeight)) return true;
 * // …fall through to normal HeliBoard handling
 * }</pre>
 */
public final class SwipeTypingHandler {

    // -------------------------------------------------------------------------
    // Constants
    // -------------------------------------------------------------------------

    /**
     * A gesture must span at least this many <em>average key widths</em> in total path length
     * before it is classified as a swipe. Below this threshold the event sequence is treated as a
     * tap and not consumed.
     */
    private static final float MIN_SWIPE_DISTANCE_KEYS = 3.0f;

    /**
     * Maximum number of raw touch points retained in the in-progress trail arrays.
     * Older points beyond this limit are dropped (FIFO) to cap memory usage during long words.
     */
    private static final int MAX_TRAIL_POINTS = 512;

    /**
     * Minimum pixel distance between consecutive sampled points. Points closer than this are
     * skipped to reduce noise without losing shape information.
     */
    private static final float MIN_SAMPLE_DISTANCE_PX = 4.0f;

    // -------------------------------------------------------------------------
    // State
    // -------------------------------------------------------------------------

    /** The hosting {@link InputMethodService}; used for context and resources. */
    private final InputMethodService mService;

    /** Decodes a completed {@link GesturePath} into ranked word candidates. */
    private GestureDecoder mDecoder;

    /** Accumulates touch points for the current in-progress gesture. */
    private GesturePath mCurrentPath;

    /** Whether the handler is enabled. When disabled all touch events are passed through. */
    private boolean mEnabled = true;

    /**
     * Whether a swipe gesture is currently in progress (i.e. the user put a finger down and has
     * moved far enough to exceed the tap threshold).
     */
    private boolean mGestureInProgress = false;

    /**
     * Whether the finger is currently down but has not yet crossed the swipe threshold.
     * Used to batch-feed the pre-threshold points once the threshold is crossed.
     */
    private boolean mFingerDown = false;

    /** Listener that receives gesture events. */
    private SwipeListener mListener;

    // Trail point arrays (parallel x/y, ring-buffer semantics).
    private float[] mTrailX = new float[MAX_TRAIL_POINTS];
    private float[] mTrailY = new float[MAX_TRAIL_POINTS];
    private int     mTrailCount = 0;

    /** X-coordinate of the previous sampled point; used for distance filtering. */
    private float mLastSampledX;
    /** Y-coordinate of the previous sampled point; used for distance filtering. */
    private float mLastSampledY;

    /** X-coordinate where the finger first touched down. */
    private float mDownX;
    /** Y-coordinate where the finger first touched down. */
    private float mDownY;

    /** Total accumulated path length of the current gesture in pixels. */
    private float mPathLength = 0f;

    /** Average key width derived from the keyboard dimensions supplied at touch time. */
    private float mKeyWidth = 0f;

    /** Most recently known keyboard width, used when building a KeyboardLayout. */
    private int mKeyboardWidth = 0;
    /** Most recently known keyboard height, used when building a KeyboardLayout. */
    private int mKeyboardHeight = 0;

    /** Word list loaded via {@link #setDictionary}. */
    private List<String> mDictionary = Collections.emptyList();

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    /**
     * Creates a new {@code SwipeTypingHandler}.
     *
     * <p>The decoder is not fully initialised until the first call to
     * {@link #onTouchEvent(MotionEvent, int, int)} supplies the keyboard dimensions, or until
     * {@link #setDictionary(List)} is called.</p>
     *
     * @param service the hosting {@link InputMethodService}; must not be {@code null}.
     */
    public SwipeTypingHandler(InputMethodService service) {
        if (service == null) throw new IllegalArgumentException("service must not be null");
        mService = service;
        // Decoder needs a layout — use a default QWERTY; will be rebuilt when keyboard dimensions are known
        mDecoder = new GestureDecoder(KeyboardLayout.qwerty(1080, 600));
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Processes a touch event from the keyboard view.
     *
     * <p>This method should be called from {@code MainKeyboardView.onTouchEvent} <em>before</em>
     * the normal HeliBoard event handling. If this method returns {@code true}, HeliBoard should
     * not process the event further.</p>
     *
     * @param event          the {@link MotionEvent} from the keyboard view.
     * @param keyboardWidth  the current keyboard view width in pixels.
     * @param keyboardHeight the current keyboard view height in pixels.
     * @return {@code true} if the event was consumed as part of a swipe gesture,
     *         {@code false} if HeliBoard should handle it as a normal tap/press.
     */
    public boolean onTouchEvent(MotionEvent event, int keyboardWidth, int keyboardHeight) {
        if (!mEnabled) return false;

        // Update keyboard dimensions on every call (layout can change mid-session).
        mKeyboardWidth  = keyboardWidth;
        mKeyboardHeight = keyboardHeight;

        // Estimate average key width assuming a standard 10-column QWERTY row.
        // The gesture library uses this as a spatial reference for shape matching.
        mKeyWidth = keyboardWidth / 10.0f;

        final int action = event.getActionMasked();

        switch (action) {
            case MotionEvent.ACTION_DOWN:
                return handleDown(event);

            case MotionEvent.ACTION_MOVE:
                return handleMove(event);

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                return handleUp(event, action == MotionEvent.ACTION_CANCEL);

            default:
                return false;
        }
    }

    /**
     * Returns {@code true} while the user is actively swiping (i.e. after the swipe threshold
     * has been crossed and before {@link MotionEvent#ACTION_UP}).
     *
     * @return {@code true} if a swipe gesture is in progress.
     */
    public boolean isGestureInProgress() {
        return mGestureInProgress;
    }

    /**
     * Replaces the word list used by the gesture decoder.
     *
     * <p>Call this whenever the active language or dictionary changes. The decoder is re-initialised
     * immediately; any in-progress gesture is not affected.</p>
     *
     * @param words the new word list; must not be {@code null}.
     * @throws IllegalArgumentException if {@code words} is {@code null}.
     */
    public void setDictionary(List<String> words) {
        if (words == null) throw new IllegalArgumentException("words must not be null");
        mDictionary = new ArrayList<>(words);
        rebuildDecoder();
    }

    /**
     * Registers a {@link SwipeListener} to receive gesture events.
     *
     * <p>Passing {@code null} clears the current listener.</p>
     *
     * @param listener the listener, or {@code null} to unregister.
     */
    public void setSwipeListener(SwipeListener listener) {
        mListener = listener;
    }

    /**
     * Enables or disables swipe gesture processing.
     *
     * <p>When disabled, {@link #onTouchEvent} always returns {@code false} and all events are
     * passed through to HeliBoard unchanged. Any in-progress gesture is silently cancelled.</p>
     *
     * @param enabled {@code true} to enable, {@code false} to disable.
     */
    public void setEnabled(boolean enabled) {
        if (!enabled && mGestureInProgress) {
            cancelGesture();
        }
        mEnabled = enabled;
    }

    /**
     * Returns whether swipe gesture processing is currently enabled.
     *
     * @return {@code true} if enabled.
     */
    public boolean isEnabled() {
        return mEnabled;
    }

    /**
     * Returns a snapshot of the current swipe trail for rendering.
     *
     * <p>The returned array contains interleaved {@code [x0, y0, x1, y1, …]} co-ordinates.
     * The length of the array is {@code 2 × getGestureTrailPointCount()}. If no gesture is in
     * progress the array is empty (not {@code null}).</p>
     *
     * @return a newly allocated {@code float[]} with the trail co-ordinates.
     */
    public float[] getGestureTrailPoints() {
        if (mTrailCount == 0) return new float[0];
        float[] result = new float[mTrailCount * 2];
        for (int i = 0; i < mTrailCount; i++) {
            result[i * 2]     = mTrailX[i];
            result[i * 2 + 1] = mTrailY[i];
        }
        return result;
    }

    // -------------------------------------------------------------------------
    // SwipeListener interface
    // -------------------------------------------------------------------------

    /**
     * Callback interface for swipe gesture events.
     *
     * <p>Implementations are always called on the main thread.</p>
     */
    public interface SwipeListener {

        /**
         * Called when gesture decoding is complete and ranked word candidates are available.
         *
         * <p>The list is ordered by descending confidence: {@code candidates.get(0)} is the
         * best match. The list contains at most the number of candidates returned by
         * {@link GestureDecoder#decode}.</p>
         *
         * @param candidates the ranked word candidates; never {@code null}, may be empty.
         */
        void onSwipeCandidates(List<String> candidates);

        /**
         * Called the moment the swipe threshold is crossed and a gesture begins.
         *
         * <p>Implementations should show the gesture trail overlay.</p>
         */
        void onSwipeStarted();

        /**
         * Called after every sampled point is added to the trail.
         *
         * <p>Implementations should redraw the trail using the supplied co-ordinates.</p>
         *
         * @param pathX      array of x co-ordinates; length is {@code pointCount}.
         * @param pathY      array of y co-ordinates; length is {@code pointCount}.
         * @param pointCount number of valid points in {@code pathX} and {@code pathY}.
         */
        void onSwipeTrailUpdate(float[] pathX, float[] pathY, int pointCount);

        /**
         * Called when the swipe gesture ends (finger lifted) and visual cleanup should occur.
         *
         * <p>Implementations should hide the gesture trail overlay.</p>
         */
        void onSwipeEnded();
    }

    // -------------------------------------------------------------------------
    // Touch event handlers (private)
    // -------------------------------------------------------------------------

    /**
     * Handles {@link MotionEvent#ACTION_DOWN}: records the finger-down position and resets state.
     *
     * @param event the touch event.
     * @return always {@code false} — we don't consume {@code ACTION_DOWN} immediately because we
     *         cannot yet tell whether it will become a tap or a swipe.
     */
    private boolean handleDown(MotionEvent event) {
        mFingerDown       = true;
        mGestureInProgress = false;
        mPathLength        = 0f;
        mTrailCount        = 0;
        mDownX             = event.getX();
        mDownY             = event.getY();
        mLastSampledX      = mDownX;
        mLastSampledY      = mDownY;

        // Begin accumulating a fresh GesturePath.
        mCurrentPath = new GesturePath();
        mCurrentPath.addPoint(mDownX, mDownY, event.getEventTime());

        return false; // Let HeliBoard handle the key-press visual feedback.
    }

    /**
     * Handles {@link MotionEvent#ACTION_MOVE}: samples points, accumulates path length, and
     * detects threshold crossing.
     *
     * @param event the touch event (may carry multiple historical samples via
     *              {@link MotionEvent#getHistoricalX}).
     * @return {@code true} once the swipe threshold has been crossed (to prevent HeliBoard from
     *         treating intermediate move events as key previews), {@code false} before threshold.
     */
    private boolean handleMove(MotionEvent event) {
        if (!mFingerDown) return false;

        // Process all historical samples first to preserve shape fidelity.
        final int histCount = event.getHistorySize();
        for (int h = 0; h < histCount; h++) {
            processPoint(event.getHistoricalX(h), event.getHistoricalY(h),
                         event.getHistoricalEventTime(h));
        }
        processPoint(event.getX(), event.getY(), event.getEventTime());

        return mGestureInProgress;
    }

    /**
     * Handles {@link MotionEvent#ACTION_UP} and {@link MotionEvent#ACTION_CANCEL}.
     *
     * <p>If a swipe was in progress, the gesture path is finalised and dispatched to
     * {@link GestureDecoder#decode(GesturePath)}. On cancel, the gesture is silently abandoned.</p>
     *
     * @param event    the touch event.
     * @param cancelled {@code true} if the gesture should be silently abandoned.
     * @return {@code true} if the finger-up was consumed (i.e. it ended a swipe gesture),
     *         {@code false} if it should be handled as a tap by HeliBoard.
     */
    private boolean handleUp(MotionEvent event, boolean cancelled) {
        if (!mFingerDown) return false;
        mFingerDown = false;

        if (!mGestureInProgress) {
            // Total path never crossed the threshold → treat as a tap.
            resetState();
            return false;
        }

        if (cancelled) {
            cancelGesture();
            return true;
        }

        // Add the finger-up point.
        mCurrentPath.addPoint(event.getX(), event.getY(), event.getEventTime());

        // Decode the completed path.
        List<String> candidates = Collections.emptyList();
        try {
            List<GestureDecoder.Candidate> decoded = mDecoder.decode(mCurrentPath, 5);
            if (decoded != null && !decoded.isEmpty()) {
                candidates = new ArrayList<>(decoded.size());
                for (GestureDecoder.Candidate c : decoded) {
                    candidates.add(c.word);
                }
            }
        } catch (Exception e) {
            // Decoder failure must never crash the keyboard.
            android.util.Log.e("SwipeTypingHandler", "Gesture decode failed", e);
        }

        // Notify listener.
        if (mListener != null) {
            mListener.onSwipeEnded();
            if (!candidates.isEmpty()) {
                mListener.onSwipeCandidates(candidates);
            }
        }

        resetState();
        return true;
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    /**
     * Processes a single touch sample: applies distance filtering, appends to the path,
     * accumulates path length, and fires the threshold-crossing logic.
     *
     * @param x         the x co-ordinate of the sample.
     * @param y         the y co-ordinate of the sample.
     * @param timestamp the event timestamp in milliseconds.
     */
    private void processPoint(float x, float y, long timestamp) {
        final float dx = x - mLastSampledX;
        final float dy = y - mLastSampledY;
        final float dist = (float) Math.sqrt(dx * dx + dy * dy);

        if (dist < MIN_SAMPLE_DISTANCE_PX) return; // Too close to the previous sample — skip.

        mPathLength   += dist;
        mLastSampledX  = x;
        mLastSampledY  = y;

        if (mCurrentPath != null) {
            mCurrentPath.addPoint(x, y, timestamp);
        }

        // Detect threshold crossing.
        final float threshold = MIN_SWIPE_DISTANCE_KEYS * mKeyWidth;
        if (!mGestureInProgress && mPathLength >= threshold) {
            mGestureInProgress = true;
            ensureDecoderReady();
            if (mListener != null) {
                mListener.onSwipeStarted();
            }
        }

        if (mGestureInProgress) {
            appendTrailPoint(x, y);
            if (mListener != null) {
                // Publish a snapshot of the parallel arrays (not the live arrays) so the
                // listener cannot mutate our internal state.
                float[] snapX = java.util.Arrays.copyOf(mTrailX, mTrailCount);
                float[] snapY = java.util.Arrays.copyOf(mTrailY, mTrailCount);
                mListener.onSwipeTrailUpdate(snapX, snapY, mTrailCount);
            }
        }
    }

    /**
     * Appends a point to the in-progress trail, evicting the oldest point when the buffer is full.
     *
     * @param x the x co-ordinate.
     * @param y the y co-ordinate.
     */
    private void appendTrailPoint(float x, float y) {
        if (mTrailCount < MAX_TRAIL_POINTS) {
            mTrailX[mTrailCount] = x;
            mTrailY[mTrailCount] = y;
            mTrailCount++;
        } else {
            // Ring-buffer: shift everything left by one to evict the oldest point.
            System.arraycopy(mTrailX, 1, mTrailX, 0, MAX_TRAIL_POINTS - 1);
            System.arraycopy(mTrailY, 1, mTrailY, 0, MAX_TRAIL_POINTS - 1);
            mTrailX[MAX_TRAIL_POINTS - 1] = x;
            mTrailY[MAX_TRAIL_POINTS - 1] = y;
        }
    }

    /**
     * Cancels any in-progress gesture without delivering candidates to the listener.
     */
    private void cancelGesture() {
        if (mGestureInProgress && mListener != null) {
            mListener.onSwipeEnded();
        }
        resetState();
    }

    /**
     * Resets all per-gesture mutable state ready for the next touch sequence.
     */
    private void resetState() {
        mFingerDown        = false;
        mGestureInProgress = false;
        mPathLength        = 0f;
        mTrailCount        = 0;
        mCurrentPath       = null;
    }

    /**
     * Ensures the {@link GestureDecoder} has been initialised with the current keyboard layout.
     * Called lazily when the swipe threshold is crossed.
     */
    private void ensureDecoderReady() {
        if (mKeyboardWidth <= 0 || mKeyboardHeight <= 0) return;

        KeyboardLayout layout = buildLayout(mKeyboardWidth, mKeyboardHeight);
        try {
            mDecoder = new GestureDecoder(layout);
            if (mDictionary != null) mDecoder.setDictionary(mDictionary);
        } catch (Exception e) {
            android.util.Log.e("SwipeTypingHandler", "GestureDecoder init failed", e);
        }
    }

    /**
     * Rebuilds the decoder from scratch using the latest dictionary and keyboard dimensions.
     * Safe to call even when {@link #mKeyboardWidth}/{@link #mKeyboardHeight} are not yet known —
     * in that case {@link #ensureDecoderReady()} will finish the job at gesture time.
     */
    private void rebuildDecoder() {
        mDecoder = new GestureDecoder(KeyboardLayout.qwerty(
                mKeyboardWidth > 0 ? mKeyboardWidth : 1080,
                mKeyboardHeight > 0 ? mKeyboardHeight : 600));
        if (mKeyboardWidth > 0 && mKeyboardHeight > 0) {
            ensureDecoderReady();
        }
    }

    /**
     * Builds a {@link KeyboardLayout} approximating a standard QWERTY layout scaled to the
     * supplied pixel dimensions.
     *
     * <p>This approximation is sufficient for the gesture decoder's shape-matching algorithm.
     * A future iteration could read the actual HeliBoard key positions from
     * {@code Keyboard.getKey()} to achieve higher accuracy.</p>
     *
     * @param widthPx  keyboard view width in pixels.
     * @param heightPx keyboard view height in pixels.
     * @return a {@link KeyboardLayout} describing approximate key centre positions.
     */
    private static KeyboardLayout buildLayout(int widthPx, int heightPx) {
        // QWERTY rows. Each string character represents one key.
        final String[] rows    = { "qwertyuiop", "asdfghjkl", "zxcvbnm" };
        final float    keyW    = widthPx / 10.0f;
        final float    keyH    = heightPx / 4.0f; // Approximate — 4 rows including spacebar.
        final float[]  offsets = { 0f, keyW * 0.5f, keyW * 1.5f }; // Row indentation.

        KeyboardLayout layout = new KeyboardLayout(widthPx, heightPx);

        for (int row = 0; row < rows.length; row++) {
            String keys   = rows[row];
            float  startX = offsets[row] + keyW / 2f;
            float  cy     = keyH * row + keyH / 2f;

            for (int col = 0; col < keys.length(); col++) {
                float cx = startX + col * keyW;
                layout.addKey(keys.charAt(col), cx, cy, keyW, keyH);
            }
        }

        return layout;
    }
}
