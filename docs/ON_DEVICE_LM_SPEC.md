# On-Device Language Model — Technical Specification

**Project**: CircleOne Keyboard
**Phase**: 3 — AI Layer
**Status**: Design Document (Implementation Pending)
**Author**: The Geek Network
**Date**: 2026-03-25
**Version**: 1.0.0

---

## 1. Overview

This document specifies the architecture, training strategy, inference pipeline, and integration contract for a 50-million-parameter on-device neural language model (NLM) embedded in CircleOne. The model runs entirely on-device with no network calls, targeting ~10 ms inference latency on mid-range Android hardware.

The NLM closes four Gboard feature gaps simultaneously:

| Gap | Feature | Addressed By |
|-----|---------|--------------|
| #4  | Sentence completion | `complete()` inference path |
| #6  | Better autocorrect | `correct()` inference path |
| #10 | Grammar correction | `correct()` inference path (extended context) |
| #11 | Smart reply | `smartReply()` inference path |

Rather than building four separate subsystems, a single shared transformer backbone handles all four tasks via task-specific prompt prefixes and output heads.

---

## 2. Supported Languages

The model must support all 11 South African official languages:

| Code | Language        | Script | Approx. Speakers (SA) |
|------|----------------|--------|-----------------------|
| zul  | isiZulu         | Latin  | 12.1 M |
| xho  | isiXhosa        | Latin  | 8.2 M |
| afr  | Afrikaans       | Latin  | 6.9 M |
| sep  | Sepedi          | Latin  | 5.6 M |
| tsn  | Setswana        | Latin  | 4.6 M |
| sot  | Sesotho         | Latin  | 3.9 M |
| tso  | Xitsonga        | Latin  | 2.7 M |
| ssw  | siSwati         | Latin  | 1.3 M |
| nbl  | isiNdebele      | Latin  | 1.1 M |
| ven  | Tshivenda       | Latin  | 1.2 M |
| eng  | English (SA)    | Latin  | (lingua franca) |

All 11 languages use the Latin script, which significantly simplifies tokenizer design. SA English is included as the dominant code-switching partner for all Bantu languages.

---

## 3. Model Architecture

### 3.1 High-Level Configuration

| Parameter | Value | Rationale |
|-----------|-------|-----------|
| Architecture | Decoder-only Transformer | Standard for autoregressive next-token prediction |
| Parameter count | ~50 M | Fits in ~200 MB INT8, runs within 500 MB RAM budget |
| Layers (depth) | 12 | Standard for this parameter scale |
| Hidden dimension | 512 | Balanced capacity vs. speed |
| Attention heads | 8 | 64 dimensions per head |
| FFN dimension | 2048 | 4× hidden, standard multiplier |
| Vocabulary size | 32,000 | Covers all 11 SA languages + code-switching |
| Context window | 128 tokens | Sufficient for keyboard context; short = fast |
| Positional encoding | Rotary (RoPE) | Better generalisation at variable sequence lengths |
| Activation | SiLU (Swish) | Slightly better than GELU at this scale |
| Normalization | RMSNorm (pre-norm) | More stable than LayerNorm; fewer parameters |
| Attention type | Multi-head self-attention | No sliding window needed at 128-token context |
| KV cache | Enabled | Reuse across incremental decoding steps |

### 3.2 Multi-Task Output Heads

The backbone feeds into task-specific linear heads via prompt prefixes. The model is trained end-to-end with all heads active:

| Task | Prompt Prefix (prepended to input) | Output |
|------|-------------------------------------|--------|
| Next-word prediction | `[PRED]` | Top-K token logits |
| Sentence completion | `[COMP]` | Continuation tokens until `[EOS]` |
| Autocorrect/grammar | `[CORR]` | Corrected token sequence |
| Smart reply | `[SREP]` | Reply token sequence until `[EOS]` |

Prefix tokens are special tokens added to the vocabulary. This is the "prompt-as-task-specification" pattern from T5/FLAN; it avoids separate model weights per task.

