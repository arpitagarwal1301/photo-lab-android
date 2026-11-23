# Spike A — Is a 2B vision model fast enough on a mid-range phone?

**Question:** how long does one agent step (screenshot → "list the tappable elements") take with a ~2B vision-language model on the Motorola Edge 60 Stylus (Snapdragon 7s Gen 2, CPU only)?

**Kill/pivot rule:** if a step takes more than ~15 s, Tier 3 (vision) goes cloud-only and the on-device story focuses on the tree tier.

## Steps

1. Build the CLI (clones llama.cpp into `.work/`, about 5 minutes):
   ```bash
   ./build-llama.sh
   ```
2. Get model files (not committed — `*.gguf` is git-ignored). Start with **Qwen3-VL-2B-Instruct** at Q4_K_M plus its `mmproj` file (Q8_0). Check the exact Hugging Face repo and filenames before downloading. Arm's learning path uses this pair: https://learn.arm.com/learning-paths/mobile-graphics-and-gaming/ai-portal-mobile-vision-language/
   Later, try **GUI-Owl-1.5-2B** (MIT, built on Qwen3-VL); there's no official GGUF yet, so it needs `convert_hf_to_gguf.py`.
3. Connect the phone (USB debugging on), open an app such as Clock, then:
   ```bash
   ./run-on-device.sh /path/to/model-Q4_K_M.gguf /path/to/mmproj-Q8_0.gguf
   ```
4. Copy the numbers into [`results/phase0.md`](../../results/phase0.md).

## What it measures

- Image encode time and prompt/decode speed (from llama.cpp's own timing output).
- Peak memory (`VmHWM`, polled once a second by `measure.sh` on the phone).
- Thermal status before and after each run (run 3 tells you about sustained use).

## Variations worth one extra run each

- `threads` = 4 vs 6 vs 8 (the small A55 cores can slow things down).
- A cropped or downscaled screenshot — this previews the "changed-region crop" idea from the plan.
