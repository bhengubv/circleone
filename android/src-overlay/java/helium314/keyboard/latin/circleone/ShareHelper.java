/*
 * Copyright (C) 2026 The Other Bhengu (Pty) Ltd t/a The Geek.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package helium314.keyboard.latin.circleone;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Saves rendered isiBheqe bitmaps to cache and creates share intents.
 */
public class ShareHelper {

    private static final String CACHE_DIR = "isibheqe_images";
    private static final String IMAGE_FILENAME = "isibheqe_text.png";

    /**
     * Saves a bitmap to the app's cache directory and returns a content URI via FileProvider.
     *
     * @param context Application context
     * @param bitmap  The bitmap to save
     * @return Content URI for the saved image, or null on failure
     */
    public static Uri saveBitmapToCache(Context context, Bitmap bitmap) {
        File cacheDir = new File(context.getCacheDir(), CACHE_DIR);
        if (!cacheDir.exists() && !cacheDir.mkdirs()) {
            return null;
        }

        File imageFile = new File(cacheDir, IMAGE_FILENAME);
        try (FileOutputStream fos = new FileOutputStream(imageFile)) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
            fos.flush();
        } catch (IOException e) {
            return null;
        }

        String authority = context.getPackageName() + ".circleone.fileprovider";
        return FileProvider.getUriForFile(context, authority, imageFile);
    }

    /**
     * Creates an ACTION_SEND intent for sharing an isiBheqe image.
     *
     * @param imageUri Content URI of the image
     * @return Share intent ready for startActivity
     */
    public static Intent createShareIntent(Uri imageUri) {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("image/png");
        intent.putExtra(Intent.EXTRA_STREAM, imageUri);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        return Intent.createChooser(intent, "Share isiBheqe text");
    }

    /**
     * Renders text and shares it in one call. Convenience method for the compose activity.
     *
     * @param context Application context
     * @param renderer GlyphRenderer instance
     * @param text isiBheqe PUA text to share
     * @return Share intent, or null on failure
     */
    public static Intent renderAndShare(Context context, GlyphRenderer renderer, String text) {
        Bitmap bitmap = renderer.render(text);
        Uri uri = saveBitmapToCache(context, bitmap);
        if (uri == null) return null;
        return createShareIntent(uri);
    }
}
