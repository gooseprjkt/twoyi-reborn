package io.twoyi.security.lock.digital;

import android.os.Bundle;
import android.widget.TextView;
import com.andrognito.pinlockview.IndicatorDots;
import com.andrognito.pinlockview.PinLockView;
import com.andrognito.pinlockview.PinLockListener;
import androidx.appcompat.app.AppCompatActivity;
import android.content.SharedPreferences;
import io.twoyi.R;

public class SimpleSetDigitalActivity extends AppCompatActivity {

    private PinLockView pinLockView;
    private IndicatorDots indicatorDots;
    private TextView profileName;
    private String enteredPin = "";
    private int pinState = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_digital_lock);

        pinLockView = findViewById(R.id.pin_lock_view);
        indicatorDots = findViewById(R.id.indicator_dots);
        profileName = findViewById(R.id.profile_name);

        pinLockView.attachIndicatorDots(indicatorDots);
        pinLockView.setPinLockListener(mPinLockListener);
        pinLockView.setPinLength(4);

        pinState = getIntent().getIntExtra("pin_state", -1);
        if (pinState == -1) {
            finish();
            return;
        }

        if (pinState == 2) {
            profileName.setText("Set New PIN");
        } else if (pinState == 4 || pinState == 1) {
            profileName.setText("Enter PIN");
        }
    }

    private PinLockListener mPinLockListener = new PinLockListener() {
        @Override
        public void onComplete(String pin) {
            // Save the PIN to shared preferences
            SharedPreferences prefs = getSharedPreferences("authentication", MODE_PRIVATE);
            prefs.edit()
                .putInt("pass_type", 1) // 1 for PIN
                .putString("pass_pin", pin)
                .apply();
            finish();
        }

        @Override
        public void onEmpty() {
            // Handle empty PIN if needed
        }

        @Override
        public void onPinChange(int pinLength, String intermediatePin) {
            // Handle PIN change if needed
        }
    };
}