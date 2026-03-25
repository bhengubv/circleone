/*
 * Copyright (C) 2026 The Other Bhengu (Pty) Ltd t/a The Geek.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package helium314.keyboard.latin.circleone;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.provider.MediaStore;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Manages user-selected photo backgrounds for the CircleOne keyboard.
 *
 * <p>Extends HeliBoard's colour-scheme theming with photo backgrounds — the
 * same personalisation feature popularised by Gboard. The chosen photo is:
 * <ul>
 *   <li>Scaled and centre-cropped to exactly match the keyboard's measured
 *       dimensions so no letterboxing occurs.</li>
 *   <li>Composited with a configurable semi-transparent dark overlay so key
 *       labels remain legible regardless of photo content.</li>
 *   <li>Stored in the app's internal private storage — never on external
 *       storage or in the media library.</li>
 *   <li>Persisted across keyboard sessions; survives process death.</li>
 * </ul>
 *
 * <h2>Typical usage (inside the hosting Activity / InputMethodService)</h2>
 * <pre>{@code
 * // 1. Launch picker from an Activity
 * PhotoBackgroundManager.pickPhoto(activity, REQUEST_PHOTO_BG);
 *
 * // 2. Handle result in onActivityResult()
 * if (requestCode == REQUEST_PHOTO_BG && resultCode == RESULT_OK) {
 *     PhotoBackgroundManager.handlePickResult(context, data,
 *             keyboardView.getWidth(), keyboardView.getHeight());
 *     keyboardView.setBackground(
 *             PhotoBackgroundManager.getBackgroundDrawable(context));
 * }
 *
 * // 3. On keyboard inflate / attach
 * if (PhotoBackgroundManager.hasCustomBackground(context)) {
 *     keyboardView.setBackground(
 *             PhotoBackgroundManager.getBackgroundDrawable(context));
 * }
 * }</pre>
 *
 * <p>All public methods are safe to call on the main thread. Bitmap I/O is
 * kept synchronous and lightweight because it operates on already-decoded,
 * already-scaled bitmaps stored as PNG files on disk.
 */
public final class PhotoBackgroundManager {

    private static final String TAG = "PhotoBgMgr";

    // -------------------------------------------------------------------------
    // SharedPreferences keys
    // -------------------------------------------------------------------------

    private static final String PREFS_NAME = "circleone_photo_background";

    /** Boolean — whether the user has set a custom photo background. */
    private static final String KEY_HAS_BACKGROUND = "has_background";

    /**
     * Float — darkening overlay opacity in [0.0, 1.0].
     * 0.0 = completely transparent (photo only); 1.0 = fully opaque black.
     */
    private static final String KEY_OVERLAY_OPACITY = "overlay_opacity";

    // -------------------------------------------------------------------------
    // Defaults
    // -------------------------------------------------------------------------

    /** Default overlay opacity — dark enough to keep white key labels readable. */
    private static final float DEFAULT_OVERLAY_OPACITY = 0.5f;

    /** JPEG quality used when re-encoding the cropped bitmap for storage. */
    private static final int STORAGE_JPEG_QUALITY = 85;

    /**
     * File name for the persisted, scaled-and-cropped background bitmap.
     * Stored in {@link Context#getFilesDir()}.
     */
    private static final String BG_FILE_NAME = "keyboard_photo_bg.jpg";

    // -------------------------------------------------------------------------
    // Private constructor — static utility class
    // -------------------------------------------------------------------------

    private PhotoBackgroundManager() {
        throw new UnsupportedOperationException("Static utility class");
    }

    // =========================================================================
    // Public API
    // =========================================================================

