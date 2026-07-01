package com.sayboard.ime;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

public class KeyboardTestActivity extends Activity {
    private EditText testInput;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(buildContent());
        testInput.requestFocus();
        testInput.postDelayed(() -> {
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.showSoftInput(testInput, InputMethodManager.SHOW_IMPLICIT);
            }
        }, 300);
    }

    private LinearLayout buildContent() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(20), dp(20), dp(20));
        root.setBackgroundColor(Color.rgb(248, 249, 250));

        TextView title = new TextView(this);
        title.setText("Keyboard Test");
        title.setTextSize(22);
        title.setTextColor(Color.rgb(31, 35, 40));
        title.setGravity(Gravity.START);
        root.addView(title);

        testInput = new EditText(this);
        testInput.setHint("Type here to test the keyboard");
        testInput.setTextSize(18);
        testInput.setMinLines(5);
        testInput.setGravity(Gravity.TOP | Gravity.START);
        testInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
        );
        params.setMargins(0, dp(16), 0, 0);
        root.addView(testInput, params);
        return root;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
