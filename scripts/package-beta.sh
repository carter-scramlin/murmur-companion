#!/usr/bin/env bash
# Persistent private beta key. Never rotate this key to make a build pass.
set -euo pipefail
cd "$(dirname "$0")/.."
: "${ANDROID_HOME:?Set ANDROID_HOME to the Android SDK}"
beta_key_dir="${MURMUR_BETA_KEY_DIR:-${XDG_DATA_HOME:-$HOME/.local/share}/murmur-companion/signing}"
umask 077
mkdir -p "$beta_key_dir"
export MURMUR_KEYSTORE="$beta_key_dir/beta.jks"
export MURMUR_KEY_ALIAS=beta MURMUR_STORE_PASSWORD MURMUR_KEY_PASSWORD
if [[ ! -f "$MURMUR_KEYSTORE" ]]; then
  if [[ -f signing/beta-certificate.sha256 ]]; then
    echo 'The pinned beta signing key is missing. Restore it from a private backup; do not generate a replacement.' >&2
    exit 1
  fi
  python3 -c 'import secrets; print(secrets.token_urlsafe(48))' > "$beta_key_dir/password"
  MURMUR_STORE_PASSWORD=$(cat "$beta_key_dir/password")
  MURMUR_KEY_PASSWORD="$MURMUR_STORE_PASSWORD"
  keytool -genkeypair -keystore "$MURMUR_KEYSTORE" -storepass:env MURMUR_STORE_PASSWORD -keypass:env MURMUR_KEY_PASSWORD -alias beta -keyalg RSA -keysize 3072 -validity 10000 -dname 'CN=Murmur Companion Beta, OU=Community Android Client' >/dev/null 2>&1
else
  MURMUR_STORE_PASSWORD=$(cat "$beta_key_dir/password")
  MURMUR_KEY_PASSWORD="$MURMUR_STORE_PASSWORD"
fi
keytool -exportcert -keystore "$MURMUR_KEYSTORE" -alias beta -storepass:env MURMUR_STORE_PASSWORD | sha256sum | cut -d' ' -f1 > "$beta_key_dir/certificate.sha256"
if [[ -f signing/beta-certificate.sha256 ]]; then
  cmp signing/beta-certificate.sha256 "$beta_key_dir/certificate.sha256"
else
  cp "$beta_key_dir/certificate.sha256" signing/beta-certificate.sha256
  chmod 644 signing/beta-certificate.sha256
fi
if [[ "${GITHUB_ACTIONS:-}" == true ]]; then echo "::add-mask::$MURMUR_STORE_PASSWORD"; fi
./gradlew :core:test :phone:testDebugUnitTest :wear:testDebugUnitTest :phone:lintDebug :wear:lintDebug :phone:assembleDebug :wear:assembleDebug :phone:assembleRelease :wear:assembleRelease --console=plain
python3 scripts/prepare-beta.py
