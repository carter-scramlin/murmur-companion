package dev.murmur.companion.core;

import org.json.*;
import java.util.*;

/** In-memory transcript. No web rendering, disk transcript cache or automatic prompt retry. */
public final class NativeChat {
    public static final class Row {
        public final String id, role;
        public String text;
        public boolean streaming;
        Row(String id, String role, String text, boolean streaming) { this.id=id; this.role=role; this.text=text; this.streaming=streaming; }
    }
    public final String hermes, profile, storedId;
    public String runtimeId="", title="New conversation", status="Ready", model="";
    public boolean busy, ready, needsSync;
    public final List<Row> rows = new ArrayList<>();
    public final LinkedHashMap<String,JSONObject> requests = new LinkedHashMap<>();
    private long sequence=-1;
    private String epoch="", turn="";
    private int segment=-1;
    private long nextRow;
    public NativeChat(String hermes, String profile, String storedId) { this.hermes=hermes; this.profile=profile; this.storedId=storedId; }
    public void snapshot(JSONObject state) throws Exception {
        runtimeId=state.getString("session_id"); rows.clear(); requests.clear(); sequence=-1; segment=-1; turn=""; epoch=""; needsSync=false;
        JSONObject info=state.optJSONObject("info");
        if(info!=null){ title=info.optString("title",title); model=info.optString("model",""); }
        JSONArray history=state.optJSONArray("display_messages"); if(history==null)history=state.optJSONArray("messages");
        if(history!=null)for(int i=Math.max(0,history.length()-500);i<history.length();i++){
            JSONObject row=history.getJSONObject(i); String role=row.optString("role");
            if("hidden".equals(row.optString("display_kind")))continue;
            String text=row.optString("display_content",row.optString("text",row.opt("content") instanceof String ? row.optString("content") : ""));
            if((role.equals("user")||role.equals("assistant"))&&!text.isBlank())add(role,text,false);
        }
        JSONObject inflight=state.optJSONObject("inflight");
        if(inflight!=null&&!inflight.optString("assistant").isBlank())add("assistant",inflight.optString("assistant"),true);
        busy=state.optBoolean("running")||Set.of("working","starting","streaming","waiting").contains(state.optString("status"));
        JSONArray open=state.optJSONArray("open_requests");
        if(open!=null)for(int i=0;i<open.length();i++)request(open.getJSONObject(i));
        JSONObject approval=state.optJSONObject("pending_approval");
        if(approval!=null&&requests.isEmpty())requests.put("legacy:"+approval.optString("request_id"),new JSONObject().put("id","legacy:"+approval.optString("request_id")).put("method","approval").put("params",approval));
        ready=true; status=requests.isEmpty()?(busy?"Working…":"Up to date"):"Needs you";
    }
    public String key(){return hermes+":"+profile+":"+storedId;}
    public void add(String role,String text,boolean streaming){ rows.add(new Row("native-"+(++nextRow),role,text,streaming)); }
    public void request(JSONObject frame){ requests.put(String.valueOf(frame.opt("id")),frame); busy=true; status="Needs you"; }
    public void event(JSONObject event) {
        if(!runtimeId.equals(event.optString("session_id")))return;
        String incomingEpoch=event.optString("replay_epoch",epoch);
        if(!incomingEpoch.equals(epoch)){sequence=-1;epoch=incomingEpoch;}
        if(event.has("seq")){
            long incoming=event.optLong("seq"); if(incoming<=sequence)return;
            if(sequence>=0&&incoming>sequence+1)needsSync=true;
            sequence=incoming;
        }
        JSONObject p=event.optJSONObject("payload");if(p==null)p=new JSONObject();
        String text=p.opt("text") instanceof String?p.optString("text"):"";
        switch(event.optString("type")){
            case "message.start" -> { busy=true;status="Thinking…"; }
            case "message.delta" -> {
                if(text.isEmpty())return;
                String t=p.optString("turn_id",turn);int s=p.optInt("segment",segment);
                if(!t.equals(turn)||s!=segment){if(!turn.isEmpty()||segment!=-1)seal();turn=t;segment=s;}
                Row row=tail();if(row==null||!row.streaming){add("assistant","",true);row=tail();}
                int offset=p.optInt("offset",row.text.length());
                if(offset>row.text.length()){needsSync=true;return;}
                int overlap=row.text.length()-offset;
                if(overlap<text.length())row.text+=text.substring(Math.max(0,overlap));
                busy=true;status="Replying…";
            }
            case "message.interim" -> { Row row=tail();if(row!=null&&row.streaming){if(!text.isEmpty())row.text=text;row.streaming=false;}else if(!text.isEmpty()&&!p.optBoolean("already_streamed"))add("assistant",text,false); }
            case "message.complete" -> {
                Row row=tail();if(row!=null&&row.streaming){if(!text.isBlank())row.text=text;row.streaming=false;}else if(!text.isBlank()&&(row==null||!row.role.equals("assistant")||!row.text.equals(text)))add("assistant",text,false);
                busy=false;status="interrupted".equals(p.optString("status"))?"Stopped":"Up to date";
                if("error".equals(p.optString("status")))add("notice",p.optString("error","The agent could not finish that reply."),false);
            }
            case "tool.start" -> { seal();busy=true;status="Using "+p.optString("name","a tool").replace('_',' ')+"…"; }
            case "tool.complete" -> {status=requests.isEmpty()?"Working…":"Needs you";}
            case "session.title" -> title=p.optString("title",title);
            case "session.info" -> {model=p.optString("model",model);title=p.optString("title",title);}
            case "request.cancel", "approval.cancelled" -> { String requestId=p.optString("id",p.optString("request_id"));requests.remove(requestId);requests.remove("legacy:"+requestId); status=requests.isEmpty()?(busy?"Working…":"Up to date"):"Needs you"; }
            case "error" -> { add("notice",p.optString("message","Murmur reported an error."),false);busy=false;status="Needs attention"; }
            default -> { }
        }
        if(!requests.isEmpty())status="Needs you";
    }
    private Row tail(){return rows.isEmpty()?null:rows.get(rows.size()-1);}
    private void seal(){Row row=tail();if(row!=null)row.streaming=false;}
}
