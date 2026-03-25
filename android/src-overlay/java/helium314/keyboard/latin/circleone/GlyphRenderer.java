/*
 * Copyright (C) 2026 The Other Bhengu (Pty) Ltd t/a The Geek.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package helium314.keyboard.latin.circleone;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.Build;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * Renders isiBheqe PUA text to a {@link Bitmap} using one.ttf.
 *
 * <p>Two rendering modes are provided:
 * <ul>
 *   <li>{@link #render(String, int)} -- full-size, white background, black text. Intended for the
 *       companion compose / editor activity where the user shares the result as an image.</li>
 *   <li>{@link #renderInline(String)} -- compact, <em>transparent</em> background with dark text
 *       and a white shadow so the glyph is legible on any host-app background (light or dark).
 *       Intended for {@code commitContent} (keyboard image sticker mode).</li>
 * </ul>
 *
 * <p>Helper methods {@link #renderToBytes(String, String)} and
 * {@link #renderToFile(String, String, File)} compress the inline render to WebP or PNG for
 * delivery via {@code InputContentInfo}.
 */
public class GlyphRenderer {

    // -------------------------------------------------------------------------
    // Constants -- full-size render (compose activity)
    // -------------------------------------------------------------------------

    /** Default text size in SP for the full-size {@link #render} path. */
    private static final int DEFAULT_TEXT_SIZE_SP = 32;

    /** Padding (px) around text in the full-size render. */
    private static final int PADDING_PX = 40;

    /** Minimum bitmap width (px) for the full-size render. */
    private static final int MIN_WIDTH = 200;

    /** Maximum bitmap width (px) for the full-size render. */
    private static final int MAX_WIDTH = 1080;

    // -------------------------------------------------------------------------
    // Constants -- inline render (commitContent / sticker)
    // -------------------------------------------------------------------------

    /** Text size in SP for the inline (sticker) render. */
    private static final int INLINE_TEXT_SIZE_SP = 32;

    /** Padding (px) around text in the inline render. */
    private static final int INLINE_PADDING_PX = 12;

    /**
     * Maximum bitmap width (dp) for the inline render before text wraps to a new line.
     * Keeps stickers from being unreasonably wide.
     */
    private static final int MAX_INLINE_WIDTH_DP = 600;

    /**
     * Maximum bitmap height (dp) for the inline render. Text that would exceed this height
     * is ellipsised.
     */
    private static final int MAX_INLINE_HEIGHT_DP = 256;

    /** Dark-grey text colour used in the inline render -- readable on white and mid-tone bgs. */
    private static final int INLINE_TEXT_COLOR = 0xFF333333;

    /** Shadow radius (px) for the inline text -- provides contrast on dark backgrounds. */
    private static final float INLINE_SHADOW_RADIUS = 2f;

    // -------------------------------------------------------------------------
    // Fields
    // -------------------------------------------------------------------------

    private final Typeface mTypeface;
    private final float mDensity;

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    /**
     * Creates a new {@code GlyphRenderer}.
     *
     * @param context Any {@link Context}; used to load the typeface and screen density.
     */
    public GlyphRenderer(@NonNull Context context) {
        mTypeface = IsiBheqeSpan.getTypeface(context);
        mDensity = context.getResources().getDisplayMetrics().density;
    }

    // =========================================================================
    // Public API -- full-size render (compose / share-as-image)
    // =========================================================================

