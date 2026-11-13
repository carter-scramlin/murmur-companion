# Beta 0.3.0-beta.2

Download the **install** APK pair from [GitHub Releases](https://github.com/carter-scramlin/murmur-companion/releases/tag/v0.3.0-beta.2). Debug APKs are also provided for troubleshooting. All four share the pinned persistent beta certificate and the application ID `dev.murmur.companion.beta`. The launcher name is **Murmur Beta**.

The new ID avoids earlier preview signing conflicts and allows both apps to coexist. Sign in again in the new beta. Your server chats are retained; cookies/drafts in the old app remain private to it. Install the matching beta watch APK too. See [INSTALL.md](INSTALL.md).

## Signing

The private beta key is retained on the signing host outside the repository. Only its public fingerprint is committed in `signing/beta-certificate.sha256`. The packager refuses to generate a replacement once that fingerprint exists. Keep an encrypted private backup of the key and password; never publish either. CI validation keys and developer debug keys cannot update public release downloads.

Publication can use prebuilt signed assets from the signing host: GitHub verifies signatures, manifests, checksums and a successful CI run for the source commit before creating a draft release and publishing all assets together. GitHub Actions signing secrets remain optional until configured with this same existing key. See [RELEASING.md](RELEASING.md).

## Tester pass

1. Install `phone-install.apk` alongside the old app; check that **Murmur Beta** opens. Install the matching `wear-install.apk` on your paired Pixel Watch.
2. Connect and use native Murmur password sign-in, or Workspace for an Umbrel gateway. Confirm native agents and history load.
3. Send a text message, photo and file. Observe Thinking → Working → Replying → Finished, tool summaries, plans and Stop. Confirm the agent received the file contents.
4. Test an approval, clarification and private-value request. Nothing should be answered automatically; private values must not appear in the read cache.
5. Test pin/archive/restore/delete on disposable chats, older history pages, model selection and personality editing on a test agent.
6. Enable offline reading, open a chat, close/reopen the app without network, then reconnect. Verify one prompt submission only. Disable offline reading and verify saved copies are removed.
7. Check system/dark/light theme, large fonts, reduced motion, dictation, share and read-aloud.
8. While an agent works, select **Notify when this reply finishes**, turn off the screen, and test completion, a request for input and a Tailscale/network interruption. Stop following from its notification.
9. Test reviewed watch dictation, reply reading, Stop and phone handoff.
10. Install the matching debug phone APK over the install APK, then switch back. Check the same certificate and retained local settings.

Physical phone/Pixel Watch, live Murmur compatibility, battery/background behavior, accessibility and Tailscale cellular/Wi-Fi transitions remain hardware validation gates. Automated tests and runtime renders do not substitute for those checks. The beta retains the advanced workspace features listed in [FEATURES.md](FEATURES.md); full native desktop parity is not claimed.
