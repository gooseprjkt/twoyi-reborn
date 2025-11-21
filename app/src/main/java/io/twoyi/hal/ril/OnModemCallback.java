package io.twoyi.hal.ril;

import androidx.annotation.Keep;

public interface OnModemCallback {
    @Keep
    int onChange(int state);
}