package com.costavong.promptoverlay.pro;

import android.content.Context;
import org.json.*;
import org.vosk.Model;
import org.vosk.Recognizer;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.zip.*;

/** Optional mobile speech packs. Recognition stays on the phone. */
final class ProCaptions {
    private static String pack(String language)throws IOException{
        switch(language){case "en":return "vosk-model-small-en-us-0.15";case "es":return "vosk-model-small-es-0.42";case "fr":return "vosk-model-small-fr-0.22";default:throw new IOException("Choose English, Spanish or French for automatic captions. Arabic captions can be entered manually.");}
    }
    private static File model(Context context,String language,ProAudio.Control control)throws Exception{
        String name=pack(language);File root=new File(context.getFilesDir(),"pro/speech");root.mkdirs();File target=new File(root,name);
        if(new File(target,"conf/model.conf").exists())return target;
        File archive=new File(root,name+".download"),staging=new File(root,name+".unpack");ProRenderEngine.erase(staging);staging.mkdirs();
        HttpURLConnection connection=null;
        try{control.progress("Downloading speech pack (about 40 MB)",0);
            connection=(HttpURLConnection)new URL("https://alphacephei.com/vosk/models/"+name+".zip").openConnection();connection.setConnectTimeout(20000);connection.setReadTimeout(20000);
            if(connection.getResponseCode()!=200)throw new IOException("Speech pack download failed. Check your internet connection and retry.");
            long length=connection.getContentLengthLong(),total=0;
            try(InputStream in=connection.getInputStream();OutputStream out=new FileOutputStream(archive)){byte[] b=new byte[32768];int n;
                while((n=in.read(b))>=0){control.check();total+=n;if(total>150_000_000)throw new IOException("Speech pack exceeds the expected size.");out.write(b,0,n);if(length>0)control.progress("Downloading speech pack",(int)(total*40/length));}}
            control.progress("Installing speech pack",40);String safe=staging.getCanonicalPath()+File.separator;long unpacked=0;
            try(ZipInputStream zip=new ZipInputStream(new FileInputStream(archive))){ZipEntry entry;byte[] b=new byte[32768];
                while((entry=zip.getNextEntry())!=null){control.check();File output=new File(staging,entry.getName());if(!output.getCanonicalPath().startsWith(safe))throw new IOException("Invalid speech-pack archive.");
                    if(entry.isDirectory()){output.mkdirs();continue;}output.getParentFile().mkdirs();try(OutputStream out=new FileOutputStream(output)){int n;while((n=zip.read(b))>=0){control.check();unpacked+=n;if(unpacked>300_000_000)throw new IOException("Invalid speech-pack size.");out.write(b,0,n);}}}}
            File extracted=new File(staging,name);if(!new File(extracted,"conf/model.conf").exists())throw new IOException("The speech pack is incomplete.");
            ProRenderEngine.erase(target);if(!extracted.renameTo(target))throw new IOException("Cannot install speech pack.");return target;
        }finally{if(connection!=null)connection.disconnect();archive.delete();ProRenderEngine.erase(staging);}
    }
    static void generate(Context context,ProProject project,String clipId,String language,ProAudio.Control control)throws Exception{
        ProProject.Clip clip=project.clip(clipId);if(clip==null)throw new IOException("Select a video clip first.");File model=model(context,language,control);
        File work=new File(context.getCacheDir(),"caption-"+project.id);work.mkdirs();List<ProProject.Layer> additions=new ArrayList<>();
        try(Model speech=new Model(model.getPath());Recognizer recognizer=new Recognizer(speech,16000)){
            recognizer.setWords(true);control.progress("Reading clip audio",45);
            try(ProAudio.Pcm pcm=ProAudio.decode(clip.path,clip.inUs,clip.outUs,1,16000,new File(work,"speech.pcm"),control)){
                if(pcm.frames==0)throw new IOException("This clip has no audio. Add captions manually.");float[] stereo=new float[8000];short[] mono=new short[4000];
                for(long pos=0;pos<pcm.frames;pos+=4000){control.check();int count=(int)Math.min(4000,pcm.frames-pos);pcm.read(pos,count,stereo);
                    for(int i=0;i<count;i++)mono[i]=(short)Math.round((stereo[i*2]+stereo[i*2+1])*.5f*32767);
                    if(recognizer.acceptWaveForm(mono,count))add(recognizer.getResult(),clip,additions);control.progress("Creating local captions",50+(int)(49L*pos/pcm.frames));}
                add(recognizer.getFinalResult(),clip,additions);
            }
            if(additions.isEmpty())throw new IOException("No speech was recognized. Try a clearer recording or enter captions manually.");
            project.layers.removeIf(l->"caption".equals(l.kind)&&clip.id.equals(l.clipId));project.layers.addAll(additions);project.revision++;project.save(context);control.progress("Captions ready",100);
        }finally{ProRenderEngine.erase(work);}
    }
    private static void add(String result,ProProject.Clip clip,List<ProProject.Layer> layers)throws Exception{
        JSONArray words=new JSONObject(result).optJSONArray("result");if(words==null)return;StringBuilder phrase=new StringBuilder();double start=0,end=0;int count=0;
        for(int i=0;i<words.length();i++){JSONObject word=words.getJSONObject(i);double a=word.getDouble("start"),b=word.getDouble("end");
            if(count>0&&(count>=6||b-start>3||a-end>.6)){caption(clip,layers,phrase.toString(),start,end);phrase.setLength(0);count=0;}
            if(count==0)start=a;if(count++>0)phrase.append(' ');phrase.append(word.getString("word"));end=b;}
        if(count>0)caption(clip,layers,phrase.toString(),start,end);
    }
    private static void caption(ProProject.Clip clip,List<ProProject.Layer> layers,String text,double a,double b){ProProject.Layer l=new ProProject.Layer();l.kind="caption";l.clipId=clip.id;l.text=text;
        l.startUs=clip.inUs+Math.round(a*1_000_000);l.endUs=Math.min(clip.outUs,clip.inUs+Math.round((b+.12)*1_000_000));l.bold=true;layers.add(l);}
}
