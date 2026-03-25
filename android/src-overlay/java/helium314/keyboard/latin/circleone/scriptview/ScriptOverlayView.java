/*
 * ScriptOverlayView.java
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
package helium314.keyboard.latin.circleone.scriptview;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.View;

import java.util.List;

/**
 * Transparent, full-screen overlay {@link View} that draws isiBheqe glyphs at the exact screen
 * positions reported by {@link GlyphMap}.
 *
 * <h2>Purpose</h2>
 * <p>Third-party apps that render PUA (Private Use Area) codepoints via a custom font produce
 * "tofu" (empty rectangles) on devices where that font is not installed. ScriptOverlayView sits
 * in a {@code TYPE_ACCESSIBILITY_OVERLAY} window above those apps and paints the correct
 * isiBheqe character — sourced from {@code one.ttf} — at each tofu location, creating the
 * illusion that the third-party app itself rendered the glyph correctly.
 *
 * <h2>Transparency</h2>
 * <p>The view has no background. Only the small per-glyph backing rectangles (see
 * {@link #BACKGROUND_ALPHA}) and the glyph characters themselves are painted, so every other
 * pixel remains fully transparent and passes interaction events through to the app below.
 *
 * <h2>Thread safety</h2>
 * <p>{@link #onDraw(Canvas)} runs on the main thread. {@link #setGlyphMap(GlyphMap)} may be
 * called from any thread; it reads atomically via a volatile reference and always calls
 * {@link #postInvalidate()} so the view is redrawn on the correct thread.
 */
public final class ScriptOverlayView extends View {

    // -----------------------------------------------------------------------------------------
    // Constants
    // -----------------------------------------------------------------------------------------

    /**
     * Alpha value (0–255) for the backing rectangle painted behind each glyph.
     * 200/255 ≈ 78% opaque — enough to reliably cover a tofu box without being distracting.
     */
    private static final int BACKGROUND_ALPHA = 200;

    /**
     * Padding added around each glyph's backing rectangle in pixels.
     * A small amount of padding ensures the backing rect fully covers the underlying tofu.
     */
    private static final float BACKGROUND_PADDING_PX = 2f;

    // -----------------------------------------------------------------------------------------
    // Fields
    // -----------------------------------------------------------------------------------------

    /**
     * Source of truth for all glyphs to be rendered in the current frame.
     * Volatile so that a {@link #setGlyphMap(GlyphMap)} call from a background thread is
     * immediately visible to the next {@link #onDraw(Canvas)} call on the main thread.
     */
    private volatile GlyphMap mGlyphMap;

    /**
     * Reusable {@link Paint} instance for glyph text rendering.
     * Configured per-entry inside {@link #onDraw(Canvas)} (color, textSize, typeface).
     */
    private final Paint mGlyphPaint;

    /**
     * Reusable {@link Paint} instance for the per-glyph backing rectangle.
     * Color is resolved once per draw call based on the current UI mode.
     */
    private final Paint mBackgroundPaint;

    /**
     * The one.ttf Typeface loaded from assets via {@link FontProvider}.
     * Cached at construction time; never {@code null} after the constructor completes.
     */
    private final Typeface mTypeface;

    // -----------------------------------------------------------------------------------------
    // Constructor
    // -----------------------------------------------------------------------------------------

    /**
     * Creates a new ScriptOverlayView.
     *
     * <p>Loads the {@code one.ttf} Typeface from assets via {@link FontProvider} and configures
     * the reusable {@link Paint} objects. The view is explicitly made drawable by calling
     * {@link #setWillNotDraw(false)}, which is required because {@link View} subclasses that
     * are not {@link android.view.ViewGroup}s default to {@code willNotDraw = false} already,
     * but setting it explicitly guards against any future framework changes or subclassing.
     *
     * @param context the Android context; used to load the Typeface from assets
     */
    public ScriptOverlayView(Context context) {
        super(context);

        // Explicitly enable drawing — this view has no background by design.
        setWillNotDraw(false);

        // Load (or retrieve from cache) the isiBheqe Typeface.
        mTypeface = FontProvider.getTypeface(context);

        // Glyph paint: anti-aliased text using the isiBheqe font.
        mGlyphPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mGlyphPaint.setTypeface(mTypeface);
        mGlyphPaint.setStyle(Paint.Style.FILL);

        // Background paint: filled rectangle, no anti-aliasing needed for rectangles.
        mBackgroundPaint = new Paint();
        mBackgroundPaint.setStyle(Paint.Style.FILL);
    }

