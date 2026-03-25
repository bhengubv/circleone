/*
 * Copyright (C) 2026 The Other Bhengu (Pty) Ltd t/a The Geek.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package helium314.keyboard.latin.circleone;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.text.style.ReplacementSpan;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * A ReplacementSpan that draws isiBheqe PUA codepoints using the bundled one.ttf font.
 *
 * When attached to composing text via setComposingText(), apps that respect spans
 * (WhatsApp, Telegram, Samsung Notes, etc.) will render the actual isiBheqe glyphs
 * in the text field while the user is typing.
 */
public class IsiBheqeSpan extends ReplacementSpan {

    private static Typeface sTypeface;
    private static final Object sLock = new Object();

    /**
     * Loads the one.ttf typeface from assets (cached as singleton).
     */
    public static Typeface getTypeface(@NonNull Context context) {
        if (sTypeface == null) {
            synchronized (sLock) {
                if (sTypeface == null) {
                    try {
                        sTypeface = Typeface.createFromAsset(context.getAssets(), "one.ttf");
                    } catch (Exception e) {
                        // Fallback: use default typeface
                        sTypeface = Typeface.DEFAULT;
                    }
                }
            }
        }
        return sTypeface;
    }

    private final Typeface mTypeface;

    public IsiBheqeSpan(@NonNull Context context) {
        mTypeface = getTypeface(context);
    }

    public IsiBheqeSpan(@NonNull Typeface typeface) {
        mTypeface = typeface;
    }

    @Override
    public int getSize(@NonNull Paint paint, CharSequence text, int start, int end,
                       @Nullable Paint.FontMetricsInt fm) {
        Paint measurePaint = new Paint(paint);
        measurePaint.setTypeface(mTypeface);
        if (fm != null) {
            measurePaint.getFontMetricsInt(fm);
        }
        return Math.round(measurePaint.measureText(text, start, end));
    }

    @Override
    public void draw(@NonNull Canvas canvas, CharSequence text, int start, int end,
                     float x, int top, int y, int bottom, @NonNull Paint paint) {
        Paint drawPaint = new Paint(paint);
        drawPaint.setTypeface(mTypeface);
        canvas.drawText(text, start, end, x, y, drawPaint);
    }
}
