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
mkdir -p artifacts
cp phone/build/outputs/apk/release/phone-release.apk artifacts/murmur-companion-phone.apk
cp wear/build/outputs/apk/release/wear-release.apk artifacts/murmur-companion-wear.apk
apksigner verify --verbose artifacts/murmur-companion-phone.apk
apksigner verify --verbose artifacts/murmur-companion-wear.apk
phone_certificate=$(apksigner verify --print-certs artifacts/murmur-companion-phone.apk | sed -n 's/^Signer #1 certificate SHA-256 digest: //p')
wear_certificate=$(apksigner verify --print-certs artifacts/murmur-companion-wear.apk | sed -n 's/^Signer #1 certificate SHA-256 digest: //p')
test -n "$phone_certificate"
test "$phone_certificate" = "$wear_certificate" || { echo 'Phone and watch signing certificates differ.' >&2; exit 1; }
cd artifacts
sha256sum murmur-companion-phone.apk murmur-companion-wear.apk > SHA256SUMS
echo 'Signed APK pair and checksums are in artifacts/. Nothing has been published.'
