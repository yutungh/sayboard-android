package com.sayboard.ime;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.InsetDrawable;
import android.inputmethodservice.InputMethodService;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.textservice.SentenceSuggestionsInfo;
import android.view.textservice.SpellCheckerSession;
import android.view.textservice.SuggestionsInfo;
import android.view.textservice.TextInfo;
import android.view.textservice.TextServicesManager;
import android.widget.LinearLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.PopupMenu;
import android.widget.TextView;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SayboardKeyboardService extends InputMethodService {
    private static final int KEY_HEIGHT_DP = 48;
    private static final int KEY_VISUAL_GAP_DP = 3;
    private static final int SPELL_CHECK_DELAY_MS = 120;
    private static final Map<String, String> COMMON_TYPOS = commonTypos();
    private static final PhraseReplacement[] PHRASE_REPLACEMENTS = {
            new PhraseReplacement("Cloud Code", "Claude Code")
    };

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final List<TextView> keyButtons = new ArrayList<>();

    private Palette colors;
    private LinearLayout keyboardPanel;
    private LinearLayout chipStrip;
    private TextView statusText;
    private ImageButton micButton;
    private TextView presetButton;
    private boolean recording;
    private boolean processing;
    private boolean shift;
    private boolean symbolsMode;
    private boolean symbolsMoreMode;
    private boolean deleteHeld;
    private float downX;
    private long deleteHoldStartMs;
    private String selectedPreset;
    private MediaRecorder recorder;
    private File currentAudioFile;
    private SpeechRecognizer speechRecognizer;
    private SpellCheckerSession spellCheckerSession;
    private Runnable deleteRepeatRunnable;
    private Runnable statusSpinnerRunnable;
    private Runnable spellCheckRunnable;
    private String statusSpinnerBase = "";
    private String pendingAutoCorrectWord = "";
    private String pendingAutoCorrectReplacement = "";
    private boolean pendingAutoCorrectAccept;
    private int spellCheckGeneration;
    private int statusSpinnerStep;

    @Override
    public View onCreateInputView() {
        colors = Palette.from(this);
        selectedPreset = Prefs.activePreset(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(5), dp(5), dp(5), dp(6));
        root.setBackgroundColor(colors.background);
        root.setOnTouchListener(this::handleSwipe);

        root.addView(buildStrip());
        keyboardPanel = buildKeyboardPanel();
        keyboardPanel.setOnTouchListener(this::handleSwipe);
        root.addView(keyboardPanel);
        showIdleChips();
        return root;
    }

    @Override
    public void onDestroy() {
        stopRecorderSilently();
        destroySpeechRecognizer();
        destroySpellChecker();
        executor.shutdownNow();
        super.onDestroy();
    }

    @Override
    public void onStartInput(EditorInfo attribute, boolean restarting) {
        super.onStartInput(attribute, restarting);
        clearAutoCorrection();
    }

    @Override
    public void onFinishInput() {
        clearAutoCorrection();
        super.onFinishInput();
    }

    private boolean handleSwipe(View view, MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            downX = event.getX();
        } else if (event.getAction() == MotionEvent.ACTION_UP) {
            float delta = event.getX() - downX;
            if (recording && Math.abs(delta) > dp(70)) {
                cyclePreset(delta < 0 ? 1 : -1);
                return true;
            }
        }
        return false;
    }

    private LinearLayout buildStrip() {
        LinearLayout outer = new LinearLayout(this);
        outer.setOrientation(LinearLayout.VERTICAL);
        outer.setPadding(0, 0, 0, dp(4));

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);

        statusText = new TextView(this);
        statusText.setText("Ready");
        statusText.setTextColor(colors.text);
        statusText.setTextSize(14);
        statusText.setSingleLine(true);
        statusText.setEllipsize(TextUtils.TruncateAt.END);
        statusText.setGravity(Gravity.CENTER_VERTICAL);
        statusText.setPadding(dp(6), 0, dp(10), 0);
        statusText.setBackgroundColor(Color.TRANSPARENT);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(0, dp(36), 1f);
        statusParams.setMargins(dp(2), dp(2), dp(5), dp(2));
        top.addView(statusText, statusParams);

        micButton = micButton();
        micButton.setOnClickListener(v -> {
            haptic(v);
            toggleVoiceCapture();
        });
        top.addView(micButton);

        presetButton = toolButton(presetDropdownText());
        presetButton.setOnClickListener(v -> {
            haptic(v);
            showPresetMenu(v);
        });
        top.addView(presetButton);

        chipStrip = new LinearLayout(this);
        chipStrip.setOrientation(LinearLayout.HORIZONTAL);
        chipStrip.setGravity(Gravity.CENTER_VERTICAL);
        HorizontalScrollView chipScroller = new HorizontalScrollView(this);
        chipScroller.setHorizontalScrollBarEnabled(false);
        chipScroller.addView(chipStrip);
        outer.addView(top);
        outer.addView(chipScroller);
        return outer;
    }

    private LinearLayout buildKeyboardPanel() {
        keyButtons.clear();
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        populateKeyboardPanel(panel);
        return panel;
    }

    private void populateKeyboardPanel(LinearLayout panel) {
        panel.removeAllViews();
        keyButtons.clear();
        if (symbolsMode) {
            if (symbolsMoreMode) {
                panel.addView(keyRow(new String[]{"[", "]", "{", "}", "#", "%", "^", "*", "+", "="}));
                panel.addView(keyRow(new String[]{"_", "\\", "|", "~", "<", ">", "€", "£", "¥", "•"}));
            } else {
                panel.addView(keyRow(new String[]{"1", "2", "3", "4", "5", "6", "7", "8", "9", "0"}));
                panel.addView(keyRow(new String[]{"-", "/", ":", ";", "(", ")", "$", "&", "@", "\""}));
            }
            LinearLayout third = new LinearLayout(this);
            third.setOrientation(LinearLayout.HORIZONTAL);
            third.addView(keyButton(symbolsMoreMode ? "123" : "#+=", 1.2f, v -> toggleMoreSymbolsMode(), true));
            String[] thirdRow = symbolsMoreMode
                    ? new String[]{"`", "…", "—", "–", "¿", "¡", "°"}
                    : new String[]{".", ",", "?", "!", "'", "_", "+"};
            for (String value : thirdRow) {
                third.addView(keyButton(value, 1f, v -> commitKey(((TextView) v).getText().toString())));
            }
            third.addView(deleteKey());
            panel.addView(third);
            panel.addView(bottomRow("ABC"));
            return;
        }

        panel.addView(keyRow("qwertyuiop"));
        panel.addView(letterMiddleRow());

        LinearLayout third = new LinearLayout(this);
        third.setOrientation(LinearLayout.HORIZONTAL);
        third.addView(keyButton("shift", 1.35f, v -> {
            shift = !shift;
            setStatus(shift ? "Shift on" : "Ready");
        }, true));
        for (char c : "zxcvbnm".toCharArray()) {
            third.addView(keyButton(String.valueOf(c), 1f, v -> commitKey(((TextView) v).getText().toString())));
        }
        third.addView(deleteKey());
        panel.addView(third);
        panel.addView(bottomRow("?123"));
    }

    private LinearLayout bottomRow(String modeLabel) {
        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.HORIZONTAL);
        bottom.setGravity(Gravity.CENTER_VERTICAL);
        String normalizedMode = modeLabel.replace("?", "");
        bottom.addView(keyButton(normalizedMode, 1.4f, v -> toggleSymbolsMode(), true));
        bottom.addView(keyButton("space", symbolsMode ? 5.45f : 5.7f, v -> commitSeparator(" ")));
        if (!symbolsMode) {
            bottom.addView(keyButton(".", 0.9f, v -> commitSeparator(".")));
        }
        bottom.addView(keyButton("return", 1.65f, v -> sendEnter(), true));
        return bottom;
    }

    private LinearLayout letterMiddleRow() {
        LinearLayout outer = new LinearLayout(this);
        outer.setOrientation(LinearLayout.HORIZONTAL);
        outer.setPadding(dp(24), 0, dp(24), 0);
        outer.addView(keyRow("asdfghjkl"), new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        return outer;
    }

    private LinearLayout keyRow(String chars) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        for (char c : chars.toCharArray()) {
            row.addView(keyButton(String.valueOf(c), 1f, v -> commitKey(((TextView) v).getText().toString())));
        }
        return row;
    }

    private LinearLayout keyRow(String[] labels) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        for (String label : labels) {
            row.addView(keyButton(label, 1f, v -> commitKey(((TextView) v).getText().toString())));
        }
        return row;
    }

    private void showIdleChips() {
        if (showAutoCorrectionChip()) {
            return;
        }
        chipStrip.removeAllViews();
        chipStrip.setVisibility(View.GONE);
    }

    private boolean showAutoCorrectionChip() {
        if (chipStrip == null || recording || processing || pendingAutoCorrectReplacement.isEmpty()) {
            return false;
        }
        chipStrip.removeAllViews();
        chipStrip.setVisibility(View.VISIBLE);
        TextView chip = chip(pendingAutoCorrectReplacement, v -> applyPendingAutoCorrection(false));
        chipStrip.addView(chip);
        return true;
    }

    private void showRecordingChips() {
        chipStrip.removeAllViews();
        chipStrip.setVisibility(View.VISIBLE);
        for (int i = 0; i < Prefs.PRESET_VALUES.length; i++) {
            final String preset = Prefs.PRESET_VALUES[i];
            TextView chip = chip(Prefs.PRESET_LABELS[i], v -> {
                selectedPreset = preset;
                Prefs.setActivePreset(this, selectedPreset);
                if (presetButton != null) {
                    presetButton.setText(presetDropdownText());
                }
                showRecordingChips();
                setStatus(recording ? "Recording: " + selectedPresetLabel() : "Preset: " + selectedPresetLabel());
            });
            stylePresetChip(chip, preset.equals(selectedPreset));
            chipStrip.addView(chip);
        }
    }

    private TextView chip(String text, View.OnClickListener listener) {
        TextView chip = toolButton(text);
        chip.setOnClickListener(v -> {
            haptic(v);
            listener.onClick(v);
        });
        return chip;
    }

    private void showPresetMenu(View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor);
        for (int i = 0; i < Prefs.PRESET_VALUES.length; i++) {
            menu.getMenu().add(0, i, i, Prefs.PRESET_LABELS[i]);
        }
        menu.getMenu().add(1, 100, 100, "Settings");
        menu.setOnMenuItemClickListener(item -> {
            haptic(anchor);
            if (item.getItemId() == 100) {
                openSettings();
                return true;
            }
            int index = item.getItemId();
            if (index >= 0 && index < Prefs.PRESET_VALUES.length) {
                selectedPreset = Prefs.PRESET_VALUES[index];
                Prefs.setActivePreset(this, selectedPreset);
                if (presetButton != null) {
                    presetButton.setText(presetDropdownText());
                }
                if (recording) {
                    showRecordingChips();
                    setStatus("Recording: " + selectedPresetLabel());
                } else {
                    setStatus("Preset: " + selectedPresetLabel());
                }
                return true;
            }
            return false;
        });
        menu.show();
    }

    private String presetDropdownText() {
        return labelForPreset(selectedPreset) + " ▾";
    }

    private TextView toolButton(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(12);
        view.setTypeface(Typeface.DEFAULT_BOLD);
        view.setSingleLine(true);
        view.setGravity(Gravity.CENTER);
        view.setTextColor(colors.text);
        view.setBackground(keyBackground(colors.key, false));
        view.setClickable(true);
        view.setMinWidth(0);
        view.setMinHeight(0);
        view.setIncludeFontPadding(false);
        view.setPadding(dp(14), 0, dp(14), 0);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(32));
        params.setMargins(dp(2), dp(2), dp(2), dp(2));
        view.setLayoutParams(params);
        return view;
    }

    private ImageButton micButton() {
        ImageButton button = new ImageButton(this);
        button.setImageResource(R.drawable.ic_mic_24);
        button.setColorFilter(colors.text);
        button.setBackground(ovalBackground(colors.key, false));
        button.setScaleType(ImageButton.ScaleType.CENTER);
        button.setPadding(dp(8), dp(8), dp(8), dp(8));
        button.setMinimumWidth(0);
        button.setMinimumHeight(0);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(38), dp(38));
        params.setMargins(dp(2), dp(2), dp(2), dp(2));
        button.setLayoutParams(params);
        return button;
    }

    private TextView keyButton(String text, float weight, View.OnClickListener listener) {
        return keyButton(text, weight, listener, false);
    }

    private TextView keyButton(String text, float weight, View.OnClickListener listener, boolean utility) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(text.length() > 5 ? 10 : text.length() > 3 ? 11 : 18);
        view.setTypeface(utility || text.length() > 3 ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        view.setSingleLine(true);
        view.setGravity(Gravity.CENTER);
        view.setTextColor(colors.text);
        view.setBackground(keyVisualBackground(utility ? colors.keyAlt : colors.key, false));
        view.setClickable(true);
        view.setMinWidth(0);
        view.setMinHeight(0);
        view.setIncludeFontPadding(false);
        view.setPadding(0, 0, 0, 0);
        view.setOnClickListener(v -> {
            haptic(v);
            listener.onClick(v);
        });
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(KEY_HEIGHT_DP), weight);
        params.setMargins(0, 0, 0, 0);
        view.setLayoutParams(params);
        keyButtons.add(view);
        return view;
    }

    private TextView deleteKey() {
        TextView key = keyButton("del", 1.35f, v -> {
        }, true);
        key.setOnTouchListener((view, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                haptic(view);
                startDeleteHold();
                return true;
            }
            if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
                stopDeleteHold();
                return true;
            }
            return true;
        });
        return key;
    }

    private void stylePresetChip(TextView chip, boolean selected) {
        chip.setTextColor(selected ? colors.onAccent : colors.text);
        chip.setBackground(keyBackground(selected ? colors.accent : colors.key, selected));
    }

    private void setKeyboardLocked(boolean locked) {
        for (TextView button : keyButtons) {
            button.setEnabled(!locked);
        }
        if (keyboardPanel != null) {
            keyboardPanel.setAlpha(locked ? 0.38f : 1f);
        }
    }

    private void commitKey(String value) {
        if (recording || processing) {
            return;
        }
        String text = symbolsMode ? value : (shift ? value.toUpperCase(Locale.US) : value.toLowerCase(Locale.US));
        shift = false;
        if (isSeparator(text)) {
            commitSeparator(text);
            return;
        }
        commitText(text);
        if (isAutoCorrectWordCharacter(text)) {
            scheduleAutoCorrection();
        } else {
            clearAutoCorrection();
        }
    }

    private void commitSeparator(String separator) {
        if (recording || processing) {
            return;
        }
        applyPendingAutoCorrection(true);
        applyRecentPhraseReplacement();
        commitText(separator);
        clearAutoCorrection();
    }

    private void commitText(String text) {
        InputConnection connection = getCurrentInputConnection();
        if (connection != null) {
            connection.commitText(text, 1);
        }
    }

    private void insertVoiceText(String text) {
        InputConnection connection = getCurrentInputConnection();
        if (connection == null) {
            return;
        }
        String prepared = prepareVoiceOutput(text);
        if (prepared.isEmpty()) {
            return;
        }
        if (needsLeadingSpace(connection, prepared)) {
            prepared = " " + prepared;
        }
        connection.commitText(prepared, 1);
        clearAutoCorrection();
    }

    private String prepareVoiceOutput(String text) {
        String result = text == null ? "" : text.trim();
        result = applyPhraseReplacements(result);
        result = removeShortTrailingPeriod(result);
        return result;
    }

    private String applyPhraseReplacements(String text) {
        String result = text;
        for (PhraseReplacement replacement : PHRASE_REPLACEMENTS) {
            Pattern pattern = Pattern.compile("(?i)(?<![A-Za-z])" + Pattern.quote(replacement.from) + "(?![A-Za-z])");
            Matcher matcher = pattern.matcher(result);
            result = matcher.replaceAll(Matcher.quoteReplacement(replacement.to));
        }
        return result;
    }

    private String removeShortTrailingPeriod(String text) {
        if (text.indexOf('\n') >= 0 || !text.endsWith(".") || text.endsWith("...")) {
            return text;
        }
        String withoutPeriod = text.substring(0, text.length() - 1).trim();
        int words = countWords(withoutPeriod);
        if (words > 0 && words < 5) {
            return withoutPeriod;
        }
        return text;
    }

    private int countWords(String text) {
        if (text.isEmpty()) {
            return 0;
        }
        int count = 0;
        boolean inWord = false;
        for (int i = 0; i < text.length(); i++) {
            boolean word = Character.isLetterOrDigit(text.charAt(i));
            if (word && !inWord) {
                count++;
            }
            inWord = word;
        }
        return count;
    }

    private boolean needsLeadingSpace(InputConnection connection, String text) {
        if (text.isEmpty() || startsWithSpacingOrPunctuation(text)) {
            return false;
        }
        CharSequence before = connection.getTextBeforeCursor(1, 0);
        if (before == null || before.length() == 0) {
            return false;
        }
        char previous = before.charAt(before.length() - 1);
        return !Character.isWhitespace(previous) && "([{/'\"".indexOf(previous) < 0;
    }

    private boolean startsWithSpacingOrPunctuation(String text) {
        char first = text.charAt(0);
        return Character.isWhitespace(first) || ".,?!:;)]}/'\"".indexOf(first) >= 0;
    }

    private void deleteOne() {
        if (recording || processing) {
            return;
        }
        InputConnection connection = getCurrentInputConnection();
        if (connection != null) {
            connection.deleteSurroundingText(1, 0);
        }
        scheduleAutoCorrection();
    }

    private void deletePreviousWord() {
        if (recording || processing) {
            return;
        }
        InputConnection connection = getCurrentInputConnection();
        if (connection == null) {
            return;
        }
        CharSequence before = connection.getTextBeforeCursor(80, 0);
        if (before == null || before.length() == 0) {
            return;
        }
        int i = before.length() - 1;
        int count = 0;
        while (i >= 0 && Character.isWhitespace(before.charAt(i))) {
            i--;
            count++;
        }
        while (i >= 0 && !Character.isWhitespace(before.charAt(i))) {
            i--;
            count++;
        }
        connection.deleteSurroundingText(Math.max(count, 1), 0);
    }

    private void startDeleteHold() {
        if (recording || processing) {
            return;
        }
        deleteHeld = true;
        deleteHoldStartMs = System.currentTimeMillis();
        deleteOne();
        if (deleteRepeatRunnable == null) {
            deleteRepeatRunnable = () -> {
                if (!deleteHeld) {
                    return;
                }
                long heldMs = System.currentTimeMillis() - deleteHoldStartMs;
                if (heldMs >= 3000) {
                    deletePreviousWord();
                    mainHandler.postDelayed(deleteRepeatRunnable, 320);
                } else {
                    deleteOne();
                    mainHandler.postDelayed(deleteRepeatRunnable, 75);
                }
            };
        }
        mainHandler.postDelayed(deleteRepeatRunnable, 350);
    }

    private void stopDeleteHold() {
        deleteHeld = false;
        if (deleteRepeatRunnable != null) {
            mainHandler.removeCallbacks(deleteRepeatRunnable);
        }
    }

    private void scheduleAutoCorrection() {
        if (!shouldAutoCorrectTyping()) {
            clearAutoCorrection();
            return;
        }
        if (spellCheckRunnable == null) {
            spellCheckRunnable = this::requestAutoCorrectionForCurrentWord;
        }
        mainHandler.removeCallbacks(spellCheckRunnable);
        mainHandler.postDelayed(spellCheckRunnable, SPELL_CHECK_DELAY_MS);
    }

    private void requestAutoCorrectionForCurrentWord() {
        if (!shouldAutoCorrectTyping()) {
            clearAutoCorrection();
            return;
        }
        String word = currentWordBeforeCursor();
        if (word.length() < 2 || containsDigit(word)) {
            clearAutoCorrection();
            return;
        }

        String fallback = fallbackCorrectionFor(word);
        if (!fallback.isEmpty()) {
            setPendingAutoCorrection(word, fallback, true);
            return;
        }

        ensureSpellChecker();
        if (spellCheckerSession == null) {
            clearAutoCorrection();
            return;
        }
        int generation = ++spellCheckGeneration;
        spellCheckerSession.getSuggestions(new TextInfo[]{new TextInfo(word, 0, generation)}, 3, false);
    }

    private void ensureSpellChecker() {
        if (spellCheckerSession != null) {
            return;
        }
        TextServicesManager manager = (TextServicesManager) getSystemService(Context.TEXT_SERVICES_MANAGER_SERVICE);
        if (manager == null || !manager.isSpellCheckerEnabled()) {
            return;
        }
        spellCheckerSession = manager.newSpellCheckerSession(
                null,
                Locale.getDefault(),
                new SpellCheckerSession.SpellCheckerSessionListener() {
                    @Override
                    public void onGetSuggestions(SuggestionsInfo[] results) {
                        handleSpellSuggestions(results);
                    }

                    @Override
                    public void onGetSentenceSuggestions(SentenceSuggestionsInfo[] results) {
                    }
                },
                true
        );
    }

    private void handleSpellSuggestions(SuggestionsInfo[] results) {
        if (results == null || results.length == 0) {
            return;
        }
        SuggestionsInfo info = results[0];
        int generation = info.getSequence();
        mainHandler.post(() -> applySpellSuggestions(generation, info));
    }

    private void applySpellSuggestions(int generation, SuggestionsInfo info) {
        if (generation != spellCheckGeneration || !shouldAutoCorrectTyping()) {
            return;
        }
        String word = currentWordBeforeCursor();
        if (word.length() < 2) {
            clearAutoCorrection();
            return;
        }

        int attrs = info.getSuggestionsAttributes();
        if ((attrs & SuggestionsInfo.RESULT_ATTR_IN_THE_DICTIONARY) != 0) {
            clearAutoCorrection();
            return;
        }

        String replacement = "";
        for (int i = 0; i < info.getSuggestionsCount(); i++) {
            String candidate = info.getSuggestionAt(i);
            if (candidate != null && !candidate.equalsIgnoreCase(word) && sameLeadingLetter(word, candidate)) {
                replacement = matchCase(word, candidate);
                break;
            }
        }
        if (replacement.isEmpty()) {
            clearAutoCorrection();
            return;
        }

        boolean recommended = (attrs & SuggestionsInfo.RESULT_ATTR_HAS_RECOMMENDED_SUGGESTIONS) != 0;
        setPendingAutoCorrection(word, replacement, recommended);
    }

    private boolean applyPendingAutoCorrection(boolean autoOnly) {
        if (pendingAutoCorrectReplacement.isEmpty() || (autoOnly && !pendingAutoCorrectAccept)) {
            return false;
        }
        InputConnection connection = getCurrentInputConnection();
        if (connection == null) {
            return false;
        }
        String word = currentWordBeforeCursor();
        if (!word.equals(pendingAutoCorrectWord)) {
            clearAutoCorrection();
            return false;
        }
        connection.deleteSurroundingText(word.length(), 0);
        connection.commitText(pendingAutoCorrectReplacement, 1);
        clearAutoCorrection();
        return true;
    }

    private void setPendingAutoCorrection(String word, String replacement, boolean autoAccept) {
        pendingAutoCorrectWord = word;
        pendingAutoCorrectReplacement = replacement;
        pendingAutoCorrectAccept = autoAccept;
        showIdleChips();
    }

    private void clearAutoCorrection() {
        pendingAutoCorrectWord = "";
        pendingAutoCorrectReplacement = "";
        pendingAutoCorrectAccept = false;
        if (spellCheckRunnable != null) {
            mainHandler.removeCallbacks(spellCheckRunnable);
        }
        if (chipStrip != null && !recording && !processing) {
            chipStrip.removeAllViews();
            chipStrip.setVisibility(View.GONE);
        }
    }

    private boolean shouldAutoCorrectTyping() {
        if (recording || processing || symbolsMode) {
            return false;
        }
        EditorInfo info = getCurrentInputEditorInfo();
        if (info == null) {
            return true;
        }
        int inputType = info.inputType;
        if ((inputType & InputType.TYPE_MASK_CLASS) != InputType.TYPE_CLASS_TEXT) {
            return false;
        }
        if ((inputType & InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS) != 0) {
            return false;
        }
        int variation = inputType & InputType.TYPE_MASK_VARIATION;
        return !isSensitiveTextVariation(variation);
    }

    private boolean shouldAllowVoiceCapture() {
        EditorInfo info = getCurrentInputEditorInfo();
        if (info == null) {
            return true;
        }
        int inputType = info.inputType;
        if ((inputType & InputType.TYPE_MASK_CLASS) != InputType.TYPE_CLASS_TEXT) {
            return false;
        }
        int variation = inputType & InputType.TYPE_MASK_VARIATION;
        return !isSensitiveTextVariation(variation);
    }

    private boolean isSensitiveTextVariation(int variation) {
        return variation == InputType.TYPE_TEXT_VARIATION_PASSWORD
                || variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                || variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
                || variation == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
                || variation == InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS
                || variation == InputType.TYPE_TEXT_VARIATION_URI;
    }

    private String currentWordBeforeCursor() {
        InputConnection connection = getCurrentInputConnection();
        if (connection == null) {
            return "";
        }
        CharSequence before = connection.getTextBeforeCursor(64, 0);
        if (before == null || before.length() == 0) {
            return "";
        }
        int end = before.length();
        int start = end;
        while (start > 0 && isAutoCorrectWordCharacter(before.charAt(start - 1))) {
            start--;
        }
        return before.subSequence(start, end).toString();
    }

    private boolean isAutoCorrectWordCharacter(String text) {
        return text.length() == 1 && isAutoCorrectWordCharacter(text.charAt(0));
    }

    private boolean isAutoCorrectWordCharacter(char value) {
        return Character.isLetter(value) || value == '\'';
    }

    private boolean isSeparator(String text) {
        return " ".equals(text)
                || ".".equals(text)
                || ",".equals(text)
                || "?".equals(text)
                || "!".equals(text)
                || ":".equals(text)
                || ";".equals(text);
    }

    private boolean containsDigit(String word) {
        for (int i = 0; i < word.length(); i++) {
            if (Character.isDigit(word.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    private String fallbackCorrectionFor(String word) {
        String replacement = COMMON_TYPOS.get(word.toLowerCase(Locale.US));
        return replacement == null ? "" : matchCase(word, replacement);
    }

    private boolean sameLeadingLetter(String word, String candidate) {
        if (word.isEmpty() || candidate == null || candidate.isEmpty()) {
            return false;
        }
        return Character.toLowerCase(word.charAt(0)) == Character.toLowerCase(candidate.charAt(0));
    }

    private String matchCase(String original, String replacement) {
        if (original.equals(original.toUpperCase(Locale.US))) {
            return replacement.toUpperCase(Locale.US);
        }
        if (Character.isUpperCase(original.charAt(0))) {
            return replacement.substring(0, 1).toUpperCase(Locale.US) + replacement.substring(1);
        }
        return replacement;
    }

    private void applyRecentPhraseReplacement() {
        InputConnection connection = getCurrentInputConnection();
        if (connection == null) {
            return;
        }
        CharSequence before = connection.getTextBeforeCursor(96, 0);
        if (before == null || before.length() == 0) {
            return;
        }
        String text = before.toString();
        for (PhraseReplacement replacement : PHRASE_REPLACEMENTS) {
            if (endsWithPhrase(text, replacement.from)) {
                connection.deleteSurroundingText(replacement.from.length(), 0);
                connection.commitText(replacement.to, 1);
                return;
            }
        }
    }

    private boolean endsWithPhrase(String text, String phrase) {
        if (text.length() < phrase.length()) {
            return false;
        }
        String tail = text.substring(text.length() - phrase.length());
        if (!tail.equalsIgnoreCase(phrase)) {
            return false;
        }
        int boundary = text.length() - phrase.length() - 1;
        return boundary < 0 || !isAutoCorrectWordCharacter(text.charAt(boundary));
    }

    private void sendEnter() {
        if (recording || processing) {
            return;
        }
        InputConnection connection = getCurrentInputConnection();
        if (connection == null) {
            return;
        }
        applyPendingAutoCorrection(true);
        applyRecentPhraseReplacement();
        EditorInfo info = getCurrentInputEditorInfo();
        int action = info == null ? EditorInfo.IME_ACTION_NONE : info.imeOptions & EditorInfo.IME_MASK_ACTION;
        boolean noEnterAction = info != null && (info.imeOptions & EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0;
        if (!noEnterAction && action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED) {
            if (connection.performEditorAction(action)) {
                return;
            }
        }
        boolean multiline = info != null && (info.inputType & InputType.TYPE_TEXT_FLAG_MULTI_LINE) != 0;
        if (multiline) {
            connection.commitText("\n", 1);
            return;
        }
        connection.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER));
        connection.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER));
    }

    private void toggleSymbolsMode() {
        if (recording || processing || keyboardPanel == null) {
            return;
        }
        symbolsMode = !symbolsMode;
        symbolsMoreMode = false;
        shift = false;
        populateKeyboardPanel(keyboardPanel);
        setStatus(symbolsMode ? "Symbols" : "Ready");
    }

    private void toggleMoreSymbolsMode() {
        if (recording || processing || keyboardPanel == null) {
            return;
        }
        haptic(keyboardPanel);
        symbolsMoreMode = !symbolsMoreMode;
        populateKeyboardPanel(keyboardPanel);
        setStatus(symbolsMoreMode ? "More symbols" : "Symbols");
    }

    private void toggleVoiceCapture() {
        if (processing) {
            return;
        }
        if (!recording && !shouldAllowVoiceCapture()) {
            setStatus("Voice disabled in this field");
            return;
        }
        String provider = Prefs.transcriptionProvider(this);
        if (Prefs.PROVIDER_ANDROID.equals(provider)) {
            toggleAndroidRecognition();
        } else {
            toggleOpenAiRecording();
        }
    }

    private void toggleOpenAiRecording() {
        if (recording) {
            stopOpenAiRecordingAndTranscribe();
            return;
        }
        if (!hasAudioPermission()) {
            setStatus("Open settings and grant microphone permission.");
            openSettings();
            return;
        }
        try {
            selectedPreset = Prefs.activePreset(this);
            currentAudioFile = File.createTempFile("sayboard-", ".m4a", getCacheDir());
            recorder = createRecorder();
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            recorder.setAudioEncodingBitRate(128000);
            recorder.setAudioSamplingRate(44100);
            recorder.setOutputFile(currentAudioFile.getAbsolutePath());
            recorder.prepare();
            recorder.start();
            recording = true;
            setMicVisual(true, true);
            setKeyboardLocked(true);
            showRecordingChips();
            setStatus("Recording: " + selectedPresetLabel());
        } catch (Exception e) {
            stopRecorderSilently();
            setStatus("Recording failed: " + concise(e));
        }
    }

    private MediaRecorder createRecorder() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return new MediaRecorder(this);
        }
        return new MediaRecorder();
    }

    private void stopOpenAiRecordingAndTranscribe() {
        File audio = currentAudioFile;
        stopRecorderOnly();
        if (audio == null || !audio.exists() || audio.length() == 0) {
            finishProcessingState("No audio captured.");
            return;
        }
        processing = true;
        setKeyboardLocked(true);
        micButton.setEnabled(false);
        setMicVisual(true, false);
        startStatusSpinner("Transcribing");
        String presetForThisRecording = selectedPreset;
        executor.execute(() -> {
            try {
                String transcript = OpenAiClient.transcribe(this, audio);
                String finalText = transcript;
                String finalStatus = "Inserted";
                if (shouldTransform(presetForThisRecording)) {
                    postStatusSpinner("Formatting: " + labelForPreset(presetForThisRecording));
                    try {
                        finalText = OpenAiClient.transform(this, transcript, presetForThisRecording);
                    } catch (Exception transformError) {
                        finalStatus = "Inserted raw";
                    }
                }
                String result = finalText;
                String status = finalStatus;
                mainHandler.post(() -> {
                    insertVoiceText(result);
                    finishProcessingState(status);
                });
            } catch (Exception e) {
                mainHandler.post(() -> finishProcessingState(concise(e)));
            } finally {
                if (!audio.delete()) {
                    audio.deleteOnExit();
                }
            }
        });
    }

    private boolean shouldTransform(String preset) {
        return Prefs.enableTransform(this) && !Prefs.PRESET_RAW.equals(preset);
    }

    private void toggleAndroidRecognition() {
        if (recording) {
            destroySpeechRecognizer();
            recording = false;
            finishProcessingState("Stopped");
            return;
        }
        if (!hasAudioPermission()) {
            setStatus("Open settings and grant microphone permission.");
            openSettings();
            return;
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            setStatus("Android speech recognition is not available on this device.");
            return;
        }
        selectedPreset = Prefs.activePreset(this);
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        speechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override public void onReadyForSpeech(Bundle params) {
                setStatus("Listening: " + selectedPresetLabel());
            }
            @Override public void onBeginningOfSpeech() {
                setStatus("Listening: " + selectedPresetLabel());
            }
            @Override public void onRmsChanged(float rmsdB) {
            }
            @Override public void onBufferReceived(byte[] buffer) {
            }
            @Override public void onEndOfSpeech() {
                setStatus("Processing...");
            }
            @Override public void onError(int error) {
                recording = false;
                destroySpeechRecognizer();
                finishProcessingState("Speech recognition error " + error);
            }
            @Override public void onResults(Bundle results) {
                handleAndroidSpeechResults(results);
            }
            @Override public void onPartialResults(Bundle partialResults) {
            }
            @Override public void onEvent(int eventType, Bundle params) {
            }
        });
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false);
        speechRecognizer.startListening(intent);
        recording = true;
        setMicVisual(true, true);
        setKeyboardLocked(true);
        showRecordingChips();
    }

    private void handleAndroidSpeechResults(Bundle results) {
        recording = false;
        processing = true;
        micButton.setEnabled(false);
        setMicVisual(true, false);
        ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        destroySpeechRecognizer();
        if (matches == null || matches.isEmpty()) {
            finishProcessingState("No speech recognized");
            return;
        }
        String transcript = matches.get(0);
        String presetForThisRecording = selectedPreset;
        if (!shouldTransform(presetForThisRecording)) {
            insertVoiceText(transcript);
            finishProcessingState("Inserted");
            return;
        }
        startStatusSpinner("Formatting: " + labelForPreset(presetForThisRecording));
        executor.execute(() -> {
            String result = transcript;
            String status = "Inserted";
            try {
                result = OpenAiClient.transform(this, transcript, presetForThisRecording);
            } catch (Exception transformError) {
                status = "Inserted raw";
            }
            String finalResult = result;
            String finalStatus = status;
            mainHandler.post(() -> {
                insertVoiceText(finalResult);
                finishProcessingState(finalStatus);
            });
        });
    }

    private void cyclePreset(int direction) {
        int index = presetIndex(selectedPreset);
        int next = (index + direction + Prefs.PRESET_VALUES.length) % Prefs.PRESET_VALUES.length;
        selectedPreset = Prefs.PRESET_VALUES[next];
        Prefs.setActivePreset(this, selectedPreset);
        if (presetButton != null) {
            presetButton.setText(presetDropdownText());
        }
        showRecordingChips();
        setStatus("Recording: " + selectedPresetLabel());
    }

    private int presetIndex(String preset) {
        for (int i = 0; i < Prefs.PRESET_VALUES.length; i++) {
            if (Prefs.PRESET_VALUES[i].equals(preset)) {
                return i;
            }
        }
        return 1;
    }

    private String selectedPresetLabel() {
        return labelForPreset(selectedPreset);
    }

    private String labelForPreset(String preset) {
        int index = presetIndex(preset);
        return Prefs.PRESET_LABELS[index];
    }

    private void stopRecorderSilently() {
        stopRecorderOnly();
        finishProcessingState("Ready");
    }

    private void stopRecorderOnly() {
        if (recorder != null) {
            try {
                recorder.stop();
            } catch (RuntimeException ignored) {
            }
            recorder.release();
            recorder = null;
        }
        recording = false;
        currentAudioFile = null;
    }

    private void finishProcessingState(String status) {
        stopStatusSpinner();
        recording = false;
        processing = false;
        stopDeleteHold();
        setKeyboardLocked(false);
        if (micButton != null) {
            micButton.setEnabled(true);
            setMicVisual(false, true);
        }
        if (chipStrip != null) {
            showIdleChips();
        }
        if (presetButton != null) {
            presetButton.setText(presetDropdownText());
        }
        setStatus(status);
    }

    private void destroySpeechRecognizer() {
        if (speechRecognizer != null) {
            speechRecognizer.destroy();
            speechRecognizer = null;
        }
    }

    private void destroySpellChecker() {
        if (spellCheckerSession != null) {
            spellCheckerSession.close();
            spellCheckerSession = null;
        }
    }

    private void openSettings() {
        Intent intent = new Intent(this, SettingsActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    private boolean hasAudioPermission() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M
                || checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
    }

    private void postStatus(String status) {
        mainHandler.post(() -> setStatus(status));
    }

    private void startStatusSpinner(String base) {
        statusSpinnerBase = base;
        statusSpinnerStep = 0;
        if (statusSpinnerRunnable == null) {
            statusSpinnerRunnable = () -> {
                if (!processing) {
                    return;
                }
                String dots = statusSpinnerStep == 0 ? "." : statusSpinnerStep == 1 ? ".." : "...";
                setStatus(statusSpinnerBase + dots);
                statusSpinnerStep = (statusSpinnerStep + 1) % 3;
                mainHandler.postDelayed(statusSpinnerRunnable, 450);
            };
        }
        mainHandler.removeCallbacks(statusSpinnerRunnable);
        mainHandler.post(statusSpinnerRunnable);
    }

    private void postStatusSpinner(String base) {
        mainHandler.post(() -> startStatusSpinner(base));
    }

    private void stopStatusSpinner() {
        if (statusSpinnerRunnable != null) {
            mainHandler.removeCallbacks(statusSpinnerRunnable);
        }
    }

    private void setStatus(String status) {
        if (statusText != null) {
            statusText.setText(status);
        }
    }

    private String concise(Exception e) {
        String message = e.getMessage();
        if (message == null || message.trim().isEmpty()) {
            return e.getClass().getSimpleName();
        }
        return message.length() > 120 ? message.substring(0, 120) + "..." : message;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private GradientDrawable keyBackground(int color, boolean selected) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(7));
        if (!selected) {
            drawable.setStroke(dp(1), colors.stroke);
        }
        return drawable;
    }

    private InsetDrawable keyVisualBackground(int color, boolean selected) {
        return new InsetDrawable(
                keyBackground(color, selected),
                dp(KEY_VISUAL_GAP_DP),
                dp(KEY_VISUAL_GAP_DP),
                dp(KEY_VISUAL_GAP_DP),
                dp(KEY_VISUAL_GAP_DP)
        );
    }

    private GradientDrawable ovalBackground(int color, boolean selected) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.OVAL);
        drawable.setColor(color);
        if (!selected) {
            drawable.setStroke(dp(1), colors.stroke);
        }
        return drawable;
    }

    private void setMicVisual(boolean active, boolean enabled) {
        if (micButton == null) {
            return;
        }
        micButton.setEnabled(enabled);
        micButton.setColorFilter(active ? colors.onDanger : colors.text);
        micButton.setBackground(ovalBackground(active ? colors.danger : colors.key, active));
        micButton.setAlpha(enabled ? 1f : 0.72f);
    }

    private void haptic(View view) {
        if (view != null) {
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
        }
    }

    private static final class Palette {
        final int background;
        final int status;
        final int key;
        final int keyAlt;
        final int text;
        final int stroke;
        final int accent;
        final int onAccent;
        final int danger;
        final int onDanger;

        private Palette(
                int background,
                int status,
                int key,
                int keyAlt,
                int text,
                int stroke,
                int accent,
                int onAccent,
                int danger,
                int onDanger
        ) {
            this.background = background;
            this.status = status;
            this.key = key;
            this.keyAlt = keyAlt;
            this.text = text;
            this.stroke = stroke;
            this.accent = accent;
            this.onAccent = onAccent;
            this.danger = danger;
            this.onDanger = onDanger;
        }

        static Palette from(SayboardKeyboardService service) {
            boolean night = (service.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                    == Configuration.UI_MODE_NIGHT_YES;
            int accent = service.resolveThemeColor(android.R.attr.colorAccent, night ? Color.rgb(100, 181, 246) : Color.rgb(25, 103, 210));
            if (night) {
                return new Palette(
                        Color.rgb(32, 33, 36),
                        Color.rgb(45, 46, 50),
                        Color.rgb(58, 59, 63),
                        Color.rgb(74, 75, 80),
                        Color.rgb(241, 243, 244),
                        Color.rgb(82, 83, 88),
                        accent,
                        Color.WHITE,
                        Color.rgb(198, 40, 40),
                        Color.WHITE
                );
            }
            return new Palette(
                    Color.rgb(238, 240, 243),
                    Color.rgb(247, 248, 250),
                    Color.WHITE,
                    Color.rgb(224, 228, 233),
                    Color.rgb(31, 35, 40),
                    Color.rgb(218, 223, 230),
                    accent,
                    Color.WHITE,
                    Color.rgb(191, 54, 12),
                    Color.WHITE
            );
        }
    }

    private int resolveThemeColor(int attr, int fallback) {
        android.util.TypedValue value = new android.util.TypedValue();
        if (getTheme().resolveAttribute(attr, value, true)) {
            return value.data;
        }
        return fallback;
    }

    private static Map<String, String> commonTypos() {
        Map<String, String> typos = new HashMap<>();
        typos.put("teh", "the");
        typos.put("hte", "the");
        typos.put("liek", "like");
        typos.put("becuase", "because");
        typos.put("becasue", "because");
        typos.put("definately", "definitely");
        typos.put("seperate", "separate");
        typos.put("recieve", "receive");
        typos.put("adress", "address");
        typos.put("wierd", "weird");
        typos.put("thier", "their");
        typos.put("freind", "friend");
        typos.put("dont", "don't");
        typos.put("cant", "can't");
        typos.put("wont", "won't");
        typos.put("im", "I'm");
        typos.put("ive", "I've");
        typos.put("ill", "I'll");
        return typos;
    }

    private static final class PhraseReplacement {
        final String from;
        final String to;

        PhraseReplacement(String from, String to) {
            this.from = from;
            this.to = to;
        }
    }
}
