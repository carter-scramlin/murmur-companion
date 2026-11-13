package dev.murmur.companion.core;

import org.json.*;
import java.util.*;

/** In-memory transcript. No web rendering or automatic prompt retry. */
public final class NativeChat {
    public static final class Row {
        public final String id, role;
        public String text;
        public boolean streaming;
        Row(String id, String role, String text, boolean streaming) { this.id=id; this.role=role; this.text=text; this.streaming=streaming; }
    }
    public final String hermes, profile, storedId;
    public String runtimeId="", title="New conversation", status="Ready", model="";
    public enum Phase { IDLE, THINKING, WORKING, REPLYING, WAITING, DONE, STOPPED, ERROR }
    private Phase phase=Phase.IDLE;
    public long workStartedAt;
    public String activity="", lastError="";
    public boolean busy, ready, needsSync;
    public Phase phase(){return requests.isEmpty()?phase:Phase.WAITING;}
    public void begin(Phase next,String label){if(!busy||workStartedAt==0)workStartedAt=System.currentTimeMillis();busy=true;phase=next;status=label;}
    public void unknownActivity(){requests.clear();busy=false;phase=Phase.IDLE;status="Open to sync";seal();}
    public String stateLabel(){return switch(phase()){case THINKING->"Thinking";case WORKING->"Working";case REPLYING->"Replying";case WAITING->"Needs you";case DONE->"Finished";case STOPPED->"Stopped";case ERROR->"Needs attention";default->"Ready";};}
    public String activityLabel(){return switch(phase()){case THINKING->"Putting a response together";case WORKING->activity.isBlank()?"Working on your request":activity;case REPLYING->"Writing a reply";case WAITING->"Waiting for your answer";case DONE->"Reply is ready";case STOPPED->"You can continue whenever you're ready";case ERROR->lastError.isBlank()?"Open the conversation to check":lastError;default->"Ready when you are";};}
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
            if((role.equals("user")||role.equals("assistant")||role.equals("tool"))&&!text.isBlank())add(role,role.equals("tool")?bounded(text):text,false);
        }
        JSONObject todo=state.optJSONObject("todo_state");if(todo!=null)plan(todo.optJSONArray("todos"));
        JSONObject inflight=state.optJSONObject("inflight");
        lastError="";activity="";
        if(inflight!=null&&!inflight.optString("assistant").isBlank())add("assistant",inflight.optString("assistant"),state.optBoolean("running")||inflight.optBoolean("streaming"));
        busy=state.optBoolean("running")||Set.of("working","starting","streaming","waiting").contains(state.optString("status"));
        phase=busy?(inflight!=null&&!inflight.optString("assistant").isBlank()?Phase.REPLYING:Phase.WORKING):rows.isEmpty()?Phase.IDLE:Phase.DONE;
        long started=state.optLong("turn_started_at",state.optLong("started_at",0));if(started>0&&started<100000000000L)started*=1000;
        workStartedAt=busy?(started>0?Math.min(started,System.currentTimeMillis()):System.currentTimeMillis()):0;
        if(inflight!=null&&!inflight.optString("error").isBlank()){lastError=inflight.optString("error");phase=Phase.ERROR;}
        JSONArray open=state.optJSONArray("open_requests");
        if(open!=null)for(int i=0;i<open.length();i++)request(open.getJSONObject(i));
        JSONObject approval=state.optJSONObject("pending_approval");
        if(approval!=null&&requests.isEmpty())requests.put("legacy:"+approval.optString("request_id"),new JSONObject().put("id","legacy:"+approval.optString("request_id")).put("method","approval").put("params",approval));
        if(!requests.isEmpty())busy=true;
        ready=true; status=requests.isEmpty()?(busy?"Working…":"Up to date"):"Needs you";
    }
    public String key(){return hermes+":"+profile+":"+storedId;}
    public void add(String role,String text,boolean streaming){ rows.add(new Row("native-"+(++nextRow),role,text,streaming));while(rows.size()>500)rows.remove(0); }
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
            case "message.start" -> { begin(Phase.THINKING,"Thinking…");activity="";lastError=""; }
            case "message.delta" -> {
                if(text.isEmpty())return;
                String t=p.optString("turn_id",turn);int s=p.optInt("segment",segment);
                if(!t.equals(turn)||s!=segment){if(!turn.isEmpty()||segment!=-1)seal();turn=t;segment=s;}
                Row row=tail();if(row==null||!row.streaming){add("assistant","",true);row=tail();}
                int offset=p.optInt("offset",row.text.length());
                if(offset>row.text.length()){needsSync=true;return;}
                int overlap=row.text.length()-offset;
                if(overlap<text.length())row.text+=text.substring(Math.max(0,overlap));
                begin(Phase.REPLYING,"Replying…");
            }
            case "message.interim" -> { Row row=tail();if(row!=null&&row.streaming){if(!text.isEmpty())row.text=text;row.streaming=false;}else if(!text.isEmpty()&&!p.optBoolean("already_streamed"))add("assistant",text,false); }
            case "message.complete" -> {
                Row row=tail();if(row!=null&&row.streaming){if(!text.isBlank())row.text=text;row.streaming=false;}else if(!text.isBlank()&&(row==null||!row.role.equals("assistant")||!row.text.equals(text)))add("assistant",text,false);
                busy=false;activity="";phase="interrupted".equals(p.optString("status"))?Phase.STOPPED:"error".equals(p.optString("status"))?Phase.ERROR:Phase.DONE;lastError=p.optString("error","");status=phase==Phase.STOPPED?"Stopped":phase==Phase.ERROR?"Needs attention":"Up to date";
                if("error".equals(p.optString("status")))add("notice",p.optString("error","The agent could not finish that reply."),false);
            }
            case "tool.start" -> { seal();activity=toolLabel(p);begin(Phase.WORKING,activity); }
            case "tool.complete" -> {String summary=p.optString("summary",p.optString("result_text",""));if(!summary.isBlank())add("tool",p.optString("name","Tool")+"\n"+bounded(summary),false);if(p.optJSONArray("todos")!=null)plan(p.optJSONArray("todos"));if(busy){phase=Phase.WORKING;activity="Putting the results together";status=requests.isEmpty()?"Working…":"Needs you";}}
            case "tool.progress" -> {String message=p.optString("message");if(!message.isBlank()){activity=clip(message);if(busy)status=activity;}}
            case "todo.updated" -> plan(p.optJSONArray("todos"));
            case "subagent.start", "subagent.progress", "subagent.tool", "subagent.thinking" -> {activity="Helper: "+clip(p.optString("goal",p.optString("text","Working")));if(busy)status=activity;}
            case "subagent.complete" -> {String summary=p.optString("summary",p.optString("text",""));if(!summary.isBlank())add("tool","Helper finished\n"+bounded(summary),false);}
            case "notification.show", "agent.print" -> {if(!text.isBlank())add("notice",bounded(text),false);}
            case "session.title" -> title=p.optString("title",title);
            case "session.info" -> {model=p.optString("model",model);title=p.optString("title",title);}
            case "request.cancel", "approval.cancelled" -> { String requestId=p.optString("id",p.optString("request_id"));requests.remove(requestId);requests.remove("legacy:"+requestId); status=requests.isEmpty()?(busy?"Working…":"Up to date"):"Needs you"; }
            case "error" -> { add("notice",p.optString("message","Murmur reported an error."),false);busy=false;phase=Phase.ERROR;lastError=p.optString("message","Murmur reported an error.");status="Needs attention"; }
            default -> { }
        }
        if(!requests.isEmpty())status="Needs you";
    }
    private static String bounded(String text){return text.length()>4000?text.substring(0,4000)+"\n… Open Workspace for the full result.":text;}
    private void plan(JSONArray todos){if(todos==null)return;StringBuilder text=new StringBuilder();for(int i=0;i<Math.min(todos.length(),50);i++){JSONObject task=todos.optJSONObject(i);if(task==null)continue;String content=task.optString("content",task.optString("text",task.optString("title")));if(content.isBlank())continue;text.append(task.optString("status").equals("completed")?"✓ ":task.optString("status").equals("in_progress")?"◉ ":"○ ").append(content).append('\n');}if(text.length()==0)return;for(Row row:rows)if(row.role.equals("plan")){row.text=bounded(text.toString());return;}add("plan",bounded(text.toString()),false);}
    private String toolLabel(JSONObject payload){
        JSONArray labels=payload.optJSONArray("labels");if(labels!=null&&labels.length()>0){JSONObject label=labels.optJSONObject(0);if(label!=null&&!label.optString("text").isBlank())return clip(label.optString("text"));}
        String name=payload.optString("name","").toLowerCase(Locale.ROOT);
        if(name.contains("search"))return "Looking things up";
        if(name.contains("browse")||name.contains("fetch")||name.contains("web"))return "Reading a page";
        if(name.contains("write")||name.contains("edit"))return "Updating a file";
        if(name.contains("read")||name.contains("file"))return "Checking files";
        if(name.contains("exec")||name.contains("terminal")||name.contains("shell"))return "Running a command";
        if(name.contains("memory"))return "Checking memory";
        return "Using a tool";
    }
    private static String clip(String value){return value.length()>120?value.substring(0,120)+"…":value;}
    private Row tail(){return rows.isEmpty()?null:rows.get(rows.size()-1);}
    private void seal(){Row row=tail();if(row!=null)row.streaming=false;}
}
