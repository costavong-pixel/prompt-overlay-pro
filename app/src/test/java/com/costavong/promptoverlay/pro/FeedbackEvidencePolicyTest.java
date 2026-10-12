package com.costavong.promptoverlay.pro;

import org.junit.Test;
import static org.junit.Assert.*;

public final class FeedbackEvidencePolicyTest {
    @Test public void onlyImagesAndVideosAccepted(){
        assertTrue(FeedbackEvidencePolicy.isImageOrVideo("image/jpeg"));
        assertTrue(FeedbackEvidencePolicy.isImageOrVideo("video/mp4"));
        assertFalse(FeedbackEvidencePolicy.isImageOrVideo("application/pdf"));
        assertFalse(FeedbackEvidencePolicy.isImageOrVideo(null));
    }
    @Test public void noMoreThanFourMediaFilesOrFifteenMiB(){
        long max=FeedbackEvidencePolicy.MAX_TOTAL_BYTES;
        assertTrue(FeedbackEvidencePolicy.canAttach(0,0,max));
        assertFalse(FeedbackEvidencePolicy.canAttach(1,1,max));
        assertFalse(FeedbackEvidencePolicy.canAttach(4,0,1));
        assertFalse(FeedbackEvidencePolicy.canAttach(0,0,0));
        assertFalse(FeedbackEvidencePolicy.canAttach(1,0,-20));
        assertFalse(FeedbackEvidencePolicy.canAttach(1,max,1));
    }
    @Test public void normalizeLinksAndRejectUnsafeOrigins(){
        assertEquals("",FeedbackEvidencePolicy.normalizeLink(" "));
        assertEquals("https://youtu.be/demo",FeedbackEvidencePolicy.normalizeLink("youtu.be/demo"));
        assertEquals("https://drive.google.com/file/d/1",FeedbackEvidencePolicy.normalizeLink("https://drive.google.com/file/d/1"));
        assertEquals("http://example.com/video",FeedbackEvidencePolicy.normalizeLink("http://example.com/video"));
        for(String link:new String[]{"javascript:alert(1)","file:///data/a.mp4",
                "ftp://example.org/file","https://alice:pwd@example.com/video","https://","http://good.com\nX-Mail: bad"}){
            try{FeedbackEvidencePolicy.normalizeLink(link);fail("Accepted "+link);}
            catch(IllegalArgumentException expected){}
        }
    }
}
