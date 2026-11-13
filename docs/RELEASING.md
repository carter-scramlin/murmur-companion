# Signing and releases

Phone and watch must use the same app ID and signing certificate. A permanent signing key is essential for updates. Keep it outside the repository and back it up securely. Losing it means installed copies cannot accept updates signed by a new key.

## Create a personal release key

Use JDK's `keytool` and let it prompt for the password:

```bash
keytool -genkeypair -keystore /secure/path/murmur-companion.jks -alias murmur -keyalg RSA -keysize 4096 -validity 10000
```

Set these environment variables in your shell or secret manager without committing their values:

| Variable | Value |
|---|---|
| `MURMUR_KEYSTORE` | Absolute path to the `.jks` file |
| `MURMUR_STORE_PASSWORD` | Keystore password |
| `MURMUR_KEY_ALIAS` | `murmur`, if using the command above |
| `MURMUR_KEY_PASSWORD` | Key password |

Then run:

```bash
./scripts/package-release.sh
```

The script runs tests, lint, signed release builds, verifies both APK signatures, compares certificates and writes `artifacts/SHA256SUMS`. It requires the Android SDK's `apksigner` on PATH (usually inside `build-tools/36.0.0`). It will fail early if signing variables or `apksigner` are missing. It does not publish anything.

For local testing use debug builds. Debug APKs built in one CI job share a key, but that key is generated afresh on a clean runner and is unsuitable for a stable update channel.

## GitHub Actions

The CI workflow runs tests, lint and both debug builds, then uploads one ZIP containing both APKs and checksums. Keep that pair together.

The manual **Signed preview** workflow uses repository secrets:

- `MURMUR_KEYSTORE_B64`: base64 encoding of the release keystore.
- `MURMUR_STORE_PASSWORD`, `MURMUR_KEY_ALIAS`, `MURMUR_KEY_PASSWORD`.

Only trusted repository collaborators should run it. It uploads signed APK artifacts and validation reports; it does not create a public release. Before publishing, complete the physical-device checklist in [VALIDATION.md](VALIDATION.md), choose a version, update both modules' `versionName`/`versionCode`, update the changelog, then attach the two APKs and checksums to your reviewed GitHub Release manually.

No signed distribution key is created or committed by this preview project.
