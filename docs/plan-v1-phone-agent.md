# spike-ai — project plan

_Working title. Decided 2026-11-23, revised same day to fold in on-device RAG and Paparazzi._

## One line

An Android agent that operates apps by reading the screen the **cheapest way that works**, sends the model **only what changed**, and **remembers apps it has used** so each repeat run is cheaper — published as an **open benchmark** (tokens/step vs success vs latency on a mid-range phone) with a **QA mode** that explores your own app and finds broken flows.

Headline to aim for: **"The agent gets cheaper the more it uses an app."**

## Decisions so far

| Topic | Decision |
|---|---|
| Framing | Research benchmark at the core; QA-your-own-app as the use case. Consumer assistant only as a demo moment. |
| Goal | Visibility: GitHub stars, posts, talks. No deadline. |
| Compute | Hybrid, reported honestly (% of steps on-device, cost of cloud fallback). |
| Test device | Motorola Edge 60 Stylus — Snapdragon 7s Gen 2, 8 GB RAM, Android 16 rolling out. Mid-range is a selling point. No Gemini Nano; assume CPU/GPU, no NPU. |
| Skills | NDK/JNI known. |
| Licence | Apache-2.0. Model weights never bundled — download script only. |

## Ideas borrowed from the two reference repos

### probelalkhan/on-device-rag-android (reference only)

- What it is: a course companion — "private files → chunks → embeddings → retrieval → Gemma" on **LiteRT-LM**, with EmbeddingGemma, PDF chunking and **hybrid BM25 + vector search** (per its folder names).
- **No licence file**, so legally it is all-rights-reserved: we learn from it and re-implement; we do not copy code.
- What we take: the LiteRT-LM + Gemma text path (our Tier-2 engine) and hybrid retrieval — applied to **app memory**, not documents (see `:memory` below). BM25 matters here because UI labels are exact words ("Alarm", "Add to cart").

### cashapp/paparazzi (dependency, Apache-2.0)

- What it is: renders **your own** Views/Compose screens on the JVM via layoutlib — no device. Has `paparazzi.gif(...)` (animated PNG over a time window, default 0–500 ms at 30 fps) and `AccessibilityRenderExtension` (TalkBack-order legend of every focusable element). Latest is 2.0.0-alpha05.1 (Java 21+, AGP 9).
- What it **can't** do: capture other installed apps or a live device screen. So it is not the agent's eyes on a phone.
- What we use it for:
  1. **QA fixture factory** — render the seeded-bug sample app's screens with ground-truth labels, no emulator, fast in CI.
  2. **Offline perception tests** — feed rendered screens to the pruner / vision tier in unit tests, deterministic.
  3. **Animation fixtures** — `gif()` frames with known timings to test the "wait until the screen is stable" detector offline.
  4. **Synthetic training data (Phase 7)** — render screen variants (font scale, theme, locale, device size) as (image, semantics) pairs. Needs a custom `RenderExtension` that dumps the hierarchy as JSON — Spike D checks this is possible.

### About "continuous screenshots like a GIF to save tokens"

A GIF or video does **not** save tokens by itself: vision models turn every frame into image tokens, so 15 frames cost roughly 15 screenshots. (Some video-capable models, like the Qwen-VL family, merge pairs of frames — about half the cost per frame — still far more than one image. To verify for Qwen3-VL.)

The idea behind it is right, though. The win is **capture continuously on the phone (free), send to the model rarely and only the change**:

- **Stability detector** — watch frames / accessibility events locally; only call the model once the screen stops changing (skips spinners and transitions — no wasted steps).
- **Delta perception** — after an action, send a **tree diff** ("+ dialog 'Delete alarm?' with buttons Cancel, Delete") or a **cropped changed region**, not the whole screen.
- **Keyframes for verification** — keep a short local frame buffer; send 1–2 keyframes only when the agent must check that an action worked.

## Architecture

```
Goal ─► Agent loop (observe → decide → act → check progress)
            │
            ├─► :memory  App Map (screens graph + past traces) ──► "I know the way" → replay with few tokens
            │
            │  else: cheapest view first, only what changed
   ┌────────┼──────────────────────┬──────────────────────────┐
Tier 1: AppFunctions   Tier 2: pruned a11y tree      Tier 3: screenshot + set-of-marks
(direct call)          + tree diff (small model)     + changed-region crop (2B VLM)
   └────────┬──────────────────────┴──────────────────────────┘
       ScreenStream (continuous local capture) → Stability detector → Delta encoder
            │
       Router (escalate on low confidence / no progress / error; local → cloud)
            │
       Actuator (AccessibilityService) ─► Safety gate ─► Telemetry JSON
```

### Gradle modules

- `:agent-core` — loop, typed actions (`Tap`, `Type`, `Scroll`, `Back`, `OpenApp`, `Done`), budgets.
- `:perception` — AppFunctions source, a11y-tree source + **pruner**, vision source (MediaProjection + set-of-marks), **ScreenStream**, **StabilityDetector**, **DeltaEncoder** (tree diff, pixel-region diff).
- `:memory` — **App Map**: screen fingerprints (hash of pruned tree), transitions (screen A —tap "Alarms"→ screen B), successful task traces. Hybrid retrieval: BM25 over labels + embeddings (EmbeddingGemma via LiteRT). Store: SQLite with brute-force cosine first (phone-sized data); compare sqlite-vec / ObjectBox later.
- `:llm` — `Engine` interface: llama.cpp (JNI + mtmd) for VLM, LiteRT-LM (Gemma) for text, cloud for baseline/fallback.
- `:router`, `:actuator`, `:safety`, `:telemetry`.
- `:app` — Compose UI with live step-trace overlay (doubles as demo video).
- `qa-fixtures/` — sample app with seeded bugs + Paparazzi tests producing goldens, accessibility legends and semantics JSON.
- `bench/` — Python: AndroidWorld bridge, own task suite runner, charts, leaderboard generator.

