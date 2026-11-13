package dev.murmur.companion;

import android.app.Application;
import android.os.*;
import androidx.lifecycle.AndroidViewModel;
import dev.murmur.companion.core.*;
import org.json.*;
import java.util.*;
import java.util.concurrent.*;

/** Retains native lists, drafts and the live connection across Activity recreation. */
public class NativeStore extends AndroidViewModel {
    public static final class Agent {
        public final String hermes, profile, name, home;
        public final float hue;
        public Agent(String hermes,String profile,String name,String home){this(hermes,profile,name,home,278);}
        public Agent(String hermes,String profile,String name,String home,float hue){this.hermes=hermes;this.profile=profile;this.name=name;this.home=home;this.hue=hue;}
        public String key(){return hermes+":"+profile;}
    }
    public final List<Agent> agents=new ArrayList<>();
    public final Map<String,JSONArray> lists=new HashMap<>();
    public final Map<String,String> drafts=new HashMap<>();
    private final LinkedHashMap<String,NativeChat> cache=new LinkedHashMap<>(16,.75f,true);
    private final Map<String,JSONObject> active=new HashMap<>();
    private final ConcurrentHashMap<String,Long> socketVersions=new ConcurrentHashMap<>();
    private final java.util.concurrent.atomic.AtomicLong socketSequence=new java.util.concurrent.atomic.AtomicLong();
    private final Map<String,MurmurApi.Rpc> sockets=new HashMap<>();
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private final Handler main=new Handler(Looper.getMainLooper());
    private final List<JSONObject> heldRequests=new ArrayList<>(), heldEvents=new ArrayList<>();
    private String watchHome="",watchProfile="";
    private volatile MurmurApi api;
    private String credentials="", origin="";
    private OfflineCache disk;
    public boolean archived, moreChats, uploading;
    public final Map<String,List<String>> attachments=new HashMap<>();
    private final Map<String,List<String>> images=new HashMap<>();
    private final Runnable saveCache=()->persistCache();
    public boolean cacheEnabled(){return getApplication().getSharedPreferences("connection",0).getBoolean("offline_cache",true);}
    public void cacheEnabled(boolean enabled){getApplication().getSharedPreferences("connection",0).edit().putBoolean("offline_cache",enabled).apply();if(enabled)persistCache();else{main.removeCallbacks(saveCache);io.execute(()->OfflineCache.clearAll(getApplication()));}}
    private void persistSoon(){main.removeCallbacks(saveCache);if(cacheEnabled())main.postDelayed(saveCache,1000);}
    private void persistCache(){
        if(disk==null||!cacheEnabled())return;
        try{JSONObject value=new JSONObject();JSONArray catalog=new JSONArray(),chats=new JSONArray();JSONObject history=new JSONObject();
            for(Agent a:agents)catalog.put(new JSONObject().put("hermes",a.hermes).put("profile",a.profile).put("name",a.name).put("home",a.home).put("hue",a.hue));
            for(Map.Entry<String,JSONArray> e:lists.entrySet())history.put(e.getKey(),e.getValue());
            for(NativeChat c:cache.values())chats.put(OfflineCache.chat(c));
            value.put("agents",catalog).put("lists",history).put("chats",chats);OfflineCache destination=disk;int savedGeneration=generation;
            io.execute(()->{if(savedGeneration==generation&&cacheEnabled())destination.write(value);});
        }catch(Exception ignored){/* An unavailable read cache must not interrupt live chat. */}
    }
    private void restoreCache(JSONObject value){try{
        JSONArray catalog=value.optJSONArray("agents");if(catalog!=null)for(int i=0;i<catalog.length();i++){JSONObject a=catalog.getJSONObject(i);agents.add(new Agent(a.getString("hermes"),a.getString("profile"),a.getString("name"),a.getString("home"),(float)a.optDouble("hue",278)));}
        JSONObject history=value.optJSONObject("lists");if(history!=null)for(Iterator<String> keys=history.keys();keys.hasNext();){String key=keys.next();lists.put(key,history.getJSONArray(key));}
        JSONArray chats=value.optJSONArray("chats");if(chats!=null)for(int i=Math.max(0,chats.length()-10);i<chats.length();i++){NativeChat c=OfflineCache.restore(chats.getJSONObject(i));cache.put(c.key(),c);}
        if(!agents.isEmpty()){selected=agents.get(0);connection="Saved conversations · Reconnect to send";}
    }catch(Exception ignored){agents.clear();lists.clear();cache.clear();}}
    public void login(String password,java.util.function.Consumer<java.util.List<String>> success,java.util.function.Consumer<String> failure){int g=generation;io.execute(()->{try{if(api==null)throw new java.io.IOException("Connect to your server first");java.util.List<String> cookies=api.login(password);main.post(()->{if(g==generation&&!disposed)success.accept(cookies);});}catch(Exception ex){main.post(()->{if(g==generation&&!disposed)failure.accept(ex.getMessage());});}});}

