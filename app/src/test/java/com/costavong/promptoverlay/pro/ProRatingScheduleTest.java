package com.costavong.promptoverlay.pro;

import org.junit.Test;
import static org.junit.Assert.*;

public final class ProRatingScheduleTest {
    private static final long DAY=ProRatingSchedule.DAY_MS;
    private static final long START=10*DAY;
    private boolean ask(long now,long first,int sessions,int exports,boolean engaged,
                        boolean reviewed,long prompted,int sessionAtPrompt,long visited){
        return ProRatingSchedule.eligible(now,first,sessions,exports,engaged,reviewed,
                prompted,sessionAtPrompt,visited);
    }
    @Test public void neverAtFirstLaunchOrBeforeEngagement(){
        assertFalse(ask(START,START,1,0,false,false,0,0,0));
        assertFalse(ask(START,START,1,0,true,false,0,0,0));
    }
    @Test public void weekAndThreeSessionsAfterEngagement(){
        assertFalse(ask(START+6*DAY,START,3,0,true,false,0,0,0));
        assertFalse(ask(START+7*DAY,START,2,0,true,false,0,0,0));
        assertTrue(ask(START+7*DAY,START,3,0,true,false,0,0,0));
    }
    @Test public void twoSuccessfulExportsAcrossSessionsQualifyEarly(){
        assertFalse(ask(START+DAY,START,1,2,true,false,0,0,0));
        assertTrue(ask(START+DAY,START,2,2,true,false,0,0,0));
    }
    @Test public void nextPromptNeedsSixtyDaysAndTwoMoreSessions(){
        long prompt=START+7*DAY;
        assertFalse(ask(prompt+59*DAY,START,6,2,true,false,prompt,3,0));
        assertFalse(ask(prompt+60*DAY,START,4,2,true,false,prompt,3,0));
        assertTrue(ask(prompt+60*DAY,START,5,2,true,false,prompt,3,0));
    }
    @Test public void openingStoreStopsPromptsForNinetyDays(){
        long visit=START+8*DAY;
        assertFalse(ask(visit+89*DAY,START,8,2,true,false,0,0,visit));
        assertTrue(ask(visit+90*DAY,START,8,2,true,false,0,0,visit));
    }
    @Test public void selfReportedReviewAndFutureClocksNeverPrompt(){
        assertFalse(ask(START+100*DAY,START,20,5,true,true,0,0,0));
        assertFalse(ask(START,START+DAY,20,5,true,false,0,0,0));
        assertFalse(ask(START+100*DAY,START,20,5,true,false,START+110*DAY,2,0));
    }
}