    // -----------------------------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------------------------

    /**
     * Sets the {@link GlyphMap} that this view will use as its data source.
     *
     * <p>This method is safe to call from any thread. It schedules a redraw via
     * {@link #postInvalidate()} so that the next frame reflects the new map immediately.
     *
     * @param map the glyph map; passing {@code null} causes {@link #onDraw} to draw nothing
     */
    public void setGlyphMap(GlyphMap map) {
        mGlyphMap = map;
        postInvalidate();
    }

    // -----------------------------------------------------------------------------------------
    // Drawing
    // -----------------------------------------------------------------------------------------

    /**
     * Paints all visible isiBheqe glyphs onto the canvas.
     *
     * <p>For each {@link GlyphEntry} returned by {@link GlyphMap#getAllEntries()}:
     * <ol>
     *   <li>A semi-transparent backing rectangle is drawn over the tofu box to hide it.</li>
     *   <li>The PUA character is drawn at the baseline position that matches the original
     *       text height, using the {@code one.ttf} Typeface and the entry's foreground color.</li>
     * </ol>
     *
     * <p>Text size is set to {@link GlyphEntry#screenBounds} height so the rendered glyph
     * exactly fills the same vertical space as the original character. The baseline is computed
     * as {@code screenBounds.bottom - descent} to avoid clipping descenders.
     *
     * @param canvas the canvas on which to draw; never {@code null}
     */
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        GlyphMap map = mGlyphMap;
        if (map == null) return;

        // Snapshot the entries — getAllEntries() returns an unmodifiable defensive copy.
        List<GlyphEntry> entries = map.getAllEntries();
        if (entries.isEmpty()) return;

        // Resolve backing-rectangle color once per frame based on current UI mode.
        int bgComponent = resolveBackgroundComponent();

        for (GlyphEntry entry : entries) {
            RectF bounds = entry.screenBounds;
            if (bounds == null || bounds.isEmpty()) continue;

            // 1. Draw the backing rectangle to cover the underlying tofu box.
            mBackgroundPaint.setColor(Color.argb(BACKGROUND_ALPHA, bgComponent, bgComponent, bgComponent));
            canvas.drawRect(
                    bounds.left  - BACKGROUND_PADDING_PX,
                    bounds.top   - BACKGROUND_PADDING_PX,
                    bounds.right + BACKGROUND_PADDING_PX,
                    bounds.bottom + BACKGROUND_PADDING_PX,
                    mBackgroundPaint
            );

            // 2. Configure the glyph paint for this entry.
            mGlyphPaint.setColor(entry.textColor);

            // Use the bounds height as the text size so the glyph fills the same vertical
            // space as the original character. Clamp to at least 1 px to avoid zero-size text.
            float textSize = Math.max(1f, bounds.height());
            mGlyphPaint.setTextSize(textSize);

            // Typeface is constant (one.ttf) but set explicitly in case Paint recycles state.
            mGlyphPaint.setTypeface(mTypeface);

            // 3. Compute the baseline.
            // Paint.FontMetrics.descent is positive (pixels below baseline).
            // baseline = bounds.bottom - descent so descenders are not clipped.
            Paint.FontMetrics fm = mGlyphPaint.getFontMetrics();
            float baseline = bounds.bottom - fm.descent;

            // 4. Draw the PUA character.
            canvas.drawText(
                    String.valueOf(entry.puaCodepoint),
                    bounds.left,
                    baseline,
                    mGlyphPaint
            );
        }
    }

    // -----------------------------------------------------------------------------------------
    // Internal helpers
    // -----------------------------------------------------------------------------------------

    /**
     * Resolves the RGB component value (0–255) used for the per-glyph backing rectangle.
     *
     * <p>In night (dark) mode the backing rectangle is black ({@code 0}), which hides the
     * white tofu box against a dark app background. In day (light) mode it is white
     * ({@code 255}), which hides the dark tofu against a light background.
     *
     * @return {@code 0} for night mode, {@code 255} for day mode
     */
    private int resolveBackgroundComponent() {
        Configuration config = getContext().getResources().getConfiguration();
        int uiMode = config.uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return (uiMode == Configuration.UI_MODE_NIGHT_YES) ? 0 : 255;
    }
}
