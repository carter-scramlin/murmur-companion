#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
: "${MURMUR_KEYSTORE:?Set the path to your release keystore}"
: "${MURMUR_STORE_PASSWORD:?Set the keystore password}"
: "${MURMUR_KEY_ALIAS:?Set the signing key alias}"
: "${MURMUR_KEY_PASSWORD:?Set the signing key password}"
command -v apksigner >/dev/null || { echo 'Add Android SDK build-tools/36.0.0 to PATH.' >&2; exit 1; }
test -f "$MURMUR_KEYSTORE"
./gradlew :core:test :phone:testDebugUnitTest :wear:testDebugUnitTest :phone:lintRelease :wear:lintRelease :phone:assembleRelease :wear:assembleRelease --console=plain
python3 scripts/prepare-candidate.py --signed
echo 'Signed beta pair and checksums are in artifacts/signed-beta-VERSION/. Nothing has been published.'
