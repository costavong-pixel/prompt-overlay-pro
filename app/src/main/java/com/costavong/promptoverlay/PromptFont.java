package com.costavong.promptoverlay;

import android.graphics.Typeface;

/**
 * The small set of offline, readable font choices used by the editor and overlay.
 * These are Android system families, so the app stays private and works offline.
 */
public final class PromptFont {
    public static final String CLEAN = "clean";
    public static final String CLASSIC = "classic";
    public static final String MONO = "mono";
    public static final String COMPACT = "compact";

    private PromptFont() {
        // Utility class.
    }

    public static String normalize(String value) {
        if (CLASSIC.equals(value) || MONO.equals(value) || COMPACT.equals(value)) {
            return value;
        }
        return CLEAN;
    }

    public static Typeface typefaceFor(String value) {
        String font = normalize(value);
        if (CLASSIC.equals(font)) {
            return Typeface.create("serif", Typeface.NORMAL);
        }
        if (MONO.equals(font)) {
            return Typeface.create("monospace", Typeface.NORMAL);
        }
        if (COMPACT.equals(font)) {
            return Typeface.create("sans-serif-condensed", Typeface.NORMAL);
        }
        return Typeface.create("sans-serif", Typeface.NORMAL);
    }
}
