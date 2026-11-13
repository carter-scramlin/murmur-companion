package dev.murmur.companion;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.*;
import android.speech.RecognizerIntent;
import android.view.*;
import android.widget.*;
import com.google.android.gms.wearable.*;
import org.json.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Deliberately small: choose an agent, say something, read the answer. */
public final class WatchActivity extends androidx.activity.ComponentActivity implements MessageClient.OnMessageReceivedListener {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private ScrollView scroll;
    private LinearLayout page;
    private TextView status;
    private JSONObject agent, latestChat;
    private String pendingId, pendingNode, draft = "", operation = "";
    private boolean foreground, busy, uncertain, composing, reading;
    private final Runnable poll = () -> { if (foreground && agent != null && pendingId == null && !uncertain) request("read", ""); };
    private final Runnable timeout = () -> {
        boolean send = operation.equals("send"); pendingId = null; pendingNode = null; busy = false;
        if (send) { uncertain = true; draft = ""; }
        showError(send ? "No confirmation. Your message may have arrived. Check the Watch chat on your phone before sending again." : "Phone didn't answer. Open Murmur on your phone and check Bluetooth and Tailscale.");
    };
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        getOnBackPressedDispatcher().addCallback(this, new androidx.activity.OnBackPressedCallback(true) { @Override public void handleOnBackPressed() { handleBack(); } });
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        if (saved != null) {
            draft = saved.getString("draft", ""); uncertain = saved.getBoolean("uncertain", false);
            try { if (saved.containsKey("agent")) agent = new JSONObject(saved.getString("agent")); } catch (JSONException ignored) { }
        }
        if (agent != null && !draft.isEmpty()) review(); else homeShell();
    }
    private int dp(int value) { return (int)(value * getResources().getDisplayMetrics().density + .5f); }
    private TextView label(String text, int size, int color, boolean bold) {
        TextView view = new TextView(this); view.setText(text); view.setTextSize(size); view.setTextColor(color); view.setGravity(Gravity.CENTER);
        view.setLineSpacing(dp(2), 1); if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD); view.setPadding(0, dp(5), 0, dp(5)); return view;
    }
    private void shell(String title, String subtitle) {
        scroll = new ScrollView(this); scroll.setFillViewport(true); scroll.setBackgroundColor(Color.BLACK); scroll.setVerticalScrollBarEnabled(false);
        page = new LinearLayout(this); page.setOrientation(LinearLayout.VERTICAL); page.setGravity(Gravity.CENTER_HORIZONTAL);
        // 18% side inset keeps every control within a small round Pixel Watch display.
        int side = (int)(getResources().getDisplayMetrics().widthPixels * .18);
        page.setPadding(side, dp(20), side, dp(40)); scroll.addView(page); setContentView(scroll);
        page.addView(label(title, 18, Color.rgb(244,237,255), true));
        status = label(subtitle, 12, Color.rgb(175,167,187), false); status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE); page.addView(status);
        scroll.setFocusable(true); scroll.setFocusableInTouchMode(true); scroll.requestFocus();
        scroll.setOnGenericMotionListener((view,event) -> {
            if (event.getAction() == MotionEvent.ACTION_SCROLL && event.isFromSource(android.view.InputDevice.SOURCE_ROTARY_ENCODER)) {
                scroll.smoothScrollBy(0, (int)(-event.getAxisValue(MotionEvent.AXIS_SCROLL) * dp(42))); return true;
            } return false;
        });
    }
    private void chip(String title, boolean primary, Runnable action) {
        Button button = new Button(this); button.setText(title); button.setAllCaps(false); button.setTextSize(14); button.setGravity(Gravity.CENTER);
        button.setTextColor(primary ? Color.BLACK : Color.rgb(244,237,255)); button.setMinHeight(dp(50));
        GradientDrawable background = new GradientDrawable(); background.setColor(primary ? Color.rgb(196,165,255) : Color.rgb(34,29,41)); background.setCornerRadius(dp(28)); button.setBackground(background);
        button.setPadding(dp(10), dp(10), dp(10), dp(10)); button.setOnClickListener(view -> { if (pendingId == null) action.run(); });
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2); p.topMargin = dp(8); page.addView(button, p);
    }
    private void homeShell() { shell("murmur", "Your agents, close by"); chip("Find my agents", true, () -> request("agents", "")); page.addView(label("Keep your paired phone nearby and connected to Tailscale.", 12, Color.GRAY, false)); }
    private void showAgents(JSONObject response) throws JSONException {
        agent = null; shell("murmur", "Choose an agent");
        JSONArray agents = response.getJSONArray("agents");
        for (int i = 0; i < agents.length(); i++) {
            JSONObject row = agents.getJSONObject(i);
            chip(row.getString("name") + "\n" + row.optString("home"), false, () -> { agent = row; latestChat = null; reading = false; uncertain = false; draft = ""; shell(row.optString("name"), "Opening Watch chat…"); request("read", ""); });
        }
        if (agents.length() == 0) page.addView(label("No agents yet. Add one in Murmur on your phone.", 14, Color.LTGRAY, false));
        JSONArray warnings = response.optJSONArray("warnings");
        if (warnings != null) for (int i = 0; i < warnings.length(); i++) page.addView(label(warnings.optString(i), 12, Color.rgb(253,141,140), false));
        chip("Refresh", false, () -> request("agents", ""));
    }
    private void showChat(JSONObject response) throws JSONException {
        latestChat = response; reading = false;
        shell(agent.optString("name", "Murmur"), response.optString("status", "Watch chat"));
        busy = response.optBoolean("busy") || response.optBoolean("waiting");
        JSONArray messages = response.optJSONArray("messages");
        if (!busy && !uncertain) chip("Say something", true, this::speak);
        if (response.optBoolean("waiting")) chip("Continue on phone", true, () -> request("open", ""));
        if (messages != null && messages.length() > 0) chip("Read reply", false, this::showReader);
        if (!busy && !uncertain) chip("Type a message", false, this::type);
        if (uncertain) page.addView(label("Check your phone before sending again.", 13, Color.rgb(253,141,140), false));
        chip("Refresh reply", false, () -> request("read", ""));
        chip("Other agents", false, () -> { agent = null; uncertain = false; reading = false; request("agents", ""); });
        handler.removeCallbacks(poll);
        if (response.optBoolean("busy") && foreground && !uncertain) handler.postDelayed(poll, 5000);
    }
    private void showReader() {
        int oldY = reading && scroll != null ? scroll.getScrollY() : 0;
        reading = true;
        handler.removeCallbacks(poll);
        shell(agent.optString("name", "Murmur"), "Watch chat · Recent messages");
        JSONArray messages = latestChat == null ? null : latestChat.optJSONArray("messages");
        if (messages != null) for (int i = 0; i < messages.length(); i++) {
            JSONObject row = messages.optJSONObject(i);
            if (row == null) continue;
            TextView message = label((row.optString("role").equals("user") ? "You\n" : "") + row.optString("text"), 14,
                    row.optString("role").equals("user") ? Color.rgb(196,165,255) : Color.rgb(239,234,246), false);
            message.setTextIsSelectable(true); message.setGravity(Gravity.START); page.addView(message);
        }
        chip("Refresh reply", false, () -> request("read", ""));
        chip("Continue on phone", false, () -> request("open", ""));
        chip("Back to agent", true, () -> { try { showChat(latestChat); } catch (JSONException ex) { showError("Refresh your chat."); } });
        scroll.post(() -> scroll.scrollTo(0, oldY));
        if (latestChat != null && latestChat.optBoolean("busy") && foreground && !uncertain) handler.postDelayed(poll, 5000);
    }
    private void speak() {
        composing = true;
        handler.removeCallbacks(poll);
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM).putExtra(RecognizerIntent.EXTRA_PROMPT, "Message " + agent.optString("name"));
        try { startActivityForResult(intent, 1); } catch (ActivityNotFoundException ex) { type(); }
    }
    private void type() {
        composing = true;
        handler.removeCallbacks(poll);
        shell("Your message", "Tap below to type");
        EditText input = new EditText(this); input.setText(draft); input.setTextColor(Color.WHITE); input.setTextSize(16); input.setHint("Message"); input.setHintTextColor(Color.GRAY); input.setMinHeight(dp(56)); input.setMaxLines(5);
        input.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(2000)}); page.addView(input, new LinearLayout.LayoutParams(-1, -2));
        chip("Review", true, () -> { draft = input.getText().toString().trim(); review(); }); chip("Cancel", false, () -> request("read", ""));
    }
    private void review() {
        composing = true;
        if (draft.isEmpty()) { type(); return; }
        shell("Send to " + agent.optString("name"), "Review your message");
        page.addView(label(draft, 15, Color.WHITE, false));
        chip("Send", true, () -> { String message = draft; request("send", message); }); chip("Edit", false, this::type); chip("Cancel", false, () -> { draft = ""; request("read", ""); });
    }
    private void request(String action, String text) {
        if (pendingId != null || !foreground) return;
        composing = false;
        handler.removeCallbacks(poll);
        pendingId = UUID.randomUUID().toString(); operation = action; String id = pendingId;
        status.setText(action.equals("send") ? "Sending once…" : "Connecting to phone…");
        handler.postDelayed(timeout, 60000);
        Wearable.getCapabilityClient(this).getCapability("murmur_phone_v1", CapabilityClient.FILTER_REACHABLE).addOnSuccessListener(capability -> {
            List<Node> nodes = new ArrayList<>(capability.getNodes());
            if (!id.equals(pendingId)) return;
            Node phone = nodes.stream().filter(Node::isNearby).findFirst().orElse(nodes.isEmpty() ? null : nodes.get(0));
            if (phone == null) { pendingId = null; handler.removeCallbacks(timeout); showError("No matching phone app is reachable. Install both APKs from the same build, connect your paired phone and open Murmur there."); return; }
            pendingNode = phone.getId();
            try {
                JSONObject body = new JSONObject().put("id", id).put("action", action);
                if (agent != null) body.put("profile", agent.getString("profile")).put("hermes", agent.getString("hermes"));
                if (action.equals("send")) body.put("text", text);
                Wearable.getMessageClient(this).sendMessage(phone.getId(), "/murmur/v1/request", body.toString().getBytes(StandardCharsets.UTF_8))
                        .addOnFailureListener(ex -> { if (id.equals(pendingId)) { handler.removeCallbacks(timeout); timeout.run(); } });
            } catch (JSONException ex) { pendingId = null; handler.removeCallbacks(timeout); showError("Choose your agent again."); }
        }).addOnFailureListener(ex -> { if (id.equals(pendingId)) { handler.removeCallbacks(timeout); timeout.run(); } });
    }
    @Override public void onMessageReceived(MessageEvent event) {
        if (!event.getPath().equals("/murmur/v1/response") || event.getData().length > 95000) return;
        String source = event.getSourceNodeId(); byte[] bytes = event.getData();
        runOnUiThread(() -> {
            try {
                JSONObject response = new JSONObject(new String(bytes, StandardCharsets.UTF_8));
                if (!foreground || pendingId == null || !pendingId.equals(response.optString("id")) || !source.equals(pendingNode)) return;
                handler.removeCallbacks(timeout); String completed = operation; pendingId = null; pendingNode = null;
                if (!response.optBoolean("ok")) { if (completed.equals("send")) { uncertain = true; draft = ""; } showError(response.optString("error", "Open Murmur on your phone.")); return; }
                if (completed.equals("agents")) showAgents(response);
                else if (completed.equals("open")) { shell("On your phone", response.optString("status")); chip("Back to chat", true, () -> request("read", "")); }
                else if (completed.equals("send")) { draft = ""; shell(agent.optString("name"), "Sent · Your agent is working"); chip("Read reply", true, () -> request("read", "")); handler.postDelayed(poll, 2000); }
                else { latestChat = response; if (reading) showReader(); else showChat(response); }
            } catch (JSONException ex) { pendingId = null; handler.removeCallbacks(timeout); showError("Update Murmur on both your phone and watch."); }
        });
    }
    private void showError(String error) {
        handler.removeCallbacks(poll); shell("Let's reconnect", "Phone connection needed"); page.addView(label(error, 14, Color.LTGRAY, false));
        chip("Try reading again", true, () -> request(agent == null ? "agents" : "read", ""));
        chip("Continue on phone", false, () -> request("open", ""));
    }
    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 1) { ArrayList<String> words = data == null ? null : data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS); if (resultCode == RESULT_OK && words != null && !words.isEmpty()) { draft = words.get(0); review(); } else type(); }
    }
    @Override protected void onResume() {
        super.onResume(); foreground = true;
        Wearable.getMessageClient(this).addListener(this).addOnSuccessListener(ignored -> {
            if (foreground && pendingId == null && draft.isEmpty() && !composing) request(agent == null ? "agents" : "read", "");
        });
    }
    @Override protected void onPause() {
        foreground = false; handler.removeCallbacks(poll); handler.removeCallbacks(timeout);
        if (pendingId != null && operation.equals("send")) { uncertain = true; draft = ""; }
        pendingId = null; Wearable.getMessageClient(this).removeListener(this); super.onPause();
    }
    private void handleBack() {
        if (agent != null && pendingId == null && (composing || reading)) {
            draft = ""; composing = false; reading = false;
            try { if (latestChat != null) showChat(latestChat); else request("read", ""); }
            catch (JSONException ex) { showError("Refresh your chat."); }
            return;
        }
        handler.removeCallbacks(poll); handler.removeCallbacks(timeout);
        pendingId = null; pendingNode = null;
        if (agent != null) { agent = null; draft = ""; uncertain = false; composing = false; request("agents", ""); }
        else finish();
    }
    @Override protected void onSaveInstanceState(Bundle state) { super.onSaveInstanceState(state); if (agent != null) state.putString("agent", agent.toString()); state.putString("draft", draft); state.putBoolean("uncertain", uncertain || (pendingId != null && operation.equals("send"))); }
}
