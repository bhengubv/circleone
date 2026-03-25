package org.ofrp.scriptview;

import android.content.Context;
import android.graphics.Typeface;

// License: GPL-3.0-only, Copyright (C) 2026 The Other Bhengu (Pty) Ltd t/a The Geek.

/**
 * Provides thread-safe access to the one.ttf font loaded from assets.
 * The Typeface is cached as a singleton to avoid repeated disk I/O.
 */
public final class FontProvider {
    private static Typeface sTypeface;
    private static final Object sLock = new Object();

    private FontProvider() {
        // Private constructor to prevent instantiation
    }

    /**
     * Gets the one.ttf Typeface, loading it from assets if not already cached.
     *
     * @param context The Android Context used to access assets
     * @return The cached Typeface, or Typeface.DEFAULT if the asset is not found
     */
    public static Typeface getTypeface(Context context) {
        if (sTypeface != null) {
            return sTypeface;
        }

        synchronized (sLock) {
            // Double-checked locking: verify again after acquiring lock
            if (sTypeface != null) {
                return sTypeface;
            }

            try {
                sTypeface = Typeface.createFromAsset(context.getAssets(), "one.ttf");
            } catch (Exception e) {
                // Asset not found or I/O error; fall back to default
                sTypeface = Typeface.DEFAULT;
            }

            return sTypeface;
        }
    }
}
