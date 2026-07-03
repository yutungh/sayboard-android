package com.voiceflowkeyboard.ime;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
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

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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
    private TextView transcriptionModelInput;
    private TextView transformModelInput;
    private Spinner activePresetSpinner;
    private String[] activePresetValues;
    private CheckBox transformEnabledInput;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private boolean created;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestAudioPermission();
        setTitle("VoiceFlow Keyboard");
        setContentView(buildContent());
        created = true;
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (created) {
            setContentView(buildContent());
        }
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
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

        transcriptionModelInput = dropdownValue(Prefs.transcriptionModel(this), true);
        root.addView(label("OpenAI transcription model"));
        root.addView(transcriptionModelInput);

        transformModelInput = dropdownValue(Prefs.transformModel(this), false);
        root.addView(label("Transform model"));
        root.addView(transformModelInput);

        transformEnabledInput = new CheckBox(this);
        transformEnabledInput.setText("Transform transcript before inserting");
        transformEnabledInput.setTextColor(Color.rgb(31, 35, 40));
        transformEnabledInput.setChecked(Prefs.enableTransform(this));
        root.addView(transformEnabledInput);

        activePresetValues = Prefs.selectablePresetValues(this);
        activePresetSpinner = new Spinner(this);
        activePresetSpinner.setAdapter(new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_dropdown_item,
                Prefs.labelsForPresets(this, activePresetValues)
        ));
        activePresetSpinner.setSelection(Prefs.presetIndex(activePresetValues, Prefs.activePreset(this)));
        root.addView(label("Default transform profile"));
        root.addView(activePresetSpinner);

        root.addView(text("Use Casual for everyday dictation. Use Professional when you want a cleaner rewrite. Add your own prompts for repeat workflows.", 13, false));

        root.addView(label("Prompts"));
        for (PromptProfile profile : Prefs.promptProfiles(this)) {
            Button promptButton = button(profile.name);
            promptButton.setOnClickListener(v -> openPrompt(profile.id));
            root.addView(promptButton);
        }

        Button newPrompt = button("New prompt");
        newPrompt.setOnClickListener(v -> showNewPromptDialog());
        root.addView(newPrompt);

        root.addView(label("Find and replace"));
        root.addView(text("Add personal name and phrase corrections that should be applied after transcription. Built-in technical corrections run quietly in the background.", 13, false));
        Button replacements = button("Edit find and replace");
        replacements.setOnClickListener(v -> startActivity(new Intent(this, FindReplaceActivity.class)));
        root.addView(replacements);

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
                activePresetValues[activePresetSpinner.getSelectedItemPosition()]
        );
        Toast.makeText(this, "VoiceFlow Keyboard settings saved", Toast.LENGTH_SHORT).show();
    }

    private void chooseModel(boolean transcription) {
        String title = transcription ? "Transcription model" : "Transform model";
        Toast.makeText(this, "Loading OpenAI models...", Toast.LENGTH_SHORT).show();
        String apiKey = apiKeyInput.getText().toString();
        executor.execute(() -> {
            List<String> models;
            String message = "";
            try {
                List<String> allModels = OpenAiClient.listModels(apiKey);
                models = transcription
                        ? OpenAiClient.transcriptionModelsFrom(allModels)
                        : OpenAiClient.transformModelsFrom(allModels);
            } catch (Exception e) {
                models = transcription
                        ? OpenAiClient.defaultTranscriptionModels()
                        : OpenAiClient.defaultTransformModels();
                message = "Using fallback model list: " + e.getMessage();
            }
            List<String> finalModels = models;
            String finalMessage = message;
            mainHandler.post(() -> {
                if (!finalMessage.isEmpty()) {
                    Toast.makeText(this, finalMessage, Toast.LENGTH_LONG).show();
                }
                showModelDialog(title, finalModels, transcription ? transcriptionModelInput : transformModelInput);
            });
        });
    }

    private void showModelDialog(String title, List<String> models, TextView target) {
        String[] items = models.toArray(new String[0]);
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setItems(items, (dialog, which) -> target.setText(items[which]))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void openPrompt(String id) {
        Intent intent = new Intent(this, PromptEditorActivity.class);
        intent.putExtra(PromptEditorActivity.EXTRA_PROMPT_ID, id);
        startActivity(intent);
    }

    private void showNewPromptDialog() {
        EditText nameInput = input("Prompt name", false, 1);
        nameInput.setSingleLine(true);
        new AlertDialog.Builder(this)
                .setTitle("New prompt")
                .setView(nameInput)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Create", (dialog, which) -> {
                    String id = Prefs.addPromptProfile(this, nameInput.getText().toString());
                    openPrompt(id);
                })
                .show();
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

    private TextView dropdownValue(String value, boolean transcription) {
        TextView text = new TextView(this);
        text.setText(value == null || value.trim().isEmpty() ? "Tap to load models" : value.trim());
        text.setTextSize(16);
        text.setTextColor(Color.rgb(31, 35, 40));
        text.setGravity(Gravity.CENTER_VERTICAL);
        text.setSingleLine(true);
        text.setPadding(dp(12), dp(12), dp(12), dp(12));
        text.setBackgroundColor(Color.WHITE);
        text.setCompoundDrawablesWithIntrinsicBounds(0, 0, android.R.drawable.arrow_down_float, 0);
        text.setCompoundDrawablePadding(dp(8));
        text.setClickable(true);
        text.setOnClickListener(v -> chooseModel(transcription));
        return text;
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
