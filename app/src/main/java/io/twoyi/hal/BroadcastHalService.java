package io.twoyi.hal;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.LocalSocket;
import android.os.BatteryManager;
import android.util.Log;

import java.io.IOException;
import java.io.OutputStream;

/**
 * Handles system broadcasts and forwards them through the HAL socket to the container
 * This is particularly important for battery status reporting and other system events
 */
public class BroadcastHalService {

    private static final String TAG = "BroadcastHalService";
    private static final BroadcastHalService INSTANCE = new BroadcastHalService();
    public static final BroadcastHalService getInstance() {
        return INSTANCE;
    }

    private Context context;
    private LocalSocket localSocket;
    private final BroadcastReceiver broadcastReceiver = new InternalBroadcastReceiver();
    private volatile boolean isInitialized = false;

    private class InternalBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (localSocket == null || !localSocket.isConnected()) {
                return;
            }
            try {
                String action = intent.getAction();
                if (action != null) {
                    if ("android.intent.action.BATTERY_CHANGED".equals(action)) {
                        // Extract battery info for forwarding to container
                        int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                        int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
                        int status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
                        boolean isCharging = intent.getBooleanExtra(BatteryManager.EXTRA_PLUGGED, false);

                        // Forward battery information to container
                        forwardBatteryInfo(level, scale, status, isCharging);
                    } else {
                        // Forward other broadcast intents
                        forwardIntent(intent);
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error processing broadcast", e);
            }
        }
    }

    private void forwardBatteryInfo(int level, int scale, int status, boolean isCharging) {
        try {
            OutputStream outputStream = localSocket.getOutputStream();

            // Write battery information to socket in predefined format
            // Format: [length][level][scale][status][isCharging]
            String batteryInfo = "BATTERY:" + level + ":" + scale + ":" + status + ":" + (isCharging ? 1 : 0);
            byte[] data = batteryInfo.getBytes();
            writeInt(outputStream, data.length);
            outputStream.write(data);
            outputStream.flush();
        } catch (Exception e) {
            Log.e(TAG, "Error forwarding battery info", e);
        }
    }

    private void forwardIntent(Intent intent) {
        try {
            OutputStream outputStream = localSocket.getOutputStream();

            String action = intent.getAction();
            if (action != null) {
                String intentInfo = action + ":";
                byte[] data = intentInfo.getBytes();
                writeInt(outputStream, data.length);
                outputStream.write(data);
                outputStream.flush();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error forwarding intent", e);
        }
    }

    /**
     * Helper method to write integer in little-endian format
     */
    private static void writeInt(OutputStream outputStream, int value) throws IOException {
        outputStream.write(value & 0xFF);
        outputStream.write((value >> 8) & 0xFF);
        outputStream.write((value >> 16) & 0xFF);
        outputStream.write((value >> 24) & 0xFF);
    }

    public void initialize(Context context, LocalSocket localSocket) {
        if (isInitialized) {
            return;
        }

        this.context = context;
        this.localSocket = localSocket;
        this.isInitialized = true;

        if (context == null || localSocket == null) {
            Log.e(TAG, "Invalid context or socket for broadcast service");
            return;
        }

        IntentFilter filter = new IntentFilter();

        // Battery-related intents - this is the key for showing real battery status
        filter.addAction("android.intent.action.BATTERY_CHANGED");

        // Time-related intents
        filter.addAction("android.intent.action.TIME_SET");
        filter.addAction("android.intent.action.TIMEZONE_CHANGED");
        filter.addAction("android.intent.action.DATE_CHANGED");
        filter.addAction("android.intent.action.TIME_TICK");

        // Phone state changes
        filter.addAction("android.intent.action.SERVICE_STATE_CHANGED");
        filter.addAction("android.intent.action.SIG_STR");

        // Connectivity changes
        filter.addAction("android.net.conn.CONNECTIVITY_CHANGE");
        filter.addAction("android.net.wifi.WIFI_STATE_CHANGED");
        filter.addAction("android.net.wifi.STATE_CHANGE");

        try {
            context.registerReceiver(this.broadcastReceiver, filter);
            Log.d(TAG, "Broadcast receiver registered for system events, including battery status reporting");
        } catch (Exception e) {
            Log.e(TAG, "Error registering broadcast receiver", e);
        }
    }

    public void cleanup() {
        try {
            if (context != null && broadcastReceiver != null) {
                context.unregisterReceiver(broadcastReceiver);
                Log.d(TAG, "Broadcast receiver unregistered");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error unregistering broadcast receiver", e);
        }
        isInitialized = false;
    }
}