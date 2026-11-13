package dev.murmur.companion.core;

import org.json.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class NativeChatTest {
    private NativeChat chat()throws Exception{NativeChat c=new NativeChat("home","default","stored");c.snapshot(new JSONObject().put("session_id","runtime").put("messages",new JSONArray()));return c;}
    private JSONObject event(String type,int seq,JSONObject payload)throws Exception{return new JSONObject().put("type",type).put("session_id","runtime").put("seq",seq).put("payload",payload);}
    @Test public void streamsDeltasDeduplicatesReplayAndFinishesInPlace()throws Exception{
        NativeChat c=chat();
        c.event(event("message.start",1,new JSONObject()));
        c.event(event("message.delta",2,new JSONObject().put("text","Hello ").put("offset",0).put("turn_id","a").put("segment",0)));
        c.event(event("message.delta",3,new JSONObject().put("text","world").put("offset",6).put("turn_id","a").put("segment",0)));
        c.event(event("message.delta",3,new JSONObject().put("text","world").put("offset",6).put("turn_id","a").put("segment",0)));
        assertEquals("Hello world",c.rows.get(0).text);assertEquals(1,c.rows.size());assertTrue(c.busy);
        c.event(event("message.complete",4,new JSONObject().put("text","Hello world!").put("status","complete")));
        assertEquals("Hello world!",c.rows.get(0).text);assertFalse(c.rows.get(0).streaming);assertFalse(c.busy);
    }
    @Test public void resumedInflightDoesNotDuplicateEarlierDeltasAndHiddenRowsStayHidden()throws Exception{
        NativeChat c=chat();c.snapshot(new JSONObject().put("session_id","runtime").put("running",true)
            .put("messages",new JSONArray().put(new JSONObject().put("role","user").put("text","private plumbing").put("display_kind","hidden"))
              .put(new JSONObject().put("role","user").put("text","Hello")))
            .put("inflight",new JSONObject().put("assistant","Hi there")));
        c.event(event("message.delta",10,new JSONObject().put("text"," there").put("offset",2).put("turn_id","existing").put("segment",0)));
        assertEquals(2,c.rows.size());assertEquals("Hi there",c.rows.get(1).text);
        c.event(event("message.delta",11,new JSONObject().put("text","!").put("offset",8).put("turn_id","existing").put("segment",0)));
        assertEquals("Hi there!",c.rows.get(1).text);assertFalse(c.needsSync);
    }
    @Test public void streamGapRequestsSyncInsteadOfInventingMissingText()throws Exception{
        NativeChat c=chat();c.event(event("message.delta",1,new JSONObject().put("text","Hello").put("offset",0)));
        c.event(event("message.delta",3,new JSONObject().put("text","missing").put("offset",12)));
        assertTrue(c.needsSync);assertEquals("Hello",c.rows.get(0).text);
        c.event(new JSONObject().put("type","message.complete").put("session_id","someone-else").put("payload",new JSONObject().put("text","Do not show")));
        assertEquals(1,c.rows.size());
    }
    @Test public void pendingRequestsRestoreAndExpireWithoutAnAutomaticAnswer()throws Exception{
        NativeChat c=chat();JSONObject ask=new JSONObject().put("id","approve").put("method","approval").put("params",new JSONObject().put("description","Delete a file?"));
        c.snapshot(new JSONObject().put("session_id","runtime").put("open_requests",new JSONArray().put(ask)));
        assertEquals("Needs you",c.status);assertTrue(c.requests.containsKey("approve"));
        c.event(event("request.cancel",1,new JSONObject().put("id","approve")));
        assertTrue(c.requests.isEmpty());
    }
    @Test public void activityProgressesFromThinkingToToolsReplyAndDone()throws Exception{
        NativeChat c=chat();c.event(event("message.start",1,new JSONObject()));
        assertEquals(NativeChat.Phase.THINKING,c.phase());assertTrue(c.workStartedAt>0);
        c.event(event("tool.start",2,new JSONObject().put("name","web_search")));
        assertEquals(NativeChat.Phase.WORKING,c.phase());assertEquals("Looking things up",c.activityLabel());
        c.event(event("message.delta",3,new JSONObject().put("text","Here is a plan")));
        assertEquals(NativeChat.Phase.REPLYING,c.phase());
        c.event(event("message.complete",4,new JSONObject().put("text","Here is a plan")));
        assertEquals(NativeChat.Phase.DONE,c.phase());assertFalse(c.busy);
    }
    @Test public void waitingRemainsVisibleWhenDeltasArriveAndLegacyApprovalsAreBusy()throws Exception{
        NativeChat c=chat();c.snapshot(new JSONObject().put("session_id","runtime").put("pending_approval",new JSONObject().put("request_id","ask").put("description","Delete?")));
        assertTrue(c.busy);assertEquals(NativeChat.Phase.WAITING,c.phase());
        c.event(event("message.delta",1,new JSONObject().put("text","Please check first")));
        assertEquals(NativeChat.Phase.WAITING,c.phase());
        c.event(event("request.cancel",2,new JSONObject().put("request_id","ask")));assertTrue(c.requests.isEmpty());
    }
    @Test public void completedSnapshotsDoNotShowAFakeStreamingAnswerAndHistoryStaysBounded()throws Exception{
        NativeChat c=chat();c.snapshot(new JSONObject().put("session_id","runtime").put("running",false).put("inflight",new JSONObject().put("assistant","A saved answer").put("streaming",false)));
        assertFalse(c.rows.get(0).streaming);assertFalse(c.busy);assertEquals(NativeChat.Phase.DONE,c.phase());
        for(int i=0;i<600;i++)c.add("user","message "+i,false);
        assertEquals(500,c.rows.size());assertEquals("message 599",c.rows.get(499).text);
    }

}
