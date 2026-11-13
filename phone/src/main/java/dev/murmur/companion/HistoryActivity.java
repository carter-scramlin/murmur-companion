package dev.murmur.companion;

import android.app.Activity;
import android.os.*;
import android.webkit.CookieManager;
import android.view.*;
import android.widget.*;
import androidx.recyclerview.widget.*;
import dev.murmur.companion.core.*;
import org.json.*;
import java.util.*;
import java.util.concurrent.*;
import static dev.murmur.companion.NativeViews.*;

/** Pages through stored history without holding the entire conversation in memory. */
public final class HistoryActivity extends Activity {
    private final ExecutorService io=Executors.newSingleThreadExecutor();private final Handler main=new Handler(Looper.getMainLooper());
    private MurmurApi api;private int offset,generation;private Button before,after;private TextView status;private Rows rows;
    private String home,profile,session;
    @Override public void onCreate(Bundle state){palette(this);setTheme(dark?android.R.style.Theme_Material_NoActionBar:R.style.AppTheme);super.onCreate(state);offset=state==null?0:state.getInt("offset");
        home=getIntent().getStringExtra("home");profile=getIntent().getStringExtra("profile");session=getIntent().getStringExtra("session");
        if(home==null||profile==null||session==null){finish();return;}
        LinearLayout page=column(this);page.setBackgroundColor(PAPER);page.setFitsSystemWindows(true);pad(page,16,12,12);setContentView(page);
        LinearLayout title=row(this);title.addView(icon(this,"back","Back",this::finish));title.addView(text(this,"Conversation history",21,INK,true));page.addView(title);
        status=text(this,"",12,MUTED,false);page.addView(status);RecyclerView list=new RecyclerView(this);list.setLayoutManager(new LinearLayoutManager(this));rows=new Rows();list.setAdapter(rows);page.addView(list,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout controls=row(this);before=button(this,"Previous page",false,()->{offset=Math.max(0,offset-100);load();});after=button(this,"Next page",false,()->{offset+=100;load();});controls.addView(before,new LinearLayout.LayoutParams(0,-2,1));controls.addView(after,new LinearLayout.LayoutParams(0,-2,1));page.addView(controls);
        try{android.content.SharedPreferences prefs=getSharedPreferences("connection",0);Endpoint endpoint=new Endpoint(prefs.getString("origin",""),prefs.getBoolean("http",false));api=new MurmurApi(endpoint,CookieManager.getInstance().getCookie(endpoint.origin));load();}catch(Exception ex){status.setText("Reconnect to Murmur before reading older history.");before.setEnabled(false);after.setEnabled(false);}
    }
    private void load(){int current=++generation;before.setEnabled(false);after.setEnabled(false);status.setText("Reading messages "+(offset+1)+"–"+(offset+100)+"…");String path="/hermes/api/sessions/"+Endpoint.encode(session)+"/messages?hermes="+Endpoint.encode(home)+"&profile="+Endpoint.encode(profile)+"&order=oldest&limit=100&offset="+offset;
        io.execute(()->{try{JSONObject result=api.get(path);JSONArray history=result.optJSONArray("messages");if(history==null)throw new java.io.IOException("Your server did not provide paged messages. Use Workspace for this server.");List<String> page=new ArrayList<>();for(int i=0;i<history.length();i++){JSONObject message=history.optJSONObject(i);if(message==null||"hidden".equals(message.optString("display_kind")))continue;String role=message.optString("role");if(!Set.of("assistant","user","tool").contains(role))continue;String text=message.optString("display_content",message.optString("text",message.optString("content","")));if(!text.isBlank())page.add((role.equals("user")?"You":role.equals("assistant")?"Agent":"Tool")+"\n\n"+text);}boolean more=history.length()==100;main.post(()->{if(current!=generation||isFinishing())return;rows.values=page;rows.notifyDataSetChanged();before.setEnabled(offset>0);after.setEnabled(more);status.setText(page.isEmpty()?"No more messages":"Messages "+(offset+1)+"–"+(offset+history.length())+" · Oldest first");});}
        catch(Exception ex){main.post(()->{if(current!=generation||isFinishing())return;status.setText(ex.getMessage());before.setEnabled(offset>0);after.setText("Retry page");after.setEnabled(true);after.setOnClickListener(v->{after.setText("Next page");after.setOnClickListener(w->{offset+=100;load();});load();});});}});
    }
    private final class Rows extends RecyclerView.Adapter<Line>{List<String> values=new ArrayList<>();@Override public Line onCreateViewHolder(ViewGroup parent,int type){TextView line=text(HistoryActivity.this,"",15,INK,false);pad(line,12,18,18);line.setTextIsSelectable(true);line.setLayoutParams(new RecyclerView.LayoutParams(-1,-2));return new Line(line);}@Override public void onBindViewHolder(Line line,int position){((TextView)line.itemView).setText(markdown(values.get(position)));}@Override public int getItemCount(){return values.size();}}
    private static final class Line extends RecyclerView.ViewHolder{Line(View view){super(view);}}
    @Override protected void onSaveInstanceState(Bundle out){out.putInt("offset",offset);super.onSaveInstanceState(out);}
    @Override protected void onDestroy(){generation++;if(api!=null)api.close();io.shutdownNow();main.removeCallbacksAndMessages(null);super.onDestroy();}
}
