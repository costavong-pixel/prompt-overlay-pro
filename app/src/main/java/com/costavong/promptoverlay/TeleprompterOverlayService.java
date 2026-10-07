package com.costavong.promptoverlay;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.content.pm.PackageManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * A camera-free foreground service that draws the prompt above the user's preferred camera app.
 * It never accesses, records, saves, or uploads camera or microphone data.
 */
public class TeleprompterOverlayService extends Service {
    public static final String EXTRA_SCRIPT = "extra_script";
    public static final String EXTRA_STYLED_SCRIPT = "extra_styled_script";
    public static final String EXTRA_SPEED = "extra_speed";
    public static final String EXTRA_FONT = "extra_font";
    public static final String EXTRA_TEXT_COLOR = "extra_text_color";
    public static final String EXTRA_TEXT_SIZE = "extra_text_size";
    public static final String EXTRA_BACKGROUND_OPACITY = "extra_background_opacity";
    public static final String EXTRA_HEIGHT_OVERLAY = "extra_height_overlay";
    public static final String EXTRA_WIDTH_OVERLAY = "extra_width_overlay";
    public static final String EXTRA_REPEAT = "extra_repeat";
    public static final String EXTRA_SIDE_MARGIN = "extra_side_margin";
    public static final String EXTRA_LINE_SPACING = "extra_line_spacing";
    public static final String EXTRA_TEXT_ALIGNMENT = "extra_text_alignment";
    public static final String EXTRA_START_DELAY = "extra_start_delay";
    public static final String EXTRA_BROWSER_REMOTE_ENABLED = "extra_browser_remote_enabled";

    private static final int NOTIFICATION_ID = 7001;
    private static final String CHANNEL_ID = "prompt_overlay_channel";
    private static final int DEFAULT_SPEED = 45;
    private static final int MIN_SPEED = 10;
    private static final int MAX_SPEED = 180;
    private static final int MIN_TEXT_SIZE = 18;
    private static final int MAX_TEXT_SIZE = 72;
    private static final int MIN_HEIGHT_PERCENT = 35;
    // At 100% the floating window can hide the route back to the app.
    private static final int MAX_HEIGHT_PERCENT = 75;
    private static final int MIN_WIDTH_PERCENT = 45;

    private final Handler handler = new Handler(Looper.getMainLooper());

    private WindowManager windowManager;
    private WindowManager.LayoutParams overlayParams;
    private FrameLayout overlayRoot;
    private ScrollView promptScroll;
    private TextView promptText;
    private TextView countdownText;
    private TextView speedText;
    private TextView dragHandle;
    private TextView browserRemoteText;
    private Button pauseButton;
    private SharedPreferences preferences;
    private ScaleGestureDetector resizeDetector;

    private CharSequence script = "";
    private int speed = DEFAULT_SPEED;
    private String font = PromptFont.CLEAN;
    private String textColor = PromptTextColor.WHITE;
    private int textSizeSp = 32;
    private int backgroundOpacity = 56;
    private int heightPercent = 70;
    private int widthPercent = 100;
    private int sideMarginPercent = 5;
    private int lineSpacingPercent = 125;
    private String textAlignment = PromptTextAlignment.LEFT;
    private int startDelaySeconds = 3;
    private boolean repeatPrompt = true;
    private boolean browserRemoteEnabled;
    private boolean scrolling;
    private int countdownStep;
    private long lastScrollTime;
    private float partialPixels;
    private boolean trialExpired;
    private boolean overlayAdded;
    private boolean resizing;
    private float dragStartRawX;
    private float dragStartRawY;
    private int dragStartX;
    private int dragStartY;
    private int overlayWidth;
    private int overlayHeight;
    private BluetoothGatt remoteGatt;
    private MediaSession mediaSession;
    private LocalBrowserRemoteServer browserRemoteServer;

