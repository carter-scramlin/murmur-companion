package dev.murmur.companion.core;
import org.json.*;
import org.junit.Test;
import static org.junit.Assert.*;
public class AgentLookTest {
    @Test public void usesMurmurHueIncludingZeroAndNormalizesIt()throws Exception{
        JSONObject p=new JSONObject().put("name","atlas").put("ui_meta",new JSONObject().put("murmur",new JSONObject().put("hue",0)));
        assertEquals(0,AgentLook.hue(p),.01);
        p.getJSONObject("ui_meta").getJSONObject("murmur").put("hue",420);assertEquals(60,AgentLook.hue(p),.01);
    }
    @Test public void respectsBotColoursAndUsesStableFallbacks()throws Exception{
        JSONObject p=new JSONObject().put("name","garden").put("ui_meta",new JSONObject().put("hermes-bots",new JSONObject().put("color","#00ff00")));
        assertEquals(128,AgentLook.hue(p),.01);
        assertEquals(278,AgentLook.hue(new JSONObject().put("name","default")),.01);
        p.remove("ui_meta");assertEquals(AgentLook.hue(p),AgentLook.hue(new JSONObject(p.toString())),.01);
    }
}
