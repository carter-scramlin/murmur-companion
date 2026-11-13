# Changelog

## 0.3.0-beta.1 — 2026-11-13

- Native Murmur Buddy characters with agent colours, seeded proportions and state expressions on phone/watch.
- Persistent activity card with Thinking / Working / Replying / Needs you, human-readable tool activity, elapsed time and Stop.
- Completion updates for cached chats, responsive Back during slow resumes and stale-navigation guards.
- Bounded read-only reconnects, scoped draft autosave, bounded history and reduced-motion/battery-saver support.
- Optimized release builds with R8/resource shrinking, candidate packaging and separate private-key beta workflow.
- Privacy-preserving beta feedback sheet, GitHub feedback template and an explicit beta readiness/tester record.

Temporary candidate signing and physical-device/live-server checks remain before broad beta distribution.

## 0.2.0 — 2026-11-13

- Default phone experience rebuilt with native Android lists, agent selection, conversation history, search and Settings.
- Direct WebSocket streaming, native composer, dictation into a draft, copy, basic Markdown, new chats, rename and stop.
- In-memory conversation cache, connection reuse across rotation and app-private draft persistence.
- Native approval and clarification cards; no automatic approval or retry after uncertain send delivery.
- Explicit Full Murmur workspace retains advanced server features and sign-in.
- Watch handoff opens the selected agent's existing Watch chat in the native UI.
- Added streaming/replay contract checks and native Android flow/rotation/approval tests with no WebView.

## 0.1.0 — 2026-11-12

First preview of independent Android and Wear OS clients for Murmur.

- Native connection setup, private HTTP opt-in, Tailscale shortcut and diagnostics.
- Server-delivered Murmur interface with WebView sign-in, Android file picker, direct file saving and microphone permission handling.
- Native round-screen Wear OS agent picker and Watch chat, reviewed dictation/keyboard messages, crown scrolling and foreground reply refresh.
- Phone relay using the existing authenticated Murmur proxy, explicit operation allowlist and send receipt handling.
- Installation, Tailscale, privacy, security, compatibility and signing documentation.
- Unit/contract fixture tests, Android lint, paired APK CI and manual signed-build workflow.

Background Web Push, universal embedded OAuth, blob downloads and complete media parity remain open. Physical Pixel Watch and live-server checks are still required; see the validation document.
