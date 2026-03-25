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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Represents a series of touch points recorded during a gesture swipe.
 *
 * <p>A {@code GesturePath} captures raw pointer events and provides geometric
 * operations — resampling to a fixed number of equidistant points and normalizing
 * to a unit bounding box — that make paths comparable regardless of size, position,
 * or input speed.
 *
 * <h3>Typical usage</h3>
 * <pre>{@code
 * GesturePath path = new GesturePath();
 * // ... feed touch events from the system:
 * path.addPoint(x, y, event.getEventTime());
 *
 * // Prepare for matching:
 * GesturePath resampled = path.resample(64);
 * GesturePath normalized = resampled.normalize();
 * }</pre>
 *
 * <p>This class is <em>not</em> thread-safe; external synchronization is required
 * if multiple threads share a single instance.
 */
public final class GesturePath {

    /** Default resample count used by {@link GestureDecoder}. */
    public static final int DEFAULT_RESAMPLE_COUNT = 64;

    private final List<Point> points = new ArrayList<>();

    // -------------------------------------------------------------------------
    // Mutation
    // -------------------------------------------------------------------------

    /**
     * Appends a touch point to the end of this path.
     *
     * @param x         x coordinate in pixels (or any consistent unit)
     * @param y         y coordinate in pixels (or any consistent unit)
     * @param timestamp event timestamp in milliseconds, e.g. {@code System.currentTimeMillis()}
     */
    public void addPoint(float x, float y, long timestamp) {
        points.add(new Point(x, y, timestamp, 1.0f));
    }

    /**
     * Appends a touch point with pressure to the end of this path.
     *
     * @param x         x coordinate in pixels (or any consistent unit)
     * @param y         y coordinate in pixels (or any consistent unit)
     * @param timestamp event timestamp in milliseconds
     * @param pressure  normalized touch pressure in [0, 1]; 1.0 if not available
     */
    public void addPoint(float x, float y, long timestamp, float pressure) {
        points.add(new Point(x, y, timestamp, pressure));
    }

    // -------------------------------------------------------------------------
    // Geometric queries
    // -------------------------------------------------------------------------

    /**
     * Returns the total arc length of this path, i.e. the sum of Euclidean
     * distances between consecutive points.
     *
     * <p>Alias for {@link #totalLength()} — both names are provided so callers
     * can use whichever naming convention they prefer.
     *
     * @return total path length, or {@code 0} when fewer than two points exist
     */
    public float getLength() {
        return totalLength();
    }

    /**
     * Returns the total arc length of this path.
     *
     * <p>Equivalent to {@link #getLength()}.
     *
     * @return total path length, or {@code 0} when fewer than two points exist
     */
    public float totalLength() {
        if (points.size() < 2) {
            return 0f;
        }
        float total = 0f;
        for (int i = 1; i < points.size(); i++) {
            total += points.get(i - 1).distanceTo(points.get(i));
        }
        return total;
    }

    /**
     * Returns the axis-aligned bounding box of all points in this path.
     *
     * @return a four-element array {@code [minX, minY, maxX, maxY]}, or
     *         {@code [0, 0, 0, 0]} when the path is empty
     */
    public float[] getBoundingBox() {
        if (points.isEmpty()) {
            return new float[]{0f, 0f, 0f, 0f};
        }
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        for (Point p : points) {
            if (p.x < minX) minX = p.x;
            if (p.y < minY) minY = p.y;
            if (p.x > maxX) maxX = p.x;
            if (p.y > maxY) maxY = p.y;
        }
        return new float[]{minX, minY, maxX, maxY};
    }

    /**
     * Returns an unmodifiable view of the raw point list.
     *
     * @return immutable list of {@link Point} objects in insertion order
     */
    public List<Point> getPoints() {
        return Collections.unmodifiableList(points);
    }

    /**
     * Returns the number of points currently in this path.
     */
    public int size() {
        return points.size();
    }

    // -------------------------------------------------------------------------
    // Geometric transforms — return new GesturePath instances
    // -------------------------------------------------------------------------

