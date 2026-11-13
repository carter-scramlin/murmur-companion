# Security

Use a trusted HTTPS address and preserve the server's authentication layer. See [Tailscale setup](docs/TAILSCALE.md) before exposing an Umbrel installation through Serve. The client cannot secure an unauthenticated server or configure your tailnet policy.

Native authenticated HTTP requests are bound to the saved origin and refuse redirects. HTTPS certificate errors are never bypassed. The WebView has no native JS bridge; file/content URL access and mixed content are disabled. Microphone access is limited to the saved HTTPS origin and requires Android permission.

The watch uses Google Play services' application-ID and certificate matching. Keep phone and watch signing keys identical. The phone accepts a small allowlist of watch operations, bounds payloads, leaves approval requests unanswered and records send IDs before submission. Network ambiguity can still leave a send's outcome unknown; check the server chat before manually repeating an action.

The app enables manifest cleartext support for explicitly opted-in private HTTP addresses. Prefer HTTPS. Server-provided web content has the normal privileges of its WebView origin and must be trusted. This preview has not received an independent security audit.

If you find a vulnerability, avoid putting keys, cookies, passwords, private hostnames or sensitive chats in a public issue. Contact the repository owner privately through their published GitHub profile or use GitHub private vulnerability reporting if the owner has enabled it. There is no invented security email or promised response SLA.
