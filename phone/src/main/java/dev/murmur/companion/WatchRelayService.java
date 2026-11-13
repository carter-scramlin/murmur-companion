package dev.murmur.companion;

import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.*;
import android.webkit.CookieManager;
import com.google.android.gms.tasks.Tasks;
import com.google.android.gms.wearable.*;
import dev.murmur.companion.core.*;
import org.json.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;

/** Play services enforces the matching application ID and signing certificate. */
public final class WatchRelayService extends WearableListenerService {
    private static final Object LOCK = new Object();
    @Override public void onMessageReceived(MessageEvent event) {
        if (!event.getPath().equals("/murmur/v1/request") || event.getData().length > 12000) return;
        // Serialize sends, including redeliveries, across service instances.
        synchronized (LOCK) {
            String id = ""; JSONObject response = new JSONObject();
            try {
                JSONObject request = new JSONObject(new String(event.getData(), StandardCharsets.UTF_8));
                id = request.getString("id");
                if (!id.matches("[a-zA-Z0-9-]{1,64}")) return;
                SharedPreferences prefs = getSharedPreferences("connection", MODE_PRIVATE);
                if (!prefs.getBoolean("watch", true)) throw new Exception("Watch access is off. Enable it in Murmur on your phone.");
                Endpoint endpoint = new Endpoint(prefs.getString("origin", ""), prefs.getBoolean("http", false));
                String action = request.getString("action");
                if (action.equals("open")) {
                    if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
                        throw new Exception("Open Murmur on your phone. Enable notifications there for handoff reminders.");
                    NotificationManager manager = getSystemService(NotificationManager.class);
                    manager.createNotificationChannel(new NotificationChannel("handoff", "Continue from watch", NotificationManager.IMPORTANCE_DEFAULT));
                    PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class).putExtra("watch_hermes",request.optString("hermes")).putExtra("watch_profile",request.optString("profile")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
                    manager.notify(10, new Notification.Builder(this, "handoff").setSmallIcon(R.drawable.ic_launcher_foreground).setContentTitle("Continue in Murmur")
                            .setContentText("Tap to continue in your native Watch chat.").setVisibility(Notification.VISIBILITY_PRIVATE).setContentIntent(open).setAutoCancel(true).build());
                    response.put("status", "Tap the Murmur reminder on your phone.");
                } else {
                    if (!action.equals("agents") && !action.equals("read") && !action.equals("send") && !action.equals("stop")) throw new Exception("Unsupported watch action.");
                    boolean send = action.equals("send");
                    SharedPreferences receipts = getSharedPreferences("watch_receipts", MODE_PRIVATE);
                    String receiptKey = endpoint.origin + ":" + id;
                    if (send && receipts.contains(receiptKey)) throw new Exception("This message was already attempted. Check the Watch chat on your phone before sending again.");
                    if (send) {
                        // Mark BEFORE the network mutation: an uncertain send is never replayed.
                        SharedPreferences.Editor edit = receipts.edit();
                        long cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(7);
                        for (String key : receipts.getAll().keySet()) if (receipts.getLong(key, 0) < cutoff) edit.remove(key);
                        edit.putLong(receiptKey, System.currentTimeMillis());
                        if (!edit.commit()) throw new Exception("Can't safely record this send. Open Murmur on your phone.");
                    }
                    CompletableFuture<String> cookie = new CompletableFuture<>();
                    new Handler(Looper.getMainLooper()).post(() -> {
                        try { cookie.complete(CookieManager.getInstance().getCookie(endpoint.origin)); }
                        catch (Exception ex) { cookie.completeExceptionally(ex); }
                    });
                    try (MurmurApi api = new MurmurApi(endpoint, cookie.get(4, TimeUnit.SECONDS))) {
                        response = WatchActions.execute(api, request);
                    }
                }
                response.put("ok", true);
            } catch (Exception ex) {
                try { response.put("ok", false).put("error", ex instanceof IllegalArgumentException ? "Set up Murmur on your phone first." : ex.getMessage() == null ? "Couldn't reach your phone. Open Murmur there and try again." : ex.getMessage()); }
                catch (JSONException ignored) { }
            }
            try {
                response.put("id", id);
                byte[] bytes = response.toString().getBytes(StandardCharsets.UTF_8);
                if (bytes.length > 90000) bytes = new JSONObject().put("id", id).put("ok", false).put("error", "This reply is long. Read it on your phone.").toString().getBytes(StandardCharsets.UTF_8);
                Tasks.await(Wearable.getMessageClient(this).sendMessage(event.getSourceNodeId(), "/murmur/v1/response", bytes), 8, TimeUnit.SECONDS);
            } catch (Exception ignored) { /* Watch timeout explains the uncertain outcome. */ }
        }
    }
}