### 3.3 Language Identification Token

Every input sequence is prepended with a language-identification token (e.g., `[LANG:zul]`, `[LANG:afr]`) derived from the keyboard's active language or from the language detector (see Section 7.3). This allows the model to condition generation on the target language without separate per-language models.

---

## 4. Tokenizer Design

### 4.1 Algorithm

**SentencePiece BPE (Byte Pair Encoding)**

SentencePiece operates directly on raw Unicode text without a pre-tokenization step that assumes word boundaries. This is important for Bantu languages, which use agglutinative morphology (isiZulu: *ngiyakuthanda* = I + like + you + [emphasis]).

### 4.2 Vocabulary Specification

| Segment | Tokens | Notes |
|---------|--------|-------|
| SA Bantu subwords | ~18,000 | Derived from corpus; covers prefixes, stems, suffixes |
| Afrikaans subwords | ~4,000 | Many shared with Dutch/English |
| SA English subwords | ~5,000 | SA-specific orthography (e.g., "braai", "lekker", "eish") |
| Special tokens | 32 | Task prefixes, language IDs, padding, EOS, BOS, UNK, MASK |
| Byte fallback | 256 | Covers any unseen Unicode character byte-by-byte |
| **Total** | **~32,000** | — |

### 4.3 Training the Tokenizer

```
sentencepiece_trainer.train(
    input=["zul_corpus.txt", "xho_corpus.txt", "afr_corpus.txt", ...],
    model_prefix="circleone_bpe",
    vocab_size=32000,
    model_type="bpe",
    character_coverage=0.9999,   # captures rare chars via byte fallback
    byte_fallback=True,
    pad_id=0, bos_id=1, eos_id=2, unk_id=3,
    user_defined_symbols=["[PRED]","[COMP]","[CORR]","[SREP]",
                          "[LANG:zul]","[LANG:xho]","[LANG:afr]",
                          "[LANG:sep]","[LANG:tsn]","[LANG:sot]",
                          "[LANG:tso]","[LANG:ssw]","[LANG:nbl]",
                          "[LANG:ven]","[LANG:eng]"]
)
```

Character coverage of 0.9999 ensures click consonants (isiXhosa/isiZulu: c, q, x with diacritics) and Tshivenda tone marks are handled natively, not via byte fallback.

### 4.4 Tokenizer Artefacts

| File | Format | Size (approx) |
|------|--------|---------------|
| `circleone_bpe.model` | SentencePiece binary | ~1.5 MB |
| `circleone_bpe.vocab` | Plain text | ~2 MB |

Both artefacts are bundled in the APK under `assets/model/`.

---

## 5. Training Data

### 5.1 Corpora

| Source | Languages | Token Count (est.) | Notes |
|--------|-----------|-------------------|-------|
| NCHLT Speech Corpus (text) | zul, xho, sep, tsn, sot, ssw, nbl, ven | ~50 M | SADILAR; permissive research license |
| Leipzig Corpora Collection | afr, zul, xho, tsn, sot | ~200 M | Mix of web/news |
| CC-100 (Common Crawl) | afr, eng | ~5 B | Filter to SA variants |
| OSCAR (Common Crawl) | afr | ~2 B | High quality; deduplicated |
| Autshumato Translation Corpus | zul↔eng, xho↔eng, tsn↔eng | ~10 M | SADILAR; parallel text |
| SA Government Gazette | All 11 | ~20 M | Official multilingual text |
| PanSALB Publications | All 11 | ~5 M | Orthographic standards body |
| SMS/chat datasets (public) | eng, afr | ~30 M | Informal register; code-switching |
| Synthetic code-switching | All pairs | ~50 M | Generated via back-translation |

**Total**: ~7–8 B tokens (after deduplication and quality filtering).

SA English is heavily represented to support the code-switching reality of everyday SA typing (e.g., a Zulu speaker typing "Ngiyabonga shame neh" — mixed isiZulu + SA English).

### 5.2 Data Pipeline

