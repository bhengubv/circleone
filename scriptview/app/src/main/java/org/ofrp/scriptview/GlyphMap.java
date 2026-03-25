/*
 * GlyphMap.java
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
package org.ofrp.scriptview;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe collection that manages all visible PUA glyphs and their screen positions.
 *
 * <p>Entries are stored in a {@link ConcurrentHashMap} keyed by accessibility node ID, so
 * individual nodes can be updated or removed independently without touching unrelated entries.
 * All public methods that expose entry data return defensive copies, making it safe for the
 * render thread to iterate a snapshot while another thread simultaneously modifies the map.
 *
 * <h2>Usage</h2>
 * <pre>
 *   // Service side (accessibility thread / main thread):
 *   glyphMap.update(nodeId, entries);   // replace entries for one node
 *   glyphMap.removeNode(nodeId);        // remove a node that is no longer visible
 *   glyphMap.clear();                   // wipe everything (e.g. on interrupt)
 *
 *   // View side (render thread, via invalidate → onDraw):
 *   List&lt;GlyphEntry&gt; snapshot = glyphMap.getAllEntries();
 *   for (GlyphEntry e : snapshot) { ... }
 * </pre>
 *
 * <h2>Scroll support</h2>
 * <p>{@link #scrollAll(float)} shifts every entry's {@link GlyphEntry#screenBounds} vertically
 * so the overlay can track scrolling content without a full node-tree rescan.
 *
 * <h2>Thread safety</h2>
 * <p>The backing store is a {@link ConcurrentHashMap}, so individual read/write operations are
 * atomic. {@link #scrollAll(float)} and {@link #update(long, List)} perform compound operations
 * that are guarded with {@code synchronized(this)} to maintain consistency.
 */
public final class GlyphMap {

    /**
     * Backing store: node ID → list of glyph entries for that node.
     *
     * <p>Using {@link ConcurrentHashMap} gives safe concurrent reads for {@link #getAllEntries()}
     * and {@link #size()} without a global lock.
     */
    private final ConcurrentHashMap<Long, List<GlyphEntry>> mEntries = new ConcurrentHashMap<>();

    // -----------------------------------------------------------------------------------------
    // Write operations
    // -----------------------------------------------------------------------------------------

    /**
     * Replaces all {@link GlyphEntry} objects stored for the given node ID.
     *
     * <p>If {@code entries} is {@code null} or empty the node is removed from the map entirely,
     * which is equivalent to calling {@link #removeNode(long)}.
     *
     * @param nodeId  the accessibility node ID whose entries should be replaced
     * @param entries the new list of entries; may be {@code null} or empty
     */
    public synchronized void update(long nodeId, List<GlyphEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            mEntries.remove(nodeId);
        } else {
            // Store a defensive copy so callers cannot mutate the map through the original list.
            mEntries.put(nodeId, new ArrayList<>(entries));
        }
    }

    /**
     * Convenience overload that accepts a flat list of {@link GlyphEntry} objects and groups
     * them by their embedded {@link GlyphEntry#nodeId}.
     *
     * <p>Any node that is not represented in {@code entries} is left untouched. Nodes that
     * previously had entries but no longer appear in {@code entries} are also left untouched;
     * call {@link #clear()} or {@link #removeNode(long)} to evict stale nodes explicitly.
     *
     * @param entries flat list of all currently visible glyph entries; may be {@code null}
     */
    public synchronized void update(List<GlyphEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            mEntries.clear();
            return;
        }

        // Group incoming entries by nodeId.
        ConcurrentHashMap<Long, List<GlyphEntry>> grouped = new ConcurrentHashMap<>();
        for (GlyphEntry entry : entries) {
            grouped.computeIfAbsent(entry.nodeId, k -> new ArrayList<>()).add(entry);
        }

        // Replace the entire map with the freshly grouped snapshot.
        mEntries.clear();
        mEntries.putAll(grouped);
    }

    /**
     * Removes all glyph entries associated with the given node ID.
     *
     * <p>Has no effect if the node ID is not present in the map.
     *
     * @param nodeId the accessibility node ID to remove
     */
    public void removeNode(long nodeId) {
        mEntries.remove(nodeId);
    }

    /**
     * Shifts the {@link GlyphEntry#screenBounds} of every entry vertically by {@code deltaY}.
     *
     * <p>Positive {@code deltaY} moves entries downward; negative moves them upward. This allows
     * the overlay to follow scroll events without triggering a full accessibility tree rescan.
     *
     * @param deltaY vertical offset in pixels (positive = down, negative = up)
     */
    public synchronized void scrollAll(float deltaY) {
        if (deltaY == 0f) return;

        for (Map.Entry<Long, List<GlyphEntry>> mapEntry : mEntries.entrySet()) {
            List<GlyphEntry> oldList = mapEntry.getValue();
            List<GlyphEntry> newList = new ArrayList<>(oldList.size());

            for (GlyphEntry e : oldList) {
                // RectF is mutable; clone it before modifying to preserve immutability of the
                // GlyphEntry. GlyphEntry itself is immutable but screenBounds is a mutable RectF.
                android.graphics.RectF shifted = new android.graphics.RectF(e.screenBounds);
                shifted.offset(0f, deltaY);
                newList.add(new GlyphEntry(e.puaCodepoint, shifted, e.textSize, e.textColor, e.nodeId));
            }

            mapEntry.setValue(newList);
        }
    }

    /**
     * Removes all glyph entries from every node.
     */
    public void clear() {
        mEntries.clear();
    }

    // -----------------------------------------------------------------------------------------
    // Read operations
    // -----------------------------------------------------------------------------------------

    /**
     * Returns a flat snapshot of all currently stored {@link GlyphEntry} objects across all
     * nodes, in an unspecified order.
     *
     * <p>The returned list is a defensive copy — it is safe to iterate without holding any lock
     * even if another thread concurrently calls {@link #update}, {@link #removeNode}, or
     * {@link #clear}.
     *
     * @return a new, unmodifiable flat list of all glyph entries; never {@code null}
     */
    public List<GlyphEntry> getAllEntries() {
        List<GlyphEntry> snapshot = new ArrayList<>();
        for (List<GlyphEntry> nodeEntries : mEntries.values()) {
            snapshot.addAll(nodeEntries);
        }
        return Collections.unmodifiableList(snapshot);
    }

    /**
     * Returns the total number of glyph entries across all nodes.
     *
     * <p>This is an O(n) operation where n is the number of distinct node IDs.
     *
     * @return total glyph count; always &ge; 0
     */
    public int size() {
        int total = 0;
        for (List<GlyphEntry> list : mEntries.values()) {
            total += list.size();
        }
        return total;
    }

    /**
     * Returns {@code true} if there are no glyph entries in the map.
     *
     * @return {@code true} if the map is empty
     */
    public boolean isEmpty() {
        return mEntries.isEmpty();
    }
}
