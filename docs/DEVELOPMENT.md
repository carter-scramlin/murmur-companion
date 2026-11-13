# Native Android architecture (0.2.0)

- `MainActivity`: native Chats, Agents and Settings screens, RecyclerView transcript, composer, Android dictation, draft persistence, approval confirmations and clarification forms. Native conversation screens do not instantiate a WebView.
- `NativeStore`: Android ViewModel retains selected agent/chat, cached lists, ten recent transcripts, drafts and the transport across configuration changes. A serial executor owns network operations; the main thread owns UI state. Sockets close after 20 seconds in the background and resume read-only on return.
- `core/NativeChat`: pure Java snapshot/event reducer, hidden-row filtering, runtime/stored session identity, stream offset/sequence deduplication and gap detection. A gap requests an authoritative resume; prompts are never replayed.
- `core/MurmurApi`: authenticated HTTP and persistent JSON-RPC sockets. Events and server requests are opt-in listeners; direct replies to server requests require a user action. Watch callers retain their narrow interface.
- `WorkspaceActivity`: the explicit full-workspace and sign-in screen, containing the former WebView shell and native device integrations. This preserves advanced server features while native chat is developed.

Native phone RPC methods: `profiles.list`, `session.list`, `session.create`, `session.resume`, `prompt.submit`, `session.interrupt`, `session.title`, `client.capabilities`; approval/question answers respond to the original server request ID. Legacy pending approvals use `approval.respond`. Unsupported renderer requests return an explicit unsupported error and require the full workspace.

Phone draft keys include server origin, Hermes, profile and stored chat ID. Drafts persist only in app-private preferences; transcripts are memory-only and bounded to 500 rows per snapshot, ten cached chats. No offline prompt queue exists. Native chat supports basic Markdown, not the complete rich renderer.

Tests include direct native HTTP/WebSocket fixture integration, exactly one submission from a Send tap, Activity recreation with draft retention, approval confirmation without implicit permission, stream deduplication/gap behavior, watch contracts and endpoint policy. Use JDK 17 and run the Gradle checks below.

## Build

Use JDK 17, Android SDK Platform 36 and Build Tools 36.0.0. The repository pins Android Gradle Plugin 9.1.0 and Gradle 9.3.1. The Gradle wrapper and both Android app modules are checked in. Android Studio can create `local.properties`; keep machine-specific SDK paths out of Git.

```bash
./gradlew :core:test :phone:testDebugUnitTest :wear:testDebugUnitTest :phone:assembleDebug :wear:assembleDebug :phone:lintDebug :wear:lintDebug
```

The first build resolves dependencies from Google Maven and Maven Central. All app code is Java 17. Kotlin is not required to edit the app, although the Android Gradle plugin includes Kotlin support.

## Structure and boundaries

- `phone/WorkspaceActivity`: explicit web workspace lifecycle, microphone permissions, Android file picking/saving, and sign-in.
- `phone/WatchRelayService`: exported WearableListenerService filtered to `/murmur/v1/request`. Play services restricts Data Layer exchange to applications with the same package name and signing key. No HTTP listener is opened on the phone.
- `wear/WatchActivity`: foreground-only Data Layer message client, request ID and source-node correlation, round-screen layout, crown scrolling, system dictation and keyboard entry. No server cookie or Hermes key is sent to the watch.
- `core/Endpoint`: origin validation and explicit private-HTTP policy. No credentials, paths, queries or fragments are accepted as a server address.
- `core/MurmurApi`: authenticated HTTP and WebSocket calls to one saved Murmur origin. HTTP redirects are refused. RPC responses are correlated by numeric ID. Native phone listeners support explicit server-request answers; watch callers do not answer them.
- `core/WatchActions`: explicit watch operations. Reads never create a chat. A send finds or creates the exact `Watch chat` title in the chosen profile, resumes using the stored ID and submits against the returned runtime ID. Existing busy or waiting chats reject another prompt.

## Watch protocol

Request path: `/murmur/v1/request`. Response path: `/murmur/v1/response`. UTF-8 JSON envelopes carry a UUID `id` and `action`; supported actions are `agents`, `read`, `send`, and `open` (phone reminder). Chat actions include exact `hermes` and `profile` IDs. Send includes `text` (1–2,000 characters). Arbitrary URLs, RPC methods or API paths are not accepted from the watch.

The watch selects a reachable node advertising `murmur_phone_v1`, preferring a nearby phone. The phone serializes relay operations. A send's request ID is persisted before any network mutation. Re-delivery of that ID is refused, including after process death. Receipts expire after seven days. The watch never retries a send automatically. On interruption or an ambiguous error, the user must check the server chat. This prevents blind replay; it is not an exactly-once delivery guarantee across networks.

Responses are limited to 90 KB, below the Data Layer's message limit. Chat text is bounded to eight recent messages plus any in-progress assistant answer, with 2,400 characters per row. Approvals and connections requiring interaction stay on the phone. The watch does not claim to implement Hermes's full renderer request protocol.

## WebView choices

JavaScript and DOM storage are needed for Murmur. File and content URL access are disabled; mixed content is never allowed. Audio capture is granted only to the saved HTTPS origin, after Android runtime permission. There is no JavaScript-to-native interface. Third-party cookies are disabled. The top bar shows the loaded host; another HTTPS host requires the user's choice before loading in the WebView. That per-host navigation trust is kept only in memory.

Cleartext is enabled at the manifest level so user-selected LAN/tailnet HTTP servers can work, and restricted by the app's endpoint policy. This is not a network firewall: HTTP subresources are governed by the server and WebView. Use HTTPS for normal operation. No TLS error is bypassed, no custom trust manager is installed, and native authenticated requests refuse redirects.

Direct downloads go through Save as and a native HTTP client with redirects disabled. Blob exports and browser-specific features are tracked as limitations rather than routed through an unrestricted JS bridge.

## Test without a personal server

`core` tests use MockWebServer; they do not contact a personal Murmur or perform an agent action. They assert HTTP authority policy, cookie forwarding, refusal of redirects/login HTML, RPC ID matching, watch chat creation, no creation on read, no auto-approval, send behavior and transcript bounds.

For visual smoke checks, install the APK in an Android emulator. Onboarding and failure screens work without a server. Use a local fixture server for connection checks, and keep mock data clearly labeled. For the full watch relay, pair a Wear OS emulator with a Google Play phone emulator, or use actual paired hardware with Google Play services. Do not mistake a standalone APK build for a successful relay test.

## GitHub setup

The public project repository is `carter-scramlin/murmur-companion`. The download website lives in `website/`; see [website development and deployment](WEBSITE.md). The Pages workflow deploys the static site. The preview release workflow builds, verifies and publishes the matching APK pair; website download buttons use those fixed assets. The separate CI and manual signed-preview workflows provide downloadable artifacts. They do not automatically publish a Play Store listing.
