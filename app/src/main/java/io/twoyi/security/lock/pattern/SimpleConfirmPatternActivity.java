package io.twoyi.security.lock.pattern;

import android.os.Bundle;
import android.content.SharedPreferences;
import java.util.List;
import me.zhanghai.android.patternlock.ConfirmPatternActivity;
import me.zhanghai.android.patternlock.PatternView;

public class SimpleConfirmPatternActivity extends ConfirmPatternActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    protected boolean isPatternCorrect(List<PatternView.Cell> pattern) {
        SharedPreferences prefs = getSharedPreferences("authentication", MODE_PRIVATE);
        String savedPattern = prefs.getString("pass_pattern", "");
        String inputPattern = patternToString(pattern);
        return savedPattern.equals(inputPattern);
    }

    private String patternToString(List<PatternView.Cell> pattern) {
        StringBuilder builder = new StringBuilder();
        for (PatternView.Cell cell : pattern) {
            builder.append(cell.getRow()).append(cell.getColumn());
        }
        return builder.toString();
    }
}