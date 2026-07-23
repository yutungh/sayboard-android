package com.voiceflowkeyboard.ime;

import java.util.HashMap;
import java.util.Map;

final class VoiceHistoryItem {
    static final String OPERATION_DICTATION = "dictation";
    static final String OPERATION_CREATION = "creation";
    static final String OPERATION_TRANSLATION = "translation";

    final String id;
    final long timestampMs;
    final String rawText;
    final String finalText;
    final String preset;
    final int expression;
    final String operation;
    final String targetLanguage;
    final Map<String, String> outputs;

    VoiceHistoryItem(String id, long timestampMs, String rawText, String finalText, String preset) {
        this(
                id,
                timestampMs,
                rawText,
                finalText,
                preset,
                Prefs.DEFAULT_EXPRESSION,
                null,
                OPERATION_DICTATION,
                ""
        );
    }

    VoiceHistoryItem(String id, long timestampMs, String rawText, String finalText, String preset, Map<String, String> outputs) {
        this(id, timestampMs, rawText, finalText, preset, Prefs.DEFAULT_EXPRESSION, outputs, OPERATION_DICTATION, "");
    }

    VoiceHistoryItem(
            String id,
            long timestampMs,
            String rawText,
            String finalText,
            String preset,
            int expression,
            Map<String, String> outputs,
            String operation,
            String targetLanguage
    ) {
        this.id = id;
        this.timestampMs = timestampMs;
        this.rawText = rawText == null ? "" : rawText;
        this.preset = preset == null ? Prefs.PRESET_RAW : preset;
        this.expression = Prefs.sanitizeExpression(expression);
        this.operation = OPERATION_TRANSLATION.equals(operation)
                ? OPERATION_TRANSLATION
                : OPERATION_CREATION.equals(operation) ? OPERATION_CREATION : OPERATION_DICTATION;
        this.targetLanguage = targetLanguage == null ? "" : targetLanguage.trim();
        this.outputs = new HashMap<>();
        if (outputs != null) {
            for (Map.Entry<String, String> entry : outputs.entrySet()) {
                String key = entry.getKey();
                String value = entry.getValue();
                if (key != null && value != null && !value.trim().isEmpty()) {
                    String normalizedKey = Prefs.PRESET_RAW.equals(key)
                            ? Prefs.PRESET_RAW
                            : Prefs.historyVariantKey(
                                    Prefs.historyVariantPreset(key),
                                    Prefs.historyVariantExpression(key)
                            );
                    if (!Prefs.PRESET_RAW.equals(normalizedKey)) {
                        this.outputs.put(normalizedKey, value.trim());
                    }
                }
            }
        }
        if (!Prefs.PRESET_RAW.equals(this.preset) && finalText != null && !finalText.trim().isEmpty()) {
            this.outputs.put(Prefs.historyVariantKey(this.preset, this.expression), finalText.trim());
        }
        this.finalText = outputForVariant(this.preset, this.expression);
    }

    boolean isTranslation() {
        return OPERATION_TRANSLATION.equals(operation);
    }

    boolean isCreation() {
        return OPERATION_CREATION.equals(operation);
    }

    boolean hasOutputForPreset(String preset) {
        int targetExpression = preset != null && preset.equals(this.preset)
                ? expression
                : Prefs.DEFAULT_EXPRESSION;
        return hasOutputForVariant(preset, targetExpression);
    }

    String outputForPreset(String preset) {
        int targetExpression = preset != null && preset.equals(this.preset)
                ? expression
                : Prefs.DEFAULT_EXPRESSION;
        return outputForVariant(preset, targetExpression);
    }

    boolean hasOutputForVariant(String preset, int expression) {
        if (Prefs.PRESET_RAW.equals(preset)) {
            return !rawText.trim().isEmpty();
        }
        String output = outputs.get(Prefs.historyVariantKey(preset, expression));
        return output != null && !output.trim().isEmpty();
    }

    String outputForVariant(String preset, int expression) {
        if (Prefs.PRESET_RAW.equals(preset)) {
            return rawText.trim();
        }
        String output = outputs.get(Prefs.historyVariantKey(preset, expression));
        if (output != null && !output.trim().isEmpty()) {
            return output.trim();
        }
        return "";
    }

    boolean hasOutputForVariantKey(String variantKey) {
        return hasOutputForVariant(
                Prefs.historyVariantPreset(variantKey),
                Prefs.historyVariantExpression(variantKey)
        );
    }

    String outputForVariantKey(String variantKey) {
        return outputForVariant(
                Prefs.historyVariantPreset(variantKey),
                Prefs.historyVariantExpression(variantKey)
        );
    }

    String selectedVariantKey() {
        return Prefs.historyVariantKey(preset, expression);
    }

    String selectedText() {
        String selected = outputForVariant(preset, expression);
        if (!selected.isEmpty()) {
            return selected;
        }
        return rawText.trim();
    }
}
