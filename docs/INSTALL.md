# Install Murmur Companion

This repository builds two separate apps: one for an Android phone and one for a Wear OS watch. Installing the phone APK does not automatically install the watch app. There is no Play Store listing yet.

## 1. Check what you need

- An Android 9 or newer phone with a current Android System WebView.
- For the watch: a Pixel Watch or another round Wear OS 3+ watch already paired to that phone. The phone and watch need Google Play services.
- A working Murmur installation. Open it in your phone's browser first. This is a Murmur client; a bare Hermes dashboard URL is not the correct address.
- For remote access: Tailscale on the phone and the machine exposing Murmur, on the same tailnet. Follow [Tailscale setup](TAILSCALE.md).
- To install on the watch: a computer with [Android SDK Platform Tools](https://developer.android.com/tools/releases/platform-tools) for the `adb` command. You do not need Android Studio, Java or Gradle when downloading the built APKs. Source builds optionally require JDK 17 and Android SDK 36.

The native API integration was checked against Murmur source revisions `27253d221302af4c2b76b95f993428d698322519` and `2791c7e5234bae4ca0ad050c404b7b3913f687ab`. Older/newer servers may change the watch protocol. The upstream source was inspected, but a live server and physical Pixel Watch were not used for end-to-end verification. See [validation](VALIDATION.md).

## 2. Download both apps

Open [Releases → v0.3.0-beta.1](https://github.com/carter-scramlin/murmur-companion/releases/tag/v0.3.0-beta.1) and expand **Assets**. Download `murmur-companion-0.3.0-beta.1-candidate.zip` and extract it. It contains:

- `murmur-companion-phone-candidate.apk` — install on the phone.
- `murmur-companion-wear-candidate.apk` — install on the watch.
- Full documentation and `SHA256SUMS`.

You can also download the APKs individually from the same release. Keep the pair together because both apps must share the same signing certificate. **Skip the optional source build below** and continue with section 3.

To check downloaded files on Linux/macOS, run `sha256sum -c SHA256SUMS` (or `shasum -a 256 -c SHA256SUMS` on macOS) in the folder containing the matching files and checksum list. The release checksum list covers individual assets; the ZIP's internal list covers its two APKs.

### Optional: build from source

Open this repository in Android Studio and let Gradle sync finish. Set **Gradle JDK to 17** in Android Studio's Gradle settings. Set the Android SDK path through Android Studio or create a local, untracked `local.properties` file:

```properties
sdk.dir=/absolute/path/to/Android/Sdk
```

From the repository root, run:

```bash
./gradlew :core:test :phone:testDebugUnitTest :wear:testDebugUnitTest :phone:assembleDebug :wear:assembleDebug :phone:lintDebug :wear:lintDebug
```

On Windows, use `gradlew.bat` instead of `./gradlew`. The first build needs internet access to download dependencies. The outputs are:

| Install on | APK |
|---|---|
| Phone | `phone/build/outputs/apk/debug/phone-debug.apk` |
| Watch | `wear/build/outputs/apk/debug/wear-debug.apk` |

The public candidate uses optimized release APKs signed with a temporary preview key. The source-build outputs above are debug APKs for local development. If you are using a signed-beta workflow artifact, substitute `murmur-companion-phone.apk` and `murmur-companion-wear.apk` in the install commands.

Both apps must have the same application ID (`dev.murmur.companion`) **and signing certificate**. Building them together on one computer uses the same Android debug key. Download both APKs from the same GitHub Release (or the same CI run if using Actions artifacts); debug keys from different machines/runs can differ.

The candidate is suitable for limited testing. Follow [BETA.md](BETA.md) for the tester pass and broad-beta signing/device gates. Debug builds are suitable for development testing. For a stable personal installation across computers or CI runs, use a persistent release signing key as described in [releasing](RELEASING.md).

## 3. Install on your phone

**Using the file manager:** transfer `murmur-companion-phone-candidate.apk` to the phone, tap it, and allow that file manager to install unknown apps when Android asks. Install the APK. You can turn that permission off afterward.

**Using ADB:** enable Developer options on the phone by tapping Build number seven times in Android Settings → About phone. Turn on USB debugging, connect the phone over USB and accept the computer's debugging prompt. In a terminal with Android Platform Tools on PATH:

```bash
adb devices -l
adb -s PHONE_SERIAL install -r murmur-companion-phone-candidate.apk
```

Run commands from the folder containing your downloaded APKs. Replace `PHONE_SERIAL` with the serial shown by `adb devices -l`; do not type the placeholder literally. If you built from source, substitute the corresponding build output path from section 2.

## 4. Connect Murmur

1. Open **Murmur Companion** on the phone.
2. Enter the root URL that opens Murmur, including `https://` and any port. Recommended: `https://umbrel.your-tailnet.ts.net` from Tailscale Serve.
3. Tap **Connect to Murmur**. Native agent and conversation lists load directly from your server.
4. If sign-in is required, tap the sign-in card (or **••• → Connection & sign-in → Sign in / Workspace**). Finish the normal Umbrel/Murmur sign-in, then tap **Native app** at the top to return.
5. Use **Agents** to choose an agent, then open a conversation or tap **+** for a new one. Type or dictate a draft, review it, and tap the send arrow. The native Buddy character and activity card show Thinking / Working / Replying / Needs you, the current operation and elapsed time. Tap Stop to interrupt a running reply. Reduce motion in Settings if you prefer.
6. Native approval cards show the proposed action; tap the card for details. **Allow once** asks you to confirm the action. Clarification cards open a native form.
7. **Settings → Full Murmur workspace** keeps groups, models, memory, routines, integrations and rich content available. Sign-in and those advanced features use the explicit web screen.
8. Use **••• → Connection & sign-in → Reconnect** after a VPN interruption. Reconnect never resends a message. If a send was unconfirmed, inspect the chat before sending again.

For private LAN or tailnet HTTP URLs such as `http://100.80.10.20:PORT`, turn on **Allow a private HTTP connection** first. Public HTTP addresses are rejected. HTTPS is needed for web microphone capture and secure-context features. HTTP is not promoted automatically to HTTPS; use the actual reachable address.

Use **Settings → Pixel Watch** to check watch access and allow the optional handoff notification. This notification only helps you continue from the watch; it is not background agent Web Push.

## 5. Install on Pixel Watch

Menu wording varies by Wear OS version. Use the watch's Settings search if an item has moved.

1. On the watch, open **Settings → System → About → Versions**. Tap **Build number** seven times to enable developer options.
2. Open **Developer options**, enable **ADB debugging**, then **Wireless debugging**. The watch must be on Wi-Fi; place your computer on the same Wi-Fi network.
3. In Wireless debugging, tap **Pair new device**. Note the pairing IP address, pairing port and six-digit code.
4. Pair from the computer:

   ```bash
   adb pair WATCH_IP:PAIRING_PORT
   ```

   Enter the pairing code when prompted. Do not share this code.

5. Return to the main Wireless debugging screen. Its connection port is usually **different from the pairing port**. Connect using that address:

   ```bash
   adb connect WATCH_IP:CONNECTION_PORT
   adb devices -l
   adb -s WATCH_IP:CONNECTION_PORT install -r murmur-companion-wear-candidate.apk
   ```

6. Open **Murmur Companion** in the watch's app launcher. Keep the phone unlocked with Murmur signed in during the first check. Agents should load from your configured Hermes connections.
7. Choose an agent. Tap **Say something** or **Type a message**. Review the text, then tap **Send**. The watch does not send just because you dictated something.
8. Read the reply. It refreshes every five seconds only while a busy chat is visible; **Refresh reply** checks on demand. Turn the crown to scroll.
9. Disable wireless debugging and ADB debugging when finished installing. Normal use goes through the paired phone, not ADB.

**Continue on phone** posts a private handoff reminder on the phone when notification permission is granted. Tap the reminder to open that agent's existing **Watch chat** in the native phone UI. If the agent is unavailable or the chat does not exist, the app explains that rather than creating another chat. Approvals must be answered in the full phone interface.

## 6. Update or uninstall

Use `adb install -r` with the newer APK for each device. Install both from the same build/signing key. Server chats survive app updates and uninstalls.

`INSTALL_FAILED_UPDATE_INCOMPATIBLE` means the signing key differs from the installed copy. Rebuild with the original key. If you choose to uninstall the old copy, local sign-in, settings and drafts will be removed; sign in again afterward. Do not uninstall casually to work around signing issues.

To stop watch access without uninstalling, use **Settings → Pixel Watch → Turn access off** on the phone. To remove local account data, use **Settings → Forget this device**. Android Settings → Apps → Murmur Companion → Storage → Clear storage also clears local app data. Downloaded files you saved outside the app remain where you saved them.

## Troubleshooting

| Symptom | What to check |
|---|---|
| Phone cannot load Murmur | Test the exact URL in Chrome. Confirm Tailscale is connected, the server is online, and tailnet policy permits the port. `.local` names generally need local networking; use a full MagicDNS hostname or tailnet IP remotely. |
| HTTPS certificate error | Use the hostname on the certificate. Do not substitute the tailnet IP for a `*.ts.net` HTTPS hostname. The app will not bypass TLS errors. |
| Login page keeps returning | Check the address belongs to the app proxy or standalone Murmur. Allow the actual Umbrel sign-in page when prompted. Some identity providers disallow embedded browsers; see [feature limitations](FEATURES.md). |
| Watch has no agents | Open the phone app and finish sign-in. Check watch access is on, both APKs have identical package/signature, Google Play services is available, and the watch is paired to this phone. |
| Watch times out away from phone | The watch requires a reachable paired phone and Google's Data Layer. LTE or Wi-Fi on the watch alone does not create a Tailscale tunnel to your home. Test nearby over Bluetooth first. |
| A watch send has no confirmation | It may already have reached Murmur. Read the Watch chat on the phone before sending again. The app never automatically retries a send. |
| Microphone missing on phone | Use HTTPS, allow Android microphone permission, and ensure Murmur's voice backend is configured. |
| Watch dictation unavailable | Use **Type a message**; enable the watch's speech/keyboard service. Speech service availability is device-dependent. |
| Native chat needs an attachment, group or rich tool card | Open **Full Murmur workspace** from Settings or the conversation menu. Native text chat does not yet render those features. |
| Download cannot be saved | Use a direct same-server HTTP(S) file download. Blob-generated or external downloads currently require a browser. |
| No notifications for completed agents | Background Web Push is not implemented in this WebView client. Browser notification settings do not enable native push here. |
| Agent needs permission on watch | Choose **Continue on phone** and answer in Murmur. The watch never silently approves a tool call. |

Still stuck? Include app/server versions, phone/watch model, the connection type and a redacted error in an issue. Never attach session cookies, keys, passwords or full private hostnames.
