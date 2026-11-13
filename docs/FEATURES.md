# Feature coverage

Version 0.2.0 makes the everyday phone experience native Android. Agent lists, conversations, search, the message composer, streamed text replies, stop, rename, copy, dictation, one-time approvals and clarification forms are Android views. A WebView is not created for those screens.

The explicit **Full Murmur workspace** screen retains the server's interface for advanced features and sign-in. Native chat is not yet a complete replacement for every desktop feature. Physical-device validation is still required.

| Feature | Android phone | Wear OS |
|---|---|---|
| Agent selection and multiple Hermes homes | Native; agents labeled by home | Native; up to 8 homes / 60 agents |
| Agent creation, editing, personalities | Full workspace | Continue on phone |
| Conversations, recent history, list search, create, rename | Native; up to 100 recent chats per agent and 500 transcript rows per snapshot | One named Watch chat per agent; bounded recent text |
| Pin, archive, delete, older history | Full workspace | Continue on phone |
| Streaming text replies | Native WebSocket events; in-memory transcript and connection retained across rotation | Foreground refresh every 5 seconds while busy |
| Stop response | Native explicit interrupt | Continue on phone |
| Approvals | Native Allow once / Don't allow for offered choices; action details and confirmation before allowing | Needs-you status; answer on phone |
| Clarification questions | Native forms, choices and free-text answers | Continue on phone |
| Tool steps, plans, helpers, rich results | Native live activity label; complete visualizations in Full workspace | Text only |
| Text formatting, copy, links | Native basic Markdown spans, selectable text and copy | Text only |
| Dictation | Android speech recognizer inserts a draft; tap Send to submit | Reviewed native dictation |
| Files/photos, rich media, calls and read-aloud | Full workspace; native Android file picker/direct Save as and microphone permission there | Continue on phone |
| Models, providers, reasoning, memory, routines, goals | Full workspace | Uses the agent's configured model |
| Groups, app integrations, WhatsApp, Telegram and Umbrel setup | Full workspace | Continue on phone |
| Saved drafts | App-private preferences plus in-memory drafts; retained on navigation/recreation | Activity restoration |
| Offline access | Previously opened chat stays in memory during this process; reconnect to refresh/send. No offline send queue. | Needs reachable paired phone |
| Tailscale | Normal Android VPN routing; HTTPS and explicit private HTTP option | Paired phone relay; no separate watch VPN |
| Watch handoff | Reminder targets the selected agent's existing Watch chat in the native phone UI | Continue on phone |
| Dark theme | Native phone screens currently light; workspace follows server theme | Native black interface |
| Background Web Push | Not implemented; use a compatible browser/PWA | No proactive agent notifications |
| Home widgets / watch tiles / complications | Not implemented | Not implemented |

## Connection and sign-in

Sign-in uses the explicit workspace screen and its app-private WebView cookie store. Native API calls and the watch relay reuse cookies only for the saved Murmur origin. Chrome has a separate cookie store. Some OAuth providers reject embedded sign-in; opening Chrome does not automatically sign in the app.

Native sockets are reused while active and released after the phone activity has been in the background for 20 seconds. Returning reconnects and resumes the chat from the server. Reconnect is read-only and never replays a prompt. If delivery is uncertain, read the chat before sending again.

Native chat handles approval and clarification server requests. Other renderer requests, such as secrets, setup cards and interactive browser actions, require Full workspace and are explicitly declined as unsupported by the native renderer. Reopen the workspace and ask the agent to continue there. Nothing is auto-approved. Large snapshots exceeding the transport limit need the full workspace.

The explicit workspace retains the 0.1.0 WebView limitations: background Web Push is absent; some OAuth, blob exports and browser-specific media features need further integration. Direct same-origin HTTP(S) file saving is supported there.