    /**
     * Launch Android's standard photo picker so the user can choose an image
     * from their gallery.
     *
     * <p>Prefer the modern Photo Picker (Android 13+) which requires no
     * runtime permission; fall back to {@link Intent#ACTION_PICK} on older
     * releases.
     *
     * @param activity    The {@link Activity} that will receive
     *                    {@code onActivityResult()} with the result.
     * @param requestCode The request code you wish to receive in
     *                    {@code onActivityResult()}.
     */
    public static void pickPhoto(Activity activity, int requestCode) {
        Intent intent;

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ Photo Picker — no READ_EXTERNAL_STORAGE permission needed.
            intent = new Intent(MediaStore.ACTION_PICK_IMAGES);
            intent.putExtra(MediaStore.EXTRA_PICK_IMAGES_MAX, 1);
        } else {
            // Pre-13 fallback using ACTION_PICK against the media store.
            intent = new Intent(Intent.ACTION_PICK,
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            intent.setType("image/*");
        }

        activity.startActivityForResult(intent, requestCode);
    }

    /**
     * Process the photo the user picked and persist it to internal storage.
     *
     * <p>This method:
     * <ol>
     *   <li>Opens the URI returned by the picker.</li>
     *   <li>Decodes a sub-sampled bitmap to avoid OOM on high-resolution
     *       cameras.</li>
     *   <li>Centre-crops the bitmap to the exact keyboard dimensions.</li>
     *   <li>Writes the result to the app's internal file store.</li>
     *   <li>Updates {@link SharedPreferences} to record that a background
     *       has been set.</li>
     * </ol>
     *
     * <p>Call this from your {@code onActivityResult()} when
     * {@code resultCode == Activity.RESULT_OK}.
     *
     * @param context         Application or service context.
     * @param data            The {@link Intent} delivered to
     *                        {@code onActivityResult()}.
     * @param keyboardWidth   Measured pixel width of the keyboard view.
     * @param keyboardHeight  Measured pixel height of the keyboard view.
     * @return {@code true} if the photo was saved successfully;
     *         {@code false} on any I/O or decoding error.
     */
    public static boolean handlePickResult(Context context, Intent data,
            int keyboardWidth, int keyboardHeight) {
        if (data == null) {
            Log.w(TAG, "handlePickResult: intent is null");
            return false;
        }

        Uri uri = data.getData();
        if (uri == null) {
            Log.w(TAG, "handlePickResult: no URI in result intent");
            return false;
        }

        if (keyboardWidth <= 0 || keyboardHeight <= 0) {
            Log.w(TAG, "handlePickResult: invalid keyboard dimensions "
                    + keyboardWidth + "x" + keyboardHeight);
            return false;
        }

        Bitmap decoded = null;
        Bitmap cropped = null;
        try {
            decoded = decodeSampledBitmap(context, uri, keyboardWidth, keyboardHeight);
            if (decoded == null) {
                Log.e(TAG, "handlePickResult: failed to decode bitmap from " + uri);
                return false;
            }

            cropped = centreScaleCrop(decoded, keyboardWidth, keyboardHeight);
            if (cropped == null) {
                Log.e(TAG, "handlePickResult: centreScaleCrop returned null");
                return false;
            }

            boolean saved = saveBitmapToInternalStorage(context, cropped);
            if (!saved) {
                return false;
            }

            getPrefs(context).edit()
                    .putBoolean(KEY_HAS_BACKGROUND, true)
                    .apply();

            Log.i(TAG, "Photo background saved (" + keyboardWidth + "x" + keyboardHeight + ")");
            return true;

        } finally {
            // Recycle intermediate bitmaps; the cropped bitmap is now encoded
            // on disk and is no longer needed in memory.
            recycleSafely(decoded);
            recycleSafely(cropped);
        }
    }

