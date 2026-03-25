/*
 * Copyright (C) 2026 The Other Bhengu (Pty) Ltd t/a The Geek.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package helium314.keyboard.latin.circleone;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.inputmethodservice.InputMethodService;
import android.os.Build;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;

/**
 * FloatingKeyboardController — drag-to-reposition, resizable floating keyboard panel.
 *
 * <p>Wraps any keyboard {@link View} in a decorated {@link FrameLayout} that can be:
 * <ul>
 *   <li>Dragged freely by the user via a drag handle bar at the top.</li>
 *   <li>Resized from any of the four corners.</li>
 *   <li>Snapped to the left or right screen edge when released close enough.</li>
 *   <li>Persisted across sessions via {@link SharedPreferences}.</li>
 * </ul>
 *
 * <h3>InputMethodService constraints</h3>
 * <p>Because this runs inside an {@link InputMethodService} there is no access to
 * {@link WindowManager#LayoutParams#TYPE_APPLICATION_OVERLAY}. Instead the controller
 * adjusts the gravity and margins of the IME window's root view so that the keyboard
 * panel appears to float. The host IME must call
 * {@link #wrapInputView(View)} in
 * {@code InputMethodService.onCreateInputView()} and return the result.
 *
 * <h3>Size constraints</h3>
 * <ul>
 *   <li>Minimum width: 50 % of screen width.</li>
 *   <li>Maximum width: 100 % of screen width.</li>
 *   <li>Minimum height: 120 dp.</li>
 *   <li>Maximum height: 80 % of screen height.</li>
 * </ul>
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * // Inside your InputMethodService subclass:
 * private FloatingKeyboardController mFloatingController;
 *
 * @Override
 * public View onCreateInputView() {
 *     View keyboardView = getLayoutInflater().inflate(R.layout.keyboard_view, null);
 *     mFloatingController = new FloatingKeyboardController(this);
 *     return mFloatingController.wrapInputView(keyboardView);
 * }
 *
 * // To toggle floating mode:
 * mFloatingController.setFloatingMode(true);
 * }</pre>
 */
public class FloatingKeyboardController {

    private static final String TAG = "FloatingKbdCtrl";

    // ── SharedPreferences keys ──────────────────────────────────────────────
    private static final String PREFS_NAME       = "floating_keyboard_state";
    private static final String KEY_FLOATING      = "floating_mode";
    private static final String KEY_OFFSET_X      = "offset_x";
    private static final String KEY_OFFSET_Y      = "offset_y";
    private static final String KEY_WIDTH_FRAC    = "width_fraction";
    private static final String KEY_HEIGHT_PX     = "height_px";

    // ── Geometry defaults ───────────────────────────────────────────────────
    /** Default width as a fraction of screen width when floating mode first activates. */
    private static final float DEFAULT_WIDTH_FRACTION  = 0.75f;
    /** Snap-to-edge threshold in dp — release within this distance to snap. */
    private static final float SNAP_THRESHOLD_DP       = 48f;
    /** Corner resize handle size in dp. */
    private static final float RESIZE_HANDLE_DP        = 28f;
    /** Drag handle height in dp. */
    private static final float DRAG_HANDLE_HEIGHT_DP   = 24f;
    /** Dot radius in the drag handle, in dp. */
    private static final float DOT_RADIUS_DP           = 2.5f;
    /** Spacing between dots in the drag handle, in dp. */
    private static final float DOT_SPACING_DP          = 8f;
    /** Minimum height of the keyboard panel in dp. */
    private static final float MIN_HEIGHT_DP           = 120f;
    /** Maximum height as a fraction of screen height. */
    private static final float MAX_HEIGHT_FRACTION     = 0.80f;
    /** Minimum width fraction (50 % of screen width). */
    private static final float MIN_WIDTH_FRACTION      = 0.50f;

    // ── Touch gesture states ────────────────────────────────────────────────
    private static final int TOUCH_NONE    = 0;
    private static final int TOUCH_DRAG    = 1;
    private static final int TOUCH_RESIZE  = 2;

