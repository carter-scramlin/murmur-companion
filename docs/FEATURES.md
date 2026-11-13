# Feature coverage — 0.3.0-beta.2

Murmur Beta uses native Android views for everyday conversations, files, sign-in and common management. **Full Murmur workspace** retains the server interface for advanced features. It remains necessary for full desktop feature access.

| Feature | Android phone | Wear OS |
|---|---|---|
| Agent selection and multiple homes | Native | Native, paired phone relay |
| Agent creation/personality editing | Native menu, selected home | Continue on phone |
| Conversations, create, rename, search | Native; recent list pages of 100 | One Watch chat per agent |
| Pin, unpin, archive, restore, delete | Native; revision-fenced edits; delete confirmation | Continue on phone |
| Older transcript history | Native reader, pages of 100; live chat retains up to 500 rows | Bounded recent text |
| Live text and stop | Native streaming connection, explicit Stop | Foreground refresh; native Stop reply |
| Approvals and clarification | Native cards and forms | Needs-you state; continue on phone |
| Secret/sudo/vault string requests | Masked native dialog, explicit Send/Skip, no read-cache persistence | Continue on phone |
| Tool summaries, plans and helpers | Native bounded text and live progress | Text/status |
| Rich tools, interactive browser and setup cards | Full workspace | Continue on phone |
| Files and photos in native chat | Android picker; streamed upload to selected agent; 25 MB photos / 100 MB files | Continue on phone |
| Models/providers and thinking | Native per-conversation picker | Configured agent model |
| Share/export and read-aloud | Native share of shown messages and system text-to-speech | Reply reader |
| Groups, memory, routines/goals and integrations | Full workspace | Continue on phone |
| Murmur password sign-in | Native; cookies shared with Workspace | Uses paired phone sign-in |
| Umbrel gateway / provider OAuth | Workspace; provider/browser restrictions still apply | Continue on phone |
| Drafts and offline reading | Private saved drafts; optional private read cache of ten chats | Activity restoration; phone required |
| Tailscale | Android VPN routing; HTTPS or explicit private HTTP | Phone relay; no watch VPN needed |
| Appearance and characters | System/light/dark; native Buddy, working states, reduced motion | Black round-screen UI; static Buddy |
| Reply notifications | Explicit 30-minute foreground follow of one running reply | May mirror phone notifications through system settings |
| General Web Push, widgets, tiles, complications | Not implemented | Not implemented |
| Standalone watch networking | — | Not implemented; paired phone required |

## Behavior and bounds

Reconnects read the server and never resubmit uncertain prompts. Saved chats open without network but cannot send until synced. Offline cache can be disabled/deleted in Settings; Forget this device clears all saved read copies and drafts. Files selected for upload are copied to a bounded temporary file and removed after the upload attempt. An attachment is staged on the server only after you select it; sending the message remains explicit. Native text sharing exports the displayed messages, not unseen history. The full-history reader requires a server supporting stored message pagination.

Native model changes are scoped to the current conversation. The server remains responsible for model availability and may ask for cost confirmation. Agent personality changes affect that agent on your server.

The opt-in reply monitor uses a visible data-sync foreground service, checks activity every 20 seconds, stops after completion/needs-input, three failed checks, manual cancellation, or 30 minutes. Android can defer network/CPU work while asleep; this is not a push notification backend or a guarantee of instant background delivery. It never submits prompts or responds to requests. Keep the app open for the most immediate activity display.

## Remaining limits

Full native desktop parity is not implemented. Groups, memory editing, routine/goal management, integration setup, interactive browser actions and complex result visualizations use Full workspace. Native result summaries are bounded to 4,000 characters; use Workspace for complete rich output. Unsupported renderer requests are explicitly declined, never auto-approved; reopen Workspace and ask the agent to continue there.

Some identity providers reject embedded browsers, and Chrome cookies do not transfer into this app. A client cannot bypass those provider restrictions; configure/sign in through a supported server flow. Workspace blob downloads and browser-specific media still require integration; direct same-origin HTTP(S) file saving and native text export work. General Web Push, standalone watch access, widgets, watch tiles and complications remain unimplemented.

APK identity, signatures, test fixtures, Android UI renders and lint are checked. Physical phone/Pixel Watch installation, live Murmur, Tailscale transitions and Android background behavior require device validation. See [BETA.md](BETA.md).