    private final Runnable countdownRunnable = new Runnable() {
        @Override
        public void run() {
            if (countdownStep > 1) {
                countdownStep--;
                countdownText.setText(AppLanguage.t(TeleprompterOverlayService.this, "Starts in")
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

            if (!TrialAccess.isUnlocked(preferences)
                    && TrialAccess.consumeActiveMillis(preferences, elapsed) <= 0L) {
                showTrialExpired();
                return;
            }

            float movement = speed * elapsed / 1000f + partialPixels;
            int wholePixels = (int) movement;
            partialPixels = movement - wholePixels;
            if (wholePixels > 0) {
                int maximumScroll = Math.max(0, promptText.getHeight() - promptScroll.getHeight());
                if (maximumScroll <= 0) {
                    scrolling = false;
                    pauseButton.setText(AppLanguage.t(TeleprompterOverlayService.this, "Replay"));
                    updateRemoteMediaState();
                    return;
                }
                int nextScroll = Math.min(maximumScroll, promptScroll.getScrollY() + wholePixels);
                promptScroll.scrollTo(0, nextScroll);
                if (nextScroll >= maximumScroll) {
                    if (repeatPrompt) {
                        // Deliberately silent: uninterrupted takes are easier to test and record.
                        promptScroll.scrollTo(0, 0);
                        partialPixels = 0f;
                    } else {
                        scrolling = false;
                        pauseButton.setText(AppLanguage.t(TeleprompterOverlayService.this, "Replay"));
                        updateRemoteMediaState();
                        return;
                    }
                }
            }
            handler.postDelayed(this, 16);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        preferences = getSharedPreferences("prompt_overlay", MODE_PRIVATE);
        windowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        createNotificationChannel();
        createRemoteMediaSession();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startAsForegroundService();
        readSettings(intent);
        if (script.toString().trim().isEmpty()) {
            script = AppLanguage.t(this, "No script yet. Open Prompt Overlay and paste your words.");
        }
        showOrUpdateOverlay();
        startBrowserRemote();
        connectConfiguredRemote();
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        stopBrowserRemote();
        closeRemote();
        releaseRemoteMediaSession();
        if (overlayRoot != null && windowManager != null) {
            try {
                windowManager.removeView(overlayRoot);
            } catch (IllegalArgumentException ignored) {
                // The system may already have removed the overlay.
            }
        }
        overlayRoot = null;
        overlayAdded = false;
        stopForeground(STOP_FOREGROUND_REMOVE);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void readSettings(Intent intent) {
        if (intent == null) {
            return;
        }
        String storedStyledScript = intent.getStringExtra(EXTRA_STYLED_SCRIPT);
        if (storedStyledScript != null) {
            script = ScriptFormatting.fromStoredText(storedStyledScript);
        } else {
            String newScript = intent.getStringExtra(EXTRA_SCRIPT);
            if (newScript != null && !newScript.trim().isEmpty()) {
                script = newScript;
            }
        }
        speed = clamp(intent.getIntExtra(EXTRA_SPEED, DEFAULT_SPEED), MIN_SPEED, MAX_SPEED);
        font = PromptFont.normalize(intent.getStringExtra(EXTRA_FONT));
        textColor = PromptTextColor.normalize(intent.getStringExtra(EXTRA_TEXT_COLOR));
        textSizeSp = clamp(intent.getIntExtra(EXTRA_TEXT_SIZE, 32), MIN_TEXT_SIZE, MAX_TEXT_SIZE);
        backgroundOpacity = clamp(intent.getIntExtra(EXTRA_BACKGROUND_OPACITY, 56), 0, 88);
        heightPercent = clamp(intent.getIntExtra(EXTRA_HEIGHT_OVERLAY, 70), MIN_HEIGHT_PERCENT, MAX_HEIGHT_PERCENT);
        widthPercent = clamp(intent.getIntExtra(EXTRA_WIDTH_OVERLAY, 100), MIN_WIDTH_PERCENT, 100);
        sideMarginPercent = clamp(intent.getIntExtra(EXTRA_SIDE_MARGIN, 5), 0, 20);
        lineSpacingPercent = clamp(intent.getIntExtra(EXTRA_LINE_SPACING, 125), 100, 200);
        textAlignment = PromptTextAlignment.normalize(intent.getStringExtra(EXTRA_TEXT_ALIGNMENT));
        startDelaySeconds = clamp(intent.getIntExtra(EXTRA_START_DELAY, 3), 0, 10);
        repeatPrompt = intent.getBooleanExtra(EXTRA_REPEAT, true);
        browserRemoteEnabled = intent.getBooleanExtra(EXTRA_BROWSER_REMOTE_ENABLED, false);
    }

    private void showOrUpdateOverlay() {
        if (overlayRoot == null) {
            createOverlay();
        }
        if (!TrialAccess.canUsePrompt(preferences)) {
            showTrialExpired();
            return;
        }

        trialExpired = false;
        promptText.setText(script);
        promptText.setTypeface(PromptFont.typefaceFor(font));
        promptText.setTextColor(PromptTextColor.colorFor(textColor));
        promptText.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSizeSp);
        promptText.setGravity(PromptTextAlignment.gravityFor(textAlignment));
        promptText.setLineSpacing(0, lineSpacingPercent / 100f);
        applyReadingMargins();
        overlayRoot.setBackgroundColor(Color.argb(Math.round(backgroundOpacity * 2.55f), 0, 0, 0));
        speedText.setText(speed + " px/s");
        applyPanelSize(false);
        promptScroll.post(new Runnable() {
            @Override
            public void run() {
                promptScroll.scrollTo(0, 0);
                beginCountdown();
            }
        });
    }

    private void createOverlay() {
        overlayRoot = new FrameLayout(this) {
            @Override
            public boolean dispatchKeyEvent(KeyEvent event) {
                if (handleHardwareRemoteKey(event)) {
                    return true;
                }
                return super.dispatchKeyEvent(event);
            }
        };
        overlayRoot.setFocusableInTouchMode(true);
        overlayRoot.setLayoutDirection(AppLanguage.layoutDirection(this));

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(12), dp(10), dp(12), dp(10));
        overlayRoot.addView(content, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        dragHandle = new TextView(this);
        dragHandle.setText(AppLanguage.t(this, "Drag prompt  •  Pinch to resize"));
        dragHandle.setTextColor(Color.rgb(229, 235, 250));
        dragHandle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        dragHandle.setGravity(Gravity.CENTER);
        dragHandle.setBackground(controlBackground());
        dragHandle.setContentDescription("Drag the prompt or pinch here to resize it");
        content.addView(dragHandle, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(36)));

        browserRemoteText = new TextView(this);
        browserRemoteText.setTextColor(Color.rgb(218, 228, 248));
        browserRemoteText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        browserRemoteText.setGravity(Gravity.CENTER);
        browserRemoteText.setPadding(dp(8), dp(5), dp(8), dp(5));
        browserRemoteText.setBackground(controlBackground());
        browserRemoteText.setVisibility(View.GONE);
        content.addView(browserRemoteText, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        promptScroll = new ScrollView(this);
        promptScroll.setFillViewport(true);
        promptScroll.setVerticalScrollBarEnabled(false);
        promptScroll.setClipToPadding(true);

        promptText = new TextView(this);
        promptText.setTextColor(Color.WHITE);
        promptText.setLineSpacing(0, 1.25f);
        promptText.setPadding(dp(10), dp(14), dp(10), dp(22));
        promptScroll.addView(promptText, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT));

        LinearLayout.LayoutParams promptParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f);
        promptParams.topMargin = dp(6);
        content.addView(promptScroll, promptParams);
        promptScroll.addOnLayoutChangeListener((view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) ->
                applyReadingMargins());

        LinearLayout controls = new LinearLayout(this);
        controls.setGravity(Gravity.CENTER_VERTICAL);
        controls.setPadding(dp(4), dp(5), dp(4), dp(5));
        controls.setBackground(controlBackground());

        pauseButton = controlButton("Pause");
        pauseButton.setOnClickListener(view -> togglePause());
        controls.addView(pauseButton, weightedControlParams(1.4f));

        Button slowerButton = controlButton("−");
        slowerButton.setOnClickListener(view -> changeSpeed(-5));
        controls.addView(slowerButton, weightedControlParams(0.7f));

        speedText = new TextView(this);
        speedText.setTextColor(Color.WHITE);
        speedText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        speedText.setGravity(Gravity.CENTER);
        controls.addView(speedText, weightedControlParams(1f));

        Button fasterButton = controlButton("+");
        fasterButton.setOnClickListener(view -> changeSpeed(5));
        controls.addView(fasterButton, weightedControlParams(0.7f));

        Button closeButton = controlButton("Close");
        closeButton.setOnClickListener(view -> stopSelf());
        controls.addView(closeButton, weightedControlParams(1f));

        LinearLayout.LayoutParams controlParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(48));
        controlParams.topMargin = dp(6);
        content.addView(controls, controlParams);

        countdownText = new TextView(this);
        countdownText.setTextColor(Color.WHITE);
        countdownText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 30);
        countdownText.setGravity(Gravity.CENTER);
        countdownText.setTypeface(countdownText.getTypeface(), android.graphics.Typeface.BOLD);
        countdownText.setPadding(dp(20), dp(12), dp(20), dp(12));
        countdownText.setBackground(controlBackground());
        countdownText.setVisibility(View.GONE);
        overlayRoot.addView(countdownText, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER));

        resizeDetector = new ScaleGestureDetector(this,
                new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                    @Override
                    public boolean onScaleBegin(ScaleGestureDetector detector) {
                        resizing = true;
                        return true;
                    }

                    @Override
                    public boolean onScale(ScaleGestureDetector detector) {
                        overlayWidth = Math.round(overlayWidth * detector.getScaleFactor());
                        overlayHeight = Math.round(overlayHeight * detector.getScaleFactor());
                        applyPanelSize(true);
                        return true;
                    }

                    @Override
                    public void onScaleEnd(ScaleGestureDetector detector) {
                        resizing = false;
                    }
                });
        dragHandle.setOnTouchListener((view, event) -> handlePanelTouch(event));

        overlayParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        overlayParams.gravity = Gravity.TOP | Gravity.START;
        overlayParams.setTitle("Prompt Overlay");
        applyPanelSize(false);
        windowManager.addView(overlayRoot, overlayParams);
        overlayAdded = true;
        overlayRoot.post(() -> overlayRoot.requestFocus());
    }

    private boolean handlePanelTouch(MotionEvent event) {
        resizeDetector.onTouchEvent(event);
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                dragStartRawX = event.getRawX();
                dragStartRawY = event.getRawY();
                dragStartX = overlayParams.x;
                dragStartY = overlayParams.y;
                return true;
            case MotionEvent.ACTION_MOVE:
                if (event.getPointerCount() == 1 && !resizing && !resizeDetector.isInProgress()) {
                    overlayParams.x = dragStartX + Math.round(event.getRawX() - dragStartRawX);
                    overlayParams.y = dragStartY + Math.round(event.getRawY() - dragStartRawY);
                    clampPanelPosition();
                    updateOverlayLayout();
                }
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                resizing = false;
                return true;
            default:
                return true;
        }
    }

    private void applyPanelSize(boolean keepCurrentSize) {
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int screenHeight = getResources().getDisplayMetrics().heightPixels;
        if (!keepCurrentSize || overlayWidth <= 0 || overlayHeight <= 0) {
            overlayWidth = Math.round(screenWidth * widthPercent / 100f);
            overlayHeight = Math.round(screenHeight * heightPercent / 100f);
        }

        int minWidth = Math.min(screenWidth, dp(240));
        int minHeight = Math.min(screenHeight, dp(200));
        overlayWidth = clamp(overlayWidth, minWidth, screenWidth);
        overlayHeight = clamp(overlayHeight, minHeight, screenHeight);
        if (overlayParams == null) {
            return;
        }

        overlayParams.width = overlayWidth;
        overlayParams.height = overlayHeight;
        if (!overlayAdded) {
            overlayParams.x = Math.max(0, (screenWidth - overlayWidth) / 2);
            overlayParams.y = dp(8);
        }
        clampPanelPosition();
        updateOverlayLayout();
    }

    private void clampPanelPosition() {
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int screenHeight = getResources().getDisplayMetrics().heightPixels;
        overlayParams.x = clamp(overlayParams.x, 0, Math.max(0, screenWidth - overlayWidth));
        overlayParams.y = clamp(overlayParams.y, 0, Math.max(0, screenHeight - overlayHeight));
    }

    private void updateOverlayLayout() {
        if (overlayAdded && overlayRoot != null) {
            windowManager.updateViewLayout(overlayRoot, overlayParams);
        }
    }

    @android.annotation.SuppressLint("MissingPermission") // Checked by hasBluetoothConnectPermission.
    private void connectConfiguredRemote() {
        if (remoteGatt != null || !hasBluetoothConnectPermission()) {
            return;
        }
        String address = preferences.getString(BleRemoteProtocol.PREF_REMOTE_ADDRESS, "");
        if (address.isEmpty()) {
            return;
        }
        BluetoothManager manager = getSystemService(BluetoothManager.class);
        BluetoothAdapter adapter = manager == null ? null : manager.getAdapter();
        if (adapter == null || !adapter.isEnabled()) {
            updateRemoteStatus("Remote off  •  Drag prompt  •  Pinch to resize");
            return;
        }
        try {
            BluetoothDevice remote = adapter.getRemoteDevice(address);
            updateRemoteStatus("Connecting remote…  •  Drag prompt  •  Pinch to resize");
            remoteGatt = remote.connectGatt(this, false, remoteCallback, BluetoothDevice.TRANSPORT_LE);
        } catch (IllegalArgumentException | SecurityException error) {
            updateRemoteStatus("Remote unavailable  •  Drag prompt  •  Pinch to resize");
        }
    }

    private void closeRemote() {
        if (remoteGatt != null) {
            try { remoteGatt.close(); } catch (SecurityException denied) { /* Permission revoked. */ }
            remoteGatt = null;
        }
    }

    private boolean hasBluetoothConnectPermission() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S
                || checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)
                == PackageManager.PERMISSION_GRANTED;
    }

    private final BluetoothGattCallback remoteCallback = new BluetoothGattCallback() {
        @android.annotation.SuppressLint("MissingPermission")
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS) {
                handler.post(() -> updateRemoteStatus("Remote connected  •  Drag prompt  •  Pinch to resize"));
                if (hasBluetoothConnectPermission()) {
                    try { gatt.discoverServices(); } catch (SecurityException denied) { closeRemote(); }
                }
                return;
            }
            if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                handler.post(() -> updateRemoteStatus("Remote disconnected  •  Drag prompt  •  Pinch to resize"));
                try { gatt.close(); } catch (SecurityException denied) { /* Permission revoked. */ }
                if (remoteGatt == gatt) {
                    remoteGatt = null;
                }
            }
        }

        @android.annotation.SuppressLint("MissingPermission")
        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status != BluetoothGatt.GATT_SUCCESS || !hasBluetoothConnectPermission()) {
                return;
            }
            BluetoothGattService remoteService = gatt.getService(BleRemoteProtocol.NUS_SERVICE);
            BluetoothGattCharacteristic commands = remoteService == null
                    ? null
                    : remoteService.getCharacteristic(BleRemoteProtocol.NUS_TX_CHARACTERISTIC);
            if (commands == null) {
                handler.post(() -> updateRemoteStatus("Wrong remote protocol  •  Drag prompt  •  Pinch to resize"));
                return;
            }
            try { gatt.setCharacteristicNotification(commands, true); } catch (SecurityException denied) { closeRemote(); return; }
            BluetoothGattDescriptor descriptor = commands.getDescriptor(
                    BleRemoteProtocol.CLIENT_CHARACTERISTIC_CONFIG);
            if (descriptor != null) {
                descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                try { gatt.writeDescriptor(descriptor); } catch (SecurityException denied) { closeRemote(); }
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            handleRemoteBytes(characteristic.getValue());
        }

        @Override
        public void onCharacteristicChanged(
                BluetoothGatt gatt,
                BluetoothGattCharacteristic characteristic,
                byte[] value) {
            handleRemoteBytes(value);
        }
    };

    private void handleRemoteBytes(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return;
        }
        String command = new String(bytes, StandardCharsets.UTF_8).trim().toUpperCase(Locale.US);
        handler.post(() -> executeRemoteCommand(command));
    }

    private void executeRemoteCommand(String command) {
        if (command.equals("TOGGLE")) {
            executeRemoteAction(RemoteControlProfile.ACTION_TOGGLE);
            return;
        }
        if (command.equals("PLAY")) {
            playFromRemote();
            return;
        }
        if (command.equals("PAUSE")) {
            pauseFromRemote();
            return;
        }
        if (command.equals("FASTER")) {
            executeRemoteAction(RemoteControlProfile.ACTION_FASTER);
            return;
        }
        if (command.equals("SLOWER")) {
            executeRemoteAction(RemoteControlProfile.ACTION_SLOWER);
            return;
        }
        if (command.equals("FORWARD")) {
            executeRemoteAction(RemoteControlProfile.ACTION_FORWARD);
            return;
        }
        if (command.equals("BACK")) {
            executeRemoteAction(RemoteControlProfile.ACTION_BACK);
            return;
        }
        if (command.equals("REPLAY")) {
            executeRemoteAction(RemoteControlProfile.ACTION_REPLAY);
            return;
        }
        if (command.equals("CLOSE")) {
            executeRemoteAction(RemoteControlProfile.ACTION_CLOSE);
        }
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
            if (promptScroll != null) {
                promptScroll.scrollTo(0, 0);
                partialPixels = 0f;
            }
            startScrolling();
            return;
        }
        if (RemoteControlProfile.ACTION_CLOSE.equals(action)) {
            stopSelf();
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

    private void updateRemoteStatus(String status) {
        if (dragHandle != null) {
            dragHandle.setText(status);
        }
    }

    private void startBrowserRemote() {
        stopBrowserRemote();
        if (!browserRemoteEnabled) {
            updateBrowserRemoteStatus(null);
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
                    + "or same Wi-Fi. Then stop and start the prompt again.");
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
        if (status == null) {
            browserRemoteText.setVisibility(View.GONE);
            return;
        }
        browserRemoteText.setText(status);
        browserRemoteText.setVisibility(View.VISIBLE);
    }

    private void createRemoteMediaSession() {
        mediaSession = new MediaSession(this, "PromptOverlayRemote");
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

    private void beginCountdown() {
        handler.removeCallbacks(scrollRunnable);
        handler.removeCallbacks(countdownRunnable);
        scrolling = false;
        updateRemoteMediaState();
        if (!TrialAccess.canUsePrompt(preferences)) {
            showTrialExpired();
            return;
        }
        trialExpired = false;
        pauseButton.setText(AppLanguage.t(this, "Pause"));
        if (startDelaySeconds == 0) {
            countdownText.setVisibility(View.GONE);
            startScrolling();
            return;
        }
        countdownStep = startDelaySeconds;
        countdownText.setText(AppLanguage.t(this, "Starts in") + " " + countdownStep);
        countdownText.setVisibility(View.VISIBLE);
        handler.postDelayed(countdownRunnable, 1000);
    }

    private void togglePause() {
        if (trialExpired || !TrialAccess.canUsePrompt(preferences)) {
            showTrialExpired();
            openUnlockScreen();
            return;
        }
        if (countdownText.getVisibility() == View.VISIBLE) {
            handler.removeCallbacks(countdownRunnable);
            countdownText.setVisibility(View.GONE);
            scrolling = false;
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
        if (!TrialAccess.canUsePrompt(preferences)) {
            showTrialExpired();
            return;
        }
        scrolling = true;
        partialPixels = 0f;
        lastScrollTime = SystemClock.uptimeMillis();
        pauseButton.setText(AppLanguage.t(this, "Pause"));
        handler.removeCallbacks(scrollRunnable);
        handler.post(scrollRunnable);
        updateRemoteMediaState();
    }

    private void showTrialExpired() {
        handler.removeCallbacks(scrollRunnable);
        handler.removeCallbacks(countdownRunnable);
        scrolling = false;
        updateRemoteMediaState();
        trialExpired = true;
        if (promptText != null) {
            promptText.setText("Your free trial is complete.\n\nTap Unlock to keep using the prompt.");
            promptText.setGravity(Gravity.CENTER);
        }
        if (countdownText != null) {
            countdownText.setText("Trial complete");
            countdownText.setVisibility(View.VISIBLE);
        }
        if (pauseButton != null) {
            pauseButton.setText("Unlock");
        }
    }

    private void openUnlockScreen() {
        Intent unlockIntent = new Intent(this, MainActivity.class);
        unlockIntent.putExtra(MainActivity.EXTRA_TRIAL_EXPIRED, true);
        unlockIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(unlockIntent);
    }

    private void changeSpeed(int amount) {
        speed = clamp(speed + amount, MIN_SPEED, MAX_SPEED);
        speedText.setText(speed + " px/s");
    }

    private void applyReadingMargins() {
        if (promptText == null || promptScroll == null) {
            return;
        }
        int contentWidth = Math.max(0, promptScroll.getWidth());
        int sideMargin = Math.round(contentWidth * sideMarginPercent / 100f);
        promptText.setPadding(sideMargin, dp(14), sideMargin, dp(22));
    }

    private void startAsForegroundService() {
        Intent openAppIntent = new Intent(this, MainActivity.class);
        PendingIntent openApp = PendingIntent.getActivity(
                this, 0, openAppIntent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification notification = new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle("Prompt Overlay is on")
                .setContentText("Your movable teleprompter is ready above your camera app.")
                .setContentIntent(openApp)
                .setOngoing(true)
                .build();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, "Prompt Overlay", NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("Keeps the teleprompter visible above another app.");
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.createNotificationChannel(channel);
        }
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

    private LinearLayout.LayoutParams weightedControlParams(float weight) {
        return new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, weight);
    }

    private GradientDrawable controlBackground() {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(Color.argb(210, 30, 35, 47));
        drawable.setCornerRadius(dp(12));
        return drawable;
    }

    private int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
