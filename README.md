# Photo Lab

Search your Android gallery by meaning, fully on the phone. You can type "beach at sunset", "wifi password" or "INV-2291".

Most of the machinery is written by hand in plain Kotlin so it can be learned from:
- two tokenizers
- a transformer forward pass
- image preprocessing
- an HNSW vector index
- BM25
- rank fusion
- a chunker
- a training loop

Each piece is checked against the reference implementation.

## How a search works

| Signal | Finds photos by | Model / algorithm |
|---|---|---|
| Looks like it | what's in the picture | MobileCLIP-S0 (image + text encoders) |
| Text means it | the meaning of text inside the photo | ML Kit OCR → chunker → MiniLM-L6 |
| Contains the words | exact words and numbers | BM25 |

The three ranked lists are merged with Reciprocal Rank Fusion. **Personal labels** ("my dog") train a one-layer model on the phone from photos you tag.

## Results

COCO test photos ([results/m1-benchmark.md](results/m1-benchmark.md)), searching 1,000 photos with 5,000 human captions:

| | Right photo is #1 | in top 5 | in top 10 |
|---|---|---|---|
| MobileCLIP-S0, our Kotlin pipeline | 61.6% | 87.2% | 94.4% |

- Quantization:
  - The uint8 ONNX files published on Hugging Face are **broken** (0.1% = random).
  - Our own int8 text encoder gives identical accuracy, is 39 MB smaller and encodes text faster.
- HNSW finds 99–100% of the brute-force top 10.
- Personal labels beat plain text search from 5 tagged photos onward (average precision 0.812 → 0.832 at 10 tagged).

On a mid-range phone, Snapdragon 7s Gen 2 ([results/m1-phone.md](results/m1-phone.md)):

| | Time |
|---|---|
| Index one photo (CLIP) | 128 ms |
| Read text in one photo (OCR + MiniLM) | ~112 ms |
| One search, end to end | 30–47 ms |
| Train a personal label | 109 ms |

## Layout

```
embed-core/   tokenizers (WordPiece, CLIP BPE), MiniLM via ONNX Runtime and by hand, image prep, CLIP encoder
search-core/  flat index, HNSW, BM25, RRF fusion, chunker, linear-probe trainer — no dependencies
bench/        COCO benchmark → results/m1-benchmark.md
photos-app/   the Android app: indexer, SQLite store, hybrid search, labels, Lab
scripts/      install-phone.sh
spikes/       earlier experiments (phone agent, Paparazzi) — see docs/
```

## Run it

Gradle needs JDK 21+. Android Studio's bundled JBR works:

```bash
export JAVA_HOME="$HOME/Applications/Android Studio.app/Contents/jbr/Contents/Home"
```

Tests (they compare against Python reference outputs; model files in `.work/models` are needed):

```bash
./gradlew :embed-core:test :search-core:test
```

Install on a USB-connected phone, copying the models over USB:

```bash
scripts/install-phone.sh
```

Or install only the APK. The app then offers to download the models (~400 MB) from Hugging Face on first launch.

## Privacy

Photos, their text and their vectors stay on the phone. The network is used only to download models on first launch. Note that Google's ML Kit library may send anonymous usage statistics of its own.

## Licences

Model weights are not in this repo:
- MobileCLIP is under Apple's ML research licence.
- all-MiniLM-L6-v2 is Apache-2.0.

Plan and history: [PLAN.md](PLAN.md). Learning notes: [LEARNING.md](LEARNING.md).
