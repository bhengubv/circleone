/*
 * Copyright (C) 2026 The Other Bhengu (Pty) Ltd t/a The Geek.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package helium314.keyboard.latin.circleone;

import android.content.ClipDescription;
import android.content.Context;
import android.graphics.Bitmap;
import android.inputmethodservice.InputMethodService;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;

import androidx.core.content.FileProvider;
import androidx.core.view.inputmethod.EditorInfoCompat;
import androidx.core.view.inputmethod.InputConnectionCompat;
import androidx.core.view.inputmethod.InputContentInfoCompat;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Handles rich-content commit (commitContent API) for sending rendered isiBheqe images
 * directly from the keyboard to receiving apps such as WhatsApp, Telegram, Signal,
 * and any other app that exposes {@code contentMimeTypes} in its {@link EditorInfo}.
 *
 * <p>This helper uses AndroidX {@link InputConnectionCompat}, {@link EditorInfoCompat},
 * and {@link InputContentInfoCompat} so the feature works on API 13+ without any
 * raw API-25+ guards in calling code.
 *
 * <h3>MIME negotiation order (preferred → fallback)</h3>
 * <ol>
 *   <li>{@code image/webp} -- smallest file, good quality</li>
 *   <li>{@code image/png}  -- lossless fallback</li>
 *   <li>{@code image/gif}  -- legacy receivers</li>
 *   <li>{@code image/jpeg} -- last resort</li>
 *   <li>image wildcard    -- accepted as webp (wildcard)</li>
 *   <li>all-types wildcard        -- accepted as webp (wildcard)</li>
 * </ol>
 *
 * <h3>Cache housekeeping</h3>
 * Every call writes a uniquely-timestamped file under
 * {@code <cacheDir>/isibheqe_images/}. When the cache directory grows beyond
 * 50 files, {@link #cleanupOldImages(Context)} is called
 * automatically to remove files older than 3600000 ms (1 hour).
 */
public final class CommitContentHelper {

    private static final String TAG = "CommitContentHelper";

    /** Sub-directory inside {@link Context#getCacheDir()} used for all committed images. */
    private static final String CACHE_DIR = "isibheqe_images";

    /** FileProvider authority suffix -- must match {@code res/xml/file_paths.xml}. */
    private static final String FILEPROVIDER_SUFFIX = ".circleone.fileprovider";

    /** Files older than this (in ms) are eligible for deletion during cleanup. */
    private static final long MAX_CACHE_AGE_MS = 60 * 60 * 1_000L; // 1 hour

    /** Trigger cleanup when cache contains more than this many files. */
    private static final int CLEANUP_THRESHOLD = 50;

    /**
     * MIME preference list, checked in order against what the editor advertises.
     * Wildcards are tested last and both resolve to {@code image/webp} for encoding.
     */
    private static final String[] PREFERRED_MIME_TYPES = {
            "image/webp",
            "image/png",
            "image/gif",
            "image/jpeg",
    };