    private boolean opening, suspended, visible=true;
    private volatile boolean disposed;
    private volatile int generation;
    private int navigation, reconnectAttempt;
    public Agent selected;
    public NativeChat chat;
    public String tab="chats", error="", connection="Connecting…";
    public boolean loading, sending, online, needsSignIn;
    public Runnable observer;
    private final Runnable retry=()->{if(visible&&!disposed&&api!=null&&!online&&!loading&&reconnectAttempt<3){reconnectAttempt++;reconnect();}};

    private final Runnable suspend=()->{suspended=true;io.execute(this::disconnect);};
    public NativeStore(Application application){super(application);}
    private void changed(){if(observer!=null)observer.run();persistSoon();}
    public void configure(Endpoint endpoint,String cookie){
        String next=cookie==null?"":cookie;
        if(!origin.isEmpty()&&origin.equals(endpoint.origin)&&credentials.equals(next))return;
        main.removeCallbacks(saveCache);disk=null;generation++;navigation++;int g=generation;main.removeCallbacks(retry);reconnectAttempt=0;loading=false;opening=false;sending=false;origin=endpoint.origin;credentials=next;
        agents.clear();lists.clear();cache.clear();active.clear();drafts.clear();attachments.clear();images.clear();selected=null;chat=null;error="";online=false;needsSignIn=false;archived=false;uploading=false;
        io.execute(()->{if(g!=generation||disposed)return;disconnect();if(api!=null)api.close();api=new MurmurApi(endpoint,next);JSONObject saved=new JSONObject();try{disk=new OfflineCache(getApplication(),endpoint.origin);if(cacheEnabled())saved=disk.read();}catch(Exception ignored){disk=null;}JSONObject snapshot=saved;main.post(()->{if(g==generation&&!disposed){restoreCache(snapshot);refresh();}});});
    }
    private void task(Work work){int g=generation,view=navigation;io.execute(()->{if(g!=generation||disposed)return;try{work.run(g);}catch(Exception ex){main.post(()->{if(g!=generation||disposed||view!=navigation)return;needsSignIn=ex instanceof MurmurApi.AuthRequired;error=ex.getMessage()==null?"Connection interrupted. Check Tailscale and try again.":ex.getMessage();loading=false;sending=false;opening=false;online=false;connection="Connection needs attention";changed();scheduleReconnect();});}});}
    private void commit(int g,Runnable work){main.post(()->{if(g!=generation||disposed)return;work.run();changed();});}
    interface Work{void run(int generation)throws Exception;}
    private MurmurApi.Rpc rpc(String home)throws Exception{
        MurmurApi.Rpc existing=sockets.get(home);if(existing!=null&&existing.isAlive())return existing;
        if(existing!=null)existing.close();
        int g=generation;long version=socketSequence.incrementAndGet();socketVersions.put(home,version);
        MurmurApi.Rpc next=api.connect(home,new MurmurApi.Listener(){
            @Override public void onEvent(JSONObject event){main.post(()->{
                if(g!=generation||disposed||!Objects.equals(socketVersions.get(home),version))return;
                if(chat!=null&&chat.hermes.equals(home)&&opening){heldEvents.add(event);return;}
                boolean updated=false;for(NativeChat cached:cache.values())if(cached.hermes.equals(home)&&!cached.runtimeId.isBlank()&&cached.runtimeId.equals(event.optString("session_id"))){cached.event(event);updated=true;}
                if(updated){changed();if(chat!=null&&chat.needsSync){chat.needsSync=false;reconnect();}}
            });}
            @Override public void onRequest(JSONObject frame){main.post(()->{
                if(g!=generation||disposed||!Objects.equals(socketVersions.get(home),version))return;
                JSONObject params=frame.optJSONObject("params");String sid=params==null?"":params.optString("session_id");
                if(opening){try{frame.put("_native_home",home);}catch(JSONException ignored){}heldRequests.add(frame);return;}
                if(chat!=null&&chat.hermes.equals(home)&&chat.runtimeId.equals(sid)){
                    if(Set.of("approval","clarify","secret","sudo","vault.unlock","vault.save_login","vault.code").contains(frame.optString("method"))){chat.request(frame);changed();}
                    else{chat.add("notice","This request needs the full Murmur workspace. Open Workspace from the conversation menu.",false);decline(home,frame,-32601,"This native client does not support this request. Use the full Murmur workspace.");changed();}
                }else decline(home,frame,4404,"Session not shown in this client");
            });}
            @Override public void onDisconnected(){main.post(()->{
                if(g!=generation||disposed||!Objects.equals(socketVersions.get(home),version))return;
                if(selected!=null&&!selected.hermes.equals(home))return;
                online=false;connection="Connection interrupted";
                error="Connection interrupted. Reconnecting to sync your conversation. Your messages will not be resent.";
                changed();scheduleReconnect();
            });}
        });
        sockets.put(home,next);
        try{next.call("client.capabilities",new JSONObject().put("server_requests",true));}catch(Exception ignored){/* Older servers may not expose capabilities. */}
        return next;
    }
    private void decline(String home,JSONObject request,int code,String message){task(g->rpc(home).decline(request.opt("id"),code,message));}
    public void refresh(){
        if(loading)return;needsSignIn=false;loading=true;error="";changed();
        task(g->{
            JSONArray homes=api.get("/murmur/connections").getJSONArray("connections");
            List<Agent> found=new ArrayList<>();List<String> warnings=new ArrayList<>();
            for(int i=0;i<Math.min(homes.length(),16);i++){
                JSONObject home=homes.getJSONObject(i);String id=home.getString("id"),name=home.optString("name","Home");
                if(!"connected".equals(home.optString("status","connected"))){warnings.add(name+" is unavailable");continue;}
                try{
                    JSONArray profiles=rpc(id).call("profiles.list",new JSONObject()).getJSONArray("profiles");
                    for(int j=0;j<profiles.length();j++){
                        JSONObject profile=profiles.getJSONObject(j),meta=profile.optJSONObject("ui_meta");
                        JSONObject murmur=meta==null?null:meta.optJSONObject("murmur"),bots=meta==null?null:meta.optJSONObject("hermes-bots");
                        String title=murmur==null?"":murmur.optString("display_name");if(title.isBlank()&&bots!=null)title=bots.optString("title");if(title.isBlank())title=profile.optString("display_name",profile.getString("name"));
                        found.add(new Agent(id,profile.getString("name"),title,name,AgentLook.hue(profile)));
                    }
                }catch(Exception ex){warnings.add(name+": "+ex.getMessage());}
            }
            if(found.isEmpty()&&!warnings.isEmpty())throw new java.io.IOException(String.join("\n",warnings));
            commit(g,()->{
                String previous=selected==null?"":selected.key();agents.clear();agents.addAll(found);
                selected=agents.stream().filter(a->a.key().equals(previous)).findFirst().orElse(agents.isEmpty()?null:agents.get(0));
                loading=false;online=!found.isEmpty();connection=online?"Connected to your Murmur":"No agents yet";
                error=String.join("\n",warnings);if(!watchHome.isEmpty()){openWatch(watchHome,watchProfile);}else if(selected!=null&&chat==null)loadChats();
            });
        });
    }
    public void select(Agent agent){archived=false;moreChats=false;navigation++;opening=false;heldRequests.clear();heldEvents.clear();selected=agent;chat=null;tab="chats";error="";loadChats();}
    public void loadChats(){loadChats(false);}
    public void loadChats(boolean append){
        Agent agent=selected;if(agent==null)return;boolean archivedPage=archived;String listKey=agent.key()+(archivedPage?":archived":"");int view=navigation;loading=true;error="";changed();
        task(g->{MurmurApi.Rpc channel=rpc(agent.hermes);JSONArray sessions;int offset=append&&lists.containsKey(listKey)?lists.get(listKey).length():0;
            try{sessions=api.get("/hermes/api/sessions?hermes="+Endpoint.encode(agent.hermes)+"&profile="+Endpoint.encode(agent.profile)+"&archived="+(archivedPage?"only":"exclude")+"&limit=100&offset="+offset).optJSONArray("sessions");}
            catch(MurmurApi.AuthRequired ex){throw ex;}
            catch(Exception ex){if(append||archivedPage)throw ex;sessions=channel.call("session.list",new JSONObject().put("profile",agent.profile).put("limit",100)).optJSONArray("sessions");}
            JSONArray page=sessions==null?new JSONArray():sessions;
            JSONArray live;try{live=channel.call("session.active_list",new JSONObject().put("profile",agent.profile)).optJSONArray("sessions");}catch(Exception ignored){live=null;}
            JSONArray running=live;
            commit(g,()->{JSONArray merged=append&&lists.containsKey(listKey)?lists.get(listKey):new JSONArray();Set<String> ids=new HashSet<>();for(int i=0;i<merged.length();i++)ids.add(merged.optJSONObject(i).optString("id"));for(int i=0;i<page.length();i++){JSONObject row=page.optJSONObject(i);if(row!=null&&!row.optBoolean("hidden")&&(!archivedPage||row.optBoolean("archived"))&&ids.add(row.optString("id")))merged.put(row);}lists.put(listKey,merged);moreChats=page.length()==100;
                active.keySet().removeIf(key->key.startsWith(agent.key()+":"));if(running!=null)for(int i=0;i<running.length();i++){JSONObject row=running.optJSONObject(i);if(row!=null&&!row.optString("session_key").isBlank())active.put(agent.key()+":"+row.optString("session_key"),row);}
                if(running!=null)for(NativeChat value:cache.values())if(value.hermes.equals(agent.hermes)&&value.profile.equals(agent.profile)&&value.busy){JSONObject row=active.get(value.key());if(row==null||"idle".equals(row.optString("status")))value.unknownActivity();}
                if(view!=navigation)return;loading=false;online=true;reconnectAttempt=0;connection="Connected to your Murmur";
                if(watchHome.equals(agent.hermes)&&watchProfile.equals(agent.profile)){
                    watchHome="";watchProfile="";
                    JSONArray rows=lists.get(agent.key());boolean found=false;
                    for(int i=0;i<rows.length();i++){JSONObject row=rows.optJSONObject(i);if(row!=null&&WatchActions.TITLE.equals(row.optString("title"))){found=true;open(row.optString("id"),WatchActions.TITLE);break;}}
                    if(!found)error="No Watch chat exists for this agent yet. Send your first message from the watch.";
                }});});
    }
    public JSONArray sessions(){return selected==null?null:lists.get(selected.key()+(archived?":archived":""));}
    public void toggleArchived(){navigation++;archived=!archived;moreChats=false;loadChats();}
    public void open(String id,String title){
        if(selected==null)return;int view=++navigation;
        Agent agent=selected;String key=agent.key()+":"+id;
        chat=cache.get(key);if(chat==null)chat=new NativeChat(agent.hermes,agent.profile,id);chat.title=title;opening=true;loading=true;error="";heldRequests.clear();heldEvents.clear();changed();
        NativeChat target=chat;
        task(g->{JSONObject snapshot=rpc(agent.hermes).call("session.resume",new JSONObject().put("profile",agent.profile).put("session_id",id).put("source","desktop").put("close_on_disconnect",false));
            commit(g,()->{if(view!=navigation||chat!=target)return;try{target.snapshot(snapshot);for(JSONObject request:heldRequests){JSONObject p=request.optJSONObject("params");if(agent.hermes.equals(request.optString("_native_home"))&&p!=null&&target.runtimeId.equals(p.optString("session_id"))){if(Set.of("approval","clarify","secret","sudo","vault.unlock","vault.save_login","vault.code").contains(request.optString("method")))target.request(request);else decline(agent.hermes,request,-32601,"Use the full Murmur workspace for this request.");}else decline(request.optString("_native_home"),request,4404,"Session not shown in this client");}for(JSONObject event:heldEvents)target.event(event);online=true;reconnectAttempt=0;cache.put(key,target);while(cache.size()>10)cache.remove(cache.keySet().iterator().next());}catch(Exception ex){error=ex.getMessage();target.ready=false;online=false;}heldRequests.clear();heldEvents.clear();opening=false;loading=false;});});
    }
    public void newChat(){
        if(selected==null||loading)return;Agent agent=selected;int view=++navigation;loading=true;error="";changed();
        task(g->{JSONObject state=rpc(agent.hermes).call("session.create",new JSONObject().put("profile",agent.profile).put("source","desktop").put("close_on_disconnect",false).put("idempotency_key",UUID.randomUUID().toString()));
            NativeChat created=new NativeChat(agent.hermes,agent.profile,state.getString("stored_session_id"));created.snapshot(state);
            commit(g,()->{if(view!=navigation)return;chat=created;cache.put(agent.key()+":"+created.storedId,created);while(cache.size()>10)cache.remove(cache.keySet().iterator().next());loading=false;online=true;});});
    }
    public void send(String text){
        NativeChat target=chat;if(target==null||!target.ready||sending||loading||uploading||target.busy||!target.requests.isEmpty()||!online||text.isBlank())return;
        List<String> files=new ArrayList<>(attachments.getOrDefault(target.key(),Collections.emptyList()));
        sending=true;error="";target.begin(NativeChat.Phase.THINKING,"Sending…");target.add("user",text,false);drafts.remove(target.key());changed();
        task(g->{try{JSONObject receipt=rpc(target.hermes).call("prompt.submit",new JSONObject().put("session_id",target.runtimeId).put("profile",target.profile).put("text",(files.isEmpty()?"":String.join("\n",files)+"\n\n")+text));
            commit(g,()->{attachments.remove(target.key());images.remove(target.key());sending=false;if(target.status.equals("Sending…"))target.status="queued".equals(receipt.optString("status"))?"Queued":"Thinking…";});
        }catch(Exception ex){commit(g,()->{sending=false;target.busy=false;online=false;error="Delivery is unconfirmed. Reconnect and read the chat before sending again. "+ex.getMessage();scheduleReconnect();});}});
    }
    public void stop(){NativeChat target=chat;if(target==null||!online)return;task(g->{rpc(target.hermes).call("session.interrupt",new JSONObject().put("session_id",target.runtimeId).put("profile",target.profile));commit(g,()->{if(target.busy)target.status="Stopping…";});});}
    public void answer(String id,JSONObject result){
        NativeChat target=chat;if(target==null||!target.requests.containsKey(id)||!online)return;
        JSONObject request=target.requests.get(id);JSONObject params=request.optJSONObject("params");
        task(g->{MurmurApi.Rpc channel=rpc(target.hermes);
            if(id.startsWith("legacy:"))channel.call("approval.respond",new JSONObject().put("profile",target.profile).put("session_id",target.runtimeId).put("request_id",params==null?"":params.optString("request_id")).put("choice",result.getString("choice")));
            else channel.respond(request.get("id"),result);
            commit(g,()->{target.requests.remove(id);target.begin(NativeChat.Phase.WORKING,"Working…");});});
    }
    public void rename(String title){NativeChat target=chat;if(target==null||title.isBlank())return;task(g->{rpc(target.hermes).call("session.title",new JSONObject().put("session_id",target.runtimeId).put("profile",target.profile).put("title",title));commit(g,()->target.title=title);});}
    public void models(java.util.function.Consumer<JSONObject> result){NativeChat target=chat;if(target==null)return;task(g->{JSONObject options=rpc(target.hermes).call("model.options",new JSONObject().put("session_id",target.runtimeId).put("profile",target.profile).put("include_unconfigured",false));commit(g,()->{if(chat==target)result.accept(options);});});}
    public void config(String key,String value,boolean confirmed,java.util.function.Consumer<JSONObject> result){NativeChat target=chat;if(target==null||target.busy||!online)return;task(g->{JSONObject answer=rpc(target.hermes).call("config.set",new JSONObject().put("session_id",target.runtimeId).put("profile",target.profile).put("scope","session").put("key",key).put("value",value).put("confirm_expensive_model",confirmed));commit(g,()->{if(chat==target)result.accept(answer);});});}
    public void describe(Agent agent,java.util.function.Consumer<JSONObject> result){task(g->{JSONObject description=rpc(agent.hermes).call("profiles.describe",new JSONObject().put("name",agent.profile));commit(g,()->result.accept(description));});}
    public void saveAgent(Agent agent,String description,String soul){task(g->{rpc(agent.hermes).call("profiles.configure",new JSONObject().put("name",agent.profile).put("description",description).put("soul",soul));commit(g,()->{error="";refresh();});});}
    public void createAgent(String home,String name,String description,String soul){task(g->{rpc(home).call("profiles.create",new JSONObject().put("name",name).put("description",description).put("soul",soul).put("mirror_credentials",true));commit(g,()->{tab="agents";refresh();});});}
    private String sessionPath(NativeChat c){return "/hermes/api/sessions/"+Endpoint.encode(c.storedId);}
    private String query(NativeChat c){return "?hermes="+Endpoint.encode(c.hermes)+"&profile="+Endpoint.encode(c.profile);}
    public void edit(String key,boolean value){NativeChat target=chat;if(target==null||!online||target.busy)return;task(g->{
        JSONObject change=new JSONObject().put("profile",target.profile).put(key,value);JSONObject snapshot=null;
        try{snapshot=api.get(sessionPath(target)+"/mutation-snapshot"+query(target));}catch(MurmurApi.AuthRequired ex){throw ex;}catch(Exception ignored){}
        if(snapshot!=null&&snapshot.has("runtime_revision"))change.put("expected_revision",snapshot.get("runtime_revision")).put("request_id",UUID.randomUUID().toString());
        api.http("PATCH",sessionPath(target)+"?hermes="+Endpoint.encode(target.hermes),change);
        commit(g,()->{if(chat==target){chat=null;navigation++;loadChats();}});
    });}
    public void delete(){NativeChat target=chat;if(target==null||!online||target.busy)return;task(g->{
        MurmurApi.Rpc channel=rpc(target.hermes);if(!target.runtimeId.isEmpty())channel.call("session.close",new JSONObject().put("session_id",target.runtimeId).put("profile",target.profile));
        channel.call("session.delete",new JSONObject().put("session_id",target.storedId).put("profile",target.profile));
        commit(g,()->{cache.remove(target.key());drafts.remove(target.key());attachments.remove(target.key());getApplication().getSharedPreferences("drafts",0).edit().remove(origin+":"+target.key()).apply();if(chat==target){chat=null;navigation++;loadChats();}});
    });}
    public void clearAttachments(){NativeChat target=chat;if(target==null||uploading||sending||target.busy||!online)return;List<String> paths=new ArrayList<>(images.getOrDefault(target.key(),Collections.emptyList()));uploading=true;changed();task(g->{try{for(String path:paths)rpc(target.hermes).call("image.detach",new JSONObject().put("session_id",target.runtimeId).put("profile",target.profile).put("path",path));commit(g,()->{uploading=false;attachments.remove(target.key());images.remove(target.key());});}catch(Exception ex){commit(g,()->{uploading=false;error="Couldn't clear attachments. "+ex.getMessage();});}});}
    public void attach(android.net.Uri uri){NativeChat target=chat;if(target==null||uploading||sending||target.busy||!online)return;uploading=true;error="";changed();task(g->{java.io.File copy=null;try{
        android.content.ContentResolver resolver=getApplication().getContentResolver();String name="file";
        try(android.database.Cursor cursor=resolver.query(uri,new String[]{android.provider.OpenableColumns.DISPLAY_NAME},null,null,null)){if(cursor!=null&&cursor.moveToFirst())name=cursor.getString(0);}
        name=name.replaceAll("[^a-zA-Z0-9._ -]","_");if(name.length()>100)name=name.substring(name.length()-100);if(name.isBlank()||name.equals(".")||name.equals(".."))name="file";
        String mime=resolver.getType(uri);boolean image=mime!=null&&mime.matches("image/(png|jpe?g|gif|webp|bmp)");
        copy=java.io.File.createTempFile("upload-",".tmp",getApplication().getCacheDir());
        try(java.io.InputStream in=resolver.openInputStream(uri);java.io.OutputStream out=new java.io.FileOutputStream(copy)){if(in==null)throw new java.io.IOException("Cannot open that file");byte[] bytes=new byte[32768];long size=0;int n;while((n=in.read(bytes))!=-1){size+=n;if(size>(image?25L:100L)*1024*1024)throw new java.io.IOException(image?"Choose a photo under 25 MB":"Choose a file under 100 MB");out.write(bytes,0,n);}}
        MurmurApi.Rpc channel=rpc(target.hermes);JSONArray profiles=channel.call("profiles.list",new JSONObject()).getJSONArray("profiles");String dir="";for(int i=0;i<profiles.length();i++){JSONObject row=profiles.getJSONObject(i);if(target.profile.equals(row.optString("name")))dir=row.optString("path");}if(dir.isBlank())throw new java.io.IOException("The server didn't provide an attachment folder for this agent");
        String path=api.upload(target.hermes,dir+"/attachments/"+UUID.randomUUID()+"-"+name,name,copy,mime).getString("path");
        JSONObject attached=channel.call(image?"image.attach":"file.attach",new JSONObject().put("session_id",target.runtimeId).put("profile",target.profile).put("path",path));
        String ref=attached.optBoolean("uploaded")?path:attached.optString("ref_path",path);
        String token=image?"[Image attached: "+name+"]":"@file:\""+ref.replace("\"", "\\\"")+"\"";
        commit(g,()->{uploading=false;attachments.computeIfAbsent(target.key(),ignored->new ArrayList<>()).add(token);if(image)images.computeIfAbsent(target.key(),ignored->new ArrayList<>()).add(path);});
    }catch(Exception ex){commit(g,()->{uploading=false;error="Attachment wasn't added. "+ex.getMessage();});}finally{if(copy!=null)copy.delete();}});}
    public void reconnect(){if(loading)return;if(chat!=null)open(chat.storedId,chat.title);else refresh();}
    public void back(){navigation++;opening=false;heldRequests.clear();heldEvents.clear();chat=null;error="";loadChats();}
    public void foreground(boolean visible){this.visible=visible;main.removeCallbacks(suspend);main.removeCallbacks(retry);if(!visible){persistCache();main.postDelayed(suspend,20000);}else if(suspended){suspended=false;reconnectAttempt=0;reconnect();}else if(!online&&api!=null)scheduleReconnect();}
    private void scheduleReconnect(){main.removeCallbacks(retry);if(visible&&!needsSignIn&&reconnectAttempt<3)main.postDelayed(retry,1000L<<reconnectAttempt);}
    public NativeChat cachedChat(Agent agent,String id){return agent==null?null:cache.get(agent.key()+":"+id);}
    public String listState(Agent agent,String id){
        NativeChat cached=cachedChat(agent,id);if(cached!=null)return cached.stateLabel();
        JSONObject row=agent==null?null:active.get(agent.key()+":"+id);
        if(row!=null)return switch(row.optString("status")){case "working","starting","resuming"->"Working";case "streaming"->"Replying";case "waiting"->"Needs you";default->"";};
        return "";
    }
    public NativeChat.Phase agentPhase(Agent agent){
        NativeChat.Phase result=NativeChat.Phase.IDLE;
        for(NativeChat value:cache.values())if(value.hermes.equals(agent.hermes)&&value.profile.equals(agent.profile)){
            if(value.phase()==NativeChat.Phase.WAITING)return value.phase();if(value.busy)result=value.phase();
        }return result;
    }
    public void openWatch(String home,String profile){
        if(home==null||profile==null)return;
        if(!home.matches("[a-zA-Z0-9_-]{1,128}")||!profile.matches("[a-z0-9][a-z0-9_-]{0,63}"))return;
        watchHome=home;watchProfile=profile;
        for(Agent agent:agents)if(agent.hermes.equals(home)&&agent.profile.equals(profile)){select(agent);return;}
        if(!loading&&api!=null){watchHome="";watchProfile="";error="That watch agent is not available. Reconnect or choose another agent.";changed();}
    }
    public void reset(){
        main.removeCallbacks(saveCache);disk=null;generation++;io.execute(()->OfflineCache.clearAll(getApplication()));navigation++;main.removeCallbacks(retry);reconnectAttempt=0;origin="";credentials="";watchHome="";watchProfile="";agents.clear();lists.clear();cache.clear();active.clear();drafts.clear();attachments.clear();images.clear();selected=null;chat=null;error="";online=false;loading=false;uploading=false;sending=false;opening=false;
        io.execute(()->{disconnect();if(api!=null){api.close();api=null;}});
    }
    private void disconnect(){socketVersions.clear();for(MurmurApi.Rpc rpc:sockets.values())rpc.close();sockets.clear();}
    @Override protected void onCleared(){disposed=true;generation++;main.removeCallbacksAndMessages(null);io.execute(()->{disconnect();if(api!=null)api.close();});io.shutdown();}
}
