package dev.murmur.companion;
import dev.murmur.companion.core.NativeChat;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
public class OfflineCacheTest {
    @Test public void savedReadCacheSurvivesProcessLossWithoutRuntimeOrPrivateRequests()throws Exception{
        NativeChat live=new NativeChat("home","agent","stored");live.runtimeId="private-runtime";live.ready=true;live.busy=true;live.add("assistant","Saved reply",false);
        live.request(new JSONObject().put("id","private-request").put("params",new JSONObject().put("secret","never-cache-this")));
        JSONObject saved=OfflineCache.chat(live);assertFalse(saved.toString().contains("private-runtime"));assertFalse(saved.toString().contains("never-cache-this"));
        OfflineCache first=new OfflineCache(RuntimeEnvironment.getApplication(),"https://one.example");first.write(saved);
        NativeChat restored=OfflineCache.restore(new OfflineCache(RuntimeEnvironment.getApplication(),"https://one.example").read());
        assertEquals("Saved reply",restored.rows.get(0).text);assertFalse(restored.ready);assertFalse(restored.busy);assertTrue(restored.requests.isEmpty());assertEquals("",restored.runtimeId);
        assertEquals(0,new OfflineCache(RuntimeEnvironment.getApplication(),"https://two.example").read().length());first.clear();assertEquals(0,first.read().length());
    }
}
