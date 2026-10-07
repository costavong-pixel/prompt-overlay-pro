package com.costavong.promptoverlay.pro;

import java.util.ArrayList;
import java.util.List;

/** Pure timeline math shared by preview, rendering, captions and PCM mixing. */
public final class ProTimeline {
    public static final class Entry {
        public final String id;
        public final long sourceStartUs, sourceEndUs, startUs, endUs, overlapUs;
        public final double speed;
        public Entry(String id, long inUs, long outUs, double speed, long startUs, long overlapUs) {
            if (inUs < 0 || outUs <= inUs || !Double.isFinite(speed) || speed <= 0)
                throw new IllegalArgumentException("Invalid clip timing");
            this.id=id; sourceStartUs=inUs; sourceEndUs=outUs; this.speed=speed;
            this.startUs=startUs; this.overlapUs=overlapUs;
            endUs=startUs+Math.max(1,Math.round((outUs-inUs)/speed));
        }
        public long sourceAt(long timelineUs) {
            return Math.min(sourceEndUs-1,Math.max(sourceStartUs,
                    sourceStartUs+Math.round((timelineUs-startUs)*speed)));
        }
        public long mapSource(long sourceUs) { return startUs+Math.round((sourceUs-sourceStartUs)/speed); }
        public long durationUs() { return endUs-startUs; }
    }
    public final List<Entry> entries=new ArrayList<>();
    public long durationUs;
    public void append(String id, long inUs, long outUs, double speed, long requestedCrossfadeUs) {
        long duration=Math.max(1,Math.round((outUs-inUs)/speed));
        long overlap=entries.isEmpty()?0:Math.max(0,Math.min(requestedCrossfadeUs,
                Math.min(duration/2, entries.get(entries.size()-1).durationUs()/2)));
        Entry e=new Entry(id,inUs,outUs,speed,durationUs-overlap,overlap);
        entries.add(e); durationUs=e.endUs;
    }
    public Entry byId(String id) {
        for (Entry e:entries) if(e.id.equals(id))return e;
        return null;
    }
    public List<Entry> at(long timeUs) {
        ArrayList<Entry> result=new ArrayList<>(2);
        for(Entry e:entries)if(timeUs>=e.startUs&&timeUs<e.endUs)result.add(e);
        return result;
    }
    public double incomingWeight(Entry e,long timeUs) {
        return e.overlapUs==0?1:Math.min(1,Math.max(0,(double)(timeUs-e.startUs)/e.overlapUs));
    }
    public double audioWeight(Entry e,long timeUs) {
        double gain=1;
        if(e.overlapUs>0&&timeUs<e.startUs+e.overlapUs)
            gain*=Math.sin(Math.PI/2*incomingWeight(e,timeUs));
        int index=entries.indexOf(e);
        if(index+1<entries.size()) {
            Entry next=entries.get(index+1);
            if(next.overlapUs>0&&timeUs>=next.startUs)
                gain*=Math.cos(Math.PI/2*incomingWeight(next,timeUs));
        }
        return gain;
    }
    public static long sampleAt(long timeUs,int sampleRate) {
        return Math.round(timeUs*(sampleRate/1_000_000.0));
    }
    public static long timeAtSample(long sample,int sampleRate) {
        return Math.round(sample*(1_000_000.0/sampleRate));
    }
    public static long frameTime(int frame,int fps) { return frame*1_000_000L/fps; }
}
