package com.costavong.promptoverlay.pro;

/** Maps clipped source-player milliseconds to the project's speed-adjusted playhead. */
final class ProClipPlayback {
    final ProTimeline.Entry entry;
    final long startMs,endMs;
    ProClipPlayback(ProTimeline.Entry entry){
        this.entry=entry;
        startMs=entry.sourceStartUs/1000;
        endMs=Math.max(startMs+1,(entry.sourceEndUs+999)/1000);
    }
    long positionMs(long timelineUs){
        long time=Math.max(entry.startUs,Math.min(entry.endUs-1,timelineUs));
        return Math.max(0,Math.min(endMs-startMs-1,entry.sourceAt(time)/1000-startMs));
    }
    long timelineUs(long playerPositionMs){
        long position=Math.max(0,Math.min(endMs-startMs,playerPositionMs));
        long sourceUs=Math.max(entry.sourceStartUs,Math.min(entry.sourceEndUs-1,(startMs+position)*1000));
        return Math.max(entry.startUs,Math.min(entry.endUs-1,entry.mapSource(sourceUs)));
    }
}
