# spike-ai — plan v2: build-time app understanding

_2026-11-23. Replaces the on-phone agent plan (archived in [docs/plan-v1-phone-agent.md](docs/plan-v1-phone-agent.md)) after Phase 0 showed on-device vision costs ~86 s per screen on a mid-range phone._

## The problem with what everyone else does

Almost every AI testing tool (Journeys, DroidRun, Drizz, Autosana, vision agents) makes an AI **look at a running app, every step, every run**. That is slow (our test: 86 s/screen on-device), costly (thousands of tokens per screenshot), and flaky. Maestro's own team calls natural-language-at-runtime testing a demo, not what real teams use.

## The different idea

**Understand the app at build time, as text, and spend AI tokens only on what changed.**

1. Every Compose screen already renders on the JVM with no device (Paparazzi/Roborazzi). Its semantics tree is tiny: our checkout screen was **~61 tokens** vs thousands for a screenshot (Spike D).
2. Scan **all `@Preview`s** automatically (ComposablePreviewScanner) → a text **App Model** of every screen and state, rebuilt in CI in seconds.
3. Check **e-commerce rules deterministically first** (zero tokens): line items add up to the total, currency format, pay button amount = order total, no unlabelled tappables, no empty CTA. Spike D's ₹1,498 vs ₹1,499 bug is caught by arithmetic, not an LLM.
4. Call the LLM **only on the diff** of a PR (changed screens' text), and only for fuzzy rules.
5. Generate UI tests **once** as Maestro flows from the App Model; CI replays them with **no LLM**. LLM only repairs a flow when it breaks (bounded retries), and caches the fix.

Tokens scale with *what changed*, not with *app size × runs*. That's the efficiency story, and it's measurable.

## Across the development lifecycle (e-commerce app)

| Stage | What runs | AI cost |
|---|---|---|
| Spec | Ticket/PRD → acceptance criteria → Maestro flow drafts grounded in the App Model (real screen labels, no guessing) | One call per ticket |
| Code / PR | Re-render only screens the PR touched → semantic diff → commerce rules (deterministic) → LLM reviews the diff only → PR comment | Zero for most PRs; small text diff otherwise |
| Test | Maestro flows replay in CI deterministically. On failure: LLM sees that one screen's semantics + the failed step → proposes a flow fix | Only on failures |
| Release | Preview matrix (dark mode, font scale 1.3×/2×, Hindi/RTL, small phone) → rule checks + pixel diff; AI looks only at screens whose pixels changed | Only changed screens |
| Production (later) | Crashes / ANRs / Play reviews → mapped to a screen in the App Model → issue with a repro flow | Batched, daily |

## What already exists (we build on it, not around it)

- **Roborazzi**: JVM screenshots via Robolectric (works with Hilt/ViewModels), official **UI-tree JSON + numbered-box image** dump for AI agents, AI image assertion that runs only when a screenshot differs. Better fit than Paparazzi for a real app.
- **ComposablePreviewScanner**: auto-tests every `@Preview` (built into Roborazzi's plugin).
- **Maestro**: deterministic mobile flows; Trendyol (e-commerce) reports 10,400 AI-generated Maestro tests in under a year.
- **Midscene** (web): caches AI plans and only re-calls the model when the UI changes. Nothing equivalent on Android that's built-time-first.

**Our original part:** the build-time App Model + commerce-rule checker + diff-only LLM review + compile-once Maestro generation, with published token/time numbers. None of the above does that end to end.

## Milestones

| # | Build | Done when | Public artifact |
|---|---|---|---|
| M1 | **Commerce PR Guard (offline)**: Gradle plugin → scan previews → semantics JSON → commerce rules → report. Fixture e-commerce module (cart, checkout, PDP, coupon) with 10 seeded bugs. | Catches ≥ 8/10 seeded bugs with **zero** LLM calls; runs in < 1 min in CI | Post: "Catching checkout bugs without a device or an LLM" |
| M2 | **Diff-only AI review**: semantic diff vs `main`; send only changed screens to the LLM for fuzzy rules (wrong copy, misleading discount text). GitHub Action posts a PR comment. | Tokens per PR measured; compare to sending whole screenshots | Post + chart: tokens per PR, text-diff vs screenshot |
| M3 | **Compile-once flows**: App Model + ticket → Maestro YAML; CI replays without AI; LLM repairs on failure (max 2 tries), fix cached. | 20 flows; repair success rate; LLM calls per 100 CI runs | Post: "AI that writes the test once, not every run" |
| M4 | **Release matrix**: dark mode / font scale / Hindi / small screen; rules + pixel diff; AI only on changed pixels. | Matrix runtime and tokens measured | — |
| M5 | **Try it on a real e-commerce app** (with permission if it's company code), publish anonymised numbers. | Before/after: bugs caught pre-merge, test authoring time, tokens per PR | v1 release, Show HN, r/androiddev, Android Weekly, DroidCon CFP |
| M6 (later) | Production loop: crashes/reviews → screen → repro flow. | — | — |

## What we keep from Phase 0

- Spike D (semantics dump, ~61 tokens/screen, unlabelled-button detection) is the core of M1.
- Spike A result (86 s/screen on-device vision) is the evidence for "don't do runtime vision" — first blog post material.
- Spike B: AppFunctions locked to system apps on Motorola Android 16 — dropped.
- Spike C (AndroidWorld): dropped; not needed for this plan.

## Rules for ourselves

- Deterministic before AI. AI before vision. Vision only for canvas/WebView screens.
- Every milestone reports tokens, time, and bugs caught — no number, no post.
- Company code never goes into this public repo; only anonymised numbers.
