/*
 * ScriptView - Color Estimator
 * License: GPL-3.0-only
 * Copyright (C) 2026 The Other Bhengu (Pty) Ltd t/a The Geek
 */

package helium314.keyboard.latin.circleone.scriptview;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;

/**
 * Estimates text color based on device UI mode (day/night).
 * Simple V1 implementation that checks system dark mode setting.
 */
public final class ColorEstimator {
    /**
     * Dark gray color used in day mode.
     */
    private static final int DAY_MODE_COLOR = 0xFF333333;

    /**
     * Estimate the appropriate text color based on the system UI mode.
     *
     * @param context the Android context
     * @return Color.WHITE for night mode, 0xFF333333 for day mode
     */
    public static int estimateTextColor(Context context) {
        if (context == null) {
            // Default to day mode if context is null
            return DAY_MODE_COLOR;
        }

        Configuration config = context.getResources().getConfiguration();
        int uiMode = config.uiMode & Configuration.UI_MODE_NIGHT_MASK;

        if (uiMode == Configuration.UI_MODE_NIGHT_YES) {
            // Night mode: use white text for visibility on dark backgrounds
            return Color.WHITE;
        } else {
            // Day mode: use dark gray for visibility on light backgrounds
            return DAY_MODE_COLOR;
        }
    }

    // Private constructor to prevent instantiation
    private ColorEstimator() {
    }
}
