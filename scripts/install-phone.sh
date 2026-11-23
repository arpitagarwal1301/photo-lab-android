#!/usr/bin/env bash
# Build Photo Lab, install it on the USB-connected phone, and copy the models over USB (skips files already there).
# Usage: scripts/install-phone.sh
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
ADB="${ADB:-$HOME/Library/Android/sdk/platform-tools/adb}"
PKG=dev.spikeai.photos
export JAVA_HOME="${JAVA_HOME:-$HOME/Applications/Android Studio.app/Contents/jbr/Contents/Home}"

"$ROOT/gradlew" -p "$ROOT" :photos-app:assembleDebug -q
"$ADB" install -r "$ROOT/photos-app/build/outputs/apk/debug/photos-app-debug.apk"

FILES=(mobileclip/vision_model.onnx mobileclip/text_model_q8.onnx mobileclip/tokenizer.json minilm/model.onnx minilm/vocab.txt minilm/model.safetensors)
for f in "${FILES[@]}"; do
  src="$ROOT/.work/models/$f"
  [ -f "$src" ] || { echo "missing $src (download it first)"; exit 1; }
  size=$(stat -f%z "$src")
  have=$("$ADB" shell run-as $PKG stat -c %s "files/models/$f" 2>/dev/null | tr -d '\r' || true)
  if [ "$have" = "$size" ]; then echo "ok   $f"; continue; fi
  echo "copy $f ($((size / 1000000)) MB)"
  "$ADB" push -q "$src" /data/local/tmp/spikeai.tmp
  "$ADB" shell run-as $PKG sh -c "'mkdir -p files/models/$(dirname "$f") && cp /data/local/tmp/spikeai.tmp files/models/$f'"
done
"$ADB" shell rm -f /data/local/tmp/spikeai.tmp
"$ADB" shell am start -n $PKG/.MainActivity >/dev/null
echo "installed and started"
