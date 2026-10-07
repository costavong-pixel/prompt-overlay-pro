package com.costavong.promptoverlay;

import android.content.SharedPreferences;
import android.view.KeyEvent;

/**
 * Maps standard hardware-key events to teleprompter actions. Bluetooth and USB
 * remotes normally identify themselves to Android as a keyboard, presentation
 * clicker, foot pedal, media controller, or gamepad; they do not need a custom
 * BLE connection in this app.
 */
public final class RemoteControlProfile {
    public static final String ACTION_TOGGLE = "toggle";
    public static final String ACTION_FASTER = "faster";
    public static final String ACTION_SLOWER = "slower";
    public static final String ACTION_FORWARD = "forward";
    public static final String ACTION_BACK = "back";
    public static final String ACTION_REPLAY = "replay";
    public static final String ACTION_CLOSE = "close";

    public static final String[] ACTIONS = {
            ACTION_TOGGLE,
            ACTION_FASTER,
            ACTION_SLOWER,
            ACTION_FORWARD,
            ACTION_BACK,
            ACTION_REPLAY,
            ACTION_CLOSE
    };

    private static final String PREF_PREFIX = "remote_key_";
    private static final int NO_KEY = -1;

    private RemoteControlProfile() {
    }

    public static String actionForKey(SharedPreferences preferences, int keyCode) {
        for (String action : ACTIONS) {
            if (preferences.getInt(preferenceName(action), NO_KEY) == keyCode) {
                return action;
            }
        }
        return defaultActionForKey(keyCode);
    }

    public static void setKey(SharedPreferences preferences, String action, int keyCode) {
        SharedPreferences.Editor editor = preferences.edit();
        for (String candidate : ACTIONS) {
            if (candidate.equals(action)) {
                continue;
            }
            if (preferences.getInt(preferenceName(candidate), NO_KEY) == keyCode) {
                editor.remove(preferenceName(candidate));
            }
        }
        editor.putInt(preferenceName(action), keyCode).apply();
    }

    public static void clearCustomKeys(SharedPreferences preferences) {
        SharedPreferences.Editor editor = preferences.edit();
        for (String action : ACTIONS) {
            editor.remove(preferenceName(action));
        }
        editor.apply();
    }

    public static int customKeyFor(SharedPreferences preferences, String action) {
        return preferences.getInt(preferenceName(action), NO_KEY);
    }

    public static String actionLabel(String action) {
        switch (action) {
            case ACTION_TOGGLE:
                return "Start / pause";
            case ACTION_FASTER:
                return "Faster";
            case ACTION_SLOWER:
                return "Slower";
            case ACTION_FORWARD:
                return "Move forward";
            case ACTION_BACK:
                return "Move back";
            case ACTION_REPLAY:
                return "Back to top";
            case ACTION_CLOSE:
                return "Close prompt";
            default:
                return action;
        }
    }

    public static String defaultHint(String action) {
        switch (action) {
            case ACTION_TOGGLE:
                return "Play/Pause, Space, Enter, centre button";
            case ACTION_FASTER:
                return "Fast-forward or gamepad right shoulder";
            case ACTION_SLOWER:
                return "Rewind or gamepad left shoulder";
            case ACTION_FORWARD:
                return "Page Down, down arrow, or gamepad B";
            case ACTION_BACK:
                return "Page Up, up arrow, or gamepad X";
            case ACTION_REPLAY:
                return "Media Stop or gamepad Y";
            case ACTION_CLOSE:
                return "No default — set one if wanted";
            default:
                return "No default";
        }
    }

    public static String keyLabel(int keyCode) {
        if (keyCode == NO_KEY) {
            return "";
        }
        return KeyEvent.keyCodeToString(keyCode)
                .replace("KEYCODE_", "")
                .replace('_', ' ');
    }

    public static boolean canAssign(KeyEvent event) {
        if (event.getAction() != KeyEvent.ACTION_DOWN
                || event.getRepeatCount() != 0
                || (event.getFlags() & KeyEvent.FLAG_SOFT_KEYBOARD) != 0) {
            return false;
        }
        switch (event.getKeyCode()) {
            case KeyEvent.KEYCODE_BACK:
            case KeyEvent.KEYCODE_HOME:
            case KeyEvent.KEYCODE_POWER:
            case KeyEvent.KEYCODE_VOLUME_UP:
            case KeyEvent.KEYCODE_VOLUME_DOWN:
            case KeyEvent.KEYCODE_VOLUME_MUTE:
                return false;
            default:
                return true;
        }
    }

    public static boolean isRemotePress(KeyEvent event) {
        return event.getAction() == KeyEvent.ACTION_DOWN
                && event.getRepeatCount() == 0
                && (event.getFlags() & KeyEvent.FLAG_SOFT_KEYBOARD) == 0;
    }

    private static String preferenceName(String action) {
        return PREF_PREFIX + action;
    }

    private static String defaultActionForKey(int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
            case KeyEvent.KEYCODE_MEDIA_PLAY:
            case KeyEvent.KEYCODE_MEDIA_PAUSE:
            case KeyEvent.KEYCODE_SPACE:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_BUTTON_A:
            case KeyEvent.KEYCODE_BUTTON_START:
                return ACTION_TOGGLE;

            case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
            case KeyEvent.KEYCODE_BUTTON_R1:
            case KeyEvent.KEYCODE_BUTTON_R2:
                return ACTION_FASTER;

            case KeyEvent.KEYCODE_MEDIA_REWIND:
            case KeyEvent.KEYCODE_BUTTON_L1:
            case KeyEvent.KEYCODE_BUTTON_L2:
                return ACTION_SLOWER;

            case KeyEvent.KEYCODE_PAGE_DOWN:
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_BUTTON_B:
                return ACTION_FORWARD;

            case KeyEvent.KEYCODE_PAGE_UP:
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_BUTTON_X:
                return ACTION_BACK;

            case KeyEvent.KEYCODE_MEDIA_STOP:
            case KeyEvent.KEYCODE_BUTTON_Y:
                return ACTION_REPLAY;

            default:
                return null;
        }
    }
}
