#!/usr/bin/env bash
# Verify R8/resource shrinking with a disposable validation key. Never publish these APKs.
set -euo pipefail
cd "$(dirname "$0")/.."
validation_dir=$(mktemp -d)
trap 'rm -rf "$validation_dir"' EXIT
export MURMUR_KEYSTORE="$validation_dir/validation.jks"
export MURMUR_STORE_PASSWORD
MURMUR_STORE_PASSWORD=$(python3 -c 'import secrets; print(secrets.token_urlsafe(32))')
export MURMUR_KEY_PASSWORD="$MURMUR_STORE_PASSWORD"
export MURMUR_KEY_ALIAS=validation
keytool -genkeypair -keystore "$MURMUR_KEYSTORE" -storepass:env MURMUR_STORE_PASSWORD -keypass:env MURMUR_KEY_PASSWORD -alias validation -keyalg RSA -keysize 2048 -validity 2 -dname 'CN=Murmur Build Validation' >/dev/null 2>&1
./gradlew :phone:assembleRelease :wear:assembleRelease --console=plain
sdk_path="${ANDROID_HOME:-${ANDROID_SDK_ROOT:?Set ANDROID_HOME or ANDROID_SDK_ROOT}}"
for module in phone wear; do
  apk="$module/build/outputs/apk/release/$module-release.apk"
  "$sdk_path/build-tools/36.0.0/apksigner" verify "$apk"
  if "$sdk_path/build-tools/36.0.0/aapt" dump badging "$apk" | grep -q '^application-debuggable'; then
    echo 'Release validation failed: APK is debuggable.' >&2
    exit 1
  fi
done
echo 'Both optimized release APKs passed verification with a disposable validation key. Do not distribute them.'
