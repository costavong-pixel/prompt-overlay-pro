package com.costavong.promptoverlay;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.IOException;

/**
 * Full-screen reader for a physical beam-splitter teleprompter. Only the script is
 * flipped; controls stay readable on the device while the glass restores the script.
 */
public class MirrorPrompterActivity extends Activity {
    private static final int MIN_SPEED = 10;
    private static final int MAX_SPEED = 180;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private ScrollView promptScroll;
    private TextView promptText;
    private TextView countdownText;
    private TextView speedText;
    private TextView browserRemoteText;
    private Button pauseButton;
    private SharedPreferences preferences;
    private MediaSession mediaSession;
    private LocalBrowserRemoteServer browserRemoteServer;
    private int speed = 45;
    private int countdownSeconds = 3;
    private boolean repeatPrompt = true;
    private boolean scrolling;
    private int countdownStep;
    private long lastScrollTime;
    private float partialPixels;

    private final Runnable countdownRunnable = new Runnable() {
        @Override
        public void run() {
            if (countdownStep > 1) {
                countdownStep--;
                countdownText.setText(AppLanguage.t(MirrorPrompterActivity.this, "Starts in")
                        + " " + countdownStep);
                handler.postDelayed(this, 1000);
                return;
            }
            countdownText.setVisibility(View.GONE);
            startScrolling();
        }
    };

