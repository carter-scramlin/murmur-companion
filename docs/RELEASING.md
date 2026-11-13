# Building and publishing beta downloads

Public beta APKs use **one persistent private key**, not Android's default debug key and not a disposable runner key. Both modules use `dev.murmur.companion.beta`. The public certificate fingerprint is pinned in `signing/beta-certificate.sha256`; its presence forbids automatically generating a replacement key.

## Signing host

The signing host retains the keystore and password under `${XDG_DATA_HOME:-$HOME/.local/share}/murmur-companion/signing/` (`beta.jks` and `password`). The directory and files are private to the current user. Keep an encrypted backup outside this computer. Do not commit, upload as an artifact, paste into an issue, or publish these files. Only the certificate fingerprint is public.

Use JDK 17 and Android SDK 36, then run:

```bash
./scripts/package-beta.sh
```

It runs core/phone/watch tests and lint, builds optimized install and debuggable APKs with the same retained key, verifies all four certificates/identities/flags, and packages `artifacts/beta-VERSION/`. Output must be empty to prevent mixing builds. Move a previous output directory aside before rebuilding the same version. Public releases must use a fresh version name and increasing versionCode.

If the key is missing after the public fingerprint exists, restore the original private backup. **Never remove the pin or create a new key just to make a build pass.** Android upgrades and Wear Data Layer depend on certificate continuity. If the key is actually lost, a new package/channel and explicit migration are required.

## Publish locally signed assets with GitHub

When GitHub signing secrets are unavailable, the private key stays on the host. Commit the reviewed source to `main` and wait for its **Android & Wear OS** CI run to pass. On a separate `codex/publish-VERSION` branch based on that exact source commit, add only `staged-release/` containing the packaged assets and `PUBLICATION.json`:

```json
{"source_sha":"THE_TESTED_40_CHARACTER_SOURCE_COMMIT","version":"0.3.0-beta.2"}
```

Push the publication branch. **Publish verified beta downloads** checks that only staged assets differ from the source, verifies every checksum, package ID, certificate and debugging flag, requires successful source CI, then creates the GitHub Release as a draft, uploads all downloads, and publishes it. The release tag targets the source commit, not the publication branch. Binaries are absent from the normal source branch. The staging branch holds only already-public artifacts, never the key or password. Published releases are not overwritten.

## Optional GitHub builds with the same key

In repository Settings → Secrets and variables → Actions, configure:

- `MURMUR_KEYSTORE_B64`: base64 encoding of the existing `beta.jks`
- `MURMUR_STORE_PASSWORD`: the saved password
- `MURMUR_KEY_ALIAS`: `beta`
- `MURMUR_KEY_PASSWORD`: the saved password

Run **Build and publish signed beta**. It decodes the key only into the runner's temporary directory, builds/tests/verifies the same install/debug quartet, removes the key and publishes the assets. It fails if secrets are missing or the certificate differs from the pin. It never generates a fresh key. Repository signing secrets have not yet been configured; the public beta is signed on the local signing host.

## Verification keys and old releases

Ordinary CI debug APKs use the runner's developer key. `verify-optimized.sh` creates and deletes a temporary validation key to test R8 builds. Neither is a distribution key or an update-compatible public APK.

Versions before 0.3.0-beta.2 used disposable preview keys and `dev.murmur.companion`. The new beta ID allows coexistence and avoids their signature conflicts. Their private app data cannot be imported by another signing identity. Do not replace old release files to hide that history.