    /**
     * Resamples this path to exactly {@code n} equidistant points.
     *
     * <p>The resampling walks the original polyline at uniform arc-length
     * intervals so that the resulting path has the same shape but a fixed
     * point count, making different-length paths directly comparable.
     *
     * <p>If the path contains fewer than two points, the method returns a copy
     * of this path with the available points repeated to fill {@code n} slots.
     *
     * @param n the desired number of points; must be &ge; 2
     * @return a new {@code GesturePath} with exactly {@code n} points
     * @throws IllegalArgumentException if {@code n < 2}
     */
    public GesturePath resample(int n) {
        if (n < 2) {
            throw new IllegalArgumentException("resample count must be >= 2, got: " + n);
        }

        // Edge case: degenerate paths
        if (points.size() < 2) {
            GesturePath result = new GesturePath();
            Point sole = points.isEmpty() ? new Point(0f, 0f, 0L) : points.get(0);
            for (int i = 0; i < n; i++) {
                result.addPoint(sole.x, sole.y, sole.timestamp, sole.pressure);
            }
            return result;
        }

        float totalLength = totalLength();
        float interval = totalLength / (n - 1);

        GesturePath result = new GesturePath();
        // Always include the first point
        Point first = points.get(0);
        result.addPoint(first.x, first.y, first.timestamp, first.pressure);

        float accumulated = 0f;
        int segIndex = 1;

        while (result.size() < n - 1 && segIndex < points.size()) {
            Point prev = points.get(segIndex - 1);
            Point curr = points.get(segIndex);
            float segLen = prev.distanceTo(curr);

            // How many whole intervals fit in [accumulated, accumulated + segLen)?
            while (accumulated + segLen >= interval && result.size() < n - 1) {
                float t = (interval - accumulated) / segLen;
                float nx = prev.x        + t * (curr.x        - prev.x);
                float ny = prev.y        + t * (curr.y        - prev.y);
                float np = prev.pressure + t * (curr.pressure - prev.pressure);
                long  nt = prev.timestamp + (long)(t * (curr.timestamp - prev.timestamp));
                result.addPoint(nx, ny, nt, np);

                // The new interpolated point becomes the new "prev" for the next interval
                prev = new Point(nx, ny, nt, np);
                segLen = prev.distanceTo(curr);
                accumulated = 0f;
            }
            accumulated += segLen;
            segIndex++;
        }

        // Guarantee exactly n points (floating-point rounding can leave us one short)
        while (result.size() < n) {
            Point last = points.get(points.size() - 1);
            result.addPoint(last.x, last.y, last.timestamp, last.pressure);
        }

        return result;
    }

    /**
     * Normalizes this path to a unit bounding box with its origin at (0, 0).
     *
     * <p>The path is first translated so that its minimum corner is at the
     * origin, then uniformly scaled so that the larger of width and height
     * equals 1.0. Aspect ratio is preserved; a perfectly horizontal or
     * vertical path will occupy only one axis of the unit square.
     *
     * <p>If all points are identical (zero bounding box) the path is returned
     * as-is (translated to origin, scale factor 1).
     *
     * <p>This is an alias for {@link #normalizeToUnitBox()}.
     *
     * @return a new normalized {@code GesturePath}
     */
    public GesturePath normalize() {
        return normalizeToUnitBox();
    }

    /**
     * Normalizes this path to a unit bounding box with its origin at (0, 0).
     *
     * <p>Equivalent to {@link #normalize()}. Both names are provided so callers
     * can use whichever naming convention they prefer.
     *
     * @return a new normalized {@code GesturePath}
     */
    public GesturePath normalizeToUnitBox() {
        float[] bb = getBoundingBox();
        float minX = bb[0], minY = bb[1], maxX = bb[2], maxY = bb[3];
        float w     = maxX - minX;
        float h     = maxY - minY;
        float scale = Math.max(w, h);
        if (scale < 1e-9f) {
            scale = 1f; // degenerate path — avoid division by zero
        }

        GesturePath result = new GesturePath();
        for (Point p : points) {
            result.addPoint((p.x - minX) / scale, (p.y - minY) / scale,
                    p.timestamp, p.pressure);
        }
        return result;
    }

    // -------------------------------------------------------------------------
    // Inner type
    // -------------------------------------------------------------------------

    /**
     * An immutable touch point with (x, y) coordinates, a timestamp, and optional pressure.
     */
    public static final class Point {

        /** Horizontal coordinate in the caller's coordinate space. */
        public final float x;

        /** Vertical coordinate in the caller's coordinate space. */
        public final float y;

        /** Event timestamp in milliseconds. */
        public final long timestamp;

        /**
         * Normalized touch pressure in [0, 1]. Use {@code 1.0f} when pressure is
         * unavailable (e.g. for synthetic template points).
         */
        public final float pressure;

        /**
         * Constructs a new point with default pressure (1.0).
         *
         * @param x         x coordinate
         * @param y         y coordinate
         * @param timestamp event timestamp in milliseconds
         */
        public Point(float x, float y, long timestamp) {
            this(x, y, timestamp, 1.0f);
        }

        /**
         * Constructs a new point with explicit pressure.
         *
         * @param x         x coordinate
         * @param y         y coordinate
         * @param timestamp event timestamp in milliseconds
         * @param pressure  normalized pressure in [0, 1]
         */
        public Point(float x, float y, long timestamp, float pressure) {
            this.x         = x;
            this.y         = y;
            this.timestamp = timestamp;
            this.pressure  = pressure;
        }

        /**
         * Returns the Euclidean distance from this point to {@code other}.
         *
         * @param other the target point; must not be {@code null}
         * @return distance &ge; 0
         */
        public float distanceTo(Point other) {
            float dx = other.x - this.x;
            float dy = other.y - this.y;
            return (float) Math.sqrt(dx * dx + dy * dy);
        }

        @Override
        public String toString() {
            return String.format("Point(%.3f, %.3f, t=%d, p=%.2f)", x, y, timestamp, pressure);
        }
    }

    @Override
    public String toString() {
        return String.format("GesturePath[%d points, length=%.2f]", points.size(), getLength());
    }
}
