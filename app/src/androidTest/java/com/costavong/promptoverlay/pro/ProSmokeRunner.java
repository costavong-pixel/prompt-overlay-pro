package com.costavong.promptoverlay.pro;

import android.app.Instrumentation;
import android.content.*;
import android.graphics.*;
import android.media.*;
import android.os.Bundle;
import java.io.*;
import java.util.*;

/** Real MediaCodec smoke test: trims, crossfades, natural-pitch speed, layers and shared A/V clock. */
public final class ProSmokeRunner extends Instrumentation {
    @Override public void onCreate(Bundle b){super.onCreate(b);start();}
    @Override public void onStart(){Bundle result=new Bundle();try{
        Context context=getTargetContext();File root=new File(context.getFilesDir(),"pro-smoke");root.mkdirs();File source=new File(root,"source.mp4");
        try(InputStream in=getContext().getAssets().open("sync.mp4");OutputStream out=new FileOutputStream(source)){byte[] b=new byte[65536];int n;while((n=in.read(b))>=0)out.write(b,0,n);}
        ProProject p=new ProProject();p.name="Synchronization smoke test";p.format="16:9";
        ProProject.Clip a=ProProject.inspect(source);a.inUs=125000;a.outUs=875000;a.speed=1.5;p.clips.add(a);
        ProProject.Clip b=ProProject.inspect(source);b.inUs=1125000;b.outUs=1875000;b.speed=.5;b.crossfadeUs=125000;p.clips.add(b);
        ProProject.Layer title=new ProProject.Layer();title.text="Prompt Overlay · مرحبا";title.clipId=a.id;title.startUs=200000;title.endUs=400000;title.y=.82f;p.layers.add(title);
        p.save(context);ProProject recovered=ProProject.load(p.directory(context));require(recovered.clips.size()==2,"Two clips must survive autosave");
        long duration=p.timeline().durationUs;require(duration==1875000,"Crossfade duration");
        ProAudio.Control control=new ProAudio.Control(){public void check(){}public void progress(String s,int n){android.util.Log.i("ProSmoke",s+" "+n);}};
        File output=new File(root,"output720.mp4");ProRenderEngine.render(p,720,output,new File(root,"work"),control);
        MediaExtractor ex=new MediaExtractor();try{ex.setDataSource(output.getPath());require(ex.getTrackCount()==2,"MP4 must have video and audio");for(int i=0;i<2;i++){MediaFormat f=ex.getTrackFormat(i);long d=f.getLong(MediaFormat.KEY_DURATION);require(Math.abs(d-duration)<100000,"Track duration must match timeline: "+d);}}finally{ex.release();}
        try(ProAudio.Pcm pcm=ProAudio.decode(output.getPath(),0,duration,1,48000,new File(root,"out.pcm"),control)){
            long[] expected={83333,416667,625000,1625000};float[] sample=new float[4800*2];
            for(long at:expected){pcm.read(ProTimeline.sampleAt(at+40000,48000),2400,sample);double sum=0;for(float value:sample)sum+=value*value;require(sum/sample.length>.0008,"Speech/beep must be present near "+at);
                pcm.read(ProTimeline.sampleAt(at-150000,48000),2400,sample);sum=0;for(float value:sample)sum+=value*value;require(sum/sample.length<.002,"Audio should not arrive early near "+at);}
            for(long at:new long[]{120000,660000}){pcm.read(ProTimeline.sampleAt(at,48000),2400,sample);int crossings=0;for(int i=1;i<2400;i++)if(sample[(i-1)*2]<=0&&sample[i*2]>0)crossings++;
                double hz=crossings/.05;require(hz>850&&hz<1150,"Speed changes must preserve 1 kHz audio pitch: "+hz);}
        }
        MediaMetadataRetriever m=new MediaMetadataRetriever();try{m.setDataSource(output.getPath());Bitmap frame=m.getFrameAtTime(110000,MediaMetadataRetriever.OPTION_CLOSEST);require(frame!=null,"Export must decode");int color=frame.getPixel(frame.getWidth()/2,frame.getHeight()/2);require(Color.red(color)>190&&Color.green(color)>190,"Video flash must match the audio marker");frame.recycle();}finally{m.release();}
        // Split should preserve timing and captions across the boundary.
        p.split(a.id,200000);require(p.clips.size()==3,"Split creates a second segment");require(p.timeline().durationUs==duration,"Split preserves duration");
        ProProject shortProject=new ProProject();shortProject.format="9:16";ProProject.Clip shortClip=ProProject.inspect(source);shortClip.outUs=200000;shortProject.clips.add(shortClip);
        File high=new File(root,"output1080.mp4");ProRenderEngine.render(shortProject,1080,high,new File(root,"work1080"),control);
        MediaExtractor highEx=new MediaExtractor();try{highEx.setDataSource(high.getPath());MediaFormat hf=highEx.getTrackFormat(0);require(hf.getInteger(MediaFormat.KEY_WIDTH)==1080&&hf.getInteger(MediaFormat.KEY_HEIGHT)==1920,"Portrait 1080p must render at 1080x1920");}finally{highEx.release();}
        File speechSource=new File(root,"speech.wav");try(InputStream in=getContext().getAssets().open("speech.wav");OutputStream out=new FileOutputStream(speechSource)){byte[] buf=new byte[32768];int n;while((n=in.read(buf))>=0)out.write(buf,0,n);}
        ProProject captions=new ProProject();ProProject.Clip speechClip=new ProProject.Clip();speechClip.path=speechSource.getPath();speechClip.sourceDurationUs=8300000;speechClip.outUs=8300000;captions.clips.add(speechClip);
        ProCaptions.generate(context,captions,speechClip.id,"en",control);require(!captions.layers.isEmpty(),"Local speech must produce editable captions");require(captions.layers.get(0).startUs>=0&&captions.layers.get(0).endUs<=speechClip.outUs,"Caption timestamps must be source-anchored");
        startActivitySync(new Intent(context,ProActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));waitForIdleSync();
        result.putString("stream","PASS: rendered 720p and portrait 1080p, checked both tracks, four audio markers, natural pitch, video flash, local captions, autosave and split. Output: "+output.getPath());finish(-1,result);
    }catch(Throwable e){StringWriter log=new StringWriter();e.printStackTrace(new PrintWriter(log));result.putString("stream","FAIL: "+log);finish(0,result);}}
    private void require(boolean condition,String message){if(!condition)throw new AssertionError(message);}
}