### Optimisation tricks to measure

- GBNF grammar-constrained action JSON (no format retries).
- KV-cache reuse of the fixed system-prompt prefix across steps.
- Pruning rules ablation.
- Delta perception vs full screen each step.
- Memory replay vs fresh exploration.

## Research questions

1. Tokens/step and success per tier; where escalation stops paying.
2. Which pruning rules matter.
3. **Delta perception:** tokens saved by sending diffs/crops instead of full screens, and the success cost (if any).
4. **Memory:** steps and tokens on the 1st vs 2nd vs 5th run of the same (or a similar) task.
5. On-device vs cloud: success, latency, fallback rate.
6. Robustness: injected text / fake pop-ups (AnTrap-style).
7. (Phase 7) Can a small model fine-tuned on our own traces + Paparazzi-rendered screens match the cloud on the tree tier?

## Benchmark design (what makes it shareable)

- **Task suite:** ~30 tasks on AOSP + open-source (F-Droid) apps, so anyone can reproduce.
- **QA suite (novel):** `qa-fixtures` app with **seeded bugs** — dead button, crash, dead-end flow, wrong total, missing content description. Paparazzi renders ground truth. Score = bugs found, steps, tokens.
- **Repeat-run track:** same tasks run 5× with memory on vs off.
- **AndroidWorld subset** via a bridge, for comparability with papers. Decide on host; measure latency on the real phone.
- **Results format:** one JSON per run (model, device, tier policy, memory on/off, per-step tokens/latency/memory, outcome). Committed under `results/`.
- **Leaderboard:** GitHub Pages page generated from `results/`, with a **device column** (mid-range first). Others submit runs by PR.

## Phases (each ends with something public)

| Phase | Build | Exit criteria | Public artifact |
|---|---|---|---|
| 0 | Spikes: **A** Qwen3-VL-2B via llama.cpp mtmd on the phone · **B** AppFunctions access via default-assistant role · **C** AndroidWorld on emulator · **D** Paparazzi semantics dump + gif frames | Four answers with numbers. Pivot: VLM > ~15 s/step → Tier 3 cloud-only | Post: "A 2B vision model on a mid-range Android phone — real numbers" |
| 1 | Cloud-brain baseline on a11y tree, telemetry, 10 hand tasks | Success rate + per-step JSON | — |
| 2 | Pruner + **tree diff** + on-device text model (LiteRT-LM Gemma) + GBNF | Before/after tokens & success table | **Release the pruner as a standalone library** + post "How many tokens does an Android screen cost?" |
| 3 | Vision tier on-device + **ScreenStream, stability detector, region crops** + router + KV prefix reuse | Escalation works; latency + tokens per tier | Post + video of the step-trace overlay |
| 4 | **`:memory` App Map** + hybrid retrieval + trace replay | Repeat-run chart (run 1 vs 5) | Post: "An Android agent that gets cheaper every run" |
| 5 | AppFunctions tier with own demo app | 1 call vs ~8 UI steps | Post: "AppFunctions vs UI automation" |
| 6 | Task suite + **Paparazzi-built QA suite** + AndroidWorld subset, safety gate, robustness | Benchmark v1 + leaderboard live | v1 release, README hero GIF + chart, Show HN, r/androiddev, Android Weekly / Kotlin Weekly, DroidCon CFP |
| 7 (optional) | Fine-tune a ≤1B tree-tier model on traces + Paparazzi synthetic screens | Match rate vs cloud, on-device cost | Model on Hugging Face + post |

## Visibility checklist

- README: hero GIF (agent running on the Motorola), headline chart, one-command quickstart, "reproduce our numbers".
- Build in public: one post per phase with real numbers, never a post without a chart.
- Pick the final name early; check GitHub, Maven Central and domain availability before announcing.
- Sideload only (Play policy lists automation tools/assistants as not accessibility tools).

## Open items

- Final project name.
- Confirm Android 16 on the test phone.
- Confirm AppFunctions caller access (Assistant role) on device.
- Confirm Qwen3-VL video/frame token behaviour.
- Confirm a Paparazzi `RenderExtension` can read Compose semantics.

## Reading list (Phase 0)

- AndroidWorld — https://arxiv.org/abs/2405.14573
- Ferret-UI Lite — https://arxiv.org/abs/2509.26539
- MobileExplorer (online exploration → faster agents; closest to our App Map) — https://arxiv.org/html/2605.26546v1
- MobiInfer — https://arxiv.org/pdf/2611.08496
- AnTrap — https://arxiv.org/pdf/2608.24099
- Arm: VLM on Android with llama.cpp — https://learn.arm.com/learning-paths/mobile-graphics-and-gaming/ai-portal-mobile-vision-language/
- AppFunctions — https://developer.android.com/ai/appfunctions
- Paparazzi — https://github.com/cashapp/paparazzi · accessibility: https://cashapp.github.io/paparazzi/accessibility/
- On-device RAG course repo (reference) — https://github.com/probelalkhan/on-device-rag-android
- Prior art: RunAnywhere android-use-agent — https://runanywhereai.github.io/runanywhere-sdks/Playground/android-use-agent/ASSESSMENT.html
