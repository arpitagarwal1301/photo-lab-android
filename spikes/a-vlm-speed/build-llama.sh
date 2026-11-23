#!/usr/bin/env bash
# Spike A, step 1: cross-compile llama.cpp's multimodal CLI (llama-mtmd-cli) for arm64 Android.
# Output: .work/build-android/bin/llama-mtmd-cli (static, no .so files to push).
#
# Env overrides:
#   ANDROID_HOME  SDK path         (default ~/Library/Android/sdk)
#   ANDROID_NDK   NDK path         (default newest NDK in the SDK)
#   LLAMA_REF     branch/tag       (default master; pin a tag once results matter)
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
SDK="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
NDK="${ANDROID_NDK:-$(ls -d "$SDK"/ndk/* | sort -V | tail -1)}"
CMAKE_BIN="$(ls -d "$SDK"/cmake/* | sort -V | tail -1)/bin"
LLAMA_REF="${LLAMA_REF:-master}"
WORK="$HERE/.work"
SRC="$WORK/llama.cpp"
BUILD="$WORK/build-android"

echo "NDK:   $NDK"
echo "CMake: $CMAKE_BIN"
mkdir -p "$WORK"

if [ ! -d "$SRC/.git" ]; then
  git clone --depth 1 --branch "$LLAMA_REF" https://github.com/ggml-org/llama.cpp "$SRC"
fi
git -C "$SRC" rev-parse --short HEAD | tee "$WORK/llama-commit.txt"

# Snapdragon 7s Gen 2 = Cortex-A78 + A55: ARMv8.2-A with dotprod and fp16, no i8mm.
ARCH_FLAGS="-march=armv8.2-a+dotprod+fp16"

"$CMAKE_BIN/cmake" -S "$SRC" -B "$BUILD" -G Ninja \
  -DCMAKE_MAKE_PROGRAM="$CMAKE_BIN/ninja" \
  -DCMAKE_TOOLCHAIN_FILE="$NDK/build/cmake/android.toolchain.cmake" \
  -DANDROID_ABI=arm64-v8a \
  -DANDROID_PLATFORM=android-28 \
  -DCMAKE_BUILD_TYPE=Release \
  -DBUILD_SHARED_LIBS=OFF \
  -DGGML_NATIVE=OFF \
  -DGGML_OPENMP=OFF \
  -DGGML_LLAMAFILE=OFF \
  -DLLAMA_CURL=OFF \
  -DCMAKE_C_FLAGS="$ARCH_FLAGS" \
  -DCMAKE_CXX_FLAGS="$ARCH_FLAGS"

"$CMAKE_BIN/cmake" --build "$BUILD" --target llama-mtmd-cli -j

ls -lh "$BUILD/bin/llama-mtmd-cli"
echo "Built. Next: ./run-on-device.sh <model.gguf> <mmproj.gguf>"
