package com.voiceflowkeyboard.ime;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.inputmethod.InputMethodManager;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

public class SettingsActivity extends Activity {
    private static final String[] PROVIDER_LABELS = {
            "OpenAI audio API",
            "Android device speech"
    };
    private static final String[] PROVIDER_VALUES = {
            Prefs.PROVIDER_OPENAI,
            Prefs.PROVIDER_ANDROID
    };

    private EditText apiKeyInput;
    private Spinner providerSpinner;
    private EditText transcriptionModelInput;
    private EditText transformModelInput;
    private Spinner activePresetSpinner;
    private final EditText[] promptInputs = new EditText[Prefs.EDITABLE_PRESET_VALUES.length];
    private final EditText[] customLabelInputs = new EditText[Prefs.CUSTOM_PRESET_VALUES.length];
    private CheckBox transformEnabledInput;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestAudioPermission();
        setTitle("VoiceFlow Keyboard");
        setContentView(buildContent());
    }

    private View buildContent() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(18), dp(20), dp(24));
        root.setBackgroundColor(Color.rgb(248, 249, 250));
        scroll.addView(root);

        TextView title = text("VoiceFlow Keyboard", 24, true);
        root.addView(title);
        root.addView(text("Configure transcription, cleanup prompts, and keyboard activation.", 14, false));

        apiKeyInput = input("OpenAI API key", true, 1);
        apiKeyInput.setText(Prefs.openAiApiKey(this));
        root.addView(label("OpenAI API key"));
        root.addView(apiKeyInput);

        providerSpinner = new Spinner(this);
        providerSpinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, PROVIDER_LABELS));
        providerSpinner.setSelection(providerIndex(Prefs.transcriptionProvider(this)));
        root.addView(label("Transcription provider"));
        root.addView(providerSpinner);

        transcriptionModelInput = input("gpt-4o-transcribe or whisper-1", false, 1);
        transcriptionModelInput.setText(Prefs.transcriptionModel(this));
        root.addView(label("OpenAI transcription model"));
        root.addView(transcriptionModelInput);

        transformModelInput = input("gpt-5.5", false, 1);
        transformModelInput.setText(Prefs.transformModel(this));
        root.addView(label("Transform model"));
        root.addView(transformModelInput);

        transformEnabledInput = new CheckBox(this);
        transformEnabledInput.setText("Transform transcript before inserting");
        transformEnabledInput.setTextColor(Color.rgb(31, 35, 40));
        transformEnabledInput.setChecked(Prefs.enableTransform(this));
        root.addView(transformEnabledInput);

        activePresetSpinner = new Spinner(this);
        activePresetSpinner.setAdapter(new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_dropdown_item,
                Prefs.labelsForPresets(this, Prefs.SELECTABLE_PRESET_VALUES)
        ));
        activePresetSpinner.setSelection(Prefs.presetIndex(Prefs.SELECTABLE_PRESET_VALUES, Prefs.activePreset(this)));
        root.addView(label("Default transform profile"));
        root.addView(activePresetSpinner);

        root.addView(text("Use Casual for most dictation. Use Professional when you want more polish. Rename Custom profiles for your own workflows.", 13, false));

        for (int i = 0; i < Prefs.EDITABLE_PRESET_VALUES.length; i++) {
            String preset = Prefs.EDITABLE_PRESET_VALUES[i];
            if (Prefs.isCustomPreset(preset)) {
                int customIndex = Prefs.customPresetIndex(preset);
                EditText labelInput = input(Prefs.defaultLabelForPreset(preset), false, 1);
                labelInput.setText(Prefs.labelForPreset(this, preset));
                customLabelInputs[customIndex] = labelInput;
                root.addView(label(Prefs.defaultLabelForPreset(preset) + " name"));
                root.addView(labelInput);
            }

            EditText promptInput = input("Prompt", false, 8);
            promptInput.setText(Prefs.promptForPreset(this, preset));
            promptInput.setGravity(android.view.Gravity.TOP | android.view.Gravity.START);
            promptInputs[i] = promptInput;
            root.addView(label(Prefs.labelForPreset(this, preset) + " prompt"));
            root.addView(promptInput);
        }

        Button save = button("Save settings");
        save.setOnClickListener(v -> saveSettings());
        root.addView(save);

        Button inputSettings = button("Open keyboard settings");
        inputSettings.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)));
        root.addView(inputSettings);

        Button picker = button("Choose active keyboard");
        picker.setOnClickListener(v -> {
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.showInputMethodPicker();
            }
        });
        root.addView(picker);

        Button test = button("Open keyboard test");
        test.setOnClickListener(v -> startActivity(new Intent(this, KeyboardTestActivity.class)));
        root.addView(test);

        root.addView(text("OpenAI audio records the whole clip, then transcribes and formats it after you stop. Android speech is available as a fallback but uses the device speech recognizer.", 13, false));
        return scroll;
    }

    private void saveSettings() {
        Prefs.save(
                this,
                apiKeyInput.getText().toString(),
                PROVIDER_VALUES[providerSpinner.getSelectedItemPosition()],
                transcriptionModelInput.getText().toString(),
                transformModelInput.getText().toString(),
                transformEnabledInput.isChecked(),
                Prefs.SELECTABLE_PRESET_VALUES[activePresetSpinner.getSelectedItemPosition()],
                promptTexts(),
                customLabelTexts()
        );
        Toast.makeText(this, "VoiceFlow Keyboard settings saved", Toast.LENGTH_SHORT).show();
    }

    private String[] promptTexts() {
        String[] prompts = new String[promptInputs.length];
        for (int i = 0; i < promptInputs.length; i++) {
            prompts[i] = promptInputs[i].getText().toString();
        }
        return prompts;
    }

    private String[] customLabelTexts() {
        String[] labels = new String[customLabelInputs.length];
        for (int i = 0; i < customLabelInputs.length; i++) {
            labels[i] = customLabelInputs[i] == null ? "" : customLabelInputs[i].getText().toString();
        }
        return labels;
    }

    private void requestAudioPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 10);
        }
    }

    private int providerIndex(String provider) {
        for (int i = 0; i < PROVIDER_VALUES.length; i++) {
            if (PROVIDER_VALUES[i].equals(provider)) {
                return i;
            }
        }
        return 0;
    }

    private TextView label(String value) {
        TextView label = text(value, 13, true);
        label.setPadding(0, dp(18), 0, dp(6));
        return label;
    }

    private EditText input(String hint, boolean password, int lines) {
        EditText input = new EditText(this);
        input.setHint(hint);
        input.setTextColor(Color.rgb(31, 35, 40));
        input.setHintTextColor(Color.rgb(95, 99, 104));
        input.setSingleLine(lines == 1);
        input.setMinLines(lines);
        input.setPadding(dp(12), dp(8), dp(12), dp(8));
        input.setBackgroundColor(Color.WHITE);
        input.setInputType(password
                ? InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD
                : InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
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
        text.setTextColor(Color.rgb(31, 35, 40));
        if (bold) {
            text.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        }
        return text;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
