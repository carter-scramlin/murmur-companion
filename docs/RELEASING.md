# Signing and beta releases

Phone and watch must share an application ID and signing certificate. Preserve and securely back up a private distribution key outside the repository before starting a stable beta update channel. Losing it prevents updates to installed copies signed with that key.

## Public candidate

The [0.3.0 beta candidate](https://github.com/carter-scramlin/murmur-companion/releases/tag/v0.3.0-beta.1) provides optimized, non-debuggable release APKs, the matching ZIP, guide, checksums and certificate fingerprint. **Build and publish beta candidate** runs tests/lint/debug builds, then builds optimized APKs using the runner's temporary Android preview key. Published assets are preserved on reruns. An incomplete draft must be inspected/removed before retrying.

The temporary candidate key is not a stable signing channel. Independently built candidates may require uninstalling old apps. The website links to fixed release assets and does not rebuild them on Pages deploys. See [BETA.md](BETA.md) for the gates before broad distribution.

## Create a private distribution key

Run JDK's `keytool` and let it prompt for the password:

```bash
keytool -genkeypair -keystore /secure/path/murmur-companion.jks -alias murmur -keyalg RSA -keysize 4096 -validity 10000
```

Never commit, attach or post the keystore or its passwords. Back up the private key securely. Set these environment variables in a trusted shell/secret manager:

| Variable | Value |
|---|---|
| `MURMUR_KEYSTORE` | Absolute path to the `.jks` file |
| `MURMUR_STORE_PASSWORD` | Keystore password |
| `MURMUR_KEY_ALIAS` | `murmur`, if using the example |
| `MURMUR_KEY_PASSWORD` | Key password |

With JDK 17, Android SDK 36 and `ANDROID_HOME` configured, add `build-tools/36.0.0` to PATH and run:

```bash
./scripts/package-release.sh
```

The script runs tests, lint and optimized signed release builds. It verifies both signatures, checks that the certificates and versions match and rejects debuggable APKs. The output is `artifacts/signed-beta-VERSION/`, containing `murmur-companion-phone.apk`, `murmur-companion-wear.apk`, a signed-beta ZIP, install guide, signing fingerprint and `SHA256SUMS`. It publishes nothing.

## GitHub signed beta workflow

In **Settings → Secrets and variables → Actions**, configure:

- `MURMUR_KEYSTORE_B64`: base64 encoding of the private distribution keystore.
- `MURMUR_STORE_PASSWORD`, `MURMUR_KEY_ALIAS`, `MURMUR_KEY_PASSWORD`.

Only trusted collaborators should run **Build signed beta**. It decodes the key into the runner's temporary directory, builds and verifies the release pair, removes the key and uploads only the signed-beta package as an Actions artifact. It does not replace the public candidate or automatically publish a release.

Before publishing a signed beta, complete the [hardware/Tailscale checks](BETA.md), increment both app versionNames and versionCodes, update notes/website links, build using the backed-up private key and publish the reviewed pair under a new tag. Never replace a published version with APKs signed by another key.

## Build verification key

CI's `scripts/verify-optimized.sh` creates a disposable validation key solely to exercise R8/resource shrinking and APK verification. It is deleted on exit. Those test-signed APKs must not be distributed and are not uploaded with the debug artifacts. This is separate from the stable beta signing workflow.
