package com.costavong.promptoverlay;

import android.content.SharedPreferences;

/**
 * Access compatibility for the one-time paid Google Play app.
 * The purchase happens in Play before installation; there is no in-app trial.
 */
public final class TrialAccess {
    public static final long TRIAL_DURATION_MILLIS = 15L * 60L * 1000L;

    private static final boolean TEST_BUILD = BuildConfig.DEBUG;

    private static final String KEY_ACTIVE_TRIAL_MILLIS = "active_trial_millis";
    private static final String KEY_FOUNDER_UNLOCKED = "founder_unlocked";

    private TrialAccess() {
    }

    public static boolean isTestBuild() {
        return TEST_BUILD;
    }

    public static boolean isUnlocked(SharedPreferences preferences) {
        return true;
    }

    public static void setUnlocked(SharedPreferences preferences, boolean unlocked) {
        preferences.edit().putBoolean(KEY_FOUNDER_UNLOCKED, unlocked).apply();
    }

    public static boolean canUsePrompt(SharedPreferences preferences) {
        return true;
    }

    public static long remainingMillis(SharedPreferences preferences) {
        if (TEST_BUILD || isUnlocked(preferences)) {
            return Long.MAX_VALUE;
        }
        long activeMillis = Math.max(0L, preferences.getLong(KEY_ACTIVE_TRIAL_MILLIS, 0L));
        return Math.max(0L, TRIAL_DURATION_MILLIS - activeMillis);
    }

    /**
     * Records a bounded slice of actual scrolling time and returns the time
     * remaining. Calling this after the trial is unlocked is harmless.
     */
    public static long consumeActiveMillis(SharedPreferences preferences, long elapsedMillis) {
        if (TEST_BUILD || isUnlocked(preferences)) {
            return Long.MAX_VALUE;
        }

        long safeElapsed = Math.max(0L, elapsedMillis);
        long current = Math.max(0L, preferences.getLong(KEY_ACTIVE_TRIAL_MILLIS, 0L));
        long updated = Math.min(TRIAL_DURATION_MILLIS, current + safeElapsed);
        if (updated != current) {
            preferences.edit().putLong(KEY_ACTIVE_TRIAL_MILLIS, updated).apply();
        }
        return Math.max(0L, TRIAL_DURATION_MILLIS - updated);
    }

    public static String formatRemaining(long remainingMillis) {
        long totalSeconds = Math.max(0L, (remainingMillis + 999L) / 1000L);
        long minutes = totalSeconds / 60L;
        long seconds = totalSeconds % 60L;
        return String.format(java.util.Locale.US, "%d:%02d", minutes, seconds);
    }
}
