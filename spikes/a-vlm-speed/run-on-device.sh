#!/usr/bin/env bash
# Spike A, step 2: time one "agent step" of a 2B vision model on the connected phone.
#
# Usage: ./run-on-device.sh <model.gguf> <mmproj.gguf> [runs=3] [threads=4]
#
# Before running: open any app on the phone (e.g. Clock) — the script screenshots
# whatever is on screen and asks the model to list the tappable elements, like a real step.
set -euo pipefail

MODEL="${1:?model .gguf path}"
MMPROJ="${2:?mmproj .gguf path}"
RUNS="${3:-3}"
THREADS="${4:-4}"   # 4 = the A78 big cores on Snapdragon 7s Gen 2

HERE="$(cd "$(dirname "$0")" && pwd)"
SDK="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
ADB="$SDK/platform-tools/adb"
BIN="$HERE/.work/build-android/bin/llama-mtmd-cli"
DEV_DIR=/data/local/tmp/spike-a
STAMP="$(date +%Y%m%d-%H%M%S)"
OUT_DIR="$HERE/../../results/phase0/spike-a-$STAMP"
PROMPT='You are a phone agent. List every tappable element on this screen as JSON: [{"label": "...", "x": 0, "y": 0}]. JSON only.'

[ -x "$BIN" ] || { echo "Build first: ./build-llama.sh"; exit 1; }
"$ADB" get-state >/dev/null 2>&1 || { echo "No device. Connect the phone with USB debugging on."; exit 1; }
mkdir -p "$OUT_DIR"

{
  echo "date=$STAMP"
  echo "model=$("$ADB" shell getprop ro.product.model | tr -d '\r')"
  echo "soc=$("$ADB" shell getprop ro.soc.model | tr -d '\r')"
  echo "android=$("$ADB" shell getprop ro.build.version.release | tr -d '\r')"
  echo "sdk_int=$("$ADB" shell getprop ro.build.version.sdk | tr -d '\r')"
  echo "mem_total=$("$ADB" shell grep MemTotal /proc/meminfo | tr -d '\r')"
  echo "llama_commit=$(cat "$HERE/.work/llama-commit.txt" 2>/dev/null || echo unknown)"
  echo "model_file=$(basename "$MODEL")"
  echo "mmproj_file=$(basename "$MMPROJ")"
  echo "threads=$THREADS"
} | tee "$OUT_DIR/device.txt"

"$ADB" exec-out screencap -p > "$OUT_DIR/screen.png"
echo "Screenshot saved: $OUT_DIR/screen.png"

push_if_changed() {  # skip re-pushing multi-GB files that are already there
  local src="$1" dst="$2"
  local local_size remote_size
  local_size=$(stat -f%z "$src")
  remote_size=$("$ADB" shell "stat -c %s $dst 2>/dev/null" | tr -d '\r' || true)
  if [ "$local_size" != "$remote_size" ]; then "$ADB" push "$src" "$dst"; else echo "up to date: $dst"; fi
}

"$ADB" shell mkdir -p "$DEV_DIR"
push_if_changed "$BIN" "$DEV_DIR/llama-mtmd-cli"
push_if_changed "$HERE/measure.sh" "$DEV_DIR/measure.sh"
push_if_changed "$MODEL" "$DEV_DIR/model.gguf"
push_if_changed "$MMPROJ" "$DEV_DIR/mmproj.gguf"
"$ADB" push "$OUT_DIR/screen.png" "$DEV_DIR/screen.png" >/dev/null
# The prompt contains quotes, so ship it as a file instead of escaping it through adb shell.
printf '%s' "$PROMPT" > "$OUT_DIR/prompt.txt"
"$ADB" push "$OUT_DIR/prompt.txt" "$DEV_DIR/prompt.txt" >/dev/null
"$ADB" shell chmod 755 "$DEV_DIR/llama-mtmd-cli" "$DEV_DIR/measure.sh"

thermal() { "$ADB" shell dumpsys thermalservice 2>/dev/null | grep -m1 -i "Thermal Status" | tr -d '\r'; }

for i in $(seq 1 "$RUNS"); do
  echo "--- run $i/$RUNS ---"
  echo "thermal_before: $(thermal)" > "$OUT_DIR/run-$i.txt"
  "$ADB" shell "cd $DEV_DIR && ./measure.sh run.log ./llama-mtmd-cli \
      -m model.gguf --mmproj mmproj.gguf --image screen.png \
      -p \"\$(cat prompt.txt)\" -n 128 -c 4096 -t $THREADS --temp 0"
  "$ADB" shell cat "$DEV_DIR/run.log" | tr -d '\r' >> "$OUT_DIR/run-$i.txt"
  echo "thermal_after: $(thermal)" >> "$OUT_DIR/run-$i.txt"
  # Timing lines printed by mtmd / llama_perf, plus our memory line.
  grep -E "encoded in|decoded|load time|prompt eval time|eval time|total time|PEAK_RSS_KB|EXIT_CODE|thermal_" "$OUT_DIR/run-$i.txt" || true
done

echo
echo "Logs: $OUT_DIR — copy the numbers into results/phase0.md (section A)."
