/*
 * Copyright (C) 2026 The Other Bhengu (Pty) Ltd t/a The Geek.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package helium314.keyboard.latin.circleone;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;

import helium314.keyboard.latin.R;

/**
 * Companion text editor for composing isiBheqe text with full glyph rendering.
 *
 * Users type in the custom EditText (which uses one.ttf), see their text rendered
 * correctly, and share it as a PNG image via WhatsApp/SMS/email.
 */
public class CircleOneComposeActivity extends Activity {

    private IsiBheqeEditText mEditText;
    private ImageView mPreviewImage;
    private GlyphRenderer mRenderer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_compose);

        if (getActionBar() != null) {
            getActionBar().setTitle("CircleOne");
            getActionBar().setSubtitle("isiBheqe soHlamvu");
            getActionBar().setDisplayHomeAsUpEnabled(true);
        }

        mEditText = findViewById(R.id.compose_edit_text);
        mPreviewImage = findViewById(R.id.compose_preview);
        mRenderer = new GlyphRenderer(this);

        // Handle text passed from keyboard via intent
        String initialText = getIntent().getStringExtra(Intent.EXTRA_TEXT);
        if (initialText != null && !initialText.isEmpty()) {
            mEditText.setText(initialText);
            mEditText.setSelection(initialText.length());
            updatePreview();
        }

        // Preview button
        View previewBtn = findViewById(R.id.btn_preview);
        if (previewBtn != null) {
            previewBtn.setOnClickListener(v -> updatePreview());
        }

        // Share button
        View shareBtn = findViewById(R.id.btn_share);
        if (shareBtn != null) {
            shareBtn.setOnClickListener(v -> shareAsImage());
        }

        // Clear button
        View clearBtn = findViewById(R.id.btn_clear);
        if (clearBtn != null) {
            clearBtn.setOnClickListener(v -> {
                mEditText.setText("");
                mPreviewImage.setVisibility(View.GONE);
            });
        }
    }

    @Override
    public boolean onNavigateUp() {
        finish();
        return true;
    }

    private void updatePreview() {
        Editable text = mEditText.getText();
        if (text == null || text.length() == 0) {
            mPreviewImage.setVisibility(View.GONE);
            return;
        }

        Bitmap bitmap = mRenderer.render(text.toString());
        mPreviewImage.setImageBitmap(bitmap);
        mPreviewImage.setVisibility(View.VISIBLE);
    }

    private void shareAsImage() {
        Editable text = mEditText.getText();
        if (text == null || text.length() == 0) {
            Toast.makeText(this, "Type some text first", Toast.LENGTH_SHORT).show();
            return;
        }

        Intent shareIntent = ShareHelper.renderAndShare(this, mRenderer, text.toString());
        if (shareIntent != null) {
            startActivity(shareIntent);
        } else {
            Toast.makeText(this, "Failed to create image", Toast.LENGTH_SHORT).show();
        }
    }
}
