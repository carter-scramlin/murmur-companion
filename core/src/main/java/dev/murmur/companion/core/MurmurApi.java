package dev.murmur.companion.core;

import okhttp3.*;
import org.json.*;
import java.io.IOException;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Uses Murmur's authenticated proxy. Never handles a Hermes access key. */
public final class MurmurApi implements AutoCloseable {
    private final Endpoint endpoint;
    private final String cookies;
    private final OkHttpClient client;
    private final java.util.Set<WebSocket> sockets = ConcurrentHashMap.newKeySet();
    public MurmurApi(Endpoint endpoint, String cookies) {
        this.endpoint = endpoint;
        this.cookies = cookies == null ? "" : cookies;
        client = new OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
                .connectTimeout(8, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS)
                .callTimeout(20, TimeUnit.SECONDS).pingInterval(20, TimeUnit.SECONDS).build();
    }
    private Request.Builder request(String url) {
        return new Request.Builder().url(url).header("Origin", endpoint.origin).header("Cookie", cookies);
    }
    public static final class AuthRequired extends IOException { public AuthRequired(String message){super(message);} }
    public JSONObject get(String path) throws Exception {
        if (!path.startsWith("/") || path.startsWith("//")) throw new IOException("Invalid server path");
        try (Response response = client.newCall(request(endpoint.origin + path).build()).execute()) {
            if (response.code() == 401 || response.code() == 403 || response.isRedirect()) throw new AuthRequired("Sign in to Murmur in the workspace, then return to the native app.");
            if (!response.isSuccessful() || response.body() == null) throw new IOException("Murmur is unavailable (" + response.code() + "). Check Tailscale on your phone.");
            if (response.body().contentLength() > 1024 * 1024) throw new IOException("Server response is too large.");
            String text = response.body().source().readUtf8( Math.min(1024 * 1024, response.body().source().request(1024 * 1024) ? 1024 * 1024 : response.body().source().buffer().size()));
            try { return new JSONObject(text); }
            catch (JSONException ex) { throw new AuthRequired("Sign in to Murmur in the workspace, then return to the native app."); }
        }
    }
    public interface Listener {
        default void onEvent(JSONObject event) { }
        default void onRequest(JSONObject request) { }
        default void onDisconnected() { }
    }
    public Rpc connect(String hermes) throws Exception { return new Rpc(hermes, null); }
    public Rpc connect(String hermes, Listener listener) throws Exception { return new Rpc(hermes, listener); }
    public final class Rpc implements AutoCloseable {
        private final ConcurrentMap<Integer, CompletableFuture<JSONObject>> pending = new ConcurrentHashMap<>();
        private final AtomicInteger sequence = new AtomicInteger();
        private final CompletableFuture<Void> opened = new CompletableFuture<>();
        private final WebSocket socket;
        private volatile boolean closing;
        private volatile boolean alive = true;
        private final Listener listener;
        public boolean isAlive() { return alive && !closing; }
        Rpc(String hermes, Listener listener) throws Exception {
            this.listener = listener;
            socket = client.newWebSocket(request(endpoint.socketUrl(hermes)).build(), new WebSocketListener() {
                @Override public void onOpen(WebSocket ws, Response response) { opened.complete(null); }
                @Override public void onMessage(WebSocket ws, String text) {
                    if (text.length() > 1024 * 1024) { fail(new IOException("Server message is too large.")); ws.cancel(); return; }
                    try {
                        JSONObject frame = new JSONObject(text);
                        if ("event".equals(frame.optString("method")) && frame.optJSONObject("params") != null) {
                            if (listener != null) listener.onEvent(frame.getJSONObject("params"));
                            return;
                        }
                        if (frame.has("id") && frame.has("method")) {
                            if (listener != null) listener.onRequest(frame);
                            return;
                        }
                        if (!(frame.opt("id") instanceof Number)) return;
                        CompletableFuture<JSONObject> future = pending.remove(frame.getInt("id"));
                        if (future == null) return;
                        if (frame.has("error")) future.completeExceptionally(new IOException(frame.getJSONObject("error").optString("message", "Murmur could not complete that request.")));
                        else future.complete(frame.optJSONObject("result") == null ? new JSONObject() : frame.getJSONObject("result"));
                    } catch (Exception ignored) { /* unrelated events do not settle an RPC */ }
                }
                @Override public void onFailure(WebSocket ws, Throwable error, Response response) { fail(new IOException("Can't reach Murmur. Check your phone's sign-in and Tailscale connection.")); }
                @Override public void onClosing(WebSocket ws, int code, String reason) { ws.close(code, reason); }
                @Override public void onClosed(WebSocket ws, int code, String reason) { sockets.remove(ws); fail(new IOException("Murmur disconnected. Open it on your phone.")); }
            });
            sockets.add(socket);
            try { opened.get(10, TimeUnit.SECONDS); }
            catch (Exception ex) { socket.cancel(); throw new IOException("Can't connect. Open Murmur on your phone and check Tailscale."); }
        }
        private void fail(Throwable error) {
            boolean wasAlive = alive; alive = false;
            opened.completeExceptionally(error);
            if (wasAlive && !closing && listener != null) listener.onDisconnected();
            pending.values().forEach(f -> f.completeExceptionally(error)); pending.clear();
        }
        public JSONObject call(String method, JSONObject params) throws Exception {
            if (closing) throw new IOException("Connection closed");
            int id = sequence.incrementAndGet();
            CompletableFuture<JSONObject> future = new CompletableFuture<>(); pending.put(id, future);
            try {
                if (!socket.send(new JSONObject().put("jsonrpc", "2.0").put("id", id).put("method", method).put("params", params).toString())) throw new IOException("Connection closed");
                return future.get(15, TimeUnit.SECONDS);
            } catch (ExecutionException ex) { throw new IOException(ex.getCause().getMessage()); }
            catch (TimeoutException ex) { throw new IOException("No confirmation from Murmur. Check the chat on your phone before sending again."); }
            finally { pending.remove(id); }
        }
        public void respond(Object id, JSONObject result) throws Exception {
            if (!isAlive() || !socket.send(new JSONObject().put("jsonrpc", "2.0").put("id", id).put("result", result).toString()))
                throw new IOException("Disconnected. Reconnect and check whether this request is still waiting.");
        }
        public void decline(Object id, int code, String message) throws Exception {
            socket.send(new JSONObject().put("jsonrpc", "2.0").put("id", id)
                    .put("error", new JSONObject().put("code", code).put("message", message)).toString());
        }
        @Override public void close() { closing = true; socket.close(1000, "Done"); fail(new IOException("Closed")); }
    }
    @Override public void close() { sockets.forEach(WebSocket::cancel); sockets.clear(); client.dispatcher().cancelAll(); client.connectionPool().evictAll(); client.dispatcher().executorService().shutdown(); }
}
