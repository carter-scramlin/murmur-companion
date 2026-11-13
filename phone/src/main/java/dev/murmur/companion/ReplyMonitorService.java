package dev.murmur.companion;

import android.app.*;
import android.content.*;
import android.os.*;
import android.webkit.CookieManager;
import dev.murmur.companion.core.*;
import org.json.*;
import java.util.concurrent.*;

/** User-started, visible, bounded read-only monitoring of one reply. Never submits or approves. */
public final class ReplyMonitorService extends Service {
    private final ScheduledExecutorService worker=Executors.newSingleThreadScheduledExecutor();
    private final Handler main=new Handler(Looper.getMainLooper());
    private volatile MurmurApi api;
    private MurmurApi.Rpc socket;private ScheduledFuture<?> polling;
    private String home,profile,session,name;
    private long deadline;private boolean seenWorking;private int failures;private volatile int generation;
    private NotificationManager notifications;
    private static final int ACTIVE=42,RESULT=43;
    @Override public IBinder onBind(Intent intent){return null;}
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        if(intent==null){stopSelf();return START_NOT_STICKY;}
        if("stop".equals(intent.getAction())){stopSelf();return START_NOT_STICKY;}
        if(home!=null){generation++;if(polling!=null)polling.cancel(true);if(api!=null)api.close();api=null;socket=null;home=null;}
        home=intent.getStringExtra("home");profile=intent.getStringExtra("profile");session=intent.getStringExtra("session");name=intent.getStringExtra("name");
        if(home==null||profile==null||session==null||!home.matches("[a-zA-Z0-9_-]{1,128}")||!profile.matches("[a-z0-9][a-z0-9_-]{0,63}")||session.length()>256){stopSelf();return START_NOT_STICKY;}
        notifications=getSystemService(NotificationManager.class);notifications.createNotificationChannel(new NotificationChannel("replies","Agent replies",NotificationManager.IMPORTANCE_DEFAULT));
        deadline=SystemClock.elapsedRealtime()+30*60*1000L;seenWorking=false;failures=0;
        startForeground(ACTIVE,notification("Following your reply","We'll let you know when your agent finishes or needs you.",true));
        try{android.content.SharedPreferences prefs=getSharedPreferences("connection",0);Endpoint endpoint=new Endpoint(prefs.getString("origin",""),prefs.getBoolean("http",false));api=new MurmurApi(endpoint,CookieManager.getInstance().getCookie(endpoint.origin));}
        catch(Exception ex){finish("Connection needs attention","Open Murmur Beta to sign in or check your connection.");return START_NOT_STICKY;}
        int current=++generation;polling=worker.scheduleWithFixedDelay(()->poll(current),0,20,TimeUnit.SECONDS);return START_NOT_STICKY;
    }
    private Notification notification(String title,String detail,boolean ongoing){
        Intent open=new Intent(this,MainActivity.class).putExtra("watch_hermes",home).putExtra("watch_profile",profile).putExtra("open_session",session).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP|Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent content=PendingIntent.getActivity(this,42,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder=new Notification.Builder(this,"replies").setSmallIcon(R.drawable.ic_launcher_foreground).setContentTitle(title).setContentText(detail).setVisibility(Notification.VISIBILITY_PRIVATE).setContentIntent(content).setOngoing(ongoing).setAutoCancel(!ongoing).setOnlyAlertOnce(ongoing);
        if(ongoing)builder.addAction(new Notification.Action.Builder(null,"Stop following",PendingIntent.getService(this,42,new Intent(this,ReplyMonitorService.class).setAction("stop"),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE)).build());
        return builder.build();
    }
    private void poll(int current){
        if(current!=generation)return;
        if(SystemClock.elapsedRealtime()>deadline){main.post(()->finish("Following paused","The 30-minute follow window ended. Open Murmur to check your reply."));return;}
        try{if(socket==null||!socket.isAlive())socket=api.connect(home);
            JSONArray live=socket.call("session.active_list",new JSONObject().put("profile",profile)).optJSONArray("sessions");if(live==null)throw new java.io.IOException("No activity response");
            String status="idle";for(int i=0;i<live.length();i++){JSONObject row=live.optJSONObject(i);if(row!=null&&session.equals(row.optString("session_key")))status=row.optString("status","unknown");}
            failures=0;if(!"idle".equals(status))seenWorking=true;if("waiting".equals(status))main.post(()->{if(current==generation)finish("Your agent needs you","Open the conversation to answer or review a permission request.");});
            else if("idle".equals(status)&&(seenWorking||SystemClock.elapsedRealtime()>deadline-30*60*1000L+15000))main.post(()->{if(current==generation)finish("Your agent finished working","Open Murmur to read the latest conversation.");});
        }catch(Exception ex){failures++;if(socket!=null){socket.close();socket=null;}if(failures>=3)main.post(()->{if(current==generation)finish("Couldn't confirm your reply","Open Murmur and check Tailscale. No messages were resent.");});}
    }
    private void finish(String title,String detail){if(notifications!=null)notifications.notify(RESULT,notification(title,detail,false));stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();}
    @Override public void onTimeout(int startId,int fgsType){finish("Following paused","Open Murmur to check your conversation.");}
    @Override public void onDestroy(){generation++;worker.shutdownNow();if(api!=null)api.close();super.onDestroy();}
}