    /**
     * Renders the given PUA text to a {@link Bitmap} with a <strong>white background</strong>
     * and black text. Intended for the compose/editor activity where the output is shared as
     * a standalone image file.
     *
     * <p>Long text wraps automatically up to {@link #MAX_WIDTH} pixels wide.
     *
     * @param text       The isiBheqe PUA text to render. Must not be {@code null}.
     * @param textSizeSp Text size in SP; {@link #DEFAULT_TEXT_SIZE_SP} is used when {@code <= 0}.
     * @return A new {@link Bitmap} containing the rendered text.
     */
    @NonNull
    public Bitmap render(@NonNull String text, int textSizeSp) {
        if (text.isEmpty()) {
            text = " "; // avoid zero-size bitmap
        }
        if (textSizeSp <= 0) {
            textSizeSp = DEFAULT_TEXT_SIZE_SP;
        }

        float textSizePx = textSizeSp * mDensity;

        TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setTypeface(mTypeface);
        textPaint.setTextSize(textSizePx);
        textPaint.setColor(Color.BLACK);

        int layoutWidth = Math.max(MIN_WIDTH, Math.min(MAX_WIDTH,
                (int) Math.ceil(textPaint.measureText(text)) + PADDING_PX * 2));

        StaticLayout layout = StaticLayout.Builder
                .obtain(text, 0, text.length(), textPaint, layoutWidth - PADDING_PX * 2)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(0f, 1.2f)
                .build();

        int bitmapHeight = layout.getHeight() + PADDING_PX * 2;

        Bitmap bitmap = Bitmap.createBitmap(layoutWidth, bitmapHeight, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(Color.WHITE);

        canvas.save();
        canvas.translate(PADDING_PX, PADDING_PX);
        layout.draw(canvas);
        canvas.restore();

        return bitmap;
    }

    /**
     * Renders with the default text size ({@link #DEFAULT_TEXT_SIZE_SP} SP).
     *
     * @param text The isiBheqe PUA text to render. Must not be {@code null}.
     * @return A new {@link Bitmap} with a white background.
     * @see #render(String, int)
     */
    @NonNull
    public Bitmap render(@NonNull String text) {
        return render(text, DEFAULT_TEXT_SIZE_SP);
    }

    // =========================================================================
    // Public API -- inline render (commitContent / sticker)
    // =========================================================================

    /**
     * Renders a compact image suitable for {@code InputMethodManager.commitContent}
     * (keyboard image / sticker mode).
     *
     * <p>Key differences from {@link #render}:
     * <ul>
     *   <li><strong>Transparent background</strong> -- the bitmap alpha channel is preserved so
     *       the sticker blends naturally whether the host app uses a light or dark theme.</li>
     *   <li><strong>Dark text (#333333) with a white shadow</strong> -- readable on both light and
     *       dark host-app backgrounds without any solid fill.</li>
     *   <li><strong>Multi-line support</strong> -- text wider than {@link #MAX_INLINE_WIDTH_DP} dp
     *       wraps to additional lines.</li>
     *   <li><strong>Height cap</strong> -- if wrapped text exceeds {@link #MAX_INLINE_HEIGHT_DP} dp
     *       the last visible line is ellipsised with "…" to keep the sticker a reasonable size.</li>
     * </ul>
     *
     * @param text The isiBheqe PUA text to render. Must not be {@code null}.
     * @return A new transparent {@link Bitmap} containing the rendered text.
     */
    @NonNull
    public Bitmap renderInline(@NonNull String text) {
        if (text.isEmpty()) {
            // Return a minimal 1×1 transparent bitmap rather than falling back to the full render.
            return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
        }

        float textSizePx = INLINE_TEXT_SIZE_SP * mDensity;
        int maxWidthPx = (int) (MAX_INLINE_WIDTH_DP * mDensity);
        int maxHeightPx = (int) (MAX_INLINE_HEIGHT_DP * mDensity);
        int padding = INLINE_PADDING_PX;

        // --- Build TextPaint with shadow ---
        TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setTypeface(mTypeface);
        textPaint.setTextSize(textSizePx);
        textPaint.setColor(INLINE_TEXT_COLOR);
        // White shadow provides contrast on dark backgrounds; effectively invisible on light ones.
        textPaint.setShadowLayer(INLINE_SHADOW_RADIUS, 0f, 0f, Color.WHITE);

        // --- Determine layout width ---
        int singleLineWidth = (int) Math.ceil(textPaint.measureText(text)) + padding * 2;
        int layoutWidth = Math.min(singleLineWidth, maxWidthPx);
        int textColumnWidth = layoutWidth - padding * 2;

        // --- Build StaticLayout (handles multi-line automatically) ---
        StaticLayout layout = StaticLayout.Builder
                .obtain(text, 0, text.length(), textPaint, textColumnWidth)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(0f, 1.2f)
                .setEllipsize(TextUtils.TruncateAt.END)
                .setMaxLines(computeMaxLines(textSizePx, maxHeightPx, padding))
                .build();

        int bitmapWidth = layoutWidth;
        int bitmapHeight = Math.min(layout.getHeight() + padding * 2, maxHeightPx);

        // --- Draw onto a fully transparent bitmap ---
        Bitmap bitmap = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888);
        // Do NOT call canvas.drawColor() -- leave alpha channel at 0 (transparent).
        Canvas canvas = new Canvas(bitmap);

        canvas.save();
        canvas.translate(padding, padding);
        layout.draw(canvas);
        canvas.restore();

        return bitmap;
    }

