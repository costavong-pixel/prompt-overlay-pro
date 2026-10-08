package com.costavong.promptoverlay.pro;

import android.media.*;
import androidx.media3.common.C;
import androidx.media3.common.audio.AudioProcessor;
import androidx.media3.common.audio.SonicAudioProcessor;
import java.io.*;
import java.nio.*;

/** Stream decoded PCM to disk. Source timestamps preserve silence and trimmed A/V alignment. */
@androidx.annotation.OptIn(markerClass=androidx.media3.common.util.UnstableApi.class)
final class ProAudio {
    interface Control { void check() throws Exception; void progress(String stage,int percent); }
    static final class Pcm implements AutoCloseable {
        final File file; final int rate,channels; final long frames; private RandomAccessFile reader;
        Pcm(File f,int r,int c){file=f;rate=r;channels=c;frames=f.length()/(2*c);}
        void read(long start,int count,float[] stereo)throws IOException {
            java.util.Arrays.fill(stereo,0);if(start>=frames||start+count<=0)return;
            int skip=(int)Math.max(0,-start),n=(int)Math.min(count-skip,frames-Math.max(0,start));
            if(reader==null)reader=new RandomAccessFile(file,"r");reader.seek(Math.max(0,start)*channels*2);
            byte[] b=new byte[n*channels*2];reader.readFully(b);ByteBuffer bb=ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN);
            for(int i=0;i<n;i++){float left=bb.getShort()/32768f,right=left;
                if(channels>1)right=bb.getShort()/32768f;for(int k=2;k<channels;k++)bb.getShort();
                stereo[(i+skip)*2]=left;stereo[(i+skip)*2+1]=right;}
        }
        @Override public void close()throws IOException{if(reader!=null)reader.close();}
    }
    static Pcm decode(String path,long inUs,long outUs,double speed,int targetRate,File destination,Control control)throws Exception {
        MediaExtractor ex=new MediaExtractor();MediaCodec decoder=null;SonicAudioProcessor sonic=null;
        int channels=2,rate=targetRate;boolean active=false;long supplied=0;boolean configured=false;
        try(FileOutputStream output=new FileOutputStream(destination)) {
            ex.setDataSource(path);int track=-1;MediaFormat format=null;
            for(int i=0;i<ex.getTrackCount();i++)if(ex.getTrackFormat(i).getString(MediaFormat.KEY_MIME).startsWith("audio/")){track=i;format=ex.getTrackFormat(i);break;}
            if(track<0)return new Pcm(destination,targetRate,2);
            ex.selectTrack(track);ex.seekTo(inUs,MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
            format.setInteger(MediaFormat.KEY_PCM_ENCODING,AudioFormat.ENCODING_PCM_16BIT);
            decoder=MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME));decoder.configure(format,null,null,0);decoder.start();
            MediaCodec.BufferInfo info=new MediaCodec.BufferInfo();boolean inputDone=false,outputDone=false;long lastActivity=System.nanoTime();
            while(!outputDone){control.check();boolean moved=false;
                if(!inputDone){int idx=decoder.dequeueInputBuffer(10000);if(idx>=0){ByteBuffer input=decoder.getInputBuffer(idx);long pts=ex.getSampleTime();
                    int size=pts<0||pts>=outUs?-1:ex.readSampleData(input,0);
                    if(size<0){decoder.queueInputBuffer(idx,0,0,0,MediaCodec.BUFFER_FLAG_END_OF_STREAM);inputDone=true;}
                    else{decoder.queueInputBuffer(idx,0,size,pts,0);ex.advance();}moved=true;}}
                int idx=decoder.dequeueOutputBuffer(info,10000);
                if(idx==MediaCodec.INFO_OUTPUT_FORMAT_CHANGED){MediaFormat f=decoder.getOutputFormat();rate=f.getInteger(MediaFormat.KEY_SAMPLE_RATE);channels=f.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
                    if(channels<1||channels>8)throw new IOException("Unsupported audio channel layout.");
                    sonic=prepareProcessor(rate,channels,speed,targetRate);active=sonic.isActive();configured=true;moved=true;
                }else if(idx>=0){moved=true;
                    if(info.size>0&&(info.flags&MediaCodec.BUFFER_FLAG_CODEC_CONFIG)==0){
                        if(!configured)throw new IOException("Audio decoder did not supply a format.");
                        MediaFormat f=decoder.getOutputFormat();int encoding=f.containsKey(MediaFormat.KEY_PCM_ENCODING)?f.getInteger(MediaFormat.KEY_PCM_ENCODING):AudioFormat.ENCODING_PCM_16BIT;
                        if(encoding!=AudioFormat.ENCODING_PCM_16BIT&&encoding!=AudioFormat.ENCODING_PCM_FLOAT)throw new IOException("Unsupported decoded audio format.");
                        int bytesPerSample=encoding==AudioFormat.ENCODING_PCM_FLOAT?4:2;
                        int frames=info.size/(channels*bytesPerSample);
                        long begin=Math.round((info.presentationTimeUs-inUs)*rate/1_000_000.0);
                        int first=(int)Math.max(0,-begin),last=(int)Math.min(frames,Math.ceil((outUs-info.presentationTimeUs)*rate/1_000_000.0));
                        long wanted=begin+first;first+=(int)Math.max(0,supplied-wanted);wanted=begin+first;
                        if(last>first){
                            while(supplied<wanted){control.check();int gap=(int)Math.min(4096,wanted-supplied);feed(ByteBuffer.allocateDirect(gap*channels*2).order(ByteOrder.nativeOrder()),sonic,active,output);supplied+=gap;}
                            ByteBuffer source=decoder.getOutputBuffer(idx).duplicate().order(ByteOrder.nativeOrder());source.position(info.offset+first*channels*bytesPerSample);source.limit(info.offset+last*channels*bytesPerSample);
                            ByteBuffer pcm=ByteBuffer.allocateDirect((last-first)*channels*2).order(ByteOrder.nativeOrder());
                            while(source.hasRemaining()){short sample=encoding==AudioFormat.ENCODING_PCM_FLOAT?(short)Math.round(Math.max(-1,Math.min(1,source.getFloat()))*32767):source.getShort();pcm.putShort(sample);}pcm.flip();
                            feed(pcm,sonic,active,output);supplied=begin+last;
                        }
                    }
                    outputDone=(info.flags&MediaCodec.BUFFER_FLAG_END_OF_STREAM)!=0;decoder.releaseOutputBuffer(idx,false);
                }
                if(moved)lastActivity=System.nanoTime();else if(System.nanoTime()-lastActivity>30_000_000_000L)throw new IOException("Audio decoder stopped responding.");
            }
            if(configured){long target=Math.round((outUs-inUs)*rate/1_000_000.0);
                while(supplied<target){control.check();int count=(int)Math.min(4096,target-supplied);feed(ByteBuffer.allocateDirect(count*channels*2),sonic,active,output);supplied+=count;}
                if(active){sonic.queueEndOfStream();while(!sonic.isEnded()){control.check();write(sonic.getOutput(),output);}}}
        }finally{ex.release();if(decoder!=null){try{decoder.stop();}catch(Exception ignored){}decoder.release();}if(sonic!=null)sonic.reset();}
        return new Pcm(destination,targetRate,channels);
    }
    static SonicAudioProcessor prepareProcessor(int rate,int channels,double speed,int targetRate)throws AudioProcessor.UnhandledAudioFormatException {
        SonicAudioProcessor sonic=new SonicAudioProcessor();
        sonic.setSpeed((float)speed);sonic.setPitch(1);sonic.setOutputSampleRateHz(targetRate);
        sonic.configure(new AudioProcessor.AudioFormat(rate,channels,C.ENCODING_PCM_16BIT));
        // Offline PCM starts at zero. Media3 1.11.1 requires stream metadata;
        // its deprecated no-argument flush throws before audio can be rendered.
        sonic.flush(AudioProcessor.StreamMetadata.DEFAULT);
        return sonic;
    }
    private static void feed(ByteBuffer input,SonicAudioProcessor sonic,boolean active,OutputStream output)throws IOException{
        if(active){sonic.queueInput(input);write(sonic.getOutput(),output);}else write(input,output);
    }
    private static void write(ByteBuffer b,OutputStream output)throws IOException{byte[] bytes=new byte[Math.min(32768,b.remaining())];while(b.hasRemaining()){int n=Math.min(bytes.length,b.remaining());b.get(bytes,0,n);output.write(bytes,0,n);}}
}
