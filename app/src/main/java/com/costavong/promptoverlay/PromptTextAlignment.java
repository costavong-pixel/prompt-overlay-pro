package com.costavong.promptoverlay;

import android.view.Gravity;

/** Small, local reading-alignment choices shared by floating and mirror modes. */
public final class PromptTextAlignment {
    public static final String LEFT = "left";
    public static final String CENTER = "center";
    public static final String RIGHT = "right";

    private PromptTextAlignment() {
    }

    public static String normalize(String value) {
        if (CENTER.equals(value)) {
            return CENTER;
        }
        if (RIGHT.equals(value)) {
            return RIGHT;
        }
        return LEFT;
    }

    public static String[] labels() {
        return new String[]{"Left", "Centre", "Right"};
    }

    public static int indexOf(String value) {
        String normalized = normalize(value);
        if (CENTER.equals(normalized)) {
            return 1;
        }
        if (RIGHT.equals(normalized)) {
            return 2;
        }
        return 0;
    }

    public static String valueAt(int index) {
        if (index == 1) {
            return CENTER;
        }
        if (index == 2) {
            return RIGHT;
        }
        return LEFT;
    }

    public static int gravityFor(String value) {
        String normalized = normalize(value);
        if (CENTER.equals(normalized)) {
            return Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        }
        if (RIGHT.equals(normalized)) {
            return Gravity.TOP | Gravity.END;
        }
        return Gravity.TOP | Gravity.START;
    }
}
