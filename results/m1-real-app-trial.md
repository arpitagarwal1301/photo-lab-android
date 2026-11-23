# M1 — first run on a real e-commerce app (anonymised)

_2026-11-23. One feature module (membership / rewards wallet) of a large production Android e-commerce app. Run locally on a throwaway branch in a separate worktree; no code left the laptop; no LLM used._

## Setup cost

- Changes to the module: 1 Gradle plugin line, 3 test dependencies, 2 test files copied. No production code touched.
- The app module needs signing credentials to configure, so the run used `--configure-on-demand` to build only what the feature module needs.

## Numbers

| Metric | Value |
|---|---|
| `@Preview`s found and rendered automatically | 41 |
| Time, first run / re-run | 46 s / 10 s |
| Compact text per screen | avg ~43 tokens, max ~150 tokens |
| LLM calls | 0 |
| Findings (after two checker fixes) | 4 |

## Findings, triaged by hand

| Finding | Verdict |
|---|---|
| Invisible "clear" icon (alpha 0, disabled) still in the accessibility tree as an unlabelled button | **Real a11y bug** — TalkBack can focus an invisible control |
| Search text field has no accessible label (placeholder drawn as separate text) | **Real a11y bug** (minor) |
| "₹1006" without thousands separator | Preview data only — but the same string comes from the server in production, so worth a server-side check |
| "Total eligible ₹50" vs three ₹25 rows | **False alarm** — one row is already claimed; rule must learn "eligible / claimed" |

## Checker gaps the real app exposed (fixed)

- Text fields: their typed value wasn't read, causing 3 false "no label" alarms → now reads `EditableText`.
- ₹0 balances/rewards were flagged as bad prices → rule now flags only negative amounts.
- Sample-shop check after fixes: still 10/10 planted bugs, 0 false alarms.

## What this tells us

- The approach works on a real codebase with near-zero setup and no device.
- It can only check what has a `@Preview`. Cart and payment modules have just 3 previews between them, so **checkout coverage needs previews (or Roborazzi rendering real screens with fake state)** before money rules can find anything there.
