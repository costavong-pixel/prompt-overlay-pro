package com.costavong.promptoverlay.pro;

import org.junit.Test;
import static org.junit.Assert.*;

public final class ProClipPlaybackTest {
    @Test public void trimmedClipStartsAtZeroInThePlayer(){
        ProClipPlayback p=new ProClipPlayback(new ProTimeline.Entry("a",2_000_000,8_000_000,1,4_000_000,0));
        assertEquals(2000,p.startMs);assertEquals(8000,p.endMs);
        assertEquals(0,p.positionMs(4_000_000));assertEquals(4_000_000,p.timelineUs(0));
        assertEquals(1500,p.positionMs(5_500_000));assertEquals(5_500_000,p.timelineUs(1500));
    }
    @Test public void fasterPlaybackUsesSourcePositionWithoutApplyingSpeedTwice(){
        ProClipPlayback p=new ProClipPlayback(new ProTimeline.Entry("a",2_000_000,8_000_000,2,4_000_000,0));
        assertEquals(2000,p.positionMs(5_000_000));assertEquals(5_000_000,p.timelineUs(2000));
    }
    @Test public void slowerPlaybackMapsTheTimelineAndPlayerInBothDirections(){
        ProClipPlayback p=new ProClipPlayback(new ProTimeline.Entry("a",2_000_000,8_000_000,.5,4_000_000,0));
        assertEquals(1000,p.positionMs(6_000_000));assertEquals(6_000_000,p.timelineUs(1000));
    }
    @Test public void aSplitClipDoesNotSeekBackToTheBeginningOfTheOriginal(){
        ProTimeline t=new ProTimeline();t.append("first",0,3_000_000,1,0);t.append("second",3_000_000,6_000_000,1,0);
        ProClipPlayback p=new ProClipPlayback(t.byId("second"));
        assertEquals(3000,p.startMs);assertEquals(1000,p.positionMs(4_000_000));assertEquals(4_000_000,p.timelineUs(1000));
    }
    @Test public void crossfadeOverlapKeepsTheSelectedClipsTimelineOffset(){
        ProTimeline t=new ProTimeline();t.append("a",0,4_000_000,1,0);t.append("b",5_000_000,9_000_000,1,1_000_000);
        ProClipPlayback p=new ProClipPlayback(t.byId("b"));
        assertEquals(500,p.positionMs(3_500_000));assertEquals(3_500_000,p.timelineUs(500));
    }
    @Test public void seekingIsBoundedToTheSelectedTrimmedRange(){
        ProClipPlayback p=new ProClipPlayback(new ProTimeline.Entry("a",2_000_000,8_000_000,1,4_000_000,0));
        assertEquals(0,p.positionMs(-10));assertEquals(5999,p.positionMs(Long.MAX_VALUE));
        assertEquals(4_000_000,p.timelineUs(-10));assertEquals(9_999_999,p.timelineUs(Long.MAX_VALUE));
    }
    @Test public void fractionalMillisecondCutsRemainWithinTheTimeline(){
        ProClipPlayback p=new ProClipPlayback(new ProTimeline.Entry("a",2_000_125,8_000_625,1.5,4_000_000,0));
        assertEquals(2000,p.startMs);assertEquals(8001,p.endMs);
        assertEquals(p.entry.startUs,p.timelineUs(0));assertEquals(p.entry.endUs-1,p.timelineUs(Long.MAX_VALUE));
        for(long time=p.entry.startUs;time<p.entry.endUs;time+=11_111)
            assertTrue(Math.abs(time-p.timelineUs(p.positionMs(time)))<=667);
    }
}
