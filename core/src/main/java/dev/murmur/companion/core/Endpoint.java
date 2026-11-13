package dev.murmur.companion.core;

import java.net.URI;
import java.util.Locale;

/** The only server authority allowed to receive Murmur cookies. */
public final class Endpoint {
    public final String origin;
    public Endpoint(String input, boolean allowPrivateHttp) {
        try {
            URI uri = new URI(input.trim());
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            String host = uri.getHost();
            if (host == null || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null
                    || (uri.getPath() != null && !uri.getPath().isEmpty() && !uri.getPath().equals("/"))
                    || uri.getPort() == 0 || uri.getPort() > 65535)
                throw new IllegalArgumentException();
            host = host.toLowerCase(Locale.ROOT);
            if (!scheme.equals("https") && !(scheme.equals("http") && allowPrivateHttp && privateHost(host)))
                throw new IllegalArgumentException();
            int port = uri.getPort();
            origin = scheme + "://" + host + ((port == -1 || (port == 443 && scheme.equals("https"))
                    || (port == 80 && scheme.equals("http"))) ? "" : ":" + port);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Enter an HTTPS address without a path, password or query. For a private HTTP address, enable the local connection option.");
        }
    }
    private static boolean privateHost(String host) {
        if (host.equals("localhost") || host.equals("[::1]") || host.endsWith(".local") || host.endsWith(".ts.net")) return true;
        if (!host.contains(".") && host.matches("[a-z0-9-]+")) return true;
        String[] p = host.split("\\.");
        if (p.length != 4) return false;
        int[] n = new int[4];
        try { for (int i = 0; i < 4; i++) { n[i] = Integer.parseInt(p[i]); if (n[i] < 0 || n[i] > 255) return false; } }
        catch (NumberFormatException ex) { return false; }
        return n[0] == 10 || n[0] == 127 || (n[0] == 192 && n[1] == 168)
                || (n[0] == 172 && n[1] >= 16 && n[1] <= 31) || (n[0] == 100 && n[1] >= 64 && n[1] <= 127);
    }
    public boolean contains(String url) {
        try { return origin.equals(new Endpoint(new URI(url).resolve("/").toString(), true).origin); }
        catch (Exception ex) { return false; }
    }
    public String socketUrl(String hermes) {
        String scheme = origin.startsWith("https:") ? "wss:" : "ws:";
        return scheme + origin.substring(origin.indexOf(':') + 1) + "/hermes/api/ws?hermes=" + encode(hermes);
    }
    public static String encode(String value) {
        try { return java.net.URLEncoder.encode(value, "UTF-8").replace("+", "%20"); }
        catch (java.io.UnsupportedEncodingException ex) { throw new AssertionError(ex); }
    }
}
