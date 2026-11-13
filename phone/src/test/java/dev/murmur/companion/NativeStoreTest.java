package dev.murmur.companion;

import android.os.Looper;
import dev.murmur.companion.core.*;
import okhttp3.*;
import okhttp3.mockwebserver.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35)
public class NativeStoreTest {
    MockWebServer server;NativeStore store;
    AtomicReference<WebSocket> live=new AtomicReference<>();
    AtomicReference<JSONObject> lastPrompt=new AtomicReference<>(),lastEdit=new AtomicReference<>();
    AtomicInteger uploads=new AtomicInteger();
    AtomicInteger prompts=new AtomicInteger(),connections=new AtomicInteger();
    CountDownLatch resumeSeen=new CountDownLatch(1),allowResume=new CountDownLatch(1);
    volatile boolean holdResume,backendRunning;
    @Before public void setup()throws Exception{
        server=new MockWebServer();server.setDispatcher(new okhttp3.mockwebserver.Dispatcher(){
            @Override public MockResponse dispatch(RecordedRequest request){
                if(request.getPath().equals("/murmur/connections"))return new MockResponse().setBody("{\"connections\":[{\"id\":\"home\",\"name\":\"Home\",\"status\":\"connected\"}]}");
                if(request.getPath().startsWith("/hermes/api/sessions?"))return new MockResponse().setBody("{\"sessions\":[{\"id\":\"stored\",\"title\":\"Hello\"}]}");
                if(request.getPath().contains("mutation-snapshot"))return new MockResponse().setBody("{\"runtime_revision\":7}");
                if("PATCH".equals(request.getMethod())){try{lastEdit.set(new JSONObject(request.getBody().readUtf8()));}catch(Exception ex){throw new AssertionError(ex);}return new MockResponse().setBody("{\"ok\":true}");}
                if(request.getPath().startsWith("/hermes/api/files/upload-stream")){uploads.incrementAndGet();return new MockResponse().setBody("{\"path\":\"/agent/attachments/note.txt\"}");}
                return new MockResponse().withWebSocketUpgrade(new WebSocketListener(){
                    @Override public void onOpen(WebSocket ws,Response response){live.set(ws);connections.incrementAndGet();}
                    @Override public void onClosing(WebSocket ws,int code,String reason){ws.close(code,reason);}
                    @Override public void onMessage(WebSocket ws,String text){try{
                        JSONObject call=new JSONObject(text);if(!call.has("method"))return;JSONObject result=new JSONObject();
                        switch(call.getString("method")){
                            case "profiles.list"->result.put("profiles",new JSONArray().put(new JSONObject().put("name","default").put("display_name","Atlas").put("path","/agent")));
                            case "session.list"->result.put("sessions",new JSONArray().put(new JSONObject().put("id","stored").put("title","Hello")));
                            case "session.active_list"->result.put("sessions",backendRunning?new JSONArray().put(new JSONObject().put("id","runtime").put("session_key","stored").put("status","working")):new JSONArray());
                            case "session.resume"->{resumeSeen.countDown();if(holdResume)allowResume.await(5,TimeUnit.SECONDS);result.put("session_id","runtime").put("stored_session_id","stored").put("messages",prompts.get()>0?new JSONArray().put(new JSONObject().put("role","user").put("text","Hello once")):new JSONArray());}
                            case "file.attach"->result.put("uploaded",false).put("ref_path","/agent/attachments/note.txt");
                            case "prompt.submit"->{lastPrompt.set(call.getJSONObject("params"));prompts.incrementAndGet();result.put("status","streaming");}
                            default->{ }
                        }
                        ws.send(new JSONObject().put("id",call.get("id")).put("result",result).toString());
                    }catch(Exception ex){throw new AssertionError(ex);}}
                });
            }
        });server.start();store=new NativeStore(RuntimeEnvironment.getApplication());store.configure(new Endpoint(server.url("/").toString(),true),"");
        await(()->store.selected!=null&&!store.loading);
    }
    @After public void close()throws Exception{allowResume.countDown();store.onCleared();server.shutdown();}
    @Test public void backDuringSlowResumeDoesNotReopenTheOldScreen()throws Exception{
        holdResume=true;store.open("stored","Hello");assertTrue(resumeSeen.await(3,TimeUnit.SECONDS));store.back();
        assertNull(store.chat);allowResume.countDown();await(()->!store.loading);assertNull(store.chat);assertEquals(0,prompts.get());
    }
    @Test public void cachedChatReceivesCompletionAfterNavigatingBack()throws Exception{
        store.open("stored","Hello");await(()->store.chat!=null&&store.chat.ready&&!store.loading);
        send("message.start",1,new JSONObject());await(()->store.chat.busy);NativeChat cached=store.chat;store.back();await(()->!store.loading);
        send("message.complete",2,new JSONObject().put("text","All done"));await(()->!cached.busy&&!cached.rows.isEmpty());
        assertEquals("Finished",store.listState(store.selected,"stored"));assertEquals(NativeChat.Phase.DONE,cached.phase());assertEquals("All done",cached.rows.get(0).text);assertEquals(NativeChat.Phase.IDLE,store.agentPhase(store.selected));
    }
    @Test public void reconnectResumesReadOnlyAndNeverRepeatsAPrompt()throws Exception{
        store.open("stored","Hello");await(()->store.chat!=null&&store.chat.ready&&!store.loading);store.send("Hello once");await(()->prompts.get()==1&&!store.sending);
        live.get().close(1001,"Simulated server restart");await(()->!store.online);shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(2));
        await(()->store.online&&store.chat.ready&&!store.loading&&connections.get()>1);assertEquals(1,prompts.get());assertEquals("Hello once",store.chat.rows.get(0).text);
    }
    @Test public void nativeArchiveUsesStoredRevisionAndDoesNotSubmitAPrompt()throws Exception{
        store.open("stored","Hello");await(()->store.chat!=null&&store.chat.ready&&!store.loading);store.edit("archived",true);await(()->lastEdit.get()!=null&&store.chat==null&&!store.loading);
        assertTrue(lastEdit.get().getBoolean("archived"));assertEquals(7,lastEdit.get().getInt("expected_revision"));assertFalse(lastEdit.get().getString("request_id").isEmpty());assertEquals(0,prompts.get());
    }
    @Test public void attachmentStagesOnlySelectedFileAndIsSentOnceWithThePrompt()throws Exception{
        store.open("stored","Hello");await(()->store.chat!=null&&store.chat.ready&&!store.loading);java.io.File file=java.io.File.createTempFile("selected-",".txt");
        try{java.nio.file.Files.write(file.toPath(),"My chosen file".getBytes(java.nio.charset.StandardCharsets.UTF_8));store.attach(android.net.Uri.fromFile(file));await(()->!store.uploading);assertEquals("",store.error);assertEquals(1,uploads.get());assertEquals(0,prompts.get());assertEquals(1,store.attachments.get(store.chat.key()).size());store.send("Read this note");await(()->prompts.get()==1&&!store.sending);assertTrue(lastPrompt.get().getString("text").contains("@file:"));assertTrue(lastPrompt.get().getString("text").endsWith("Read this note"));assertFalse(store.attachments.containsKey(store.chat.key()));}
        finally{file.delete();}
    }
    private void send(String type,int seq,JSONObject payload)throws Exception{if(type.equals("message.start"))backendRunning=true;if(type.equals("message.complete"))backendRunning=false;live.get().send(new JSONObject().put("method","event").put("params",new JSONObject().put("session_id","runtime").put("seq",seq).put("type",type).put("payload",payload)).toString());}
    interface Check{boolean yes();}
    private void await(Check check)throws Exception{long end=System.currentTimeMillis()+12000;while(System.currentTimeMillis()<end){shadowOf(Looper.getMainLooper()).idle();if(check.yes())return;Thread.sleep(15);}fail("Store did not reach expected state");}
}
