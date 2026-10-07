package com.costavong.promptoverlay;

import java.util.UUID;

/**
 * The documented protocol for the future Prompt Overlay custom BLE remote.
 * The remote advertises as "Prompt Remote" and sends short UTF-8 commands over
 * the Nordic UART Service notification characteristic.
 */
public final class BleRemoteProtocol {
    public static final String EXPECTED_DEVICE_NAME = "Prompt Remote";
    public static final String PREF_REMOTE_ADDRESS = "bluetooth_remote_address";
    public static final String PREF_REMOTE_NAME = "bluetooth_remote_name";

    public static final UUID NUS_SERVICE =
            UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e");
    public static final UUID NUS_TX_CHARACTERISTIC =
            UUID.fromString("6e400003-b5a3-f393-e0a9-e50e24dcca9e");
    public static final UUID CLIENT_CHARACTERISTIC_CONFIG =
            UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    private BleRemoteProtocol() {
    }
}
