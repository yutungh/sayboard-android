package com.voiceflowkeyboard.ime;

import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ModelPickerActivity extends Activity {
    static final String EXTRA_MODE = "mode";
    static final String MODE_TRANSCRIPTION = "transcription";
    static final String MODE_TRANSFORM = "transform";

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private LinearLayout list;
    private CheckBox showAllInput;
    private EditText manualInput;
    private String mode;
    private String provider;
    private boolean transcription;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Ui.applyWindow(this);
        mode = getIntent().getStringExtra(EXTRA_MODE);
        transcription = MODE_TRANSCRIPTION.equals(mode);
        provider = transcription ? Prefs.transcriptionProvider(this) : Prefs.transformProvider(this);
        setTitle(transcription ? "Transcription model" : "Transform model");
        setContentView(buildContent());
        loadModels();
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }

    private View buildContent() {
        LinearLayout screen = new LinearLayout(this);
        screen.setOrientation(LinearLayout.VERTICAL);
        screen.setBackgroundColor(Ui.BACKGROUND);
        screen.addView(topBar());

        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(8), dp(18), dp(24));
        scroll.addView(root);
        screen.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
        ));

        TextView note = Ui.text(this, (transcription
                ? "Pick the model that turns your audio into raw text."
                : "Pick the model that cleans and formats the transcript.")
                + " Current provider: " + Prefs.providerLabel(provider) + ".", 14, false, Ui.MUTED);
        note.setPadding(0, 0, 0, dp(8));
        root.addView(note);

        LinearLayout settings = section(root, "View");
        showAllInput = new CheckBox(this);
        showAllInput.setChecked(Prefs.showAllOpenAiModels(this));
        settings.addView(checkRow("Show all provider models", showAllInput, () -> {
            Prefs.setShowAllOpenAiModels(this, showAllInput.isChecked());
            loadModels();
        }));

        list = section(root, "Recommended models");

        LinearLayout manual = section(root, "Manual model ID");
        manualInput = new EditText(this);
        manualInput.setSingleLine(true);
        manualInput.setText(currentModel());
        manualInput.setTextColor(Ui.TEXT);
        manualInput.setHintTextColor(Ui.MUTED);
        manualInput.setHint("model-id");
        manualInput.setPadding(dp(16), dp(10), dp(16), dp(10));
        manualInput.setBackgroundColor(0x00000000);
        manual.addView(manualInput);
        manual.addView(divider());
        manual.addView(actionRow("Use manual model", "Save exactly what is typed above", v -> selectModel(manualInput.getText().toString())));
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

        TextView title = Ui.text(this, transcription ? "Transcription model" : "Transform model", 18, true, Ui.TEXT);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        titleParams.setMargins(dp(12), 0, 0, 0);
        bar.addView(title, titleParams);
        return bar;
    }

    private void loadModels() {
        list.removeAllViews();
        list.addView(loadingRow("Loading models..."));
        String apiKey = Prefs.apiKeyForProvider(this, provider);
        boolean showAll = Prefs.showAllOpenAiModels(this);
        if (Prefs.PROVIDER_OFFLINE_VOSK.equals(provider)) {
            renderModels(OfflineVoskClient.defaultTranscriptionModels(), OfflineVoskClient.isModelReady(this)
                    ? "The local model is installed and runs on-device."
                    : "This model downloads on first use, then runs on-device.");
            return;
        }
        if (transcription && Prefs.PROVIDER_XAI.equals(provider)) {
            renderModels(XAiClient.defaultTranscriptionModels(), "xAI REST speech-to-text currently exposes Grok transcription through this model.");
            return;
        }
        if (transcription && Prefs.PROVIDER_DEEPGRAM.equals(provider)) {
            renderModels(DeepgramClient.defaultTranscriptionModels(), "Deepgram Nova models are recommended for pre-recorded dictation.");
            return;
        }
        if (apiKey == null || apiKey.trim().isEmpty()) {
            renderModels(defaultModels(), "Add a " + Prefs.providerLabel(provider) + " key to refresh live model availability.");
            return;
        }
        executor.execute(() -> {
            List<String> models;
            String message = showAll ? "All matching models from " + Prefs.providerLabel(provider) + "." : "Recommended models only.";
            try {
                if (Prefs.PROVIDER_OPENAI.equals(provider)) {
                    List<String> all = OpenAiClient.listModels(apiKey);
                    if (transcription) {
                        models = showAll ? OpenAiClient.transcriptionModelsFrom(all) : OpenAiClient.recommendedTranscriptionModelsFrom(all);
                    } else {
                        models = showAll ? OpenAiClient.transformModelsFrom(all) : OpenAiClient.recommendedTransformModelsFrom(all);
                    }
                } else if (Prefs.PROVIDER_XAI.equals(provider)) {
                    models = showAll ? XAiClient.transformModelsFrom(XAiClient.listModels(apiKey)) : defaultModels();
                } else if (Prefs.PROVIDER_ANTHROPIC.equals(provider)) {
                    models = showAll ? AnthropicClient.transformModelsFrom(AnthropicClient.listModels(apiKey)) : defaultModels();
                } else {
                    models = defaultModels();
                }
            } catch (Exception e) {
                models = defaultModels();
                message = "Using defaults because live model loading failed.";
            }
            List<String> finalModels = new ArrayList<>(models);
            String finalMessage = message;
            runOnUiThread(() -> renderModels(finalModels, finalMessage));
        });
    }

    private List<String> defaultModels() {
        if (transcription) {
            if (Prefs.PROVIDER_XAI.equals(provider)) {
                return XAiClient.defaultTranscriptionModels();
            }
            if (Prefs.PROVIDER_DEEPGRAM.equals(provider)) {
                return DeepgramClient.defaultTranscriptionModels();
            }
            if (Prefs.PROVIDER_OFFLINE_VOSK.equals(provider)) {
                return OfflineVoskClient.defaultTranscriptionModels();
            }
            return OpenAiClient.defaultTranscriptionModels();
        }
        if (Prefs.PROVIDER_XAI.equals(provider)) {
            return XAiClient.defaultTransformModels();
        }
        if (Prefs.PROVIDER_ANTHROPIC.equals(provider)) {
            return AnthropicClient.defaultTransformModels();
        }
        return OpenAiClient.defaultTransformModels();
    }

    private void renderModels(List<String> models, String message) {
        list.removeAllViews();
        if (message != null && !message.trim().isEmpty()) {
            TextView note = Ui.text(this, message, 13, false, Ui.MUTED);
            note.setPadding(dp(16), dp(14), dp(16), dp(14));
            list.addView(note);
            list.addView(divider());
        }
        for (int i = 0; i < models.size(); i++) {
            String model = models.get(i);
            list.addView(modelRow(model));
            if (i < models.size() - 1) {
                list.addView(divider());
            }
        }
    }

    private View modelRow(String model) {
        boolean current = model.equalsIgnoreCase(currentModel());
        return row(model, current ? "Current" : modelDescription(model), current ? "Selected" : ">", v -> selectModel(model));
    }

    private View row(String title, String subtitle, String accessory, View.OnClickListener listener) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), dp(14), dp(14), dp(14));
        row.setClickable(true);
        row.setOnClickListener(listener);

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.addView(Ui.text(this, title, 16, true, Ui.TEXT));
        TextView sub = Ui.text(this, subtitle, 13, false, Ui.MUTED);
        sub.setPadding(0, dp(5), 0, 0);
        labels.addView(sub);
        row.addView(labels, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(Ui.text(this, accessory, "Selected".equals(accessory) ? 13 : 20, true, "Selected".equals(accessory) ? Ui.ACCENT : Ui.MUTED));
        return row;
    }

    private View actionRow(String title, String subtitle, View.OnClickListener listener) {
        return row(title, subtitle, ">", listener);
    }

    private View loadingRow(String text) {
        TextView view = Ui.text(this, text, 15, false, Ui.MUTED);
        view.setPadding(dp(16), dp(18), dp(16), dp(18));
        return view;
    }

    private View checkRow(String title, CheckBox checkBox, Runnable onChanged) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), dp(12), dp(10), dp(12));
        row.setClickable(true);
        row.setOnClickListener(v -> {
            checkBox.setChecked(!checkBox.isChecked());
            onChanged.run();
        });
        row.addView(Ui.text(this, title, 16, true, Ui.TEXT), new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        checkBox.setButtonTintList(android.content.res.ColorStateList.valueOf(Ui.ACCENT));
        checkBox.setOnClickListener(v -> onChanged.run());
        row.addView(checkBox);
        return row;
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
        root.addView(section);
        return section;
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

    private void selectModel(String model) {
        String trimmed = model == null ? "" : model.trim();
        if (trimmed.isEmpty()) {
            Toast.makeText(this, "Enter a model ID first.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (transcription) {
            Prefs.setTranscriptionModel(this, trimmed);
        } else {
            Prefs.setTransformModel(this, trimmed);
        }
        Toast.makeText(this, "Model saved", Toast.LENGTH_SHORT).show();
        finish();
    }

    private String currentModel() {
        return transcription ? Prefs.transcriptionModel(this) : Prefs.transformModel(this);
    }

    private String modelDescription(String model) {
        String lower = model.toLowerCase();
        if (lower.contains("mini")) {
            return "Faster and cheaper";
        }
        if (lower.contains("haiku")) {
            return "Fast Claude model";
        }
        if (lower.contains("sonnet")) {
            return "Balanced Claude model";
        }
        if (lower.contains("grok")) {
            return lower.contains("transcribe") ? "xAI speech-to-text" : "xAI transform model";
        }
        if (lower.contains("nova")) {
            return "Deepgram speech-to-text";
        }
        if (lower.contains("vosk")) {
            return "Local offline speech-to-text";
        }
        if (lower.contains("transcribe")) {
            return "Speech-to-text";
        }
        if (lower.contains("whisper")) {
            return "Legacy Whisper transcription";
        }
        return "Higher quality, usually slower";
    }

    private int dp(int value) {
        return Ui.dp(this, value);
    }
}