    /**
     * Return the stored photo background as a {@link BitmapDrawable} with the
     * configured darkening overlay pre-composited.
     *
     * <p>Returns {@code null} if no custom background has been set or if the
     * stored file cannot be read. The caller should fall back to the default
     * keyboard background in that case.
     *
     * <p>The returned drawable's bitmap is freshly decoded from disk on every
     * call. Cache the result yourself if you need to avoid repeated disk I/O.
     *
     * @param context Application or service context.
     * @return A {@link BitmapDrawable} ready to pass to
     *         {@link android.view.View#setBackground(android.graphics.drawable.Drawable)},
     *         or {@code null}.
     */
    public static BitmapDrawable getBackgroundDrawable(Context context) {
        if (!hasCustomBackground(context)) {
            return null;
        }

        File bgFile = getBgFile(context);
        if (!bgFile.exists()) {
            Log.w(TAG, "getBackgroundDrawable: file missing, clearing flag");
            clearBackground(context);
            return null;
        }

        Bitmap base = BitmapFactory.decodeFile(bgFile.getAbsolutePath());
        if (base == null) {
            Log.e(TAG, "getBackgroundDrawable: could not decode stored bitmap");
            return null;
        }

        float opacity = getPrefs(context).getFloat(KEY_OVERLAY_OPACITY, DEFAULT_OVERLAY_OPACITY);
        Bitmap composited = applyDarkeningOverlay(base, opacity);

        // base is no longer needed after compositing; composited owns the pixels.
        recycleSafely(base);

        return new BitmapDrawable(context.getResources(), composited);
    }

    /**
     * Check whether the user has set a custom photo background.
     *
     * @param context Application or service context.
     * @return {@code true} if a photo background file exists and is registered.
     */
    public static boolean hasCustomBackground(Context context) {
        if (!getPrefs(context).getBoolean(KEY_HAS_BACKGROUND, false)) {
            return false;
        }
        // Also verify the file actually exists — it may have been deleted by
        // a system clean-up or a failed write on a previous session.
        return getBgFile(context).exists();
    }

    /**
     * Remove the stored photo background and revert to the default keyboard
     * theme.
     *
     * <p>Safe to call even when no background is set.
     *
     * @param context Application or service context.
     */
    public static void clearBackground(Context context) {
        File bgFile = getBgFile(context);
        if (bgFile.exists()) {
            boolean deleted = bgFile.delete();
            if (!deleted) {
                Log.w(TAG, "clearBackground: could not delete " + bgFile.getAbsolutePath());
            }
        }

        getPrefs(context).edit()
                .remove(KEY_HAS_BACKGROUND)
                .apply();

        Log.i(TAG, "Photo background cleared");
    }

    /**
     * Set the opacity of the darkening overlay applied on top of the photo
     * background.
     *
     * <p>A value of {@code 0.0} means no overlay — the raw photo is shown.
     * A value of {@code 1.0} means a fully opaque black overlay, hiding the
     * photo entirely. {@code 0.5} is the default and works well for most
     * light-coloured keyboards.
     *
     * <p>The new opacity takes effect the next time
     * {@link #getBackgroundDrawable(Context)} is called.
     *
     * @param context Application or service context.
     * @param opacity Desired overlay opacity, clamped to [0.0, 1.0].
     */
    public static void setOverlayOpacity(Context context, float opacity) {
        float clamped = Math.max(0.0f, Math.min(1.0f, opacity));
        getPrefs(context).edit()
                .putFloat(KEY_OVERLAY_OPACITY, clamped)
                .apply();
        Log.d(TAG, "Overlay opacity set to " + clamped);
    }

    /**
     * Return the currently configured overlay opacity.
     *
     * @param context Application or service context.
     * @return Overlay opacity in [0.0, 1.0]; {@value #DEFAULT_OVERLAY_OPACITY}
     *         if never explicitly set.
     */
    public static float getOverlayOpacity(Context context) {
        return getPrefs(context).getFloat(KEY_OVERLAY_OPACITY, DEFAULT_OVERLAY_OPACITY);
    }

    // =========================================================================
    // Private helpers — bitmap processing
    // =========================================================================

