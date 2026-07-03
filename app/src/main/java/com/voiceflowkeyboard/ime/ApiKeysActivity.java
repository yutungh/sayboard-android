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

public class ApiKeysActivity extends Activity {
    private EditText openAiInput;
    private EditText anthropicInput;
    private EditText xAiInput;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle("API keys");
        setContentView(buildContent());
    }

    private View buildContent() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.BACKGROUND);
        Ui.applySystemBarPadding(root, dp(18), dp(16), dp(18), dp(22));
        scroll.addView(root);

        TextView title = text("API keys", 26, true, Ui.TEXT);
        title.setIncludeFontPadding(false);
        root.addView(title);

        LinearLayout providers = section(root, "Providers");
        openAiInput = keyInput("OpenAI API key");
        openAiInput.setText(Prefs.openAiApiKey(this));
        providers.addView(field("OpenAI", "Transcription and transform", openAiInput));
        providers.addView(divider());

        anthropicInput = keyInput("Anthropic API key");
        anthropicInput.setText(Prefs.anthropicApiKey(this));
        providers.addView(field("Claude", "Stored for provider setup", anthropicInput));
        providers.addView(divider());

        xAiInput = keyInput("xAI API key");
        xAiInput.setText(Prefs.xAiApiKey(this));
        providers.addView(field("Grok", "Stored for provider setup", xAiInput));

        Button save = button("Save API keys");
        save.setOnClickListener(v -> saveKeys());
        root.addView(save);

        Button back = button("Back to settings");
        back.setOnClickListener(v -> finish());
        root.addView(back);
        return scroll;
    }

    private LinearLayout section(LinearLayout root, String title) {
        TextView label = text(title, 13, true, Ui.MUTED);
        label.setAllCaps(true);
        label.setPadding(0, dp(22), 0, dp(7));
        root.addView(label);

        LinearLayout section = new LinearLayout(this);
        section.setOrientation(LinearLayout.VERTICAL);
        section.setBackgroundColor(Ui.SURFACE);
        root.addView(section, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        return section;
    }

    private View field(String title, String value, EditText input) {
        LinearLayout field = new LinearLayout(this);
        field.setOrientation(LinearLayout.VERTICAL);
        field.setPadding(dp(14), dp(12), dp(14), dp(14));
        field.setBackgroundColor(Ui.SURFACE);

        LinearLayout line = new LinearLayout(this);
        line.setOrientation(LinearLayout.HORIZONTAL);
        line.setGravity(Gravity.CENTER_VERTICAL);
        line.addView(text(title, 16, false, Ui.TEXT), new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        line.addView(text(value, 13, false, Ui.MUTED));
        field.addView(line);
        field.addView(input);
        return field;
    }

    private EditText keyInput(String hint) {
        EditText input = new EditText(this);
        input.setHint(hint);
        input.setTextColor(Ui.TEXT);
        input.setHintTextColor(Ui.MUTED);
        input.setSingleLine(true);
        input.setPadding(0, dp(8), 0, 0);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        return input;
    }

    private void saveKeys() {
        Prefs.saveApiKeys(
                this,
                openAiInput.getText().toString(),
                anthropicInput.getText().toString(),
                xAiInput.getText().toString()
        );
        Toast.makeText(this, "API keys saved", Toast.LENGTH_SHORT).show();
        finish();
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

    private View divider() {
        View divider = new View(this);
        divider.setBackgroundColor(Ui.DIVIDER);
        divider.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                Math.max(1, dp(1))
        ));
        return divider;
    }

    private TextView text(String value, int sp, boolean bold, int color) {
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextSize(sp);
        text.setTextColor(color);
        if (bold) {
            text.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        }
        return text;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
