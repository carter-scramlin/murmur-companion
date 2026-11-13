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
        public Agent(String hermes,String profile,String name,String home){this.hermes=hermes;this.profile=profile;this.name=name;this.home=home;}
        public String key(){return hermes+":"+profile;}
    }
    public final List<Agent> agents=new ArrayList<>();
    public final Map<String,JSONArray> lists=new HashMap<>();
    public final Map<String,String> drafts=new HashMap<>();
    private final LinkedHashMap<String,NativeChat> cache=new LinkedHashMap<>();
    private final Map<String,MurmurApi.Rpc> sockets=new HashMap<>();
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private final Handler main=new Handler(Looper.getMainLooper());
    private final List<JSONObject> heldRequests=new ArrayList<>(), heldEvents=new ArrayList<>();
    private String watchHome="",watchProfile="";
    private MurmurApi api;
    private String credentials="", origin="";
    private boolean opening, disposed, suspended;
    private int generation;
    public Agent selected;
    public NativeChat chat;
    public String tab="chats", error="", connection="Connecting…";
    public boolean loading, sending, online;
    public Runnable observer;
    private final Runnable suspend=()->{suspended=true;io.execute(this::disconnect);};
    public NativeStore(Application application){super(application);}
    private void changed(){if(observer!=null)observer.run();}
    public void configure(Endpoint endpoint,String cookie){
        String next=cookie==null?"":cookie;
        if(!origin.isEmpty()&&origin.equals(endpoint.origin)&&credentials.equals(next))return;
        generation++;int g=generation;origin=endpoint.origin;credentials=next;
        agents.clear();lists.clear();cache.clear();drafts.clear();selected=null;chat=null;error="";online=false;
        io.execute(()->{disconnect();if(api!=null)api.close();api=new MurmurApi(endpoint,next);main.post(()->{if(g==generation)refresh();});});
    }
    private void task(Work work){int g=generation;io.execute(()->{try{work.run(g);}catch(Exception ex){main.post(()->{if(g!=generation||disposed)return;error=ex.getMessage()==null?"Connection interrupted. Check Tailscale and try again.":ex.getMessage();loading=false;sending=false;opening=false;online=false;connection="Connection needs attention";changed();});}});}
    private void commit(int g,Runnable work){main.post(()->{if(g!=generation||disposed)return;work.run();changed();});}
    interface Work{void run(int generation)throws Exception;}
    private MurmurApi.Rpc rpc(String home)throws Exception{
        MurmurApi.Rpc existing=sockets.get(home);if(existing!=null&&existing.isAlive())return existing;
        if(existing!=null)existing.close();
        int g=generation;
        MurmurApi.Rpc next=api.connect(home,new MurmurApi.Listener(){
            @Override public void onEvent(JSONObject event){main.post(()->{if(g!=generation||disposed)return;if(chat!=null&&chat.hermes.equals(home)&&opening){heldEvents.add(event);return;}if(chat!=null&&chat.hermes.equals(home)&&!opening){chat.event(event);changed();if(chat.needsSync){chat.needsSync=false;reconnect();}}});}
            @Override public void onRequest(JSONObject frame){main.post(()->{
                if(g!=generation||disposed)return;
                JSONObject params=frame.optJSONObject("params");String sid=params==null?"":params.optString("session_id");
                if(opening){try{frame.put("_native_home",home);}catch(JSONException ignored){}heldRequests.add(frame);return;}
                if(chat!=null&&chat.hermes.equals(home)&&chat.runtimeId.equals(sid)){
                    if(Set.of("approval","clarify").contains(frame.optString("method"))){chat.request(frame);changed();}
                    else{chat.add("notice","This request needs the full Murmur workspace. Open Workspace from the conversation menu.",false);decline(home,frame,-32601,"This native client does not support this request. Use the full Murmur workspace.");changed();}
                }else decline(home,frame,4404,"Session not shown in this client");
            });}
            @Override public void onDisconnected(){main.post(()->{if(g!=generation||disposed)return;online=false;connection="Disconnected · Tap to reconnect";if(chat!=null&&chat.hermes.equals(home))error="Connection interrupted. Tap Reconnect to sync the conversation. Messages are never resent automatically.";changed();});}
        });
        sockets.put(home,next);
        try{next.call("client.capabilities",new JSONObject().put("server_requests",true));}catch(Exception ignored){/* Older servers may not expose capabilities. */}
        return next;
    }
    private void decline(String home,JSONObject request,int code,String message){task(g->rpc(home).decline(request.opt("id"),code,message));}
    public void refresh(){
        if(loading)return;loading=true;error="";changed();
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
                        found.add(new Agent(id,profile.getString("name"),title,name));
                    }
                }catch(Exception ex){warnings.add(name+": "+ex.getMessage());}
            }
            commit(g,()->{
                String previous=selected==null?"":selected.key();agents.clear();agents.addAll(found);
                selected=agents.stream().filter(a->a.key().equals(previous)).findFirst().orElse(agents.isEmpty()?null:agents.get(0));
                loading=false;online=!found.isEmpty();connection=online?"Connected to your Murmur":"No agents yet";
                error=String.join("\n",warnings);if(!watchHome.isEmpty()){openWatch(watchHome,watchProfile);}else if(selected!=null&&chat==null)loadChats();
            });
        });
    }
    public void select(Agent agent){selected=agent;chat=null;tab="chats";error="";loadChats();}
    public void loadChats(){
        Agent agent=selected;if(agent==null)return;loading=true;error="";changed();
        task(g->{JSONArray sessions=rpc(agent.hermes).call("session.list",new JSONObject().put("profile",agent.profile).put("limit",100)).optJSONArray("sessions");
            commit(g,()->{lists.put(agent.key(),sessions==null?new JSONArray():sessions);loading=false;online=true;connection="Connected to your Murmur";
                if(watchHome.equals(agent.hermes)&&watchProfile.equals(agent.profile)){
                    watchHome="";watchProfile="";
                    JSONArray rows=lists.get(agent.key());boolean found=false;
                    for(int i=0;i<rows.length();i++){JSONObject row=rows.optJSONObject(i);if(row!=null&&WatchActions.TITLE.equals(row.optString("title"))){found=true;open(row.optString("id"),WatchActions.TITLE);break;}}
                    if(!found)error="No Watch chat exists for this agent yet. Send your first message from the watch.";
                }});});
    }
    public void open(String id,String title){
        if(selected==null||opening)return;
        Agent agent=selected;String key=agent.key()+":"+id;
        chat=cache.get(key);if(chat==null)chat=new NativeChat(agent.hermes,agent.profile,id);chat.title=title;opening=true;loading=true;error="";heldRequests.clear();heldEvents.clear();changed();
        NativeChat target=chat;
        task(g->{JSONObject snapshot=rpc(agent.hermes).call("session.resume",new JSONObject().put("profile",agent.profile).put("session_id",id).put("source","desktop").put("close_on_disconnect",false));
            commit(g,()->{if(chat!=target){opening=false;loading=false;return;}try{target.snapshot(snapshot);for(JSONObject request:heldRequests){JSONObject p=request.optJSONObject("params");if(agent.hermes.equals(request.optString("_native_home"))&&p!=null&&target.runtimeId.equals(p.optString("session_id"))){if(Set.of("approval","clarify").contains(request.optString("method")))target.request(request);else decline(agent.hermes,request,-32601,"Use the full Murmur workspace for this request.");}else decline(request.optString("_native_home"),request,4404,"Session not shown in this client");}for(JSONObject event:heldEvents)target.event(event);online=true;cache.put(key,target);while(cache.size()>10)cache.remove(cache.keySet().iterator().next());}catch(Exception ex){error=ex.getMessage();target.ready=false;online=false;}heldRequests.clear();heldEvents.clear();opening=false;loading=false;});});
    }
    public void newChat(){
        if(selected==null||loading)return;Agent agent=selected;loading=true;error="";changed();
        task(g->{JSONObject state=rpc(agent.hermes).call("session.create",new JSONObject().put("profile",agent.profile).put("source","desktop").put("close_on_disconnect",false).put("idempotency_key",UUID.randomUUID().toString()));
            NativeChat created=new NativeChat(agent.hermes,agent.profile,state.getString("stored_session_id"));created.snapshot(state);
            commit(g,()->{chat=created;cache.put(agent.key()+":"+created.storedId,created);while(cache.size()>10)cache.remove(cache.keySet().iterator().next());loading=false;online=true;});});
    }
    public void send(String text){
        NativeChat target=chat;if(target==null||!target.ready||sending||loading||target.busy||!target.requests.isEmpty()||!online||text.isBlank())return;
        sending=true;error="";target.busy=true;target.status="Sending…";target.add("user",text,false);drafts.remove(target.key());changed();
        task(g->{try{JSONObject receipt=rpc(target.hermes).call("prompt.submit",new JSONObject().put("session_id",target.runtimeId).put("profile",target.profile).put("text",text));
            commit(g,()->{sending=false;if(target.status.equals("Sending…"))target.status="queued".equals(receipt.optString("status"))?"Queued":"Thinking…";});
        }catch(Exception ex){commit(g,()->{sending=false;target.busy=false;online=false;error="Delivery is unconfirmed. Reconnect and read the chat before sending again. "+ex.getMessage();});}});
    }
    public void stop(){NativeChat target=chat;if(target==null||!online)return;task(g->{rpc(target.hermes).call("session.interrupt",new JSONObject().put("session_id",target.runtimeId).put("profile",target.profile));commit(g,()->target.status="Stopping…");});}
    public void answer(String id,JSONObject result){
        NativeChat target=chat;if(target==null||!target.requests.containsKey(id)||!online)return;
        JSONObject request=target.requests.get(id);JSONObject params=request.optJSONObject("params");
        task(g->{MurmurApi.Rpc channel=rpc(target.hermes);
            if(id.startsWith("legacy:"))channel.call("approval.respond",new JSONObject().put("profile",target.profile).put("session_id",target.runtimeId).put("request_id",params==null?"":params.optString("request_id")).put("choice",result.getString("choice")));
            else channel.respond(request.get("id"),result);
            commit(g,()->{target.requests.remove(id);target.status="Working…";});});
    }
    public void rename(String title){NativeChat target=chat;if(target==null||title.isBlank())return;task(g->{rpc(target.hermes).call("session.title",new JSONObject().put("session_id",target.runtimeId).put("profile",target.profile).put("title",title));commit(g,()->target.title=title);});}
    public void reconnect(){if(loading)return;if(chat!=null)open(chat.storedId,chat.title);else refresh();}
    public void back(){if(opening)return;chat=null;error="";loadChats();}
    public void foreground(boolean visible){main.removeCallbacks(suspend);if(!visible)main.postDelayed(suspend,20000);else if(suspended){suspended=false;reconnect();}}
    public void openWatch(String home,String profile){
        if(home==null||profile==null)return;
        if(!home.matches("[a-zA-Z0-9_-]{1,128}")||!profile.matches("[a-z0-9][a-z0-9_-]{0,63}"))return;
        watchHome=home;watchProfile=profile;
        for(Agent agent:agents)if(agent.hermes.equals(home)&&agent.profile.equals(profile)){select(agent);return;}
    }
    public void reset(){
        generation++;origin="";credentials="";watchHome="";watchProfile="";agents.clear();lists.clear();cache.clear();drafts.clear();selected=null;chat=null;error="";online=false;loading=false;sending=false;opening=false;
        io.execute(()->{disconnect();if(api!=null){api.close();api=null;}});
    }
    private void disconnect(){for(MurmurApi.Rpc rpc:sockets.values())rpc.close();sockets.clear();}
    @Override protected void onCleared(){disposed=true;generation++;main.removeCallbacksAndMessages(null);io.execute(()->{disconnect();if(api!=null)api.close();});io.shutdown();}
}
