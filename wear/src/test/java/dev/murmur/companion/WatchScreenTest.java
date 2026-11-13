package dev.murmur.companion;

import android.graphics.*;
import android.view.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;
import java.io.*;
import java.lang.reflect.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "w192dp-h192dp-round-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class WatchScreenTest {
    @Test public void roundWatchRendersAndDraftRestoresToReview() throws Exception {
        try(ActivityController<WatchActivity> controller=Robolectric.buildActivity(WatchActivity.class).create().start()) {
            WatchActivity activity=controller.get();
            Field agent=WatchActivity.class.getDeclaredField("agent"); agent.setAccessible(true);
            agent.set(activity,new JSONObject().put("name","Atlas").put("profile","default").put("hermes","main"));
            Method chat=WatchActivity.class.getDeclaredMethod("showChat",JSONObject.class);chat.setAccessible(true);
            chat.invoke(activity,new JSONObject().put("status","Up to date").put("messages",new JSONArray().put(new JSONObject().put("role","assistant").put("text","Your afternoon is clear. Want to plan something?"))));
            render(activity.getWindow().getDecorView(),"watch-chat.png");
            Field draft=WatchActivity.class.getDeclaredField("draft");draft.setAccessible(true);draft.set(activity,"Give me a quick update.");
            Method review=WatchActivity.class.getDeclaredMethod("review");review.setAccessible(true);review.invoke(activity);
            render(activity.getWindow().getDecorView(),"watch-review.png");
            Field pending=WatchActivity.class.getDeclaredField("pendingId");pending.setAccessible(true);assertNull(pending.get(activity));
            // Dictation review alone must not initiate a Data Layer send.
            android.os.Bundle state=new android.os.Bundle();activity.onSaveInstanceState(state);
            assertEquals("Give me a quick update.",state.getString("draft"));
        }
    }
    private static void render(View view,String name)throws Exception {
        int size=384;view.measure(View.MeasureSpec.makeMeasureSpec(size,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(size,View.MeasureSpec.EXACTLY));view.layout(0,0,size,size);
        Bitmap bitmap=Bitmap.createBitmap(size,size,Bitmap.Config.ARGB_8888);view.draw(new Canvas(bitmap));
        File file=new File("build/reports/screenshots/"+name);file.getParentFile().mkdirs();try(OutputStream out=new FileOutputStream(file)){assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,out));}bitmap.recycle();
    }
}
