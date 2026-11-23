# Learning journal

One section per lesson: what the concept is, where it lives in the code, what I guessed vs what happened, and my one-line takeaway.

## Lesson 1 · Embeddings

**Concept:** a network turns a sentence into 384 numbers; similar meaning → vectors point the same way → cosine similarity near 1.

**Where it lives**

| Step | File |
|---|---|
| Text → word-pieces → ids | `embed-core/.../WordPieceTokenizer.kt` |
| Ids → MiniLM (ONNX Runtime) → one vector per token | `embed-core/.../Embedder.kt` |
| Average tokens → one vector, scale to length 1, dot product | `embed-core/.../VectorMath.kt` |
| Lab screen | `photos-app/.../LessonOneScreen.kt` |

**What happens to "blue round glasses" (real values)**

| Step | Who | Result |
|---|---|---|
| 1. Clean + split | `basicSplit()` | blue · round · glasses |
| 2. Word-pieces | `wordPieces()` | same (rare words split: spectacle ##s, photo ##ch ##rom ##ic) |
| 3. Ids | `encode()` | [CLS]=101 blue=2630 round=2461 glasses=7877 [SEP]=102 |
| 4. Table lookup | MiniLM | 30,522 × 384 table (11.7M of 22.6M weights) + position table → 5 × 384 |
| 5. 6 transformer layers | MiniLM | attention (tokens blend in each other) + feed-forward 384→1536→384; "blue" in "feeling blue today" is only 0.79 similar to "blue" here |
| 6. Average | `meanPool()` | 5 rows → 1 row, length 7.30 |
| 7. Normalize | `normalize()` | divide by 7.30 → length 1 |
| 8. Compare | `dot()` | red cap · red hat 0.76, red cap · red wine 0.54 |

Checked against Python (Hugging Face tokenizer + onnxruntime): tokens identical, numbers within 0.0001 (`embed-core` tests).

**Guess, then test** (fill the guess column before tapping)

| Pair | My guess | Real |
|---|---|---|
| 1. blue round glasses · navy circular frames | | |
| 2. blue round glasses · blue round pizza | | |
| 3. I love these frames · I hate these frames | | |
| 4. chashma · glasses | | |
| Top 3 for "round spectacles" | | |

**My takeaway (one line):**
