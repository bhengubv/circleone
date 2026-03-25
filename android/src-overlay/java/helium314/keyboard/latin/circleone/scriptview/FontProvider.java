/*
 * Copyright (C) 2026 The Other Bhengu (Pty) Ltd t/a The Geek.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package helium314.keyboard.latin.circleone.scriptview;

import android.content.Context;
import android.graphics.Typeface;
import helium314.keyboard.latin.circleone.IsiBheqeSpan;

/** Delegates to IsiBheqeSpan's singleton typeface loader. */
public final class FontProvider {
    private FontProvider() {}
    public static Typeface getTypeface(Context context) {
        return IsiBheqeSpan.getTypeface(context);
    }
}
