/*
 * MIT License
 *
 * Copyright (c) 2026 The Other Bhengu (Pty) Ltd t/a The Geek
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.thegeek.gesture;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Describes the physical position and size of every alphabetic key on a keyboard.
 *
 * <p>A {@code KeyboardLayout} is the geometric contract between the gesture decoder
 * and the on-screen keyboard. Each key is stored as a {@link KeyRect} that records
 * the key center and dimensions in pixel coordinates relative to the top-left corner
 * of the keyboard view.
 *
 * <h3>Creating a layout</h3>
 * <p>For standard QWERTY use the factory method:
 * <pre>{@code
 * KeyboardLayout layout = KeyboardLayout.qwerty(1080, 300);
 * }</pre>
 *
 * <p>For custom or non-Latin layouts, construct manually:
 * <pre>{@code
 * KeyboardLayout layout = new KeyboardLayout(800, 240);
 * layout.addKey('q', 40f, 40f, 80f, 80f);
 * // ...
 * }</pre>
 *
 * <p>Instances are <em>not</em> thread-safe during construction; once all keys have
 * been added (and no further mutations will occur) the instance may be shared safely
 * across threads.
 */
public final class KeyboardLayout {

    private final int width;
    private final int height;
    private final Map<Character, KeyRect> keys = new HashMap<>();

