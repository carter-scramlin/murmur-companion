package dev.murmur.companion.core;

import okhttp3.*;
import okhttp3.mockwebserver.*;
import org.json.*;
import org.junit.*;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.Assert.*;

public class MurmurApiTest {
    private MockWebServer server;
    private MurmurApi api;
    @Before public void setUp() throws Exception {
        server = new MockWebServer(); server.start();
        api = new MurmurApi(new Endpoint(server.url("/").toString(), true), "murmur_session=private");
    }
    @After public void tearDown() throws Exception { api.close(); server.shutdown(); }
    @Test public void sendsCookiesOnlyToSavedOriginAndRefusesRedirects() throws Exception {
        MockWebServer other = new MockWebServer(); other.start();
        try {
            server.enqueue(new MockResponse().setResponseCode(302).addHeader("Location", other.url("/steal")));
            try { api.get("/murmur/session"); fail(); } catch (IOException expected) { assertTrue(expected.getMessage().contains("sign in")); }
            assertEquals("murmur_session=private", server.takeRequest().getHeader("Cookie"));
            assertNull(other.takeRequest(200, TimeUnit.MILLISECONDS));
            try { api.get("//attacker.example"); fail(); } catch (IOException expected) { }
        } finally { other.shutdown(); }
    }
    @Test public void refusesHtmlSignInPages() throws Exception {
        server.enqueue(new MockResponse().setBody("<html>Umbrel login</html>"));
        try { api.get("/murmur/connections"); fail(); } catch (IOException expected) { assertTrue(expected.getMessage().contains("signing in")); }
    }
    @Test public void agentListKeepsHermesIdentityAndSkipsUnavailableHomes() throws Exception {
        server.enqueue(new MockResponse().setBody(new JSONObject().put("connections", new JSONArray()
                .put(new JSONObject().put("id", "home").put("name", "Home").put("status", "unreachable"))
                .put(new JSONObject().put("id", "office").put("name", "Work").put("status", "connected"))).toString()));
        socket((method, params) -> {
            assertEquals("profiles.list", method);
            return new JSONObject().put("profiles", new JSONArray().put(new JSONObject().put("name", "default")
                    .put("ui_meta", new JSONObject().put("murmur", new JSONObject().put("display_name", "Atlas")))));
        });
        JSONObject result = WatchActions.execute(api, new JSONObject().put("action", "agents"));
        assertEquals(1, result.getJSONArray("warnings").length());
        JSONObject agent = result.getJSONArray("agents").getJSONObject(0);
        assertEquals("office", agent.getString("hermes")); assertEquals("Work", agent.getString("home")); assertEquals("Atlas", agent.getString("name"));
        assertEquals("/murmur/connections", server.takeRequest().getPath());
        assertEquals("/hermes/api/ws?hermes=office", server.takeRequest().getPath());
    }
    @Test public void readDoesNotCreateChatAndUnsupportedActionIsRejected() throws Exception {
        List<String> methods = Collections.synchronizedList(new ArrayList<>());
        socket((method, params) -> {
            methods.add(method);
            assertEquals("session.list", method);
            return new JSONObject().put("sessions", new JSONArray());
        });
        JSONObject result = WatchActions.execute(api, request("read"));
        assertEquals(0, result.getJSONArray("messages").length());
        assertEquals(List.of("session.list"), methods);
        try { WatchActions.execute(api, new JSONObject().put("action", "delete")); fail(); } catch (IOException expected) { }
    }
    @Test public void createsWatchChatAndSubmitsExactlyOnce() throws Exception {
        List<String> methods = Collections.synchronizedList(new ArrayList<>());
        socket((method, params) -> {
            methods.add(method);
            return switch (method) {
                case "session.list" -> new JSONObject().put("sessions", new JSONArray());
                case "session.create" -> {
                    assertEquals("Watch chat", params.getString("title")); assertFalse(params.getBoolean("close_on_disconnect"));
                    yield new JSONObject().put("session_id", "runtime").put("stored_session_id", "stored");
                }
                case "prompt.submit" -> {
                    assertEquals("Hello from my watch", params.getString("text")); assertEquals("runtime", params.getString("session_id"));
                    yield new JSONObject().put("status", "streaming");
                }
                default -> throw new AssertionError(method);
            };
        });
        assertTrue(WatchActions.execute(api, request("send").put("text", "Hello from my watch")).getBoolean("sent"));
        assertEquals(List.of("session.list", "session.create", "prompt.submit"), methods);
    }
    @Test public void resumesStoredIdAndDoesNotApproveOrSendWhileWaiting() throws Exception {
        List<String> methods = Collections.synchronizedList(new ArrayList<>());
        socket((method, params) -> {
            methods.add(method);
            if (method.equals("session.list")) return new JSONObject().put("sessions", new JSONArray().put(new JSONObject().put("id", "stored").put("title", "Watch chat")));
            assertEquals("session.resume", method); assertEquals("stored", params.getString("session_id"));
            return new JSONObject().put("session_id", "runtime").put("open_requests", new JSONArray().put(new JSONObject().put("id", "approval")));
        });
        try { WatchActions.execute(api, request("send").put("text", "Hello")); fail(); } catch (IOException expected) { assertTrue(expected.getMessage().contains("needs you")); }
        assertEquals(List.of("session.list", "session.resume"), methods);
    }
    @Test public void boundsTranscriptAndMarksBusy() throws Exception {
        socket((method, params) -> {
            if (method.equals("session.list")) return new JSONObject().put("sessions", new JSONArray().put(new JSONObject().put("id", "stored").put("title", "Watch chat")));
            JSONArray rows = new JSONArray();
            for (int i=0; i<100; i++) rows.put(new JSONObject().put("role", "assistant").put("text", "x".repeat(4000)));
            rows.put(new JSONObject().put("role", "tool").put("text", "private tool trace"));
            rows.put(new JSONObject().put("role", "user").put("text", "hidden prompt").put("display_kind", "hidden"));
            return new JSONObject().put("session_id", "runtime").put("running", true).put("messages", rows);
        });
        JSONObject result = WatchActions.execute(api, request("read"));
        assertTrue(result.getBoolean("busy")); assertEquals(8, result.getJSONArray("messages").length());
        assertFalse(result.toString().contains("private tool trace")); assertFalse(result.toString().contains("hidden prompt"));
        assertTrue(result.toString().length() < 22000);
    }
    @Test public void correlatesRpcResponsesAndIgnoresServerRequests() throws Exception {
        server.enqueue(new MockResponse().withWebSocketUpgrade(new WebSocketListener() {
            @Override public void onClosing(WebSocket socket, int code, String reason) { socket.close(code, reason); }
            final List<JSONObject> calls = new ArrayList<>();
            @Override public void onMessage(WebSocket socket, String text) {
                try {
                    calls.add(new JSONObject(text));
                    if (calls.size() == 2) {
                        socket.send(new JSONObject().put("id", "approval").put("method", "approval.request").toString());
                        for (int i=1; i>=0; i--) socket.send(new JSONObject().put("id", calls.get(i).getInt("id")).put("result", new JSONObject().put("value", calls.get(i).getString("method"))).toString());
                    }
                } catch (JSONException ex) { throw new AssertionError(ex); }
            }
        }));
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try (MurmurApi.Rpc rpc = api.connect("main")) {
            Future<JSONObject> a = executor.submit(() -> rpc.call("first", new JSONObject()));
            Future<JSONObject> b = executor.submit(() -> rpc.call("second", new JSONObject()));
            assertEquals("first", a.get(5, TimeUnit.SECONDS).getString("value")); assertEquals("second", b.get(5, TimeUnit.SECONDS).getString("value"));
        } finally { executor.shutdownNow(); }
    }
    private JSONObject request(String action) throws Exception { return new JSONObject().put("action", action).put("hermes", "main").put("profile", "default"); }
    interface Reply { JSONObject result(String method, JSONObject params) throws Exception; }
    private void socket(Reply reply) {
        server.enqueue(new MockResponse().withWebSocketUpgrade(new WebSocketListener() {
            @Override public void onClosing(WebSocket socket, int code, String reason) { socket.close(code, reason); }
            @Override public void onMessage(WebSocket socket, String text) {
                try {
                    JSONObject call = new JSONObject(text);
                    socket.send(new JSONObject().put("id", call.getInt("id")).put("result", reply.result(call.getString("method"), call.getJSONObject("params"))).toString());
                } catch (Exception ex) { throw new AssertionError(ex); }
            }
        }));
    }
}
