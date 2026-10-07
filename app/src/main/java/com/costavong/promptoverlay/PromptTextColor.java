package com.costavong.promptoverlay;

import android.graphics.Color;

/** Offline reading colours shared by the editor and floating prompt. */
public final class PromptTextColor {
    public static final String WHITE = "white";
    public static final String WARM_WHITE = "warm_white";
    public static final String YELLOW = "yellow";
    public static final String MINT = "mint";
    public static final String CYAN = "cyan";
    public static final String PINK = "pink";

    private PromptTextColor() {
    }

    public static String[] labels() {
        return new String[]{"White", "Warm white", "Yellow", "Mint", "Cyan", "Pink"};
    }

    public static String normalize(String value) {
        if (WARM_WHITE.equals(value) || YELLOW.equals(value) || MINT.equals(value)
                || CYAN.equals(value) || PINK.equals(value)) {
            return value;
        }
        return WHITE;
    }

    public static int colorFor(String value) {
        String color = normalize(value);
        if (WARM_WHITE.equals(color)) {
            return Color.rgb(255, 246, 220);
        }
        if (YELLOW.equals(color)) {
            return Color.rgb(255, 226, 92);
        }
        if (MINT.equals(color)) {
            return Color.rgb(151, 242, 198);
        }
        if (CYAN.equals(color)) {
            return Color.rgb(130, 228, 255);
        }
        if (PINK.equals(color)) {
            return Color.rgb(255, 180, 219);
        }
        return Color.WHITE;
    }

    public static String valueAt(int index) {
        switch (index) {
            case 1: return WARM_WHITE;
            case 2: return YELLOW;
            case 3: return MINT;
            case 4: return CYAN;
            case 5: return PINK;
            default: return WHITE;
        }
    }

    public static int indexOf(String value) {
        String normalized = normalize(value);
        if (WARM_WHITE.equals(normalized)) return 1;
        if (YELLOW.equals(normalized)) return 2;
        if (MINT.equals(normalized)) return 3;
        if (CYAN.equals(normalized)) return 4;
        if (PINK.equals(normalized)) return 5;
        return 0;
    }
}
