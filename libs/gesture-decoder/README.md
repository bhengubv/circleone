# Open Gesture Decoder

An open-source gesture/swipe typing decoder for software keyboards. MIT licensed.

---

## Why this exists

Every production-quality gesture/swipe typing engine in use today — SwiftKey, Gboard, the Android
framework's built-in gesture engine — is closed source. Keyboard developers who want swipe typing
must either license a commercial SDK, accept the framework's limited public API, or build from
scratch without a reference implementation to learn from.

**Open Gesture Decoder** fills that gap. It is a self-contained, dependency-free Java library that
turns a sequence of touch co-ordinates into a ranked list of word candidates. It is fast enough for
real-time keyboard use (<50 ms for a 50 000-word dictionary on a mid-range Android device), and
its scoring pipeline is documented and extensible.

Built by **The Other Bhengu (Pty) Ltd t/a The Geek** as the gesture engine for
**CircleOne Keyboard** — a South African keyboard with deep African language support.

---

## Features

- **Shape matching** — dynamic time warping between the input gesture and each word's ideal path
  through the key layout, normalised for speed and direction.
- **Multi-channel scoring** — combines shape similarity, language-model unigram frequency, word
  length penalty, and start-key / end-key proximity into a single ranked score.
- **Fast pruning** — spatial index over key centroids rejects impossible words before full DTW
  is run, keeping decode time sub-linear in dictionary size.
- **Language model support** — supply word frequencies at load time; the decoder weights common
  words higher without sacrificing shape fidelity.
- **Layout-agnostic** — describe any key layout (QWERTY, Dvorak, Colemak, custom scripts) via
  `KeyboardLayout` + `KeyCell`. Works equally well for Latin and non-Latin scripts.
- **No dependencies** — plain Java 8, no Android SDK, no third-party libraries. Runs on Android,
  desktop JVM, and server-side.
- **MIT licensed** — use it in any project, open or proprietary.

---

## Performance targets

| Dictionary size | Gesture length | Decode time (Pixel 6a) |
|----------------|----------------|------------------------|
| 10 000 words   | 8 keys          | < 5 ms                 |
| 50 000 words   | 8 keys          | < 50 ms                |
| 100 000 words  | 8 keys          | < 90 ms                |

Pruning is the primary lever. The spatial index typically eliminates 85–95 % of candidates before
full DTW runs. Tune `GestureDecoder.setPruningRadius()` to trade accuracy for speed.

---

## Quick start

```java
// 1. Describe your keyboard layout.
KeyboardLayout layout = new KeyboardLayout(1080, 720);
layout.addKey(new KeyCell('q', 54,  90, 108, 180));
layout.addKey(new KeyCell('w', 162, 90, 108, 180));
// … add all keys …

// 2. Load your dictionary.
List<String> words = Arrays.asList("hello", "world", "swipe", /* … */);

// 3. Initialise the decoder.
GestureDecoder decoder = new GestureDecoder();
decoder.init(layout, words);

// 4. Record a gesture.
GesturePath path = new GesturePath();
path.addPoint(54,  90,  0);    // 'q' key centre, t=0ms
path.addPoint(270, 90,  80);   // midway
path.addPoint(486, 90, 160);   // 'e' key
// … more points …

// 5. Decode.
List<String> candidates = decoder.decode(path);
System.out.println(candidates.get(0)); // e.g. "hello"
```

---

## API overview

### `GesturePath`

Accumulates raw touch samples.

| Method | Description |
|--------|-------------|
| `addPoint(float x, float y, long timestamp)` | Append a touch sample. |
| `getPointCount()` | Number of samples recorded. |
| `getX(int i)` / `getY(int i)` | Co-ordinate of sample `i`. |
| `getTimestamp(int i)` | Timestamp of sample `i` in milliseconds. |
| `clear()` | Reset the path for reuse. |

---

### `KeyboardLayout`

Describes physical key positions.

| Method | Description |
|--------|-------------|
| `KeyboardLayout(int widthPx, int heightPx)` | Constructor. |
| `addKey(KeyCell cell)` | Register a key. |
| `getKey(char c)` | Look up a key by character. |
| `getAllKeys()` | Return all registered keys. |

---

### `KeyCell`

Immutable value type representing one key.

| Field | Description |
|-------|-------------|
| `char character` | The character this key produces. |
| `float centerX` / `centerY` | Key centre in pixels. |
| `float width` / `height` | Key dimensions in pixels. |

