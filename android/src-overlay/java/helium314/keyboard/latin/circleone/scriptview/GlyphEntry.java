/*
 * ScriptView - Glyph Entry Value Class
 * License: GPL-3.0-only
 * Copyright (C) 2026 The Other Bhengu (Pty) Ltd t/a The Geek
 */

package helium314.keyboard.latin.circleone.scriptview;

import android.graphics.RectF;

/**
 * Immutable value class representing a detected PUA glyph on screen.
 * Contains position, size, color, and node reference for rendering.
 */
public final class GlyphEntry {
    /**
     * PUA codepoint (U+E000 to U+E340) representing the glyph.
     */
    public final char puaCodepoint;

    /**
     * Screen bounds in DP coordinates where this glyph should be rendered.
     */
    public final RectF screenBounds;

    /**
     * Text size in SP for rendering.
     */
    public final float textSize;

    /**
     * ARGB color for the rendered text.
     */
    public final int textColor;

    /**
     * Accessibility node ID for linking back to the source view.
     */
    public final long nodeId;

    /**
     * Create a new GlyphEntry.
     *
     * @param puaCodepoint the PUA codepoint
     * @param screenBounds the screen bounds
     * @param textSize     the text size in SP
     * @param textColor    the text color (ARGB)
     * @param nodeId       the accessibility node ID
     */
    public GlyphEntry(char puaCodepoint, RectF screenBounds, float textSize, int textColor, long nodeId) {
        this.puaCodepoint = puaCodepoint;
        this.screenBounds = screenBounds;
        this.textSize = textSize;
        this.textColor = textColor;
        this.nodeId = nodeId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof GlyphEntry)) return false;

        GlyphEntry that = (GlyphEntry) o;
        return puaCodepoint == that.puaCodepoint &&
                Float.compare(that.textSize, textSize) == 0 &&
                textColor == that.textColor &&
                nodeId == that.nodeId &&
                screenBounds.equals(that.screenBounds);
    }

    @Override
    public int hashCode() {
        int result = Integer.hashCode(puaCodepoint);
        result = 31 * result + screenBounds.hashCode();
        result = 31 * result + Float.hashCode(textSize);
        result = 31 * result + textColor;
        result = 31 * result + Long.hashCode(nodeId);
        return result;
    }

    @Override
    public String toString() {
        return "GlyphEntry{" +
                "puaCodepoint=U+" + Integer.toHexString(puaCodepoint).toUpperCase() +
                ", screenBounds=" + screenBounds +
                ", textSize=" + textSize +
                ", textColor=0x" + Integer.toHexString(textColor).toUpperCase() +
                ", nodeId=" + nodeId +
                '}';
    }
}