    // ── Corner indices for resize handles ──────────────────────────────────
    private static final int CORNER_TOP_LEFT     = 0;
    private static final int CORNER_TOP_RIGHT    = 1;
    private static final int CORNER_BOTTOM_LEFT  = 2;
    private static final int CORNER_BOTTOM_RIGHT = 3;

    // ── Members ─────────────────────────────────────────────────────────────
    private final InputMethodService mService;
    private final float mDensity;

    private FloatingContainer mContainer;
    private View              mKeyboardView;

    private boolean mFloatingMode = false;

    /** Current horizontal offset from the left edge of the IME window, in pixels. */
    private float mOffsetX;
    /** Current vertical offset from the bottom of the IME window, in pixels. */
    private float mOffsetY;
    /** Current panel width as a fraction [MIN_WIDTH_FRACTION, 1.0] of screen width. */
    private float mWidthFraction = DEFAULT_WIDTH_FRACTION;
    /** Current panel height in pixels. */
    private int   mHeightPx;

    // ── Constructor ─────────────────────────────────────────────────────────

    /**
     * Creates a new controller bound to the given {@link InputMethodService}.
     *
     * @param service the host input method service; must not be null.
     */
    public FloatingKeyboardController(InputMethodService service) {
        if (service == null) throw new IllegalArgumentException("service must not be null");
        mService = service;
        mDensity = service.getResources().getDisplayMetrics().density;
    }

    // ── Public API ──────────────────────────────────────────────────────────

    /**
     * Wraps {@code keyboardView} in a floating container with a drag handle and corner
     * resize handles, then returns the container.
     *
     * <p>Return the container from {@code InputMethodService.onCreateInputView()}.
     * Calling this method a second time replaces the previously wrapped view.
     *
     * @param keyboardView the keyboard view to wrap; must not be null.
     * @return a {@link FrameLayout} that contains the drag handle and keyboard view.
     */
    public View wrapInputView(View keyboardView) {
        if (keyboardView == null) throw new IllegalArgumentException("keyboardView must not be null");
        mKeyboardView = keyboardView;

        mContainer = new FloatingContainer(mService);

        // The keyboard view fills the container below the drag handle.
        FrameLayout.LayoutParams kbParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);
        kbParams.topMargin = dpToPx(DRAG_HANDLE_HEIGHT_DP);
        mContainer.addView(keyboardView, kbParams);

