package com.costavong.promptoverlay;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/** Selects the named BLE remote used by TeleprompterOverlayService. */
public class BluetoothRemoteSetupActivity extends Activity {
    private static final int REQUEST_NEARBY_DEVICES = 201;
    private static final long SCAN_DURATION_MILLIS = 10_000L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private BluetoothLeScanner scanner;
    private boolean scanning;
    private TextView status;
    private LinearLayout results;
    private SharedPreferences preferences;

    private final Runnable stopScanRunnable = this::stopScan;
    private final ScanCallback scanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            showMatchingDevice(result == null ? null : result.getDevice());
        }

        @Override
        public void onScanFailed(int errorCode) {
            scanning = false;
            status.setText("Bluetooth scan could not start (code " + errorCode + ").");
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        preferences = getSharedPreferences("prompt_overlay", MODE_PRIVATE);
        setContentView(createScreen());
        showSavedRemote();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(stopScanRunnable);
        stopScan();
        super.onDestroy();
    }

    private View createScreen() {
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(dp(20), dp(24), dp(20), dp(24));
        column.setBackgroundColor(Color.rgb(20, 24, 33));
        column.setLayoutDirection(AppLanguage.layoutDirection(this));

        TextView title = label("Custom Bluetooth remote", 25, Color.WHITE);
        title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
        column.addView(title, matchWidth());

        TextView description = label(
                "This beta looks only for a custom BLE device named “Prompt Remote.” "
                        + "It controls the prompt, not the camera app.",
                15,
                Color.rgb(202, 213, 236));
        LinearLayout.LayoutParams descriptionParams = matchWidth();
        descriptionParams.topMargin = dp(12);
        column.addView(description, descriptionParams);

        status = label("Ready to scan.", 14, Color.rgb(151, 242, 198));
        LinearLayout.LayoutParams statusParams = matchWidth();
        statusParams.topMargin = dp(16);
        column.addView(status, statusParams);

        Button scanButton = button("Scan for custom remote");
        scanButton.setOnClickListener(view -> startScan());
        LinearLayout.LayoutParams scanParams = matchWidth();
        scanParams.topMargin = dp(14);
        column.addView(scanButton, scanParams);

        TextView commands = label(
                "Remote commands: PLAY, PAUSE, TOGGLE, FASTER, SLOWER, FORWARD, BACK, REPLAY, CLOSE.\n\n"
                        + "A future Prompt Remote can use these commands over the Nordic UART BLE service.",
                13,
                Color.rgb(176, 190, 214));
        LinearLayout.LayoutParams commandsParams = matchWidth();
        commandsParams.topMargin = dp(16);
        column.addView(commands, commandsParams);

        TextView foundTitle = label("Found remotes", 17, Color.WHITE);
        foundTitle.setTypeface(foundTitle.getTypeface(), android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams foundTitleParams = matchWidth();
        foundTitleParams.topMargin = dp(18);
        column.addView(foundTitle, foundTitleParams);

        results = new LinearLayout(this);
        results.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams resultsParams = matchWidth();
        resultsParams.topMargin = dp(8);
        column.addView(results, resultsParams);

        return column;
    }

    @android.annotation.SuppressLint("MissingPermission") // hasNearbyPermission checks both runtime permissions.
    private void startScan() {
        if (!hasNearbyPermission()) {
            requestNearbyPermission();
            return;
        }
        BluetoothManager manager = getSystemService(BluetoothManager.class);
        BluetoothAdapter adapter = manager == null ? null : manager.getAdapter();
        if (adapter == null || !adapter.isEnabled()) {
            status.setText("Turn on Bluetooth, then scan again.");
            return;
        }
        scanner = adapter.getBluetoothLeScanner();
        if (scanner == null) {
            status.setText("Bluetooth LE scanning is not available on this phone.");
            return;
        }
        results.removeAllViews();
        status.setText("Scanning for “Prompt Remote” for 10 seconds…");
        scanning = true;
        try { scanner.startScan(scanCallback); }
        catch (SecurityException denied) { scanning = false; status.setText("Nearby Devices permission was revoked. Scan again to grant it."); return; }
        handler.removeCallbacks(stopScanRunnable);
        handler.postDelayed(stopScanRunnable, SCAN_DURATION_MILLIS);
    }

    @android.annotation.SuppressLint("MissingPermission")
    private void stopScan() {
        if (scanner != null && scanning && hasNearbyPermission()) {
            try { scanner.stopScan(scanCallback); } catch (SecurityException denied) { /* Permission can be revoked while scanning. */ }
        }
        if (scanning) {
            status.setText("Scan finished. Select a found remote, or scan again.");
        }
        scanning = false;
    }

    @android.annotation.SuppressLint("MissingPermission")
    private void showMatchingDevice(BluetoothDevice device) {
        if (device == null || !hasNearbyPermission()) {
            return;
        }
        String name;
        try { name = device.getName(); } catch (SecurityException denied) { return; }
        if (name == null || !name.equalsIgnoreCase(BleRemoteProtocol.EXPECTED_DEVICE_NAME)) {
            return;
        }
        String address = device.getAddress();
        for (int index = 0; index < results.getChildCount(); index++) {
            Object tag = results.getChildAt(index).getTag();
            if (address.equals(tag)) {
                return;
            }
        }
        Button remote = button(name + "\n" + address);
        remote.setTag(address);
        remote.setOnClickListener(view -> selectRemote(name, address));
        results.addView(remote, matchWidth());
        status.setText("Found a custom remote. Tap it to select.");
    }

    private void selectRemote(String name, String address) {
        preferences.edit()
                .putString(BleRemoteProtocol.PREF_REMOTE_NAME, name)
                .putString(BleRemoteProtocol.PREF_REMOTE_ADDRESS, address)
                .apply();
        status.setText("Selected \"" + name + "\". Start the prompt to connect it.");
        Toast.makeText(this, "Custom remote selected.", Toast.LENGTH_SHORT).show();
    }

    private void showSavedRemote() {
        String savedName = preferences.getString(BleRemoteProtocol.PREF_REMOTE_NAME, "");
        if (!savedName.isEmpty()) {
            status.setText("Selected remote: \"" + savedName + "\". Start the prompt to connect it.");
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_NEARBY_DEVICES && hasNearbyPermission()) {
            startScan();
        } else if (requestCode == REQUEST_NEARBY_DEVICES) {
            status.setText("Nearby Devices permission is needed to find the custom remote.");
        }
    }

    private boolean hasNearbyPermission() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S
                || (checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
                && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED);
    }

    private void requestNearbyPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            requestPermissions(new String[]{
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT
            }, REQUEST_NEARBY_DEVICES);
        }
    }

    private TextView label(String text, int size, int color) {
        TextView view = new TextView(this);
        view.setText(AppLanguage.t(this, text));
        view.setTextColor(color);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, size);
        view.setLineSpacing(dp(3), 1f);
        view.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG);
        view.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
        return view;
    }

    private Button button(String text) {
        Button button = new Button(this);
        button.setText(AppLanguage.t(this, text));
        button.setAllCaps(false);
        button.setTextColor(Color.WHITE);
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        button.setMinHeight(dp(48));
        android.graphics.drawable.GradientDrawable background = new android.graphics.drawable.GradientDrawable();
        background.setColor(Color.rgb(45, 51, 68));
        background.setCornerRadius(dp(12));
        button.setBackground(background);
        return button;
    }

    private LinearLayout.LayoutParams matchWidth() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
