/*
 * ScriptViewService.java
 *
 * Copyright (C) 2026 The Other Bhengu (Pty) Ltd t/a The Geek. GPL-3.0-only
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package org.ofrp.scriptview;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.Collections;
import java.util.List;

/**
 * Core {@link AccessibilityService} for ScriptView.
 *
 * <p>ScriptViewService attaches a transparent {@link ScriptOverlayView} to the system window
 * layer and listens for accessibility events. As the user navigates apps that render Private
 * Use Area (PUA) glyphs via a custom font, the service scans the active window's node tree,
 * extracts glyph positions and metadata, and triggers the overlay to redraw replacement
 * characters at the correct screen positions.
 *
 * <h2>Lifecycle</h2>
 * <ol>
 *   <li>{@link #onServiceConnected()} — adds the full-screen overlay window.</li>
 *   <li>{@link #onAccessibilityEvent(AccessibilityEvent)} — receives events, debounces them
 *       at 16 ms, and routes to {@link #processEvent()}.</li>
 *   <li>{@link #onInterrupt()} — clears the overlay glyph map.</li>
 *   <li>{@link #onDestroy()} — removes the overlay window.</li>
 * </ol>
 *
 * <h2>Dormant mode</h2>
 * <p>If no PUA codepoints are detected for {@value #DORMANT_TIMEOUT_MS} ms the service enters
 * <em>dormant mode</em>: it narrows event monitoring to
 * {@link AccessibilityEvent#TYPE_WINDOW_STATE_CHANGED} only, reducing CPU overhead.
 * Full monitoring resumes the moment a PUA glyph is found again.
 *
 * <h2>Thread safety</h2>
 * <p>All methods are called on the main thread by the Android accessibility framework.
 * No locking is required.
 */
public final class ScriptViewService extends AccessibilityService {

    private static final String TAG = "ScriptViewService";

    /** Debounce window in milliseconds — one frame at 60 Hz. */
    private static final long DEBOUNCE_MS = 16L;

    /**
     * Time without any PUA detection before the service enters dormant mode.
     * 30 000 ms = 30 seconds.
     */
    private static final long DORMANT_TIMEOUT_MS = 30_000L;

    // -----------------------------------------------------------------------------------------
    // Fields
    // -----------------------------------------------------------------------------------------

    /** Overlay view rendered above all other windows. */
    private ScriptOverlayView mOverlayView;

    /** WindowManager used to add / remove the overlay. */
    private WindowManager mWindowManager;

    /** Shared glyph map owned by this service, passed to the overlay view. */
    private GlyphMap mGlyphMap;

    /** Provides the one.ttf Typeface from assets. */
    

    /** Estimates foreground text colour for a node. */
    

    /** Main-thread handler for debouncing and dormant-mode scheduling. */
    private final Handler mHandler = new Handler(Looper.getMainLooper());

    /** Debounce runnable — posted / cancelled on every qualifying event. */
    private final Runnable mProcessRunnable = this::processEvent;

    /** Dormant-mode runnable — schedules entry into dormant mode. */
    private final Runnable mDormantRunnable = this::enterDormantMode;

    /** {@code true} while dormant; only TYPE_WINDOW_STATE_CHANGED is monitored. */
    private boolean mDormant = false;

    /** Whether the overlay window has been successfully added. */
    private boolean mOverlayAttached = false;

    // -----------------------------------------------------------------------------------------
    // AccessibilityService callbacks
    // -----------------------------------------------------------------------------------------

    /**
     * Called by the system after the service is connected and the window token is available.
     *
     * <p>Creates a single {@code TYPE_ACCESSIBILITY_OVERLAY} window containing
     * {@link ScriptOverlayView}. The window is full-screen, non-focusable, non-touchable, and
     * translucent so it does not interfere with user interaction.
     */
    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        Log.i(TAG, "onServiceConnected — attaching overlay");

        mGlyphMap       = new GlyphMap();
        // FontProvider and ColorEstimator are static utility classes
        

        mOverlayView = new ScriptOverlayView(this);
        mOverlayView.setGlyphMap(mGlyphMap);

