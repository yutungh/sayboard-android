package com.voiceflowkeyboard.ime;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
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

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private TextView providerValue;
    private TextView transcriptionModelValue;
    private TextView transformModelValue;
    private TextView activeProfileValue;
    private CheckBox transformEnabledInput;
    private CheckBox showAllModelsInput;
    private String selectedProvider;
    private String selectedPreset;
    private boolean created;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Ui.applyWindow(this);
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
        selectedProvider = Prefs.transcriptionProvider(this);
        selectedPreset = Prefs.activePreset(this);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.BACKGROUND);
        Ui.applySystemBarPadding(root, dp(18), dp(16), dp(18), dp(22));
        scroll.addView(root);

        root.addView(header());

        LinearLayout setup = section(root, "Setup");
        setup.addView(row("API keys", apiKeySummary(), ">", v -> startActivity(new Intent(this, ApiKeysActivity.class))));
        setup.addView(divider());
        setup.addView(row("Active keyboard", activeKeyboardSummary(), ">", v -> showInputMethodPicker()));

        LinearLayout voice = section(root, "Voice input");
        providerValue = rowValue(providerLabel(selectedProvider));
        voice.addView(row("Provider", providerValue, ">", v -> showProviderDialog()));
        voice.addView(divider());
        transcriptionModelValue = rowValue(Prefs.transcriptionModel(this));
        voice.addView(row("Transcription model", transcriptionModelValue, ">", v -> chooseModel(true)));

        LinearLayout transform = section(root, "Text transform");
        transformEnabledInput = new CheckBox(this);
        transformEnabledInput.setChecked(Prefs.enableTransform(this));
        transform.addView(checkboxRow("Transform transcript", transformEnabledInput, this::saveCurrentSettings));
        transform.addView(divider());
        transformModelValue = rowValue(Prefs.transformModel(this));
        transform.addView(row("Transform model", transformModelValue, ">", v -> chooseModel(false)));
        transform.addView(divider());
        activeProfileValue = rowValue(Prefs.labelForPreset(this, selectedPreset));
        transform.addView(row("Default profile", activeProfileValue, ">", v -> showProfileDialog()));

        LinearLayout prompts = section(root, "Prompts");
        List<PromptProfile> profiles = Prefs.promptProfiles(this);
        for (int i = 0; i < profiles.size(); i++) {
            PromptProfile profile = profiles.get(i);
            prompts.addView(row(profile.name, "Edit prompt", ">", v -> openPrompt(profile.id)));
            prompts.addView(divider());
        }
        prompts.addView(row("+ New prompt", "Create a profile", ">", v -> showNewPromptDialog()));

        LinearLayout replacements = section(root, "Find and replace");
        replacements.addView(row("Personal replacements", replacementSummary(), ">", v -> startActivity(new Intent(this, FindReplaceActivity.class))));

        LinearLayout advanced = section(root, "Advanced");
        showAllModelsInput = new CheckBox(this);
        showAllModelsInput.setChecked(Prefs.showAllOpenAiModels(this));
        advanced.addView(checkboxRow("Show all OpenAI models", showAllModelsInput,
                () -> Prefs.setShowAllOpenAiModels(this, showAllModelsInput.isChecked())));
        advanced.addView(divider());
        advanced.addView(row("Keyboard test", "Open test field", ">", v -> startActivity(new Intent(this, KeyboardTestActivity.class))));
        advanced.addView(divider());
        advanced.addView(row("Android keyboard settings", "System settings", ">", v -> startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))));

        return scroll;
    }

    private View header() {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(0, 0, 0, dp(8));

        TextView title = text("VoiceFlow Keyboard", 26, true, Ui.TEXT);
        title.setIncludeFontPadding(false);
        header.addView(title);

        TextView status = text(setupStatus(), 13, true, Prefs.hasOpenAiApiKey(this) ? Ui.ACCENT : Ui.MUTED);
        status.setPadding(dp(12), 0, dp(12), 0);
        status.setGravity(Gravity.CENTER);
        status.setMinHeight(dp(32));
        status.setBackground(Ui.rounded(this, Prefs.hasOpenAiApiKey(this) ? Ui.ACCENT_SOFT : Ui.SURFACE_ALT, 16));
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        statusParams.setMargins(0, dp(14), 0, 0);
        header.addView(status, statusParams);
        return header;
    }

    private String setupStatus() {
        if (!Prefs.hasOpenAiApiKey(this)) {
            return "OpenAI key needed for cloud transcription";
        }
        if (!isVoiceFlowActive()) {
            return "Keyboard installed, not active";
        }
        return "Ready";
    }

    private LinearLayout section(LinearLayout root, String title) {
        TextView label = text(title, 13, true, Ui.MUTED);
        label.setAllCaps(true);
        label.setLetterSpacing(0.04f);
        label.setPadding(0, dp(20), 0, dp(7));
        root.addView(label);

        LinearLayout section = new LinearLayout(this);
        section.setOrientation(LinearLayout.VERTICAL);
        section.setBackground(Ui.roundedStroke(this, Ui.SURFACE, 18, Ui.DIVIDER));
        section.setPadding(0, dp(2), 0, dp(2));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, 0, 0, dp(2));
        root.addView(section, params);
        return section;
    }

    private View row(String title, String value, String accessory, View.OnClickListener listener) {
        return row(title, rowValue(value), accessory, listener);
    }

    private View row(String title, TextView valueView, String accessory, View.OnClickListener listener) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(66));
        row.setPadding(dp(16), dp(10), dp(12), dp(10));
        row.setBackgroundColor(Color.TRANSPARENT);
        row.setClickable(listener != null);
        if (listener != null) {
            row.setOnClickListener(listener);
        }

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.setGravity(Gravity.CENTER_VERTICAL);
        labels.addView(text(title, 16, true, Ui.TEXT));
        valueView.setPadding(0, dp(5), 0, 0);
        labels.addView(valueView);
        row.addView(labels, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView end = text(accessory, 20, true, Ui.MUTED);
        end.setGravity(Gravity.CENTER);
        row.addView(end, new LinearLayout.LayoutParams(dp(28), LinearLayout.LayoutParams.MATCH_PARENT));
        return row;
    }

    private View checkboxRow(String title, CheckBox checkBox, Runnable onChanged) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(62));
        row.setPadding(dp(16), dp(8), dp(10), dp(8));
        row.setBackgroundColor(Color.TRANSPARENT);
        row.setClickable(true);
        row.setOnClickListener(v -> {
            checkBox.setChecked(!checkBox.isChecked());
            onChanged.run();
        });

        TextView titleView = text(title, 16, true, Ui.TEXT);
        row.addView(titleView, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        checkBox.setButtonTintList(android.content.res.ColorStateList.valueOf(Ui.ACCENT));
        checkBox.setOnClickListener(v -> onChanged.run());
        row.addView(checkBox);
        return row;
    }

    private TextView rowValue(String value) {
        TextView text = text(value == null || value.trim().isEmpty() ? "Not set" : value.trim(), 13, false, Ui.MUTED);
        text.setSingleLine(true);
        return text;
    }

    private View divider() {
        View divider = new View(this);
        divider.setBackgroundColor(Ui.DIVIDER);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                Math.max(1, dp(1))
        );
        params.setMargins(dp(16), 0, 0, 0);
        divider.setLayoutParams(params);
        return divider;
    }

    private void showProviderDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Provider")
                .setItems(PROVIDER_LABELS, (dialog, which) -> {
                    selectedProvider = PROVIDER_VALUES[which];
                    providerValue.setText(providerLabel(selectedProvider));
                    saveCurrentSettings();
                })
                .show();
    }

    private void chooseModel(boolean transcription) {
        String title = transcription ? "Transcription model" : "Transform model";
        String apiKey = Prefs.openAiApiKey(this);
        boolean showAll = Prefs.showAllOpenAiModels(this);
        if (apiKey == null || apiKey.trim().isEmpty()) {
            showModelDialog(
                    title,
                    transcription ? OpenAiClient.defaultTranscriptionModels() : OpenAiClient.defaultTransformModels(),
                    transcription ? transcriptionModelValue : transformModelValue
            );
            Toast.makeText(this, "Add an OpenAI key to refresh models.", Toast.LENGTH_SHORT).show();
            return;
        }

        Toast.makeText(this, "Loading models...", Toast.LENGTH_SHORT).show();
        executor.execute(() -> {
            List<String> models;
            String message = "";
            try {
                List<String> allModels = OpenAiClient.listModels(apiKey);
                if (transcription) {
                    models = showAll
                            ? OpenAiClient.transcriptionModelsFrom(allModels)
                            : OpenAiClient.recommendedTranscriptionModelsFrom(allModels);
                } else {
                    models = showAll
                            ? OpenAiClient.transformModelsFrom(allModels)
                            : OpenAiClient.recommendedTransformModelsFrom(allModels);
                }
            } catch (Exception e) {
                models = transcription
                        ? OpenAiClient.defaultTranscriptionModels()
                        : OpenAiClient.defaultTransformModels();
                message = "Using recommended defaults.";
            }
            List<String> finalModels = models;
            String finalMessage = message;
            runOnUiThread(() -> {
                if (!finalMessage.isEmpty()) {
                    Toast.makeText(this, finalMessage, Toast.LENGTH_SHORT).show();
                }
                showModelDialog(title, finalModels, transcription ? transcriptionModelValue : transformModelValue);
            });
        });
    }

    private void showModelDialog(String title, List<String> models, TextView target) {
        String[] items = models.toArray(new String[0]);
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setItems(items, (dialog, which) -> {
                    target.setText(items[which]);
                    saveCurrentSettings();
                })
                .setNeutralButton("Manual", (dialog, which) -> showManualModelDialog(title, target))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showManualModelDialog(String title, TextView target) {
        EditText input = input("Model ID", 1, false);
        input.setText(target.getText());
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setView(input)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", (dialog, which) -> {
                    target.setText(input.getText().toString().trim());
                    saveCurrentSettings();
                })
                .show();
    }

    private void showProfileDialog() {
        String[] values = Prefs.selectablePresetValues(this);
        String[] labels = Prefs.labelsForPresets(this, values);
        new AlertDialog.Builder(this)
                .setTitle("Default profile")
                .setItems(labels, (dialog, which) -> {
                    selectedPreset = values[which];
                    activeProfileValue.setText(labels[which]);
                    saveCurrentSettings();
                })
                .show();
    }

    private void saveCurrentSettings() {
        Prefs.save(
                this,
                Prefs.openAiApiKey(this),
                selectedProvider,
                transcriptionModelValue.getText().toString(),
                transformModelValue.getText().toString(),
                transformEnabledInput.isChecked(),
                selectedPreset
        );
    }

    private void openPrompt(String id) {
        Intent intent = new Intent(this, PromptEditorActivity.class);
        intent.putExtra(PromptEditorActivity.EXTRA_PROMPT_ID, id);
        startActivity(intent);
    }

    private void showNewPromptDialog() {
        EditText nameInput = input("Prompt name", 1, false);
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

    private String providerLabel(String provider) {
        for (int i = 0; i < PROVIDER_VALUES.length; i++) {
            if (PROVIDER_VALUES[i].equals(provider)) {
                return PROVIDER_LABELS[i];
            }
        }
        return PROVIDER_LABELS[0];
    }

    private String apiKeySummary() {
        int count = Prefs.savedApiKeyCount(this);
        if (count == 0) {
            return "OpenAI not set";
        }
        if (count == 1 && Prefs.hasOpenAiApiKey(this)) {
            return "OpenAI connected";
        }
        return count + " keys saved";
    }

    private String replacementSummary() {
        int count = Prefs.userPhraseReplacements(this).size();
        return count == 0 ? "None" : count + " saved";
    }

    private String activeKeyboardSummary() {
        return isVoiceFlowActive() ? "VoiceFlow" : "Choose keyboard";
    }

    private boolean isVoiceFlowActive() {
        String current = Settings.Secure.getString(getContentResolver(), Settings.Secure.DEFAULT_INPUT_METHOD);
        return current != null && current.contains("com.voiceflowkeyboard.ime/.VoiceFlowKeyboardService");
    }

    private void showInputMethodPicker() {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.showInputMethodPicker();
        }
    }

    private EditText input(String hint, int lines, boolean password) {
        EditText input = new EditText(this);
        input.setHint(hint);
        input.setTextColor(Ui.TEXT);
        input.setHintTextColor(Ui.MUTED);
        input.setSingleLine(lines == 1);
        input.setMinLines(lines);
        input.setPadding(dp(12), dp(8), dp(12), dp(8));
        input.setInputType(password
                ? InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD
                : InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        return input;
    }

    private TextView text(String value, int sp, boolean bold, int color) {
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextSize(sp);
        text.setTextColor(color);
        if (bold) {
            text.setTypeface(Typeface.DEFAULT_BOLD);
        }
        return text;
    }

    private void requestAudioPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 10);
        }
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