1. **Download** — Scripts fetch corpora from SADILAR, Hugging Face Datasets, Leipzig.
2. **Language ID filter** — `fastText` lid.176 model; discard documents with confidence < 0.85.
3. **Quality filter** — Remove documents with > 30% punctuation, < 50 characters, or flagged as spam.
4. **Deduplication** — MinHash LSH (datasketch library) with 5-gram shingles; threshold 0.8 Jaccard.
5. **Tokenize** — Convert to SentencePiece token IDs, store as binary `.arrow` files (Hugging Face Datasets format).
6. **Split** — 99% train / 0.5% validation / 0.5% test (stratified by language).

### 5.3 Task-Specific Fine-Tuning Data

After pre-training, the model is fine-tuned on smaller, task-labelled datasets:

| Task | Data Source | Size |
|------|------------|------|
| Autocorrect | Synthetically generated typos (keyboard distance model) on clean corpora | ~5 M pairs |
| Grammar correction | CoNLL-like grammatical error annotation (SA English GEC datasets) | ~500 K pairs |
| Smart reply | SA WhatsApp/SMS conversation datasets (anonymised) | ~2 M pairs |
| Sentence completion | Masked suffix prediction from existing corpora (no new data needed) | Derived |

---

## 6. Inference Pipeline

```
User types context
        │
        ▼
┌───────────────────┐
│  Language Detector │  (fastText micro-model, ~2 MB, separate from NLM)
└───────────┬───────┘
            │ lang_code
            ▼
┌───────────────────────────────────────────────────────────┐
│  Input Assembly                                           │
│  [LANG:zul] [TASK_PREFIX] <context_tokens> [current_word]│
└───────────────────────────┬───────────────────────────────┘
                            │ token_ids[0..127]
                            ▼
┌───────────────────────────────────────────────────────────┐
│  ONNX Runtime Inference                                   │
│  • INT8 quantized model (~50 MB)                         │
│  • 12-layer transformer, 8 heads, dim=512                 │
│  • KV-cache for incremental decode                        │
│  • Target: <10 ms on Snapdragon 695 / Exynos 850         │
└───────────────────────────┬───────────────────────────────┘
                            │ logits[vocab_size]
                            ▼
┌───────────────────────────────────────────────────────────┐
│  Sampling / Decoding                                      │
│  • PRED path: top-K (K=5), temperature=0.7               │
│  • COMP/SREP path: beam search (beam=3, max_len=20)       │
│  • CORR path: greedy decode (deterministic)               │
│  • Language-constrained sampling (boost [LANG:X] tokens) │
└───────────────────────────┬───────────────────────────────┘
                            │ PredictionResult[]
                            ▼
┌───────────────────────────────────────────────────────────┐
│  NeuralPredictionProvider (Java)                          │
│  Translates results → keyboard suggestion strip / UI     │
└───────────────────────────────────────────────────────────┘
```

### 6.1 Incremental Decode & KV Cache

For `complete()` and `smartReply()`, the model generates tokens one at a time. The KV cache stores key/value tensors for the context tokens so only the new token's attention is computed per step. This reduces per-step cost from O(n²) to O(n) and is essential for staying within latency budget during multi-token generation.

### 6.2 Language-Constrained Sampling

At sampling time, tokens associated with the wrong language are penalised by multiplying their logit by a dampening factor (0.3). The language association map is pre-computed from the tokenizer vocabulary. This prevents the model from switching languages mid-output without user intent.

---

## 7. Integration Points with the Keyboard

### 7.1 Prediction Strip (Next-Word Prediction)

- Trigger: after every committed word or space.
- Input: last 128 tokens of typed text + `[PRED]` prefix.
- Output: top-3 `PredictionResult` objects displayed in the suggestion strip.
- Latency budget: 10 ms (single forward pass, no beam search).

### 7.2 Autocorrect (In-Word Correction)

