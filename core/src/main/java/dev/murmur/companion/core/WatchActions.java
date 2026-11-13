package dev.murmur.companion.core;

import org.json.*;
import java.io.IOException;

/** A small, explicit RPC surface for the paired watch. */
public final class WatchActions {
    public static final String TITLE = "Watch chat";
    public static JSONObject execute(MurmurApi api, JSONObject request) throws Exception {
        String action = request.getString("action");
        if (action.equals("agents")) return agents(api);
        if (!action.equals("read") && !action.equals("send")) throw new IOException("Unsupported watch action.");
        String hermes = request.getString("hermes");
        String profile = request.getString("profile");
        if (!hermes.matches("[a-zA-Z0-9_-]{1,128}") || !profile.matches("[a-z0-9][a-z0-9_-]{0,63}")) throw new IOException("Choose an agent again.");
        String text = request.optString("text").trim();
        if (action.equals("send") && (text.isEmpty() || text.length() > 2000)) throw new IOException("Keep your message between 1 and 2,000 characters.");
        try (MurmurApi.Rpc rpc = api.connect(hermes)) {
            JSONObject params = new JSONObject().put("profile", profile);
            JSONArray sessions = rpc.call("session.list", new JSONObject().put("profile", profile).put("title", TITLE).put("limit", 100)).optJSONArray("sessions");
            String stored = null;
            if (sessions != null) for (int i = 0; i < sessions.length(); i++) {
                JSONObject row = sessions.getJSONObject(i);
                if (TITLE.equals(row.optString("title"))) { stored = row.getString("id"); break; }
            }
            JSONObject state;
            if (stored == null) {
                if (action.equals("read")) return new JSONObject().put("messages", new JSONArray()).put("status", "Ready when you are");
                state = rpc.call("session.create", params.put("title", TITLE).put("source", "desktop").put("close_on_disconnect", false));
            } else state = rpc.call("session.resume", params.put("session_id", stored).put("source", "desktop").put("close_on_disconnect", false));
            String runtime = state.getString("session_id");
            boolean waiting = (state.optJSONArray("open_requests") != null && state.optJSONArray("open_requests").length() > 0)
                    || state.optJSONObject("pending_approval") != null || state.optJSONObject("pending_connection") != null;
            boolean busy = state.optBoolean("running") || "working".equals(state.optString("status")) || "starting".equals(state.optString("status"));
            if (action.equals("send")) {
                if (waiting) throw new IOException("Your agent needs you. Open this chat on your phone.");
                if (busy) throw new IOException("Your agent is still working. Refresh before sending another message.");
                JSONObject result = rpc.call("prompt.submit", new JSONObject().put("session_id", runtime).put("profile", profile).put("text", text));
                return new JSONObject().put("sent", true).put("status", "queued".equals(result.optString("status")) ? "Queued" : "Working…");
            }
            JSONArray rows = state.optJSONArray("messages");
            JSONArray messages = new JSONArray();
            if (rows != null) for (int i = Math.max(0, rows.length() - 30); i < rows.length(); i++) {
                JSONObject row = rows.getJSONObject(i);
                String role = row.optString("role");
                String content = row.optString("text", row.optString("content", ""));
                if ((role.equals("user") || role.equals("assistant")) && !content.trim().isEmpty() && !"hidden".equals(row.optString("display_kind"))) {
                    messages.put(new JSONObject().put("role", role).put("text", clip(content, 2400)));
                }
            }
            // Bound the Data Layer response while retaining the most recent conversation.
            while (messages.length() > 8) messages.remove(0);
            JSONObject inflight = state.optJSONObject("inflight");
            if (inflight != null && !inflight.optString("assistant").trim().isEmpty()) messages.put(new JSONObject().put("role", "assistant").put("text", clip(inflight.optString("assistant"), 2400)));
            return new JSONObject().put("messages", messages).put("status", waiting ? "Needs you · Open on phone" : busy ? "Working…" : "Up to date").put("waiting", waiting).put("busy", busy);
        }
    }
    private static JSONObject agents(MurmurApi api) throws Exception {
        long started = System.nanoTime();
        JSONArray connections = api.get("/murmur/connections").getJSONArray("connections");
        JSONArray agents = new JSONArray(), warnings = new JSONArray();
        for (int i = 0; i < Math.min(connections.length(), 8); i++) {
            if (System.nanoTime() - started > java.util.concurrent.TimeUnit.SECONDS.toNanos(20)) {
                warnings.put("Some homes took too long to answer. Open Murmur on your phone to check them."); break;
            }
            JSONObject connection = connections.getJSONObject(i);
            String hermes = connection.getString("id");
            if (!"connected".equals(connection.optString("status", "connected"))) {
                warnings.put(connection.optString("name", "A Hermes") + " needs attention on your phone."); continue;
            }
            try (MurmurApi.Rpc rpc = api.connect(hermes)) {
                JSONArray profiles = rpc.call("profiles.list", new JSONObject()).getJSONArray("profiles");
                for (int j = 0; j < profiles.length() && agents.length() < 60; j++) {
                    JSONObject profile = profiles.getJSONObject(j);
                    String id = profile.getString("name");
                    JSONObject meta = profile.optJSONObject("ui_meta");
                    JSONObject murmur = meta == null ? null : meta.optJSONObject("murmur");
                    JSONObject bot = meta == null ? null : meta.optJSONObject("hermes-bots");
                    String name = murmur == null ? "" : murmur.optString("display_name");
                    if (name.trim().isEmpty() && bot != null) name = bot.optString("title");
                    if (name.trim().isEmpty()) name = profile.optString("display_name");
                    if (name.trim().isEmpty()) name = id.equals("default") ? "Hermes" : id;
                    agents.put(new JSONObject().put("profile", id).put("hermes", hermes).put("name", clip(name, 80))
                            .put("home", clip(connection.optString("name", "Home"), 80)));
                }
            } catch (Exception ex) { warnings.put(connection.optString("name", "A Hermes") + " is unavailable. Open Murmur on your phone."); }
        }
        return new JSONObject().put("agents", agents).put("warnings", warnings);
    }
    private static String clip(String text, int max) { return text.length() > max ? text.substring(0, max) + "…" : text; }
}
