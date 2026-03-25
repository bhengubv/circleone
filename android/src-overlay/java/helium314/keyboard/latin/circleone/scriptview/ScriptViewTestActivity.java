/*
 * SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) 2026 The Other Bhengu (Pty) Ltd t/a The Geek Network
 */

package helium314.keyboard.latin.circleone.scriptview;

import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.app.Activity;

/**
 * Test activity for validating ScriptView overlay positioning on PUA (Private Use Area) characters.
 * Contains various text views with PUA glyphs at different sizes and formats.
 */
public class ScriptViewTestActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Outer ScrollView for vertical scrolling
        ScrollView scrollView = new ScrollView(this);

        // Inner LinearLayout for test items
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(16, 16, 16, 16);

        // Title
        TextView titleView = new TextView(this);
        titleView.setText("ScriptView Test — PUA Overlay Validation");
        titleView.setTextSize(20);
        titleView.setTypeface(null, android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        titleParams.setMargins(0, 0, 0, 16);
        layout.addView(titleView, titleParams);

        // Test 1: Single PUA char
        TextView test1 = createTestView(
            "Single PUA char: " + (char)0xE001,
            16
        );
        layout.addView(test1, createTestParams());

        // Test 2: Multiple PUA chars
        TextView test2 = createTestView(
            "Multiple PUA: " + (char)0xE001 + (char)0xE042 + (char)0xE003,
            16
        );
        layout.addView(test2, createTestParams());

        // Test 3: Mixed text with PUA
        TextView test3 = createTestView(
            "Mixed text: Hello " + (char)0xE001 + " world " + (char)0xE042,
            16
        );
        layout.addView(test3, createTestParams());

        // Test 4: Large text with PUA
        TextView test4 = createTestView(
            "Big: " + (char)0xE010,
            24
        );
        layout.addView(test4, createTestParams());

        // Test 5: Small text with PUA
        TextView test5 = createTestView(
            "Small: " + (char)0xE010,
            12
        );
        layout.addView(test5, createTestParams());

        scrollView.addView(layout, new ScrollView.LayoutParams(
            ScrollView.LayoutParams.MATCH_PARENT,
            ScrollView.LayoutParams.WRAP_CONTENT
        ));

        setContentView(scrollView);
    }

    /**
     * Creates a test TextView with the given text and size.
     */
    private TextView createTestView(String text, int textSizeSp) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(textSizeSp);
        view.setPadding(16, 16, 16, 16);
        return view;
    }

    /**
     * Creates layout params for test views (wrap content, 8dp margin).
     */
    private LinearLayout.LayoutParams createTestParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(8, 8, 8, 8);
        return params;
    }
}
