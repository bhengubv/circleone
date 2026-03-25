/*
 * Copyright (C) 2026 The Other Bhengu (Pty) Ltd t/a The Geek.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * CircleOne — On-Device Neural Language Model Interface
 * Phase 3 (AI Layer) — Stub / Interface only.
 * Actual ONNX Runtime inference will be wired in when the trained model
 * is available. Until then, DummyNeuralProvider returns empty results so
 * the keyboard remains fully functional with dictionary-based predictions.
 *
 * See docs/ON_DEVICE_LM_SPEC.md for full architecture details.
 */

package helium314.keyboard.latin.circleone;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * Contract for the CircleOne on-device neural language model.
 *
 * <p>All methods are expected to be called from a background thread.
 * Implementations must not block the main (UI) thread.
 *
 * <p>The keyboard should always check {@link #isModelLoaded()} before calling
 * inference methods. If the model is not loaded, callers must fall back to the
 * existing dictionary-based suggestion pipeline.
 *
 * <p>Lifecycle:
 * <pre>
 *   loadModel(context)      // call once, e.g. in LatinIME.onCreate()
 *       │
 *       ▼
 *   isModelLoaded() == true
 *       │
 *       ▼  (on every keypress / commit)
 *   predict() / complete() / correct() / smartReply()
 *       │
 *       ▼
 *   unloadModel()           // call in LatinIME.onDestroy() to free RAM
 * </pre>
 */
public interface NeuralPredictionProvider {

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    /**
     * Load the ONNX model and tokenizer from the app's assets directory.
     *
     * <p>This method may block for up to 3 seconds on a cold start. It must be
     * called on a background thread. After this method returns, {@link #isModelLoaded()}
     * should return {@code true} if loading succeeded.
     *
     * <p>If loading fails (e.g., insufficient memory, corrupt asset), the
     * implementation must silently degrade — never throw an unchecked exception
     * that would crash the keyboard service.
     *
     * @param context Application or service context used to open assets.
     */
    void loadModel(@NonNull Context context);

    /**
     * Release all native resources held by the ONNX Runtime session.
     *
     * <p>After this call, {@link #isModelLoaded()} returns {@code false} and all
     * inference methods return empty results. Safe to call multiple times.
     */
    void unloadModel();

    /**
     * Returns {@code true} if the model has been successfully loaded and is
     * ready for inference. Returns {@code false} before {@link #loadModel} is
     * called, while loading is still in progress, or if loading failed.
     */
    boolean isModelLoaded();

    /**
     * Returns the version string embedded in the ONNX model metadata, or
     * {@code "unloaded"} if the model is not currently loaded.
     *
     * <p>Format: {@code "major.minor.patch"}, e.g., {@code "1.0.0"}.
     */
    @NonNull
    String getModelVersion();

    // -------------------------------------------------------------------------
    // Inference — Next-Word Prediction
    // -------------------------------------------------------------------------

    /**
     * Predict the most likely next words given the current typing context.
     *
     * <p>This is the hot path called after every committed word or space. The
     * implementation must complete within 10 ms on a Snapdragon 695.
     *
     * <p>The {@code context} string should contain the text preceding the cursor,
     * truncated to a reasonable length (the implementation may further truncate
     * to its context window). Pass an empty string if there is no prior context.
     *
     * @param context        Text preceding the cursor (up to ~500 chars; will be
     *                       tokenized and truncated to the model's context window).
     * @param numSuggestions Number of top predictions to return (typically 3–5).
     * @return Ordered list of {@link PredictionResult} objects, highest confidence
     *         first. Returns an empty list if the model is not loaded or inference
     *         fails. Never returns {@code null}.
     */
    @NonNull
    List<PredictionResult> predict(@NonNull String context, int numSuggestions);

    // -------------------------------------------------------------------------
    // Inference — Sentence / Phrase Completion
    // -------------------------------------------------------------------------

    /**
     * Complete a partial sentence or phrase started by the user.
     *
     * <p>Used when the user long-presses a suggestion strip item or invokes the
     * sentence-completion gesture. The model continues generating tokens from
     * {@code prefix} until it produces an end-of-sequence token or reaches the
     * maximum completion length.
     *
     * <p>This operation uses beam search (beam=3) and may take up to 50 ms.
     * Call on a background thread; post results to the UI thread via a handler.
     *
     * @param prefix Text typed so far, forming the beginning of the sentence.
     *               Must not be null or empty.
     * @return Up to 3 completion candidates as {@link PredictionResult} objects,
     *         where {@link PredictionResult#getWord()} contains the full completed
     *         string (prefix + generated continuation). Returns an empty list if
     *         the model is not loaded. Never returns {@code null}.
     */
    @NonNull
    List<PredictionResult> complete(@NonNull String prefix);

    // -------------------------------------------------------------------------
    // Inference — Spelling / Grammar Correction
    // -------------------------------------------------------------------------

    /**
     * Suggest a corrected form of {@code word} given its surrounding context.
     *
     * <p>Handles two correction modes:
     * <ul>
     *   <li><b>Spelling correction</b>: called immediately after a word is
     *       committed (space/punctuation detected). The model corrects typos
     *       and misspellings.</li>
     *   <li><b>Grammar correction</b>: called after a full sentence is committed
     *       (period/question/exclamation detected). Pass the entire sentence as
     *       {@code word} and the paragraph context as {@code context}.</li>
     * </ul>
     *
     * <p>The caller should apply the correction only if the returned result has
     * confidence ≥ 0.75 and differs from the original input.
     *
     * @param word    The word or sentence to correct.
     * @param context Surrounding text (preceding sentence or paragraph) that
     *                helps the model disambiguate corrections.
     * @return A single {@link PredictionResult} containing the corrected text
     *         and confidence score. If no correction is needed or the model is
     *         not loaded, returns a result with the original {@code word} and
     *         confidence 1.0. Never returns {@code null}.
     */
    @NonNull
    PredictionResult correct(@NonNull String word, @NonNull String context);

    // -------------------------------------------------------------------------
    // Inference — Smart Reply
    // -------------------------------------------------------------------------

    /**
     * Suggest short reply messages in response to an incoming message.
     *
     * <p>Called when the keyboard is opened in a messaging app and an incoming
     * message is available from the editor's hint or accessibility text. Generates
     * up to 3 contextually appropriate short replies.
     *
     * <p>This operation uses beam search and may take up to 60 ms. Always call
     * on a background thread.
     *
     * @param message The incoming message text to reply to. Must not be null.
     * @return Up to 3 reply candidates as {@link PredictionResult} objects,
     *         where {@link PredictionResult#getWord()} contains the full reply
     *         string. Returns an empty list if the model is not loaded or the
     *         message is empty. Never returns {@code null}.
     */
    @NonNull
    List<PredictionResult> smartReply(@NonNull String message);

    // =========================================================================
    // PredictionResult — Value Object
    // =========================================================================

    /**
     * Immutable value object representing a single prediction from the neural
     * language model.
     *
     * <p>{@code word} contains the predicted text (next word, completion, correction,
     * or reply). {@code confidence} is a probability in [0.0, 1.0]. {@code language}
     * is the BCP-47 language code the model believes the output is in
     * (e.g., {@code "zu"}, {@code "af"}, {@code "en-ZA"}).
     */
    final class PredictionResult {

        private final String word;
        private final float confidence;
        private final String language;

        /**
         * @param word       Predicted text. Must not be null or empty.
         * @param confidence Probability score in [0.0, 1.0].
         * @param language   BCP-47 language tag for the predicted text.
         */
        public PredictionResult(@NonNull String word, float confidence, @NonNull String language) {
            if (word.isEmpty()) {
                throw new IllegalArgumentException("PredictionResult word must not be empty");
            }
            if (confidence < 0f || confidence > 1f) {
                throw new IllegalArgumentException("confidence must be in [0.0, 1.0], got: " + confidence);
            }
            this.word = word;
            this.confidence = confidence;
            this.language = language;
        }

        /**
         * The predicted word, phrase, completion, correction, or reply text.
         */
        @NonNull
        public String getWord() {
            return word;
        }

        /**
         * Confidence score in [0.0, 1.0]. Higher is more confident.
         * Callers should use 0.75 as a threshold for auto-applying corrections.
         */
        public float getConfidence() {
            return confidence;
        }

        /**
         * BCP-47 language tag of the predicted output.
         * Examples: {@code "zu"} (isiZulu), {@code "af"} (Afrikaans), {@code "en-ZA"}.
         */
        @NonNull
        public String getLanguage() {
            return language;
        }

        @Override
        public String toString() {
            return "PredictionResult{"
                    + "word='" + word + '\''
                    + ", confidence=" + confidence
                    + ", language='" + language + '\''
                    + '}';
        }
    }

    // =========================================================================
    // DummyNeuralProvider — Placeholder implementation
    // =========================================================================

    /**
     * No-op implementation of {@link NeuralPredictionProvider}.
     *
     * <p>This is the active implementation until the trained ONNX model is
     * available. All inference methods return empty / identity results. The
     * keyboard falls back entirely to its existing dictionary-based suggestion
     * pipeline when this provider is active.
     *
     * <p>Usage — wire into LatinIME during the AI Layer build phase:
     * <pre>
     *   private NeuralPredictionProvider mNeuralProvider = new DummyNeuralProvider();
     * </pre>
     *
     * <p>When the real model is ready, replace the instantiation with:
     * <pre>
     *   private NeuralPredictionProvider mNeuralProvider = new OnnxNeuralProvider();
     * </pre>
     *
     * No other code changes are required because all call sites use the
     * {@link NeuralPredictionProvider} interface.
     */
    final class DummyNeuralProvider implements NeuralPredictionProvider {

        private static final String VERSION_UNLOADED = "unloaded";
        private static final String DEFAULT_LANGUAGE = "en-ZA";

        @Override
        public void loadModel(@NonNull Context context) {
            // No-op: model not yet available.
        }

        @Override
        public void unloadModel() {
            // No-op.
        }

        @Override
        public boolean isModelLoaded() {
            return false;
        }

        @Override
        @NonNull
        public String getModelVersion() {
            return VERSION_UNLOADED;
        }

        /**
         * Returns an empty list. The keyboard will use dictionary predictions.
         */
        @Override
        @NonNull
        public List<PredictionResult> predict(@NonNull String context, int numSuggestions) {
            return Collections.emptyList();
        }

        /**
         * Returns an empty list. No sentence completion is shown.
         */
        @Override
        @NonNull
        public List<PredictionResult> complete(@NonNull String prefix) {
            return Collections.emptyList();
        }

        /**
         * Returns the original word unchanged with full confidence.
         * The keyboard will not apply any neural correction.
         */
        @Override
        @NonNull
        public PredictionResult correct(@NonNull String word, @NonNull String context) {
            return new PredictionResult(word, 1.0f, DEFAULT_LANGUAGE);
        }

        /**
         * Returns an empty list. No smart reply chips are shown.
         */
        @Override
        @NonNull
        public List<PredictionResult> smartReply(@NonNull String message) {
            return Collections.emptyList();
        }
    }
}
