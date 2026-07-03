package com.voiceflowkeyboard.ime;

import android.app.Activity;
import android.graphics.Rect;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class ApiKeysActivity extends Activity {
    private EditText openAiInput;
    private EditText anthropicInput;
    private EditText xAiInput;
    private EditText deepgramInput;
    private ScrollView scroll;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Ui.applyWindow(this);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING);
        setTitle("API keys");
        setContentView(buildContent());
    }

    private View buildContent() {
        LinearLayout screen = new LinearLayout(this);
        screen.setOrientation(LinearLayout.VERTICAL);
        screen.setBackgroundColor(Ui.BACKGROUND);

        screen.addView(topBar());

        scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        scroll.setClipToPadding(false);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(8), dp(18), dp(160));
        scroll.addView(root);
        screen.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
        ));

        TextView note = Ui.text(this, "Add provider keys here first. VoiceFlow only enables provider features after the key is saved.", 14, false, Ui.MUTED);
        note.setPadding(0, 0, 0, dp(8));
        root.addView(note);

        LinearLayout providers = section(root, "Providers");
        openAiInput = keyInput("OpenAI API key");
        openAiInput.setText(Prefs.openAiApiKey(this));
        providers.addView(field("OpenAI", "Transcription and transform", openAiInput));
        providers.addView(divider());

        anthropicInput = keyInput("Anthropic API key");
        anthropicInput.setText(Prefs.anthropicApiKey(this));
        providers.addView(field("Claude", "Transform only", anthropicInput));
        providers.addView(divider());

        xAiInput = keyInput("xAI API key");
        xAiInput.setText(Prefs.xAiApiKey(this));
        providers.addView(field("Grok / xAI", "Transcription and transform", xAiInput));
        providers.addView(divider());

        deepgramInput = keyInput("Deepgram API key");
        deepgramInput.setText(Prefs.deepgramApiKey(this));
        providers.addView(field("Deepgram", "Transcription only", deepgramInput));
        return screen;
    }

    private View topBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackgroundColor(Ui.BACKGROUND);
        Ui.applySystemBarPadding(bar, dp(16), dp(10), dp(16), dp(10));

        TextView back = Ui.topAction(this, "Back", false);
        back.setOnClickListener(v -> finish());
        bar.addView(back);

        TextView title = Ui.text(this, "API keys", 18, true, Ui.TEXT);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        titleParams.setMargins(dp(12), 0, dp(12), 0);
        bar.addView(title, titleParams);

        TextView save = Ui.topAction(this, "Save", true);
        save.setOnClickListener(v -> saveKeys());
        bar.addView(save);
        return bar;
    }

    private LinearLayout section(LinearLayout root, String title) {
        TextView label = Ui.text(this, title, 12, true, Ui.MUTED);
        label.setAllCaps(true);
        label.setLetterSpacing(0.04f);
        label.setPadding(0, dp(18), 0, dp(7));
        root.addView(label);

        LinearLayout section = new LinearLayout(this);
        section.setOrientation(LinearLayout.VERTICAL);
        section.setBackground(Ui.roundedStroke(this, Ui.SURFACE, 18, Ui.DIVIDER));
        root.addView(section, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        return section;
    }

    private View field(String title, String value, EditText input) {
        LinearLayout field = new LinearLayout(this);
        field.setOrientation(LinearLayout.VERTICAL);
        field.setPadding(dp(16), dp(14), dp(16), dp(14));
        field.setFocusable(false);

        LinearLayout line = new LinearLayout(this);
        line.setOrientation(LinearLayout.HORIZONTAL);
        line.setGravity(Gravity.CENTER_VERTICAL);
        line.addView(Ui.text(this, title, 16, true, Ui.TEXT), new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        line.addView(Ui.text(this, value, 12, false, Ui.MUTED));
        field.addView(line);
        field.addView(input);
        input.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                scrollFieldIntoView(field);
            }
        });
        input.setOnClickListener(v -> scrollFieldIntoView(field));
        return field;
    }

    private EditText keyInput(String hint) {
        EditText input = new EditText(this);
        input.setHint(hint);
        input.setTextColor(Ui.TEXT);
        input.setHintTextColor(Ui.MUTED);
        input.setSingleLine(true);
        input.setPadding(0, dp(10), 0, dp(4));
        input.setBackgroundColor(0x00000000);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        return input;
    }

    private void scrollFieldIntoView(View field) {
        if (scroll == null || field == null) {
            return;
        }
        scroll.postDelayed(() -> scrollToField(field), 120);
        scroll.postDelayed(() -> scrollToField(field), 360);
    }

    private void scrollToField(View field) {
        Rect fieldRect = new Rect();
        Rect scrollRect = new Rect();
        field.getDrawingRect(fieldRect);
        scroll.offsetDescendantRectToMyCoords(field, fieldRect);
        scroll.getDrawingRect(scrollRect);
        int topPadding = dp(14);
        int bottomPadding = dp(28);
        if (fieldRect.top < scrollRect.top + topPadding) {
            scroll.smoothScrollTo(0, Math.max(0, fieldRect.top - topPadding));
        } else if (fieldRect.bottom > scrollRect.bottom - bottomPadding) {
            scroll.smoothScrollTo(0, fieldRect.bottom - scrollRect.height() + bottomPadding);
        }
    }

    private void saveKeys() {
        Prefs.saveApiKeys(
                this,
                openAiInput.getText().toString(),
                anthropicInput.getText().toString(),
                xAiInput.getText().toString(),
                deepgramInput.getText().toString()
        );
        Toast.makeText(this, "API keys saved", Toast.LENGTH_SHORT).show();
        finish();
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

    private int dp(int value) {
        return Ui.dp(this, value);
    }
}
