# Reach Murmur with Tailscale

The phone runs the official Tailscale Android app. Murmur Companion uses Android's network routing normally; it does not create its own VPN or store a tailnet auth key. The watch uses the phone as a relay, so Tailscale does not need to run on Wear OS.

## Recommended: a private HTTPS address

1. Install Tailscale on the machine that exposes Murmur and on your Android phone. Sign both into the same tailnet. Enable the phone's Tailscale connection. Keep this app included if you use Tailscale's per-app VPN settings.
2. On the server, find the exact **local listener serving Murmur through its existing authentication layer**. For standalone Docker, this is often `http://127.0.0.1:3000`. On Umbrel, use the app proxy port assigned to Murmur, not its internal web container or Hermes port. Inspect your installed app's port mapping; do not assume an upstream container port is the host port.
3. On the same machine/network namespace where that local address is reachable, use Tailscale Serve. For example, **only if** `http://127.0.0.1:3000` is your intended authenticated listener:

   ```bash
   curl -I http://127.0.0.1:3000
   tailscale serve --bg http://127.0.0.1:3000
   tailscale serve status
   ```

   Follow Tailscale's prompt if HTTPS needs enabling. The command prints the private `https://machine.tailnet-name.ts.net` address. Use the exact address it prints. A Tailscale container can have a different loopback network from its host; `127.0.0.1` inside that container is not the host's loopback. Configure Serve where the target is actually reachable.
4. Open the HTTPS address in Chrome on your phone while Tailscale is connected. Confirm it shows Murmur or the normal Umbrel/Murmur sign-in, and complete sign-in. If it opens a different app or a general dashboard, correct the Serve target first.
5. Paste that HTTPS root address into Murmur Companion, leaving the HTTP option off. Sign in again inside the app; browser and WebView cookies are separate.
6. Keep Tailscale connected. Open the watch app after the phone app is signed in. Test a short Watch chat while the devices are close together, then test away from your home Wi-Fi.

Serve is private to the tailnet and provides a trusted TLS certificate. Existing tailnet policy still applies. This guide uses Serve, **not Funnel**, which exposes a service to the public internet. No router port forwarding is needed.

## Preserve the Umbrel login

The upstream Umbrel Murmur package runs its web container on internal port 3000 and puts Umbrel's app proxy in front of it. It leaves Murmur's own standalone password unset because that proxy supplies authentication. Forwarding directly to the internal web container would bypass the expected Umbrel login.

Keep Serve pointed at the authenticated **app proxy listener** for an Umbrel installation. If using a standalone Murmur instance instead, configure its documented `MURMUR_PASSWORD` before exposing it to other tailnet users. Murmur Companion does not change any server authentication settings.

The [upstream app configuration](https://github.com/getumbrel/umbrel-apps/blob/master/murmur/docker-compose.yml) documents these boundaries. Your installed version and port assignments are authoritative.

## Alternative: tailnet IP or MagicDNS with HTTP

If you have an existing authenticated app listener reachable as `http://100.x.y.z:PORT` or `http://umbrel:PORT`, you can use it with **Allow a private HTTP connection** switched on. Tailnet routing encrypts traffic between Tailscale devices, but HTTP does not create a browser secure context. Phone voice capture and some sign-in integrations need HTTPS.

Use the full MagicDNS `machine.tailnet-name.ts.net` name for reliable remote name resolution. `umbrel.local` normally depends on local multicast DNS and is not a dependable remote address. An HTTPS certificate for a DNS name will not validate if you replace the hostname with its IP.

## If your tailnet restricts access

An administrator can add a grant to the existing policy. Merge with the actual policy; this example is not a complete replacement. Substitute your user and the **actual server tag** assigned to your Murmur machine:

```json
{
  "grants": [
    {
      "src": ["you@example.com"],
      "dst": ["tag:murmur"],
      "ip": ["tcp:443"]
    }
  ]
}
```

That tag must already be defined in `tagOwners` and assigned to the server. A direct HTTP setup needs the actual published HTTP port allowed instead. The watch is not a separate tailnet device in this architecture.

## Check the connection

- Does the exact address work in the phone's browser while away from local Wi-Fi?
- Is Tailscale connected on both the phone and server, and is Murmur Companion included in VPN routing?
- Does the certificate match the full hostname?
- Does the app show an expired sign-in? Reopen it and sign in.
- If the watch alone fails, check pairing and app signatures before changing Tailscale. The phone is the network client.

Murmur's **Check connection** reports server reachability and the standalone Murmur session endpoint's response. Android's VPN indicator may refer to a different VPN; the app deliberately does not label any active VPN as Tailscale. Some app proxies can return their own login page before Murmur's session endpoint is reached.

## Official references

- [Install Tailscale on Android](https://tailscale.com/docs/install/android)
- [Tailscale Serve](https://tailscale.com/docs/features/tailscale-serve)
- [Serve command and background behavior](https://tailscale.com/docs/reference/tailscale-cli/serve)
- [MagicDNS](https://tailscale.com/docs/features/magicdns)
- [Grants](https://tailscale.com/docs/reference/grants)