- Trigger: on space/punctuation commit, if current word differs from top correction candidate.
- Input: current word + surrounding sentence context + `[CORR]` prefix.
- Output: corrected string; keyboard replaces the committed word if confidence > 0.75.
- Latency budget: 15 ms (greedy decode, typically 1–3 output tokens).

### 7.3 Sentence Completion

- Trigger: user long-presses a prediction strip suggestion or swipes up on it.
- Input: typed text so far + `[COMP]` prefix.
- Output: up to 10 completion tokens, shown as a dismissible banner above the keyboard.
- Latency budget: 50 ms (beam search, up to 10 steps).

### 7.4 Grammar Correction

- Trigger: after a full sentence is committed (period/question mark/exclamation mark detected).
- Input: the full sentence + `[CORR]` prefix.
- Output: corrected sentence displayed as an underlined suggestion the user can tap to apply.
- Latency budget: 30 ms (greedy decode, sentence length typically 10–20 tokens).

### 7.5 Smart Reply

- Trigger: when the keyboard is opened in a messaging app and the incoming message is detected in the editor hint text or accessibility text.
- Input: incoming message + `[SREP]` prefix.
- Output: 3 short reply strings shown as tappable chips above the keyboard.
- Latency budget: 60 ms (beam search, up to 12 steps per candidate).

---

## 8. Performance Requirements

### 8.1 Latency

| Operation | Budget | Measurement Point |
|-----------|--------|-------------------|
| Next-word prediction (top-3) | ≤ 10 ms | Model forward pass only |
| Autocorrect (single word) | ≤ 15 ms | Model forward pass + greedy decode |
| Grammar correction (sentence) | ≤ 30 ms | Full pipeline including tokenization |
| Sentence completion (10 tokens) | ≤ 50 ms | Full pipeline including beam search |
| Smart reply (3 candidates) | ≤ 60 ms | Full pipeline for 3 beam searches |

Measured on a Snapdragon 695 (SM6375) device, the most common mid-range SoC in the SA market (Samsung Galaxy A33, Moto G82). Inference runs on the **CPU** (NNAPI optional, disabled by default due to driver fragmentation).

### 8.2 Memory

| Budget Item | Limit |
|-------------|-------|
| Model weights (INT8 on-disk) | ≤ 55 MB |
| Model weights (loaded in RAM) | ≤ 200 MB |
| KV cache (128-token context) | ≤ 30 MB |
| Tokenizer + vocab | ≤ 5 MB |
| Working buffers | ≤ 20 MB |
| **Total RAM footprint** | **≤ 255 MB** |

The keyboard process must remain under 300 MB total; the NLM budget is 255 MB leaving headroom for HeliBoard's own memory use.

### 8.3 Battery

- Inference is triggered only on user action (keypress / commit / app open).
- No background polling or continuous inference.
- Target: < 2% battery drain per hour of active typing (50 inferences/minute).
- Model runs on CPU thread pool (2 threads max) to avoid thermal throttling.

### 8.4 Startup Latency

- `loadModel()` must complete within 3 seconds on the target device.
- Model is loaded asynchronously; `isModelLoaded()` returns false until ready.
- The keyboard is fully functional (dictionary-based predictions) before the model loads.

### 8.5 Cold Start / Warm Start

| State | Description | Behaviour |
|-------|-------------|-----------|
| Cold | First launch after install | Model loaded on first `onStartInput`, ~3 s |
| Warm | Process already running | ONNX session already created, ~0 ms overhead |
| Unloaded | `unloadModel()` called | Falls back to `DummyNeuralProvider` immediately |

---

## 9. Deployment Format

### 9.1 Chosen Format: ONNX Runtime for Android

**Decision: ONNX Runtime (ORT) Mobile.**

Rationale:

| Criterion | ONNX Runtime | TFLite |
|-----------|-------------|--------|
| Transformer support | Native; all attention ops supported | Requires custom ops or MLIR delegation |
| INT8 quantization | `quantize_dynamic()` one-liner | More complex per-layer quantization |
| KV cache | Supported via external state tensors | Requires manual graph manipulation |
| Python training export | `torch.onnx.export()` directly from PyTorch | Requires TF or ONNX→TFLite conversion |
| Community | Large, active; Hugging Face Optimum support | Older, Google-centric |
| Android APK size delta | +6 MB (ORT AAR, stripped) | +1.5 MB (smaller) |

