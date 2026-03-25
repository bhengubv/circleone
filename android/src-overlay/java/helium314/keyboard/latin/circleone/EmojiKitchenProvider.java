/*
 * Copyright (C) 2026 The Other Bhengu (Pty) Ltd t/a The Geek.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package helium314.keyboard.latin.circleone;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Provides Emoji Kitchen sticker combinations sourced from Google's public Emoji Kitchen CDN.
 *
 * <p>Google's Emoji Kitchen (originally a Gboard feature) publishes mashup sticker images at
 * {@code https://www.gstatic.com/android/keyboard/emojikitchen/} under a permissive licence.
 * This class resolves known combinations, constructs the CDN URL, downloads the PNG on a
 * background thread, caches it to disk, and delivers the decoded {@link Bitmap} to the caller
 * on the main thread via {@link StickerCallback}.
 *
 * <p>Usage example:
 * <pre>{@code
 * EmojiKitchenProvider kitchen = new EmojiKitchenProvider(context);
 *
 * // Step 1 – user picks first emoji
 * List<String> partners = kitchen.getAvailableCombinations("😀");
 *
 * // Step 2 – user picks second emoji
 * kitchen.loadSticker("😀", "❤️", new EmojiKitchenProvider.StickerCallback() {
 *     public void onStickerReady(Bitmap sticker) { showSticker(sticker); }
 *     public void onError(String message) { Log.w(TAG, message); }
 * });
 * }</pre>
 *
 * <p>Sticker images are cached under {@code <cacheDir>/emoji_kitchen/} and persist across
 * sessions until {@link #clearCache(Context)} is called explicitly.
 */
public class EmojiKitchenProvider {

    private static final String TAG = "EmojiKitchenProvider";

    /** Base CDN URL for all Emoji Kitchen sticker PNGs. */
    private static final String CDN_BASE =
            "https://www.gstatic.com/android/keyboard/emojikitchen/";

    /** Subdirectory inside the app cache for downloaded stickers. */
    private static final String CACHE_SUBDIR = "emoji_kitchen";

    /** Connection and read timeout for sticker downloads, in milliseconds. */
    private static final int TIMEOUT_MS = 12_000;

    /**
     * Known Emoji Kitchen release dates, ordered from most recent to oldest.
     * The provider tries each date in turn until a 200 response is received.
     * New batches are published periodically; add new dates at the front of this array.
     */
    private static final String[] KNOWN_DATES = {
            "20230803",
            "20230301",
            "20221101",
            "20220815",
            "20220506",
            "20220203",
            "20211115",
            "20210521",
            "20210218",
            "20201001",
    };

    // -----------------------------------------------------------------------------------------
    // Combination map
    //
    // Key   = codepoint string of the *first* emoji (canonical form, no variation selectors).
    // Value = array of codepoint strings that can be combined with the key emoji.
    //
    // The map is intentionally symmetric: if A→B exists then B→A also exists so that the
    // CDN lookup always puts the lexicographically-first codepoint in the path segment.
    //
    // This is a curated subset (~200 popular emoji) of the full ~30 000-combination dataset
    // published at https://github.com/googlefonts/noto-emoji/raw/main/metadata/emoji_kitchen_data.json
    // -----------------------------------------------------------------------------------------

    /**
     * Internal combination table.
     * Keys and values are Unicode codepoint strings in lowercase hex (e.g. {@code "1f600"}).
     * Variation selectors (U+FE0F) are included where required by the CDN.
     */
    private static final Map<String, Set<String>> COMBINATIONS = new HashMap<>();