    /**
     * Constructs an empty layout for a keyboard of the given pixel dimensions.
     *
     * @param width  total keyboard width in pixels; must be &gt; 0
     * @param height total keyboard height in pixels; must be &gt; 0
     * @throws IllegalArgumentException if either dimension is non-positive
     */
    public KeyboardLayout(int width, int height) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException(
                    "Keyboard dimensions must be positive, got: " + width + "x" + height);
        }
        this.width  = width;
        this.height = height;
    }

    // -------------------------------------------------------------------------
    // Layout construction
    // -------------------------------------------------------------------------

    /**
     * Registers a key for the given letter.
     *
     * <p>If a key for {@code letter} already exists it is silently replaced.
     * Both upper- and lower-case forms of the character resolve to the same
     * key — look-ups are always performed case-insensitively.
     *
     * @param letter    the alphabetic character this key represents
     * @param centerX   x coordinate of the key center in pixels
     * @param centerY   y coordinate of the key center in pixels
     * @param keyWidth  key width in pixels; must be &gt; 0
     * @param keyHeight key height in pixels; must be &gt; 0
     */
    public void addKey(char letter, float centerX, float centerY,
                       float keyWidth, float keyHeight) {
        char lower = Character.toLowerCase(letter);
        keys.put(lower, new KeyRect(lower, centerX, centerY, keyWidth, keyHeight));
    }

    // -------------------------------------------------------------------------
    // Queries
    // -------------------------------------------------------------------------

    /**
     * Returns the center point of the key for the given letter.
     *
     * @param letter the letter to look up (case-insensitive)
     * @return the key center as a {@link GesturePath.Point}, or {@code null} if
     *         no key is registered for that letter
     */
    public GesturePath.Point getKeyCenter(char letter) {
        KeyRect rect = keys.get(Character.toLowerCase(letter));
        if (rect == null) return null;
        return new GesturePath.Point(rect.centerX, rect.centerY, 0L);
    }

    /**
     * Returns the center coordinates of the key for the given letter as a two-element
     * {@code float[]} array {@code [x, y]}, or {@code null} if the letter is not in this layout.
     *
     * <p>This overload is provided for callers that need raw coordinates without allocating a
     * {@link GesturePath.Point} wrapper (e.g. {@code TemplateCache}).
     *
     * @param letter the letter to look up (case-insensitive)
     * @return {@code float[]{centerX, centerY}}, or {@code null}
     */
    public float[] getKeyCentre(char letter) {
        KeyRect rect = keys.get(Character.toLowerCase(letter));
        if (rect == null) return null;
        return new float[]{rect.centerX, rect.centerY};
    }

    /**
     * Returns the {@link KeyRect} descriptor for the given letter.
     *
     * @param letter the letter to look up (case-insensitive)
     * @return the {@code KeyRect}, or {@code null} if not present
     */
    public KeyRect getKeyRect(char letter) {
        return keys.get(Character.toLowerCase(letter));
    }

    /**
     * Returns the letter whose key center is nearest to the given point.
     *
     * <p>Distances are computed using Euclidean distance to key centers.
     * Ties are broken arbitrarily.
     *
     * @param x x coordinate in pixels
     * @param y y coordinate in pixels
     * @return the closest character, or {@code '\0'} if no keys have been added
     */
    public char getClosestKey(float x, float y) {
        char best = '\0';
        float bestDist = Float.MAX_VALUE;
        for (KeyRect rect : keys.values()) {
            float dx = rect.centerX - x;
            float dy = rect.centerY - y;
            float dist = dx * dx + dy * dy; // squared — avoids sqrt for comparison
            if (dist < bestDist) {
                bestDist = dist;
                best = rect.letter;
            }
        }
        return best;
    }

    /**
     * Returns {@code true} if this layout contains a key for the given letter.
     *
     * @param letter the letter to test (case-insensitive)
     */
    public boolean hasKey(char letter) {
        return keys.containsKey(Character.toLowerCase(letter));
    }

    /**
     * Returns an unmodifiable view of all registered keys, keyed by lower-case character.
     */
    public Map<Character, KeyRect> getAllKeys() {
        return Collections.unmodifiableMap(keys);
    }

    /** Returns the keyboard width in pixels as supplied at construction time. */
    public int getWidth()  { return width; }

    /** Returns the keyboard height in pixels as supplied at construction time. */
    public int getHeight() { return height; }

    /**
     * Returns a representative key width in pixels for this layout.
     *
     * <p>When keys have been added, this returns the average width across all registered keys.
     * For a layout with no keys, it falls back to {@code width / 10f} (the QWERTY default).
     * This value is used by {@code TemplateCache} to scale dwell offsets for repeated keys.
     *
     * @return representative key width in pixels; always &gt; 0
     */
    public float getKeyWidth() {
        if (keys.isEmpty()) return width / 10f;
        float total = 0f;
        for (KeyRect rect : keys.values()) {
            total += rect.keyWidth;
        }
        return total / keys.size();
    }

    // -------------------------------------------------------------------------
    // Factory: QWERTY
    // -------------------------------------------------------------------------

    /**
     * Creates a standard QWERTY layout scaled proportionally to the given dimensions.
     *
     * <p>Key positions follow the ISO/ANSI QWERTY staggered-row layout:
     * <ul>
     *   <li>Row 1 (top): Q W E R T Y U I O P — 10 keys, no stagger</li>
     *   <li>Row 2 (home): A S D F G H J K L — 9 keys, staggered ~5 % right</li>
     *   <li>Row 3 (bottom): Z X C V B N M — 7 keys, staggered ~10 % right</li>
     * </ul>
     *
     * <p>The factory does not include number keys, shift, delete, spacebar, or
     * special-character keys — only the 26 alphabetic keys needed for gesture decoding.
     *
     * @param width  keyboard view width in pixels; must be &gt; 0
     * @param height keyboard view height in pixels; must be &gt; 0
     * @return a fully populated QWERTY {@code KeyboardLayout}
     */
    public static KeyboardLayout qwerty(int width, int height) {
        KeyboardLayout layout = new KeyboardLayout(width, height);

        // Three alphabetic rows occupy the top 80 % of the keyboard height.
        // The bottom 20 % is reserved for the spacebar row.
        float usableHeight = height * 0.80f;

        // Divide usable height into 3 equal rows.
        float rowHeight = usableHeight / 3f;
        float keyHeight = rowHeight * 0.90f; // 10 % gap between rows

        // Row vertical centers
        float row1Y = rowHeight * 0.5f;
        float row2Y = rowHeight * 1.5f;
        float row3Y = rowHeight * 2.5f;

        // ------------------------------------------------------------------
        // Row 1: Q W E R T Y U I O P  (10 keys)
        // ------------------------------------------------------------------
        char[] row1 = {'q', 'w', 'e', 'r', 't', 'y', 'u', 'i', 'o', 'p'};
        int   nCols1   = row1.length;
        float keyW1    = (float) width / nCols1;
        float halfKeyW1 = keyW1 * 0.90f;

        for (int i = 0; i < nCols1; i++) {
            float cx = keyW1 * i + keyW1 * 0.5f;
            layout.addKey(row1[i], cx, row1Y, halfKeyW1, keyHeight);
        }

        // ------------------------------------------------------------------
        // Row 2: A S D F G H J K L  (9 keys, staggered 5 % of keyboard width)
        // ------------------------------------------------------------------
        char[] row2   = {'a', 's', 'd', 'f', 'g', 'h', 'j', 'k', 'l'};
        int   nCols2  = row2.length;
        float stagger2 = width * 0.05f;
        float keyW2   = (float) width / 10f; // same key width as row 1 (10-unit grid)
        float halfKeyW2 = keyW2 * 0.90f;

        for (int i = 0; i < nCols2; i++) {
            float cx = stagger2 + keyW2 * i + keyW2 * 0.5f;
            layout.addKey(row2[i], cx, row2Y, halfKeyW2, keyHeight);
        }

        // ------------------------------------------------------------------
        // Row 3: Z X C V B N M  (7 keys, staggered 10 % of keyboard width)
        // ------------------------------------------------------------------
        char[] row3   = {'z', 'x', 'c', 'v', 'b', 'n', 'm'};
        int   nCols3  = row3.length;
        float stagger3 = width * 0.10f;
        float keyW3   = (float) width / 10f;
        float halfKeyW3 = keyW3 * 0.90f;

        for (int i = 0; i < nCols3; i++) {
            float cx = stagger3 + keyW3 * i + keyW3 * 0.5f;
            layout.addKey(row3[i], cx, row3Y, halfKeyW3, keyHeight);
        }

        return layout;
    }

    // -------------------------------------------------------------------------
    // Inner type
    // -------------------------------------------------------------------------

    /**
     * Immutable descriptor for a single key's geometry.
     */
    public static final class KeyRect {

        /** The lower-case character this key represents. */
        public final char  letter;

        /** x coordinate of the key center in pixels. */
        public final float centerX;

        /** y coordinate of the key center in pixels. */
        public final float centerY;

        /** Key width in pixels. */
        public final float keyWidth;

        /** Key height in pixels. */
        public final float keyHeight;

        /**
         * Constructs a key descriptor.
         *
         * @param letter    lower-case character
         * @param centerX   key center x in pixels
         * @param centerY   key center y in pixels
         * @param keyWidth  key width in pixels
         * @param keyHeight key height in pixels
         */
        public KeyRect(char letter, float centerX, float centerY,
                       float keyWidth, float keyHeight) {
            this.letter    = letter;
            this.centerX   = centerX;
            this.centerY   = centerY;
            this.keyWidth  = keyWidth;
            this.keyHeight = keyHeight;
        }

        /**
         * Returns the half-diagonal of this key's bounding box, useful as a
         * proximity tolerance when deciding whether a touch point "hits" this key.
         */
        public float hitRadius() {
            return (float) Math.sqrt(keyWidth * keyWidth + keyHeight * keyHeight) / 2f;
        }

        @Override
        public String toString() {
            return String.format("KeyRect('%c', cx=%.1f, cy=%.1f, w=%.1f, h=%.1f)",
                    letter, centerX, centerY, keyWidth, keyHeight);
        }
    }

    @Override
    public String toString() {
        return String.format("KeyboardLayout[%dx%d, %d keys]", width, height, keys.size());
    }
}
