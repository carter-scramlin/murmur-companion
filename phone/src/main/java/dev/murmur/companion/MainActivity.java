package dev.murmur.companion;

import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.*;
import android.speech.RecognizerIntent;
import android.text.*;
import android.text.method.LinkMovementMethod;
import android.view.*;
import android.view.inputmethod.*;
import android.webkit.CookieManager;
import android.webkit.WebStorage;
import android.widget.*;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.*;
import dev.murmur.companion.core.*;
import dev.murmur.companion.ui.BuddyView;
import org.json.*;
import java.util.*;
import static dev.murmur.companion.NativeViews.*;

/** Native Android launcher, conversations and streaming transcript. */
public class MainActivity extends androidx.activity.ComponentActivity {
    NativeStore store;
    private LinearLayout root, body, toolbar, composer, requestBox,activityCard;
    private BuddyView headerBuddy,activityBuddy;
    private TextView activityTitle,activityDetail,activityElapsed;
    private Button stopWork;
    private final Runnable tick=new Runnable(){public void run(){if(foreground&&!isFinishing()&&!isDestroyed()&&surface.equals("native")&&store!=null&&store.chat!=null&&store.chat.busy){scheduleRender();main.postDelayed(this,1000);}}};
    private final Runnable saveDraft=()->rememberDraft();
    private TextView title, subtitle, banner, empty, sendHint;
    private EditText input;
    private NativeChat composerChat;
    private View send;
    private RecyclerView list;
    private NativeAdapter adapter;
    private Endpoint endpoint;
    private android.speech.tts.TextToSpeech speech;
    private String pendingSpeech="";
    private String pendingSession="";
    private String screen="", query="", surface="connection";
    private final Handler main=new Handler(Looper.getMainLooper());
    private boolean updatePending,foreground;
    private android.content.SharedPreferences prefs(){return getSharedPreferences("connection",MODE_PRIVATE);}
    @Override public void onCreate(Bundle state){
        palette(this);setTheme(dark?android.R.style.Theme_Material_NoActionBar:R.style.AppTheme);
        super.onCreate(state);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        getWindow().setStatusBarColor(PAPER);getWindow().setNavigationBarColor(PAPER);
        getWindow().getDecorView().setSystemUiVisibility(dark?0:View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        store=new ViewModelProvider(this).get(NativeStore.class);
        store.observer=this::scheduleRender;
        if(state!=null)query=state.getString("query","");
        getOnBackPressedDispatcher().addCallback(this,new androidx.activity.OnBackPressedCallback(true){@Override public void handleOnBackPressed(){
            if(surface.equals("native")&&store.chat!=null){rememberDraft();store.back();}
            else if(surface.equals("native")&&!store.tab.equals("chats")){store.tab="chats";render();}
            else if(surface.equals("connection")&&endpoint!=null)showNative();else finish();
        }});
        try{endpoint=new Endpoint(prefs().getString("origin",""),prefs().getBoolean("http",false));showNative();configure();}
        catch(IllegalArgumentException ex){showConnection();}
        handoff(getIntent());
    }
    private void handoff(Intent intent){if(intent!=null&&intent.hasExtra("watch_hermes")){pendingSession=intent.getStringExtra("open_session");if(pendingSession==null)pendingSession="";store.openWatch(intent.getStringExtra("watch_hermes"),intent.getStringExtra("watch_profile"));intent.removeExtra("watch_hermes");intent.removeExtra("watch_profile");}}
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);handoff(intent);}
    private void configure(){if(endpoint!=null)store.configure(endpoint,CookieManager.getInstance().getCookie(endpoint.origin));}
    private int d(float n){return dp(this,n);}
    private void base(){
        root=column(this);root.setBackgroundColor(PAPER);
        root.setOnApplyWindowInsetsListener((v,i)->{
            if(Build.VERSION.SDK_INT>=30){android.graphics.Insets bars=i.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout()|WindowInsets.Type.ime());v.setPadding(bars.left,bars.top,bars.right,bars.bottom);return WindowInsets.CONSUMED;}
            v.setPadding(i.getSystemWindowInsetLeft(),i.getSystemWindowInsetTop(),i.getSystemWindowInsetRight(),i.getSystemWindowInsetBottom());return i.consumeSystemWindowInsets();
        });setContentView(root);
        if(Build.VERSION.SDK_INT>=30&&getWindow().getInsetsController()!=null)getWindow().getInsetsController().setSystemBarsAppearance(dark?0:WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS|WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS|WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
    }
    private void space(LinearLayout parent,int n){parent.addView(new View(this),new LinearLayout.LayoutParams(1,d(n)));}
    private void showConnection(){
        surface="connection";screen="";base();
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);root.addView(scroll);
        LinearLayout page=column(this);pad(page,28,36,28);scroll.addView(page);
        ImageView mark=new ImageView(this);mark.setImageResource(R.drawable.murmur_icon);mark.setContentDescription("Murmur Companion");page.addView(mark,new LinearLayout.LayoutParams(d(80),d(80)));space(page,24);
        page.addView(text(this,"Your agents.\nRight here.",36,INK,true));space(page,12);
        page.addView(text(this,"Native conversations for Android. A little closer to home.",17,MUTED,false));space(page,32);
        page.addView(text(this,"Your Murmur address",14,INK,true));space(page,8);
        EditText url=new EditText(this);url.setSingleLine();url.setTextSize(16);url.setTextColor(INK);url.setHintTextColor(MUTED);url.setHint("https://umbrel.your-tailnet.ts.net");url.setText(prefs().getString("origin",""));url.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_URI);pad(url,16,16,16);url.setBackground(shape(this,CARD,18));url.setContentDescription("Murmur server address");page.addView(url,new LinearLayout.LayoutParams(-1,-2));space(page,8);
        page.addView(text(this,"Use the address that opens Murmur. Tailscale HTTPS works at home and away.",13,MUTED,false));
        Switch local=new Switch(this);local.setText("Allow a private HTTP connection");local.setTextColor(INK);local.setTextSize(14);local.setMinHeight(d(56));local.setChecked(prefs().getBoolean("http",false));page.addView(local);
        TextView problem=text(this,"",13,RED,false);problem.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);page.addView(problem);space(page,8);
        page.addView(button(this,"Connect to Murmur",true,()->{try{endpoint=new Endpoint(url.getText().toString(),local.isChecked());prefs().edit().putString("origin",endpoint.origin).putBoolean("http",local.isChecked()).apply();showNative();configure();}catch(IllegalArgumentException ex){problem.setText(ex.getMessage());}}),new LinearLayout.LayoutParams(-1,d(54)));space(page,12);
        page.addView(button(this,"Open Tailscale",false,this::tailscale),new LinearLayout.LayoutParams(-1,d(52)));space(page,28);
        page.addView(text(this,"PRIVATE BY DESIGN",11,PURPLE,true));space(page,8);page.addView(text(this,"Your phone connects to your server. Your watch uses the paired phone. No added chat service or analytics.",14,MUTED,false));space(page,24);
        page.addView(text(this,"Independent companion for Murmur by Umbrel",12,MUTED,false));
    }
    void showNative(){surface="native";screen="";render();}
    private void scheduleRender(){if(!foreground||!surface.equals("native")||updatePending)return;updatePending=true;main.postDelayed(()->{updatePending=false;if(foreground&&!isDestroyed()&&!isFinishing())render();},40);}
    private String key(){return store.chat!=null?"chat:"+store.chat.hermes+":"+store.chat.profile+":"+store.chat.storedId:store.tab+":"+(store.selected==null?"":store.selected.key());}
    private void render(){
        if(!surface.equals("native"))return;
        if(!pendingSession.isEmpty()&&store.selected!=null&&!store.loading){String id=pendingSession;pendingSession="";if(id.length()<=256)store.open(id,"Conversation");}
        String next=key();if(!next.equals(screen)){screen=next;buildScreen();}
        if(store.chat!=null){
            NativeChat chat=store.chat;title.setText(chat.title.isBlank()?"Conversation":chat.title);subtitle.setText((store.selected==null?"Agent":store.selected.name)+" · "+(store.loading?"Syncing…":chat.status));
            boolean canSend=store.online&&!store.loading&&!store.sending&&!store.uploading&&chat.ready&&chat.requests.isEmpty()&&!chat.busy;
            if(send instanceof Glyph glyph){String name=chat.busy?"stop":"send";if(!glyph.name.equals(name))replaceSend(name);}
            send.setEnabled(chat.busy&&store.online||canSend&&input!=null&&(!input.getText().toString().trim().isEmpty()||!store.attachments.getOrDefault(chat.key(),Collections.emptyList()).isEmpty()));send.setAlpha(send.isEnabled()?1f:.4f);
            send.setContentDescription(chat.busy?"Stop response":"Send message");
            sendHint.setText(store.uploading?"Adding your attachment…":!store.attachments.getOrDefault(chat.key(),Collections.emptyList()).isEmpty()?store.attachments.get(chat.key()).size()+" attached · Ready to send":store.loading?"Syncing conversation…":!chat.requests.isEmpty()?"Your agent needs your answer":!store.online?"Reconnect to send · Your draft stays here":chat.busy?"Your agent is working · Tap stop to interrupt":"Your messages go directly to your Murmur");
            updateRequests(chat);
            updateActivity(chat);
            List<Item> items=new ArrayList<>();for(NativeChat.Row row:chat.rows)items.add(new Item(row.id,row.role,row.text,row.streaming?"streaming":"",store.selected==null?"":Float.toString(store.selected.hue)));
            if(chat.busy&&chat.requests.isEmpty())items.add(new Item("live-work","activity",(store.selected==null?"Your agent":store.selected.name)+" is "+(chat.phase()==NativeChat.Phase.THINKING?"thinking":chat.phase()==NativeChat.Phase.REPLYING?"replying":"working"),chat.activityLabel()));
            if(items.isEmpty())items.add(new Item("welcome","welcome",store.selected==null?"Say hello.":"A little space for you and "+store.selected.name+".","Start a conversation. Your messages stay on your server."));
            boolean bottom=!list.canScrollVertically(1);adapter.submit(items,()->{if(bottom&&!items.isEmpty())list.scrollToPosition(items.size()-1);});
        }else if(!store.tab.equals("settings")){
            title.setText(store.tab.equals("agents")?"Your agents":store.archived?"Archived conversations":"Conversations");subtitle.setText(store.tab.equals("agents")?"A familiar face, whenever you need one":store.selected==null?store.connection:store.selected.name+" · "+store.selected.home);
            List<Item> items=new ArrayList<>();
            if(store.tab.equals("agents")){
                for(NativeStore.Agent a:store.agents)if((a.name+" "+a.home).toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT)))items.add(new Item("agent:"+a.key(),"agent",a.name,a.home,a.hue+":"+store.agentPhase(a).name()));
            }else if(store.selected!=null){
                JSONArray sessions=store.sessions();if(sessions!=null)for(int i=0;i<sessions.length();i++){JSONObject s=sessions.optJSONObject(i);if(s==null)continue;String t=s.optString("title","Untitled conversation"),preview=s.optString("preview",s.optInt("message_count")+" messages");if((t+" "+preview).toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT)))items.add(new Item(s.optString("id"),"chat",t.isBlank()?"Untitled conversation":t,(s.optBoolean("pinned")?"Pinned · ":"")+preview,store.selected.hue+":"+store.listState(store.selected,s.optString("id"))));}
            }
            if(store.moreChats&&!store.tab.equals("agents"))items.add(new Item("load-more","chat","Load more conversations","Read older history"));
            if(items.isEmpty())items.add(new Item("empty","empty",store.loading?"Finding your conversations…":query.isBlank()?(store.tab.equals("agents")?"Your agents will be here.":"Make room for a new thought."):"No matches",store.loading?"Connecting privately to your Murmur.":store.agents.isEmpty()?"Sign in to your server or open the workspace to set up your first agent.":"Choose an agent and start a new conversation."));
            adapter.submit(items,null);
        }
        banner.setText(store.error.isBlank()?store.loading?"Connecting…":store.online?"":"Offline · Tap Reconnect":store.error);
        banner.setVisibility(banner.getText().length()==0?View.GONE:View.VISIBLE);banner.setTextColor(store.error.isBlank()?MUTED:RED);banner.setBackgroundColor(store.error.isBlank()?SOFT:dark?0xff41232b:0xfff7e9e8);
    }
    private void buildScreen(){
        rememberDraft();main.removeCallbacks(tick);base();input=null;composerChat=null;send=null;adapter=null;headerBuddy=null;activityCard=null;
        toolbar=row(this);pad(toolbar,12,8,8);root.addView(toolbar,new LinearLayout.LayoutParams(-1,d(80)));
        boolean chat=store.chat!=null;
        if(chat){toolbar.addView(icon(this,"back","Back to conversations",()->{rememberDraft();store.back();}));headerBuddy=buddy(store.selected,BuddyView.Mood.IDLE,44);LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(d(44),d(44));bp.rightMargin=d(8);toolbar.addView(headerBuddy,bp);}
        else {ImageView logo=new ImageView(this);logo.setImageResource(R.drawable.murmur_icon);logo.setContentDescription("Murmur Companion");LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(d(42),d(42));lp.setMargins(d(4),0,d(12),0);toolbar.addView(logo,lp);}
        LinearLayout heading=column(this);title=text(this,"",chat?19:25,INK,true);title.setMaxLines(1);title.setEllipsize(TextUtils.TruncateAt.END);subtitle=text(this,"",11,MUTED,false);subtitle.setMaxLines(1);subtitle.setEllipsize(TextUtils.TruncateAt.END);heading.addView(title);heading.addView(subtitle);toolbar.addView(heading,new LinearLayout.LayoutParams(0,-2,1));
        if(!chat&&!store.tab.equals("settings"))toolbar.addView(icon(this,"plus","New conversation",this::chooseNewChat));
        toolbar.addView(icon(this,"more","Conversation and connection options",this::menu));
        banner=text(this,"",13,RED,false);pad(banner,20,10,10);banner.setBackgroundColor(0xfff7e9e8);banner.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);banner.setOnClickListener(v->connectionActions());root.addView(banner,new LinearLayout.LayoutParams(-1,-2));
        body=column(this);root.addView(body,new LinearLayout.LayoutParams(-1,0,1));
        if(chat)buildActivityCard();
        if(store.tab.equals("settings")&&!chat){buildSettings();}
        else{
            if(!chat)buildSearch();
            list=new RecyclerView(this);LinearLayoutManager manager=new LinearLayoutManager(this);manager.setStackFromEnd(chat);list.setLayoutManager(manager);list.setClipToPadding(false);list.setPadding(d(16),d(8),d(16),d(12));list.setItemAnimator(null);list.setHasFixedSize(false);adapter=new NativeAdapter();list.setAdapter(adapter);body.addView(list,new LinearLayout.LayoutParams(-1,0,1));
            if(chat)buildComposer();
        }
        if(!chat)buildNavigation();
    }
    private BuddyView buddy(NativeStore.Agent agent,BuddyView.Mood state,int size){
        BuddyView view=new BuddyView(this);view.character(agent==null?"buddy":agent.profile,agent==null?278:agent.hue);view.mood(state);view.motionAllowed(!prefs().getBoolean("reduce_motion",false));view.setLayoutParams(new LinearLayout.LayoutParams(d(size),d(size)));return view;
    }
    private BuddyView.Mood mood(NativeChat.Phase phase){return switch(phase){case THINKING->BuddyView.Mood.THINKING;case WORKING->BuddyView.Mood.WORKING;case REPLYING->BuddyView.Mood.REPLYING;case WAITING->BuddyView.Mood.WAITING;case DONE->BuddyView.Mood.DONE;case ERROR->BuddyView.Mood.ERROR;default->BuddyView.Mood.IDLE;};}
    private void buildActivityCard(){
        activityCard=row(this);pad(activityCard,12,10,10);activityCard.setBackground(shape(this,SOFT,22));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(d(16),d(4),d(16),d(10));body.addView(activityCard,p);
        activityBuddy=buddy(store.selected,BuddyView.Mood.IDLE,60);activityCard.addView(activityBuddy);
        LinearLayout copy=column(this);pad(copy,8,0,0);activityTitle=text(this,"",16,INK,true);activityTitle.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);activityDetail=text(this,"",12,MUTED,false);activityDetail.setMaxLines(2);activityDetail.setEllipsize(TextUtils.TruncateAt.END);activityElapsed=text(this,"",10,MUTED,false);activityElapsed.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);copy.addView(activityTitle);copy.addView(activityDetail);copy.addView(activityElapsed);activityCard.addView(copy,new LinearLayout.LayoutParams(0,-2,1));stopWork=button(this,"Stop",false,()->store.stop());stopWork.setMinWidth(0);activityCard.addView(stopWork,new LinearLayout.LayoutParams(d(62),d(48)));
    }
    private void updateActivity(NativeChat chat){
        BuddyView.Mood state=store.loading?BuddyView.Mood.THINKING:store.online?mood(chat.phase()):BuddyView.Mood.OFFLINE;
        if(headerBuddy!=null){headerBuddy.character(store.selected==null?"buddy":store.selected.profile,store.selected==null?278:store.selected.hue);headerBuddy.mood(state);headerBuddy.setContentDescription((store.selected==null?"Agent":store.selected.name)+" · "+(store.online?chat.stateLabel():"Offline"));}
        if(activityCard==null)return;boolean visible=store.loading||chat.busy||!chat.requests.isEmpty()||!store.online;
        activityCard.setVisibility(visible?View.VISIBLE:View.GONE);activityBuddy.character(store.selected==null?"buddy":store.selected.profile,store.selected==null?278:store.selected.hue);activityBuddy.mood(state);
        String name=store.selected==null?"Your agent":store.selected.name;
        String label=store.loading?"Syncing your conversation":!store.online?"Connection interrupted":chat.status.equals("Stopping…")?"Stopping…":chat.phase()==NativeChat.Phase.WAITING?name+" needs you":name+" is "+chat.stateLabel().toLowerCase(Locale.ROOT);
        if(!label.contentEquals(activityTitle.getText()))activityTitle.setText(label);
        activityDetail.setText(store.loading?"Reading the latest messages from your server":store.online?chat.activityLabel():"Your draft is safe. No messages will be resent.");
        long elapsed=chat.workStartedAt==0?0:Math.max(0,(System.currentTimeMillis()-chat.workStartedAt)/1000);
        activityElapsed.setText(!store.online?"Tap the connection banner to retry":chat.phase()==NativeChat.Phase.WAITING?"Waiting for your permission or answer":elapsed<60?elapsed+"s elapsed":(elapsed/60)+"m "+(elapsed%60)+"s elapsed");
        stopWork.setVisibility(!store.loading&&store.online&&chat.busy&&chat.requests.isEmpty()?View.VISIBLE:View.GONE);stopWork.setEnabled(!chat.status.equals("Stopping…"));
        main.removeCallbacks(tick);if(foreground&&chat.busy)main.postDelayed(tick,1000);
    }
    private void buildSearch(){
        LinearLayout search=row(this);search.setBackground(shape(this,SOFT,18));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,d(48));lp.setMargins(d(20),d(4),d(20),d(16));body.addView(search,lp);
        View glyph=new Glyph(this,"search");search.addView(glyph,new LinearLayout.LayoutParams(d(48),d(44)));
        EditText field=new EditText(this);field.setText(query);field.setTextSize(14);field.setTextColor(INK);field.setHintTextColor(MUTED);field.setHint(store.tab.equals("agents")?"Find an agent":"Search conversations");field.setContentDescription(field.getHint());field.setSingleLine();field.setPadding(0,0,d(12),0);field.setBackgroundColor(Color.TRANSPARENT);search.addView(field,new LinearLayout.LayoutParams(0,-1,1));
        field.addTextChangedListener(watcher(value->{query=value;scheduleRender();}));
        if(!store.tab.equals("agents")&&store.selected!=null){LinearLayout r=row(this);pad(r,22,0,8);TextView label=text(this,"WITH "+store.selected.name.toUpperCase(Locale.ROOT),10,PURPLE,true);r.addView(label,new LinearLayout.LayoutParams(0,-2,1));TextView change=text(this,"Change agent",12,PURPLE,true);change.setPadding(d(8),d(8),d(8),d(8));change.setBackground(ripple(this,Color.TRANSPARENT,12));change.setOnClickListener(v->{query="";store.tab="agents";render();});r.addView(change);body.addView(r);}
    }
    private void buildNavigation(){
        LinearLayout nav=row(this);nav.setBackgroundColor(CARD);pad(nav,12,5,8);root.addView(nav,new LinearLayout.LayoutParams(-1,d(76)));
        String[] ids={"chats","agents","settings"},labels={"Chats","Agents","Settings"};
        for(int i=0;i<ids.length;i++){
            String id=ids[i];LinearLayout item=column(this);item.setGravity(Gravity.CENTER);item.setContentDescription(labels[i]);item.setBackground(ripple(this,Color.TRANSPARENT,18));item.setOnClickListener(v->{query="";store.tab=id;render();});
            View icon=new Glyph(this,id);icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);icon.setBackground(shape(this,store.tab.equals(id)?SOFT:Color.TRANSPARENT,20));item.addView(icon,new LinearLayout.LayoutParams(d(58),d(32)));TextView label=text(this,labels[i],11,store.tab.equals(id)?PURPLE:MUTED,store.tab.equals(id));label.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);label.setGravity(Gravity.CENTER);item.addView(label);nav.addView(item,new LinearLayout.LayoutParams(0,-1,1));
        }
    }
    private void buildComposer(){
        requestBox=column(this);body.addView(requestBox,new LinearLayout.LayoutParams(-1,-2));
        LinearLayout container=column(this);pad(container,16,8,8);container.setBackgroundColor(PAPER);body.addView(container,new LinearLayout.LayoutParams(-1,-2));
        composer=row(this);composer.setGravity(Gravity.BOTTOM);composer.setBackground(shape(this,CARD,26));pad(composer,4,4,4);container.addView(composer,new LinearLayout.LayoutParams(-1,-2));
        composer.addView(icon(this,"plus","Attach a photo or file",this::attach));
        composer.addView(icon(this,"mic","Dictate a message",this::dictate));
        composerChat=store.chat;
        input=new EditText(this);input.setTextSize(16);input.setTextColor(INK);input.setHintTextColor(MUTED);input.setHint("Message your agent…");input.setContentDescription("Message your agent");input.setBackgroundColor(Color.TRANSPARENT);input.setMinLines(1);input.setMaxLines(5);input.setPadding(d(4),d(12),d(4),d(12));input.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES|android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);input.setImeOptions(EditorInfo.IME_FLAG_NO_EXTRACT_UI);input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(32000)});
        String draft=store.drafts.getOrDefault(store.chat.key(),getSharedPreferences("drafts",0).getString(draftKey(store.chat),""));input.setText(draft);input.setSelection(input.length());composer.addView(input,new LinearLayout.LayoutParams(0,-2,1));
        replaceSend("send");
        input.addTextChangedListener(watcher(value->{if(composerChat!=null)store.drafts.put(composerChat.key(),value);main.removeCallbacks(saveDraft);main.postDelayed(saveDraft,500);scheduleRender();}));
        sendHint=text(this,"",10,MUTED,false);sendHint.setGravity(Gravity.CENTER);sendHint.setPadding(0,d(7),0,0);container.addView(sendHint);
    }
    private void replaceSend(String name){
        if(send!=null)composer.removeView(send);send=icon(this,name,name.equals("stop")?"Stop response":"Send message",()->{
            if(store.chat==null)return;
            if(store.chat.busy){store.stop();return;}
            String value=input.getText().toString().trim();if(value.isEmpty()){if(store.attachments.getOrDefault(store.chat.key(),Collections.emptyList()).isEmpty())return;value="Please review the attached files.";}
            if(!store.online||store.loading||store.sending||store.uploading||!store.chat.ready||!store.chat.requests.isEmpty())return;
            store.send(value);input.setText("");getSharedPreferences("drafts",0).edit().remove(draftKey(store.chat)).apply();
            send.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK);
        });send.setBackground(ripple(this,PURPLE,24));composer.addView(send,new LinearLayout.LayoutParams(d(44),d(44)));
    }
    private String shownRequests="";
    private void updateRequests(NativeChat chat){
        String signature=chat.storedId+":"+chat.requests.toString()+":"+store.online;
        if(requestBox==null||signature.equals(shownRequests)&&requestBox.getChildCount()>0)return;shownRequests=signature;requestBox.removeAllViews();
        if(chat.requests.isEmpty())return;
        Map.Entry<String,JSONObject> entry=chat.requests.entrySet().iterator().next();String id=entry.getKey();JSONObject request=entry.getValue(),params=request.optJSONObject("params");if(params==null)return;
        LinearLayout card=column(this);card.setBackground(shape(this,SOFT,20));pad(card,16,12,12);LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);cp.setMargins(d(16),0,d(16),d(4));requestBox.addView(card,cp);
        TextView count=text(this,"NEEDS YOU"+(chat.requests.size()>1?" · "+chat.requests.size()+" requests":""),10,PURPLE,true);card.addView(count);
        if("approval".equals(request.optString("method"))){
            TextView summary=text(this,params.optString("description","Your agent is asking for permission."),14,INK,true);summary.setMaxLines(3);card.addView(summary);
            if(!params.optString("command").isBlank()){TextView command=text(this,params.optString("command"),11,MUTED,false);command.setMaxLines(2);command.setEllipsize(TextUtils.TruncateAt.END);card.addView(command);}
            LinearLayout actions=row(this);card.addView(actions);JSONArray choices=params.optJSONArray("choices");boolean allow=choices==null||choices.toString().contains("\"once\""),deny=choices==null||choices.toString().contains("\"deny\"");
            if(deny){Button no=button(this,"Don't allow",false,()->answerApproval(id,"deny"));no.setEnabled(store.online);actions.addView(no,new LinearLayout.LayoutParams(0,d(48),1));}
            if(allow){Button yes=button(this,"Allow once",true,()->confirmApproval(id));yes.setEnabled(store.online);LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(0,d(48),1);bp.leftMargin=d(8);actions.addView(yes,bp);}
            card.setOnClickListener(v->approvalDetails(id));
        }else if(!"clarify".equals(request.optString("method"))){
            card.addView(text(this,params.optString("prompt",params.optString("display_name",params.optString("site","Your agent needs a private value."))),14,INK,true));
            Button enter=button(this,"Enter securely",true,()->answerSecret(id));enter.setEnabled(store.online);card.addView(enter,new LinearLayout.LayoutParams(-1,d(48)));
        }else{
            JSONArray questions=params.optJSONArray("questions");String question=questions!=null&&questions.length()>0?questions.optJSONObject(0).optString("question","Your agent has a question."):"Your agent has a question.";TextView text=text(this,question,14,INK,true);text.setMaxLines(3);card.addView(text);Button answer=button(this,"Answer"+(questions!=null&&questions.length()>1?" "+questions.length()+" questions":""),true,()->answerQuestions(id,paramsFor(id)));answer.setEnabled(store.online);card.addView(answer,new LinearLayout.LayoutParams(-1,d(48)));
        }
    }
    private JSONObject paramsFor(String id){JSONObject request=store.chat==null?null:store.chat.requests.get(id);return request==null?new JSONObject():request.optJSONObject("params");}
    private void answerApproval(String id,String choice){try{store.answer(id,new JSONObject().put("choice",choice));}catch(Exception ex){toast(ex.getMessage());}}
    private void approvalDetails(String id){JSONObject p=paramsFor(id);new AlertDialog.Builder(this).setTitle("Permission request").setMessage(p.optString("description")+"\n\n"+p.optString("command")).setPositiveButton("Done",null).show();}
    private void confirmApproval(String id){JSONObject p=paramsFor(id);new AlertDialog.Builder(this).setTitle("Allow this action once?").setMessage(p.optString("description")+"\n\n"+p.optString("command")).setPositiveButton("Allow once",(dialog,w)->answerApproval(id,"once")).setNegativeButton("Cancel",null).show();}
    private void answerQuestions(String id,JSONObject params){
        JSONArray questions=params==null?null:params.optJSONArray("questions");if(questions==null)return;
        ScrollView scroll=new ScrollView(this);LinearLayout form=column(this);pad(form,24,8,8);scroll.addView(form);Map<String,EditText> values=new LinkedHashMap<>();
        for(int i=0;i<questions.length();i++){JSONObject q=questions.optJSONObject(i);if(q==null)continue;form.addView(text(this,q.optString("question"),16,INK,true));EditText value=new EditText(this);value.setTextSize(15);value.setHint("Your answer");values.put(q.optString("qid"),value);form.addView(value,new LinearLayout.LayoutParams(-1,-2));JSONArray choices=q.optJSONArray("choices");if(choices!=null)for(int j=0;j<choices.length();j++){String choice=choices.optString(j);TextView option=text(this,"＋ "+choice,14,PURPLE,false);pad(option,8,10,10);option.setBackground(ripple(this,Color.TRANSPARENT,12));option.setOnClickListener(v->value.setText(q.optBoolean("multi_select")&&!value.getText().toString().isBlank()?value.getText()+", "+choice:choice));form.addView(option);}space(form,20);}
        new AlertDialog.Builder(this).setTitle("Your agent needs you").setView(scroll).setPositiveButton("Send answers",(dialog,w)->{try{JSONObject answers=new JSONObject();for(Map.Entry<String,EditText> e:values.entrySet())answers.put(e.getKey(),e.getValue().getText().toString().isBlank()?JSONObject.NULL:e.getValue().getText().toString());store.answer(id,new JSONObject().put("answers",answers).put("outcome","submitted"));}catch(Exception ex){toast(ex.getMessage());}}).setNegativeButton("Later",null).show();
    }
    private void chooseNewChat(){if(store.agents.isEmpty()){connectionActions();return;}if(store.selected==null||store.tab.equals("agents")){String[] labels=store.agents.stream().map(a->a.name+" · "+a.home).toArray(String[]::new);new AlertDialog.Builder(this).setTitle("Talk to…").setItems(labels,(dialog,w)->{store.selected=store.agents.get(w);store.tab="chats";store.newChat();}).show();}else store.newChat();}
    private void menu(){
        List<String> labels=new ArrayList<>();List<Runnable> actions=new ArrayList<>();
        java.util.function.BiConsumer<String,Runnable> add=(name,action)->{labels.add(name);actions.add(action);};
        add.accept("Reconnect & sync",()->store.reconnect());
        if(store.chat!=null){
            if(store.chat.busy&&store.online)add.accept("Notify when this reply finishes",this::followReply);add.accept("Rename conversation",this::rename);if(store.online&&!store.chat.busy){add.accept("Choose model",this::chooseModel);add.accept("Thinking level",this::chooseReasoning);}add.accept("Copy conversation",this::copyConversation);if(!store.attachments.getOrDefault(store.chat.key(),Collections.emptyList()).isEmpty())add.accept("Remove staged attachments",()->store.clearAttachments());
            add.accept("Share / export shown messages",this::shareConversation);add.accept("Read full history",()->startActivity(new Intent(this,HistoryActivity.class).putExtra("home",store.chat.hermes).putExtra("profile",store.chat.profile).putExtra("session",store.chat.storedId)));
            if(store.online&&!store.chat.busy){
                add.accept("Pin conversation",()->store.edit("pinned",true));add.accept("Unpin conversation",()->store.edit("pinned",false));
                add.accept(store.archived?"Restore conversation":"Archive conversation",()->store.edit("archived",!store.archived));
                add.accept("Delete conversation",()->new AlertDialog.Builder(this).setTitle("Delete this conversation?").setMessage("This permanently deletes it from your Murmur server.").setNegativeButton("Cancel",null).setPositiveButton("Delete",(dialog,w)->store.delete()).show());
            }
        }else if(store.selected!=null){add.accept(store.archived?"Show recent conversations":"Show archived conversations",()->{store.toggleArchived();});}
        if(store.online&&store.selected!=null){add.accept("Edit agent personality",()->store.describe(store.selected,description->editAgent(store.selected,description)));add.accept("Create agent",()->editAgent(null,new JSONObject()));}
        add.accept("Full Murmur workspace",this::workspace);add.accept("Connection & sign-in",this::connectionActions);
        new AlertDialog.Builder(this).setItems(labels.toArray(new String[0]),(dialog,which)->actions.get(which).run()).show();
    }
    private void followReply(){if(store.chat==null||store.selected==null)return;if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS},23);toast("Allow notifications, then choose Notify again.");return;}Intent monitor=new Intent(this,ReplyMonitorService.class).putExtra("home",store.chat.hermes).putExtra("profile",store.chat.profile).putExtra("session",store.chat.storedId).putExtra("name",store.selected.name);try{startForegroundService(monitor);toast("Following this reply for up to 30 minutes. Stop from the notification.");}catch(Exception ex){toast("Couldn't start following. Keep Murmur open to see the reply.");}}
    private void chooseModel(){store.models(options->{JSONArray providers=options.optJSONArray("providers");if(providers==null)return;List<String> labels=new ArrayList<>(),ids=new ArrayList<>();List<JSONArray> choices=new ArrayList<>();for(int i=0;i<providers.length();i++){JSONObject provider=providers.optJSONObject(i);JSONArray models=provider==null?null:provider.optJSONArray("models");if(models!=null&&models.length()>0){labels.add(provider.optString("name",provider.optString("slug")));ids.add(provider.optString("slug"));choices.add(models);}}new AlertDialog.Builder(this).setTitle("Model provider").setItems(labels.toArray(new String[0]),(dialog,index)->{JSONArray models=choices.get(index);String[] names=new String[models.length()];for(int i=0;i<names.length;i++)names[i]=models.optString(i);new AlertDialog.Builder(this).setTitle(labels.get(index)).setItems(names,(d,w)->new AlertDialog.Builder(this).setTitle("Use "+names[w]+"?").setMessage("This changes this conversation's model. Your provider's usage and pricing apply.").setNegativeButton("Cancel",null).setPositiveButton("Use model",(yes,n)->setConfig("model",names[w]+" --provider "+ids.get(index)+" --session",false)).show()).show();}).show();});}
    private void chooseReasoning(){String[] levels={"off","minimal","low","medium","high","xhigh","max"};new AlertDialog.Builder(this).setTitle("Thinking level").setMessage("Availability depends on your model.").setPositiveButton("Choose",(d,w)->new AlertDialog.Builder(this).setItems(levels,(dialog,which)->setConfig("reasoning",levels[which],false)).show()).setNegativeButton("Cancel",null).show();}
    private void setConfig(String key,String value,boolean confirmed){store.config(key,value,confirmed,result->{if(result.optBoolean("confirm_required"))new AlertDialog.Builder(this).setTitle("Confirm model change").setMessage(result.optString("confirm_message",result.optString("warning","The server asks you to confirm this model's cost."))).setNegativeButton("Cancel",null).setPositiveButton("Confirm",(d,w)->setConfig(key,value,true)).show();else toast(result.optString("warning","Updated for this conversation"));});}
    private void editAgent(NativeStore.Agent agent,JSONObject description){
        ScrollView scroll=new ScrollView(this);LinearLayout form=column(this);pad(form,24,8,8);scroll.addView(form);
        EditText name=new EditText(this);name.setSingleLine();name.setHint("Agent ID (lowercase, numbers, hyphens)");name.setText(agent==null?"":agent.profile);name.setEnabled(agent==null);form.addView(name);
        EditText summary=new EditText(this);summary.setHint("Description");summary.setText(description.optString("description"));form.addView(summary);
        EditText soul=new EditText(this);soul.setHint("Personality and instructions");soul.setMinLines(5);soul.setGravity(Gravity.TOP);soul.setText(description.optString("soul"));form.addView(soul,new LinearLayout.LayoutParams(-1,-2));
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle(agent==null?"Create agent":"Edit "+agent.name).setView(scroll).setNegativeButton("Cancel",null).setPositiveButton("Save",null).create();dialog.show();dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{String id=name.getText().toString().trim();if(!id.matches("[a-z0-9][a-z0-9_-]{0,63}")){name.setError("Use a lowercase agent ID");return;}if(agent==null){if(store.selected==null)return;store.createAgent(store.selected.hermes,id,summary.getText().toString(),soul.getText().toString());}else store.saveAgent(agent,summary.getText().toString(),soul.getText().toString());dialog.dismiss();});
    }
    private void attach(){if(store.chat==null||!store.online||!store.chat.ready||store.chat.busy||store.uploading){toast("Connect to an idle conversation to attach files.");return;}try{startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE),41);}catch(ActivityNotFoundException ex){toast("No file picker is available.");}}
    private void answerSecret(String id){
        JSONObject params=paramsFor(id),frame=store.chat==null?null:store.chat.requests.get(id);boolean login=frame!=null&&"vault.save_login".equals(frame.optString("method"));
        LinearLayout form=column(this);pad(form,24,8,8);EditText identifier=new EditText(this);identifier.setSingleLine();identifier.setHint("Email or username");identifier.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);if(login)form.addView(identifier);
        EditText value=new EditText(this);value.setSingleLine();value.setHint(login?"Password":"Private value");value.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);value.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);form.addView(value);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle(login?"Save login in your agent's vault":"Private reply to your agent").setMessage(params.optString("prompt",params.optString("command",params.optString("site","This value is sent directly to your Murmur server.")))).setView(form).setPositiveButton(login?"Save":"Send",null).setNegativeButton("Skip",(d,w)->{try{store.answer(id,new JSONObject().put("value",""));}catch(Exception ex){toast(ex.getMessage());}}).create();dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);dialog.setOnDismissListener(d->{value.setText("");identifier.setText("");});dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{if(value.length()==0){value.setError("Enter a value or choose Skip");return;}if(login&&identifier.getText().toString().trim().isEmpty()){identifier.setError("Enter your email or username");return;}try{String answer=login?new JSONObject().put("identifier",identifier.getText().toString().trim()).put("password",value.getText().toString()).toString():value.getText().toString();store.answer(id,new JSONObject().put("value",answer));value.setText("");identifier.setText("");dialog.dismiss();}catch(Exception ex){toast(ex.getMessage());}});
    }
    private void signIn(){
        if(endpoint==null){showConnection();return;}
        EditText password=new EditText(this);password.setSingleLine();password.setHint("Murmur password");password.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Sign in to Murmur").setMessage(endpoint.origin+"\nEnter the Murmur access password. Umbrel gateway sign-in is available through Workspace.").setView(password).setPositiveButton("Sign in",null).setNeutralButton("Workspace",(d,w)->workspace()).setNegativeButton("Cancel",null).create();dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);String value=password.getText().toString();password.setText("");store.login(value,cookies->{for(String cookie:cookies)CookieManager.getInstance().setCookie(endpoint.origin,cookie);CookieManager.getInstance().flush();configure();store.refresh();dialog.dismiss();},message->{dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);password.setError(message);});});
        dialog.setOnDismissListener(d->password.setText(""));
    }
    private String transcript(){StringBuilder text=new StringBuilder();if(store.chat!=null)for(NativeChat.Row row:store.chat.rows)text.append(row.role.equals("user")?"You":row.role.equals("assistant")?"Agent":"Activity").append(":\n").append(row.text).append("\n\n");return text.toString();}
    private void shareConversation(){startActivity(Intent.createChooser(new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_SUBJECT,store.chat==null?"Murmur conversation":store.chat.title).putExtra(Intent.EXTRA_TEXT,transcript()),"Export conversation"));}
    private void messageActions(String value){new AlertDialog.Builder(this).setItems(new String[]{"Copy","Read aloud","Share"},(d,w)->{if(w==0)copy(value);else if(w==1)readAloud(value);else startActivity(Intent.createChooser(new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,value),"Share message"));}).show();}
    private void readAloud(String value){pendingSpeech=value;if(speech!=null){speak();return;}speech=new android.speech.tts.TextToSpeech(this,status->{if(status==android.speech.tts.TextToSpeech.SUCCESS)speak();else toast("Text-to-speech is unavailable.");});}
    private void speak(){if(speech!=null){speech.stop();int max=android.speech.tts.TextToSpeech.getMaxSpeechInputLength();for(int i=0;i<pendingSpeech.length();i+=max)speech.speak(pendingSpeech.substring(i,Math.min(i+max,pendingSpeech.length())),android.speech.tts.TextToSpeech.QUEUE_ADD,null,"message-"+i);pendingSpeech="";}}
    private void rename(){NativeChat chat=store.chat;if(chat==null)return;EditText field=new EditText(this);field.setText(chat.title);field.setSingleLine();new AlertDialog.Builder(this).setTitle("Rename conversation").setView(field).setPositiveButton("Save",(dialog,w)->store.rename(field.getText().toString().trim())).setNegativeButton("Cancel",null).show();}
    private void connectionActions(){new AlertDialog.Builder(this).setTitle("Your connection").setMessage(store.error.isBlank()?store.connection:store.error).setPositiveButton(store.needsSignIn?"Sign in":"Reconnect",(dialog,w)->{if(store.needsSignIn)signIn();else store.reconnect();}).setNeutralButton("Sign in",(dialog,w)->signIn()).setNegativeButton("Tailscale",(dialog,w)->tailscale()).show();}
    private void buildSettings(){
        title.setText("Make yourself at home.");subtitle.setText("Murmur Companion · "+BuildConfig.VERSION_NAME);ScrollView scroll=new ScrollView(this);body.addView(scroll);LinearLayout page=column(this);pad(page,20,14,24);scroll.addView(page);
        page.addView(text(this,"YOUR CONNECTION",11,PURPLE,true));space(page,12);
        setting(page,"Murmur server",prefs().getString("origin","Your private server"),this::connectionActions);
        setting(page,"Sign in","Native Murmur password sign-in",this::signIn);
        setting(page,"Tailscale","Use your phone's private connection",this::tailscale);
        setting(page,"Pixel Watch","Paired phone relay · Native watch companion",this::watchSettings);space(page,24);
        page.addView(text(this,"YOUR WORKSPACE",11,PURPLE,true));space(page,12);
        setting(page,"Full Murmur workspace","Groups, models, memories, routines, files and integrations",this::workspace);
        setting(page,"Native conversations","Android lists, live replies, dictation, approval cards and saved drafts",()->new AlertDialog.Builder(this).setTitle("Native, where you talk").setMessage("Conversations are Android views, with a live connection directly to your server. Recent chats can be read offline; drafts stay in app-private storage. Attach files or photos with +. Long-press a reply to copy, share or read aloud. Manage pinning, archive and deletion from the conversation menu. Advanced server configuration and rich browser tools use Full workspace.").setPositiveButton("Done",null).show());space(page,24);
        setting(page,"Appearance","System, light or dark",()->new AlertDialog.Builder(this).setTitle("Appearance").setSingleChoiceItems(new String[]{"System","Light","Dark"},Arrays.asList("system","light","dark").indexOf(prefs().getString("theme","system")),(dialog,which)->{rememberDraft();prefs().edit().putString("theme",new String[]{"system","light","dark"}[which]).apply();dialog.dismiss();recreate();}).setNegativeButton("Cancel",null).show());
        Switch offline=new Switch(this);offline.setText("Save recent chats for offline reading");offline.setTextColor(INK);offline.setMinHeight(d(56));offline.setChecked(store.cacheEnabled());offline.setOnCheckedChangeListener((button,checked)->store.cacheEnabled(checked));page.addView(offline);
        page.addView(text(this,"Up to 10 opened chats stay in app-private storage. Turning this off deletes the saved read cache. Reconnect before sending.",12,MUTED,false));space(page,12);
        Switch motion=new Switch(this);motion.setText("Reduce character motion");motion.setTextColor(INK);motion.setTextSize(14);motion.setMinHeight(d(56));motion.setChecked(prefs().getBoolean("reduce_motion",false));motion.setOnCheckedChangeListener((button,checked)->prefs().edit().putBoolean("reduce_motion",checked).apply());page.addView(motion);
        setting(page,"Send beta feedback","Share device details without chats or server credentials",this::feedback);
        setting(page,"Change server","Connect to another Murmur",this::showConnection);
        setting(page,"Forget this device","Remove saved connection, sign-in and drafts",this::forget);
        space(page,24);page.addView(text(this,"Independent, open source. Made for Murmur by Umbrel.\nNo added analytics or hosted chat relay.",12,MUTED,false));
    }
    private void setting(LinearLayout parent,String name,String detail,Runnable click){LinearLayout row=row(this);pad(row,16,16,16);row.setBackground(ripple(this,CARD,20));LinearLayout copy=column(this);copy.addView(text(this,name,16,INK,true));space(copy,4);copy.addView(text(this,detail,12,MUTED,false));row.addView(copy,new LinearLayout.LayoutParams(0,-2,1));row.addView(new Glyph(this,"chevron"),new LinearLayout.LayoutParams(d(24),d(32)));row.setOnClickListener(v->click.run());row.setFocusable(true);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=d(8);parent.addView(row,p);}
    private void feedback(){
        String details="Murmur Companion "+BuildConfig.VERSION_NAME+"\nAndroid "+Build.VERSION.RELEASE+" (API "+Build.VERSION.SDK_INT+")\nDevice: "+Build.MANUFACTURER+" "+Build.MODEL+"\n\nWhat happened:\n\nWhat I expected:\n\nConnection: Wi-Fi / cellular / Tailscale (choose one)\n\nPlease remove private details before posting. Do not include cookies, server addresses or chat content.";
        Intent share=new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_SUBJECT,"Murmur Companion beta feedback").putExtra(Intent.EXTRA_TEXT,details);startActivity(Intent.createChooser(share,"Share beta feedback"));
    }
    private void watchSettings(){if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS},23);boolean enabled=prefs().getBoolean("watch",true);new AlertDialog.Builder(this).setTitle("Your Pixel Watch").setMessage("Install both APKs from the same release and sign in on your phone. The watch uses your phone's connection and has a separate Watch chat per agent.\n\nAccess is "+(enabled?"on.":"off.")).setPositiveButton(enabled?"Turn access off":"Turn access on",(dialog,w)->prefs().edit().putBoolean("watch",!enabled).apply()).setNegativeButton("Done",null).show();}
    private void workspace(){rememberDraft();startActivity(new Intent(this,WorkspaceActivity.class));}
    private void tailscale(){Intent intent=getPackageManager().getLaunchIntentForPackage("com.tailscale.ipn");if(intent==null)intent=new Intent(Intent.ACTION_VIEW,android.net.Uri.parse("https://play.google.com/store/apps/details?id=com.tailscale.ipn"));try{startActivity(intent);}catch(ActivityNotFoundException ex){toast("Install Tailscale on your phone first.");}}
    private void forget(){new AlertDialog.Builder(this).setTitle("Forget this device?").setMessage("Remove sign-in, saved connection and drafts on this phone. Server conversations remain.").setPositiveButton("Forget",(dialog,w)->{stopService(new Intent(this,ReplyMonitorService.class));prefs().edit().clear().apply();getSharedPreferences("drafts",0).edit().clear().apply();getSharedPreferences("watch_receipts",0).edit().clear().apply();WebStorage.getInstance().deleteAllData();android.webkit.WebView cacheCleaner=new android.webkit.WebView(this);cacheCleaner.clearCache(true);cacheCleaner.destroy();CookieManager.getInstance().removeAllCookies(done->{CookieManager.getInstance().flush();endpoint=null;store.reset();input=null;composerChat=null;showConnection();});}).setNegativeButton("Cancel",null).show();}
    private void dictate(){Intent intent=new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM).putExtra(RecognizerIntent.EXTRA_PROMPT,"Message your agent");try{startActivityForResult(intent,40);}catch(ActivityNotFoundException ex){toast("No dictation service is installed. Use your keyboard's microphone or type a message.");}}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request==41&&result==RESULT_OK&&data!=null&&data.getData()!=null){store.attach(data.getData());return;}if(request==40&&result==RESULT_OK&&data!=null&&input!=null){ArrayList<String> words=data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);if(words!=null&&!words.isEmpty()){String before=input.getText().toString();input.setText(before+(before.isBlank()?"":" ")+words.get(0));input.setSelection(input.length());input.requestFocus();}}}
    private void copyConversation(){if(store.chat==null)return;StringBuilder text=new StringBuilder();for(NativeChat.Row r:store.chat.rows)text.append(r.role.equals("user")?"You":"Agent").append(":\n").append(r.text).append("\n\n");copy(text.toString());}
    private void copy(String text){((android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("Murmur",text));toast("Copied");}
    private void toast(String message){Toast.makeText(this,message,Toast.LENGTH_SHORT).show();}
    private String draftKey(NativeChat c){return prefs().getString("origin","")+":"+c.hermes+":"+c.profile+":"+c.storedId;}
    private void rememberDraft(){if(input==null||store==null)return;NativeChat c=composerChat;if(c==null)return;String value=input.getText().toString();store.drafts.put(c.key(),value);getSharedPreferences("drafts",0).edit().putString(draftKey(c),value).apply();}
    private TextWatcher watcher(java.util.function.Consumer<String> change){return new TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){change.accept(s.toString());}public void afterTextChanged(Editable e){}};}
    @Override protected void onStart(){super.onStart();foreground=true;scheduleRender();if(store!=null)store.foreground(true);}
    @Override protected void onResume(){super.onResume();if(store!=null&&store.chat!=null&&store.chat.busy)main.postDelayed(tick,1000);if(surface.equals("native")){try{endpoint=new Endpoint(prefs().getString("origin",""),prefs().getBoolean("http",false));configure();}catch(IllegalArgumentException ex){endpoint=null;store.reset();showConnection();}}}
    @Override protected void onStop(){foreground=false;main.removeCallbacks(tick);main.removeCallbacks(saveDraft);rememberDraft();if(store!=null&&!isChangingConfigurations())store.foreground(false);super.onStop();}
    @Override protected void onSaveInstanceState(Bundle out){rememberDraft();out.putString("query",query);super.onSaveInstanceState(out);}
    @Override protected void onDestroy(){if(store!=null)store.observer=null;if(speech!=null){speech.stop();speech.shutdown();}main.removeCallbacksAndMessages(null);super.onDestroy();}
    private record Item(String id,String role,String text,String detail,String look){Item(String id,String role,String text,String detail){this(id,role,text,detail,"");}}
    private class NativeAdapter extends RecyclerView.Adapter<Holder>{
        List<Item> items=new ArrayList<>();
        NativeAdapter(){setHasStableIds(false);}
        void submit(List<Item> next,Runnable done){List<Item> before=items;DiffUtil.DiffResult diff=DiffUtil.calculateDiff(new DiffUtil.Callback(){public int getOldListSize(){return before.size();}public int getNewListSize(){return next.size();}public boolean areItemsTheSame(int o,int n){return before.get(o).id.equals(next.get(n).id);}public boolean areContentsTheSame(int o,int n){return before.get(o).equals(next.get(n));}});items=next;diff.dispatchUpdatesTo(this);if(done!=null)done.run();}
        @Override public int getItemViewType(int pos){return switch(items.get(pos).role){case "activity"->5;case "user"->1;case "assistant","notice","tool","plan"->2;case "agent","chat"->3;default->4;};}
        @Override public Holder onCreateViewHolder(ViewGroup parent,int type){
            LinearLayout outer=column(MainActivity.this);outer.setLayoutParams(new RecyclerView.LayoutParams(-1,-2));pad(outer,4,6,6);
            LinearLayout card=column(MainActivity.this);pad(card,16,type==3?18:14,type==3?18:14);outer.addView(card,new LinearLayout.LayoutParams(-1,-2));
            TextView label=text(MainActivity.this,"",10,MUTED,true),primary=text(MainActivity.this,"",type==3?17:16,INK,type==3),secondary=text(MainActivity.this,"",12,MUTED,false);
            LinearLayout line=row(MainActivity.this);if(type==2)line.setGravity(Gravity.TOP);BuddyView face=buddy(null,BuddyView.Mood.IDLE,type==3?56:type==5?32:26);LinearLayout.LayoutParams faceParams=new LinearLayout.LayoutParams(d(type==3?56:type==5?32:26),d(type==3?56:type==5?32:26));faceParams.rightMargin=d(type==2?7:12);line.addView(face,faceParams);
            LinearLayout copy=column(MainActivity.this);copy.addView(label);copy.addView(primary,new LinearLayout.LayoutParams(-1,-2));copy.addView(secondary);TextView badge=text(MainActivity.this,"",11,PURPLE,true);badge.setPadding(0,d(6),0,0);copy.addView(badge);line.addView(copy,new LinearLayout.LayoutParams(0,-2,1));card.addView(line);return new Holder(outer,card,label,primary,secondary,badge,face,type);
        }
        @Override public void onBindViewHolder(Holder h,int position){Item item=items.get(position);h.label.setVisibility(View.GONE);h.secondary.setVisibility(View.GONE);h.badge.setVisibility(View.GONE);h.face.setVisibility(View.GONE);h.primary.setTypeface(Typeface.create(h.type==3?"sans-serif-medium":"sans-serif",Typeface.NORMAL));
            LinearLayout.LayoutParams lp=(LinearLayout.LayoutParams)h.card.getLayoutParams();lp.leftMargin=h.type==1?d(38):0;lp.rightMargin=h.type==1?0:h.type==2?d(12):0;h.card.setLayoutParams(lp);
            NativeStore.Agent character=store.selected;if(item.role.equals("agent"))for(NativeStore.Agent agent:store.agents)if(item.id.equals("agent:"+agent.key())){character=agent;break;}
            h.face.character(character==null?"buddy":character.profile,character==null?278:character.hue);h.face.motionAllowed(!prefs().getBoolean("reduce_motion",false));h.face.mood(BuddyView.Mood.IDLE);
            if(h.type==1||h.type==2){
                h.card.setBackground(shape(MainActivity.this,h.type==1?SOFT:Color.TRANSPARENT,22));h.label.setVisibility(View.VISIBLE);h.label.setText(item.role.equals("user")?"YOU":item.role.equals("notice")?"CONNECTION NOTE":item.role.equals("tool")?"TOOL ACTIVITY":item.role.equals("plan")?"PLAN":store.selected==null?"YOUR AGENT":store.selected.name.toUpperCase(Locale.ROOT));h.label.setTextColor(item.role.equals("notice")?RED:MUTED);h.label.setPadding(0,0,0,d(7));
                h.face.setVisibility(item.role.equals("assistant")?View.VISIBLE:View.GONE);h.face.mood(item.detail.equals("streaming")?BuddyView.Mood.REPLYING:BuddyView.Mood.IDLE);h.primary.setText(!item.role.equals("user")?markdown(item.text):item.text);h.primary.setTextIsSelectable(true);h.primary.setLinksClickable(true);h.primary.setMovementMethod(LinkMovementMethod.getInstance());h.primary.setLineSpacing(d(5),1);h.primary.setOnLongClickListener(v->{messageActions(item.text);return true;});
                h.card.setOnClickListener(null);if(item.detail.equals("streaming")){h.secondary.setText("●  Replying");h.secondary.setTextColor(PURPLE);h.secondary.setVisibility(View.VISIBLE);}
            }else if(h.type==3){
                h.primary.setTextIsSelectable(false);h.primary.setMaxLines(1);h.primary.setEllipsize(TextUtils.TruncateAt.END);h.primary.setText(item.text);h.secondary.setText(item.detail);h.secondary.setTextColor(MUTED);h.secondary.setMaxLines(2);h.secondary.setEllipsize(TextUtils.TruncateAt.END);h.secondary.setVisibility(View.VISIBLE);h.secondary.setPadding(0,d(5),0,0);h.card.setBackground(ripple(MainActivity.this,CARD,22));
                h.face.setVisibility(View.VISIBLE);String state=item.role.equals("agent")?(character!=null&&store.agentPhase(character)!=NativeChat.Phase.IDLE?store.agentPhase(character).name():""):store.listState(store.selected,item.id);
                if(!state.isBlank()&&!Set.of("Ready","Finished","Stopped").contains(state)){h.badge.setText(state.equals("WAITING")?"Needs you":state.substring(0,1).toUpperCase(Locale.ROOT)+state.substring(1).toLowerCase(Locale.ROOT));h.badge.setVisibility(View.VISIBLE);h.face.mood(state.equals("Needs you")||state.equals("WAITING")?BuddyView.Mood.WAITING:state.equals("Replying")||state.equals("REPLYING")?BuddyView.Mood.REPLYING:BuddyView.Mood.WORKING);}
                h.card.setOnClickListener(v->{if(item.id.equals("load-more")){store.loadChats(true);return;}query="";if(item.role.equals("agent")){for(NativeStore.Agent a:store.agents)if(item.id.equals("agent:"+a.key())){store.select(a);break;}}else store.open(item.id,item.text);});h.card.setFocusable(true);
            }else if(h.type==5){
                h.face.setVisibility(View.VISIBLE);h.face.mood(store.chat==null?BuddyView.Mood.THINKING:mood(store.chat.phase()));pad(h.card,12,6,6);h.card.setBackgroundColor(Color.TRANSPARENT);h.primary.setTextSize(13);h.primary.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));h.primary.setText(item.text);h.secondary.setText(item.detail);h.secondary.setVisibility(View.VISIBLE);h.card.setOnClickListener(null);
            }else{
                h.face.setVisibility(View.VISIBLE);h.card.setBackground(shape(MainActivity.this,SOFT,26));pad(h.card,24,34,34);h.primary.setText(item.text);h.primary.setTextSize(25);h.primary.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));h.secondary.setText(item.detail);h.secondary.setVisibility(View.VISIBLE);h.secondary.setPadding(0,d(14),0,0);h.card.setOnClickListener(null);if(item.role.equals("empty")&&store.agents.isEmpty()&&!store.loading){h.card.setBackground(ripple(MainActivity.this,SOFT,26));if(!store.error.isBlank()&&!store.needsSignIn){h.primary.setText("Let’s get you reconnected.");h.secondary.setText("Tap to retry, or check Tailscale from the connection menu.");h.card.setOnClickListener(v->store.reconnect());}else{h.primary.setText(store.needsSignIn?"Sign in to your Murmur":"Your agents will be here.");h.secondary.setText("Tap to sign in or set up your Murmur workspace.");h.card.setOnClickListener(v->{if(store.needsSignIn)signIn();else workspace();});}}
            }
        }
        @Override public int getItemCount(){return items.size();}
    }
    private static class Holder extends RecyclerView.ViewHolder{
        final LinearLayout card;final TextView label,primary,secondary,badge;final BuddyView face;final int type;
        Holder(View outer,LinearLayout card,TextView label,TextView primary,TextView secondary,TextView badge,BuddyView face,int type){super(outer);this.card=card;this.label=label;this.primary=primary;this.secondary=secondary;this.badge=badge;this.face=face;this.type=type;}
    }
}
