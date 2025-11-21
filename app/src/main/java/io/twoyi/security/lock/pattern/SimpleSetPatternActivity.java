package io.twoyi.security.lock.pattern;

import android.os.Bundle;
import android.content.SharedPreferences;
import java.util.List;
import me.zhanghai.android.patternlock.PatternView;
import me.zhanghai.android.patternlock.SetPatternActivity;

public class SimpleSetPatternActivity extends SetPatternActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    protected void onSetPattern(List<PatternView.Cell> pattern) {
        // Save the pattern to shared preferences
        SharedPreferences prefs = getSharedPreferences("authentication", MODE_PRIVATE);
        prefs.edit()
            .putInt("pass_type", 2) // 2 for pattern
            .putString("pass_pattern", patternToString(pattern))
            .apply();
        finish();
    }

    private String patternToString(List<PatternView.Cell> pattern) {
        StringBuilder builder = new StringBuilder();
        for (PatternView.Cell cell : pattern) {
            builder.append(cell.getRow()).append(cell.getColumn());
        }
        return builder.toString();
    }
}