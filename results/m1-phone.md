# Photo Lab on a real phone

_Motorola Edge 60 Stylus (Snapdragon 7s Gen 2, 8 GB, Android 16), ONNX Runtime 1.31.1 on CPU, 4 threads. 2026-11-24. 51 photos, mostly screenshots._

| What | Time |
|---|---|
| Load the three networks at start-up | 2.8 s |
| Understand one photo (thumbnail → resize/crop → MobileCLIP) | 128 ms |
| Read text in one photo (ML Kit OCR → chunk → MiniLM) | 110–115 ms |
| One search, end to end | 30–47 ms |
| … CLIP text encoder | 20–35 ms |
| … MiniLM | 7–10 ms |
| … HNSW vector index | ~2 ms |
| … BM25 keywords | < 1 ms |
| Train a personal label on 5 tagged photos (30 passes) | 109 ms |
| MiniLM, one phrase: ONNX Runtime vs our hand-written Kotlin | 7–11 ms vs 122–185 ms (same numbers) |

At these speeds a 5,000-photo gallery is fully indexed in about 20 minutes, once. After that only new photos are processed.

## Bugs only the phone found

- Android's regex engine rejects `(?U)`, which works on the JVM. Fixed with `[\s\p{Z}]+`.
- Indexing ran on the UI thread. Fixed: it now runs on `Dispatchers.Default`.
- The keyed `LazyVerticalGrid` kept the old first photo in view after re-ranking, so the best match wasn't shown first. Fixed: it scrolls to the top on new results.
- A MiniLM threshold of 0.35 let unrelated screenshots into the "text means it" list. Raised to 0.45.
- "update" showed a Wi-Fi screenshot as the best match. No photo is about updates, but nearest-neighbour search always returns *something*, and rank fusion hid how weak it was (0.18 vs a gallery median of 0.16; a real match like "wifi password" reaches 0.27).
  - First fix, "beat the gallery median by 0.06": still let 2 non-dog photos through for "a dog" (a gallery of screenshots makes any camera photo stand out), and found 0 for "eyeglasses" (most photos are glasses, so the median was already high).
  - Final fix: a photo counts as "looks like it" only if the query describes it better than 18 generic guesses ("a screenshot of a web page", "a photo of a person", …). On COCO this lets in 3× fewer wrong photos for short queries and keeps the right photo for 98% of full captions. When nothing qualifies, the app says "No strong match" and shows the closest photos.
