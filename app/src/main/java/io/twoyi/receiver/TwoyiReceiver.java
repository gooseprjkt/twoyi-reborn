package io.twoyi.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;

import java.io.File;

import io.twoyi.utils.RomManager;

public class TwoyiReceiver extends BroadcastReceiver {
    private static final String TAG = "TwoyiReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "onReceive: " + intent);
        String action = intent.getAction();

        if ("android.hardware.usb.action.USB_STATE".equals(action)) {
            // Handle USB state changes
            boolean connected = intent.getBooleanExtra("connected", false);
            boolean none = intent.getBooleanExtra("none", false);
            boolean adb = intent.getBooleanExtra("adb", false);
            boolean rndis = intent.getBooleanExtra("rndis", false);
            boolean mtp = intent.getBooleanExtra("mtp", false);
            boolean ptp = intent.getBooleanExtra("ptp", false);
            boolean audioSource = intent.getBooleanExtra("audio_source", false);
            boolean midi = intent.getBooleanExtra("midi", false);
            return;
        }

        Uri data;
        String str;
        if ("android.intent.action.MEDIA_MOUNTED".equals(action)) {
            Uri data2 = intent.getData();
            if (data2 == null) {
                return;
            }
            String path = data2.getPath();
            Log.i(TAG, "media mounted: " + path);
            if (new File(RomManager.getRootfsDir(context), path).mkdirs()) {
                return;
            } else {
                str = "create media mirror dir failed.";
            }
        } else {
            if (!"android.intent.action.MEDIA_UNMOUNTED".equals(action) || (data = intent.getData()) == null) {
                return;
            }
            String path2 = data.getPath();
            Log.i(TAG, "media unmounted: " + path2);
            File file = new File(RomManager.getRootfsDir(context), path2);
            if (!file.exists() || !file.isDirectory() || file.delete()) {
                return;
            } else {
                str = "delete media mirror dir failed.";
            }
        }
        Log.w(TAG, str);
    }
}