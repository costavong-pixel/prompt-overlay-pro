package com.costavong.promptoverlay.pro;

import android.media.*;
import android.view.Surface;
import java.io.*;
import java.nio.*;
import java.util.*;

/** The same offline renderer makes preview and final output; all media uses one master timeline. */
final class ProRenderEngine {
    static final int RATE=48000,FPS=30;
    static void render(ProProject p,int resolution,File destination,File work,ProAudio.Control control)throws Exception{
        if(p.clips.isEmpty())throw new IOException("Import a video first.");erase(work);work.mkdirs();
        long duration=p.timeline().durationUs;
        if(work.getUsableSpace()<duration/1_000_000.0*(RATE*4*3+2_200_000)+64*1024*1024)throw new IOException("Free some storage before rendering this project.");
        File video=new File(work,"video.mp4"),audio=new File(work,"audio.m4a"),partial=new File(destination.getPath()+".partial");
        try{control.progress("Rendering video",0);video(p,resolution,video,control);
            control.progress("Preparing audio",70);audio(p,audio,work,control);
            control.progress("Finishing MP4",97);mux(video,audio,partial,control);
            if(destination.exists()&&!destination.delete())throw new IOException("Cannot replace the previous export.");
            if(!partial.renameTo(destination))throw new IOException("Cannot save the completed export.");control.progress("Complete",100);
        }finally{partial.delete();erase(work);}
    }
    static void erase(File f){File[] children=f.listFiles();if(children!=null)for(File c:children)erase(c);f.delete();}
    private static final class EncoderSink implements AutoCloseable{
        final MediaCodec codec;final MediaMuxer muxer;int track=-1;boolean started=false,eos=false;long lastPts=-1;
        EncoderSink(MediaCodec c,File f)throws Exception{codec=c;muxer=new MediaMuxer(f.getPath(),MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);}
        void drain(boolean wait,ProAudio.Control control)throws Exception{
            MediaCodec.BufferInfo info=new MediaCodec.BufferInfo();long deadline=System.nanoTime()+30_000_000_000L;
            while(!eos){control.check();int idx=codec.dequeueOutputBuffer(info,wait?10000:0);
                if(idx==MediaCodec.INFO_TRY_AGAIN_LATER){if(!wait)return;if(System.nanoTime()>deadline)throw new IOException("Encoder stopped responding.");continue;}
                if(idx==MediaCodec.INFO_OUTPUT_FORMAT_CHANGED){if(started)throw new IOException("Encoder changed format unexpectedly.");track=muxer.addTrack(codec.getOutputFormat());muxer.start();started=true;continue;}
                if(idx>=0){deadline=System.nanoTime()+30_000_000_000L;ByteBuffer data=codec.getOutputBuffer(idx);
                    if((info.flags&MediaCodec.BUFFER_FLAG_CODEC_CONFIG)!=0)info.size=0;
                    if(info.size>0){if(!started)throw new IOException("Encoder output has no format.");
                        if(info.presentationTimeUs<lastPts)throw new IOException("Encoder returned out-of-order timestamps.");lastPts=info.presentationTimeUs;
                        data.position(info.offset);data.limit(info.offset+info.size);muxer.writeSampleData(track,data,info);}
                    eos=(info.flags&MediaCodec.BUFFER_FLAG_END_OF_STREAM)!=0;codec.releaseOutputBuffer(idx,false);}
            }
        }
        @Override public void close(){try{if(started)muxer.stop();}finally{muxer.release();}}
    }
    private static void video(ProProject p,int resolution,File output,ProAudio.Control control)throws Exception{
        int[] size=p.outputSize(resolution);MediaFormat f=MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC,size[0],size[1]);
        f.setInteger(MediaFormat.KEY_COLOR_FORMAT,MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        f.setInteger(MediaFormat.KEY_BIT_RATE,resolution==1080?8_000_000:4_000_000);f.setInteger(MediaFormat.KEY_FRAME_RATE,FPS);f.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL,1);
        if(android.os.Build.VERSION.SDK_INT>=29)f.setInteger(MediaFormat.KEY_MAX_B_FRAMES,0);
        MediaCodec codec=MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC);Surface input=null;
        try(ProFrameRenderer frames=new ProFrameRenderer(p,size[0],size[1])){control.progress("Decoding first frame",0);android.graphics.Bitmap first=frames.render(0);
            control.progress("Configuring video encoder",0);try{codec.configure(f,null,null,MediaCodec.CONFIGURE_FLAG_ENCODE);}catch(IllegalArgumentException unsupported){throw new IOException("This phone could not encode "+size[0]+"×"+size[1]+". Try 720p or a different format.",unsupported);}input=codec.createInputSurface();
            control.progress("Connecting encoder surface",0);
            try(ProGlSurface gl=new ProGlSurface(input);EncoderSink sink=new EncoderSink(codec,output)){
                control.progress("Starting video encoder",0);codec.start();
                long duration=p.timeline().durationUs;int count=(int)Math.ceil(duration*FPS/1_000_000.0);
                for(int n=0;n<count;n++){control.check();sink.drain(false,control);long us=ProTimeline.frameTime(n,FPS);gl.draw(n==0?first:frames.render(us),us);sink.drain(false,control);
                    if(n%FPS==0)control.progress("Rendering video",(int)(70L*n/count));}
                codec.signalEndOfInputStream();sink.drain(true,control);
            }
        }finally{try{codec.stop();}catch(Exception ignored){}codec.release();if(input!=null)input.release();}
    }
    private static void audio(ProProject p,File output,File work,ProAudio.Control control)throws Exception{
        ProTimeline t=p.timeline();Map<String,ProAudio.Pcm> pcm=new HashMap<>();ProAudio.Pcm music=null;
        try{
            int done=0;for(ProProject.Clip c:p.clips){control.check();if(!c.mute&&c.volume>0)pcm.put(c.id,ProAudio.decode(c.path,c.inUs,c.outUs,c.speed,RATE,new File(work,c.id+".pcm"),control));
                control.progress("Preparing audio",70+(++done)*10/p.clips.size());}
            if(p.music!=null)music=ProAudio.decode(p.music.path,p.music.inUs,p.music.outUs,1,RATE,new File(work,"music.pcm"),control);
            MediaFormat f=MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC,RATE,2);f.setInteger(MediaFormat.KEY_AAC_PROFILE,MediaCodecInfo.CodecProfileLevel.AACObjectLC);f.setInteger(MediaFormat.KEY_BIT_RATE,128000);f.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE,16384);
            MediaCodec codec=MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC);
            try{codec.configure(f,null,null,MediaCodec.CONFIGURE_FLAG_ENCODE);codec.start();
                try(EncoderSink sink=new EncoderSink(codec,output)){
                    long total=ProTimeline.sampleAt(t.durationUs,RATE),position=0;boolean inputDone=false;float[] scratch=new float[4096*2],mix=new float[4096*2];
                    while(!sink.eos){control.check();if(!inputDone){int idx=codec.dequeueInputBuffer(10000);if(idx>=0){ByteBuffer input=codec.getInputBuffer(idx).order(ByteOrder.LITTLE_ENDIAN);input.clear();
                        int count=(int)Math.min(Math.min(4096,input.capacity()/4),total-position);Arrays.fill(mix,0);
                        if(count==0){codec.queueInputBuffer(idx,0,0,ProTimeline.timeAtSample(position,RATE),MediaCodec.BUFFER_FLAG_END_OF_STREAM);inputDone=true;}
                        else{
                            for(ProTimeline.Entry e:t.entries){ProAudio.Pcm data=pcm.get(e.id);long clipStart=ProTimeline.sampleAt(e.startUs,RATE),clipEnd=ProTimeline.sampleAt(e.endUs,RATE);
                                if(data==null||position>=clipEnd||position+count<=clipStart)continue;data.read(position-clipStart,count,scratch);ProProject.Clip c=p.clip(e.id);
                                for(int n=0;n<count;n++){long sample=position+n;if(sample<clipStart||sample>=clipEnd)continue;double gain=c.volume*t.audioWeight(e,ProTimeline.timeAtSample(sample,RATE));mix[n*2]+=scratch[n*2]*gain;mix[n*2+1]+=scratch[n*2+1]*gain;}}
                            if(music!=null){ProProject.Music m=p.music;long start=ProTimeline.sampleAt(m.startUs,RATE),length=ProTimeline.sampleAt(m.outUs-m.inUs,RATE);music.read(position-start,count,scratch);
                                for(int n=0;n<count;n++){long relative=position+n-start;if(relative<0||relative>=length)continue;long us=ProTimeline.timeAtSample(relative,RATE);
                                    double fade=m.fadeInUs<=0?1:Math.min(1,(double)us/m.fadeInUs);fade*=m.fadeOutUs<=0?1:Math.min(1,(double)(m.outUs-m.inUs-us)/m.fadeOutUs);
                                    mix[n*2]+=scratch[n*2]*m.volume*fade;mix[n*2+1]+=scratch[n*2+1]*m.volume*fade;}}
                            for(int n=0;n<count*2;n++)input.putShort((short)Math.round(Math.max(-1,Math.min(1,mix[n]))*32767));
                            codec.queueInputBuffer(idx,0,count*4,ProTimeline.timeAtSample(position,RATE),0);position+=count;
                            control.progress("Mixing sound",80+(int)(16L*position/Math.max(1,total)));}
                    }}sink.drain(inputDone,control);}
                }
            }finally{try{codec.stop();}catch(Exception ignored){}codec.release();}
        }finally{for(ProAudio.Pcm a:pcm.values())a.close();if(music!=null)music.close();}
    }
    private static void mux(File video,File audio,File output,ProAudio.Control control)throws Exception{
        MediaExtractor[] sources={new MediaExtractor(),new MediaExtractor()};MediaMuxer mux=new MediaMuxer(output.getPath(),MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);boolean started=false;
        try{int[] tracks=new int[2];for(int i=0;i<2;i++){sources[i].setDataSource((i==0?video:audio).getPath());sources[i].selectTrack(0);tracks[i]=mux.addTrack(sources[i].getTrackFormat(0));}mux.start();started=true;
            ByteBuffer buffer=ByteBuffer.allocateDirect(4*1024*1024);MediaCodec.BufferInfo info=new MediaCodec.BufferInfo();
            while(true){control.check();long v=sources[0].getSampleTime(),a=sources[1].getSampleTime();if(v<0&&a<0)break;int i=a<0||(v>=0&&v<=a)?0:1;buffer.clear();
                int n=sources[i].readSampleData(buffer,0);if(n<0){sources[i].advance();continue;}int flags=(sources[i].getSampleFlags()&MediaExtractor.SAMPLE_FLAG_SYNC)!=0?MediaCodec.BUFFER_FLAG_KEY_FRAME:0;info.set(0,n,sources[i].getSampleTime(),flags);mux.writeSampleData(tracks[i],buffer,info);sources[i].advance();}
        }finally{for(MediaExtractor s:sources)s.release();try{if(started)mux.stop();}finally{mux.release();}}
    }
}
