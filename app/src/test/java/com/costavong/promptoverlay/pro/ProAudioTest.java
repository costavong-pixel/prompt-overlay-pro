package com.costavong.promptoverlay.pro;

import androidx.media3.common.audio.SonicAudioProcessor;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import org.junit.Test;
import static org.junit.Assert.*;

/** Exercises the actual Media3 processor used by export, without an Android codec. */
@androidx.annotation.OptIn(markerClass=androidx.media3.common.util.UnstableApi.class)
public final class ProAudioTest {
    @Test public void normalSpeedInitializesEvenWhenProcessingIsBypassed() throws Exception {
        SonicAudioProcessor sonic=ProAudio.prepareProcessor(48000,2,1,48000);
        try { assertFalse(sonic.isActive()); } finally { sonic.reset(); }
    }

    @Test public void slowMotionPreservesPitchAndDoublesDuration() throws Exception {
        assertProcessedTone(48000,48000,0.5);
    }

    @Test public void doubleSpeedPreservesPitchAndHalvesDuration() throws Exception {
        assertProcessedTone(48000,48000,2);
    }

    @Test public void speedChangeAndResamplingPreservePitchAndDuration() throws Exception {
        assertProcessedTone(44100,48000,1.5);
    }

    private static void assertProcessedTone(int inputRate,int outputRate,double speed) throws Exception {
        SonicAudioProcessor sonic=ProAudio.prepareProcessor(inputRate,2,speed,outputRate);
        ByteArrayOutputStream output=new ByteArrayOutputStream();
        try {
            assertTrue(sonic.isActive());
            int inputFrames=inputRate*2;
            for(int start=0;start<inputFrames;start+=4096) {
                int count=Math.min(4096,inputFrames-start);
                ByteBuffer input=ByteBuffer.allocateDirect(count*4).order(ByteOrder.nativeOrder());
                for(int frame=0;frame<count;frame++) {
                    short sample=(short)(12000*Math.sin(2*Math.PI*440*(start+frame)/inputRate));
                    input.putShort(sample).putShort(sample);
                }
                input.flip();sonic.queueInput(input);drain(sonic,output);
                assertFalse(input.hasRemaining());
            }
            sonic.queueEndOfStream();
            for(int attempts=0;!sonic.isEnded()&&attempts<100;attempts++) drain(sonic,output);
            assertTrue("Processor must finish draining",sonic.isEnded());
            byte[] pcm=output.toByteArray();
            int frames=pcm.length/4;
            assertEquals("Output duration",2/speed,frames/(double)outputRate,0.03);
            // Count rising zero crossings away from the stream boundaries.
            ByteBuffer samples=ByteBuffer.wrap(pcm).order(ByteOrder.nativeOrder());
            int first=outputRate/5,last=frames-outputRate/5,crossings=0;
            short previous=samples.getShort(first*4);
            for(int frame=first+1;frame<last;frame++) {
                short current=samples.getShort(frame*4);
                if(previous<=0&&current>0)crossings++;
                previous=current;
            }
            assertEquals("Pitch must remain near 440 Hz",440,crossings*outputRate/(double)(last-first),10);
        } finally { sonic.reset(); }
    }

    private static void drain(SonicAudioProcessor sonic,ByteArrayOutputStream output) {
        ByteBuffer buffer=sonic.getOutput();byte[] bytes=new byte[buffer.remaining()];
        buffer.get(bytes);output.write(bytes,0,bytes.length);
    }
}