    static {
        // ---- Faces -----------------------------------------------------------------------
        addPair("1f600", "2764-fe0f");  // 😀 + ❤️
        addPair("1f600", "1f525");      // 😀 + 🔥
        addPair("1f600", "1f44d");      // 😀 + 👍
        addPair("1f600", "1f389");      // 😀 + 🎉
        addPair("1f600", "2b50");       // 😀 + ⭐
        addPair("1f600", "1f308");      // 😀 + 🌈
        addPair("1f600", "1f382");      // 😀 + 🎂
        addPair("1f600", "1f648");      // 😀 + 🙈
        addPair("1f600", "1f602");      // 😀 + 😂
        addPair("1f600", "1f60d");      // 😀 + 😍
        addPair("1f600", "1f62d");      // 😀 + 😭
        addPair("1f600", "1f621");      // 😀 + 😡
        addPair("1f600", "1f97a");      // 😀 + 🥺
        addPair("1f600", "1f644");      // 😀 + 🙄
        addPair("1f600", "1f929");      // 😀 + 🤩
        addPair("1f600", "1f973");      // 😀 + 🥳

        addPair("1f602", "2764-fe0f");  // 😂 + ❤️
        addPair("1f602", "1f525");      // 😂 + 🔥
        addPair("1f602", "1f44d");      // 😂 + 👍
        addPair("1f602", "1f389");      // 😂 + 🎉
        addPair("1f602", "2b50");       // 😂 + ⭐
        addPair("1f602", "1f308");      // 😂 + 🌈
        addPair("1f602", "1f62d");      // 😂 + 😭
        addPair("1f602", "1f621");      // 😂 + 😡
        addPair("1f602", "1f60d");      // 😂 + 😍
        addPair("1f602", "1f97a");      // 😂 + 🥺
        addPair("1f602", "1f648");      // 😂 + 🙈
        addPair("1f602", "1f4a9");      // 😂 + 💩
        addPair("1f602", "1f480");      // 😂 + 💀
        addPair("1f602", "1f47d");      // 😂 + 👽

        addPair("1f60d", "2764-fe0f");  // 😍 + ❤️
        addPair("1f60d", "1f525");      // 😍 + 🔥
        addPair("1f60d", "1f44d");      // 😍 + 👍
        addPair("1f60d", "1f389");      // 😍 + 🎉
        addPair("1f60d", "2b50");       // 😍 + ⭐
        addPair("1f60d", "1f308");      // 😍 + 🌈
        addPair("1f60d", "1f602");      // 😍 + 😂
        addPair("1f60d", "1f62d");      // 😍 + 😭
        addPair("1f60d", "1f97a");      // 😍 + 🥺
        addPair("1f60d", "1f973");      // 😍 + 🥳
        addPair("1f60d", "1f929");      // 😍 + 🤩

        addPair("1f62d", "2764-fe0f");  // 😭 + ❤️
        addPair("1f62d", "1f525");      // 😭 + 🔥
        addPair("1f62d", "1f44d");      // 😭 + 👍
        addPair("1f62d", "1f389");      // 😭 + 🎉
        addPair("1f62d", "2b50");       // 😭 + ⭐
        addPair("1f62d", "1f308");      // 😭 + 🌈
        addPair("1f62d", "1f97a");      // 😭 + 🥺
        addPair("1f62d", "1f621");      // 😭 + 😡
        addPair("1f62d", "1f480");      // 😭 + 💀
        addPair("1f62d", "1f4a9");      // 😭 + 💩
        addPair("1f62d", "1f648");      // 😭 + 🙈

        addPair("1f621", "2764-fe0f");  // 😡 + ❤️
        addPair("1f621", "1f525");      // 😡 + 🔥
        addPair("1f621", "1f4a9");      // 😡 + 💩
        addPair("1f621", "1f480");      // 😡 + 💀
        addPair("1f621", "1f4a5");      // 😡 + 💥
        addPair("1f621", "1f97a");      // 😡 + 🥺
        addPair("1f621", "1f648");      // 😡 + 🙈

        addPair("1f97a", "2764-fe0f");  // 🥺 + ❤️
        addPair("1f97a", "1f525");      // 🥺 + 🔥
        addPair("1f97a", "1f44d");      // 🥺 + 👍
        addPair("1f97a", "2b50");       // 🥺 + ⭐
        addPair("1f97a", "1f308");      // 🥺 + 🌈
        addPair("1f97a", "1f648");      // 🥺 + 🙈
        addPair("1f97a", "1f480");      // 🥺 + 💀

        addPair("1f929", "2764-fe0f");  // 🤩 + ❤️
        addPair("1f929", "1f525");      // 🤩 + 🔥
        addPair("1f929", "2b50");       // 🤩 + ⭐
        addPair("1f929", "1f389");      // 🤩 + 🎉
        addPair("1f929", "1f308");      // 🤩 + 🌈
        addPair("1f929", "1f973");      // 🤩 + 🥳

        addPair("1f973", "2764-fe0f");  // 🥳 + ❤️
        addPair("1f973", "1f525");      // 🥳 + 🔥
        addPair("1f973", "1f389");      // 🥳 + 🎉
        addPair("1f973", "2b50");       // 🥳 + ⭐
        addPair("1f973", "1f308");      // 🥳 + 🌈
        addPair("1f973", "1f382");      // 🥳 + 🎂

        addPair("1f644", "2764-fe0f");  // 🙄 + ❤️
        addPair("1f644", "1f4a9");      // 🙄 + 💩
        addPair("1f644", "1f480");      // 🙄 + 💀
        addPair("1f644", "1f648");      // 🙄 + 🙈

        addPair("1f910", "2764-fe0f");  // 🤐 + ❤️
        addPair("1f910", "1f525");      // 🤐 + 🔥
        addPair("1f910", "1f648");      // 🤐 + 🙈
        addPair("1f910", "1f480");      // 🤐 + 💀

        addPair("1f914", "2764-fe0f");  // 🤔 + ❤️
        addPair("1f914", "1f525");      // 🤔 + 🔥
        addPair("1f914", "2b50");       // 🤔 + ⭐
        addPair("1f914", "1f4a9");      // 🤔 + 💩

        addPair("1f917", "2764-fe0f");  // 🤗 + ❤️
        addPair("1f917", "1f525");      // 🤗 + 🔥
        addPair("1f917", "2b50");       // 🤗 + ⭐
        addPair("1f917", "1f389");      // 🤗 + 🎉

        addPair("1f92f", "1f525");      // 🤯 + 🔥
        addPair("1f92f", "2764-fe0f");  // 🤯 + ❤️
        addPair("1f92f", "1f4a5");      // 🤯 + 💥
        addPair("1f92f", "2b50");       // 🤯 + ⭐

        addPair("1f480", "2764-fe0f");  // 💀 + ❤️
        addPair("1f480", "1f525");      // 💀 + 🔥
        addPair("1f480", "1f4a9");      // 💀 + 💩
        addPair("1f480", "1f648");      // 💀 + 🙈
        addPair("1f480", "2b50");       // 💀 + ⭐
        addPair("1f480", "1f389");      // 💀 + 🎉

        addPair("1f4a9", "2764-fe0f");  // 💩 + ❤️
        addPair("1f4a9", "1f525");      // 💩 + 🔥
        addPair("1f4a9", "2b50");       // 💩 + ⭐
        addPair("1f4a9", "1f389");      // 💩 + 🎉

        addPair("1f648", "2764-fe0f");  // 🙈 + ❤️
        addPair("1f648", "1f525");      // 🙈 + 🔥
        addPair("1f648", "2b50");       // 🙈 + ⭐

        addPair("1f47d", "2764-fe0f");  // 👽 + ❤️
        addPair("1f47d", "1f525");      // 👽 + 🔥
        addPair("1f47d", "2b50");       // 👽 + ⭐
        addPair("1f47d", "1f648");      // 👽 + 🙈

        addPair("1f431", "2764-fe0f");  // 🐱 + ❤️
        addPair("1f431", "1f525");      // 🐱 + 🔥
        addPair("1f431", "2b50");       // 🐱 + ⭐
        addPair("1f431", "1f308");      // 🐱 + 🌈
        addPair("1f431", "1f389");      // 🐱 + 🎉

        addPair("1f436", "2764-fe0f");  // 🐶 + ❤️
        addPair("1f436", "1f525");      // 🐶 + 🔥
        addPair("1f436", "2b50");       // 🐶 + ⭐
        addPair("1f436", "1f308");      // 🐶 + 🌈
        addPair("1f436", "1f389");      // 🐶 + 🎉
        addPair("1f436", "1f431");      // 🐶 + 🐱

        // ---- Hearts -----------------------------------------------------------------------
        addPair("2764-fe0f", "1f525");  // ❤️ + 🔥
        addPair("2764-fe0f", "2b50");   // ❤️ + ⭐
        addPair("2764-fe0f", "1f308");  // ❤️ + 🌈
        addPair("2764-fe0f", "1f389");  // ❤️ + 🎉
        addPair("2764-fe0f", "1f382");  // ❤️ + 🎂
        addPair("2764-fe0f", "1f4a5");  // ❤️ + 💥
        addPair("2764-fe0f", "1f44d");  // ❤️ + 👍
        addPair("2764-fe0f", "1f648");  // ❤️ + 🙈
        addPair("2764-fe0f", "1f480");  // ❤️ + 💀

        addPair("1f9e1", "2764-fe0f");  // 🧡 + ❤️
        addPair("1f9e1", "1f525");      // 🧡 + 🔥
        addPair("1f9e1", "2b50");       // 🧡 + ⭐
        addPair("1f9e1", "1f308");      // 🧡 + 🌈

        addPair("1f49b", "2764-fe0f");  // 💛 + ❤️
        addPair("1f49b", "1f525");      // 💛 + 🔥
        addPair("1f49b", "2b50");       // 💛 + ⭐
        addPair("1f49b", "1f308");      // 💛 + 🌈

        addPair("1f49a", "2764-fe0f");  // 💚 + ❤️
        addPair("1f49a", "1f525");      // 💚 + 🔥
        addPair("1f49a", "2b50");       // 💚 + ⭐
        addPair("1f49a", "1f308");      // 💚 + 🌈

        addPair("1f499", "2764-fe0f");  // 💙 + ❤️
        addPair("1f499", "1f525");      // 💙 + 🔥
        addPair("1f499", "2b50");       // 💙 + ⭐
        addPair("1f499", "1f308");      // 💙 + 🌈

        addPair("1f49c", "2764-fe0f");  // 💜 + ❤️
        addPair("1f49c", "1f525");      // 💜 + 🔥
        addPair("1f49c", "2b50");       // 💜 + ⭐
        addPair("1f49c", "1f308");      // 💜 + 🌈

        addPair("1f5a4", "2764-fe0f");  // 🖤 + ❤️
        addPair("1f5a4", "1f525");      // 🖤 + 🔥
        addPair("1f5a4", "1f480");      // 🖤 + 💀

        addPair("1f90d", "2764-fe0f");  // 🤍 + ❤️
        addPair("1f90d", "2b50");       // 🤍 + ⭐
        addPair("1f90d", "1f308");      // 🤍 + 🌈

        addPair("1f90e", "2764-fe0f");  // 🤎 + ❤️
        addPair("1f90e", "1f525");      // 🤎 + 🔥
        addPair("1f90e", "2b50");       // 🤎 + ⭐

        addPair("1f493", "2764-fe0f");  // 💓 + ❤️
        addPair("1f493", "1f525");      // 💓 + 🔥
        addPair("1f493", "2b50");       // 💓 + ⭐
        addPair("1f493", "1f308");      // 💓 + 🌈

        addPair("1f495", "2764-fe0f");  // 💕 + ❤️
        addPair("1f495", "1f525");      // 💕 + 🔥
        addPair("1f495", "2b50");       // 💕 + ⭐
        addPair("1f495", "1f308");      // 💕 + 🌈

        addPair("1f496", "2764-fe0f");  // 💖 + ❤️
        addPair("1f496", "1f525");      // 💖 + 🔥
        addPair("1f496", "2b50");       // 💖 + ⭐

        // ---- Objects / Nature / Misc ------------------------------------------------------
        addPair("1f525", "2b50");       // 🔥 + ⭐
        addPair("1f525", "1f308");      // 🔥 + 🌈
        addPair("1f525", "1f389");      // 🔥 + 🎉
        addPair("1f525", "1f382");      // 🔥 + 🎂
        addPair("1f525", "1f44d");      // 🔥 + 👍
        addPair("1f525", "1f4a5");      // 🔥 + 💥
        addPair("1f525", "1f648");      // 🔥 + 🙈
        addPair("1f525", "1f480");      // 🔥 + 💀

        addPair("2b50", "1f308");       // ⭐ + 🌈
        addPair("2b50", "1f389");       // ⭐ + 🎉
        addPair("2b50", "1f382");       // ⭐ + 🎂
        addPair("2b50", "1f44d");       // ⭐ + 👍
        addPair("2b50", "1f648");       // ⭐ + 🙈

        addPair("1f308", "1f389");      // 🌈 + 🎉
        addPair("1f308", "1f382");      // 🌈 + 🎂
        addPair("1f308", "1f44d");      // 🌈 + 👍
        addPair("1f308", "1f648");      // 🌈 + 🙈

        addPair("1f389", "1f382");      // 🎉 + 🎂
        addPair("1f389", "1f44d");      // 🎉 + 👍
        addPair("1f389", "1f648");      // 🎉 + 🙈

        addPair("1f382", "1f44d");      // 🎂 + 👍
        addPair("1f382", "2b50");       // 🎂 + ⭐

        addPair("1f4a5", "1f480");      // 💥 + 💀
        addPair("1f4a5", "2b50");       // 💥 + ⭐

        addPair("1f44d", "1f648");      // 👍 + 🙈
        addPair("1f44d", "2b50");       // 👍 + ⭐

        addPair("1f984", "2764-fe0f");  // 🦄 + ❤️
        addPair("1f984", "1f525");      // 🦄 + 🔥
        addPair("1f984", "2b50");       // 🦄 + ⭐
        addPair("1f984", "1f308");      // 🦄 + 🌈
        addPair("1f984", "1f389");      // 🦄 + 🎉

        addPair("1f40d", "2764-fe0f");  // 🐍 + ❤️
        addPair("1f40d", "1f525");      // 🐍 + 🔥
        addPair("1f40d", "2b50");       // 🐍 + ⭐

        addPair("1f422", "2764-fe0f");  // 🐢 + ❤️
        addPair("1f422", "1f525");      // 🐢 + 🔥
        addPair("1f422", "2b50");       // 🐢 + ⭐

        addPair("1f438", "2764-fe0f");  // 🐸 + ❤️
        addPair("1f438", "1f525");      // 🐸 + 🔥
        addPair("1f438", "2b50");       // 🐸 + ⭐
        addPair("1f438", "1f308");      // 🐸 + 🌈

        addPair("1f43c", "2764-fe0f");  // 🐼 + ❤️
        addPair("1f43c", "1f525");      // 🐼 + 🔥
        addPair("1f43c", "2b50");       // 🐼 + ⭐

        addPair("1f98a", "2764-fe0f");  // 🦊 + ❤️
        addPair("1f98a", "1f525");      // 🦊 + 🔥
        addPair("1f98a", "2b50");       // 🦊 + ⭐

        addPair("1f99b", "2764-fe0f");  // 🦛 + ❤️
        addPair("1f99b", "1f525");      // 🦛 + 🔥
        addPair("1f99b", "2b50");       // 🦛 + ⭐

        addPair("1f40b", "2764-fe0f");  // 🐋 + ❤️
        addPair("1f40b", "1f525");      // 🐋 + 🔥
        addPair("1f40b", "2b50");       // 🐋 + ⭐
        addPair("1f40b", "1f308");      // 🐋 + 🌈

        addPair("1f419", "2764-fe0f");  // 🐙 + ❤️
        addPair("1f419", "1f525");      // 🐙 + 🔥
        addPair("1f419", "2b50");       // 🐙 + ⭐
        addPair("1f419", "1f308");      // 🐙 + 🌈

        addPair("1f995", "2764-fe0f");  // 🦕 + ❤️
        addPair("1f995", "1f525");      // 🦕 + 🔥
        addPair("1f995", "2b50");       // 🦕 + ⭐

        addPair("1f335", "2764-fe0f");  // 🌵 + ❤️
        addPair("1f335", "1f525");      // 🌵 + 🔥
        addPair("1f335", "2b50");       // 🌵 + ⭐
        addPair("1f335", "1f308");      // 🌵 + 🌈

        addPair("1f333", "2764-fe0f");  // 🌳 + ❤️
        addPair("1f333", "1f525");      // 🌳 + 🔥
        addPair("1f333", "2b50");       // 🌳 + ⭐

        addPair("1f344", "2764-fe0f");  // 🍄 + ❤️
        addPair("1f344", "1f525");      // 🍄 + 🔥
        addPair("1f344", "2b50");       // 🍄 + ⭐
        addPair("1f344", "1f308");      // 🍄 + 🌈

        addPair("1f355", "2764-fe0f");  // 🍕 + ❤️
        addPair("1f355", "1f525");      // 🍕 + 🔥
        addPair("1f355", "2b50");       // 🍕 + ⭐

        addPair("1f354", "2764-fe0f");  // 🍔 + ❤️
        addPair("1f354", "1f525");      // 🍔 + 🔥
        addPair("1f354", "2b50");       // 🍔 + ⭐

        addPair("1f36c", "2764-fe0f");  // 🍬 + ❤️
        addPair("1f36c", "1f525");      // 🍬 + 🔥
        addPair("1f36c", "2b50");       // 🍬 + ⭐
        addPair("1f36c", "1f308");      // 🍬 + 🌈

        addPair("2615", "2764-fe0f");   // ☕ + ❤️
        addPair("2615", "1f525");       // ☕ + 🔥
        addPair("2615", "2b50");        // ☕ + ⭐

        addPair("1f3f3-fe0f", "2764-fe0f"); // 🏳️ + ❤️
        addPair("1f3f3-fe0f", "1f308");     // 🏳️ + 🌈
        addPair("1f3f3-fe0f", "1f525");     // 🏳️ + 🔥

        addPair("26bd", "2764-fe0f");   // ⚽ + ❤️
        addPair("26bd", "1f525");       // ⚽ + 🔥
        addPair("26bd", "2b50");        // ⚽ + ⭐

        addPair("1f3c6", "2764-fe0f");  // 🏆 + ❤️
        addPair("1f3c6", "1f525");      // 🏆 + 🔥
        addPair("1f3c6", "2b50");       // 🏆 + ⭐
        addPair("1f3c6", "1f389");      // 🏆 + 🎉

        addPair("1f3b8", "2764-fe0f");  // 🎸 + ❤️
        addPair("1f3b8", "1f525");      // 🎸 + 🔥
        addPair("1f3b8", "2b50");       // 🎸 + ⭐

        addPair("1f680", "2764-fe0f");  // 🚀 + ❤️
        addPair("1f680", "1f525");      // 🚀 + 🔥
        addPair("1f680", "2b50");       // 🚀 + ⭐
        addPair("1f680", "1f308");      // 🚀 + 🌈

        addPair("1f30d", "2764-fe0f");  // 🌍 + ❤️
        addPair("1f30d", "1f525");      // 🌍 + 🔥
        addPair("1f30d", "2b50");       // 🌍 + ⭐
        addPair("1f30d", "1f308");      // 🌍 + 🌈

        addPair("1f319", "2764-fe0f");  // 🌙 + ❤️
        addPair("1f319", "1f525");      // 🌙 + 🔥
        addPair("1f319", "2b50");       // 🌙 + ⭐
        addPair("1f319", "2600-fe0f");  // 🌙 + ☀️

        addPair("2600-fe0f", "2764-fe0f"); // ☀️ + ❤️
        addPair("2600-fe0f", "1f525");     // ☀️ + 🔥
        addPair("2600-fe0f", "2b50");      // ☀️ + ⭐
        addPair("2600-fe0f", "1f308");     // ☀️ + 🌈

        addPair("26a1", "2764-fe0f");   // ⚡ + ❤️
        addPair("26a1", "1f525");       // ⚡ + 🔥
        addPair("26a1", "2b50");        // ⚡ + ⭐
        addPair("26a1", "1f4a5");       // ⚡ + 💥

        addPair("1f4af", "2764-fe0f");  // 💯 + ❤️
        addPair("1f4af", "1f525");      // 💯 + 🔥
        addPair("1f4af", "2b50");       // 💯 + ⭐

        addPair("1f947", "2764-fe0f");  // 🥇 + ❤️
        addPair("1f947", "1f525");      // 🥇 + 🔥
        addPair("1f947", "2b50");       // 🥇 + ⭐
        addPair("1f947", "1f389");      // 🥇 + 🎉

        addPair("1f48e", "2764-fe0f");  // 💎 + ❤️
        addPair("1f48e", "1f525");      // 💎 + 🔥
        addPair("1f48e", "2b50");       // 💎 + ⭐
        addPair("1f48e", "1f308");      // 💎 + 🌈

        addPair("1f451", "2764-fe0f");  // 👑 + ❤️
        addPair("1f451", "1f525");      // 👑 + 🔥
        addPair("1f451", "2b50");       // 👑 + ⭐
        addPair("1f451", "1f389");      // 👑 + 🎉

        addPair("1f4ab", "2764-fe0f");  // 💫 + ❤️
        addPair("1f4ab", "1f525");      // 💫 + 🔥
        addPair("1f4ab", "2b50");       // 💫 + ⭐
        addPair("1f4ab", "1f308");      // 💫 + 🌈

        addPair("1f4a7", "2764-fe0f");  // 💧 + ❤️
        addPair("1f4a7", "1f525");      // 💧 + 🔥
        addPair("1f4a7", "2b50");       // 💧 + ⭐
        addPair("1f4a7", "1f308");      // 💧 + 🌈

        addPair("1f308", "1f4a7");      // 🌈 + 💧  (extra direction already covered above)
    }

