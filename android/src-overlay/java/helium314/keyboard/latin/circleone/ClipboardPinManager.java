/*
 * Copyright (C) 2026 The Other Bhengu (Pty) Ltd t/a The Geek.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package helium314.keyboard.latin.circleone;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Manages pinned clipboard items with auto-expiry for unpinned items.
 *
 * Extends HeliBoard's clipboard functionality by:
 * - Allowing users to pin clipboard items so they persist indefinitely
 * - Auto-expiring unpinned items after 1 hour
 * - Persisting pins across keyboard restarts via SharedPreferences
 *
 * Integration: HeliBoard's ClipboardHistoryManager calls into this class
 * to check pin status and manage expiry.
 */
public class ClipboardPinManager {

    private static final String TAG = "ClipboardPin";
    private static final String PREFS_NAME = "circleone_clipboard_pins";
    private static final String KEY_PINNED_ITEMS = "pinned_items";

    /** Unpinned items expire after 1 hour */
    private static final long EXPIRY_MS = 60 * 60 * 1000; // 1 hour

    /** Maximum number of pinned items */
    private static final int MAX_PINNED = 20;

    private final SharedPreferences mPrefs;
    private final List<PinnedItem> mPinnedItems = new ArrayList<>();

    public static class PinnedItem {
        public final String text;
        public final long pinnedAt;

        public PinnedItem(String text, long pinnedAt) {
            this.text = text;
            this.pinnedAt = pinnedAt;
        }
    }

    public ClipboardPinManager(Context context) {
        mPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        loadPinnedItems();
    }

    /**
     * Pin a clipboard item. Pinned items persist indefinitely until unpinned.
     *
     * @param text The clipboard text to pin
     * @return true if pinned successfully, false if already pinned or limit reached
     */
    public boolean pinItem(String text) {
        if (text == null || text.isEmpty()) return false;
        if (isPinned(text)) return false;

        if (mPinnedItems.size() >= MAX_PINNED) {
            // Remove oldest pinned item to make room
            mPinnedItems.remove(0);
        }

        mPinnedItems.add(new PinnedItem(text, System.currentTimeMillis()));
        savePinnedItems();
        return true;
    }

    /**
     * Unpin a clipboard item. It will now be subject to auto-expiry.
     *
     * @param text The clipboard text to unpin
     * @return true if unpinned successfully
     */
    public boolean unpinItem(String text) {
        Iterator<PinnedItem> it = mPinnedItems.iterator();
        while (it.hasNext()) {
            if (it.next().text.equals(text)) {
                it.remove();
                savePinnedItems();
                return true;
            }
        }
        return false;
    }

    /**
     * Toggle pin state for a clipboard item.
     *
     * @param text The clipboard text
     * @return true if the item is now pinned, false if now unpinned
     */
    public boolean togglePin(String text) {
        if (isPinned(text)) {
            unpinItem(text);
            return false;
        } else {
            pinItem(text);
            return true;
        }
    }

    /**
     * Check if a clipboard item is pinned.
     */
    public boolean isPinned(String text) {
        for (PinnedItem item : mPinnedItems) {
            if (item.text.equals(text)) return true;
        }
        return false;
    }

    /**
     * Get all pinned items (most recent first).
     */
    public List<PinnedItem> getPinnedItems() {
        return new ArrayList<>(mPinnedItems);
    }

    /**
     * Check if an unpinned clipboard item should be expired.
     *
     * @param clipTimestamp When the item was added to clipboard
     * @param text The clipboard text
     * @return true if the item should be removed (expired and not pinned)
     */
    public boolean shouldExpire(long clipTimestamp, String text) {
        if (isPinned(text)) return false;
        return System.currentTimeMillis() - clipTimestamp > EXPIRY_MS;
    }

    /**
     * Remove all expired unpinned items from a list of clipboard entries.
     * Returns timestamps of items that should be kept.
     *
     * @param entries List of (text, timestamp) pairs
     * @return Texts that should be retained (pinned + not expired)
     */
    public List<String> filterExpired(List<ClipboardEntry> entries) {
        List<String> retain = new ArrayList<>();
        long now = System.currentTimeMillis();

        for (ClipboardEntry entry : entries) {
            if (isPinned(entry.text)) {
                retain.add(entry.text);
            } else if (now - entry.timestamp < EXPIRY_MS) {
                retain.add(entry.text);
            }
            // else: expired, don't retain
        }
        return retain;
    }

    /**
     * Simple entry class for clipboard items.
     */
    public static class ClipboardEntry {
        public final String text;
        public final long timestamp;

        public ClipboardEntry(String text, long timestamp) {
            this.text = text;
            this.timestamp = timestamp;
        }
    }

    // ========================================================================
    // Persistence
    // ========================================================================

    private void savePinnedItems() {
        try {
            JSONArray array = new JSONArray();
            for (PinnedItem item : mPinnedItems) {
                JSONObject obj = new JSONObject();
                obj.put("text", item.text);
                obj.put("pinnedAt", item.pinnedAt);
                array.put(obj);
            }
            mPrefs.edit().putString(KEY_PINNED_ITEMS, array.toString()).apply();
        } catch (JSONException e) {
            Log.e(TAG, "Failed to save pinned items", e);
        }
    }

    private void loadPinnedItems() {
        mPinnedItems.clear();
        String json = mPrefs.getString(KEY_PINNED_ITEMS, "[]");
        try {
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.getJSONObject(i);
                String text = obj.getString("text");
                long pinnedAt = obj.getLong("pinnedAt");
                mPinnedItems.add(new PinnedItem(text, pinnedAt));
            }
        } catch (JSONException e) {
            Log.e(TAG, "Failed to load pinned items", e);
        }
    }

    /**
     * Clear all pinned items.
     */
    public void clearAll() {
        mPinnedItems.clear();
        savePinnedItems();
    }
}
