# Phase 0 spikes

Four questions to answer before building anything real. Record every answer in [`results/phase0.md`](../results/phase0.md).

| Spike | Question | Needs | Kill / pivot rule |
|---|---|---|---|
| [A](a-vlm-speed/) | How fast is a 2B vision model per agent step on the Motorola? | Phone over USB, model files | > ~15 s/step → Tier 3 cloud-only |
| [B](b-appfunctions-access/) | Can a normal app get `EXECUTE_APP_FUNCTIONS` via the Assistant role? | Phone on Android 16 | Not granted → Tier 1 only against our own demo app |
| [C](c-androidworld/) | Does AndroidWorld run on an arm64 emulator on this Mac? | uv, API 33 AVD | Doesn't run → use our own task suite only |
| [D](d-paparazzi-semantics/) | Can Paparazzi give us snapshots, frames and the semantics tree as data? | Nothing (JVM only) | No semantics → build fixtures on the emulator instead |

Suggested order: **D** (no hardware) → **B** (quick) → **A** (the big one) → **C**.