    // -----------------------------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------------------------

    /**
     * Registers a bidirectional combination between {@code cp1} and {@code cp2}.
     *
     * @param cp1 First codepoint string (lowercase hex, variation selectors included)
     * @param cp2 Second codepoint string
     */
    private static void addPair(String cp1, String cp2) {
        if (!COMBINATIONS.containsKey(cp1)) COMBINATIONS.put(cp1, new HashSet<>());
        if (!COMBINATIONS.containsKey(cp2)) COMBINATIONS.put(cp2, new HashSet<>());
        COMBINATIONS.get(cp1).add(cp2);
        COMBINATIONS.get(cp2).add(cp1);
    }

    // -----------------------------------------------------------------------------------------
    // Instance
    // -----------------------------------------------------------------------------------------

    private final ExecutorService mExecutor = Executors.newFixedThreadPool(3);
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());

    /** Application cache directory; set once in the constructor. */
    private final File mCacheDir;

    /**
     * Constructs an {@code EmojiKitchenProvider} and initialises the on-disk sticker cache.
     *
     * @param context Any {@link Context}; the application context is used internally.
     */
    public EmojiKitchenProvider(Context context) {
        mCacheDir = new File(context.getApplicationContext().getCacheDir(), CACHE_SUBDIR);
        if (!mCacheDir.exists()) {
            //noinspection ResultOfMethodCallIgnored
            mCacheDir.mkdirs();
        }
    }

    // -----------------------------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------------------------

    /**
     * Callback interface for asynchronous sticker loading.
     */
    public interface StickerCallback {
        /**
         * Called on the main thread when the sticker bitmap is ready.
         *
         * @param sticker Decoded sticker bitmap. The caller is responsible for recycling it.
         */
        void onStickerReady(Bitmap sticker);

        /**
         * Called on the main thread when the sticker could not be loaded.
         *
         * @param message Human-readable error description.
         */
        void onError(String message);
    }

    /**
     * Returns the list of emoji codepoint strings that can be combined with {@code emoji}.
     *
     * <p>The returned strings are Unicode codepoints in lowercase hex (e.g. {@code "1f602"});
     * they are <em>not</em> the rendered emoji characters. To convert back to a character use
     * {@link #codepointToEmoji(String)}.
     *
     * @param emoji The base emoji character (e.g. {@code "😀"}).
     * @return Unmodifiable list of partner codepoint strings; empty if none.
     */
    public List<String> getAvailableCombinations(String emoji) {
        String cp = emojiToCodepoint(emoji);
        Set<String> partners = COMBINATIONS.get(cp);
        if (partners == null || partners.isEmpty()) return Collections.emptyList();
        List<String> result = new ArrayList<>(partners);
        Collections.sort(result);
        return Collections.unmodifiableList(result);
    }

    /**
     * Returns whether a Kitchen combination exists for the two given emoji.
     *
     * @param emoji1 First emoji character.
     * @param emoji2 Second emoji character.
     * @return {@code true} if a combination is known to exist.
     */
    public boolean isAvailable(String emoji1, String emoji2) {
        String cp1 = emojiToCodepoint(emoji1);
        String cp2 = emojiToCodepoint(emoji2);
        Set<String> partners = COMBINATIONS.get(cp1);
        return partners != null && partners.contains(cp2);
    }

    /**
     * Returns the CDN URL for the mashup sticker of {@code emoji1} and {@code emoji2}
     * using the most recent known release date.
     *
     * <p>The URL follows the pattern:
     * {@code https://www.gstatic.com/android/keyboard/emojikitchen/{date}/u{cp1}/u{cp1}_u{cp2}.png}
     * where {@code cp1} is the lexicographically-first codepoint string.
     *
     * <p>No network check is performed; the URL may return 404 if the combination does not
     * exist in that particular release. Use {@link #loadSticker} for fault-tolerant loading
     * that tries all known dates.
     *
     * @param emoji1 First emoji character.
     * @param emoji2 Second emoji character.
     * @return CDN URL string using the most recent date, or {@code null} if the combination
     *         is not in the built-in table.
     */
    public String getCombinedStickerUrl(String emoji1, String emoji2) {
        if (!isAvailable(emoji1, emoji2)) return null;
        String[] ordered = orderedCodepoints(emoji1, emoji2);
        return buildUrl(KNOWN_DATES[0], ordered[0], ordered[1]);
    }

    /**
     * Downloads (or retrieves from disk cache) the mashup sticker for {@code emoji1} and
     * {@code emoji2}, then delivers it to {@code callback} on the main thread.
     *
     * <p>The method tries each entry in {@link #KNOWN_DATES} in order until a successful
     * HTTP 200 response is received. The decoded {@link Bitmap} is cached to disk using a
     * deterministic filename so subsequent calls skip the network entirely.
     *
     * @param emoji1   First emoji character.
     * @param emoji2   Second emoji character.
     * @param callback Receives the decoded bitmap or an error message.
     */
    public void loadSticker(String emoji1, String emoji2, StickerCallback callback) {
        mExecutor.execute(() -> {
            // Check combination table first.
            if (!isAvailable(emoji1, emoji2)) {
                deliverError(callback, "No combination available for " + emoji1 + " + " + emoji2);
                return;
            }

            String[] ordered = orderedCodepoints(emoji1, emoji2);
            String cp1 = ordered[0];
            String cp2 = ordered[1];

            // Check disk cache.
            File cachedFile = cacheFileFor(cp1, cp2);
            if (cachedFile.exists() && cachedFile.length() > 0) {
                Bitmap cached = decodeSafe(cachedFile);
                if (cached != null) {
                    deliverBitmap(callback, cached);
                    return;
                }
                // Corrupt cache entry — delete and re-download.
                //noinspection ResultOfMethodCallIgnored
                cachedFile.delete();
            }

            // Try each known date until one succeeds.
            for (String date : KNOWN_DATES) {
                String url = buildUrl(date, cp1, cp2);
                Log.d(TAG, "Trying URL: " + url);
                try {
                    Bitmap bitmap = downloadBitmap(url, cachedFile);
                    if (bitmap != null) {
                        deliverBitmap(callback, bitmap);
                        return;
                    }
                } catch (IOException e) {
                    Log.w(TAG, "Failed for date " + date + ": " + e.getMessage());
                }
            }

            deliverError(callback, "Sticker not found for " + emoji1 + " + " + emoji2
                    + " on any known date");
        });
    }

    /**
     * Deletes all sticker PNGs from the on-disk cache.
     *
     * @param context Any {@link Context}; only used to resolve the cache directory.
     */
    public void clearCache(Context context) {
        File dir = new File(context.getApplicationContext().getCacheDir(), CACHE_SUBDIR);
        if (!dir.exists()) return;
        File[] files = dir.listFiles();
        if (files == null) return;
        int deleted = 0;
        for (File f : files) {
            if (f.isFile() && f.getName().endsWith(".png")) {
                if (f.delete()) deleted++;
            }
        }
        Log.i(TAG, "Cache cleared: " + deleted + " sticker(s) removed.");
    }

    /**
     * Shuts down the background executor. Call from {@code onDestroy()} of the keyboard service.
     */
    public void shutdown() {
        mExecutor.shutdownNow();
    }

    // -----------------------------------------------------------------------------------------
    // Codepoint utilities
    // -----------------------------------------------------------------------------------------

    /**
     * Converts an emoji character (possibly multi-codepoint, e.g. with variation selectors)
     * to the hyphenated lowercase hex codepoint string used by the Emoji Kitchen CDN.
     *
     * <p>Example: {@code "❤️"} → {@code "2764-fe0f"}
     *
     * @param emoji The emoji string.
     * @return Hyphen-delimited lowercase hex codepoints; or the original string on failure.
     */
    public static String emojiToCodepoint(String emoji) {
        if (emoji == null || emoji.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i < emoji.length()) {
            int cp = emoji.codePointAt(i);
            if (sb.length() > 0) sb.append('-');
            sb.append(Integer.toHexString(cp));
            i += Character.charCount(cp);
        }
        return sb.toString();
    }

    /**
     * Converts a hyphenated lowercase hex codepoint string back to the emoji character.
     *
     * <p>Example: {@code "2764-fe0f"} → {@code "❤️"}
     *
     * @param codepoint The hyphen-delimited codepoint string.
     * @return The emoji character string.
     */
    public static String codepointToEmoji(String codepoint) {
        if (codepoint == null || codepoint.isEmpty()) return "";
        String[] parts = codepoint.split("-");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            try {
                int cp = Integer.parseInt(part, 16);
                sb.appendCodePoint(cp);
            } catch (NumberFormatException e) {
                Log.w(TAG, "Unexpected codepoint part: " + part);
            }
        }
        return sb.toString();
    }

    // -----------------------------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------------------------

    /**
     * Returns a two-element array with the emoji codepoint strings in canonical order.
     * The CDN always expects the lexicographically-first codepoint as the directory segment.
     */
    private String[] orderedCodepoints(String emoji1, String emoji2) {
        String cp1 = emojiToCodepoint(emoji1);
        String cp2 = emojiToCodepoint(emoji2);
        if (cp1.compareTo(cp2) <= 0) return new String[]{cp1, cp2};
        return new String[]{cp2, cp1};
    }

    /**
     * Builds the CDN URL for a specific release date.
     *
     * @param date Emoji Kitchen release date string (e.g. {@code "20201001"}).
     * @param cp1  Lexicographically-first codepoint string.
     * @param cp2  Second codepoint string.
     * @return Full CDN URL.
     */
    private String buildUrl(String date, String cp1, String cp2) {
        return CDN_BASE + date + "/u" + cp1 + "/u" + cp1 + "_u" + cp2 + ".png";
    }

    /**
     * Returns the deterministic cache file for a given ordered pair of codepoints.
     */
    private File cacheFileFor(String cp1, String cp2) {
        String name = "ek_" + cp1 + "_" + cp2 + ".png";
        return new File(mCacheDir, name);
    }

    /**
     * Downloads the PNG at {@code urlStr} and writes it to {@code dest}.
     * Returns the decoded {@link Bitmap}, or {@code null} if the server returned a non-200
     * status.
     *
     * @param urlStr URL to fetch.
     * @param dest   Destination cache file.
     * @return Decoded bitmap, or {@code null} on a non-200 response.
     * @throws IOException on network or I/O failure.
     */
    private Bitmap downloadBitmap(String urlStr, File dest) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(TIMEOUT_MS);
        conn.setReadTimeout(TIMEOUT_MS);
        conn.setRequestProperty("User-Agent", "CircleOne-Keyboard/1.0");

        try {
            int status = conn.getResponseCode();
            if (status != HttpURLConnection.HTTP_OK) {
                Log.d(TAG, "HTTP " + status + " for " + urlStr);
                return null;
            }

            // Write to disk while streaming so we can cache and decode in one pass.
            File tmp = new File(dest.getParent(), dest.getName() + ".tmp");
            try (InputStream in = conn.getInputStream();
                 FileOutputStream out = new FileOutputStream(tmp)) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) != -1) {
                    out.write(buf, 0, n);
                }
                out.flush();
            }

            // Rename atomically to avoid serving a partially-written file.
            if (!tmp.renameTo(dest)) {
                // Fallback: copy and delete on platforms where rename across dirs fails.
                copyFile(tmp, dest);
                //noinspection ResultOfMethodCallIgnored
                tmp.delete();
            }

            return decodeSafe(dest);
        } finally {
            conn.disconnect();
        }
    }

    /**
     * Decodes a PNG file to a {@link Bitmap}, returning {@code null} on any failure.
     */
    private Bitmap decodeSafe(File file) {
        try (FileInputStream fis = new FileInputStream(file)) {
            Bitmap bm = BitmapFactory.decodeStream(fis);
            if (bm == null) Log.w(TAG, "BitmapFactory returned null for " + file.getName());
            return bm;
        } catch (IOException e) {
            Log.w(TAG, "Failed to decode cached file: " + e.getMessage());
            return null;
        }
    }

    /** Copies {@code src} to {@code dst} byte-for-byte. */
    private void copyFile(File src, File dst) throws IOException {
        try (FileInputStream in = new FileInputStream(src);
             FileOutputStream out = new FileOutputStream(dst)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) {
                out.write(buf, 0, n);
            }
        }
    }

    /** Posts a successful bitmap result to the main thread. */
    private void deliverBitmap(StickerCallback callback, Bitmap bitmap) {
        mMainHandler.post(() -> callback.onStickerReady(bitmap));
    }

    /** Posts an error message to the main thread. */
    private void deliverError(StickerCallback callback, String message) {
        Log.w(TAG, message);
        mMainHandler.post(() -> callback.onError(message));
    }
}
