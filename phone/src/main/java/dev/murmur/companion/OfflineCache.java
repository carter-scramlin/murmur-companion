package dev.murmur.companion;

import android.content.Context;
import android.util.AtomicFile;
import dev.murmur.companion.core.NativeChat;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.json.*;

/** Bounded, app-private, origin-isolated read cache. Never stores runtime authorization or pending requests. */
final class OfflineCache {
    private final AtomicFile file;
    OfflineCache(Context context,String origin) throws Exception {
        byte[] digest=MessageDigest.getInstance("SHA-256").digest(origin.getBytes(StandardCharsets.UTF_8));
        StringBuilder key=new StringBuilder();for(byte b:digest)key.append(String.format(java.util.Locale.ROOT,"%02x",b));
        File dir=new File(context.getNoBackupFilesDir(),"conversations");if(!dir.exists()&&!dir.mkdirs())throw new IOException("Cannot create conversation cache");
        file=new AtomicFile(new File(dir,key+".json"));
    }
    JSONObject read(){try(FileInputStream in=file.openRead()){if(in.getChannel().size()>4*1024*1024)return new JSONObject();ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] bytes=new byte[16384];int n;while((n=in.read(bytes))!=-1){if(out.size()+n>4*1024*1024)return new JSONObject();out.write(bytes,0,n);}return new JSONObject(new String(out.toByteArray(),StandardCharsets.UTF_8));}catch(Exception ignored){return new JSONObject();}}
    void write(JSONObject value){byte[] data=value.toString().getBytes(StandardCharsets.UTF_8);if(data.length>4*1024*1024)return;FileOutputStream out=null;try{out=file.startWrite();out.write(data);file.finishWrite(out);}catch(Exception ex){if(out!=null)file.failWrite(out);}}
    void clear(){file.delete();}
    static void clearAll(Context context){File[] files=new File(context.getNoBackupFilesDir(),"conversations").listFiles();if(files!=null)for(File value:files)if(value.isFile())value.delete();}
    static JSONObject chat(NativeChat chat) throws Exception {
        JSONArray rows=new JSONArray();int length=0;
        for(int i=chat.rows.size()-1;i>=0;i--){NativeChat.Row row=chat.rows.get(i);if(!row.role.equals("user")&&!row.role.equals("assistant"))continue;if(length+row.text.length()>100000)break;length+=row.text.length();rows.put(new JSONObject().put("role",row.role).put("text",row.text));}
        JSONArray ordered=new JSONArray();for(int i=rows.length()-1;i>=0;i--)ordered.put(rows.get(i));
        return new JSONObject().put("hermes",chat.hermes).put("profile",chat.profile).put("id",chat.storedId).put("title",chat.title).put("rows",ordered);
    }
    static NativeChat restore(JSONObject saved) throws Exception {
        NativeChat chat=new NativeChat(saved.getString("hermes"),saved.getString("profile"),saved.getString("id"));chat.title=saved.optString("title","Conversation");chat.status="Saved on this phone";
        JSONArray rows=saved.optJSONArray("rows");if(rows!=null)for(int i=0;i<rows.length();i++){JSONObject row=rows.getJSONObject(i);chat.add(row.getString("role"),row.getString("text"),false);}return chat;
    }
}
