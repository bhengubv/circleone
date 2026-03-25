/*
 * ScriptView - PUA Detector
 * License: GPL-3.0-only
 * Copyright (C) 2026 The Other Bhengu (Pty) Ltd t/a The Geek
 */

package org.ofrp.scriptview;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Fast scanner for Private Use Area (PUA) codepoints U+E000–U+E340.
 * Single-pass O(n) scanning with no allocations for texts without PUA.
 */
public final class PuaDetector {
    /**
     * PUA range start (U+E000).
     */
    public static final int PUA_START = 0xE000;

    /**
     * PUA range end (U+E340).
     */
    public static final int PUA_END = 0xE340;

    /**
     * Immutable match result containing index and codepoint.
     */
    public static final class PuaMatch {
        /**
         * Index in the source text where the PUA codepoint was found.
         */
        public final int index;

        /**
         * The PUA codepoint (U+E000 to U+E340).
         */
        public final char codepoint;

        /**
         * Create a new PuaMatch.
         *
         * @param index     the index in the source text
         * @param codepoint the PUA codepoint
         */
        public PuaMatch(int index, char codepoint) {
            this.index = index;
            this.codepoint = codepoint;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof PuaMatch)) return false;
            PuaMatch puaMatch = (PuaMatch) o;
            return index == puaMatch.index && codepoint == puaMatch.codepoint;
        }

        @Override
        public int hashCode() {
            return 31 * index + codepoint;
        }

        @Override
        public String toString() {
            return "PuaMatch{" +
                    "index=" + index +
                    ", codepoint=U+" + Integer.toHexString(codepoint).toUpperCase() +
                    '}';
        }
    }

    /**
     * Check if a character is in the PUA range (U+E000 to U+E340).
     *
     * @param c the character to check
     * @return true if c is in PUA range
     */
    public static boolean isPua(char c) {
        return c >= PUA_START && c <= PUA_END;
    }

    /**
     * Scan text for all PUA codepoints in a single pass.
     * Returns an empty list if no PUA codepoints are found.
     *
     * @param text the text to scan
     * @return list of PuaMatch objects in order, or empty list if none found
     */
    public static List<PuaMatch> scan(CharSequence text) {
        if (text == null || text.length() == 0) {
            return Collections.emptyList();
        }

        List<PuaMatch> matches = null;
        final int length = text.length();

        for (int i = 0; i < length; i++) {
            char c = text.charAt(i);
            if (isPua(c)) {
                if (matches == null) {
                    matches = new ArrayList<>();
                }
                matches.add(new PuaMatch(i, c));
            }
        }

        return matches != null ? matches : Collections.emptyList();
    }

    // Private constructor to prevent instantiation
    private PuaDetector() {
    }
}