    /**
     * Decode a URI into a {@link Bitmap}, using sub-sampling to avoid loading
     * a full-resolution camera image into memory when a scaled-down version
     * is all that is required.
     *
     * @param context       Context used to open a {@link android.content.ContentResolver}.
     * @param uri           URI of the source image.
     * @param targetWidth   Required output width.
     * @param targetHeight  Required output height.
     * @return Decoded bitmap, or {@code null} on failure.
     */
    private static Bitmap decodeSampledBitmap(Context context, Uri uri,
            int targetWidth, int targetHeight) {
        // --- Pass 1: decode bounds only to determine inSampleSize ---
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inJustDecodeBounds = true;

        try (InputStream probe = context.getContentResolver().openInputStream(uri)) {
            if (probe == null) return null;
            BitmapFactory.decodeStream(probe, null, opts);
        } catch (IOException e) {
            Log.e(TAG, "decodeSampledBitmap: bound probe failed", e);
            return null;
        }

        opts.inSampleSize = calculateInSampleSize(
                opts.outWidth, opts.outHeight, targetWidth, targetHeight);
        opts.inJustDecodeBounds = false;
        opts.inPreferredConfig = Bitmap.Config.ARGB_8888;

        // --- Pass 2: decode with calculated sample size ---
        try (InputStream stream = context.getContentResolver().openInputStream(uri)) {
            if (stream == null) return null;
            return BitmapFactory.decodeStream(stream, null, opts);
        } catch (IOException e) {
            Log.e(TAG, "decodeSampledBitmap: decode failed", e);
            return null;
        }
    }

    /**
     * Calculate the largest power-of-two sub-sampling factor such that the
     * decoded image dimension is at least as large as the target dimension on
     * each axis. This preserves enough resolution for a clean centre crop.
     *
     * @param srcWidth    Original image width in pixels.
     * @param srcHeight   Original image height in pixels.
     * @param reqWidth    Required (target) width in pixels.
     * @param reqHeight   Required (target) height in pixels.
     * @return {@code inSampleSize} value ≥ 1.
     */
    private static int calculateInSampleSize(int srcWidth, int srcHeight,
            int reqWidth, int reqHeight) {
        int sampleSize = 1;

        if (srcHeight > reqHeight || srcWidth > reqWidth) {
            int halfHeight = srcHeight / 2;
            int halfWidth = srcWidth / 2;

            // Keep halving while the result is still at least as large as the
            // required size so we don't lose detail needed for the crop.
            while ((halfHeight / sampleSize) >= reqHeight
                    && (halfWidth / sampleSize) >= reqWidth) {
                sampleSize *= 2;
            }
        }

        return sampleSize;
    }

    /**
     * Scale {@code src} so that it fills the target rectangle on the shorter
     * axis, then centre-crop to exactly {@code targetWidth × targetHeight}.
     *
     * <p>The aspect ratio of the source image is preserved during scaling;
     * only the excess on the longer axis is cropped away.
     *
     * @param src          Source bitmap. Not recycled by this method.
     * @param targetWidth  Desired output width in pixels.
     * @param targetHeight Desired output height in pixels.
     * @return A new bitmap of exactly {@code targetWidth × targetHeight}, or
     *         {@code null} if allocation failed.
     */
    private static Bitmap centreScaleCrop(Bitmap src, int targetWidth, int targetHeight) {
        int srcW = src.getWidth();
        int srcH = src.getHeight();

        // Scale factor: fill the target box on the shorter axis.
        float scaleX = (float) targetWidth / srcW;
        float scaleY = (float) targetHeight / srcH;
        float scale = Math.max(scaleX, scaleY);

        int scaledW = Math.round(srcW * scale);
        int scaledH = Math.round(srcH * scale);

        // Centre-crop offsets.
        int offsetX = (scaledW - targetWidth) / 2;
        int offsetY = (scaledH - targetHeight) / 2;

        // Build a single matrix that scales and then shifts by the crop offset
        // so we can use createBitmap(src, matrix) without an intermediate scaled copy.
        Matrix matrix = new Matrix();
        matrix.setScale(scale, scale);
        matrix.postTranslate(-offsetX, -offsetY);

        try {
            return Bitmap.createBitmap(src, 0, 0, srcW, srcH, matrix, true);
        } catch (OutOfMemoryError oom) {
            Log.e(TAG, "centreScaleCrop: OOM — image too large", oom);
            return null;
        }
    }

