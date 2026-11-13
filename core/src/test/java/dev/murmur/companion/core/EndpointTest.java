package dev.murmur.companion.core;

import org.junit.Test;
import static org.junit.Assert.*;

public class EndpointTest {
    @Test public void normalizesOriginsAndEncodesHermes() {
        Endpoint endpoint = new Endpoint(" https://Umbrel.tail123.ts.net:443/ ", false);
        assertEquals("https://umbrel.tail123.ts.net", endpoint.origin);
        assertTrue(endpoint.contains("https://umbrel.tail123.ts.net/chats/123?x=1"));
        assertFalse(endpoint.contains("https://umbrel.tail123.ts.net.attacker.example/"));
        assertFalse(endpoint.contains("http://umbrel.tail123.ts.net/"));
        assertFalse(endpoint.contains("https://user@umbrel.tail123.ts.net/"));
        assertEquals("wss://umbrel.tail123.ts.net/hermes/api/ws?hermes=a%20b", endpoint.socketUrl("a b"));
    }
    @Test public void privateHttpRequiresOptInAndRestrictedAddress() {
        for (String host : new String[]{"100.64.0.1", "100.127.255.254", "192.168.1.10", "10.0.0.5", "172.16.0.1", "umbrel.local", "umbrel", "umbrel.tail123.ts.net"}) {
            String url = "http://" + host + ":3000";
            assertEquals(url, new Endpoint(url, true).origin);
            rejected(url, false);
        }
        for (String host : new String[]{"example.com", "100.128.1.1", "100.63.0.1", "172.32.0.1", "192.169.1.1", "192.168.999.1"}) rejected("http://" + host, true);
    }
    @Test public void rejectsConfusedAuthoritiesAndNonRootUrls() {
        for (String url : new String[]{"javascript:alert(1)", "file:///data/data", "https://name:secret@server", "https://server/murmur", "https://server?token=secret", "https://server#x", "https://server:0", "https://server:65536", "https://server\\@attacker", "https://server/%2e%2e", "//server", "server", ""}) rejected(url, true);
    }
    private static void rejected(String url, boolean local) {
        try { new Endpoint(url, local); fail("Accepted " + url); } catch (IllegalArgumentException expected) { }
    }
}
