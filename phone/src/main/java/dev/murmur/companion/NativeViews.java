package dev.murmur.companion;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.graphics.drawable.*;
import android.text.*;
import android.text.style.*;
import android.view.*;
import android.widget.*;
import java.util.regex.*;

/** Lightweight Android widgets, vector drawing and native text spans. */
final class NativeViews {
    static final int PAPER=0xfffaf8f3, CARD=0xffffffff, INK=0xff28212f, MUTED=0xff79717f, PURPLE=0xff7954bd, SOFT=0xffeee8f6, LINE=0xffe9e4ed, RED=0xffa83e48;
    static int dp(Context c,float n){return (int)(n*c.getResources().getDisplayMetrics().density+.5f);}
    static GradientDrawable shape(Context c,int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(c,radius));return d;}
    static RippleDrawable ripple(Context c,int color,int radius){return new RippleDrawable(ColorStateList.valueOf(0x227954bd),shape(c,color,radius),shape(c,Color.WHITE,radius));}
    static TextView text(Context c,String value,int size,int color,boolean bold){TextView t=new TextView(c);t.setText(value);t.setTextSize(size);t.setTextColor(color);t.setFontFeatureSettings("kern");t.setLineSpacing(dp(c,3),1);if(bold)t.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));return t;}
    static LinearLayout column(Context c){LinearLayout l=new LinearLayout(c);l.setOrientation(LinearLayout.VERTICAL);return l;}
    static LinearLayout row(Context c){LinearLayout l=new LinearLayout(c);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
    static void pad(View v,int h,int top,int bottom){v.setPadding(dp(v.getContext(),h),dp(v.getContext(),top),dp(v.getContext(),h),dp(v.getContext(),bottom));}
    static Button button(Context c,String title,boolean primary,Runnable click){Button b=new Button(c);b.setText(title);b.setAllCaps(false);b.setTextSize(15);b.setTextColor(primary?Color.WHITE:INK);b.setBackground(ripple(c,primary?PURPLE:SOFT,18));b.setMinHeight(dp(c,52));b.setOnClickListener(v->click.run());return b;}
    static View icon(Context c,String icon,String description,Runnable action){Glyph v=new Glyph(c,icon);v.setContentDescription(description);v.setBackground(ripple(c,Color.TRANSPARENT,24));v.setFocusable(true);v.setOnClickListener(w->action.run());v.setLayoutParams(new LinearLayout.LayoutParams(dp(c,48),dp(c,48)));return v;}
    static CharSequence markdown(String raw){
        // Keep text selectable and linkable without HTML or a browser renderer.
        SpannableStringBuilder result=new SpannableStringBuilder();boolean code=false;
        String[] lines=raw.split("\n",-1);
        for(int n=0;n<lines.length;n++){
            String line=lines[n];if(line.stripLeading().startsWith("```")){code=!code;continue;}
            int start=result.length();boolean heading=!code&&line.matches("^#{1,6} .*" );
            if(heading)line=line.replaceFirst("^#{1,6} ","");
            if(!code)line=line.replaceFirst("^[-*] ","• ");
            if(code){result.append(line);result.setSpan(new TypefaceSpan("monospace"),start,result.length(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);result.setSpan(new BackgroundColorSpan(SOFT),start,result.length(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);}
            else{
                Matcher m=Pattern.compile("\\*\\*(.+?)\\*\\*|`([^`]+)`|\\[([^\\]]+)\\]\\((https?://[^ )]+)\\)").matcher(line);int at=0;
                while(m.find()){
                    result.append(line.substring(at,m.start()));int a=result.length();String value=m.group(1)!=null?m.group(1):m.group(2)!=null?m.group(2):m.group(3);result.append(value);
                    if(m.group(1)!=null)result.setSpan(new StyleSpan(Typeface.BOLD),a,result.length(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    else if(m.group(2)!=null){result.setSpan(new TypefaceSpan("monospace"),a,result.length(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);result.setSpan(new BackgroundColorSpan(SOFT),a,result.length(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);}
                    else result.setSpan(new URLSpan(m.group(4)),a,result.length(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    at=m.end();
                }result.append(line.substring(at));
            }
            if(heading){result.setSpan(new StyleSpan(Typeface.BOLD),start,result.length(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);result.setSpan(new RelativeSizeSpan(1.12f),start,result.length(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);}
            if(n<lines.length-1)result.append('\n');
        }
        return result;
    }
    static class Glyph extends View{
        final String name;final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);final Path path=new Path();
        Glyph(Context c,String name){super(c);this.name=name;setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);}
        @Override protected void onDraw(Canvas canvas){super.onDraw(canvas);canvas.save();canvas.translate((getWidth()-dp(getContext(),24))/2f,(getHeight()-dp(getContext(),24))/2f);canvas.scale(dp(getContext(),24)/24f,dp(getContext(),24)/24f);p.setColor(isEnabled()?INK:MUTED);p.setStrokeWidth(1.8f);p.setStyle(Paint.Style.STROKE);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);path.reset();
            switch(name){
                case "back" -> {path.moveTo(15,5);path.lineTo(8,12);path.lineTo(15,19);}
                case "plus" -> {path.moveTo(12,5);path.lineTo(12,19);path.moveTo(5,12);path.lineTo(19,12);}
                case "send" -> {p.setColor(Color.WHITE);path.moveTo(12,18);path.lineTo(12,6);path.moveTo(6,12);path.lineTo(12,6);path.lineTo(18,12);}
                case "stop" -> {p.setColor(Color.WHITE);canvas.drawRoundRect(6,6,18,18,3,3,p);}
                case "more" -> {p.setStyle(Paint.Style.FILL);for(int i=0;i<3;i++)canvas.drawCircle(6+i*6,12,1.5f,p);}
                case "search" -> {canvas.drawCircle(10,10,6,p);path.moveTo(15,15);path.lineTo(20,20);}
                case "chats" -> {canvas.drawRoundRect(4,4,20,17,4,4,p);path.moveTo(9,17);path.lineTo(5,21);path.lineTo(5,16);}
                case "agents" -> {canvas.drawCircle(9,8,3,p);canvas.drawCircle(17,9,2.5f,p);canvas.drawArc(3,13,15,24,180,180,false,p);canvas.drawArc(13,14,23,24,200,150,false,p);}
                case "settings" -> {canvas.drawCircle(12,12,3,p);canvas.drawCircle(12,12,8,p);for(int i=0;i<8;i++){double a=Math.PI*i/4;canvas.drawLine((float)(12+8*Math.cos(a)),(float)(12+8*Math.sin(a)),(float)(12+10*Math.cos(a)),(float)(12+10*Math.sin(a)),p);}}
                case "mic" -> {canvas.drawRoundRect(9,3,15,14,3,3,p);canvas.drawArc(6,6,18,18,0,180,false,p);path.moveTo(12,18);path.lineTo(12,21);path.moveTo(9,21);path.lineTo(15,21);}
                case "chevron" -> {path.moveTo(9,7);path.lineTo(14,12);path.lineTo(9,17);}
                case "refresh" -> {canvas.drawArc(4,4,20,20,30,300,false,p);path.moveTo(21,5);path.lineTo(21,11);path.lineTo(15,11);}
                default -> canvas.drawCircle(12,12,7,p);
            }canvas.drawPath(path,p);canvas.restore();
        }
    }
}