TFLite is smaller, but its transformer support lags ORT at this parameter scale. ORT Mobile with dynamic quantization is the clearest path from a PyTorch-trained model to a working Android deployment.

### 9.2 Model Artefacts

```
assets/
  model/
    circleone_nlm_int8.onnx       # quantized model weights (~50 MB)
    circleone_bpe.model            # SentencePiece tokenizer (~1.5 MB)
    circleone_langid.ftz           # fastText language ID model (~2 MB)
```

Total APK size impact: ~54 MB (compressed: ~48 MB).

### 9.3 ONNX Runtime AAR Dependency

```groovy
// build.gradle
implementation 'com.microsoft.onnxruntime:onnxruntime-android:1.17.3'
```

Use the `-android` variant (strips desktop backends). Full ORT AAR is ~6 MB.

---

## 10. Quantization Strategy

### 10.1 INT8 Dynamic Quantization

Post-training dynamic quantization is applied to the trained FP32 model:

```python
from onnxruntime.quantization import quantize_dynamic, QuantType

quantize_dynamic(
    model_input="circleone_nlm_fp32.onnx",
    model_output="circleone_nlm_int8.onnx",
    weight_type=QuantType.QInt8,
    per_channel=True,       # per-output-channel for linear layers
    reduce_range=True,      # required for some CPU backends
    nodes_to_exclude=[]     # quantize all nodes
)
```

**Why dynamic (not static)?** Static quantization requires a calibration dataset and per-activation scale computation. Dynamic quantization quantizes weights only; activations are quantized at runtime. For transformer models, dynamic quantization captures 85–90% of the speed benefit of static quantization with far less engineering complexity.

### 10.2 Expected Quality Impact

| Metric | FP32 Baseline | INT8 Dynamic |
|--------|--------------|-------------|
| Model size | ~200 MB | ~50 MB |
| Inference speed (CPU) | ~25 ms | ~9–11 ms |
| Perplexity delta | 0 | +1–3% (negligible) |
| WER on autocorrect | baseline | +0.5–1% (acceptable) |

### 10.3 Future: INT4 / AWQ (Deferred)

If the 10 ms budget is not met on lower-end devices (Snapdragon 480, Helio G85), evaluate:
- **GPTQ / AWQ** (activation-aware weight quantization) for INT4.
- **ORT Model Optimization** (`OrtModelOptimizer`) for graph-level fusion.

This is deferred until after initial deployment and real-device benchmarking.

---

## 11. Training Infrastructure

### 11.1 Hardware Requirements

| Role | Spec | Notes |
|------|------|-------|
| Pre-training | 4× A100 80 GB (or equivalent cloud) | ~2 weeks for 50 M params on 7 B tokens |
| Fine-tuning | 1× A100 40 GB | ~12–24 hours per task |
| Tokenizer training | CPU (32 cores) | ~4 hours on full corpus |
| ONNX export + quantization | CPU (any) | ~30 minutes |

Estimated cloud cost (AWS p4d.24xlarge): ~$2,500–$4,000 for full pre-training run.

### 11.2 Training Framework

| Component | Library |
|-----------|---------|
| Framework | PyTorch 2.x |
| Distributed training | DeepSpeed ZeRO Stage 2 |
| Data loading | Hugging Face Datasets (`arrow` format) |
| Tokenizer | SentencePiece (trained separately) |
| LR schedule | Cosine with 1000-step warmup |
| Optimizer | AdamW (β₁=0.9, β₂=0.95, ε=1e-8, wd=0.1) |
| Batch size | 512 sequences × 128 tokens (global) |
| Learning rate | 3e-4 peak, min 3e-5 |
| Gradient clipping | 1.0 |
| Mixed precision | BF16 on A100 |
| Checkpointing | Every 5,000 steps (HuggingFace `save_pretrained`) |
| Evaluation | Perplexity on validation split, every 1,000 steps |

