package dev.murmur.companion.core;

import org.json.JSONObject;

/** Murmur's public colour metadata and deterministic fallback palette (MIT; see notices). */
public final class AgentLook {
    private static final int[] HUES={278,250,222,192,160,128,82,52,28,4,338,308};
    public static float hue(JSONObject profile){
        JSONObject meta=profile.optJSONObject("ui_meta"),murmur=meta==null?null:meta.optJSONObject("murmur");
        if(murmur!=null&&murmur.opt("hue") instanceof Number){double value=murmur.optDouble("hue");if(Double.isFinite(value))return (float)((value%360+360)%360);}
        JSONObject bots=meta==null?null:meta.optJSONObject("hermes-bots");
        Float chosen=hexHue(bots==null?"":bots.optString("color"));if(chosen!=null)return chosen;
        if(profile.optBoolean("is_default")||profile.optString("name").equals("default"))return 278;
        int hash=0x811c9dc5;String seed=profile.optString("name","buddy");for(int i=0;i<seed.length();i++)hash=(hash^seed.charAt(i))*0x01000193;
        return HUES[(int)(Math.abs((long)hash)%HUES.length)];
    }
    private static Float hexHue(String hex){
        if(!hex.matches("#?[a-fA-F0-9]{6}"))return null;if(hex.startsWith("#"))hex=hex.substring(1);
        double[] c=new double[3];for(int i=0;i<3;i++){double x=Integer.parseInt(hex.substring(i*2,i*2+2),16)/255.0;c[i]=x<=.04045?x/12.92:Math.pow((x+.055)/1.055,2.4);}
        double l=Math.cbrt(.4122214708*c[0]+.5363325363*c[1]+.0514459929*c[2]),m=Math.cbrt(.2119034982*c[0]+.6806995451*c[1]+.1073969566*c[2]),s=Math.cbrt(.0883024619*c[0]+.2817188376*c[1]+.6299787005*c[2]);
        double a=1.9779984951*l-2.428592205*m+.4505937099*s,b=.0259040371*l+.7827717662*m-.808675766*s;if(Math.hypot(a,b)<.02)return null;
        double hue=(Math.toDegrees(Math.atan2(b,a))+360)%360;int closest=HUES[0];double distance=360;
        for(int h:HUES){double d=Math.abs((h-hue+540)%360-180);if(d<distance){closest=h;distance=d;}}return (float)closest;
    }
    private AgentLook(){}
}
