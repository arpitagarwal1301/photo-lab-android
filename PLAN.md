# Plan v3 — Photo Lab: on-device photo search, built from the inside out

_Earlier plans: [v1 phone agent](docs/plan-v1-phone-agent.md), [v2 commerce PR guard](docs/plan-v2-commerce-guard.md)._

## What it is

An Android app that searches your gallery by meaning, fully offline:

- **"beach at sunset"** → MobileCLIP compares the words with every photo's pixels.
- **"wifi password"** → ML Kit reads the text in photos, the text is chunked, and MiniLM compares meanings.
- **"INV-2291"** → BM25 keyword search finds exact strings.
- The three result lists are merged with Reciprocal Rank Fusion.
- **Personal labels** ("my dog") train a one-layer model on the phone from photos you tag.

Most of the machinery is written by hand so it can be learned from:
- both tokenizers
- MiniLM's forward pass
- image preprocessing
- the HNSW index
- BM25 and fusion
- the chunker
- the training loop

Each one is checked against a reference implementation.

## Modules

| Module | What | Checked against |
|---|---|---|
| `embed-core` | WordPiece + CLIP BPE tokenizers, MiniLM via ONNX Runtime **and** by hand, image resize/crop, CLIP encoder, safetensors reader | Python (HF tokenizers, onnxruntime, PIL): same ids, vectors within 1e-4 |
| `search-core` | Flat index, HNSW, BM25, RRF fusion, chunker, linear probe (trainer) | brute force (recall), unit tests |
| `bench` | COCO 1k benchmark → `results/m1-benchmark.md` | — |
| `photos-app` | Gallery indexer, OCR, SQLite store, hybrid search, labels, Lab | on the phone |

## Status (2026-11-24)

- [x] Core libraries + tests (11 tests).
- [x] Benchmark results:
  - search: 62% top-1, 94% top-10 on 1,000 photos
  - quantization: the downloaded uint8 models are broken; our int8 text model gives the same accuracy at −39 MB
  - HNSW: 99%+ recall
  - labels: help from 5 tagged photos onward
- [x] App built and installed with `scripts/install-phone.sh`.
- [ ] On-phone numbers (ms per photo, search latency) on the Motorola Edge 60 Stylus.
- [ ] README with results and a demo GIF.
- [ ] Later:
  - small on-device LLM to answer questions over the found text (RAG)
  - speculative decoding experiment

## Learning path (after the product works)

1. Embeddings: the Lab tab shows the guess-then-test pairs, and you can compare ONNX Runtime with the hand-written engine.
2. CLIP: photos and text as the same kind of numbers.
3. Quantization: why measuring the real task beats measuring vector closeness.
4. Vector index: the HNSW ef knob in the Lab, on your own photos.
5. Chunking + hybrid search: OCR text, BM25, fusion.
6. Training on the phone: the loss curve in Labels.

Progress and takeaways go in [LEARNING.md](LEARNING.md).

## Rules for ourselves

- Measure before believing: every model change goes through `bench`.
- Nothing personal leaves the phone. Models are downloaded once, and photos are never uploaded.
- Model weights are never committed (`.work/` is ignored).