### 11.3 Export Pipeline

```
PyTorch checkpoint (.bin)
    │
    ▼ torch.onnx.export(opset_version=17)
FP32 ONNX model
    │
    ▼ onnxruntime.quantization.quantize_dynamic()
INT8 ONNX model (circleone_nlm_int8.onnx)
    │
    ▼ onnxruntime.backend.run() [validation against test set]
Validated deployment artefact
    │
    ▼ Copy to android/assets/model/
APK bundle
```

### 11.4 Evaluation Benchmarks

| Benchmark | Metric | Target |
|-----------|--------|--------|
| SA language perplexity | Per-language PPL | < 30 for high-resource (zul, xho, afr), < 50 for low-resource (ssw, nbl, ven) |
| Autocorrect WER | Word Error Rate on held-out typo set | < 5% |
| Smart reply BLEU | BLEU-4 on SA conversation test set | > 0.15 |
| Inference latency | P95 on Snapdragon 695 | < 10 ms (PRED), < 60 ms (SREP) |
| Memory footprint | Peak RSS during inference | < 255 MB |

---

## 12. Privacy and Security

- All inference runs **entirely on-device**. No typed text, predictions, or context is ever transmitted to any server.
- The model file is read-only after installation; it cannot be updated remotely.
- No telemetry, analytics, or usage logging is collected by the NLM layer.
- Model updates are delivered via Play Store APK updates only, subject to standard app store review.

---

## 13. Risks and Mitigations

| Risk | Likelihood | Impact | Mitigation |
|------|-----------|--------|------------|
| 10 ms budget not met on low-end devices | Medium | High | INT4 quantization; NNAPI delegation; reduce context window to 64 |
| Low-resource languages (ssw, nbl, ven) have poor quality | High | Medium | Supplement with back-translated synthetic data; lower confidence threshold for corrections |
| OOM on 2 GB RAM devices | Low | High | `unloadModel()` on memory pressure callback; KV cache eviction |
| ONNX opset compatibility on old Android | Low | Medium | Use opset 17; test on API 26+ (Android 8.0); ship ORT AAR with known-good version |
| Training data licensing | Medium | High | Use only SADILAR (research license), Leipzig (CC-BY), CC-100 (Common Crawl terms); avoid scraped copyrighted content |
| Code-switching degrades single-language predictions | Medium | Low | Language-constrained sampling; test on monolingual held-out sets |

---

## 14. Versioning and Updates

| Version | Change |
|---------|--------|
| 1.0.0 | Initial 50 M parameter model, all 11 SA languages, PRED + CORR tasks |
| 1.1.0 | COMP + SREP tasks added after fine-tuning |
| 2.0.0 | (Future) Larger model, NNAPI acceleration, INT4 quantization |

The model version is embedded in the ONNX file metadata and reported via `NeuralPredictionProvider.getModelVersion()`.

---

## 15. Open Questions (Deferred to Implementation Phase)

1. **NNAPI delegation**: Should be evaluated per device; some Snapdragon NPUs have broken ONNX NNAPI delegates. Default to CPU; expose a developer toggle.
2. **Cross-lingual transfer**: Can one model weight serve all 11 languages or do low-resource languages need language-specific adapters (LoRA)?
3. **Dialect variation**: SA English vs. standard English; Urban Zulu vs. rural Zulu — how much does the training data reflect actual keyboard usage?
4. **Keyboard-specific fine-tuning**: The model should be fine-tuned on keyboard-length inputs (1–3 words context), not full documents. Simulated keyboard sessions need to be generated.
5. **isiBheqe support**: The PUA glyph encoding used by CircleOne's isiBheqe script is not present in any training corpus. NLM predictions for isiBheqe text require a separate approach (rule-based syllable completion, not neural).

---

*End of ON_DEVICE_LM_SPEC.md*
