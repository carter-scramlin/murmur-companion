package dev.murmur.companion;

import android.graphics.*;
import android.view.*;
import android.widget.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;
import java.io.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "w411dp-h914dp-420dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class PhoneScreenTest {
    @Test public void onboardingRendersAndRejectsUnsafeAddress() throws Exception {
        try (ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class).setup().visible()) {
            MainActivity activity = controller.get();
            View decor = activity.getWindow().getDecorView();
            assertNotNull(find(decor, "Connect to Murmur"));
            render(decor, "phone-onboarding.png", 1080, 2400);
            EditText address = edit(decor); assertNotNull(address);
            address.setText("https://name:secret@example.com");
            find(decor, "Connect to Murmur").performClick();
            assertTrue(activity.getSharedPreferences("connection", 0).getString("origin", "").isEmpty());
            assertNotNull(find(decor, "Enter an HTTPS address without a path, password or query. For a private HTTP address, enable the local connection option."));
        }
    }
    private static View find(View view, String value) {
        if (view instanceof TextView text && value.contentEquals(text.getText())) return view;
        if (view instanceof ViewGroup group) for (int i=0; i<group.getChildCount(); i++) { View result=find(group.getChildAt(i),value); if(result!=null)return result; }
        return null;
    }
    private static EditText edit(View view) {
        if (view instanceof EditText input) return input;
        if (view instanceof ViewGroup group) for(int i=0;i<group.getChildCount();i++){ EditText result=edit(group.getChildAt(i));if(result!=null)return result; }
        return null;
    }
    private static void render(View view, String name, int width, int height) throws Exception {
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)); view.layout(0,0,width,height);
        Bitmap bitmap=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888); view.draw(new Canvas(bitmap));
        File file=new File("build/reports/screenshots/"+name); file.getParentFile().mkdirs(); try(OutputStream out=new FileOutputStream(file)){ assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,out)); } bitmap.recycle();
    }
}