        applyGeometry();
        return mContainer;
    }

    /**
     * Enables or disables the floating mode.
     *
     * <p>When enabled the keyboard panel is positioned at the last saved location (or
     * the default center-bottom position). When disabled the keyboard panel fills the
     * IME window normally.
     *
     * @param enabled {@code true} to enable floating mode.
     */
    public void setFloatingMode(boolean enabled) {
        mFloatingMode = enabled;
        if (mContainer != null) {
            applyGeometry();
        }
    }

    /**
     * Returns {@code true} if the keyboard is currently in floating mode.
     *
     * @return current floating mode state.
     */
    public boolean isFloatingMode() {
        return mFloatingMode;
    }

    /**
     * Resets the panel to the default center-bottom position and the default width.
     *
     * <p>Has no visual effect if the container has not yet been created via
     * {@link #wrapInputView(View)}.
     */
    public void resetPosition() {
        mOffsetX = 0f;
        mOffsetY = 0f;
        mWidthFraction = DEFAULT_WIDTH_FRACTION;
        mHeightPx = 0; // will be recalculated in applyGeometry
        if (mContainer != null) {
            applyGeometry();
        }
    }

    /**
     * Persists the current floating mode, position, and size to {@link SharedPreferences}.
     *
     * @param context any context; used only to obtain preferences.
     */
    public void saveState(Context context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_FLOATING,   mFloatingMode)
                .putFloat(KEY_OFFSET_X,     mOffsetX)
                .putFloat(KEY_OFFSET_Y,     mOffsetY)
                .putFloat(KEY_WIDTH_FRAC,   mWidthFraction)
                .putInt(KEY_HEIGHT_PX,      mHeightPx)
                .apply();
        Log.d(TAG, "State saved: floating=" + mFloatingMode
                + " x=" + mOffsetX + " y=" + mOffsetY
                + " wFrac=" + mWidthFraction + " h=" + mHeightPx);
    }

    /**
     * Restores floating mode, position, and size from {@link SharedPreferences}.
     *
     * <p>Call this before {@link #wrapInputView(View)} so that the first layout
     * already reflects the saved position.
     *
     * @param context any context; used only to obtain preferences.
     */
    public void restoreState(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        mFloatingMode    = prefs.getBoolean(KEY_FLOATING,   false);
        mOffsetX         = prefs.getFloat(KEY_OFFSET_X,     0f);
        mOffsetY         = prefs.getFloat(KEY_OFFSET_Y,     0f);
        mWidthFraction   = prefs.getFloat(KEY_WIDTH_FRAC,   DEFAULT_WIDTH_FRACTION);
        mHeightPx        = prefs.getInt(KEY_HEIGHT_PX,      0);
        Log.d(TAG, "State restored: floating=" + mFloatingMode
                + " x=" + mOffsetX + " y=" + mOffsetY
                + " wFrac=" + mWidthFraction + " h=" + mHeightPx);
    }

    // ── Internal geometry helpers ────────────────────────────────────────────

    /** Applies current geometry to the container's LayoutParams. */
    private void applyGeometry() {
        if (mContainer == null) return;

        DisplayMetrics dm = mService.getResources().getDisplayMetrics();
        int screenW = dm.widthPixels;
        int screenH = dm.heightPixels;

        if (!mFloatingMode) {
            // Normal (docked) mode — fill the IME window.
            ViewGroup.LayoutParams lp = mContainer.getLayoutParams();
            if (lp == null) {
                lp = new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
            }
            lp.width  = ViewGroup.LayoutParams.MATCH_PARENT;
            lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            if (lp instanceof FrameLayout.LayoutParams) {
                ((FrameLayout.LayoutParams) lp).gravity = Gravity.BOTTOM;
                ((FrameLayout.LayoutParams) lp).leftMargin   = 0;
                ((FrameLayout.LayoutParams) lp).bottomMargin = 0;
            }
            mContainer.setLayoutParams(lp);
            mContainer.invalidate();
            return;
        }

        // Clamp width fraction.
        mWidthFraction = clamp(mWidthFraction, MIN_WIDTH_FRACTION, 1.0f);
        int panelW = Math.round(screenW * mWidthFraction);

        // Default / clamp height.
        int minH = dpToPx(MIN_HEIGHT_DP);
        int maxH = Math.round(screenH * MAX_HEIGHT_FRACTION);
        if (mHeightPx <= 0) {
            mHeightPx = Math.round(screenH * 0.35f); // sensible default ~35 %
        }
        mHeightPx = clamp(mHeightPx, minH, maxH);

        // Center horizontally by default (offsetX == 0 means centered).
        int leftMargin = Math.round((screenW - panelW) / 2f + mOffsetX);
        // Clamp so panel stays on screen.
        leftMargin = clamp(leftMargin, 0, screenW - panelW);
        // bottomMargin: positive moves the panel up.
        int bottomMargin = Math.max(0, Math.round(mOffsetY));

        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) mContainer.getLayoutParams();
        if (lp == null) {
            lp = new FrameLayout.LayoutParams(panelW, mHeightPx);
        }
        lp.width        = panelW;
        lp.height       = mHeightPx;
        lp.gravity      = Gravity.BOTTOM | Gravity.START;
        lp.leftMargin   = leftMargin;
        lp.bottomMargin = bottomMargin;
        mContainer.setLayoutParams(lp);
        mContainer.invalidate();
    }

    /** Applies edge-snapping to mOffsetX based on current panel width and screen width. */
    private void snapToEdgeIfNeeded() {
        DisplayMetrics dm = mService.getResources().getDisplayMetrics();
        int screenW  = dm.widthPixels;
        int panelW   = Math.round(screenW * mWidthFraction);
        float snapPx = SNAP_THRESHOLD_DP * mDensity;

        // Compute actual left edge pixel position.
        float leftPx = (screenW - panelW) / 2f + mOffsetX;

        if (leftPx < snapPx) {
            // Snap to left edge.
            mOffsetX = -(screenW - panelW) / 2f;
        } else if (leftPx > screenW - panelW - snapPx) {
            // Snap to right edge.
            mOffsetX = (screenW - panelW) / 2f;
        }
    }

    // ── Utility ─────────────────────────────────────────────────────────────

    private int dpToPx(float dp) {
        return Math.round(dp * mDensity);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    // ════════════════════════════════════════════════════════════════════════
    // FloatingContainer — the decorated FrameLayout
    // ════════════════════════════════════════════════════════════════════════

    /**
     * A {@link FrameLayout} that draws the drag handle and corner resize handles and
     * dispatches touch events for dragging and resizing.
     */
    private final class FloatingContainer extends FrameLayout {

        // ── Paints ──────────────────────────────────────────────────────────
        private final Paint mHandleBgPaint   = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint mDotPaint        = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint mResizeHandlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint mBorderPaint     = new Paint(Paint.ANTI_ALIAS_FLAG);

        // ── Pre-computed dimensions ──────────────────────────────────────────
        private final int   mDragHandleH;   // px
        private final float mResizeHandleSz; // px
        private final float mDotRadius;     // px
        private final float mDotSpacing;    // px

        // ── Touch state ─────────────────────────────────────────────────────
        private int   mTouchMode   = TOUCH_NONE;
        private int   mActiveCorner = CORNER_BOTTOM_RIGHT;
        private float mTouchStartX;
        private float mTouchStartY;
        /** Snapshot of mOffsetX at the start of the gesture. */
        private float mGestureStartOffsetX;
        /** Snapshot of mOffsetY at the start of the gesture. */
        private float mGestureStartOffsetY;
        /** Snapshot of panel width (px) at the start of a resize gesture. */
        private int   mGestureStartW;
        /** Snapshot of panel height (px) at the start of a resize gesture. */
        private int   mGestureStartH;

        FloatingContainer(Context context) {
            super(context);
            setWillNotDraw(false);

            mDragHandleH    = dpToPx(DRAG_HANDLE_HEIGHT_DP);
            mResizeHandleSz = dpToPx(RESIZE_HANDLE_DP);
            mDotRadius      = DOT_RADIUS_DP * mDensity;
            mDotSpacing     = DOT_SPACING_DP * mDensity;

            mHandleBgPaint.setColor(Color.argb(220, 50, 50, 55));
            mHandleBgPaint.setStyle(Paint.Style.FILL);

            mDotPaint.setColor(Color.argb(200, 180, 180, 185));
            mDotPaint.setStyle(Paint.Style.FILL);

            mResizeHandlePaint.setColor(Color.argb(200, 100, 180, 255));
            mResizeHandlePaint.setStyle(Paint.Style.FILL);

            mBorderPaint.setColor(Color.argb(180, 80, 80, 90));
            mBorderPaint.setStyle(Paint.Style.STROKE);
            mBorderPaint.setStrokeWidth(1.5f * mDensity);
        }

        // ── Drawing ─────────────────────────────────────────────────────────

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (!mFloatingMode) return;

            int w = getWidth();
            int h = getHeight();

            // Panel border.
            float half = mBorderPaint.getStrokeWidth() / 2f;
            canvas.drawRect(half, half, w - half, h - half, mBorderPaint);

            // Drag handle background.
            canvas.drawRect(0, 0, w, mDragHandleH, mHandleBgPaint);

            // Dots in the drag handle (three rows of dots centered horizontally).
            drawDragDots(canvas, w);

            // Corner resize triangles.
            drawCornerHandle(canvas, CORNER_TOP_LEFT,     w, h);
            drawCornerHandle(canvas, CORNER_TOP_RIGHT,    w, h);
            drawCornerHandle(canvas, CORNER_BOTTOM_LEFT,  w, h);
            drawCornerHandle(canvas, CORNER_BOTTOM_RIGHT, w, h);
        }

        /** Draws a grid of dots centred in the drag handle area. */
        private void drawDragDots(Canvas canvas, int panelW) {
            int cols = 7;
            int rows = 2;
            float totalDotsW = (cols - 1) * mDotSpacing;
            float startX = (panelW - totalDotsW) / 2f;
            float startY = (mDragHandleH - (rows - 1) * mDotSpacing) / 2f;

            for (int r = 0; r < rows; r++) {
                for (int c = 0; c < cols; c++) {
                    float cx = startX + c * mDotSpacing;
                    float cy = startY + r * mDotSpacing;
                    canvas.drawCircle(cx, cy, mDotRadius, mDotPaint);
                }
            }
        }

        /** Draws a filled triangle at the given corner to indicate resize affordance. */
        private void drawCornerHandle(Canvas canvas, int corner, int w, int h) {
            float sz = mResizeHandleSz;
            android.graphics.Path path = new android.graphics.Path();
            switch (corner) {
                case CORNER_TOP_LEFT:
                    path.moveTo(0, 0);
                    path.lineTo(sz, 0);
                    path.lineTo(0, sz);
                    break;
                case CORNER_TOP_RIGHT:
                    path.moveTo(w, 0);
                    path.lineTo(w - sz, 0);
                    path.lineTo(w, sz);
                    break;
                case CORNER_BOTTOM_LEFT:
                    path.moveTo(0, h);
                    path.lineTo(sz, h);
                    path.lineTo(0, h - sz);
                    break;
                case CORNER_BOTTOM_RIGHT:
                    path.moveTo(w, h);
                    path.lineTo(w - sz, h);
                    path.lineTo(w, h - sz);
                    break;
            }
            path.close();
            canvas.drawPath(path, mResizeHandlePaint);
        }

        // ── Touch handling ───────────────────────────────────────────────────

        @Override
        public boolean onInterceptTouchEvent(MotionEvent ev) {
            if (!mFloatingMode) return false;

            float x = ev.getX();
            float y = ev.getY();

            if (ev.getActionMasked() == MotionEvent.ACTION_DOWN) {
                if (isInDragHandle(y)) {
                    return true;
                }
                int corner = hitCorner(x, y);
                if (corner >= 0) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public boolean onTouchEvent(MotionEvent ev) {
            if (!mFloatingMode) return false;

            float rawX = ev.getRawX();
            float rawY = ev.getRawY();
            float localX = ev.getX();
            float localY = ev.getY();

            switch (ev.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    mTouchStartX = rawX;
                    mTouchStartY = rawY;
                    mGestureStartOffsetX = mOffsetX;
                    mGestureStartOffsetY = mOffsetY;
                    mGestureStartW = getWidth();
                    mGestureStartH = getHeight();

                    if (isInDragHandle(localY)) {
                        mTouchMode = TOUCH_DRAG;
                    } else {
                        int corner = hitCorner(localX, localY);
                        if (corner >= 0) {
                            mTouchMode   = TOUCH_RESIZE;
                            mActiveCorner = corner;
                        } else {
                            mTouchMode = TOUCH_NONE;
                        }
                    }
                    return mTouchMode != TOUCH_NONE;

                case MotionEvent.ACTION_MOVE:
                    float dx = rawX - mTouchStartX;
                    float dy = rawY - mTouchStartY;

                    if (mTouchMode == TOUCH_DRAG) {
                        handleDrag(dx, dy);
                    } else if (mTouchMode == TOUCH_RESIZE) {
                        handleResize(dx, dy);
                    }
                    return true;

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (mTouchMode == TOUCH_DRAG) {
                        snapToEdgeIfNeeded();
                        applyGeometry();
                    }
                    mTouchMode = TOUCH_NONE;
                    return true;
            }
            return false;
        }

        /** Returns {@code true} if {@code localY} is within the drag handle strip. */
        private boolean isInDragHandle(float localY) {
            return localY >= 0 && localY < mDragHandleH;
        }

        /**
         * Returns the corner index that {@code (localX, localY)} falls within, or -1.
         */
        private int hitCorner(float localX, float localY) {
            int w = getWidth();
            int h = getHeight();
            float sz = mResizeHandleSz;

            // Top-left
            if (localX < sz && localY < sz) return CORNER_TOP_LEFT;
            // Top-right
            if (localX > w - sz && localY < sz) return CORNER_TOP_RIGHT;
            // Bottom-left
            if (localX < sz && localY > h - sz) return CORNER_BOTTOM_LEFT;
            // Bottom-right
            if (localX > w - sz && localY > h - sz) return CORNER_BOTTOM_RIGHT;
            return -1;
        }

        // ── Gesture handlers ─────────────────────────────────────────────────

        /**
         * Updates the panel position based on the accumulated drag delta.
         *
         * @param dx horizontal delta in pixels (positive = right).
         * @param dy vertical delta in pixels (positive = down on screen).
         */
        private void handleDrag(float dx, float dy) {
            // dy positive means finger moved down on screen → panel moves down → bottomMargin decreases.
            mOffsetX = mGestureStartOffsetX + dx;
            mOffsetY = mGestureStartOffsetY - dy; // invert: screen-down reduces bottomMargin
            applyGeometry();
        }

        /**
         * Updates the panel size based on corner drag delta.
         *
         * <p>Position is also updated as needed so that the opposite corner remains
         * stationary (i.e. dragging BOTTOM_RIGHT only changes size, not position;
         * dragging TOP_LEFT changes both size and position).
         *
         * @param dx horizontal drag delta in pixels.
         * @param dy vertical drag delta in pixels.
         */
        private void handleResize(float dx, float dy) {
            DisplayMetrics dm = mService.getResources().getDisplayMetrics();
            int screenW  = dm.widthPixels;
            int minWPx   = Math.round(screenW * MIN_WIDTH_FRACTION);
            int maxWPx   = screenW;
            int minHPx   = dpToPx(MIN_HEIGHT_DP);
            int maxHPx   = Math.round(dm.heightPixels * MAX_HEIGHT_FRACTION);

            int newW = mGestureStartW;
            int newH = mGestureStartH;
            float newOffX = mGestureStartOffsetX;
            float newOffY = mGestureStartOffsetY;

            switch (mActiveCorner) {
                case CORNER_BOTTOM_RIGHT:
                    newW = clamp(mGestureStartW + Math.round(dx), minWPx, maxWPx);
                    newH = clamp(mGestureStartH + Math.round(dy), minHPx, maxHPx);
                    break;

                case CORNER_BOTTOM_LEFT:
                    newW = clamp(mGestureStartW - Math.round(dx), minWPx, maxWPx);
                    newH = clamp(mGestureStartH + Math.round(dy), minHPx, maxHPx);
                    // Adjust x so the right edge stays fixed.
                    newOffX = mGestureStartOffsetX + (mGestureStartW - newW) / 2f;
                    break;

                case CORNER_TOP_RIGHT:
                    newW = clamp(mGestureStartW + Math.round(dx), minWPx, maxWPx);
                    newH = clamp(mGestureStartH - Math.round(dy), minHPx, maxHPx);
                    // Adjust y so the bottom edge stays fixed.
                    newOffY = mGestureStartOffsetY + (mGestureStartH - newH);
                    break;

                case CORNER_TOP_LEFT:
                    newW = clamp(mGestureStartW - Math.round(dx), minWPx, maxWPx);
                    newH = clamp(mGestureStartH - Math.round(dy), minHPx, maxHPx);
                    newOffX = mGestureStartOffsetX + (mGestureStartW - newW) / 2f;
                    newOffY = mGestureStartOffsetY + (mGestureStartH - newH);
                    break;
            }

            mWidthFraction = (float) newW / screenW;
            mHeightPx      = newH;
            mOffsetX       = newOffX;
            mOffsetY       = newOffY;
            applyGeometry();
        }
    }
}
