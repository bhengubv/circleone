/*
 * Copyright (C) 2026 The Other Bhengu (Pty) Ltd t/a The Geek.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package helium314.keyboard.latin.circleone;

import android.content.ClipDescription;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.inputmethodservice.InputMethodService;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputContentInfo;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.StaggeredGridLayoutManager;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * A self-contained GIF search panel for the CircleOne keyboard.
 *
 * Layout: Search bar at top, staggered grid of GIF thumbnails below.
 * Tap a GIF → downloads full-size → commits via InputConnection.commitContent().
 *
 * This view is inflated programmatically and added to the keyboard's
 * emoji/media strip area when the user taps the GIF toolbar button.
 */
public class GifSearchView extends LinearLayout {

    private static final String TAG = "GifSearchView";
    private static final String GIF_CACHE_DIR = "gif_cache";

    private EditText mSearchInput;
    private RecyclerView mResultsGrid;
    private ProgressBar mLoadingBar;
    private TextView mStatusText;

    private TenorApiClient mApiClient;
    private GifAdapter mAdapter;
    private InputMethodService mService;
    private final Handler mSearchDebounce = new Handler(Looper.getMainLooper());
    private final ExecutorService mDownloadExecutor = Executors.newFixedThreadPool(3);

    private GifDismissListener mDismissListener;

    public interface GifDismissListener {
        void onGifPanelDismiss();
    }

    public GifSearchView(Context context) {
        super(context);
        init(context);
    }

    public GifSearchView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private void init(Context context) {
        setOrientation(VERTICAL);

        // Search bar
        LinearLayout searchBar = new LinearLayout(context);
        searchBar.setOrientation(HORIZONTAL);
        searchBar.setPadding(16, 8, 16, 8);

        mSearchInput = new EditText(context);
        mSearchInput.setHint("Search GIFs...");
        mSearchInput.setSingleLine(true);
        mSearchInput.setTextSize(14);
        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(
                0, LayoutParams.WRAP_CONTENT, 1f);
        mSearchInput.setLayoutParams(inputParams);

        // Close button
        ImageView closeBtn = new ImageView(context);
        closeBtn.setImageResource(android.R.drawable.ic_menu_close_clear_cancel);
        closeBtn.setPadding(16, 16, 16, 16);
        closeBtn.setOnClickListener(v -> {
            if (mDismissListener != null) mDismissListener.onGifPanelDismiss();
        });

        searchBar.addView(mSearchInput);
        searchBar.addView(closeBtn);
        addView(searchBar);

        // Loading indicator
        mLoadingBar = new ProgressBar(context);
        mLoadingBar.setVisibility(GONE);
        addView(mLoadingBar, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));

        // Status text (errors, empty state)
        mStatusText = new TextView(context);
        mStatusText.setPadding(16, 32, 16, 32);
        mStatusText.setTextAlignment(TEXT_ALIGNMENT_CENTER);
        mStatusText.setVisibility(GONE);
        addView(mStatusText);

