package com.voiceflowkeyboard.ime;

import android.app.Activity;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class PromptEditorActivity extends Activity {
    static final String EXTRA_PROMPT_ID = "prompt_id";

    private String promptId;
    private EditText nameInput;
    private EditText promptInput;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        promptId = getIntent().getStringExtra(EXTRA_PROMPT_ID);
        if (promptId == null || promptId.trim().isEmpty()) {
            promptId = Prefs.PRESET_CASUAL;
        }
        setTitle("Edit prompt");
        setContentView(buildContent());
    }

    private View buildContent() {
        PromptProfile profile = Prefs.promptProfile(this, promptId);

        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(18), dp(20), dp(24));
        root.setBackgroundColor(0xfff8f9fa);
        scroll.addView(root);

        root.addView(text("Edit prompt", 24, true));

        nameInput = input("Name", 1);
        nameInput.setText(profile.name);
        root.addView(label("Name"));
        root.addView(nameInput);

        promptInput = input("Prompt", 14);
        promptInput.setText(Prefs.promptForPreset(this, profile.id));
        promptInput.setGravity(Gravity.TOP | Gravity.START);
        root.addView(label("Prompt"));
        root.addView(promptInput);

        Button save = button("Save prompt");
        save.setOnClickListener(v -> savePrompt());
        root.addView(save);

        Button back = button("Back to settings");
        back.setOnClickListener(v -> finish());
        root.addView(back);

        return scroll;
    }

    private void savePrompt() {
        Prefs.savePromptProfile(this, promptId, nameInput.getText().toString(), promptInput.getText().toString());
        Toast.makeText(this, "Prompt saved", Toast.LENGTH_SHORT).show();
        finish();
    }

    private TextView label(String value) {
        TextView label = text(value, 13, true);
        label.setPadding(0, dp(18), 0, dp(6));
        return label;
    }

    private EditText input(String hint, int lines) {
        EditText input = new EditText(this);
        input.setHint(hint);
        input.setTextColor(0xff1f2328);
        input.setHintTextColor(0xff5f6368);
        input.setSingleLine(lines == 1);
        input.setMinLines(lines);
        input.setPadding(dp(12), dp(8), dp(12), dp(8));
        input.setBackgroundColor(0xffffffff);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        return input;
    }

    private Button button(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setAllCaps(false);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, dp(14), 0, 0);
        button.setLayoutParams(params);
        return button;
    }

    private TextView text(String value, int sp, boolean bold) {
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextSize(sp);
        text.setTextColor(0xff1f2328);
        if (bold) {
            text.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        }
        return text;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
