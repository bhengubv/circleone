/*
 * SPDX-License-Identifier: GPL-3.0-only
 * Copyright (C) 2026 The Other Bhengu (Pty) Ltd t/a The Geek Network
 */

package org.ofrp.scriptview;

import android.content.Intent;
import android.provider.Settings;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

/**
 * Activity that guides users to enable the ScriptView accessibility service.
 * Checks if the service is already enabled and shows appropriate UI.
 */
public class EnableGuideActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Create root LinearLayout
        LinearLayout rootLayout = new LinearLayout(this);
        rootLayout.setOrientation(LinearLayout.VERTICAL);
        rootLayout.setPadding(64, 64, 64, 64);

        LinearLayout.LayoutParams wrapParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        wrapParams.setMargins(0, 16, 0, 16);

        // Title
        TextView titleView = new TextView(this);
        titleView.setText("Enable ScriptView");
        titleView.setTextSize(24);
        titleView.setTypeface(null, android.graphics.Typeface.BOLD);
        titleView.setGravity(android.view.Gravity.CENTER);
        rootLayout.addView(titleView, wrapParams);

        // Description
        TextView descView = new TextView(this);
        descView.setText("ScriptView provides visual overlays for scripts and character information. " +
                        "It requires accessibility service permission to function.");
        descView.setTextSize(16);
        descView.setGravity(android.view.Gravity.CENTER);
        LinearLayout.LayoutParams descParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        descParams.setMargins(0, 16, 0, 0);
        rootLayout.addView(descView, descParams);

        // Button
        Button settingsBtn = new Button(this);
        settingsBtn.setText("Open Accessibility Settings");
        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        btnParams.setMargins(0, 32, 0, 0);
        settingsBtn.setOnClickListener(v -> {
            Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            startActivity(intent);
        });
        rootLayout.addView(settingsBtn, btnParams);

        // Status view
        TextView statusView = new TextView(this);
        statusView.setTextSize(16);
        statusView.setGravity(android.view.Gravity.CENTER);
        if (isAccessibilityServiceEnabled()) {
            statusView.setText("✓ ScriptView is active!");
            statusView.setTextColor(android.graphics.Color.parseColor("#4CAF50"));
        } else {
            statusView.setText("ScriptView is not yet enabled");
            statusView.setTextColor(android.graphics.Color.parseColor("#666666"));
        }
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        statusParams.setMargins(0, 16, 0, 0);
        rootLayout.addView(statusView, statusParams);

        setContentView(rootLayout);
    }

    /**
     * Checks if ScriptViewService is enabled in accessibility settings.
     */
    private boolean isAccessibilityServiceEnabled() {
        try {
            android.content.ContentResolver resolver = getContentResolver();
            String enabled = android.provider.Settings.Secure.getString(
                resolver,
                android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            );

            if (enabled != null) {
                String serviceString = getPackageName() + "/" +
                    ScriptViewService.class.getName();
                return enabled.contains(serviceString);
            }
        } catch (Exception e) {
            // Ignore
        }
        return false;
    }
}