    // =========================================================================
    // Public API -- byte array / file helpers
    // =========================================================================

    /**
     * Renders the text inline (transparent background) and compresses the result to a byte array
     * ready for delivery via {@code InputContentInfo}.
     *
     * <p>MIME-type mapping:
     * <ul>
     *   <li>{@code image/webp} -- uses {@code WEBP_LOSSY} on API 30+ or {@code WEBP} on older
     *       devices, quality 90.</li>
     *   <li>{@code image/png} -- lossless PNG, quality parameter ignored by Android.</li>
     *   <li>{@code image/gif} or {@code image/jpeg} -- falls back to PNG (GIF encoding is not
     *       built into Android; JPEG does not support transparency).</li>
     * </ul>
     *
     * @param text     The isiBheqe PUA text to render. Must not be {@code null}.
     * @param mimeType The desired output MIME type (e.g. {@code "image/webp"}). Must not be
     *                 {@code null}.
     * @return A {@code byte[]} containing the compressed image, or {@code null} if compression
     *         fails unexpectedly.
     */
    @Nullable
    public byte[] renderToBytes(@NonNull String text, @NonNull String mimeType) {
        Bitmap bitmap = renderInline(text);
        Bitmap.CompressFormat format = resolveCompressFormat(mimeType);
        int quality = format == Bitmap.CompressFormat.PNG ? 100 : 90;

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        if (!bitmap.compress(format, quality, out)) {
            return null;
        }
        return out.toByteArray();
    }

    /**
     * Renders the text inline (transparent background) and writes the compressed image directly
     * to {@code outputFile}.
     *
     * <p>The MIME-type-to-format mapping is identical to {@link #renderToBytes(String, String)}.
     *
     * @param text       The isiBheqe PUA text to render. Must not be {@code null}.
     * @param mimeType   The desired output MIME type. Must not be {@code null}.
     * @param outputFile The file to write to. Parent directories must already exist.
     *                   Must not be {@code null}.
     * @return {@code true} if the file was written successfully; {@code false} otherwise.
     */
    public boolean renderToFile(@NonNull String text,
                                @NonNull String mimeType,
                                @NonNull File outputFile) {
        Bitmap bitmap = renderInline(text);
        Bitmap.CompressFormat format = resolveCompressFormat(mimeType);
        int quality = format == Bitmap.CompressFormat.PNG ? 100 : 90;

        try (OutputStream out = new FileOutputStream(outputFile)) {
            return bitmap.compress(format, quality, out);
        } catch (IOException e) {
            return false;
        }
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    /**
     * Computes the maximum number of lines that fit within {@code maxHeightPx} given the
     * text size and vertical padding.
     *
     * @param textSizePx Text size in pixels.
     * @param maxHeightPx Maximum total bitmap height in pixels.
     * @param padding     Vertical padding (applied to top and bottom) in pixels.
     * @return A positive integer; always at least 1.
     */
    private static int computeMaxLines(float textSizePx, int maxHeightPx, int padding) {
        float usableHeight = maxHeightPx - padding * 2;
        // A generous line height factor matching the 1.2 line-spacing multiplier.
        float lineHeight = textSizePx * 1.2f;
        int maxLines = (int) Math.floor(usableHeight / lineHeight);
        return Math.max(1, maxLines);
    }

    /**
     * Maps a MIME type string to the appropriate {@link Bitmap.CompressFormat}.
     *
     * <ul>
     *   <li>{@code image/webp} → {@code WEBP_LOSSY} (API 30+) or {@code WEBP} (API < 30)</li>
     *   <li>Everything else, including {@code image/gif} and {@code image/jpeg} → {@code PNG}</li>
     * </ul>
     *
     * @param mimeType The MIME type. Must not be {@code null}.
     * @return A non-null {@link Bitmap.CompressFormat}.
     */
    @NonNull
    private static Bitmap.CompressFormat resolveCompressFormat(@NonNull String mimeType) {
        if ("image/webp".equalsIgnoreCase(mimeType)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                return Bitmap.CompressFormat.WEBP_LOSSY;
            } else {
                //noinspection deprecation -- WEBP is the only WebP format below API 30
                return Bitmap.CompressFormat.WEBP;
            }
        }
        // GIF: no built-in encoder in Android. JPEG: no transparency support.
        // Both fall back to lossless PNG which preserves the alpha channel.
        return Bitmap.CompressFormat.PNG;
    }
}