    private final Runnable scrollRunnable = new Runnable() {
        @Override
        public void run() {
            if (!scrolling || promptScroll == null || promptText == null) {
                return;
            }
            long now = SystemClock.uptimeMillis();
            long elapsed = Math.max(1, Math.min(1000, now - lastScrollTime));
            lastScrollTime = now;
            float movement = speed * elapsed / 1000f + partialPixels;
            int wholePixels = (int) movement;
            partialPixels = movement - wholePixels;
            if (wholePixels > 0) {
                int maximumScroll = Math.max(0, promptText.getHeight() - promptScroll.getHeight());
                if (maximumScroll <= 0) {
                    scrolling = false;
                    pauseButton.setText(AppLanguage.t(MirrorPrompterActivity.this, "Replay"));
                    updateRemoteMediaState();
                    return;
                }
                int nextScroll = Math.min(maximumScroll, promptScroll.getScrollY() + wholePixels);
                promptScroll.scrollTo(0, nextScroll);
                if (nextScroll >= maximumScroll) {
                    if (repeatPrompt) {
                        promptScroll.scrollTo(0, 0);
                        partialPixels = 0f;
                    } else {
                        scrolling = false;
                        pauseButton.setText(AppLanguage.t(MirrorPrompterActivity.this, "Replay"));
                        updateRemoteMediaState();
                        return;
                    }
                }
            }
            handler.postDelayed(this, 16);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        preferences = getSharedPreferences("prompt_overlay", MODE_PRIVATE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(createScreen());
        // On Android 16, a WindowInsetsController may not exist until the
        // activity's decor view has been attached. Posting this work avoids
        // opening the mirrored reader before that point.
        requestHideSystemBars();
        createRemoteMediaSession();
        startBrowserRemoteIfEnabled();
    }

    @Override
    protected void onResume() {
        super.onResume();
        requestHideSystemBars();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            requestHideSystemBars();
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (handleHardwareRemoteKey(event)) {
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        stopBrowserRemote();
        releaseRemoteMediaSession();
        super.onDestroy();
    }

    private View createScreen() {
        String storedScript = getIntent().getStringExtra(TeleprompterOverlayService.EXTRA_STYLED_SCRIPT);
        CharSequence script = storedScript == null
                ? getIntent().getStringExtra(TeleprompterOverlayService.EXTRA_SCRIPT)
                : ScriptFormatting.fromStoredText(storedScript);
        if (script == null || script.toString().trim().isEmpty()) {
            script = AppLanguage.t(this, "No script yet. Return and paste your words.");
        }

        speed = clamp(getIntent().getIntExtra(TeleprompterOverlayService.EXTRA_SPEED, 45), MIN_SPEED, MAX_SPEED);
        countdownSeconds = clamp(getIntent().getIntExtra(TeleprompterOverlayService.EXTRA_START_DELAY, 3), 0, 10);
        repeatPrompt = getIntent().getBooleanExtra(TeleprompterOverlayService.EXTRA_REPEAT, true);
        int textSize = clamp(getIntent().getIntExtra(TeleprompterOverlayService.EXTRA_TEXT_SIZE, 32), 18, 72);
        int backgroundOpacity = clamp(getIntent().getIntExtra(
                TeleprompterOverlayService.EXTRA_BACKGROUND_OPACITY, 56), 0, 88);
        int sideMarginPercent = clamp(getIntent().getIntExtra(
                TeleprompterOverlayService.EXTRA_SIDE_MARGIN, 5), 0, 20);
        int lineSpacingPercent = clamp(getIntent().getIntExtra(
                TeleprompterOverlayService.EXTRA_LINE_SPACING, 125), 100, 200);
        String alignment = PromptTextAlignment.normalize(getIntent().getStringExtra(
                TeleprompterOverlayService.EXTRA_TEXT_ALIGNMENT));
        String font = PromptFont.normalize(getIntent().getStringExtra(TeleprompterOverlayService.EXTRA_FONT));
        String textColor = PromptTextColor.normalize(getIntent().getStringExtra(
                TeleprompterOverlayService.EXTRA_TEXT_COLOR));

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.argb(Math.round(backgroundOpacity * 2.55f), 0, 0, 0));
        root.setLayoutDirection(AppLanguage.layoutDirection(this));

        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(dp(12), dp(12), dp(12), dp(12));
        root.addView(column, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        TextView hint = new TextView(this);
        hint.setText(AppLanguage.t(this,
                "Mirror full-screen  •  Script is reversed  •  Hardware remote ready"));
        hint.setTextColor(Color.rgb(214, 224, 245));
        hint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(dp(8), dp(8), dp(8), dp(8));
        hint.setBackground(controlBackground());
        column.addView(hint, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(40)));

        browserRemoteText = new TextView(this);
        browserRemoteText.setTextColor(Color.rgb(218, 228, 248));
        browserRemoteText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        browserRemoteText.setGravity(Gravity.CENTER);
        browserRemoteText.setPadding(dp(8), dp(5), dp(8), dp(5));
        browserRemoteText.setBackground(controlBackground());
        browserRemoteText.setVisibility(View.GONE);
        column.addView(browserRemoteText, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        promptScroll = new ScrollView(this);
        promptScroll.setFillViewport(true);
        promptScroll.setVerticalScrollBarEnabled(false);
        promptScroll.setClipToPadding(true);

        promptText = new TextView(this);
        promptText.setText(script);
        promptText.setTextColor(PromptTextColor.colorFor(textColor));
        promptText.setTypeface(PromptFont.typefaceFor(font));
        promptText.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSize);
        promptText.setLineSpacing(0, lineSpacingPercent / 100f);
        promptText.setGravity(PromptTextAlignment.gravityFor(alignment));
        promptText.setScaleX(-1f);
        promptText.setPadding(dp(10), dp(16), dp(10), dp(28));
        promptScroll.addView(promptText, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));
        promptScroll.addOnLayoutChangeListener((view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) ->
                applySideMargins(sideMarginPercent));
        LinearLayout.LayoutParams scriptParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        scriptParams.topMargin = dp(6);
        scriptParams.bottomMargin = dp(6);
        column.addView(promptScroll, scriptParams);

        LinearLayout controls = new LinearLayout(this);
        controls.setGravity(Gravity.CENTER_VERTICAL);
        controls.setPadding(dp(4), dp(4), dp(4), dp(4));
        controls.setBackground(controlBackground());

        Button topButton = controlButton("Top");
        topButton.setOnClickListener(view -> {
            promptScroll.scrollTo(0, 0);
            partialPixels = 0f;
        });
        controls.addView(topButton, controlParams(0.9f));

        pauseButton = controlButton("Pause");
        pauseButton.setOnClickListener(view -> togglePause());
        controls.addView(pauseButton, controlParams(1.2f));

        Button slowerButton = controlButton("−");
        slowerButton.setOnClickListener(view -> changeSpeed(-5));
        controls.addView(slowerButton, controlParams(0.6f));

        speedText = new TextView(this);
        speedText.setText(speed + " px/s");
        speedText.setTextColor(Color.WHITE);
        speedText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        speedText.setGravity(Gravity.CENTER);
        controls.addView(speedText, controlParams(1f));

        Button fasterButton = controlButton("+");
        fasterButton.setOnClickListener(view -> changeSpeed(5));
        controls.addView(fasterButton, controlParams(0.6f));

        Button closeButton = controlButton("Close");
        closeButton.setOnClickListener(view -> finish());
        controls.addView(closeButton, controlParams(0.9f));
        column.addView(controls, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(50)));

        countdownText = new TextView(this);
        countdownText.setTextColor(Color.WHITE);
        countdownText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 30);
        countdownText.setTypeface(countdownText.getTypeface(), android.graphics.Typeface.BOLD);
        countdownText.setGravity(Gravity.CENTER);
        countdownText.setPadding(dp(22), dp(12), dp(22), dp(12));
        countdownText.setBackground(controlBackground());
        countdownText.setVisibility(View.GONE);
        root.addView(countdownText, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER));

        promptScroll.post(() -> {
            applySideMargins(sideMarginPercent);
            beginCountdown();
        });
        return root;
    }

    private void applySideMargins(int percent) {
        if (promptText == null || promptScroll == null) {
            return;
        }
        int contentWidth = Math.max(0, promptScroll.getWidth());
        int side = Math.round(contentWidth * percent / 100f);
        promptText.setPadding(side, dp(16), side, dp(28));
    }

    private void beginCountdown() {
        handler.removeCallbacks(scrollRunnable);
        handler.removeCallbacks(countdownRunnable);
        scrolling = false;
        updateRemoteMediaState();
        pauseButton.setText(AppLanguage.t(this, "Pause"));
        if (countdownSeconds == 0) {
            countdownText.setVisibility(View.GONE);
            startScrolling();
            return;
        }
        countdownStep = countdownSeconds;
        countdownText.setText(AppLanguage.t(this, "Starts in") + " " + countdownStep);
        countdownText.setVisibility(View.VISIBLE);
        handler.postDelayed(countdownRunnable, 1000);
    }

    private void togglePause() {
        if (countdownText.getVisibility() == View.VISIBLE) {
            handler.removeCallbacks(countdownRunnable);
            countdownText.setVisibility(View.GONE);
            pauseButton.setText(AppLanguage.t(this, "Start"));
            updateRemoteMediaState();
            return;
        }
        if (scrolling) {
            scrolling = false;
            handler.removeCallbacks(scrollRunnable);
            pauseButton.setText(AppLanguage.t(this, "Start"));
            updateRemoteMediaState();
            return;
        }
        int maximumScroll = Math.max(0, promptText.getHeight() - promptScroll.getHeight());
        if (maximumScroll > 0 && promptScroll.getScrollY() >= maximumScroll) {
            promptScroll.scrollTo(0, 0);
        }
        startScrolling();
    }

    private void startScrolling() {
        scrolling = true;
        partialPixels = 0f;
        lastScrollTime = SystemClock.uptimeMillis();
        pauseButton.setText(AppLanguage.t(this, "Pause"));
        handler.removeCallbacks(scrollRunnable);
        handler.post(scrollRunnable);
        updateRemoteMediaState();
    }

    private void changeSpeed(int amount) {
        speed = clamp(speed + amount, MIN_SPEED, MAX_SPEED);
        speedText.setText(speed + " px/s");
    }

    private boolean handleHardwareRemoteKey(KeyEvent event) {
        if (!RemoteControlProfile.isRemotePress(event)) {
            return false;
        }
        String action = RemoteControlProfile.actionForKey(preferences, event.getKeyCode());
        if (action == null) {
            return false;
        }
        executeRemoteAction(action);
        return true;
    }

    private void executeRemoteAction(String action) {
        if (RemoteControlProfile.ACTION_TOGGLE.equals(action)) {
            togglePause();
            return;
        }
        if (RemoteControlProfile.ACTION_FASTER.equals(action)) {
            changeSpeed(5);
            return;
        }
        if (RemoteControlProfile.ACTION_SLOWER.equals(action)) {
            changeSpeed(-5);
            return;
        }
        if (RemoteControlProfile.ACTION_FORWARD.equals(action)) {
            nudgePrompt(1);
            return;
        }
        if (RemoteControlProfile.ACTION_BACK.equals(action)) {
            nudgePrompt(-1);
            return;
        }
        if (RemoteControlProfile.ACTION_REPLAY.equals(action)) {
            promptScroll.scrollTo(0, 0);
            partialPixels = 0f;
            startScrolling();
            return;
        }
        if (RemoteControlProfile.ACTION_CLOSE.equals(action)) {
            finish();
        }
    }

    private void playFromRemote() {
        if (countdownText != null && countdownText.getVisibility() == View.VISIBLE) {
            handler.removeCallbacks(countdownRunnable);
            countdownText.setVisibility(View.GONE);
        }
        if (!scrolling) {
            startScrolling();
        }
    }

    private void pauseFromRemote() {
        if (countdownText != null && countdownText.getVisibility() == View.VISIBLE) {
            handler.removeCallbacks(countdownRunnable);
            countdownText.setVisibility(View.GONE);
        }
        if (scrolling) {
            scrolling = false;
            handler.removeCallbacks(scrollRunnable);
            if (pauseButton != null) {
                pauseButton.setText(AppLanguage.t(this, "Start"));
            }
        }
        updateRemoteMediaState();
    }

    private void nudgePrompt(int direction) {
        if (promptScroll == null || promptText == null) {
            return;
        }
        pauseFromRemote();
        int maximumScroll = Math.max(0, promptText.getHeight() - promptScroll.getHeight());
        int distance = Math.max(dp(72), promptScroll.getHeight() / 5);
        int next = clamp(promptScroll.getScrollY() + direction * distance, 0, maximumScroll);
        promptScroll.scrollTo(0, next);
    }

    private void startBrowserRemoteIfEnabled() {
        if (!getIntent().getBooleanExtra(
                TeleprompterOverlayService.EXTRA_BROWSER_REMOTE_ENABLED, false)) {
            return;
        }

        updateBrowserRemoteStatus("Step 3 of 3 — preparing the iPad remote…");
        LocalBrowserRemoteServer server = new LocalBrowserRemoteServer(
                action -> handler.post(() -> executeRemoteAction(action)));
        try {
            LocalBrowserRemoteServer.ConnectionDetails details = server.start();
            browserRemoteServer = server;
            updateBrowserRemoteStatus("Step 3 of 3 — on the iPad, open Safari\n"
                    + "Type: " + details.address + "\n"
                    + "Then enter code: " + details.accessCode);
        } catch (IOException ignored) {
            server.stop();
            updateBrowserRemoteStatus("Step 1 is not complete: connect the iPad to the Galaxy Mobile Hotspot "
                    + "or same Wi-Fi. Then reopen Mirror full-screen.");
        }
    }

    private void stopBrowserRemote() {
        if (browserRemoteServer != null) {
            browserRemoteServer.stop();
            browserRemoteServer = null;
        }
    }

    private void updateBrowserRemoteStatus(String status) {
        if (browserRemoteText == null) {
            return;
        }
        browserRemoteText.setText(status);
        browserRemoteText.setVisibility(View.VISIBLE);
    }

    private void createRemoteMediaSession() {
        mediaSession = new MediaSession(this, "PromptOverlayMirrorRemote");
        mediaSession.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS
                | MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);
        mediaSession.setCallback(new MediaSession.Callback() {
            @Override
            public void onPlay() {
                handler.post(() -> playFromRemote());
            }

            @Override
            public void onPause() {
                handler.post(() -> pauseFromRemote());
            }

            @Override
            public void onStop() {
                handler.post(() -> pauseFromRemote());
            }

            @Override
            public void onFastForward() {
                handler.post(() -> executeRemoteAction(RemoteControlProfile.ACTION_FASTER));
            }

            @Override
            public void onRewind() {
                handler.post(() -> executeRemoteAction(RemoteControlProfile.ACTION_SLOWER));
            }

            @Override
            public void onSkipToNext() {
                handler.post(() -> executeRemoteAction(RemoteControlProfile.ACTION_FORWARD));
            }

            @Override
            public void onSkipToPrevious() {
                handler.post(() -> executeRemoteAction(RemoteControlProfile.ACTION_BACK));
            }

            @Override
            public boolean onMediaButtonEvent(Intent mediaButtonIntent) {
                KeyEvent event = mediaButtonIntent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
                if (event != null && RemoteControlProfile.isRemotePress(event)) {
                    String action = RemoteControlProfile.actionForKey(preferences, event.getKeyCode());
                    if (action != null) {
                        handler.post(() -> executeRemoteAction(action));
                        return true;
                    }
                }
                return super.onMediaButtonEvent(mediaButtonIntent);
            }
        });
        mediaSession.setActive(true);
        updateRemoteMediaState();
    }

    private void updateRemoteMediaState() {
        if (mediaSession == null) {
            return;
        }
        long actions = PlaybackState.ACTION_PLAY
                | PlaybackState.ACTION_PAUSE
                | PlaybackState.ACTION_PLAY_PAUSE
                | PlaybackState.ACTION_STOP
                | PlaybackState.ACTION_FAST_FORWARD
                | PlaybackState.ACTION_REWIND
                | PlaybackState.ACTION_SKIP_TO_NEXT
                | PlaybackState.ACTION_SKIP_TO_PREVIOUS;
        int state = scrolling ? PlaybackState.STATE_PLAYING : PlaybackState.STATE_PAUSED;
        mediaSession.setPlaybackState(new PlaybackState.Builder()
                .setActions(actions)
                .setState(state, PlaybackState.PLAYBACK_POSITION_UNKNOWN, scrolling ? 1f : 0f)
                .build());
    }

    private void releaseRemoteMediaSession() {
        if (mediaSession != null) {
            mediaSession.release();
            mediaSession = null;
        }
    }

    private void hideSystemBars() {
        Window window = getWindow();
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            WindowInsetsController controller = window.getInsetsController();
            if (controller != null) {
                controller.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                controller.setSystemBarsBehavior(
                        WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            window.getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        }
    }

    private void requestHideSystemBars() {
        View decorView = getWindow().getDecorView();
        decorView.post(this::hideSystemBars);
    }

    private Button controlButton(String label) {
        Button button = new Button(this);
        button.setText(AppLanguage.t(this, label));
        button.setAllCaps(false);
        button.setTextColor(Color.WHITE);
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        button.setPadding(dp(2), 0, dp(2), 0);
        button.setBackgroundColor(Color.TRANSPARENT);
        return button;
    }

    private LinearLayout.LayoutParams controlParams(float weight) {
        return new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, weight);
    }

    private GradientDrawable controlBackground() {
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.argb(220, 28, 34, 47));
        background.setCornerRadius(dp(14));
        return background;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