        mWindowManager = (WindowManager) getSystemService(WINDOW_SERVICE);

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
        );

        try {
            mWindowManager.addView(mOverlayView, params);
            mOverlayAttached = true;
            Log.i(TAG, "Overlay window added successfully");
        } catch (Exception e) {
            Log.i(TAG, "Failed to add overlay window: " + e.getMessage());
        }
    }

    /**
     * Receives raw accessibility events from the system.
     *
     * <p>Only the following event types are processed:
     * <ul>
     *   <li>{@link AccessibilityEvent#TYPE_WINDOW_CONTENT_CHANGED}</li>
     *   <li>{@link AccessibilityEvent#TYPE_VIEW_TEXT_CHANGED}</li>
     *   <li>{@link AccessibilityEvent#TYPE_VIEW_SCROLLED}</li>
     *   <li>{@link AccessibilityEvent#TYPE_WINDOW_STATE_CHANGED}</li>
     * </ul>
     *
     * <p>Events are coalesced within a {@value #DEBOUNCE_MS} ms window using
     * {@link Handler#postDelayed}. While in dormant mode only
     * {@link AccessibilityEvent#TYPE_WINDOW_STATE_CHANGED} passes the filter.
     *
     * @param event the accessibility event; never {@code null} at this call site
     */
    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) return;

        final int type = event.getEventType();

        if (mDormant) {
            // In dormant mode only window-state changes can wake us up.
            if (type != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;
        } else {
            // Full monitoring: filter to relevant event types only.
            if (type != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
                    && type != AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED
                    && type != AccessibilityEvent.TYPE_VIEW_SCROLLED
                    && type != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                return;
            }
        }

        // Debounce: cancel any pending process call and re-schedule.
        mHandler.removeCallbacks(mProcessRunnable);
        mHandler.postDelayed(mProcessRunnable, DEBOUNCE_MS);
    }

    /**
     * Called when the system wants to interrupt the feedback provided by this service.
     *
     * <p>Clears the glyph map and forces a redraw so stale overlays disappear immediately.
     */
    @Override
    public void onInterrupt() {
        Log.i(TAG, "onInterrupt — clearing overlay");
        if (mGlyphMap != null) {
            mGlyphMap.clear();
        }
        if (mOverlayView != null) {
            mOverlayView.invalidate();
        }
    }

    /**
     * Called when the service is about to be destroyed.
     *
     * <p>Cancels pending callbacks and removes the overlay window from the WindowManager.
     */
    @Override
    public void onDestroy() {
        Log.i(TAG, "onDestroy — removing overlay");
        mHandler.removeCallbacks(mProcessRunnable);
        mHandler.removeCallbacks(mDormantRunnable);
        removeOverlay();
        super.onDestroy();
    }

    // -----------------------------------------------------------------------------------------
    // Internal helpers
    // -----------------------------------------------------------------------------------------

    /**
     * Processes the current window state after the debounce period has elapsed.
     *
     * <p>Fetches the root {@link AccessibilityNodeInfo} for the active window, passes it to
     * {@link NodeScanner#scanTree} to obtain a list of {@link GlyphEntry} objects, updates
     * the {@link GlyphMap}, and requests an overlay redraw.
     *
     * <p>If no PUA glyphs are found, the dormant-mode countdown is (re-)scheduled.
     * If PUA glyphs are found, any pending dormant countdown is cancelled and full monitoring
     * is restored.
     */
    private void processEvent() {
        AccessibilityNodeInfo root = getRootInActiveWindow();

        if (root == null) {
            // Some apps do not expose an accessibility tree (e.g. certain games, secure input
            // fields). Treat this the same as "no PUA found" so dormant mode can kick in.
            Log.i(TAG, "processEvent — root node is null, skipping scan");
            scheduleDormantCountdown();
            return;
        }

        List<GlyphEntry> entries;
        try {
            entries = NodeScanner.scanTree(root, ColorEstimator.estimateTextColor(this));
        } catch (Exception e) {
            Log.i(TAG, "NodeScanner.scanTree threw an exception: " + e.getMessage());
            entries = Collections.emptyList();
        } finally {
            // Always recycle the root node to avoid leaking the IPC object.
            root.recycle();
        }

        mGlyphMap.update(entries);
        mOverlayView.invalidate();

        if (entries.isEmpty()) {
            scheduleDormantCountdown();
        } else {
            // PUA content found — cancel dormant countdown and ensure full monitoring.
            mHandler.removeCallbacks(mDormantRunnable);
            if (mDormant) {
                exitDormantMode();
            }
        }
    }

    /**
     * Schedules entry into dormant mode after {@value #DORMANT_TIMEOUT_MS} ms of no PUA
     * activity. Cancels any previously scheduled countdown before posting the new one.
     */
    private void scheduleDormantCountdown() {
        mHandler.removeCallbacks(mDormantRunnable);
        mHandler.postDelayed(mDormantRunnable, DORMANT_TIMEOUT_MS);
    }

    /**
     * Switches the service to dormant mode.
     *
     * <p>In dormant mode the event mask is narrowed to
     * {@link AccessibilityEvent#TYPE_WINDOW_STATE_CHANGED} only via
     * {@link AccessibilityServiceInfo}, significantly reducing CPU usage when the user is
     * not viewing any PUA-rendered content.
     */
    private void enterDormantMode() {
        if (mDormant) return;
        mDormant = true;
        Log.i(TAG, "Entering dormant mode — no PUA detected for " + DORMANT_TIMEOUT_MS + " ms");

        AccessibilityServiceInfo info = getServiceInfo();
        if (info != null) {
            info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED;
            setServiceInfo(info);
        }

        // Clear stale overlays.
        mGlyphMap.clear();
        mOverlayView.invalidate();
    }

    /**
     * Restores full event monitoring after PUA content is detected again.
     *
     * <p>Resets the event mask to all four monitored event types.
     */
    private void exitDormantMode() {
        if (!mDormant) return;
        mDormant = false;
        Log.i(TAG, "Exiting dormant mode — PUA content detected");

        AccessibilityServiceInfo info = getServiceInfo();
        if (info != null) {
            info.eventTypes =
                    AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
                    | AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED
                    | AccessibilityEvent.TYPE_VIEW_SCROLLED
                    | AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED;
            setServiceInfo(info);
        }
    }

    /**
     * Removes the overlay view from the WindowManager if it was previously attached.
     */
    private void removeOverlay() {
        if (mOverlayAttached && mWindowManager != null && mOverlayView != null) {
            try {
                mWindowManager.removeView(mOverlayView);
                Log.i(TAG, "Overlay window removed");
            } catch (Exception e) {
                Log.i(TAG, "Failed to remove overlay window: " + e.getMessage());
            } finally {
                mOverlayAttached = false;
            }
        }
    }
}
