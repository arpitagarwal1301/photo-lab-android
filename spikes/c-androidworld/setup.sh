#!/usr/bin/env bash
# Spike C: set up the AndroidWorld benchmark harness on this Mac.
# Needs: uv (brew install uv), git, an Android emulator AVD (see README).
# Verify each command against the live README: https://github.com/google-research/android_world
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
WORK="$HERE/.work"
REPO="$WORK/android_world"

command -v uv >/dev/null || { echo "Install uv first:  brew install uv"; exit 1; }
mkdir -p "$WORK"

if [ ! -d "$REPO/.git" ]; then
  git clone --depth 1 https://github.com/google-research/android_world "$REPO"
fi
git -C "$REPO" rev-parse --short HEAD | tee "$WORK/android-world-commit.txt"

cd "$REPO"
uv venv --python 3.11 .venv
# shellcheck disable=SC1091
source .venv/bin/activate
uv pip install -r requirements.txt
uv pip install -e .

cat <<EOF

AndroidWorld installed in: $REPO
Next (see README in this folder):
  1. Start the emulator with gRPC:
     ~/Library/Android/sdk/emulator/emulator -avd <AVD_NAME> -no-snapshot -grpc 8554
  2. One-time setup + a single task (activate the venv first: source $REPO/.venv/bin/activate):
     python run.py --perform_emulator_setup --tasks=<ONE_TASK_NAME> --agent_name=<AGENT>
EOF
