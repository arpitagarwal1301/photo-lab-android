# Phase 0 results

Fill one section per spike. Every number needs: device, OS version, date, and the exact command or commit.

## A — VLM speed on the phone

| Field | Value |
|---|---|
| Device / SoC / RAM | Motorola Edge 60 Stylus / Snapdragon 7s Gen 2 / 8 GB |
| Android version | 16 (SDK 36), build W1VBS36.62-22-17-2 |
| llama.cpp commit | 1d3aad2 |
| Model + mmproj files | Qwen3VL-2B-Instruct-Q4_K_M.gguf + mmproj-Qwen3VL-2B-Instruct-Q8_0.gguf |
| Threads | 4, CPU only |
| Image encode time (ms) | **319,387 (~5.3 min)** for a full-resolution screenshot (run 1; runs 2–3 stopped) |
| Prompt eval (tokens, ms) | |
| Decode (tokens/s) | |
| Total per step (s) | |
| Peak RSS (MB) | ~3,600 |
| Thermal status before → after | 0 → 1 |

Screenshot 1220×2712 (Clock app). Same model, 4 threads, CPU:

| Image cap | Image encode | Whole run (incl. model load) | Peak RAM | Answer quality |
|---|---|---|---|---|
| none (full res) | 319 s | ~6 min | 3.6 GB | cut off in the log |
| 1024 tokens | 43 s | 86 s | 3.5 GB | **good** — 10+ correct elements with plausible positions |
| 256 tokens | 7 s | 18 s | 3.2 GB | **useless** — one element at (0, 0) |

**Verdict:** ❌ for every-step use on this phone. Readable quality costs ~45–85 s per step. Tier 3 defaults to the cloud on mid-range phones; on-device vision is only a rare fallback. This makes the **tree tier + region crops** essential, not optional: a crop keeps detail with far fewer image tokens. Next: test a cropped region at 256–512 tokens, and the GPU (OpenCL/Vulkan) backend.

**Verdict:** ≤ ~15 s/step → Tier 3 stays on-device. Otherwise Tier 3 goes cloud-only.

## B — AppFunctions caller access

| Field | Value |
|---|---|
| Date / build | 2026-11-23 · Motorola Edge 60 Stylus · build W1VBS36.62-22-17-2 |
| Android version / SDK_INT | 16 / 36 |
| Permission defined on this OS? | Yes |
| Protection / flags (hex) | `internal` / `0x10` (= privileged) — i.e. `internal\|privileged`, **not** the role-based level |
| Assistant role available? | Yes (current holder: Google app) |
| Could the app hold the Assistant role? How? | Not tried yet |
| Permission granted after holding the role? | Not tried yet — expected **no**, since the flag is privileged, not role |
| `AppFunctionManager` service available? | Yes |

**Verdict:** Tier 1 usable against other apps / own demo app only / not usable.

## C — AndroidWorld on this Mac

| Field | Value |
|---|---|
| AVD (device, API, ABI) | |
| Setup (`--perform_emulator_setup`) succeeded? | |
| Task apps installed on arm64 image? | |
| One task run end-to-end? | |

## D — Paparazzi semantics + gif

| Field | Value |
|---|---|
| Date / setup | 2026-11-23 · Paparazzi 2.0.0-alpha05.1 · AGP 9.3.1 · Kotlin 2.3.21 · Compose 1.11.2 · JBR 25 · Apple M5 |
| Snapshot + gif recorded? | Yes. PNG snapshots + animated PNG with 11 frames (0–1000 ms at 10 fps, 16.6 KB) in `src/test/snapshots/videos/` |
| Semantics JSON produced? Node count | Yes, 10 nodes (`build/spike-d/checkout.json`) |
| Compact text size (chars, ~tokens) | 246 chars, ~61 tokens for the whole checkout screen |
| Screen fingerprint stable across runs? | `af473940f83f` — re-run to confirm |
| Seeded bug 1 (unlabelled tappable) visible? | Yes, as `(no label!)` ×2 in our dump. **Paparazzi's TalkBack legend omits these nodes entirely**, so the legend alone would miss the bug. |

**Verdict:** ✅ Paparazzi works as the offline screen factory. A custom `RenderExtension` reading `ViewRootForTest.semanticsOwner` after the first draw gets the merged semantics tree. Still to do: View-based screens (not just Compose), and checking the fingerprint is stable.
