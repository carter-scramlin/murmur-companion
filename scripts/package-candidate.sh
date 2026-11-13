#!/usr/bin/env bash
# Optimized testing build. A dedicated temporary candidate key is deleted after packaging.
set -euo pipefail
cd "$(dirname "$0")/.."
candidate_signing_dir=$(mktemp -d)
trap 'rm -rf "$candidate_signing_dir"' EXIT
export MURMUR_KEYSTORE="$candidate_signing_dir/candidate.jks"
: "${ANDROID_HOME:?Set ANDROID_HOME to the Android SDK}"
export MURMUR_STORE_PASSWORD
MURMUR_STORE_PASSWORD=$(python3 -c 'import secrets; print(secrets.token_urlsafe(32))')
export MURMUR_KEY_PASSWORD="$MURMUR_STORE_PASSWORD" MURMUR_KEY_ALIAS=candidate
if [[ "${GITHUB_ACTIONS:-}" == true ]]; then echo "::add-mask::$MURMUR_STORE_PASSWORD"; fi
keytool -genkeypair -keystore "$MURMUR_KEYSTORE" -storepass:env MURMUR_STORE_PASSWORD -keypass:env MURMUR_KEY_PASSWORD -alias candidate -keyalg RSA -keysize 3072 -validity 3650 -dname 'CN=Murmur Companion Candidate, OU=Temporary preview key' >/dev/null 2>&1
./gradlew :phone:assembleRelease :wear:assembleRelease --console=plain
python3 scripts/prepare-candidate.py
