# Feature coverage

The phone embeds the live mobile interface served by your Murmur installation. It does not fork or bundle Murmur's React frontend. A feature appearing in that interface is not the same as an end-to-end tested Android integration.

Status definitions: **Server UI** means the existing feature is presented by the server inside WebView; **Native** means this project implements the device integration; **Limited** means there is a known gap. Physical-device checks are still required. This preview does not claim verified full parity with the desktop/browser apps.

| Feature | Phone | Wear OS |
|---|---|---|
| Agents, creation, editing, personalities | Server UI | Native selection from up to 8 Hermes connections / 60 agents |
| Chats, history, search, rename, pin, archive | Server UI | One named Watch chat per agent; recent text only |
| Streaming replies, plans, helpers and tool details | Server UI | Native refresh of busy Watch chat every 5 seconds while visible; no tool trace |
| Groups and mentions | Server UI | Continue on phone |
| Approvals and clarification questions | Server UI | Shows needs-attention status for Watch chat; answer on phone |
| Models, provider configuration, reasoning, usage | Server UI | Uses the agent's configured model |
| App integrations and sign-in | Server UI; embedded OAuth can be refused by providers | Continue on phone |
| Memory, routines, goals, `/btw` | Server UI | Short messages only; advanced controls on phone |
| More than one Hermes | Server UI | Agents labeled by Hermes home; no silent fallback to another Hermes |
| WhatsApp, Telegram and Umbrel settings | Server UI | Continue on phone |
| Photos and file uploads | Native Android picker for `<input type=file>` | Continue on phone |
| Downloads | Native Save as for direct same-origin HTTP(S) downloads | Continue on phone |
| Blob-generated export / cross-origin downloads | Limited; open in browser | Continue on phone |
| Interactive widgets / inline media / browser previews | Server UI; browser capabilities and WebView compatibility apply | Text preview; continue on phone |
| Phone microphone / Murmur voice | Native runtime permission for audio capture on saved HTTPS origin; Murmur handles backend audio | Native system dictation, with text review before sending |
| Read-aloud / speech synthesis / calls | Server UI where compatible; WebView/browser APIs require device validation | Not implemented |
| Desktop keyboard shortcuts, dock badges, local desktop-only OS hooks | Platform-specific; server's mobile controls instead | Not applicable |
| Background Web Push | Limited: WebView has no Web Push integration here; use a supported browser/PWA for it | No proactive agent notifications; handoff reminder on phone only |
| Light / dark themes | Server UI respects Murmur's setting; native connection shell is cream | Native black OLED-friendly interface |
| Offline conversations | Not implemented; phone needs server, watch needs paired phone | Not implemented; no offline send queue |
| Tailscale | Native app works with Android VPN routing; private HTTP opt-in and HTTPS support | Phone relay; no standalone watch VPN |
| Home-screen widgets / watch tiles / complications | Not implemented | Not implemented |

## Compatibility boundary

WebView and Chrome have separate cookies. Opening a provider flow externally does not automatically sign the embedded page in. Providers using server-side callbacks may finish after a page reload; others require a browser-only workflow or additional callback integration. The app does not weaken sign-in provider restrictions.

Watch **Continue on phone** currently posts a notification that opens the app, not an exact-chat deep link. Find the agent's **Watch chat** in Murmur. The watch does not show every pending approval across every conversation and never auto-answers a server request.

The phone's UI follows the installed server automatically. The watch uses explicit upstream JSON-RPC methods (`profiles.list`, `session.list`, `session.create`, `session.resume`, `prompt.submit`) through Murmur's `/hermes/api/ws` proxy and reads `/murmur/connections`. Upstream changes can require a client update. See [validation](VALIDATION.md) for the inspected source revision.
