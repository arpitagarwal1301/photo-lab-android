# Spike C — Does AndroidWorld run on this Mac?

**Why:** AndroidWorld (116 tasks, 20 apps, rewards checked from system state via adb) is the benchmark papers report on. Running a subset makes our numbers comparable.

**Open risk:** this Mac is Apple Silicon (M5), so the emulator runs **arm64** images. AndroidWorld's docs target a Pixel 6 on Android 13 (API 33). We need to find out whether its task apps install and its setup completes on an arm64 image.

## Steps

1. Install uv: `brew install uv`
2. Create an AVD in Android Studio → Device Manager: **Pixel 6, API 33, arm64-v8a, Google APIs**. Your existing `Medium_Phone_API_34` / `_36` AVDs are a fallback, but not the documented setup.
3. Run the setup:
   ```bash
   ./setup.sh
   ```
4. Start the emulator with gRPC on port 8554 (command printed by the script).
5. Run the one-time emulator setup plus **one** task. Check `run.py --help` and the repo README for the current agent names. Their baseline agents need a cloud model API key; that's fine for Phase 0.
6. Record in [`results/phase0.md`](../../results/phase0.md): setup passed? apps installed? one task ran end-to-end?

## How we'll use it later (Phase 6)

We'll write our own agent class in their harness. Its decisions run on the Mac and call the same prompt, pruner and tier logic as the phone app, so success rates are comparable. On-device latency gets measured separately on the real phone.