---

### `GestureDecoder`

The core decoding engine.

| Method | Description |
|--------|-------------|
| `init(KeyboardLayout layout, List<String> dictionary)` | Load layout and word list. Must be called before `decode`. |
| `init(KeyboardLayout layout, Map<String,Float> weightedDictionary)` | Variant accepting per-word frequency weights (0.0–1.0). |
| `decode(GesturePath path)` | Decode a completed gesture. Returns candidates ranked by score. |
| `decode(GesturePath path, int maxResults)` | Limit the number of returned candidates. |
| `setPruningRadius(float radiusKeys)` | Spatial pruning radius in key-widths (default 1.5). Lower = faster, less accurate. |
| `setShapeWeight(float w)` | Weight for shape (DTW) channel in the final score (default 0.6). |
| `setFrequencyWeight(float w)` | Weight for language-model channel (default 0.3). |
| `setLengthPenaltyWeight(float w)` | Weight for word-length mismatch penalty (default 0.1). |

---

## Architecture

```
Touch events
     │
     ▼
┌──────────────┐
│  GesturePath │  Raw (x, y, t) samples — no filtering applied here.
└──────┬───────┘
       │
       ▼
┌──────────────────────────────────────────────────────────────┐
│  GestureDecoder.decode()                                      │
│                                                              │
│  1. Spatial prune                                            │
│     KeyboardLayout spatial index → discard words whose       │
│     required keys are too far from the gesture path.         │
│                                                              │
│  2. Ideal path generation                                    │
│     For each surviving word, generate the "ideal" gesture    │
│     by connecting key centre-points in order.                │
│                                                              │
│  3. Shape score (DTW)                                        │
│     Dynamic time warping between normalised input path and   │
│     ideal path. Distance → similarity score [0, 1].          │
│                                                              │
│  4. Multi-channel fusion                                     │
│     score = shapeWeight × shape                              │
│           + freqWeight  × frequency                          │
│           - lengthPenaltyWeight × |len_word - len_gesture|   │
│                                                              │
│  5. Rank and return top-N                                    │
└──────────────────────────────────────────────────────────────┘
       │
       ▼
List<String>  (ranked, best first)
```

---

## Integrating with your keyboard

### Android (HeliBoard / any AOSP-based keyboard)

1. Add the JAR to your `libs/` folder and declare it in `build.gradle`:
   ```groovy
   dependencies {
       implementation files('libs/open-gesture-decoder-0.1.0.jar')
   }
   ```

2. Build a `KeyboardLayout` from your keyboard view's measured key positions. The most accurate
   approach is to iterate over `Keyboard.getKeys()` (or equivalent) after the layout is inflated.

3. Create a `GestureDecoder`, call `init()` with the layout and your dictionary, then call
   `decode()` from `onTouchEvent` when `ACTION_UP` fires.

4. Push the returned candidates into your suggestion strip.

See `SwipeTypingHandler.java` in the CircleOne repository for a complete reference integration
(GPL-3.0 — the integration adapter is part of CircleOne, not this library).

### Desktop / server

The library has no Android dependency. Add the JAR to your classpath and use the same API.
Pixel co-ordinates can be logical units — the decoder only cares about relative distances.

---

## Contributing

Contributions are welcome. Please open an issue before submitting a large pull request so we can
discuss the approach.

**Areas where help is most appreciated:**

- Better DTW normalisation for very short gestures (1–2 keys).
- A curated weighted word list for South African languages (Zulu, Xhosa, Sotho, Tswana, …).
- Benchmarking on low-end devices (< 1 GB RAM, Android 8).
- JNI / native port for further speed gains on ARM.

All contributions must be MIT licensed and include unit tests.

---

## Building from source

```bash
git clone https://github.com/thegeeknetwork/open-gesture-decoder.git
cd open-gesture-decoder
./gradlew jar          # outputs build/libs/open-gesture-decoder-0.1.0.jar
./gradlew test         # run unit tests
./gradlew javadoc      # generate API docs
```

Requires Java 8 or later. No other tooling needed.

---

## License

```
MIT License

Copyright (c) 2026 The Other Bhengu (Pty) Ltd t/a The Geek.

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
THE SOFTWARE.
```

---

*Built with care by The Other Bhengu (Pty) Ltd t/a The Geek — creators of
[CircleOne Keyboard](https://github.com/bhengubv/circleone), a South African keyboard for the
whole continent.*
