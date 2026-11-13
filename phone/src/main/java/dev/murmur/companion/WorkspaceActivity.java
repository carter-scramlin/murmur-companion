package dev.murmur.companion;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.*;
import android.net.http.SslError;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.webkit.*;
import android.widget.*;
import dev.murmur.companion.core.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import okhttp3.*;

/** Explicit full-workspace and sign-in fallback. Everyday chat lives in MainActivity. */
public class WorkspaceActivity extends androidx.activity.ComponentActivity {
    static final int INK = Color.rgb(29,22,38), MUTED = Color.rgb(112,104,122), PAPER = Color.rgb(254,250,237), VIOLET = Color.rgb(132,96,206);
    private LinearLayout root;
    private WebView web;
    private Endpoint endpoint;
    private FrameLayout frame;
    private ProgressBar progress;
    private TextView address;
    private PermissionRequest microphone;
    private ValueCallback<Uri[]> fileCallback;
    private String downloadUrl, downloadCookies;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Set<String> allowedSignIn = new HashSet<>();
    private boolean showingConnection;
    private android.content.SharedPreferences prefs() { return getSharedPreferences("connection", MODE_PRIVATE); }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getOnBackPressedDispatcher().addCallback(this, new androidx.activity.OnBackPressedCallback(true) { @Override public void handleOnBackPressed() { handleBack(); } });
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        getWindow().setStatusBarColor(PAPER); getWindow().setNavigationBarColor(PAPER);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        String server = prefs().getString("origin", "");
        try { endpoint = new Endpoint(server, prefs().getBoolean("http", false)); showWeb(state); }
        catch (IllegalArgumentException ex) { showConnection(); }
    }
    private void base() {
        root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(PAPER);
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout() | WindowInsets.Type.ime());
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom); return WindowInsets.CONSUMED;
            }
            view.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(), insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom()); return insets.consumeSystemWindowInsets();
        });
        setContentView(root);
        if (Build.VERSION.SDK_INT >= 30) getWindow().getInsetsController().setSystemBarsAppearance(WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS, WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
    }
    private int dp(float value) { return (int)(value * getResources().getDisplayMetrics().density + .5f); }
    private TextView text(String value, int size, int color, boolean bold) {
        TextView label = new TextView(this); label.setText(value); label.setTextSize(size); label.setTextColor(color);
        if (bold) label.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        label.setLineSpacing(dp(3), 1); return label;
    }
    private GradientDrawable shape(int color, int radius) { GradientDrawable bg = new GradientDrawable(); bg.setColor(color); bg.setCornerRadius(dp(radius)); return bg; }
    private Button button(String label, boolean primary, Runnable action) {
        Button button = new Button(this); button.setText(label); button.setTextSize(15); button.setAllCaps(false);
        button.setTextColor(primary ? Color.WHITE : INK); button.setBackground(shape(primary ? VIOLET : Color.rgb(240,235,226), 18));
        button.setMinHeight(dp(54)); button.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2); p.topMargin = dp(12); button.setLayoutParams(p); return button;
    }
    private void gap(LinearLayout layout, int height) { View gap = new View(this); layout.addView(gap, new LinearLayout.LayoutParams(1, dp(height))); }
    private void showConnection() {
        showingConnection = true;
        if (microphone != null) { microphone.deny(); microphone = null; }
        if (web != null) { web.destroy(); web = null; }
        base();
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true); root.addView(scroll, new LinearLayout.LayoutParams(-1, -1));
        LinearLayout page = new LinearLayout(this); page.setOrientation(LinearLayout.VERTICAL); page.setPadding(dp(28), dp(40), dp(28), dp(32)); scroll.addView(page);
        android.widget.ImageView mark = new android.widget.ImageView(this); mark.setImageResource(R.drawable.murmur_icon); mark.setContentDescription("Two Murmur buddies");
        mark.setBackground(shape(Color.rgb(244,237,221), 24)); page.addView(mark, new LinearLayout.LayoutParams(dp(90), dp(90)));
        gap(page, 28); page.addView(text("Your agents.\nA little closer.", 36, INK, true)); gap(page, 12);
        page.addView(text("Murmur on your phone, and a quiet conversation on your wrist.", 17, MUTED, false)); gap(page, 32);
        page.addView(text("Your Murmur address", 14, INK, true)); gap(page, 8);
        EditText url = new EditText(this); url.setSingleLine(true); url.setTextSize(16); url.setTextColor(INK); url.setHintTextColor(MUTED);
        url.setHint("https://umbrel.your-tailnet.ts.net"); url.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_URI);
        url.setText(prefs().getString("origin", "")); url.setPadding(dp(16), dp(16), dp(16), dp(16)); url.setBackground(shape(Color.WHITE, 16));
        url.setContentDescription("Murmur server address"); page.addView(url, new LinearLayout.LayoutParams(-1, -2)); gap(page, 8);
        page.addView(text("Paste the address that opens Murmur in your browser. Use Tailscale HTTPS for access away from home.", 13, MUTED, false));
        Switch local = new Switch(this); local.setText("Allow a private HTTP connection"); local.setTextSize(14); local.setTextColor(INK); local.setMinHeight(dp(54)); local.setChecked(prefs().getBoolean("http", false)); page.addView(local);
        TextView httpHint = text("HTTP has no browser TLS protection. Voice needs HTTPS; Tailscale still encrypts traffic between tailnet devices.", 12, MUTED, false); page.addView(httpHint);
        TextView error = text("", 13, Color.rgb(163,48,57), false); error.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE); page.addView(error);
        page.addView(button("Connect to Murmur", true, () -> {
            try {
                Endpoint next = new Endpoint(url.getText().toString(), local.isChecked());
                endpoint = next; allowedSignIn.clear();
                prefs().edit().putString("origin", next.origin).putBoolean("http", local.isChecked()).apply(); showWeb(null);
            } catch (IllegalArgumentException ex) { error.setText(ex.getMessage()); }
        }));
        page.addView(button("Open Tailscale", false, this::openTailscale)); gap(page, 28);
        page.addView(text("PAIR ONCE. REACH HOME ANYWHERE.", 11, VIOLET, true)); gap(page, 8);
        page.addView(text("Connect your phone and Umbrel to the same tailnet. Your Pixel Watch uses the paired phone’s connection.", 14, MUTED, false)); gap(page, 24);
        page.addView(text("Murmur Companion · Independent, open source\nMade for Murmur by Umbrel", 12, MUTED, false));
    }
    @SuppressWarnings("SetJavaScriptEnabled") private void showWeb(Bundle state) {
        showingConnection = false; base();
        LinearLayout toolbar = new LinearLayout(this); toolbar.setGravity(Gravity.CENTER_VERTICAL); toolbar.setPadding(dp(16), 0, dp(8), 0);
        TextView brand = text("Workspace", 18, INK, true); toolbar.addView(brand);
        Button done = new Button(this); done.setText("Native app"); done.setAllCaps(false); done.setContentDescription("Return to native app"); done.setOnClickListener(v -> finish()); toolbar.addView(done);
        address = text(Uri.parse(endpoint.origin).getHost(), 11, MUTED, false); address.setMaxLines(1); address.setEllipsize(android.text.TextUtils.TruncateAt.END);
        address.setPadding(dp(12), 0, dp(8), 0); toolbar.addView(address, new LinearLayout.LayoutParams(0, -2, 1));
        Button menu = new Button(this); menu.setText("•••"); menu.setContentDescription("Connection and app settings"); menu.setTextColor(INK); menu.setBackgroundColor(Color.TRANSPARENT); menu.setOnClickListener(v -> showMenu(menu)); toolbar.addView(menu, new LinearLayout.LayoutParams(dp(48), dp(48)));
        root.addView(toolbar, new LinearLayout.LayoutParams(-1, dp(48)));
        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal); progress.setMax(100); root.addView(progress, new LinearLayout.LayoutParams(-1, dp(2)));
        frame = new FrameLayout(this); root.addView(frame, new LinearLayout.LayoutParams(-1, 0, 1));
        web = new WebView(this); web.setBackgroundColor(PAPER); frame.addView(web, new FrameLayout.LayoutParams(-1, -1));
        WebSettings settings = web.getSettings(); settings.setJavaScriptEnabled(true); settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false); settings.setAllowContentAccess(false); settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setMediaPlaybackRequiresUserGesture(false); settings.setSupportMultipleWindows(true); settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setSafeBrowsingEnabled(true);
        CookieManager.getInstance().setAcceptCookie(true); CookieManager.getInstance().setAcceptThirdPartyCookies(web, false);
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                if (!request.isForMainFrame()) return false;
                return navigate(request.getUrl().toString());
            }
            @Override public void onPageFinished(WebView view, String url) { address.setText(Uri.parse(url).getHost()); CookieManager.getInstance().flush(); }
            @Override public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) { if (request.isForMainFrame()) showFailure("Can't reach your Murmur", "Check Tailscale, then try again. Your Umbrel may be asleep or restarting."); }
            @Override public void onReceivedHttpError(WebView view, WebResourceRequest request, WebResourceResponse response) { if (request.isForMainFrame() && response.getStatusCode() >= 500) showFailure("Murmur is taking a moment", "Your server answered, but Murmur isn't ready. Try again shortly."); }
            @Override public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) { handler.cancel(); showFailure("This connection isn't trusted", "Check the address and the server's HTTPS certificate. Tailscale Serve provides a trusted HTTPS address."); }
            @Override public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) { frame.removeView(view); view.destroy(); web = null; showFailure("Let's reopen Murmur", "Android closed the page to free memory."); return true; }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override public void onProgressChanged(WebView view, int value) { progress.setProgress(value); progress.setVisibility(value == 100 ? View.GONE : View.VISIBLE); }
            @Override public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (!endpoint.contains(view.getUrl())) return false;
                if (fileCallback != null) fileCallback.onReceiveValue(null); fileCallback = callback;
                try { startActivityForResult(params.createIntent(), 20); } catch (ActivityNotFoundException ex) { fileCallback.onReceiveValue(null); fileCallback = null; }
                return true;
            }
            @Override public void onPermissionRequest(PermissionRequest request) { runOnUiThread(() -> {
                if (!endpoint.contains(request.getOrigin().toString()) || !endpoint.origin.startsWith("https:")) { request.deny(); return; }
                if (!Arrays.asList(request.getResources()).contains(PermissionRequest.RESOURCE_AUDIO_CAPTURE)) { request.deny(); return; }
                if (microphone != null) microphone.deny(); microphone = request;
                if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) grantMicrophone();
                else requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 21);
            }); }
            @Override public void onPermissionRequestCanceled(PermissionRequest request) { if (microphone == request) microphone = null; }
            @Override public boolean onCreateWindow(WebView view, boolean dialog, boolean gesture, Message result) {
                if (!gesture) return false;
                WebView popup = new WebView(WorkspaceActivity.this);
                popup.setWebViewClient(new WebViewClient() {
                    @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest request) {
                        String url = request.getUrl().toString();
                        if (endpoint.contains(url)) web.loadUrl(url); else openExternal(url);
                        popup.destroy(); return true;
                    }
                });
                ((WebView.WebViewTransport) result.obj).setWebView(popup); result.sendToTarget(); return true;
            }
        });
        web.setDownloadListener((url, agent, disposition, mime, length) -> {
            if (!endpoint.contains(url)) { toast("Open this download in your browser."); return; }
            downloadUrl = url; downloadCookies = CookieManager.getInstance().getCookie(endpoint.origin);
            Intent save = new Intent(Intent.ACTION_CREATE_DOCUMENT).setType(mime == null ? "application/octet-stream" : mime).addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE, URLUtil.guessFileName(url, disposition, mime));
            try { startActivityForResult(save, 22); } catch (ActivityNotFoundException ex) { toast("No file picker is available."); }
        });
        if (state == null || web.restoreState(state.getBundle("web") == null ? new Bundle() : state.getBundle("web")) == null) web.loadUrl(endpoint.origin);
    }
    private boolean navigate(String url) {
        if (endpoint.contains(url)) return false;
        Uri uri = Uri.parse(url); String scheme = uri.getScheme();
        if (!"https".equals(scheme)) { if ("mailto".equals(scheme) || "tel".equals(scheme)) openExternal(url); else toast("Only your saved server and HTTPS pages can open here."); return true; }
        if (allowedSignIn.contains(uri.getHost())) return false;
        new AlertDialog.Builder(this).setTitle("Open " + uri.getHost() + "?")
                .setMessage("You're leaving your saved Murmur server. Continue here if this is your Umbrel sign-in, or open the link in your browser.")
                .setPositiveButton("Continue here", (d,w) -> { allowedSignIn.add(uri.getHost()); web.loadUrl(url); })
                .setNeutralButton("Browser", (d,w) -> openExternal(url)).setNegativeButton("Cancel", null).show(); return true;
    }
    private void grantMicrophone() {
        if (microphone != null) {
            if (endpoint != null && endpoint.contains(microphone.getOrigin().toString()) && web != null && endpoint.contains(web.getUrl())) microphone.grant(new String[]{PermissionRequest.RESOURCE_AUDIO_CAPTURE});
            else microphone.deny();
            microphone = null;
        }
    }
    @Override public void onRequestPermissionsResult(int request, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(request, permissions, results);
        if (request == 21 && microphone != null) { if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) grantMicrophone(); else { microphone.deny(); microphone = null; } }
    }
    private void showMenu(View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor);
        String[] labels = {"Reload", "Check connection", "Pixel Watch", "Open Tailscale", "Open in browser", "Change server", "Forget this device", "About"};
        for (int i = 0; i < labels.length; i++) menu.getMenu().add(0, i, i, labels[i]);
        menu.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 0 -> { if (web != null) web.reload(); }
                case 1 -> diagnose();
                case 2 -> watchSettings();
                case 3 -> openTailscale();
                case 4 -> openExternal(endpoint.origin);
                case 5 -> showConnection();
                case 6 -> new AlertDialog.Builder(this).setTitle("Forget this device?").setMessage("Remove saved server details and this app's sign-in cookies. Your chats stay on Murmur.").setPositiveButton("Forget", (d,w) -> forget()).setNegativeButton("Cancel", null).show();
                case 7 -> new AlertDialog.Builder(this).setTitle("Murmur Companion 0.2.0").setMessage("Independent Android and Wear OS clients for Murmur by Umbrel. Not affiliated with or endorsed by Umbrel or Tailscale.\n\nYour conversations go to your server. The watch relays through your phone.\n\nThis is the optional full workspace. The main phone interface uses native Android screens. Background web push and browser-only sign-ins may require Chrome; see the feature matrix in the README.").setPositiveButton("Done", null).show();
            } return true;
        }); menu.show();
    }
    private void watchSettings() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 23);
        boolean enabled = prefs().getBoolean("watch", true);
        new AlertDialog.Builder(this).setTitle("Your Pixel Watch").setMessage("Install the matching watch APK, open Murmur on your phone and sign in, then open it on your watch.\n\nThe watch uses the phone's Tailscale connection. It has its own ‘Watch chat’ per agent. Approvals and rich content open on your phone. The phone must be reachable.\n\nWatch access is " + (enabled ? "on." : "off."))
                .setPositiveButton(enabled ? "Turn access off" : "Turn access on", (d,w) -> prefs().edit().putBoolean("watch", !enabled).apply()).setNegativeButton("Done", null).show();
    }
    private void diagnose() {
        String cookie = CookieManager.getInstance().getCookie(endpoint.origin);
        ConnectivityManager cm = (ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE);
        NetworkCapabilities nc = cm.getNetworkCapabilities(cm.getActiveNetwork());
        String network = nc != null && nc.hasTransport(NetworkCapabilities.TRANSPORT_VPN) ? "Android reports an active VPN." : "Android doesn't report an active VPN. Check Tailscale if you're away from home.";
        toast("Checking your server…");
        Endpoint target = endpoint;
        io.execute(() -> {
            String message;
            try (MurmurApi api = new MurmurApi(target, cookie)) {
                org.json.JSONObject session = api.get("/murmur/session");
                message = session.optBoolean("signedIn") ? "Murmur is reachable and signed in." : "Murmur is reachable. Finish signing in on the phone.";
            } catch (Exception ex) { message = ex.getMessage(); }
            String result = message;
            runOnUiThread(() -> { if (!isFinishing() && !isDestroyed()) new AlertDialog.Builder(this).setTitle("Connection check").setMessage(result + "\n\n" + network + "\n\n" + target.origin).setPositiveButton("Done", null).show(); });
        });
    }
    private void showFailure(String title, String detail) {
        if (frame == null || frame.getChildCount() > 1) return;
        LinearLayout error = new LinearLayout(this); error.setOrientation(LinearLayout.VERTICAL); error.setGravity(Gravity.CENTER_VERTICAL); error.setPadding(dp(28), dp(24), dp(28), dp(24)); error.setBackgroundColor(PAPER);
        error.addView(text(title, 28, INK, true)); gap(error, 12); error.addView(text(detail, 16, MUTED, false));
        error.addView(button("Try again", true, () -> { if (web == null) showWeb(null); else { frame.removeView(error); web.loadUrl(endpoint.origin); } }));
        error.addView(button("Open Tailscale", false, this::openTailscale)); error.addView(button("Connection settings", false, this::showConnection));
        frame.addView(error, new FrameLayout.LayoutParams(-1, -1));
    }
    private void openTailscale() {
        Intent launch = getPackageManager().getLaunchIntentForPackage("com.tailscale.ipn");
        if (launch != null) startActivity(launch); else openExternal("https://play.google.com/store/apps/details?id=com.tailscale.ipn");
    }
    private void openExternal(String url) {
        Uri uri = Uri.parse(url);
        if (!Arrays.asList("https", "http", "mailto", "tel").contains(uri.getScheme())) return;
        try { startActivity(new Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE)); } catch (ActivityNotFoundException ex) { toast("No app can open that link."); }
    }
    private void forget() {
        prefs().edit().clear().apply();
        getSharedPreferences("watch_receipts", MODE_PRIVATE).edit().clear().apply();
        if (web != null) { web.clearCache(true); web.clearHistory(); }
        WebStorage.getInstance().deleteAllData();
        CookieManager.getInstance().removeAllCookies(done -> { CookieManager.getInstance().flush(); endpoint = null; showConnection(); });
    }
    private void toast(String message) { Toast.makeText(this, message, Toast.LENGTH_LONG).show(); }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == 20 && fileCallback != null) { fileCallback.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(result, data)); fileCallback = null; }
        if (request == 22 && result == RESULT_OK && data != null && data.getData() != null && downloadUrl != null) {
            Uri destination = data.getData(); String source = downloadUrl, cookies = downloadCookies; downloadUrl = null; downloadCookies = null;
            toast("Saving your file…"); io.execute(() -> saveDownload(source, cookies, destination));
        }
    }
    private void saveDownload(String source, String cookies, Uri destination) {
        OkHttpClient client = new OkHttpClient.Builder().followRedirects(false).followSslRedirects(false).callTimeout(5, TimeUnit.MINUTES).build();
        try (Response response = client.newCall(new Request.Builder().url(source).header("Cookie", cookies == null ? "" : cookies).build()).execute()) {
            if (!response.isSuccessful() || response.body() == null) throw new IOException("Download unavailable. Reopen it in Murmur.");
            try (InputStream input = response.body().byteStream(); OutputStream output = getContentResolver().openOutputStream(destination)) {
                if (output == null) throw new IOException("Can't save to this location.");
                byte[] bytes = new byte[8192]; int count; while ((count = input.read(bytes)) != -1) output.write(bytes, 0, count);
            }
            runOnUiThread(() -> toast("File saved."));
        } catch (Exception ex) { runOnUiThread(() -> toast("Couldn't save the file. " + ex.getMessage())); }
        finally { client.connectionPool().evictAll(); client.dispatcher().executorService().shutdown(); }
    }
    private void handleBack() { if (!showingConnection && web != null && web.canGoBack()) web.goBack(); else if (showingConnection && endpoint != null) showWeb(null); else finish(); }
    @Override protected void onSaveInstanceState(Bundle out) { super.onSaveInstanceState(out); if (web != null) { Bundle state = new Bundle(); web.saveState(state); out.putBundle("web", state); } }
    @Override protected void onPause() { super.onPause(); if (web != null) web.onPause(); CookieManager.getInstance().flush(); }
    @Override protected void onResume() { super.onResume(); if (web != null) web.onResume(); }
    @Override protected void onDestroy() { if (microphone != null) microphone.deny(); if (fileCallback != null) fileCallback.onReceiveValue(null); if (web != null) web.destroy(); io.shutdown(); super.onDestroy(); }
}
