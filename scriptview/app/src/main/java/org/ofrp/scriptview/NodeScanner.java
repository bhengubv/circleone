/*
 * ScriptView - Node Scanner
 * License: GPL-3.0-only
 * Copyright (C) 2026 The Other Bhengu (Pty) Ltd t/a The Geek
 */

package org.ofrp.scriptview;

import android.graphics.RectF;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.Log;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Traverses the accessibility node tree to find text containing PUA (Private Use Area)
 * codepoints and resolves the exact on-screen bounds for each character using
 * {@link AccessibilityNodeInfo#EXTRA_DATA_TEXT_CHARACTER_LOCATION_KEY} (API 26+).
 *
 * <p>Usage:
 * <pre>{@code
 *   AccessibilityNodeInfo root = service.getRootInActiveWindow();
 *   List<GlyphEntry> glyphs = NodeScanner.scanTree(root, Color.WHITE);
 *   // render glyphs over the screen
 * }</pre>
 *
 * <p>Performance contract:
 * <ul>
 *   <li>Scans at most {@value #MAX_NODES} nodes per call — stops early if exceeded.</li>
 *   <li>Nodes with no text are skipped in O(1).</li>
 *   <li>Every child {@link AccessibilityNodeInfo} obtained via {@link AccessibilityNodeInfo#getChild}
 *       is recycled immediately after use to avoid native object leaks.</li>
 * </ul>
 *
 * <p>Requires API level 26 (Android 8.0, Oreo) or higher for character-location data.
 * On older APIs, {@link #getCharacterBounds} always returns {@code null} and no
 * {@link GlyphEntry} objects will be produced.
 */
public final class NodeScanner {

    private static final String TAG = "NodeScanner";

    /**
     * Maximum number of accessibility nodes inspected per {@link #scanTree} call.
     * Prevents runaway iteration on deeply nested or pathological view hierarchies.
     */
    public static final int MAX_NODES = 1000;

    // Private constructor — static utility class.
    private NodeScanner() {
    }

    // ---------------------------------------------------------------------------
    // Public API
    // ---------------------------------------------------------------------------

    /**
     * Recursively scans the accessibility tree rooted at {@code root} for nodes whose
     * text contains PUA codepoints (U+E000–U+E340).  For each PUA character found, the
     * method requests its exact screen-coordinate bounds via
     * {@link AccessibilityNodeInfo#EXTRA_DATA_TEXT_CHARACTER_LOCATION_KEY} and packages
     * the result into a {@link GlyphEntry}.
     *
     * <p>The caller retains ownership of {@code root} — this method does not recycle it.
     *
     * @param root      the root {@link AccessibilityNodeInfo} from
     *                  {@code AccessibilityService.getRootInActiveWindow()};
     *                  may be {@code null}, in which case an empty list is returned.
     * @param textColor ARGB color to embed in every produced {@link GlyphEntry}, used
     *                  by the overlay renderer to tint glyphs.
     * @return an unmodifiable list of {@link GlyphEntry} objects in depth-first tree
     *         order; never {@code null}, may be empty.
     */
    public static List<GlyphEntry> scanTree(AccessibilityNodeInfo root, int textColor) {
        if (root == null) {
            return Collections.emptyList();
        }

        List<GlyphEntry> results = new ArrayList<>();
        // nodeCount[0] is a mutable counter shared across all recursive frames.
        int[] nodeCount = {0};
        scanNode(root, textColor, results, nodeCount);
        return Collections.unmodifiableList(results);
    }

    // ---------------------------------------------------------------------------
    // Private helpers
    // ---------------------------------------------------------------------------

    /**
     * Depth-first recursive visitor.  Inspects {@code node}'s own text, then visits
     * each child.  Children are recycled immediately after recursing into them.
     *
     * @param node      the node to inspect (not owned; caller recycles when appropriate)
     * @param textColor tint to forward into created {@link GlyphEntry} objects
     * @param results   accumulator for discovered glyphs
     * @param nodeCount single-element array used as a mutable int counter;
     *                  iteration stops when {@code nodeCount[0] >= MAX_NODES}
     */
    private static void scanNode(AccessibilityNodeInfo node,
                                 int textColor,
                                 List<GlyphEntry> results,
                                 int[] nodeCount) {
        if (node == null) {
            return;
        }
        if (nodeCount[0] >= MAX_NODES) {
            return;
        }
        nodeCount[0]++;

        // --- Inspect this node's text ---
        CharSequence text = node.getText();
        if (text != null && text.length() > 0) {
            List<PuaDetector.PuaMatch> matches = PuaDetector.scan(text);
            if (!matches.isEmpty()) {
                long nodeId = getNodeId(node);
                for (PuaDetector.PuaMatch match : matches) {
                    RectF bounds = getCharacterBounds(node, match.index);
                    if (bounds != null) {
                        // Estimate text size from the character's bounding-box height.
                        float textSize = bounds.height();
                        results.add(new GlyphEntry(
                                match.codepoint,
                                bounds,
                                textSize,
                                textColor,
                                nodeId));
                    }
                }
            }
        }

        // --- Recurse into children ---
        int childCount = node.getChildCount();
        for (int i = 0; i < childCount; i++) {
            if (nodeCount[0] >= MAX_NODES) {
                break;
            }
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                try {
                    scanNode(child, textColor, results, nodeCount);
                } finally {
                    // Always recycle: getChild() returns a new reference each time.
                    child.recycle();
                }
            }
        }
    }

    /**
     * Requests the screen-coordinate bounding rectangle for a single character within
     * {@code node}'s text using the API-26 character-location extra.
     *
     * <p>Detailed steps:
     * <ol>
     *   <li>Build a {@link Bundle} with the required start-index and length arguments.</li>
     *   <li>Call {@link AccessibilityNodeInfo#refreshWithExtraData} to populate the node's
     *       extras in-place.</li>
     *   <li>Retrieve the {@code RectF[]} parcelable array from {@link AccessibilityNodeInfo#getExtras}.</li>
     *   <li>Return element [0] if non-null (off-screen characters are represented as
     *       {@code null} elements by the framework).</li>
     * </ol>
     *
     * <p>Any exception thrown by {@code refreshWithExtraData} (e.g. from buggy IME nodes
     * or system views that partially implement the API) is caught and logged; the method
     * returns {@code null} in that case.
     *
     * @param node      the node to query; must have accessible text
     * @param charIndex zero-based index into {@link AccessibilityNodeInfo#getText()}
     * @return a {@link RectF} with screen coordinates, or {@code null} if the character
     *         is off-screen, the API is unavailable, or an error occurred
     */
    private static RectF getCharacterBounds(AccessibilityNodeInfo node, int charIndex) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            // EXTRA_DATA_TEXT_CHARACTER_LOCATION_KEY requires API 26.
            return null;
        }

        try {
            Bundle args = new Bundle();
            args.putInt(
                    AccessibilityNodeInfo.EXTRA_DATA_TEXT_CHARACTER_LOCATION_ARG_START_INDEX,
                    charIndex);
            args.putInt(
                    AccessibilityNodeInfo.EXTRA_DATA_TEXT_CHARACTER_LOCATION_ARG_LENGTH,
                    1);

            boolean refreshed = node.refreshWithExtraData(
                    AccessibilityNodeInfo.EXTRA_DATA_TEXT_CHARACTER_LOCATION_KEY,
                    args);

            if (!refreshed) {
                return null;
            }

            Bundle extras = node.getExtras();
            if (extras == null) {
                return null;
            }

            // The framework stores the results as a Parcelable[].
            // Each element corresponds to one requested character; off-screen chars are null.
            Parcelable[] locations = extras.getParcelableArray(
                    AccessibilityNodeInfo.EXTRA_DATA_TEXT_CHARACTER_LOCATION_KEY);

            if (locations == null || locations.length == 0) {
                return null;
            }

            // We requested exactly one character (length == 1), so index 0 is our result.
            Parcelable location = locations[0];
            if (location instanceof RectF) {
                return (RectF) location;
            }
            return null;

        } catch (Exception e) {
            // Some nodes (system UI, certain IME containers) throw on refreshWithExtraData.
            // Log at verbose level to avoid log spam during normal operation.
            Log.v(TAG, "getCharacterBounds: exception for charIndex=" + charIndex
                    + " on node=" + node.getClassName() + ": " + e.getMessage());
            return null;
        }
    }

    /**
     * Extracts a stable numeric identifier for {@code node} by combining its window ID
     * and source node ID.  This mirrors the internal 64-bit handle Android uses for
     * accessibility nodes and is suitable for use as a cache key or render tag.
     *
     * <p>On API levels below 21, both values are available through the same accessor;
     * this method works across all API levels the app targets.
     *
     * @param node the node to identify
     * @return a 64-bit value whose high 32 bits are the window ID and low 32 bits are
     *         derived from the source node's identity hash, or {@code 0} if the node
     *         is {@code null}
     */
    private static long getNodeId(AccessibilityNodeInfo node) {
        if (node == null) {
            return 0L;
        }
        // AccessibilityNodeInfo does not expose its internal node-ID directly on the
        // public API.  We combine windowId (available since API 14) with the object's
        // identity hash as a best-effort stable identifier within a single scan pass.
        int windowId = node.getWindowId();
        int objectHash = System.identityHashCode(node);
        return ((long) windowId << 32) | (objectHash & 0xFFFFFFFFL);
    }
}
