package com.costavong.promptoverlay.pro;

/**
 * Local-only review reminder timing. We cannot query Google Play for whether a
 * particular person has reviewed the app, so "already reviewed" is user-set.
 */
final class ProRatingSchedule {
    static final long DAY_MS=24L*60*60*1000;
    static final long FIRST_WAIT_MS=7L*DAY_MS;
    static final long REMINDER_WAIT_MS=60L*DAY_MS;
    static final long STORE_VISIT_WAIT_MS=90L*DAY_MS;

    private ProRatingSchedule(){}

    static boolean eligible(long now,long firstSeen,int sessions,int exports,
                            boolean engaged,boolean alreadyReviewed,
                            long lastPrompt,int sessionsAtLastPrompt,long lastStoreVisit){
        if(alreadyReviewed||!engaged||firstSeen<=0||now<firstSeen)return false;
        if(lastStoreVisit>0&&(now<lastStoreVisit||now-lastStoreVisit<STORE_VISIT_WAIT_MS))return false;
        if(lastPrompt>0){
            if(now<lastPrompt||now-lastPrompt<REMINDER_WAIT_MS)return false;
            return sessions-sessionsAtLastPrompt>=2;
        }
        // Don't interrupt first-time users. Ask after meaningful experience,
        // or after sustained use even if no completed exports.
        return (exports>=2&&sessions>=2)
            || (now-firstSeen>=FIRST_WAIT_MS&&sessions>=3);
    }
}