    /**
     * Composite a semi-transparent black rectangle over {@code base} to
     * produce a darkened bitmap that keeps keyboard key labels readable.
     *
     * @param base    Source bitmap. Not recycled by this method.
     * @param opacity Overlay opacity in [0.0, 1.0].
     * @return A new, composited {@link Bitmap} with the same dimensions as
     *         {@code base}.
     */
    private static Bitmap applyDarkeningOverlay(Bitmap base, float opacity) {
        Bitmap result;
        try {
            result = Bitmap.createBitmap(base.getWidth(), base.getHeight(),
                    Bitmap.Config.ARGB_8888);
        } catch (OutOfMemoryError oom) {
            Log.e(TAG, "applyDarkeningOverlay: OOM", oom);
            // Return a copy of the unmodified base — better than nothing.
            return base.copy(Bitmap.Config.ARGB_8888, false);
        }

        Canvas canvas = new Canvas(result);
        canvas.drawBitmap(base, 0, 0, null);

        Paint overlayPaint = new Paint();
        overlayPaint.setColor(Color.BLACK);
        // Convert float opacity to 0-255 alpha.
        overlayPaint.setAlpha(Math.round(opacity * 255));
        canvas.drawRect(0, 0, result.getWidth(), result.getHeight(), overlayPaint);

        return result;
    }

    // =========================================================================
    // Private helpers — persistence
    // =========================================================================

    /**
     * Write {@code bitmap} to the app's internal file store as JPEG.
     *
     * @param context Application or service context.
     * @param bitmap  The bitmap to persist. Not recycled by this method.
     * @return {@code true} on success.
     */
    private static boolean saveBitmapToInternalStorage(Context context, Bitmap bitmap) {
        File bgFile = getBgFile(context);
        File tmp = new File(bgFile.getParent(), bgFile.getName() + ".tmp");

        try (FileOutputStream fos = new FileOutputStream(tmp)) {
            boolean compressed = bitmap.compress(Bitmap.CompressFormat.JPEG,
                    STORAGE_JPEG_QUALITY, fos);
            if (!compressed) {
                Log.e(TAG, "saveBitmap: compress() returned false");
                tmp.delete();
                return false;
            }
            fos.flush();
        } catch (IOException e) {
            Log.e(TAG, "saveBitmap: write failed", e);
            tmp.delete();
            return false;
        }

        // Atomic rename — avoids a partially-written file being read on crash.
        if (!tmp.renameTo(bgFile)) {
            // renameTo can fail across filesystems; fall back to copy-then-delete.
            try (java.io.FileInputStream in = new java.io.FileInputStream(tmp);
                 FileOutputStream out = new FileOutputStream(bgFile)) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) != -1) {
                    out.write(buf, 0, n);
                }
            } catch (IOException e) {
                Log.e(TAG, "saveBitmap: fallback copy failed", e);
                tmp.delete();
                return false;
            }
            tmp.delete();
        }

        return true;
    }

    /**
     * Return the {@link File} handle for the stored background bitmap.
     * The file lives in {@link Context#getFilesDir()} which is private to
     * this app and is never accessible to other apps or the media scanner.
     *
     * @param context Application or service context.
     * @return File handle (may not yet exist on disk).
     */
    private static File getBgFile(Context context) {
        return new File(context.getFilesDir(), BG_FILE_NAME);
    }

    /**
     * Return the {@link SharedPreferences} store for photo-background settings.
     *
     * @param context Application or service context.
     * @return SharedPreferences instance.
     */
    private static SharedPreferences getPrefs(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    /**
     * Recycle a bitmap if it is non-null and not already recycled.
     *
     * @param bitmap Bitmap to recycle, or {@code null}.
     */
    private static void recycleSafely(Bitmap bitmap) {
        if (bitmap != null && !bitmap.isRecycled()) {
            bitmap.recycle();
        }
    }
}
