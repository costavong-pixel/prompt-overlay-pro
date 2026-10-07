package com.costavong.promptoverlay.pro;

import android.content.Context;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.util.AtomicFile;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class ProProject {
    public String id=UUID.randomUUID().toString(),name="Untitled video",format="9:16";
    public long revision=0;
    public int height=1080;
    public final List<Clip> clips=new ArrayList<>();
    public final List<Layer> layers=new ArrayList<>();
    public Music music;
    public String greenBackground="";
    public static final class Clip {
        public String id=UUID.randomUUID().toString(),path="",name="Video";
        public long sourceDurationUs,inUs=0,outUs=0,crossfadeUs=0;
        public int width=1080,height=1920;
        public double speed=1,volume=1,brightness=0,contrast=1,saturation=1,temperature=0;
        public float cropLeft=0,cropTop=0,cropRight=1,cropBottom=1;
        public boolean mute=false,green=false;
        public float greenThreshold=.35f,greenFeather=.12f;
        public JSONObject json()throws JSONException {
            JSONObject j=new JSONObject();j.put("id",id).put("path",path).put("name",name)
                .put("sourceDurationUs",sourceDurationUs).put("inUs",inUs).put("outUs",outUs)
                .put("width",width).put("height",height).put("speed",speed).put("volume",volume)
                .put("crossfadeUs",crossfadeUs).put("brightness",brightness).put("contrast",contrast)
                .put("saturation",saturation).put("temperature",temperature).put("mute",mute)
                .put("green",green).put("greenThreshold",greenThreshold).put("greenFeather",greenFeather)
                .put("cropLeft",cropLeft).put("cropTop",cropTop).put("cropRight",cropRight).put("cropBottom",cropBottom);
            return j;
        }
        public static Clip from(JSONObject j) {
            Clip c=new Clip();c.id=j.optString("id",c.id);c.path=j.optString("path");c.name=j.optString("name","Video");
            c.sourceDurationUs=j.optLong("sourceDurationUs");c.inUs=j.optLong("inUs");c.outUs=j.optLong("outUs",c.sourceDurationUs);
            c.width=j.optInt("width",1080);c.height=j.optInt("height",1920);c.speed=j.optDouble("speed",1);
            c.volume=j.optDouble("volume",1);c.crossfadeUs=j.optLong("crossfadeUs");c.brightness=j.optDouble("brightness");
            c.contrast=j.optDouble("contrast",1);c.saturation=j.optDouble("saturation",1);c.temperature=j.optDouble("temperature");
            c.mute=j.optBoolean("mute");c.green=j.optBoolean("green");c.greenThreshold=(float)j.optDouble("greenThreshold",.35);
            c.greenFeather=(float)j.optDouble("greenFeather",.12);c.cropLeft=(float)j.optDouble("cropLeft");
            c.cropTop=(float)j.optDouble("cropTop");c.cropRight=(float)j.optDouble("cropRight",1);c.cropBottom=(float)j.optDouble("cropBottom",1);
            return c;
        }
    }
    public static final class Layer {
        public String id=UUID.randomUUID().toString(),kind="text",text="",path="",clipId="";
        // If clipId is set, start/end are original source times: speed/split/reorder preserve synchronization.
        public long startUs=0,endUs=3_000_000;
        public float x=.5f,y=.82f,size=.07f,opacity=1;
        public int color=0xffffffff,background=0x99000000;
        public boolean bold=false;
        public boolean wholeVideo=false;
        public JSONObject json()throws JSONException {
            return new JSONObject().put("id",id).put("kind",kind).put("text",text).put("path",path)
                .put("clipId",clipId).put("startUs",startUs).put("endUs",endUs).put("x",x).put("y",y)
                .put("size",size).put("opacity",opacity).put("color",color).put("background",background).put("bold",bold).put("wholeVideo",wholeVideo);
        }
        public static Layer from(JSONObject j) {
            Layer l=new Layer();l.id=j.optString("id",l.id);l.kind=j.optString("kind","text");l.text=j.optString("text");
            l.path=j.optString("path");l.clipId=j.optString("clipId");l.startUs=j.optLong("startUs");l.endUs=j.optLong("endUs",3_000_000);
            l.x=(float)j.optDouble("x",.5);l.y=(float)j.optDouble("y",.82);l.size=(float)j.optDouble("size",.07);
            l.opacity=(float)j.optDouble("opacity",1);l.color=j.optInt("color",0xffffffff);l.background=j.optInt("background",0x99000000);l.bold=j.optBoolean("bold");l.wholeVideo=j.optBoolean("wholeVideo");return l;
        }
        public long start(ProTimeline t) { if(wholeVideo)return 0;ProTimeline.Entry e=t.byId(clipId);return e==null?startUs:e.mapSource(Math.max(startUs,e.sourceStartUs)); }
        public long end(ProTimeline t) { if(wholeVideo)return t.durationUs;ProTimeline.Entry e=t.byId(clipId);return e==null?endUs:e.mapSource(Math.min(endUs,e.sourceEndUs)); }
        public boolean visible(ProTimeline t,long us) {
            if(!clipId.isEmpty()&&t.byId(clipId)==null)return false;
            return us>=start(t)&&us<end(t);
        }
    }
    public static final class Music {
        public String path="",name="Music";
        public long inUs=0,outUs=0,startUs=0,fadeInUs=500_000,fadeOutUs=500_000;
        public double volume=.35;
        public JSONObject json()throws JSONException{return new JSONObject().put("path",path).put("name",name)
            .put("inUs",inUs).put("outUs",outUs).put("startUs",startUs).put("volume",volume).put("fadeInUs",fadeInUs).put("fadeOutUs",fadeOutUs);}
        public static Music from(JSONObject j){Music m=new Music();m.path=j.optString("path");m.name=j.optString("name","Music");
            m.inUs=j.optLong("inUs");m.outUs=j.optLong("outUs");m.startUs=j.optLong("startUs");m.volume=j.optDouble("volume",.35);
            m.fadeInUs=j.optLong("fadeInUs",500_000);m.fadeOutUs=j.optLong("fadeOutUs",500_000);return m;}
    }
    public ProTimeline timeline(){ProTimeline t=new ProTimeline();for(Clip c:clips)t.append(c.id,c.inUs,c.outUs,c.speed,c.crossfadeUs);return t;}
    public Clip clip(String id){for(Clip c:clips)if(c.id.equals(id))return c;return null;}
    public JSONObject json()throws JSONException{
        JSONObject j=new JSONObject().put("schema",1).put("id",id).put("name",name).put("format",format)
            .put("height",height).put("revision",revision).put("greenBackground",greenBackground);
        JSONArray c=new JSONArray(),l=new JSONArray();for(Clip v:clips)c.put(v.json());for(Layer v:layers)l.put(v.json());
        j.put("clips",c).put("layers",l);if(music!=null)j.put("music",music.json());return j;
    }
    public static ProProject from(JSONObject j)throws JSONException{
        ProProject p=new ProProject();p.id=j.getString("id");p.name=j.optString("name","Untitled video");p.format=j.optString("format","9:16");
        p.height=j.optInt("height",1080);p.revision=j.optLong("revision");p.greenBackground=j.optString("greenBackground");
        JSONArray c=j.optJSONArray("clips"),l=j.optJSONArray("layers");if(c!=null)for(int i=0;i<c.length();i++)p.clips.add(Clip.from(c.getJSONObject(i)));
        if(l!=null)for(int i=0;i<l.length();i++)p.layers.add(Layer.from(l.getJSONObject(i)));if(j.has("music"))p.music=Music.from(j.getJSONObject("music"));return p;
    }
    public File directory(Context c){File f=new File(c.getFilesDir(),"pro/projects/"+id);f.mkdirs();return f;}
    public void save(Context c)throws Exception{
        AtomicFile af=new AtomicFile(new File(directory(c),"project.json"));FileOutputStream s=af.startWrite();
        try{s.write(json().toString().getBytes(StandardCharsets.UTF_8));af.finishWrite(s);}catch(Exception e){af.failWrite(s);throw e;}
    }
    public static ProProject load(File directory)throws Exception{
        AtomicFile af=new AtomicFile(new File(directory,"project.json"));return from(new JSONObject(new String(af.readFully(),StandardCharsets.UTF_8)));
    }
    public static List<ProProject> all(Context c){List<ProProject> r=new ArrayList<>();File root=new File(c.getFilesDir(),"pro/projects");
        File[] files=root.listFiles();if(files!=null)for(File f:files)try{r.add(load(f));}catch(Exception ignored){}
        r.sort((a,b)->Long.compare(new File(b.directory(c),"project.json").lastModified(),new File(a.directory(c),"project.json").lastModified()));return r;}
    public static Clip inspect(File f)throws Exception{
        MediaMetadataRetriever m=new MediaMetadataRetriever();try{m.setDataSource(f.getAbsolutePath());Clip c=new Clip();
            c.path=f.getAbsolutePath();c.name=f.getName();c.sourceDurationUs=Long.parseLong(m.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION))*1000;
            c.outUs=c.sourceDurationUs;String w=m.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH),h=m.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT);
            if(w==null||h==null||c.outUs<=0)throw new IOException("This file does not contain a readable video.");
            c.width=Integer.parseInt(w);c.height=Integer.parseInt(h);String rot=m.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION);
            if("90".equals(rot)||"270".equals(rot)){int tmp=c.width;c.width=c.height;c.height=tmp;}return c;
        }finally{m.release();}}
    public void split(String clipId,long timelineUs)throws Exception{
        Clip old=clip(clipId);ProTimeline.Entry e=timeline().byId(clipId);if(old==null||e==null)throw new IOException("Select a clip first.");
        long cut=e.sourceAt(timelineUs);if(cut-old.inUs<100_000||old.outUs-cut<100_000)throw new IOException("Move the playhead inside the clip before splitting.");
        ProTimeline current=timeline();int originalIndex=clips.indexOf(old);long before=Math.round((cut-old.inUs)/old.speed),after=Math.round((old.outUs-cut)/old.speed);
        long outgoing=originalIndex+1<current.entries.size()?current.entries.get(originalIndex+1).overlapUs:0;
        if(before<e.overlapUs*2||after<outgoing*2)throw new IOException("Split outside the transition area, or shorten the crossfade first.");
        Clip right=Clip.from(old.json());right.id=UUID.randomUUID().toString();right.inUs=cut;right.crossfadeUs=0;old.outUs=cut;
        clips.add(clips.indexOf(old)+1,right);
        List<Layer> additions=new ArrayList<>();for(Layer l:layers)if(l.clipId.equals(old.id)){
            if(l.startUs>=cut)l.clipId=right.id;
            else if(l.endUs>cut){Layer clone=Layer.from(l.json());clone.id=UUID.randomUUID().toString();clone.clipId=right.id;clone.startUs=cut;l.endUs=cut;additions.add(clone);}}
        layers.addAll(additions);
    }
    public int[] outputSize(int requestedHeight){double ratio;
        switch(format){case "16:9":ratio=16.0/9;break;case "1:1":ratio=1;break;case "4:5":ratio=.8;break;
            case "Original":ratio=clips.isEmpty()?9.0/16:(double)clips.get(0).width/clips.get(0).height;break;default:ratio=9.0/16;}
        // "1080p" means a 1080-pixel short edge: portrait 1080x1920, landscape 1920x1080.
        int w,h;if(ratio>=1){h=requestedHeight;w=(int)Math.round(h*ratio);}else{w=requestedHeight;h=(int)Math.round(w/ratio);}
        return new int[]{w+(w%2),h+(h%2)};
    }
}
