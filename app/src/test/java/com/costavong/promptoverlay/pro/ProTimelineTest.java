package com.costavong.promptoverlay.pro;
import org.junit.Test;
import static org.junit.Assert.*;
public class ProTimelineTest {
 @Test public void trimsAndNaturalSpeedShareSourceTimestamps(){ProTimeline t=new ProTimeline();t.append("a",2_000_000,8_000_000,2,0);
  assertEquals(3_000_000,t.durationUs);assertEquals(4_000_000,t.entries.get(0).sourceAt(1_000_000));assertEquals(1_000_000,t.entries.get(0).mapSource(4_000_000));}
 @Test public void crossfadeShortensTimelineAndBoundsOverlap(){ProTimeline t=new ProTimeline();t.append("a",0,4_000_000,1,0);t.append("b",0,2_000_000,1,10_000_000);
  assertEquals(5_000_000,t.durationUs);assertEquals(1_000_000,t.entries.get(1).overlapUs);assertEquals(2,t.at(3_500_000).size());assertEquals(.5,t.incomingWeight(t.entries.get(1),3_500_000),1e-9);}
 @Test public void sharedFrameAndSampleClockHasSubsampleError(){for(int i=0;i<100000;i++){long us=ProTimeline.frameTime(i,30);
  assertTrue(Math.abs(us-ProTimeline.timeAtSample(ProTimeline.sampleAt(us,48000),48000))<=11);}}
 @Test public void equalPowerCrossfadeDoesNotMuteAtMidpoint(){ProTimeline t=new ProTimeline();t.append("a",0,4_000_000,1,0);t.append("b",0,4_000_000,1,1_000_000);
  double a=t.audioWeight(t.entries.get(0),3_500_000),b=t.audioWeight(t.entries.get(1),3_500_000);assertEquals(1,a*a+b*b,1e-9);}
 @Test public void halfSpeedRecalculatesAttachedCaptionTime(){ProTimeline t=new ProTimeline();t.append("a",1_000_000,5_000_000,.5,0);
  assertEquals(8_000_000,t.durationUs);assertEquals(4_000_000,t.entries.get(0).mapSource(3_000_000));}
 @Test(expected=IllegalArgumentException.class)public void invalidTrimIsRejected(){new ProTimeline.Entry("a",2,1,1,0,0);}
}
