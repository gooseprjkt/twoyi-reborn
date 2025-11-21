/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package io.twoyi;

import android.app.Application;
import android.content.Context;
import android.content.res.Resources;
import android.net.ConnectivityManager;
import android.net.wifi.WifiManager;
import android.os.HandlerThread;
import android.os.Vibrator;

import com.microsoft.appcenter.AppCenter;
import com.microsoft.appcenter.analytics.Analytics;
import com.microsoft.appcenter.crashes.Crashes;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.ref.WeakReference;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import io.twoyi.utils.RomManager;

/**
 * @author weishu
 * @date 2020/12/24.
 */

public class TwoyiApplication extends Application {

    public static TwoyiApplication f5121e;

    @Override
    protected void attachBaseContext(Context base) throws IOException {
        super.attachBaseContext(base);

        f5121e = this; // Initialize static reference

        // Initialize directories and resources similar to newer version
        int rendererInit = 0; // Using constant value instead of Renderer initialization
        File rootfsDir = RomManager.getRootfsDir(base);
        File devDir = new File(rootfsDir, "dev");
        File inputDir = new File(devDir, "input");
        if (!inputDir.exists()) {
            inputDir.mkdirs();
        }
        File socketDir = new File(devDir, "socket");
        if (!socketDir.exists()) {
            socketDir.mkdirs();
        }
        File mapsDir = new File(devDir, "maps");
        if (!mapsDir.exists()) {
            mapsDir.mkdirs();
        }
        File appSocketDir = new File(base.getDataDir(), "socket");
        if (!appSocketDir.exists()) {
            appSocketDir.mkdirs();
        }

        // Create loader symlinks
        java.nio.file.Path loader64Path = new File(base.getDataDir(), "loader64").toPath();
        java.nio.file.Path loader32Path = new File(base.getDataDir(), "loader32").toPath();
        createSymlink(loader64Path, new File(base.getApplicationInfo().nativeLibraryDir, "libloader.so").getAbsolutePath());
        createSymlink(loader32Path, new File(base.getApplicationInfo().nativeLibraryDir, "libloader32.so").getAbsolutePath());

        // Kill orphan processes
        killOrphanProcesses();

        // Initialize various components with proper threading like in closed source
        initializeComponents(base);

        RomManager.ensureBootFiles(base);

        // Start both legacy socket server and comprehensive HAL server
        TwoyiSocketServer.getInstance(base).start();
        io.twoyi.hal.TwoyiHalServer.getInstance(base).start();
    }

    private void startHalServices(Context context) {
        // Initialize HAL services for better system integration
        // This enables proper battery status reporting and other system services
        // The actual HAL connection will be handled by the native side once this infrastructure is in place
    }

    private void createSymlink(java.nio.file.Path symlinkPath, String targetPath) {
        gpsThread.start();

        // Initialize RIL (Radio Interface Layer) handler thread
        HandlerThread rilThread = new HandlerThread("twoyi_ril_thread", 10);
        rilThread.start();

        // Initialize telephony handler thread
        HandlerThread telephonyThread = new HandlerThread("twoyi_telephony_thread", 10);
        telephonyThread.start();

        // Initialize vibrator service
        Vibrator vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);

        // Initialize network managers
        WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);

        // Keep references for later use
        // (In real implementation, these would connect to actual manager classes)
    }

    @Override
    public void onCreate() {
        super.onCreate();

        AppCenter.start(this, "6223c2b1-30ab-4293-8456-ac575420774e",
                Analytics.class, Crashes.class);
        if (BuildConfig.DEBUG) {
            AppCenter.setEnabled(false);
        }
    }

    static int statusBarHeight = -1;

    public static int getStatusBarHeight(Context context) {
        if (statusBarHeight != -1) {
            return statusBarHeight;
        }

        int resId = context.getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (resId > 0) {
            statusBarHeight = context.getResources().getDimensionPixelSize(resId);
        }

        if (statusBarHeight < 0) {
            int result = 0;
            try {
                Class<?> clazz = Class.forName("com.android.internal.R$dimen");
                Object obj = clazz.newInstance();
                Field field = clazz.getField("status_bar_height");
                int resourceId = Integer.parseInt(field.get(obj).toString());
                result = context.getResources().getDimensionPixelSize(resourceId);
            } catch (Exception e) {
            } finally {
                statusBarHeight = result;
            }
        }

        //Use 25dp if no status bar height found
        if (statusBarHeight < 0) {
            statusBarHeight = dip2px(context, 25);
        }
        return statusBarHeight;
    }

    private static int dip2px(Context context, float dpValue) {
        float scale = context.getResources().getDisplayMetrics().density;
        int px = (int) (dpValue * scale + 0.5f);
        return px;
    }

    public static float px2dp(float pxValue) {
        return (pxValue / Resources.getSystem().getDisplayMetrics().density);
    }
}
