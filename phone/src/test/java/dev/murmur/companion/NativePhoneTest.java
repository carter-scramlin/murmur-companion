package dev.murmur.companion;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.*;
import android.os.Looper;
import android.view.*;
import android.webkit.WebView;
import android.widget.*;
import dev.murmur.companion.core.NativeChat;
import okhttp3.*;
import okhttp3.mockwebserver.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;
import java.io.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35,qualifiers="w411dp-h914dp-420dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class NativePhoneTest {
    @Test public void nativeListsStreamDirectlyWithoutAWebViewAndDraftSurvivesRecreation()throws Exception{
        MockWebServer server=new MockWebServer();AtomicInteger prompts=new AtomicInteger();
        server.setDispatcher(new okhttp3.mockwebserver.Dispatcher(){
            @Override public MockResponse dispatch(RecordedRequest request){
                if(request.getPath().equals("/murmur/connections"))return new MockResponse().setBody("{\"connections\":[{\"id\":\"home\",\"name\":\"At home\",\"status\":\"connected\"}]}");
                return new MockResponse().withWebSocketUpgrade(new WebSocketListener(){
                    @Override public void onClosing(WebSocket ws,int code,String reason){ws.close(code,reason);}
                    @Override public void onMessage(WebSocket ws,String text){try{
                        JSONObject call=new JSONObject(text);if(!call.has("method"))return;JSONObject result=new JSONObject();
                        switch(call.getString("method")){
                            case "client.capabilities" -> result.put("server_requests",true);
                            case "profiles.list" -> result.put("profiles",new JSONArray().put(new JSONObject().put("name","default").put("display_name","Atlas")));
                            case "session.list" -> result.put("sessions",new JSONArray().put(new JSONObject().put("id","stored").put("title","A little weekend away").put("preview","Let's find somewhere quiet.")).put(new JSONObject().put("id","watchstored").put("title","Watch chat").put("preview","A conversation from your wrist.")));
                            case "session.resume" -> result.put("session_id","runtime").put("stored_session_id","stored").put("info",new JSONObject().put("title","A little weekend away").put("model","test-model")).put("messages",new JSONArray().put(new JSONObject().put("role","user").put("text","Let's plan a quiet weekend." )).put(new JSONObject().put("role","assistant").put("text","Of course. **Somewhere close to home**, or a little further?")));
                            case "prompt.submit" -> {assertEquals("runtime",call.getJSONObject("params").getString("session_id"));prompts.incrementAndGet();result.put("status","streaming");}
                            default -> { }
                        }
                        ws.send(new JSONObject().put("id",call.get("id")).put("result",result).toString());
                        if(call.getString("method").equals("prompt.submit")){
                            event(ws,"message.start",1,new JSONObject());
                            event(ws,"message.delta",2,new JSONObject().put("text","Native ").put("offset",0).put("turn_id","test").put("segment",0));
                            event(ws,"message.delta",3,new JSONObject().put("text","reply").put("offset",7).put("turn_id","test").put("segment",0));
                            event(ws,"message.complete",4,new JSONObject().put("text","Native reply").put("status","complete"));
                        }
                    }catch(Exception ex){throw new AssertionError(ex);}}
                });
            }
        });
        server.start();Context context=RuntimeEnvironment.getApplication();context.getSharedPreferences("connection",0).edit().putString("origin",server.url("/").toString()).putBoolean("http",true).commit();
        try(ActivityController<MainActivity> controller=Robolectric.buildActivity(MainActivity.class).setup().visible()){
            MainActivity activity=controller.get();await(()->activity.store.selected!=null&&!activity.store.loading);
            settle();View decor=activity.getWindow().getDecorView();render(decor,"phone-native-chats.png");assertEquals(0,countWebViews(decor));
            View chat=findText(decor,"A little weekend away");assertNotNull(chat);clickRow(chat);await(()->activity.store.chat!=null&&activity.store.chat.ready&&!activity.store.loading);settle();
            EditText input=findEdit(activity.getWindow().getDecorView(),"Message your agent");assertNotNull(input);input.setText("Hello native");settle();View send=findDescription(activity.getWindow().getDecorView(),"Send message");assertNotNull(send);assertTrue(send.isEnabled());send.performClick();
            await(()->activity.store.chat!=null&&!activity.store.chat.busy&&activity.store.chat.rows.stream().anyMatch(r->r.text.equals("Native reply")));settle();assertEquals(1,prompts.get());assertEquals(0,countWebViews(activity.getWindow().getDecorView()));
            input.setText("Keep this thought for later");settle();render(activity.getWindow().getDecorView(),"phone-native-chat.png");
            controller.configurationChange();MainActivity recreated=controller.get();settle();EditText restored=findEdit(recreated.getWindow().getDecorView(),"Message your agent");assertNotNull(restored);assertEquals("Keep this thought for later",restored.getText().toString());assertEquals(1,prompts.get());
            recreated.onNewIntent(new android.content.Intent().putExtra("watch_hermes","home").putExtra("watch_profile","default"));
            await(()->recreated.store.chat!=null&&recreated.store.chat.storedId.equals("watchstored")&&recreated.store.chat.ready&&!recreated.store.loading);
            assertEquals(1,prompts.get());
        }finally{context.getSharedPreferences("connection",0).edit().clear().commit();context.getSharedPreferences("drafts",0).edit().clear().commit();server.shutdown();}
    }
    @Test public void nativeApprovalCardDoesNotAutoApproveAndShowsExactAction()throws Exception{
        Context context=RuntimeEnvironment.getApplication();context.getSharedPreferences("connection",0).edit().clear().commit();
        try(ActivityController<MainActivity> controller=Robolectric.buildActivity(MainActivity.class).setup().visible()){
            MainActivity activity=controller.get();NativeStore store=activity.store;store.selected=new NativeStore.Agent("home","default","Atlas","At home");store.agents.add(store.selected);store.online=true;
            NativeChat chat=new NativeChat("home","default","permission-chat");chat.snapshot(new JSONObject().put("session_id","runtime").put("info",new JSONObject().put("title","A hand with the cleanup"))
                .put("messages",new JSONArray().put(new JSONObject().put("role","user").put("text","Help me clean up my old downloads.")).put(new JSONObject().put("role","assistant").put("text","I've found a few old files. Let's check before removing anything.")))
                .put("open_requests",new JSONArray().put(new JSONObject().put("id","approval-one").put("method","approval").put("params",new JSONObject().put("description","Remove the selected old downloads?").put("command","Remove 3 selected files from Downloads").put("choices",new JSONArray().put("once").put("deny"))))));
            store.chat=chat;activity.showNative();settle();View decor=activity.getWindow().getDecorView();render(decor,"phone-native-approval.png");
            assertNotNull(findText(decor,"Allow once"));assertNotNull(findText(decor,"Don't allow"));assertEquals(1,chat.requests.size());assertEquals(0,countWebViews(decor));
            findText(decor,"Allow once").performClick();AlertDialog alert=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();assertTrue(alert.isShowing());assertEquals(1,chat.requests.size());alert.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();assertEquals(1,chat.requests.size());
        }
    }
    @Test public void workingCharacterHasExplicitStatusElapsedTimeAndStop()throws Exception{
        Context context=RuntimeEnvironment.getApplication();context.getSharedPreferences("connection",0).edit().clear().putBoolean("reduce_motion",true).commit();
        try(ActivityController<MainActivity> controller=Robolectric.buildActivity(MainActivity.class).setup().visible()){
            MainActivity activity=controller.get();NativeStore store=activity.store;
            store.selected=new NativeStore.Agent("home","garden","Juniper","At home",128);store.agents.add(store.selected);store.online=true;
            NativeChat chat=new NativeChat("home","garden","working");chat.snapshot(new JSONObject().put("session_id","runtime").put("info",new JSONObject().put("title","A quiet weekend"))
                .put("messages",new JSONArray().put(new JSONObject().put("role","user").put("text","Find us somewhere quiet for the weekend."))));
            chat.event(new JSONObject().put("session_id","runtime").put("type","tool.start").put("payload",new JSONObject().put("name","web_search")));chat.workStartedAt=System.currentTimeMillis()-125000;
            store.chat=chat;activity.showNative();settle();View decor=activity.getWindow().getDecorView();render(decor,"phone-native-working.png");
            assertNotNull(findText(decor,"Juniper is working"));assertNotNull(findText(decor,"Looking things up"));assertNotNull(findText(decor,"Stop"));assertEquals(0,countWebViews(decor));
            View character=findBuddy(decor);assertNotNull(character);assertEquals(128,((dev.murmur.companion.ui.BuddyView)character).hue(),.01);
            chat.event(new JSONObject().put("session_id","runtime").put("type","message.complete").put("payload",new JSONObject().put("text","Here are three lovely places.")));store.observer.run();settle();
            assertFalse(chat.busy);assertNotNull(findDescription(decor,"Send message"));
        }finally{context.getSharedPreferences("connection",0).edit().clear().commit();}
    }
    private static View findBuddy(View view){if(view instanceof dev.murmur.companion.ui.BuddyView)return view;if(view instanceof ViewGroup g)for(int i=0;i<g.getChildCount();i++){View found=findBuddy(g.getChildAt(i));if(found!=null)return found;}return null;}
    interface Check{boolean ready();}
    private static void await(Check check)throws Exception{long end=System.currentTimeMillis()+12000;while(System.currentTimeMillis()<end){shadowOf(Looper.getMainLooper()).idle();if(check.ready())return;Thread.sleep(15);}fail("Native UI did not reach the expected state");}
    private static void settle(){shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(100));}
    private static void event(WebSocket ws,String type,int seq,JSONObject payload)throws Exception{ws.send(new JSONObject().put("method","event").put("params",new JSONObject().put("type",type).put("session_id","runtime").put("seq",seq).put("payload",payload)).toString());}
    private static void clickRow(View view){while(view!=null){if(view.isClickable()){view.performClick();return;}view=view.getParent() instanceof View?(View)view.getParent():null;}fail("No clickable conversation row");}
    private static View findText(View view,String value){if(view instanceof TextView text&&value.contentEquals(text.getText()))return view;if(view instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++){View found=findText(group.getChildAt(i),value);if(found!=null)return found;}return null;}
    private static View findDescription(View view,String value){if(value.contentEquals(view.getContentDescription()==null?"":view.getContentDescription()))return view;if(view instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++){View found=findDescription(group.getChildAt(i),value);if(found!=null)return found;}return null;}
    private static EditText findEdit(View view,String description){View v=findDescription(view,description);return v instanceof EditText?(EditText)v:null;}
    private static int countWebViews(View view){int n=view instanceof WebView?1:0;if(view instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++)n+=countWebViews(group.getChildAt(i));return n;}
    private static void render(View view,String name)throws Exception{int width=1080,height=2400;view.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY));view.layout(0,0,width,height);Bitmap b=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);view.draw(new Canvas(b));File file=new File("build/reports/screenshots/"+name);file.getParentFile().mkdirs();try(OutputStream out=new FileOutputStream(file)){assertTrue(b.compress(Bitmap.CompressFormat.PNG,100,out));}b.recycle();}
}
