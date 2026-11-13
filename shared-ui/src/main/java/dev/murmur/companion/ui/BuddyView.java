package dev.murmur.companion.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.*;
import android.os.PowerManager;
import android.view.View;

/** Native adaptation of getumbrel/murmur's MIT Buddy geometry and seeded traits.
 * Paths and shaders are cached; only active characters animate. No web renderer or image fetch. */
public final class BuddyView extends View {
    public enum Mood { IDLE, THINKING, WORKING, REPLYING, WAITING, DONE, ERROR, OFFLINE }
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path body=new Path(),accPath=new Path();
    private Shader fill,shine;
    private final RectF notch=new RectF();
    private String seed="buddy";private float hue=278,phase,eyeGap=.27f,eyeY=38,eyeScale=1,tilt,w=65,h=80,accX;
    private int accessory;private boolean rosy,motionAllowed=true;
    private Mood mood=Mood.IDLE;
    private ValueAnimator animator;
    public BuddyView(Context context){super(context);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);rebuild();}
    public void character(String seed,float hue){String next=seed==null?"buddy":seed;if(this.seed.equals(next)&&this.hue==hue)return;this.seed=next;this.hue=hue;rebuild();invalidate();}
    public void mood(Mood next){if(mood==next){syncAnimation();return;}mood=next;syncAnimation();invalidate();}
    public Mood mood(){return mood;}
    public float hue(){return hue;}
    public void motionAllowed(boolean allowed){motionAllowed=allowed;syncAnimation();}
    private void rebuild(){
        Random r=new Random(seed);float pick=r.next();accessory=pick<.5?0:pick<.67?1:pick<.84?2:3;
        w=r.between(62,67);h=r.between(79,83);float arch=r.between(.98f,1.1f),full=r.between(.62f,.68f),nw=r.between(10,12),nd=r.between(14,17);
        tilt=r.between(-2.2f,2.2f);r.next();eyeGap=r.between(.25f,.3f);float eyeFraction=r.between(.3f,.35f);eyeScale=r.between(.94f,1.08f);accX=r.between(-7,7);rosy=r.next()<.3;if(accessory!=0)h=Math.min(h,79);
        float cx=50,hw=w/2,L=cx-hw,R=cx+hw,B=91,T=B-h,S=T+hw*arch,round=w*.27f,nr=nw/2,fr=nr*.9f,s1=S+(B-round-S)*.35f,s2=S+(B-round-S)*.78f;
        body.reset();body.moveTo(L,S);body.cubicTo(L,S-(S-T)*full,cx-hw*full,T,cx,T);body.cubicTo(cx+hw*full,T,R,S-(S-T)*full,R,S);body.cubicTo(R+.9f,s1,R+.9f,s2,R,B-round);body.cubicTo(R,B-round*.42f,R-round*.42f,B,R-round,B);body.lineTo(cx+nr+fr,B);body.quadTo(cx+nr,B,cx+nr,B-fr);body.lineTo(cx+nr,B-nd+nr);notch.set(cx-nr,B-nd,cx+nr,B-nd+2*nr);body.arcTo(notch,0,-180);body.lineTo(cx-nr,B-fr);body.quadTo(cx-nr,B,cx-nr-fr,B);body.lineTo(L+round,B);body.cubicTo(L+round*.42f,B,L,B-round*.42f,L,B-round);body.cubicTo(L-.9f,s2,L-.9f,s1,L,S);body.close();
        eyeY=T+h*eyeFraction;fill=new RadialGradient(34,T+8,90,new int[]{colour(hue,.86f,.09f),colour(hue,.68f,.17f),colour(hue,.43f,.15f)},new float[]{0,.52f,1},Shader.TileMode.CLAMP);
        shine=new RadialGradient(37,T+13,24,new int[]{0x88ffffff,0x22ffffff,0x00ffffff},new float[]{0,.5f,1},Shader.TileMode.CLAMP);
        accPath.reset();float ax=50+accX;
        if(accessory==1){accPath.moveTo(ax,T+1);accPath.cubicTo(ax-1,T-4,ax,T-7,ax+1,T-10);accPath.moveTo(ax,T-5);accPath.cubicTo(ax-6,T-3,ax-10,T-7,ax-10,T-10);accPath.cubicTo(ax-5,T-12,ax-1,T-9,ax,T-5);accPath.moveTo(ax+1,T-8);accPath.cubicTo(ax+2,T-13,ax+6,T-11,ax+10,T-9);accPath.cubicTo(ax+9,T-7,ax+5,T-7,ax+1,T-8);}
        else if(accessory==2){accPath.moveTo(ax-3,T+1);accPath.cubicTo(ax-4,T-4,ax-1,T-8,ax+4,T-9);accPath.cubicTo(ax+8,T-10,ax+9,T-10,ax+9,T-12);accPath.cubicTo(ax+11,T-7,ax+1,T-5,ax+3,T+1);accPath.close();}
        else if(accessory==3){accPath.moveTo(ax,T+1);accPath.cubicTo(ax,T-3,ax,T-8,ax+1,T-6);}
    }
    /** OKLCH to sRGB, so hue metadata has the same meaning as Murmur's palette. */
    public static int colour(float hue,float light,float chroma){
        double a=chroma*Math.cos(Math.toRadians(hue)),b=chroma*Math.sin(Math.toRadians(hue));
        double l=Math.pow(light+.3963377774*a+.2158037573*b,3),m=Math.pow(light-.1055613458*a-.0638541728*b,3),s=Math.pow(light-.0894841775*a-1.291485548*b,3);
        return Color.rgb(channel(4.0767416621*l-3.3077115913*m+.2309699292*s),channel(-1.2684380046*l+2.6097574011*m-.3413193965*s),channel(-.0041960863*l-.7034186147*m+1.707614701*s));
    }
    private static int channel(double linear){double value=linear<=.0031308?12.92*linear:1.055*Math.pow(linear,1/2.4)-.055;return (int)Math.round(Math.max(0,Math.min(1,value))*255);}
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);float scale=Math.min(getWidth(),getHeight())/100f;canvas.save();canvas.translate((getWidth()-100*scale)/2,(getHeight()-100*scale)/2);canvas.scale(scale,scale);
        boolean active=mood==Mood.THINKING||mood==Mood.WORKING||mood==Mood.REPLYING;float wave=(float)Math.sin(phase*Math.PI*2);
        paint.setShader(null);paint.setStyle(Paint.Style.FILL);paint.setColor(0x14000000);canvas.drawOval(24,89,77,98,paint);
        canvas.save();canvas.rotate(tilt+(active?wave*2:0),50,89);canvas.translate(0,active?-Math.abs(wave)*2:0);
        if(mood==Mood.OFFLINE)paint.setAlpha(130);else paint.setAlpha(255);
        paint.setShader(fill);canvas.drawPath(body,paint);paint.setShader(shine);canvas.drawPath(body,paint);paint.setShader(null);paint.setColor(colour(hue,.56f,.14f));paint.setStrokeWidth(1.6f);paint.setStrokeCap(Paint.Cap.ROUND);paint.setStyle(accessory==3?Paint.Style.STROKE:Paint.Style.FILL);canvas.drawPath(accPath,paint);
        if(accessory==3){paint.setStyle(Paint.Style.FILL);canvas.drawCircle(51+accX,91-h-8.5f,3.2f,paint);paint.setColor(0x99ffffff);canvas.drawCircle(50+accX,91-h-9.5f,1,paint);}
        paint.setStyle(Paint.Style.FILL);paint.setColor(0xff35243d);float ey=eyeY+(mood==Mood.THINKING?-2:0),gap=w*eyeGap/2,eyeHeight=(mood==Mood.WAITING?9:mood==Mood.DONE?3:7)*eyeScale;
        if(mood==Mood.OFFLINE)eyeHeight=2;if(mood==Mood.REPLYING)eyeHeight=5+Math.abs(wave)*3;
        for(int side=-1;side<=1;side+=2){float x=50+side*gap+(mood==Mood.THINKING?2:0);canvas.drawRoundRect(x-1.8f,ey-eyeHeight/2,x+1.8f,ey+eyeHeight/2,2,2,paint);if(mood==Mood.ERROR){paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(1.5f);canvas.drawLine(x-3,ey-7,x+3,ey-5,paint);paint.setStyle(Paint.Style.FILL);}}
        if(rosy||mood==Mood.DONE){paint.setColor(0x30e6628d);canvas.drawOval(28,ey+5,39,ey+10,paint);canvas.drawOval(61,ey+5,72,ey+10,paint);}
        canvas.restore();paint.setAlpha(255);
        if(mood==Mood.WAITING||mood==Mood.ERROR){paint.setColor(mood==Mood.WAITING?0xffc28b25:0xffb94753);canvas.drawCircle(81,26,10,paint);paint.setColor(Color.WHITE);paint.setStrokeWidth(2);canvas.drawLine(81,21,81,27,paint);canvas.drawCircle(81,31,1,paint);}
        canvas.restore();
    }
    private void syncAnimation(){
        boolean active=mood==Mood.THINKING||mood==Mood.WORKING||mood==Mood.REPLYING;
        PowerManager power=(PowerManager)getContext().getSystemService(Context.POWER_SERVICE);
        boolean run=active&&motionAllowed&&isAttachedToWindow()&&getWindowVisibility()==VISIBLE&&isShown()&&ValueAnimator.areAnimatorsEnabled()&&(power==null||!power.isPowerSaveMode());
        if(run&&animator==null){animator=ValueAnimator.ofFloat(0,1);animator.setDuration(1800);animator.setRepeatCount(ValueAnimator.INFINITE);animator.addUpdateListener(a->{phase=(float)a.getAnimatedValue();invalidate();});animator.start();}
        else if(!run&&animator!=null){animator.cancel();animator.removeAllUpdateListeners();animator=null;phase=0;}
    }
    @Override protected void onAttachedToWindow(){super.onAttachedToWindow();syncAnimation();}
    @Override protected void onDetachedFromWindow(){if(animator!=null){animator.cancel();animator.removeAllUpdateListeners();animator=null;}super.onDetachedFromWindow();}
    @Override protected void onWindowVisibilityChanged(int visibility){super.onWindowVisibilityChanged(visibility);syncAnimation();}
    @Override protected void onVisibilityChanged(View changed,int visibility){super.onVisibilityChanged(changed,visibility);syncAnimation();}
    private static final class Random{
        int a;Random(String seed){a=0x811c9dc5;for(int i=0;i<seed.length();i++)a=(a^seed.charAt(i))*0x01000193;}
        float next(){a+=0x6d2b79f5;int t=(a^(a>>>15))*(1|a);t=(t+(t^(t>>>7))*(61|t))^t;return (float)(Integer.toUnsignedLong(t^(t>>>14))/4294967296.0);}
        float between(float low,float high){return low+(high-low)*next();}
    }
}
