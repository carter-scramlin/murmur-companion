# Privacy

Murmur Companion adds no analytics SDK, ad network, developer account backend or hosted chat relay. The phone loads the Murmur server address you provide. Murmur and its agents may contact model providers, apps and other services according to your server configuration.

The phone keeps your server origin and preferences in app-private settings. Sign-in cookies and web storage live in Android's app-private WebView storage; they are not additionally encrypted by this project. The operating system's app sandbox and device protections apply. Native phone API calls and watch requests reuse the cookies for the saved origin. Hermes access keys remain on Murmur's server.

Buddy characters are drawn locally from agent profile names and hue metadata; no avatar service or image tracker is contacted. Beta feedback sharing includes app/Android/device versions and excludes the server address, cookies and chats; Android shows a share sheet for you to choose a destination.

Native phone conversations and recent lists are cached in app-private, non-backed-up storage for offline reading by default. The cache is isolated by server and bounded to ten opened chats with at most 100,000 characters each and a 4 MB total file. It excludes runtime IDs, pending private-value/approval requests and tool traces. Settings can disable and delete saved read copies. Unsent phone drafts are stored in app-private preferences, scoped to the server, home, agent and conversation; Android app backup is disabled. **Forget this device** removes them.

The watch exchanges agent names, short prompts, bounded text replies, request IDs and status with the paired phone through Google Play services' Wear OS Data Layer. Google supplies that transport. The watch does not receive your server cookie, password or Hermes access key. The watch UI holds conversation text in memory; draft text and selected agent can be saved by Android to restore an activity. Screenshots are disabled for the watch activity, although system/device behavior can vary.

System dictation and keyboard providers are selected by the device. Dictation may send audio to a provider's cloud. No always-listening microphone or audio recording is implemented by this app. On the phone, Murmur's own voice interface and backend handle audio after permission is granted.

Optional phone handoff reminders contain a generic message and use private lock-screen visibility. You can explicitly follow one active reply through a visible foreground service for up to 30 minutes. It only reads activity, never sends messages or answers requests, and uses generic completion/needs-input notifications without conversation text. Stop following from the notification. General background Web Push is not implemented. Files explicitly saved through Android's picker remain at the location you choose.

Backup and device transfer are disabled for app data. **Forget this device** clears the saved phone connection, WebView cookies/storage/cache, offline read copies, drafts and watch send receipts. Chats remain on your Murmur server. Clearing storage or uninstalling removes local app data; downloaded files saved outside it remain.

For server behavior, consult [Murmur's source and privacy notes](https://github.com/getumbrel/murmur#privacy-and-security). This project is independent of Umbrel, Google and Tailscale.