        // Results grid
        mResultsGrid = new RecyclerView(context);
        mResultsGrid.setLayoutManager(new StaggeredGridLayoutManager(3,
                StaggeredGridLayoutManager.VERTICAL));
        mAdapter = new GifAdapter();
        mResultsGrid.setAdapter(mAdapter);
        addView(mResultsGrid, new LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f));

        // Debounced search on text change
        mSearchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                mSearchDebounce.removeCallbacksAndMessages(null);
                mSearchDebounce.postDelayed(() -> {
                    String query = s.toString().trim();
                    if (query.isEmpty()) {
                        loadTrending();
                    } else {
                        performSearch(query);
                    }
                }, 400); // 400ms debounce
            }
        });

        // Tenor attribution (required by API terms)
        TextView attribution = new TextView(context);
        attribution.setText("Powered by Tenor");
        attribution.setTextSize(10);
        attribution.setPadding(16, 4, 16, 4);
        attribution.setTextAlignment(TEXT_ALIGNMENT_TEXT_END);
        addView(attribution);

        mApiClient = new TenorApiClient();
    }

    /**
     * Attach to the InputMethodService for commitContent access.
     */
    public void attach(InputMethodService service) {
        mService = service;
        // Load trending on first show
        loadTrending();
    }

    public void setDismissListener(GifDismissListener listener) {
        mDismissListener = listener;
    }

    private void loadTrending() {
        showLoading();
        mApiClient.trending(new TenorApiClient.SearchCallback() {
            @Override
            public void onResults(List<TenorApiClient.GifResult> results) {
                hideLoading();
                mAdapter.setResults(results);
                if (results.isEmpty()) showStatus("No trending GIFs available");
            }

            @Override
            public void onError(String message) {
                hideLoading();
                showStatus("Could not load GIFs");
            }
        });
    }

    private void performSearch(String query) {
        showLoading();
        mApiClient.search(query, new TenorApiClient.SearchCallback() {
            @Override
            public void onResults(List<TenorApiClient.GifResult> results) {
                hideLoading();
                mAdapter.setResults(results);
                if (results.isEmpty()) showStatus("No GIFs found for \"" + query + "\"");
            }

            @Override
            public void onError(String message) {
                hideLoading();
                showStatus("Search failed");
            }
        });
    }

    private void showLoading() {
        mLoadingBar.setVisibility(VISIBLE);
        mStatusText.setVisibility(GONE);
    }

    private void hideLoading() {
        mLoadingBar.setVisibility(GONE);
    }

    private void showStatus(String msg) {
        mStatusText.setText(msg);
        mStatusText.setVisibility(VISIBLE);
    }

    /**
     * Downloads a GIF and commits it via commitContent API.
     */
    private void commitGif(TenorApiClient.GifResult gif) {
        if (mService == null) return;

        // Register share with Tenor (API requirement)
        mApiClient.registerShare(gif.id);

        mDownloadExecutor.execute(() -> {
            try {
                // Download GIF to cache
                File cacheDir = new File(getContext().getCacheDir(), GIF_CACHE_DIR);
                if (!cacheDir.exists()) cacheDir.mkdirs();

                File gifFile = new File(cacheDir, "tenor_" + gif.id + ".gif");

                HttpURLConnection conn = (HttpURLConnection) new URL(gif.fullUrl).openConnection();
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(15000);

                try (InputStream is = conn.getInputStream();
                     FileOutputStream fos = new FileOutputStream(gifFile)) {
                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    while ((bytesRead = is.read(buffer)) != -1) {
                        fos.write(buffer, 0, bytesRead);
                    }
                } finally {
                    conn.disconnect();
                }

                // Commit via InputConnection
                new Handler(Looper.getMainLooper()).post(() -> {
                    try {
                        commitGifFile(gifFile, gif.id);
                    } catch (Exception e) {
                        Log.e(TAG, "Failed to commit GIF", e);
                    }
                });

            } catch (Exception e) {
                Log.e(TAG, "Failed to download GIF", e);
            }
        });
    }

    private void commitGifFile(File gifFile, String gifId) {
        if (mService == null) return;

        InputConnection ic = mService.getCurrentInputConnection();
        EditorInfo editorInfo = mService.getCurrentInputEditorInfo();
        if (ic == null || editorInfo == null) return;

        String authority = getContext().getPackageName() + ".circleone.fileprovider";
        Uri contentUri = FileProvider.getUriForFile(getContext(), authority, gifFile);

        // Check if editor supports GIF
        String mimeType = "image/gif";
        boolean supportsGif = false;
        if (editorInfo.contentMimeTypes != null) {
            for (String mime : editorInfo.contentMimeTypes) {
                if (mime.equals(mimeType) || mime.equals("image/*") || mime.equals("*/*")) {
                    supportsGif = true;
                    break;
                }
            }
        }

        if (supportsGif && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N_MR1) {
            // Use commitContent API
            ClipDescription description = new ClipDescription("GIF", new String[]{mimeType});
            InputContentInfo contentInfo = new InputContentInfo(contentUri, description);
            ic.commitContent(contentInfo,
                    InputConnection.INPUT_CONTENT_GRANT_READ_URI_PERMISSION, null);
        } else {
            // Fallback: insert GIF URL as text
            ic.commitText(gifFile.getName(), 1);
        }

        // Dismiss GIF panel after sending
        if (mDismissListener != null) {
            mDismissListener.onGifPanelDismiss();
        }
    }

    public void cleanup() {
        mSearchDebounce.removeCallbacksAndMessages(null);
        mDownloadExecutor.shutdownNow();
        if (mApiClient != null) mApiClient.shutdown();
    }

    // ========================================================================
    // RecyclerView Adapter for GIF grid
    // ========================================================================

    private class GifAdapter extends RecyclerView.Adapter<GifAdapter.GifViewHolder> {

        private final List<TenorApiClient.GifResult> mResults = new ArrayList<>();
        private final ExecutorService mImageLoader = Executors.newFixedThreadPool(4);
        private final Handler mHandler = new Handler(Looper.getMainLooper());

        void setResults(List<TenorApiClient.GifResult> results) {
            mResults.clear();
            mResults.addAll(results);
            notifyDataSetChanged();
            mStatusText.setVisibility(results.isEmpty() ? VISIBLE : GONE);
        }

        @NonNull
        @Override
        public GifViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            ImageView imageView = new ImageView(parent.getContext());
            imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
            imageView.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 200));
            imageView.setPadding(2, 2, 2, 2);
            return new GifViewHolder(imageView);
        }

        @Override
        public void onBindViewHolder(@NonNull GifViewHolder holder, int position) {
            TenorApiClient.GifResult gif = mResults.get(position);
            ImageView imageView = (ImageView) holder.itemView;

            // Set aspect ratio from preview dimensions
            if (gif.previewWidth > 0 && gif.previewHeight > 0) {
                int height = (int) (200f * gif.previewHeight / gif.previewWidth);
                imageView.getLayoutParams().height = Math.max(100, Math.min(height, 400));
            }

            // Placeholder
            imageView.setImageResource(android.R.drawable.ic_menu_gallery);

            // Load preview image async
            mImageLoader.execute(() -> {
                try {
                    HttpURLConnection conn = (HttpURLConnection)
                            new URL(gif.previewUrl).openConnection();
                    conn.setConnectTimeout(5000);
                    InputStream is = conn.getInputStream();
                    Bitmap bitmap = BitmapFactory.decodeStream(is);
                    is.close();
                    conn.disconnect();

                    if (bitmap != null) {
                        mHandler.post(() -> imageView.setImageBitmap(bitmap));
                    }
                } catch (Exception e) {
                    // Keep placeholder
                }
            });

            // Tap to send GIF
            imageView.setOnClickListener(v -> commitGif(gif));
        }

        @Override
        public int getItemCount() {
            return mResults.size();
        }

        class GifViewHolder extends RecyclerView.ViewHolder {
            GifViewHolder(View itemView) {
                super(itemView);
            }
        }
    }
}
