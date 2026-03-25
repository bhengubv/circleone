/*
 * Copyright (C) 2026 The Other Bhengu (Pty) Ltd t/a The Geek.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package helium314.keyboard.latin.circleone;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Lightweight Tenor GIF API v2 client for inline GIF search.
 * Uses the free Tenor API with a CircleOne API key.
 *
 * Results are returned as a list of {@link GifResult} objects containing
 * URLs for preview (tinygif) and full-size (mediumgif) GIFs.
 */
public class TenorApiClient {

    private static final String TAG = "TenorApiClient";
    private static final String BASE_URL = "https://tenor.googleapis.com/v2";

    // Free Tenor API key — register at https://developers.google.com/tenor
    // This is a public client key scoped to CircleOne keyboard searches only
    private static final String API_KEY = "AIzaSyCircleOneTenorKeyPlaceholder";

    private static final String CLIENT_KEY = "circleone_keyboard";
    private static final int RESULT_LIMIT = 20;

    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());

    public interface SearchCallback {
        void onResults(List<GifResult> results);
        void onError(String message);
    }

    public static class GifResult {
        public final String id;
        public final String previewUrl;  // tinygif — for grid thumbnails
        public final String fullUrl;     // mediumgif — for sending
        public final int previewWidth;
        public final int previewHeight;

        public GifResult(String id, String previewUrl, String fullUrl,
                         int previewWidth, int previewHeight) {
            this.id = id;
            this.previewUrl = previewUrl;
            this.fullUrl = fullUrl;
            this.previewWidth = previewWidth;
            this.previewHeight = previewHeight;
        }
    }

    /**
     * Search for GIFs by query string.
     */
    public void search(String query, SearchCallback callback) {
        mExecutor.execute(() -> {
            try {
                String encoded = URLEncoder.encode(query, "UTF-8");
                String url = BASE_URL + "/search"
                        + "?q=" + encoded
                        + "&key=" + API_KEY
                        + "&client_key=" + CLIENT_KEY
                        + "&media_filter=tinygif,mediumgif"
                        + "&limit=" + RESULT_LIMIT
                        + "&locale=en_ZA";

                List<GifResult> results = executeRequest(url);
                mMainHandler.post(() -> callback.onResults(results));
            } catch (Exception e) {
                Log.e(TAG, "Search failed", e);
                mMainHandler.post(() -> callback.onError(e.getMessage()));
            }
        });
    }

    /**
     * Fetch trending GIFs (shown before user types a query).
     */
    public void trending(SearchCallback callback) {
        mExecutor.execute(() -> {
            try {
                String url = BASE_URL + "/featured"
                        + "?key=" + API_KEY
                        + "&client_key=" + CLIENT_KEY
                        + "&media_filter=tinygif,mediumgif"
                        + "&limit=" + RESULT_LIMIT
                        + "&locale=en_ZA";

                List<GifResult> results = executeRequest(url);
                mMainHandler.post(() -> callback.onResults(results));
            } catch (Exception e) {
                Log.e(TAG, "Trending fetch failed", e);
                mMainHandler.post(() -> callback.onError(e.getMessage()));
            }
        });
    }

    /**
     * Register a share event with Tenor (required by API terms).
     */
    public void registerShare(String gifId) {
        mExecutor.execute(() -> {
            try {
                String url = BASE_URL + "/registershare"
                        + "?key=" + API_KEY
                        + "&client_key=" + CLIENT_KEY
                        + "&id=" + gifId;

                HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(5000);
                conn.getResponseCode(); // Fire and forget
                conn.disconnect();
            } catch (Exception e) {
                Log.w(TAG, "Share registration failed (non-critical)", e);
            }
        });
    }

    private List<GifResult> executeRequest(String urlStr) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(10000);

        List<GifResult> results = new ArrayList<>();

        try {
            int code = conn.getResponseCode();
            if (code != 200) {
                throw new RuntimeException("Tenor API returned " + code);
            }

            StringBuilder sb = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
            }

            JSONObject json = new JSONObject(sb.toString());
            JSONArray resultsArray = json.getJSONArray("results");

            for (int i = 0; i < resultsArray.length(); i++) {
                JSONObject item = resultsArray.getJSONObject(i);
                String id = item.getString("id");
                JSONObject mediaFormats = item.getJSONObject("media_formats");

                // Preview (tinygif)
                JSONObject tinyGif = mediaFormats.optJSONObject("tinygif");
                String previewUrl = tinyGif != null ? tinyGif.getString("url") : "";
                int previewW = tinyGif != null ? tinyGif.optInt("dims", 0) : 0;
                int previewH = previewW; // Square fallback

                if (tinyGif != null) {
                    JSONArray dims = tinyGif.optJSONArray("dims");
                    if (dims != null && dims.length() >= 2) {
                        previewW = dims.getInt(0);
                        previewH = dims.getInt(1);
                    }
                }

                // Full size (mediumgif)
                JSONObject mediumGif = mediaFormats.optJSONObject("mediumgif");
                String fullUrl = mediumGif != null ? mediumGif.getString("url") : previewUrl;

                results.add(new GifResult(id, previewUrl, fullUrl, previewW, previewH));
            }
        } finally {
            conn.disconnect();
        }

        return results;
    }

    /**
     * Shut down the executor when the keyboard is destroyed.
     */
    public void shutdown() {
        mExecutor.shutdownNow();
    }
}
