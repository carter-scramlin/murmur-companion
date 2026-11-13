# Beta candidate: 0.3.0-beta.1

This candidate is ready for limited testing of the native character and conversation experience. It is **not yet a signed, validated broad beta channel**.

## What is ready

- Native Murmur Buddy characters on the phone and watch. The phone uses the agent's hue metadata, deterministic shape, expressions and gentle motion while working.
- Explicit Thinking / Working / Replying / Needs you / Finished states. A persistent activity card shows the current operation, elapsed time and Stop. The status is also written as text, so it does not depend on animation or colour.
- Reduced-motion preference, respect for Android animation settings and battery saver; watch characters remain static to save power.
- Bounded read-only reconnects with no automatic prompt resubmission, cached-chat completion updates, cancellable slow navigation, scoped drafts saved after a short debounce and memory bounds.
- Native Android tests, upstream contract fixtures, lint, APK signature checks and optimized release builds.
- Feedback template and Settings → Send beta feedback. The report contains app/Android/device versions; it excludes chats, server origin and credentials. Sharing only opens the Android share sheet; you choose where to send it.

## Install the candidate

Download both **candidate** APKs from the same GitHub Release, or the combined candidate ZIP. They are optimized, non-debuggable release builds signed with the build runner's **temporary preview key**. The `.apk` filename distinguishes them from the earlier debug builds. Install the phone APK normally and install the watch APK with wireless ADB as described in [INSTALL.md](INSTALL.md).

A change from an earlier preview signing key may require uninstalling **both** old apps first. Uninstall removes local sign-in, preferences and drafts; server chats remain. Keep the matching pair together. A temporary preview key does not support stable upgrades between independently built candidates.

## Before a broad beta

| Gate | Current status |
|---|---|
| Automated tests, lint and optimized builds | Checked in CI; see [validation](VALIDATION.md) |
| Fixed private release key, backed up securely | Required; configure the signed-build workflow before distributing a stable update channel |
| GitHub signing secrets | Setup guide prepared; desktop GitHub configuration was unavailable during candidate preparation |
| Real phone and Pixel Watch installation | Still required |
| Live Murmur sign-in and API compatibility | Still required |
| Tailscale Wi-Fi/cellular transitions and phone screen off | Still required |
| TalkBack, large fonts, reduced motion and both watch sizes | Still required |
| Update with the same distribution key | Still required |

## Stable signing setup

Follow [RELEASING.md](RELEASING.md) to create and back up a private keystore outside the repository. In this repository's **Settings → Secrets and variables → Actions**, add `MURMUR_KEYSTORE_B64`, `MURMUR_STORE_PASSWORD`, `MURMUR_KEY_ALIAS` and `MURMUR_KEY_PASSWORD`. Never put these values in an issue, commit or release asset.

Run **Build signed beta** from Actions. It builds and verifies the matching optimized release pair, package and checksums as a workflow artifact. It does not silently replace the candidate's APKs or publish a production release. Before publishing a signed beta, choose a new version/versionCode, complete the hardware checks and publish that matching signed pair under its own tag. Preserve that private key for every later beta update.

## Short tester pass

1. Install both candidate APKs, finish phone sign-in, choose an agent, and confirm the character's colour matches Murmur.
2. Send a short prompt and a tool-using prompt. Check Thinking → Working → Replying → Finished, elapsed time and Stop.
3. Trigger a safe approval/question; confirm Needs you stays visible and nothing is auto-approved.
4. Leave a working chat, then return. Turn off networking, reconnect and verify the prompt appears once.
5. Type a draft, rotate the phone, background it and return. Try reduced motion and larger fonts.
6. Use the watch's voice review and reply reader. Continue on phone and confirm the correct Watch chat opens.
7. Report failures using the feedback form without private details.

Advanced groups, models, memories, routines, integrations, attachments and rich tool content still use Full Murmur workspace. See [FEATURES.md](FEATURES.md); complete native desktop feature parity is not claimed.