    // Utility class -- no instances.
    private CommitContentHelper() {}

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Returns {@code true} when the current editor supports receiving any image
     * content via the commitContent API.
     *
     * <p>Uses {@link EditorInfoCompat#getContentMimeTypes(EditorInfo)} so it works
     * correctly on all API levels without manual version guards.
     *
     * @param editorInfo The {@link EditorInfo} for the currently-focused field.
     * @return {@code true} if at least one image MIME type (including wildcards) is
     *         advertised by the editor.
     */
    public static boolean canCommitContent(EditorInfo editorInfo) {
        if (editorInfo == null) return false;
        String[] mimes = EditorInfoCompat.getContentMimeTypes(editorInfo);
        if (mimes == null || mimes.length == 0) return false;
        for (String mime : mimes) {
            if (mime != null && (mime.startsWith("image/") || mime.equals("*/*"))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Renders the given isiBheqe PUA text as an image and commits it to the currently
     * focused input field via {@link InputConnectionCompat#commitContent}.
     *
     * <p>The best mutually-supported MIME type is negotiated automatically; see class
     * Javadoc for the preference order. A uniquely-timestamped file is written to the
     * app's cache directory and exposed via {@link FileProvider}.
     *
     * @param service    The running {@link InputMethodService} (typically LatinIME).
     * @param editorInfo The {@link EditorInfo} for the currently-focused field.
     * @param text       The isiBheqe PUA string to render.
     * @return {@code true} if the content was committed successfully.
     */
    public static boolean commitIsiBheqeImage(InputMethodService service,
                                               EditorInfo editorInfo,
                                               String text) {
        if (text == null || text.isEmpty()) return false;

        InputConnection ic = service.getCurrentInputConnection();
        if (ic == null) return false;

        if (!canCommitContent(editorInfo)) return false;

        String negotiatedMime = findBestMimeType(editorInfo);
        if (negotiatedMime == null) {
            Log.d(TAG, "No compatible image MIME type found -- not committing.");
            return false;
        }

        Context context = service.getApplicationContext();

        // Render the glyph to a bitmap.
        GlyphRenderer renderer = new GlyphRenderer(context);
        Bitmap bitmap = renderer.renderInline(text);
        if (bitmap == null) {
            Log.d(TAG, "GlyphRenderer.renderInline() returned null bitmap.");
            return false;
        }

        // Write bitmap to a timestamped cache file.
        File imageFile = writeToCache(context, bitmap, negotiatedMime);
        bitmap.recycle();
        if (imageFile == null) return false;

        // Trigger housekeeping if cache is getting large.
        File cacheDir = new File(context.getCacheDir(), CACHE_DIR);
        String[] cached = cacheDir.list();
        if (cached != null && cached.length > CLEANUP_THRESHOLD) {
            cleanupOldImages(context);
        }

        return commitFile(context, ic, imageFile, negotiatedMime);
    }

    /**
     * Shared utility that wraps any already-written image {@link File} in a
     * {@link FileProvider} URI and commits it to the given {@link InputConnection}
     * via {@link InputConnectionCompat#commitContent}.
     *
     * <p>Can be called directly by other components such as {@code GifSearchView}.
     *
     * @param context  Application context (used to resolve the FileProvider authority).
     * @param ic       The active {@link InputConnection}.
     * @param file     The image file to share. Must be accessible to the FileProvider.
     * @param mimeType The MIME type of the file (e.g. {@code "image/webp"}).
     * @return {@code true} if {@link InputConnectionCompat#commitContent} returned
     *         {@code true}.
     */
    public static boolean commitFile(Context context,
                                     InputConnection ic,
                                     File file,
                                     String mimeType) {
        String authority = context.getPackageName() + FILEPROVIDER_SUFFIX;
        Uri contentUri;
        try {
            contentUri = FileProvider.getUriForFile(context, authority, file);
        } catch (IllegalArgumentException e) {
            Log.d(TAG, "FileProvider could not map file: " + file.getAbsolutePath(), e);
            return false;
        }

        ClipDescription description = new ClipDescription("isiBheqe", new String[]{mimeType});
        InputContentInfoCompat contentInfo = new InputContentInfoCompat(contentUri, description,
                /* linkUri= */ null);

        int flags = InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION;
        boolean committed = InputConnectionCompat.commitContent(ic,
                /* editorInfo= */ null, contentInfo, flags, /* opts= */ null);

        Log.d(TAG, "commitContent [" + mimeType + "] → " + committed
                + " (" + file.getName() + ")");
        return committed;
    }

    /**
     * Negotiates the best image MIME type that is mutually supported by this helper
     * and the target editor.
     *
     * <p>Logs the full list of MIME types advertised by the editor so that debugging
     * is straightforward.
     *
     * @param editorInfo The {@link EditorInfo} for the currently-focused field.
     * @return The best negotiated MIME type string, or {@code null} if no match.
     */
    public static String findBestMimeType(EditorInfo editorInfo) {
        if (editorInfo == null) return null;

        String[] editorMimes = EditorInfoCompat.getContentMimeTypes(editorInfo);
        if (editorMimes == null || editorMimes.length == 0) return null;

        // Log what the app actually advertises -- very useful for debugging new apps.
        StringBuilder sb = new StringBuilder("Editor advertises MIME types: [");
        for (int i = 0; i < editorMimes.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append(editorMimes[i]);
        }
        sb.append("]");
        Log.d(TAG, sb.toString());

        // Walk our preference list and return the first match.
        for (String preferred : PREFERRED_MIME_TYPES) {
            for (String editorMime : editorMimes) {
                if (preferred.equals(editorMime)) {
                    Log.d(TAG, "Negotiated MIME type: " + preferred);
                    return preferred;
                }
            }
        }

        // Accept wildcards -- resolve both to webp for the best size/quality ratio.
        for (String editorMime : editorMimes) {
            if ("image/*".equals(editorMime) || "*/*".equals(editorMime)) {
                Log.d(TAG, "Wildcard match (" + editorMime + ") → resolving to image/webp");
                return "image/webp";
            }
        }

        return null;
    }

    /**
     * Deletes cached image files that are older than 3600000 ms.
     *
     * <p>Called automatically by {@link #commitIsiBheqeImage} when the cache directory
     * exceeds 50 files, and can also be called explicitly
     * (e.g. on keyboard hide or low-memory warnings).
     *
     * @param context Application context used to locate the cache directory.
     */
    public static void cleanupOldImages(Context context) {
        File cacheDir = new File(context.getCacheDir(), CACHE_DIR);
        if (!cacheDir.exists()) return;

        File[] files = cacheDir.listFiles();
        if (files == null || files.length == 0) return;

        long cutoff = System.currentTimeMillis() - MAX_CACHE_AGE_MS;
        int deleted = 0;
        for (File f : files) {
            if (f.isFile() && f.lastModified() < cutoff) {
                if (f.delete()) deleted++;
            }
        }
        Log.d(TAG, "cleanupOldImages: deleted " + deleted + " of " + files.length + " cached files.");
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Compresses {@code bitmap} into the format dictated by {@code mimeType} and writes
     * the result to a uniquely-timestamped file in {@value #CACHE_DIR}.
     *
     * @param context  Application context.
     * @param bitmap   The bitmap to compress. Must not be null.
     * @param mimeType The negotiated MIME type -- determines the {@link Bitmap.CompressFormat}.
     * @return The written {@link File}, or {@code null} on any error.
     */
    private static File writeToCache(Context context, Bitmap bitmap, String mimeType) {
        File cacheDir = new File(context.getCacheDir(), CACHE_DIR);
        if (!cacheDir.exists() && !cacheDir.mkdirs()) {
            Log.d(TAG, "Could not create cache dir: " + cacheDir.getAbsolutePath());
            return null;
        }

        String extension = extensionFor(mimeType);
        String filename = "isibheqe_" + System.currentTimeMillis() + "." + extension;
        File imageFile = new File(cacheDir, filename);

        Bitmap.CompressFormat format = compressFormatFor(mimeType);
        int quality = "image/webp".equals(mimeType) || "image/jpeg".equals(mimeType) ? 90 : 100;

        try (FileOutputStream fos = new FileOutputStream(imageFile)) {
            bitmap.compress(format, quality, fos);
            fos.flush();
        } catch (IOException e) {
            Log.d(TAG, "Failed to write bitmap to cache: " + e.getMessage());
            //noinspection ResultOfMethodCallIgnored
            imageFile.delete();
            return null;
        }

        Log.d(TAG, "Wrote " + imageFile.getName() + " (" + imageFile.length() + " bytes)");
        return imageFile;
    }

    /**
     * Maps a MIME type to the appropriate {@link Bitmap.CompressFormat}.
     *
     * <p>Uses {@code WEBP_LOSSY} on API 30+ (where the old {@code WEBP} constant was
     * deprecated) and falls back to {@code WEBP} on older API levels.
     */
    private static Bitmap.CompressFormat compressFormatFor(String mimeType) {
        switch (mimeType) {
            case "image/webp":
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    return Bitmap.CompressFormat.WEBP_LOSSY;
                } else {
                    //noinspection deprecation
                    return Bitmap.CompressFormat.WEBP;
                }
            case "image/jpeg":
                return Bitmap.CompressFormat.JPEG;
            case "image/png":
            case "image/gif":
            default:
                // GIF is not natively supported by Bitmap -- PNG is the safest lossless fallback.
                return Bitmap.CompressFormat.PNG;
        }
    }

    /**
     * Returns the file extension string (without the leading dot) for a given MIME type.
     */
    private static String extensionFor(String mimeType) {
        switch (mimeType) {
            case "image/webp": return "webp";
            case "image/jpeg": return "jpg";
            case "image/gif":  return "gif";
            case "image/png":
            default:           return "png";
        }
    }
}
