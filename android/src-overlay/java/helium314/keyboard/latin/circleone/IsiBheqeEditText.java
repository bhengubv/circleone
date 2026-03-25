/*
 * Copyright (C) 2026 The Other Bhengu (Pty) Ltd t/a The Geek.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package helium314.keyboard.latin.circleone;

import android.content.Context;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.widget.EditText;

/**
 * EditText subclass that uses one.ttf typeface for rendering isiBheqe PUA glyphs.
 * Used in the CircleOne companion compose activity.
 */
public class IsiBheqeEditText extends EditText {

    public IsiBheqeEditText(Context context) {
        super(context);
        init(context);
    }

    public IsiBheqeEditText(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public IsiBheqeEditText(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        Typeface tf = IsiBheqeSpan.getTypeface(context);
        setTypeface(tf);
    }
}
