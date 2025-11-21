package io.twoyi.hal.ril;

import android.net.LocalSocket;
import androidx.annotation.Keep;

@Keep
public class TwoyiModemNative {
    private static final TwoyiModemNative INSTANCE;

    static {
        System.loadLibrary("modem");
        INSTANCE = new TwoyiModemNative();
    }

    private TwoyiModemNative() {
    }

    public static TwoyiModemNative getInstance() {
        return INSTANCE;
    }

    public native void destroy();

    public native int init(LocalSocket localSocket, String config);

    public native void setNetworkType(int networkType);

    public native int setOnModemCallback(OnModemCallback callback);

    public native void setSignalStrength(int signalStrength);
}