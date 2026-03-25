/*
 * Copyright (C) 2026 The Other Bhengu (Pty) Ltd t/a The Geek.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package helium314.keyboard.latin.circleone;

import android.inputmethodservice.InputMethodService;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.inputmethod.InputConnection;

/**
 * Handles spacebar drag gestures to move the text cursor left/right.
 *
 * When the user presses the spacebar and drags horizontally, the cursor
 * moves in the drag direction. This is the most-loved Gboard power feature.
 *
 * Usage: attach to the spacebar key view's touch listener in MainKeyboardView.
 * Call {@link #onTouchEvent(MotionEvent)} from the keyboard view's touch handler
 * when the spacebar is being touched.
 */
public class SpacebarTrackpadListener {

    /** Minimum horizontal drag distance (in pixels) before cursor starts moving */
    private static final float DRAG_THRESHOLD_PX = 30f;

    /** Pixels of drag per one cursor position move */
    private static final float PIXELS_PER_STEP = 24f;

    /** Minimum vertical distance to cancel trackpad (user is swiping away) */
    private static final float VERTICAL_CANCEL_PX = 100f;

    private final InputMethodService mService;

    private boolean mIsTracking;
    private float mStartX;
    private float mStartY;
    private float mLastStepX;
    private int mCumulativeSteps;
    private boolean mThresholdMet;

    public SpacebarTrackpadListener(InputMethodService service) {
        mService = service;
    }

    /**
     * Process a touch event on the spacebar.
     *
     * @param event The motion event
     * @return true if the event was consumed (trackpad is active), false to let
     *         the spacebar handle it normally (tap for space, swipe for language switch)
     */
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                mStartX = event.getRawX();
                mStartY = event.getRawY();
                mLastStepX = mStartX;
                mIsTracking = true;
                mThresholdMet = false;
                mCumulativeSteps = 0;
                return false; // Don't consume DOWN — spacebar needs it for press visual

            case MotionEvent.ACTION_MOVE:
                if (!mIsTracking) return false;

                float deltaY = Math.abs(event.getRawY() - mStartY);
                if (deltaY > VERTICAL_CANCEL_PX) {
                    // User is swiping vertically (probably language switch)
                    mIsTracking = false;
                    return false;
                }

                float deltaX = event.getRawX() - mStartX;

                if (!mThresholdMet) {
                    if (Math.abs(deltaX) >= DRAG_THRESHOLD_PX) {
                        mThresholdMet = true;
                        mLastStepX = event.getRawX();
                    }
                    return false;
                }

                // Calculate steps since last cursor move
                float stepDelta = event.getRawX() - mLastStepX;
                int steps = (int) (stepDelta / PIXELS_PER_STEP);

                if (steps != 0) {
                    moveCursor(steps);
                    mLastStepX += steps * PIXELS_PER_STEP;
                    mCumulativeSteps += Math.abs(steps);
                }
                return true; // Consume move events once tracking

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                boolean wasTracking = mThresholdMet && mCumulativeSteps > 0;
                mIsTracking = false;
                mThresholdMet = false;
                return wasTracking; // Consume UP only if we actually moved the cursor

            default:
                return false;
        }
    }

    /**
     * Moves the cursor left or right by the given number of positions.
     * Positive = right, negative = left.
     */
    private void moveCursor(int positions) {
        InputConnection ic = mService.getCurrentInputConnection();
        if (ic == null) return;

        if (positions > 0) {
            for (int i = 0; i < positions; i++) {
                ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT));
                ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_RIGHT));
            }
        } else {
            for (int i = 0; i < -positions; i++) {
                ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_LEFT));
                ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_LEFT));
            }
        }
    }

    /**
     * Whether the trackpad is currently active (drag threshold met).
     * When true, the keyboard view should suppress spacebar repeat/popup.
     */
    public boolean isTrackpadActive() {
        return mIsTracking && mThresholdMet;
    }

    /**
     * Reset the trackpad state. Call when keyboard is hidden or input changes.
     */
    public void reset() {
        mIsTracking = false;
        mThresholdMet = false;
        mCumulativeSteps = 0;
    }
}
