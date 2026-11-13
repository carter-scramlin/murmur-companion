#!/usr/bin/env bash
# Optimized testing build. Uses this runner's private Android preview key, never a stable distribution key.
set -euo pipefail
cd "$(dirname "$0")/.."
export MURMUR_KEYSTORE="${ANDROID_USER_HOME:-$HOME/.android}/debug.keystore"
: "${ANDROID_HOME:?Set ANDROID_HOME to the Android SDK}"
test -f "$MURMUR_KEYSTORE" || { echo 'Build the matching debug pair first to initialize the runner preview key.' >&2; exit 1; }
export MURMUR_STORE_PASSWORD=android MURMUR_KEY_PASSWORD=android MURMUR_KEY_ALIAS=androiddebugkey
./gradlew :phone:assembleRelease :wear:assembleRelease --console=plain
python3 scripts/prepare-candidate.py
