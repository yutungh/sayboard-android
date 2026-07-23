package com.voiceflowkeyboard.ime;

import android.Manifest;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.InsetDrawable;
import android.inputmethodservice.InputMethodService;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.view.inputmethod.ExtractedText;
import android.view.inputmethod.ExtractedTextRequest;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.textservice.SentenceSuggestionsInfo;
import android.view.textservice.SpellCheckerSession;
import android.view.textservice.SuggestionsInfo;
import android.view.textservice.TextInfo;
import android.view.textservice.TextServicesManager;
import android.widget.LinearLayout;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class VoiceFlowKeyboardService extends InputMethodService {
    private static final int KEY_HEIGHT_DP = 48;
    private static final int KEY_VISUAL_GAP_DP = 3;
    private static final int SPELL_CHECK_DELAY_MS = 120;
    private static final int SHIFT_DOUBLE_TAP_MS = 350;
    private static final int SPACE_CURSOR_HOLD_MS = 280;
    private static final int SPACE_CURSOR_STEP_DP = 10;
    private static final Map<String, String> COMMON_TYPOS = commonTypos();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final List<TextView> keyButtons = new ArrayList<>();
    private final List<TextView> letterButtons = new ArrayList<>();

    private Palette colors;
    private FrameLayout keyboardSurface;
    private LinearLayout keyboardPanel;
    private LinearLayout voiceStyleOverlay;
    private LinearLayout chipStrip;
    private HorizontalScrollView chipScroller;
    private TextView statusText;
    private ImageButton translationButton;
    private ImageButton createButton;
    private ImageButton instructionButton;
    private ImageButton micButton;
    private TextView cancelRecordingButton;
    private ImageButton topHistoryButton;
    private TextView shiftButton;
    private TextView spaceButton;
    private boolean recording;
    private boolean processing;
    private boolean translationCapture;
    private boolean creationCapture;
    private boolean instructionCapture;
    private boolean shift;
    private boolean autoShift;
    private boolean capsLock;
    private boolean symbolsMode;
    private boolean symbolsMoreMode;
    private boolean historyMode;
    private boolean deleteHeld;
    private boolean spaceCursorMode;
    private boolean offlineRecordingSession;
    private String offlineRecordingProvider = Prefs.PROVIDER_OFFLINE_VOSK;
    private float downX;
    private float downY;
    private float rootDownX;
    private float rootDownY;
    private float spaceCursorLastStepX;
    private boolean rootSwipeConsumed;
    private boolean rootSwipeTracking;
    private boolean rootSwipeStartedOnSpace;
    private boolean rootSwipeStartedOnExpressionSlider;
    private boolean panelAnimating;
    private boolean retoneMode;
    private long deleteHoldStartMs;
    private long lastShiftTapMs;
    private String selectedPreset;
    private int selectedExpression;
    private String lastVoiceRawTranscript = "";
    private String lastVoiceInsertedText = "";
    private String lastVoicePreset = "";
    private int lastVoiceExpression = Prefs.DEFAULT_EXPRESSION;
    private String lastVoiceOperation = VoiceHistoryItem.OPERATION_DICTATION;
    private String lastVoiceTargetLanguage = "";
    private String lastVoiceHistoryId = "";
    private int lastVoiceSelectionEnd = -1;
    private MediaRecorder recorder;
    private AudioRecord offlineRecorder;
    private Thread offlineRecordThread;
    private volatile boolean offlineRecordLoop;
    private File currentAudioFile;
    private File currentPcmFile;
    private SpellCheckerSession spellCheckerSession;
    private Runnable deleteRepeatRunnable;
    private Runnable spaceCursorRunnable;
    private Runnable statusSpinnerRunnable;
    private ObjectAnimator translationLoadingAnimator;
    private ObjectAnimator createLoadingAnimator;
    private ObjectAnimator micLoadingAnimator;
    private ObjectAnimator instructionLoadingAnimator;
    private SeekBar expressionSlider;
    private Runnable spellCheckRunnable;
    private String statusSpinnerBase = "";
    private String translationTargetLanguage = "";
    private String instructionSourceText = "";
    private String pendingAutoCorrectWord = "";
    private String pendingAutoCorrectReplacement = "";
    private final List<String> pendingAutoCorrectSuggestions = new ArrayList<>();
    private String pendingAutoCompletePrefix = "";
    private final List<String> pendingAutoCompleteSuggestions = new ArrayList<>();
    private final Set<String> historyOriginalPreviewIds = new HashSet<>();
    private String lastAutoCorrectOriginal = "";
    private String lastAutoCorrectReplacement = "";
    private String lastAutoCorrectSeparator = "";
    private boolean pendingAutoCorrectAccept;
    private int spellCheckGeneration;
    private int statusSpinnerStep;
    private static final String[] COMMON_COMPLETIONS = commonCompletions();

    @Override
    public View onCreateInputView() {
        colors = Palette.from(this);
        selectedPreset = Prefs.activePreset(this);
        selectedExpression = Prefs.expressionForPreset(this, selectedPreset);
        LinearLayout root = new SwipeRootLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(5), dp(5), dp(5), dp(6));
        root.setBackgroundColor(colors.background);

        root.addView(buildStrip());
        keyboardPanel = buildKeyboardPanel();
        keyboardPanel.setOnTouchListener(this::handleSwipe);
        keyboardSurface = new FrameLayout(this);
        keyboardSurface.addView(keyboardPanel, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));
        voiceStyleOverlay = new LinearLayout(this);
        voiceStyleOverlay.setVisibility(View.GONE);
        keyboardSurface.addView(voiceStyleOverlay, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));
        root.addView(keyboardSurface, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(KEY_HEIGHT_DP * 4)
        ));
        showIdleChips();
        updateAutoCapitalization();
        return root;
    }

    @Override
    public void onDestroy() {
        stopRecorderSilently();
        destroySpellChecker();
        executor.shutdownNow();
        super.onDestroy();
    }

    @Override
    public void onStartInput(EditorInfo attribute, boolean restarting) {
        super.onStartInput(attribute, restarting);
        clearAutoCorrection();
        clearLastAutoCorrection();
        clearLastVoiceInsertion();
        if (!recording) {
            hideVoiceStyleOverlay();
        }
        if (historyMode) {
            hideHistoryPanel();
        }
        updateAutoCapitalization();
    }

    @Override
    public void onFinishInput() {
        clearAutoCorrection();
        clearLastAutoCorrection();
        clearLastVoiceInsertion();
        super.onFinishInput();
    }

    @Override
    public void onUpdateSelection(
            int oldSelStart,
            int oldSelEnd,
            int newSelStart,
            int newSelEnd,
            int candidatesStart,
            int candidatesEnd
    ) {
        super.onUpdateSelection(
                oldSelStart,
                oldSelEnd,
                newSelStart,
                newSelEnd,
                candidatesStart,
                candidatesEnd
        );
        if (!lastVoiceRawTranscript.isEmpty()
                && lastVoiceSelectionEnd >= 0
                && (newSelStart != lastVoiceSelectionEnd || newSelEnd != lastVoiceSelectionEnd)) {
            clearLastVoiceInsertion();
        }
    }

    private boolean handleSwipe(View view, MotionEvent event) {
        return false;
    }

    private boolean isHorizontalSwipe(float deltaX, float deltaY) {
        return Math.abs(deltaX) > dp(18) && Math.abs(deltaX) > Math.abs(deltaY) * 1.18f;
    }

    private boolean handleRootSwipe(MotionEvent event) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            rootDownX = event.getRawX();
            rootDownY = event.getRawY();
            rootSwipeConsumed = false;
            rootSwipeTracking = false;
            rootSwipeStartedOnSpace = isRawPointInside(spaceButton, rootDownX, rootDownY);
            rootSwipeStartedOnExpressionSlider = isRawPointInside(expressionSlider, rootDownX, rootDownY);
            return false;
        }
        if (action == MotionEvent.ACTION_CANCEL) {
            animatePanelReset();
            rootSwipeConsumed = false;
            rootSwipeTracking = false;
            return false;
        }
        if (rootSwipeConsumed) {
            if (action == MotionEvent.ACTION_UP) {
                rootSwipeConsumed = false;
                rootSwipeTracking = false;
            }
            return true;
        }
        if (rootSwipeStartedOnSpace
                || rootSwipeStartedOnExpressionSlider
                || (action != MotionEvent.ACTION_MOVE && action != MotionEvent.ACTION_UP)) {
            return false;
        }
        float deltaX = event.getRawX() - rootDownX;
        float deltaY = event.getRawY() - rootDownY;
        if (!rootSwipeTracking && (!canStartRootSwipe(deltaX, deltaY) || !canUseRootSwipe(deltaX))) {
            return false;
        }
        rootSwipeTracking = true;
        if (action == MotionEvent.ACTION_MOVE) {
            updateSwipePreview(deltaX);
            return true;
        }

        rootSwipeConsumed = true;
        rootSwipeTracking = false;
        boolean committed = Math.abs(deltaX) >= swipeCommitDistance();
        if (committed && canUseRootSwipe(deltaX)) {
            if (recording) {
                animatePanelReset();
                cyclePreset(deltaX < 0 ? 1 : -1);
                return true;
            }
            if (historyMode && deltaX > 0) {
                hideHistoryPanelAnimated();
                return true;
            }
        }
        animatePanelReset();
        return true;
    }

    private boolean canUseRootSwipe(float deltaX) {
        if (recording) {
            return !translationCapture && !creationCapture && !instructionCapture;
        }
        if (processing || panelAnimating) {
            return false;
        }
        return historyMode && deltaX > 0;
    }

    private boolean canStartRootSwipe(float deltaX, float deltaY) {
        if (!historyMode && !recording) {
            return false;
        }
        return isHorizontalSwipe(deltaX, deltaY);
    }

    private int swipeCommitDistance() {
        int panelWidth = keyboardPanel == null ? 0 : keyboardPanel.getWidth();
        if (!historyMode && !recording) {
            return Math.max(dp(96), panelWidth / 4);
        }
        return Math.max(dp(46), panelWidth / 7);
    }

    private void updateSwipePreview(float deltaX) {
        if (keyboardPanel == null || recording) {
            return;
        }
        int width = Math.max(keyboardPanel.getWidth(), dp(320));
        float max = width * 0.28f;
        float constrained = Math.max(-max, Math.min(max, deltaX));
        keyboardPanel.animate().cancel();
        keyboardPanel.setTranslationX(constrained * 0.45f);
        keyboardPanel.setAlpha(1f - Math.min(0.24f, Math.abs(constrained) / width));
    }

    private void animatePanelReset() {
        if (keyboardPanel == null || panelAnimating) {
            return;
        }
        keyboardPanel.animate()
                .translationX(0f)
                .alpha(1f)
                .setDuration(110)
                .start();
    }

    private boolean isRawPointInside(View view, float rawX, float rawY) {
        if (view == null || view.getVisibility() != View.VISIBLE) {
            return false;
        }
        int[] location = new int[2];
        view.getLocationOnScreen(location);
        return rawX >= location[0]
                && rawX <= location[0] + view.getWidth()
                && rawY >= location[1]
                && rawY <= location[1] + view.getHeight();
    }

    private LinearLayout buildStrip() {
        LinearLayout outer = new LinearLayout(this);
        outer.setOrientation(LinearLayout.VERTICAL);
        outer.setPadding(0, 0, 0, dp(4));

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);

        FrameLayout statusSlot = new FrameLayout(this);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(0, dp(36), 1f);
        statusParams.setMargins(dp(2), dp(2), dp(5), dp(2));
        top.addView(statusSlot, statusParams);

        statusText = new TextView(this);
        statusText.setText("Ready");
        statusText.setTextColor(colors.text);
        statusText.setTextSize(14);
        statusText.setSingleLine(true);
        statusText.setEllipsize(TextUtils.TruncateAt.END);
        statusText.setGravity(Gravity.CENTER_VERTICAL);
        statusText.setPadding(dp(6), 0, dp(10), 0);
        statusText.setBackgroundColor(Color.TRANSPARENT);
        statusSlot.addView(statusText, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));

        chipStrip = new LinearLayout(this);
        chipStrip.setOrientation(LinearLayout.HORIZONTAL);
        chipStrip.setGravity(Gravity.CENTER_VERTICAL);
        chipScroller = new HorizontalScrollView(this);
        chipScroller.setHorizontalScrollBarEnabled(false);
        chipScroller.setVisibility(View.GONE);
        chipScroller.addView(chipStrip);
        statusSlot.addView(chipScroller, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));

        cancelRecordingButton = toolButton("Cancel");
        cancelRecordingButton.setTextColor(colors.onDanger);
        cancelRecordingButton.setBackground(keyBackground(colors.danger, true));
        cancelRecordingButton.setVisibility(View.INVISIBLE);
        cancelRecordingButton.setOnClickListener(v -> {
            haptic(v);
            if (retoneMode) {
                closeRetoneOverlay("Retone canceled");
            } else {
                cancelRecording();
            }
        });
        LinearLayout.LayoutParams cancelParams = new LinearLayout.LayoutParams(dp(76), dp(32));
        cancelParams.setMargins(dp(2), dp(2), dp(2), dp(2));
        cancelRecordingButton.setLayoutParams(cancelParams);
        top.addView(cancelRecordingButton);

        if (Prefs.translationEnabled(this)) {
            translationLoadingAnimator = null;
            translationButton = translationButton();
            translationButton.setOnClickListener(v -> {
                haptic(v);
                toggleTranslationCapture();
            });
            top.addView(translationButton);
        } else {
            translationButton = null;
        }

        createLoadingAnimator = null;
        createButton = createButton();
        createButton.setOnClickListener(v -> {
            haptic(v);
            toggleCreationCapture();
        });
        top.addView(createButton);

        instructionLoadingAnimator = null;
        instructionButton = instructionButton();
        instructionButton.setOnClickListener(v -> {
            haptic(v);
            toggleInstructionCapture();
        });
        top.addView(instructionButton);

        micLoadingAnimator = null;
        micButton = micButton();
        micButton.setOnClickListener(v -> {
            haptic(v);
            toggleVoiceCapture();
        });
        top.addView(micButton);

        topHistoryButton = plainIconButton(R.drawable.ic_history_24, v -> {
            if (processing) {
                return;
            }
            haptic(v);
            if (historyMode) {
                hideHistoryPanelAnimated();
            } else {
                showHistoryPanelAnimated();
            }
        });
        top.addView(topHistoryButton);

        outer.addView(top);
        return outer;
    }

    private LinearLayout buildKeyboardPanel() {
        keyButtons.clear();
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setOnTouchListener(this::handleSwipe);
        populateKeyboardPanel(panel);
        return panel;
    }

    private void populateKeyboardPanel(LinearLayout panel) {
        historyMode = false;
        panel.removeAllViews();
        keyButtons.clear();
        letterButtons.clear();
        shiftButton = null;
        spaceButton = null;
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
        shiftButton = keyButton("shift", 1.35f, v -> toggleShift(), true);
        third.addView(shiftButton);
        for (char c : "zxcvbnm".toCharArray()) {
            third.addView(letterKeyButton(String.valueOf(c), 1f));
        }
        third.addView(deleteKey());
        panel.addView(third);
        panel.addView(bottomRow("?123"));
        updateShiftVisuals();
    }

    private void showHistoryPanel() {
        if (keyboardPanel == null || recording || processing) {
            return;
        }
        historyMode = true;
        symbolsMode = false;
        symbolsMoreMode = false;
        shift = false;
        autoShift = false;
        lastShiftTapMs = 0;
        clearAutoCorrection();
        hideChipStrip();
        setHistoryControlsActive(true);
        setStatus("History");
        keyboardPanel.removeAllViews();
        keyButtons.clear();
        letterButtons.clear();
        shiftButton = null;
        spaceButton = null;

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        scroll.setOnTouchListener(this::handleSwipe);
        scroll.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(KEY_HEIGHT_DP * 4)
        ));

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(0, 0, 0, dp(4));
        scroll.addView(content);

        List<VoiceHistoryItem> history = Prefs.transcriptHistory(this);
        if (history.isEmpty()) {
            TextView empty = historyText("No transcripts yet.", 14, false);
            empty.setGravity(Gravity.CENTER);
            empty.setBackground(keyVisualBackground(colors.key, false));
            content.addView(empty, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp(KEY_HEIGHT_DP * 4)
            ));
        } else {
            for (VoiceHistoryItem item : history) {
                content.addView(historyItemView(item));
            }
        }
        keyboardPanel.addView(scroll);
    }

    private void hideHistoryPanel() {
        if (keyboardPanel == null) {
            return;
        }
        historyMode = false;
        setHistoryControlsActive(false);
        hideChipStrip();
        populateKeyboardPanel(keyboardPanel);
        setStatus(capsLock ? "Caps lock" : "Ready");
        updateAutoCapitalization();
    }

    private void showHistoryPanelAnimated() {
        animateHistoryTransition(true);
    }

    private void hideHistoryPanelAnimated() {
        animateHistoryTransition(false);
    }

    private void animateHistoryTransition(boolean toHistory) {
        if (keyboardPanel == null || panelAnimating || processing) {
            return;
        }
        if (toHistory && (historyMode || recording)) {
            return;
        }
        if (!toHistory && !historyMode) {
            return;
        }
        panelAnimating = true;
        keyboardPanel.animate().cancel();
        int width = Math.max(keyboardPanel.getWidth(), dp(320));
        float outX = toHistory ? -width * 0.34f : width * 0.34f;
        float inX = toHistory ? width * 0.25f : -width * 0.25f;
        keyboardPanel.animate()
                .translationX(outX)
                .alpha(0.24f)
                .setDuration(90)
                .withEndAction(() -> {
                    if (toHistory) {
                        showHistoryPanel();
                    } else {
                        hideHistoryPanel();
                    }
                    keyboardPanel.setTranslationX(inX);
                    keyboardPanel.setAlpha(0.36f);
                    keyboardPanel.animate()
                            .translationX(0f)
                            .alpha(1f)
                            .setDuration(145)
                            .withEndAction(() -> {
                                keyboardPanel.setTranslationX(0f);
                                keyboardPanel.setAlpha(1f);
                                panelAnimating = false;
                            })
                            .start();
                })
                .start();
    }

    private View historyItemView(VoiceHistoryItem item) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(12), dp(10), dp(12), dp(10));
        card.setBackground(keyBackground(colors.key, false));
        card.setOnTouchListener(this::handleSwipe);
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        cardParams.setMargins(dp(2), dp(2), dp(2), dp(6));
        card.setLayoutParams(cardParams);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView meta = historyText(historyMeta(item), 11, true);
        meta.setTextColor(colors.text);
        meta.setAlpha(0.62f);
        meta.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(meta, new LinearLayout.LayoutParams(
                0,
                dp(34),
                1f
        ));
        header.addView(historyOpenButton(item));
        TextView outputMenu = historyOutputButton(item);
        header.addView(outputMenu);
        card.addView(header);

        boolean hasSelectedOutput = item.hasOutputForVariant(item.preset, item.expression);
        boolean transformedPreset = !Prefs.PRESET_RAW.equals(item.preset);
        boolean showingOriginal = transformedPreset
                && (!hasSelectedOutput || historyOriginalPreviewIds.contains(item.id));

        TextView previewMode = historyText(historyPreviewModeText(item, hasSelectedOutput, showingOriginal), 10, true);
        previewMode.setAlpha(0.62f);
        previewMode.setPadding(0, dp(7), 0, 0);
        card.addView(previewMode);

        TextView preview = historyText(compactPreview(historyVisibleText(item)), 14, false);
        preview.setMaxLines(4);
        preview.setEllipsize(TextUtils.TruncateAt.END);
        preview.setPadding(0, dp(4), 0, dp(10));
        card.addView(preview);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout leftActions = new LinearLayout(this);
        leftActions.setOrientation(LinearLayout.HORIZONTAL);
        if (hasSelectedOutput) {
            leftActions.addView(historyActionButton("Copy", v -> copyHistoryText(historyVisibleText(item)), false));
            leftActions.addView(historyActionButton("Paste", v -> pasteHistoryText(historyVisibleText(item)), true));
        } else {
            leftActions.addView(historyActionButton(
                    "Create " + historyOutputLabel(item.preset, item.expression),
                    v -> createHistoryTransform(item, item.preset, item.expression),
                    true
            ));
        }
        actions.addView(leftActions);
        actions.addView(new View(this), new LinearLayout.LayoutParams(0, dp(1), 1f));
        if (transformedPreset) {
            actions.addView(historyOriginalToggle(item, preview, previewMode, hasSelectedOutput));
        }
        card.addView(actions);
        return card;
    }

    private String historyVisibleText(VoiceHistoryItem item) {
        boolean hasSelectedOutput = item.hasOutputForVariant(item.preset, item.expression);
        boolean showOriginal = !hasSelectedOutput || historyOriginalPreviewIds.contains(item.id);
        return showOriginal ? item.rawText : item.outputForVariant(item.preset, item.expression);
    }

    private String historyPreviewModeText(VoiceHistoryItem item, boolean hasSelectedOutput, boolean showingOriginal) {
        String selectedVersion = historyOutputLabel(item.preset, item.expression);
        if (item.isTranslation() && !item.targetLanguage.isEmpty()) {
            selectedVersion = compactLanguageName(item.targetLanguage) + " - " + selectedVersion;
        }
        if (!hasSelectedOutput && !Prefs.PRESET_RAW.equals(item.preset)) {
            return "ORIGINAL SHOWN - " + selectedVersion.toUpperCase(Locale.US) + " NOT CREATED";
        }
        if (showingOriginal || Prefs.PRESET_RAW.equals(item.preset)) {
            return item.isTranslation() ? "ORIGINAL SOURCE" : item.isCreation() ? "CREATION REQUEST" : "ORIGINAL";
        }
        return selectedVersion.toUpperCase(Locale.US);
    }

    private Switch historyOriginalToggle(
            VoiceHistoryItem item,
            TextView preview,
            TextView previewMode,
            boolean hasSelectedOutput
    ) {
        Switch toggle = new Switch(this);
        toggle.setText("Show original");
        toggle.setTextSize(11);
        toggle.setTextColor(colors.text);
        toggle.setTypeface(Typeface.DEFAULT_BOLD);
        toggle.setIncludeFontPadding(false);
        toggle.setGravity(Gravity.CENTER_VERTICAL);
        toggle.setPadding(dp(6), 0, 0, 0);
        toggle.setMinWidth(0);
        toggle.setMinHeight(0);
        toggle.setChecked(!hasSelectedOutput || historyOriginalPreviewIds.contains(item.id));
        toggle.setEnabled(hasSelectedOutput);
        toggle.setAlpha(hasSelectedOutput ? 0.9f : 0.58f);
        toggle.setOnCheckedChangeListener((button, checked) -> {
            haptic(toggle);
            if (checked) {
                historyOriginalPreviewIds.add(item.id);
            } else {
                historyOriginalPreviewIds.remove(item.id);
            }
            preview.setText(compactPreview(historyVisibleText(item)));
            previewMode.setText(historyPreviewModeText(item, true, checked));
        });
        toggle.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                dp(36)
        ));
        return toggle;
    }

    private String historyMeta(VoiceHistoryItem item) {
        String date = item.timestampMs > 0
                ? DateFormat.format("MMM d, yyyy 'at' h:mm a", item.timestampMs).toString()
                : "Recent";
        if (item.isTranslation() && !item.targetLanguage.isEmpty()) {
            return date + " - " + compactLanguageName(item.targetLanguage);
        }
        return date + (item.isCreation() ? " - Creation" : " - Dictation");
    }

    private TextView historyOutputButton(VoiceHistoryItem item) {
        TextView button = chip(historyOutputLabel(item.preset, item.expression), v -> showHistoryOutputMenu(v, item));
        button.setTextSize(11);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setGravity(Gravity.CENTER_VERTICAL);
        button.setPadding(dp(11), 0, dp(9), 0);
        button.setMaxWidth(dp(180));
        button.setEllipsize(TextUtils.TruncateAt.END);
        button.setBackground(keyBackground(colors.keyAlt, false));
        Drawable chevron = getDrawable(R.drawable.ic_chevron_down_24);
        if (chevron != null) {
            chevron.setBounds(0, 0, dp(14), dp(14));
            chevron.setTint(colors.text);
            button.setCompoundDrawablePadding(dp(5));
            button.setCompoundDrawables(null, null, chevron, null);
        }
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                dp(32)
        );
        params.setMargins(dp(8), dp(1), 0, dp(1));
        button.setLayoutParams(params);
        return button;
    }

    private ImageButton historyOpenButton(VoiceHistoryItem item) {
        ImageButton button = plainIconButton(R.drawable.ic_open_in_full_24, v -> {
            haptic(v);
            Intent intent = new Intent(this, TranscriptDetailActivity.class);
            intent.putExtra(TranscriptDetailActivity.EXTRA_HISTORY_ID, item.id);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        });
        button.setContentDescription("Open full transcript");
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(34), dp(34));
        params.setMargins(dp(4), 0, dp(2), 0);
        button.setLayoutParams(params);
        return button;
    }

    private String historyOutputLabel(String preset, int expression) {
        if (Prefs.PRESET_RAW.equals(preset)) {
            return "Original";
        }
        return Prefs.labelForPreset(this, preset) + " - " + Prefs.expressionLabel(expression);
    }

    private void showHistoryOutputMenu(View anchor, VoiceHistoryItem item) {
        PopupMenu menu = new PopupMenu(this, anchor);
        List<String> variants = new ArrayList<>();
        variants.add(Prefs.PRESET_RAW);
        for (String preset : Prefs.selectablePresetValues(this)) {
            String variant = Prefs.historyVariantKey(preset, Prefs.expressionForPreset(this, preset));
            if (!variants.contains(variant)) {
                variants.add(variant);
            }
        }
        for (String variant : item.outputs.keySet()) {
            if (!variants.contains(variant)) {
                variants.add(variant);
            }
        }
        String selectedVariant = item.selectedVariantKey();
        for (int i = 0; i < variants.size(); i++) {
            String variant = variants.get(i);
            String preset = Prefs.historyVariantPreset(variant);
            int expression = Prefs.historyVariantExpression(variant);
            boolean available = item.hasOutputForVariant(preset, expression);
            String label = historyOutputLabel(preset, expression);
            if (!available) {
                label += " (not created)";
            }
            menu.getMenu().add(0, i, i, label)
                    .setCheckable(true)
                    .setChecked(variant.equals(selectedVariant));
        }
        menu.getMenu().setGroupCheckable(0, true, true);
        menu.setOnMenuItemClickListener(menuItem -> {
            int index = menuItem.getItemId();
            if (index < 0 || index >= variants.size()) {
                return false;
            }
            String variant = variants.get(index);
            historyOriginalPreviewIds.remove(item.id);
            Prefs.selectTranscriptHistoryVariant(
                    this,
                    item.id,
                    Prefs.historyVariantPreset(variant),
                    Prefs.historyVariantExpression(variant)
            );
            showHistoryPanel();
            return true;
        });
        menu.show();
    }

    private TextView historyActionButton(String text, View.OnClickListener listener, boolean primary) {
        TextView button = chip(text, listener);
        button.setTextSize(11);
        button.setPadding(dp(13), 0, dp(13), 0);
        button.setBackground(keyBackground(primary ? colors.accent : colors.keyAlt, primary));
        if (primary) {
            button.setTextColor(colors.onAccent);
        }
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                dp(32)
        );
        params.setMargins(0, 0, dp(6), 0);
        button.setLayoutParams(params);
        return button;
    }

    private TextView historyText(String text, int size, boolean bold) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(colors.text);
        view.setTypeface(bold ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        view.setIncludeFontPadding(false);
        return view;
    }

    private String compactPreview(String text) {
        String compact = text == null ? "" : text.replace('\n', ' ').trim();
        while (compact.contains("  ")) {
            compact = compact.replace("  ", " ");
        }
        return compact.isEmpty() ? "(empty)" : compact;
    }

    private LinearLayout bottomRow(String modeLabel) {
        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.HORIZONTAL);
        bottom.setGravity(Gravity.CENTER_VERTICAL);
        String normalizedMode = modeLabel.replace("?", "");
        bottom.addView(keyButton(normalizedMode, 1.4f, v -> toggleSymbolsMode(), true));
        bottom.addView(spaceKey(symbolsMode ? 5.45f : 5.7f));
        if (!symbolsMode) {
            bottom.addView(keyButton(".", 0.9f, v -> commitSeparator(".")));
        }
        bottom.addView(keyButton("return", 1.65f, v -> sendEnter(), true));
        return bottom;
    }

    private LinearLayout letterMiddleRow() {
        LinearLayout outer = new LinearLayout(this);
        outer.setOrientation(LinearLayout.HORIZONTAL);
        outer.addView(edgeDeadZone(10));
        outer.addView(edgeHitZone("a", 14));
        outer.addView(keyRow("asdfghjkl"), new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
        ));
        outer.addView(edgeHitZone("l", 14));
        outer.addView(edgeDeadZone(10));
        return outer;
    }

    private View edgeDeadZone(int widthDp) {
        View view = new View(this);
        view.setLayoutParams(new LinearLayout.LayoutParams(
                dp(widthDp),
                dp(KEY_HEIGHT_DP)
        ));
        return view;
    }

    private View edgeHitZone(String key, int widthDp) {
        View view = new View(this);
        view.setClickable(true);
        view.setOnClickListener(v -> {
            haptic(v);
            commitKey(key);
        });
        view.setLayoutParams(new LinearLayout.LayoutParams(
                dp(widthDp),
                dp(KEY_HEIGHT_DP)
        ));
        return view;
    }

    private LinearLayout keyRow(String chars) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        for (char c : chars.toCharArray()) {
            row.addView(letterKeyButton(String.valueOf(c), 1f));
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
        if (showRetoneChip()) {
            return;
        }
        if (showAutoCorrectionChip()) {
            return;
        }
        if (showAutoCompleteChips()) {
            return;
        }
        hideChipStrip();
    }

    private boolean showRetoneChip() {
        if (chipStrip == null
                || recording
                || processing
                || historyMode
                || lastVoiceRawTranscript.isEmpty()
                || lastVoiceInsertedText.isEmpty()
                || lastVoiceHistoryId.isEmpty()) {
            return false;
        }
        chipStrip.removeAllViews();
        showChipStrip();
        String label = "Retone - " + Prefs.labelForPreset(this, lastVoicePreset)
                + " - " + Prefs.expressionLabel(lastVoiceExpression);
        TextView retone = chip(label, v -> openRetoneOverlay());
        retone.setTextColor(colors.onAccent);
        retone.setBackground(keyBackground(colors.accent, true));
        chipStrip.addView(retone);
        return true;
    }

    private boolean showAutoCorrectionChip() {
        if (chipStrip == null || recording || processing || pendingAutoCorrectReplacement.isEmpty()) {
            return false;
        }
        chipStrip.removeAllViews();
        showChipStrip();
        TextView original = chip(pendingAutoCorrectWord, v -> rejectPendingAutoCorrection());
        original.setBackground(keyBackground(colors.keyAlt, false));
        chipStrip.addView(original);

        TextView best = chip(pendingAutoCorrectReplacement, v -> applyPendingAutoCorrection(false));
        best.setTextColor(colors.onAccent);
        best.setBackground(keyBackground(colors.accent, true));
        chipStrip.addView(best);

        String alternate = alternateAutoCorrection();
        if (!alternate.isEmpty()) {
            TextView alt = chip(alternate, v -> applyPendingAutoCorrection(false, alternate));
            chipStrip.addView(alt);
        }
        return true;
    }

    private boolean showAutoCompleteChips() {
        if (chipStrip == null || recording || processing || historyMode || pendingAutoCompleteSuggestions.isEmpty()) {
            return false;
        }
        chipStrip.removeAllViews();
        showChipStrip();
        for (String suggestion : pendingAutoCompleteSuggestions) {
            chipStrip.addView(chip(suggestion, v -> applyAutoCompleteSuggestion(((TextView) v).getText().toString())));
        }
        return true;
    }

    private void showRecordingChips() {
        chipStrip.removeAllViews();
        showChipStrip();
        for (String value : Prefs.selectablePresetValues(this)) {
            final String preset = value;
            TextView chip = chip(Prefs.displayLabelForPreset(this, preset), v -> {
                selectedPreset = preset;
                selectedExpression = Prefs.expressionForPreset(this, selectedPreset);
                Prefs.setActivePreset(this, selectedPreset);
                showRecordingChips();
                setStatus(recording ? captureStyleStatus("Recording") : "Preset: " + selectedPresetLabel());
            });
            stylePresetChip(chip, preset.equals(selectedPreset));
            chipStrip.addView(chip);
        }
    }

    private void showVoiceStyleOverlay() {
        if (voiceStyleOverlay == null) {
            return;
        }
        boolean entering = voiceStyleOverlay.getVisibility() != View.VISIBLE;
        voiceStyleOverlay.animate().cancel();
        voiceStyleOverlay.removeAllViews();
        voiceStyleOverlay.setOrientation(LinearLayout.VERTICAL);
        voiceStyleOverlay.setPadding(dp(6), dp(3), dp(6), dp(5));
        voiceStyleOverlay.setBackgroundColor(Color.argb(
                248,
                Color.red(colors.background),
                Color.green(colors.background),
                Color.blue(colors.background)
        ));

        String context = retoneMode
                ? "Retone"
                : translationCapture && !translationTargetLanguage.isEmpty()
                        ? "Voice style - " + compactLanguageName(translationTargetLanguage)
                        : "Voice style";
        LinearLayout headerRow = new LinearLayout(this);
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView header = historyText(context, 12, true);
        header.setTextColor(colors.text);
        header.setAlpha(0.76f);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(6), 0, dp(6), 0);
        headerRow.addView(header, new LinearLayout.LayoutParams(
                0,
                dp(24)
                , 1f
        ));
        if (retoneMode) {
            TextView apply = historyActionButton("Apply", v -> applyRetone(), true);
            headerRow.addView(apply);
        }
        voiceStyleOverlay.addView(headerRow, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(26)
        ));

        List<PromptProfile> styles = Prefs.promptProfiles(this);
        HorizontalScrollView scroller = new HorizontalScrollView(this);
        scroller.setHorizontalScrollBarEnabled(false);
        scroller.setFillViewport(false);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(1), 0, dp(5), 0);
        for (PromptProfile style : styles) {
            TextView button = voiceStyleButton(style);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    dp(40)
            );
            params.setMargins(dp(2), dp(2), dp(2), dp(2));
            row.addView(button, params);
        }
        scroller.addView(row);
        voiceStyleOverlay.addView(scroller, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(44)
        ));

        View divider = new View(this);
        divider.setBackgroundColor(colors.stroke);
        LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(1)
        );
        dividerParams.setMargins(dp(3), dp(3), dp(3), dp(2));
        voiceStyleOverlay.addView(divider, dividerParams);
        voiceStyleOverlay.addView(expressionControl(), new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
        ));
        voiceStyleOverlay.setVisibility(View.VISIBLE);
        voiceStyleOverlay.setClickable(true);
        if (entering) {
            voiceStyleOverlay.setAlpha(0f);
            voiceStyleOverlay.animate().alpha(1f).setDuration(140).start();
        } else {
            voiceStyleOverlay.setAlpha(1f);
        }
    }

    private TextView voiceStyleButton(PromptProfile style) {
        boolean selected = style.id.equals(selectedPreset);
        TextView button = historyText(style.displayName(), 12, true);
        button.setGravity(Gravity.CENTER);
        button.setSingleLine(true);
        button.setEllipsize(TextUtils.TruncateAt.END);
        button.setPadding(dp(12), 0, dp(12), 0);
        button.setMinWidth(dp(68));
        button.setMaxWidth(dp(150));
        button.setTextColor(selected ? colors.onAccent : colors.text);
        button.setBackground(keyBackground(selected ? colors.accent : colors.keyAlt, selected));
        button.setContentDescription("Voice style " + style.name + (selected ? ", selected" : ""));
        button.setOnClickListener(v -> {
            if ((!recording && !retoneMode) || processing || instructionCapture) {
                return;
            }
            haptic(v);
            selectedPreset = style.id;
            selectedExpression = Prefs.expressionForPreset(this, selectedPreset);
            Prefs.setActivePreset(this, selectedPreset);
            showVoiceStyleOverlay();
            setStatus(captureStyleStatus(retoneMode ? "Retone" : "Recording"));
        });
        return button;
    }

    private View expressionControl() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(4), 0, dp(4), 0);

        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = historyText("Expression", 11, true);
        title.setAlpha(0.72f);
        titleRow.addView(title, new LinearLayout.LayoutParams(0, dp(22), 1f));
        TextView current = historyText(Prefs.expressionLabel(selectedExpression), 11, true);
        current.setTextColor(colors.accent);
        current.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        current.setPadding(dp(8), 0, dp(5), 0);
        titleRow.addView(current, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                dp(22)
        ));
        panel.addView(titleRow);

        List<TextView> detentLabels = new ArrayList<>();
        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.HORIZONTAL);
        String[] names = {"Reserved", "Subtle", "Natural", "Lively", "Expressive"};
        for (int i = 0; i < names.length; i++) {
            TextView label = historyText(names[i], 9, i == selectedExpression);
            label.setGravity(Gravity.CENTER);
            styleExpressionDetentLabel(label, i == selectedExpression);
            detentLabels.add(label);
            labels.addView(label, new LinearLayout.LayoutParams(0, dp(18), 1f));
        }

        SeekBar slider = new SeekBar(this);
        expressionSlider = slider;
        slider.setMax(Prefs.EXPRESSION_EXPRESSIVE);
        slider.setProgress(selectedExpression);
        slider.setPadding(dp(7), 0, dp(7), 0);
        slider.setContentDescription("Expression: " + Prefs.expressionLabel(selectedExpression));
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            private int lastProgress = selectedExpression;

            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                selectedExpression = Prefs.sanitizeExpression(progress);
                current.setText(Prefs.expressionLabel(selectedExpression));
                seekBar.setContentDescription("Expression: " + Prefs.expressionLabel(selectedExpression));
                for (int i = 0; i < detentLabels.size(); i++) {
                    styleExpressionDetentLabel(detentLabels.get(i), i == selectedExpression);
                }
                if (fromUser && progress != lastProgress) {
                    haptic(seekBar);
                }
                lastProgress = progress;
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                Prefs.setExpressionForPreset(VoiceFlowKeyboardService.this, selectedPreset, selectedExpression);
                setStatus(captureStyleStatus(retoneMode ? "Retone" : "Recording"));
            }
        });
        panel.addView(slider, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(38)
        ));
        panel.addView(labels);
        return panel;
    }

    private void styleExpressionDetentLabel(TextView label, boolean selected) {
        label.setTypeface(selected ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        label.setTextColor(selected ? colors.accent : colors.text);
        label.setAlpha(selected ? 1f : 0.56f);
    }

    private void hideVoiceStyleOverlay() {
        if (voiceStyleOverlay == null) {
            return;
        }
        voiceStyleOverlay.animate().cancel();
        voiceStyleOverlay.setVisibility(View.GONE);
        voiceStyleOverlay.removeAllViews();
        voiceStyleOverlay.setAlpha(1f);
        expressionSlider = null;
    }

    private void showChipStrip() {
        if (statusText != null) {
            statusText.setVisibility(View.GONE);
        }
        if (chipScroller != null) {
            chipScroller.setVisibility(View.VISIBLE);
        }
        if (chipStrip != null) {
            chipStrip.setVisibility(View.VISIBLE);
        }
    }

    private void hideChipStrip() {
        if (chipStrip != null) {
            chipStrip.removeAllViews();
            chipStrip.setVisibility(View.GONE);
        }
        if (chipScroller != null) {
            chipScroller.setVisibility(View.GONE);
        }
        if (statusText != null) {
            statusText.setVisibility(View.VISIBLE);
        }
    }

    private void setHistoryControlsActive(boolean active) {
        if (translationButton != null) {
            translationButton.setVisibility(active ? View.INVISIBLE : View.VISIBLE);
        }
        if (createButton != null) {
            createButton.setVisibility(active ? View.INVISIBLE : View.VISIBLE);
        }
        if (instructionButton != null) {
            instructionButton.setVisibility(active ? View.INVISIBLE : View.VISIBLE);
        }
        if (micButton != null) {
            micButton.setVisibility(active ? View.INVISIBLE : View.VISIBLE);
        }
        if (topHistoryButton != null) {
            topHistoryButton.setVisibility(View.VISIBLE);
            topHistoryButton.setImageResource(active ? R.drawable.ic_keyboard_24 : R.drawable.ic_history_24);
        }
        updateRecordingControls();
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
        String[] presets = Prefs.selectablePresetValues(this);
        for (int i = 0; i < presets.length; i++) {
            menu.getMenu().add(0, i, i, Prefs.labelForPreset(this, presets[i]));
        }
        menu.getMenu().add(1, 100, 100, "Settings");
        menu.setOnMenuItemClickListener(item -> {
            haptic(anchor);
            if (item.getItemId() == 100) {
                openSettings();
                return true;
            }
            int index = item.getItemId();
            if (index >= 0 && index < presets.length) {
                selectedPreset = presets[index];
                selectedExpression = Prefs.expressionForPreset(this, selectedPreset);
                Prefs.setActivePreset(this, selectedPreset);
                if (recording) {
                    showRecordingChips();
                    setStatus(captureStyleStatus("Recording"));
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

    private ImageButton instructionButton() {
        ImageButton button = new ImageButton(this);
        button.setImageResource(R.drawable.ic_wand_24);
        button.setColorFilter(colors.text);
        button.setBackground(ovalBackground(colors.key, false));
        button.setScaleType(ImageButton.ScaleType.CENTER);
        button.setPadding(dp(8), dp(8), dp(8), dp(8));
        button.setMinimumWidth(0);
        button.setMinimumHeight(0);
        button.setContentDescription("Voice instruction");
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(38), dp(38));
        params.setMargins(dp(2), dp(2), dp(2), dp(2));
        button.setLayoutParams(params);
        return button;
    }

    private ImageButton createButton() {
        ImageButton button = new ImageButton(this);
        button.setImageResource(R.drawable.ic_create_text_24);
        button.setColorFilter(colors.text);
        button.setBackground(ovalBackground(colors.key, false));
        button.setScaleType(ImageButton.ScaleType.CENTER);
        button.setPadding(dp(8), dp(8), dp(8), dp(8));
        button.setMinimumWidth(0);
        button.setMinimumHeight(0);
        button.setContentDescription("Create and append text");
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(38), dp(38));
        params.setMargins(dp(2), dp(2), dp(2), dp(2));
        button.setLayoutParams(params);
        return button;
    }

    private ImageButton translationButton() {
        ImageButton button = new ImageButton(this);
        button.setImageResource(R.drawable.ic_translate_24);
        button.setColorFilter(colors.text);
        button.setBackground(ovalBackground(colors.key, false));
        button.setScaleType(ImageButton.ScaleType.CENTER);
        button.setPadding(dp(8), dp(8), dp(8), dp(8));
        button.setMinimumWidth(0);
        button.setMinimumHeight(0);
        button.setContentDescription("Translate voice to " + Prefs.translationTargetLanguage(this));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(38), dp(38));
        params.setMargins(dp(2), dp(2), dp(2), dp(2));
        button.setLayoutParams(params);
        return button;
    }

    private ImageButton plainIconButton(int drawableRes, View.OnClickListener listener) {
        ImageButton button = new ImageButton(this);
        button.setImageResource(drawableRes);
        button.setColorFilter(colors.text);
        button.setBackgroundColor(Color.TRANSPARENT);
        button.setScaleType(ImageButton.ScaleType.CENTER);
        button.setPadding(dp(8), dp(8), dp(8), dp(8));
        button.setMinimumWidth(0);
        button.setMinimumHeight(0);
        button.setOnClickListener(listener);
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
        view.setOnTouchListener(this::handleSwipe);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(KEY_HEIGHT_DP), weight);
        params.setMargins(0, 0, 0, 0);
        view.setLayoutParams(params);
        keyButtons.add(view);
        return view;
    }

    private TextView letterKeyButton(String value, float weight) {
        TextView key = keyButton(displayLetter(value), weight, v -> commitKey(value));
        key.setTag(value);
        letterButtons.add(key);
        return key;
    }

    private String displayLetter(String value) {
        return isShiftActive() ? value.toUpperCase(Locale.US) : value.toLowerCase(Locale.US);
    }

    private TextView spaceKey(float weight) {
        TextView key = keyButton("space", weight, v -> {
        });
        spaceButton = key;
        key.setOnTouchListener(this::handleSpaceTouch);
        return key;
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

    private void toggleShift() {
        if (recording || processing) {
            return;
        }
        long now = System.currentTimeMillis();
        if (capsLock) {
            capsLock = false;
            shift = false;
            autoShift = false;
            lastShiftTapMs = 0;
        } else if (autoShift && !shift) {
            autoShift = false;
            lastShiftTapMs = 0;
        } else if (shift && lastShiftTapMs > 0 && now - lastShiftTapMs <= SHIFT_DOUBLE_TAP_MS) {
            capsLock = true;
            shift = true;
            autoShift = false;
            lastShiftTapMs = 0;
        } else {
            shift = !shift;
            autoShift = false;
            lastShiftTapMs = shift ? now : 0;
        }
        updateShiftVisuals();
        setStatus(capsLock ? "Caps lock" : shift ? "Shift on" : "Ready");
    }

    private boolean isShiftActive() {
        return shift || autoShift || capsLock;
    }

    private void updateShiftVisuals() {
        boolean active = isShiftActive();
        for (TextView button : letterButtons) {
            Object tag = button.getTag();
            if (tag instanceof String) {
                button.setText(displayLetter((String) tag));
            }
        }
        if (shiftButton != null) {
            shiftButton.setText(capsLock ? "caps" : "shift");
            shiftButton.setTextColor(active ? colors.onAccent : colors.text);
            shiftButton.setBackground(keyVisualBackground(active ? colors.accent : colors.keyAlt, active));
        }
    }

    private boolean handleSpaceTouch(View view, MotionEvent event) {
        if (recording || processing) {
            return true;
        }
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            haptic(view);
            spaceCursorMode = false;
            spaceCursorLastStepX = event.getX();
            if (spaceCursorRunnable != null) {
                mainHandler.removeCallbacks(spaceCursorRunnable);
            }
            spaceCursorRunnable = () -> {
                if (recording || processing) {
                    return;
                }
                spaceCursorMode = true;
                clearAutoCorrection();
                setStatus("Cursor");
                haptic(view);
            };
            mainHandler.postDelayed(spaceCursorRunnable, SPACE_CURSOR_HOLD_MS);
            return true;
        }
        if (action == MotionEvent.ACTION_MOVE) {
            if (spaceCursorMode) {
                moveCursorFromSpaceDrag(event.getX());
            }
            return true;
        }
        if (action == MotionEvent.ACTION_UP) {
            boolean wasCursorMode = spaceCursorMode;
            stopSpaceCursorTracking();
            if (!wasCursorMode) {
                commitSeparator(" ");
            }
            return true;
        }
        if (action == MotionEvent.ACTION_CANCEL) {
            stopSpaceCursorTracking();
            return true;
        }
        return true;
    }

    private void moveCursorFromSpaceDrag(float x) {
        int stepPx = Math.max(1, dp(SPACE_CURSOR_STEP_DP));
        int steps = (int) ((x - spaceCursorLastStepX) / stepPx);
        if (steps == 0) {
            return;
        }
        moveCursorBy(steps);
        spaceCursorLastStepX += steps * stepPx;
    }

    private void stopSpaceCursorTracking() {
        if (spaceCursorRunnable != null) {
            mainHandler.removeCallbacks(spaceCursorRunnable);
            spaceCursorRunnable = null;
        }
        spaceCursorMode = false;
    }

    private void moveCursorBy(int delta) {
        InputConnection connection = getCurrentInputConnection();
        if (connection == null || delta == 0) {
            return;
        }
        if (!moveCursorWithExtractedText(connection, delta)) {
            int keyCode = delta < 0 ? KeyEvent.KEYCODE_DPAD_LEFT : KeyEvent.KEYCODE_DPAD_RIGHT;
            for (int i = 0; i < Math.abs(delta); i++) {
                connection.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, keyCode));
                connection.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, keyCode));
            }
        }
        clearLastVoiceInsertion();
        clearAutoCorrection();
        updateAutoCapitalization();
    }

    private boolean moveCursorWithExtractedText(InputConnection connection, int delta) {
        ExtractedText extracted = connection.getExtractedText(new ExtractedTextRequest(), 0);
        if (extracted == null || extracted.text == null) {
            return false;
        }
        int selection = extracted.selectionEnd >= 0 ? extracted.selectionEnd : extracted.selectionStart;
        if (selection < 0) {
            return false;
        }
        int min = extracted.startOffset;
        int max = extracted.startOffset + extracted.text.length();
        int current = Math.max(min, Math.min(max, extracted.startOffset + selection));
        int target = Math.max(min, Math.min(max, current + delta));
        if (target == current) {
            return true;
        }
        return connection.setSelection(target, target);
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
        boolean letterKey = !symbolsMode && value.length() == 1 && Character.isLetter(value.charAt(0));
        String text = symbolsMode ? value : (isShiftActive() ? value.toUpperCase(Locale.US) : value.toLowerCase(Locale.US));
        if (isSeparator(text)) {
            commitSeparator(text);
            return;
        }
        commitText(text);
        if (letterKey && (shift || autoShift) && !capsLock) {
            shift = false;
            autoShift = false;
            lastShiftTapMs = 0;
            updateShiftVisuals();
        }
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
        lastShiftTapMs = 0;
        boolean corrected = applyPendingAutoCorrection(true);
        applyRecentPhraseReplacement();
        commitText(separator);
        if (corrected) {
            lastAutoCorrectSeparator = separator;
        }
        clearAutoCorrection();
        updateAutoCapitalization();
    }

    private void commitText(String text) {
        clearLastVoiceInsertion();
        InputConnection connection = getCurrentInputConnection();
        if (connection != null) {
            connection.commitText(text, 1);
        }
    }

    private String insertVoiceText(String text) {
        InputConnection connection = getCurrentInputConnection();
        if (connection == null) {
            return "";
        }
        String prepared = prepareVoiceOutput(text);
        if (prepared.isEmpty()) {
            return "";
        }
        if (needsLeadingSpace(connection, prepared)) {
            prepared = " " + prepared;
        }
        if (!connection.commitText(prepared, 1)) {
            return "";
        }
        clearAutoCorrection();
        updateAutoCapitalization();
        return prepared;
    }

    private String appendCreatedText(String text) {
        InputConnection connection = getCurrentInputConnection();
        if (connection == null) {
            return "";
        }
        ExtractedText extracted = connection.getExtractedText(new ExtractedTextRequest(), 0);
        if (extracted != null && extracted.text != null) {
            int end = Math.max(0, extracted.startOffset + extracted.text.length());
            if (!connection.setSelection(end, end)) {
                return "";
            }
        } else {
            CharSequence selected = connection.getSelectedText(0);
            if (selected != null && selected.length() > 0) {
                CharSequence before = connection.getTextBeforeCursor(200000, 0);
                int selectionEnd = (before == null ? 0 : before.length()) + selected.length();
                if (!connection.setSelection(selectionEnd, selectionEnd)) {
                    return "";
                }
            }
        }
        return insertVoiceText(text);
    }

    private void rememberLastVoiceInsertion(
            String rawTranscript,
            String insertedText,
            String preset,
            int expression,
            String operation,
            String targetLanguage,
            String historyId
    ) {
        if (rawTranscript == null
                || rawTranscript.trim().isEmpty()
                || insertedText == null
                || insertedText.isEmpty()
                || historyId == null
                || historyId.isEmpty()) {
            clearLastVoiceInsertion();
            return;
        }
        lastVoiceRawTranscript = rawTranscript.trim();
        lastVoiceInsertedText = insertedText;
        lastVoicePreset = preset;
        lastVoiceExpression = Prefs.sanitizeExpression(expression);
        lastVoiceOperation = VoiceHistoryItem.OPERATION_TRANSLATION.equals(operation)
                ? VoiceHistoryItem.OPERATION_TRANSLATION
                : VoiceHistoryItem.OPERATION_CREATION.equals(operation)
                        ? VoiceHistoryItem.OPERATION_CREATION
                        : VoiceHistoryItem.OPERATION_DICTATION;
        lastVoiceTargetLanguage = targetLanguage == null ? "" : targetLanguage;
        lastVoiceHistoryId = historyId;
        lastVoiceSelectionEnd = currentSelectionEnd();
    }

    private void clearLastVoiceInsertion() {
        lastVoiceRawTranscript = "";
        lastVoiceInsertedText = "";
        lastVoicePreset = "";
        lastVoiceExpression = Prefs.DEFAULT_EXPRESSION;
        lastVoiceOperation = VoiceHistoryItem.OPERATION_DICTATION;
        lastVoiceTargetLanguage = "";
        lastVoiceHistoryId = "";
        lastVoiceSelectionEnd = -1;
        if (chipStrip != null && !recording && !processing && !retoneMode) {
            showIdleChips();
        }
    }

    private void openRetoneOverlay() {
        if (processing || recording || lastVoiceRawTranscript.isEmpty() || lastVoiceHistoryId.isEmpty()) {
            return;
        }
        if (!hasNetworkConnectivity()) {
            setStatus("Retone requires a connection");
            return;
        }
        String provider = Prefs.transformProvider(this);
        if (!Prefs.hasApiKeyForProvider(this, provider)) {
            setStatus("Add a " + Prefs.providerLabel(provider) + " key to retone");
            return;
        }
        if (historyMode) {
            hideHistoryPanel();
        }
        selectedPreset = lastVoicePreset;
        selectedExpression = lastVoiceExpression;
        retoneMode = true;
        hideChipStrip();
        setKeyboardLocked(true);
        setRetoneTopControls(true);
        showVoiceStyleOverlay();
        updateRecordingControls();
        setStatus(captureStyleStatus("Retone"));
    }

    private void closeRetoneOverlay(String status) {
        retoneMode = false;
        hideVoiceStyleOverlay();
        setKeyboardLocked(false);
        setRetoneTopControls(false);
        updateRecordingControls();
        showIdleChips();
        setStatus(status);
    }

    private void setRetoneTopControls(boolean active) {
        if (translationButton != null) {
            translationButton.setEnabled(!active);
            translationButton.setAlpha(active ? 0.45f : 1f);
        }
        if (createButton != null) {
            createButton.setEnabled(!active);
            createButton.setAlpha(active ? 0.45f : 1f);
        }
        if (instructionButton != null) {
            instructionButton.setEnabled(!active);
            instructionButton.setAlpha(active ? 0.45f : 1f);
        }
        if (micButton != null) {
            micButton.setEnabled(!active);
            micButton.setAlpha(active ? 0.45f : 1f);
        }
        if (topHistoryButton != null) {
            topHistoryButton.setEnabled(!active);
            topHistoryButton.setAlpha(active ? 0.45f : 1f);
        }
    }

    private void applyRetone() {
        if (!retoneMode || processing) {
            return;
        }
        if (!hasNetworkConnectivity()) {
            setStatus("Retone requires a connection");
            return;
        }
        final String raw = lastVoiceRawTranscript;
        final String oldInserted = lastVoiceInsertedText;
        final String historyId = lastVoiceHistoryId;
        final String operation = lastVoiceOperation;
        final String targetLanguage = lastVoiceTargetLanguage;
        final String preset = selectedPreset;
        final int expression = selectedExpression;

        Prefs.setActivePreset(this, preset);
        Prefs.setExpressionForPreset(this, preset, expression);
        retoneMode = false;
        processing = true;
        hideVoiceStyleOverlay();
        setRetoneTopControls(false);
        updateRecordingControls();
        setKeyboardLocked(true);
        startStatusSpinner("Retoning: " + labelForPreset(preset) + " - " + Prefs.expressionLabel(expression));
        executor.execute(() -> {
            try {
                String result;
                if (VoiceHistoryItem.OPERATION_TRANSLATION.equals(operation)) {
                    result = TransformClient.translate(this, raw, targetLanguage, preset, expression);
                } else if (VoiceHistoryItem.OPERATION_CREATION.equals(operation)) {
                    result = TransformClient.createText(this, raw, preset, expression);
                } else {
                    result = TransformClient.transform(this, raw, preset, expression);
                }
                if (result == null || result.trim().isEmpty()) {
                    throw new IllegalStateException("Retone returned no text.");
                }
                mainHandler.post(() -> {
                    Prefs.updateTranscriptHistory(this, historyId, raw, result, preset, expression);
                    String replacement = replaceLastVoiceInsertion(oldInserted, result);
                    if (!replacement.isEmpty()) {
                        rememberLastVoiceInsertion(
                                raw,
                                replacement,
                                preset,
                                expression,
                                operation,
                                targetLanguage,
                                historyId
                        );
                        finishProcessingState("Retoned - " + labelForPreset(preset)
                                + " - " + Prefs.expressionLabel(expression));
                    } else {
                        clearLastVoiceInsertion();
                        finishProcessingState("Retone saved in history; field changed");
                    }
                });
            } catch (Exception e) {
                mainHandler.post(() -> finishProcessingState("Retone failed: " + concise(e)));
            }
        });
    }

    private String replaceLastVoiceInsertion(String oldInserted, String newText) {
        InputConnection connection = getCurrentInputConnection();
        if (connection == null || oldInserted == null || oldInserted.isEmpty()) {
            return "";
        }
        CharSequence selected = connection.getSelectedText(0);
        if (selected != null && selected.length() > 0) {
            return "";
        }
        CharSequence before = connection.getTextBeforeCursor(oldInserted.length(), 0);
        if (before == null || !oldInserted.contentEquals(before)) {
            return "";
        }
        String prepared = prepareVoiceOutput(newText);
        if (prepared.isEmpty()) {
            return "";
        }
        if (oldInserted.startsWith(" ") && !prepared.startsWith(" ")) {
            prepared = " " + prepared;
        }
        connection.beginBatchEdit();
        try {
            if (!connection.deleteSurroundingText(oldInserted.length(), 0)) {
                return "";
            }
            return connection.commitText(prepared, 1) ? prepared : "";
        } finally {
            connection.endBatchEdit();
            clearAutoCorrection();
            updateAutoCapitalization();
        }
    }

    private int currentSelectionEnd() {
        InputConnection connection = getCurrentInputConnection();
        if (connection == null) {
            return -1;
        }
        ExtractedText extracted = connection.getExtractedText(new ExtractedTextRequest(), 0);
        return extracted == null ? -1 : extracted.selectionEnd;
    }

    private boolean replaceWholeFieldText(String text) {
        InputConnection connection = getCurrentInputConnection();
        if (connection == null || text == null || text.trim().isEmpty()) {
            return false;
        }
        connection.beginBatchEdit();
        try {
            boolean selectedAll = connection.performContextMenuAction(android.R.id.selectAll);
            if (!selectedAll) {
                ExtractedText extracted = connection.getExtractedText(new ExtractedTextRequest(), 0);
                if (extracted == null || extracted.text == null) {
                    return false;
                }
                int start = Math.max(0, extracted.startOffset);
                if (!connection.setSelection(start, start + extracted.text.length())) {
                    return false;
                }
            }
            boolean replaced = connection.commitText(text, 1);
            if (replaced) {
                clearLastVoiceInsertion();
                clearAutoCorrection();
                updateAutoCapitalization();
            }
            return replaced;
        } finally {
            connection.endBatchEdit();
        }
    }

    private void pasteHistoryText(String text) {
        if (text == null || text.trim().isEmpty()) {
            setStatus("Nothing to paste");
            return;
        }
        clearLastVoiceInsertion();
        hideHistoryPanel();
        insertVoiceText(text);
        setStatus("Pasted");
    }

    private void copyHistoryText(String text) {
        if (text == null || text.trim().isEmpty()) {
            setStatus("Nothing to copy");
            return;
        }
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null) {
            setStatus("Clipboard unavailable");
            return;
        }
        clipboard.setPrimaryClip(ClipData.newPlainText("VoiceFlow transcript", text.trim()));
        setStatus("Copied");
    }

    private void createHistoryTransform(VoiceHistoryItem item, String preset, int expression) {
        if (item.rawText.trim().isEmpty()) {
            setStatus("No raw transcript saved");
            return;
        }
        if (Prefs.PRESET_RAW.equals(preset)) {
            Prefs.selectTranscriptHistoryVariant(this, item.id, Prefs.PRESET_RAW, Prefs.DEFAULT_EXPRESSION);
            showHistoryPanel();
            return;
        }
        if (!hasNetworkConnectivity()) {
            setStatus("No connection for transform");
            return;
        }
        processing = true;
        setKeyboardLocked(true);
        String styleLabel = historyOutputLabel(preset, expression);
        startStatusSpinner(item.isTranslation()
                ? "Translating: " + styleLabel
                : "Creating " + styleLabel);
        executor.execute(() -> {
            try {
                String result;
                if (item.isTranslation()) {
                    result = TransformClient.translate(this, item.rawText, item.targetLanguage, preset, expression);
                } else if (item.isCreation()) {
                    result = TransformClient.createText(this, item.rawText, preset, expression);
                } else {
                    result = TransformClient.transform(this, item.rawText, preset, expression);
                }
                mainHandler.post(() -> {
                    Prefs.updateTranscriptHistory(this, item.id, item.rawText, result, preset, expression);
                    stopStatusSpinner();
                    recording = false;
                    processing = false;
                    setKeyboardLocked(false);
                    setHistoryControlsActive(true);
                    showHistoryPanel();
                    setStatus((item.isTranslation() ? "Translated - " : "Created ") + styleLabel);
                });
            } catch (Exception e) {
                mainHandler.post(() -> finishProcessingState("Create failed: " + concise(e)));
            }
        });
    }

    private String prepareVoiceOutput(String text) {
        String result = text == null ? "" : text.trim();
        result = applyPhraseReplacements(result);
        result = removeShortTrailingPeriod(result);
        return result;
    }

    private String applyPhraseReplacements(String text) {
        String result = text;
        for (PhraseReplacement replacement : Prefs.allPhraseReplacements(this)) {
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
            clearLastVoiceInsertion();
            if (deleteSelectionIfAny(connection)) {
                scheduleAutoCorrection();
                updateAutoCapitalization();
                return;
            }
            if (undoLastAutoCorrectionIfPossible(connection)) {
                updateAutoCapitalization();
                return;
            }
            clearLastAutoCorrection();
            connection.deleteSurroundingText(1, 0);
        }
        scheduleAutoCorrection();
        updateAutoCapitalization();
    }

    private void deletePreviousWord() {
        if (recording || processing) {
            return;
        }
        InputConnection connection = getCurrentInputConnection();
        if (connection == null) {
            return;
        }
        clearLastVoiceInsertion();
        if (deleteSelectionIfAny(connection)) {
            scheduleAutoCorrection();
            updateAutoCapitalization();
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
        clearLastAutoCorrection();
        connection.deleteSurroundingText(Math.max(count, 1), 0);
        updateAutoCapitalization();
    }

    private boolean deleteSelectionIfAny(InputConnection connection) {
        CharSequence selected = connection.getSelectedText(0);
        if (selected == null || selected.length() == 0) {
            return false;
        }
        clearLastAutoCorrection();
        clearAutoCorrection();
        connection.commitText("", 1);
        return true;
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
        if (Prefs.isLearnedWord(this, word)) {
            clearAutoCorrection();
            return;
        }

        String fallback = fallbackCorrectionFor(word);
        if (!fallback.isEmpty()) {
            List<String> suggestions = new ArrayList<>();
            suggestions.add(fallback);
            setPendingAutoCorrection(word, suggestions, true);
            return;
        }

        ensureSpellChecker();
        if (spellCheckerSession == null) {
            setPendingAutoComplete(word);
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
        if (manager == null
                || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !manager.isSpellCheckerEnabled())) {
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
        if (Prefs.isLearnedWord(this, word)) {
            clearAutoCorrection();
            return;
        }

        int attrs = info.getSuggestionsAttributes();
        if ((attrs & SuggestionsInfo.RESULT_ATTR_IN_THE_DICTIONARY) != 0) {
            setPendingAutoComplete(word);
            return;
        }

        List<String> suggestions = new ArrayList<>();
        for (int i = 0; i < info.getSuggestionsCount(); i++) {
            String candidate = info.getSuggestionAt(i);
            if (candidate != null && !candidate.equalsIgnoreCase(word) && sameLeadingLetter(word, candidate)) {
                addSuggestion(suggestions, matchCase(word, candidate));
            }
        }
        if (suggestions.isEmpty()) {
            setPendingAutoComplete(word);
            return;
        }

        boolean recommended = (attrs & SuggestionsInfo.RESULT_ATTR_HAS_RECOMMENDED_SUGGESTIONS) != 0;
        setPendingAutoCorrection(word, suggestions, recommended);
    }

    private boolean applyPendingAutoCorrection(boolean autoOnly) {
        return applyPendingAutoCorrection(autoOnly, pendingAutoCorrectReplacement);
    }

    private boolean applyPendingAutoCorrection(boolean autoOnly, String replacement) {
        if (replacement == null || replacement.trim().isEmpty() || (autoOnly && !pendingAutoCorrectAccept)) {
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
        connection.commitText(replacement, 1);
        rememberLastAutoCorrection(word, replacement, "");
        clearAutoCorrection();
        return true;
    }

    private void rejectPendingAutoCorrection() {
        if (!pendingAutoCorrectWord.isEmpty()) {
            learnAutoCorrectionWord(pendingAutoCorrectWord);
            setStatus("Kept " + pendingAutoCorrectWord);
        }
        clearAutoCorrection();
        updateAutoCapitalization();
    }

    private String alternateAutoCorrection() {
        for (String suggestion : pendingAutoCorrectSuggestions) {
            if (!suggestion.equals(pendingAutoCorrectReplacement)) {
                return suggestion;
            }
        }
        return "";
    }

    private void setPendingAutoCorrection(String word, List<String> suggestions, boolean autoAccept) {
        if (suggestions == null || suggestions.isEmpty()) {
            clearAutoCorrection();
            return;
        }
        pendingAutoCorrectWord = word;
        pendingAutoCompletePrefix = "";
        pendingAutoCompleteSuggestions.clear();
        pendingAutoCorrectSuggestions.clear();
        for (String suggestion : suggestions) {
            addSuggestion(pendingAutoCorrectSuggestions, suggestion);
        }
        if (pendingAutoCorrectSuggestions.isEmpty()) {
            clearAutoCorrection();
            return;
        }
        pendingAutoCorrectReplacement = pendingAutoCorrectSuggestions.get(0);
        pendingAutoCorrectAccept = autoAccept;
        showIdleChips();
    }

    private void setPendingAutoComplete(String prefix) {
        pendingAutoCorrectWord = "";
        pendingAutoCorrectReplacement = "";
        pendingAutoCorrectSuggestions.clear();
        pendingAutoCorrectAccept = false;
        pendingAutoCompletePrefix = prefix == null ? "" : prefix;
        pendingAutoCompleteSuggestions.clear();
        if (pendingAutoCompletePrefix.length() < 2 || containsDigit(pendingAutoCompletePrefix)) {
            hideChipStrip();
            return;
        }
        for (String word : COMMON_COMPLETIONS) {
            if (word.regionMatches(true, 0, pendingAutoCompletePrefix, 0, pendingAutoCompletePrefix.length())
                    && !word.equalsIgnoreCase(pendingAutoCompletePrefix)) {
                addCompletion(pendingAutoCompleteSuggestions, matchCase(pendingAutoCompletePrefix, word));
            }
            if (pendingAutoCompleteSuggestions.size() >= 3) {
                break;
            }
        }
        showIdleChips();
    }

    private void applyAutoCompleteSuggestion(String suggestion) {
        if (suggestion == null || suggestion.trim().isEmpty()) {
            return;
        }
        InputConnection connection = getCurrentInputConnection();
        if (connection == null) {
            return;
        }
        String word = currentWordBeforeCursor();
        if (!word.equals(pendingAutoCompletePrefix)) {
            clearAutoCorrection();
            return;
        }
        connection.deleteSurroundingText(word.length(), 0);
        connection.commitText(suggestion.trim() + " ", 1);
        clearLastAutoCorrection();
        clearAutoCorrection();
        updateAutoCapitalization();
    }

    private void clearAutoCorrection() {
        pendingAutoCorrectWord = "";
        pendingAutoCorrectReplacement = "";
        pendingAutoCorrectSuggestions.clear();
        pendingAutoCompletePrefix = "";
        pendingAutoCompleteSuggestions.clear();
        pendingAutoCorrectAccept = false;
        if (spellCheckRunnable != null) {
            mainHandler.removeCallbacks(spellCheckRunnable);
        }
        if (chipStrip != null && !recording && !processing) {
            hideChipStrip();
        }
    }

    private void addSuggestion(List<String> suggestions, String candidate) {
        if (candidate == null) {
            return;
        }
        String trimmed = candidate.trim();
        if (trimmed.isEmpty()) {
            return;
        }
        for (String existing : suggestions) {
            if (existing.equalsIgnoreCase(trimmed)) {
                return;
            }
        }
        if (suggestions.size() < 2) {
            suggestions.add(trimmed);
        }
    }

    private void addCompletion(List<String> suggestions, String candidate) {
        if (candidate == null) {
            return;
        }
        String trimmed = candidate.trim();
        if (trimmed.isEmpty()) {
            return;
        }
        for (String existing : suggestions) {
            if (existing.equalsIgnoreCase(trimmed)) {
                return;
            }
        }
        suggestions.add(trimmed);
    }

    private void rememberLastAutoCorrection(String original, String replacement, String separator) {
        lastAutoCorrectOriginal = original == null ? "" : original;
        lastAutoCorrectReplacement = replacement == null ? "" : replacement;
        lastAutoCorrectSeparator = separator == null ? "" : separator;
    }

    private void clearLastAutoCorrection() {
        lastAutoCorrectOriginal = "";
        lastAutoCorrectReplacement = "";
        lastAutoCorrectSeparator = "";
    }

    private boolean undoLastAutoCorrectionIfPossible(InputConnection connection) {
        if (lastAutoCorrectOriginal.isEmpty() || lastAutoCorrectReplacement.isEmpty()) {
            return false;
        }
        String tail = lastAutoCorrectReplacement + lastAutoCorrectSeparator;
        if (tail.isEmpty()) {
            return false;
        }
        CharSequence before = connection.getTextBeforeCursor(tail.length(), 0);
        if (before == null || !tail.contentEquals(before)) {
            clearLastAutoCorrection();
            return false;
        }
        connection.deleteSurroundingText(tail.length(), 0);
        connection.commitText(lastAutoCorrectOriginal, 1);
        learnAutoCorrectionWord(lastAutoCorrectOriginal);
        clearLastAutoCorrection();
        clearAutoCorrection();
        setStatus("Reverted");
        return true;
    }

    private void learnAutoCorrectionWord(String word) {
        if (!shouldLearnAutoCorrectionWord(word)) {
            return;
        }
        Prefs.learnWord(this, word);
    }

    private boolean shouldLearnAutoCorrectionWord(String word) {
        if (word == null || COMMON_TYPOS.containsKey(word.toLowerCase(Locale.US)) || containsDigit(word)) {
            return false;
        }
        String trimmed = word.trim();
        if (trimmed.length() < 2 || trimmed.length() > 40) {
            return false;
        }
        for (int i = 0; i < trimmed.length(); i++) {
            if (!isAutoCorrectWordCharacter(trimmed.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private void updateAutoCapitalization() {
        if (capsLock || shift || symbolsMode || recording || processing) {
            if (autoShift) {
                autoShift = false;
                updateShiftVisuals();
            }
            return;
        }
        boolean next = shouldAutoCapitalize();
        if (autoShift != next) {
            autoShift = next;
            updateShiftVisuals();
        }
    }

    private boolean shouldAutoCapitalize() {
        if (!shouldTypingAssistance()) {
            return false;
        }
        InputConnection connection = getCurrentInputConnection();
        if (connection == null) {
            return false;
        }
        CharSequence before = connection.getTextBeforeCursor(80, 0);
        if (before == null || before.length() == 0) {
            return true;
        }
        for (int i = before.length() - 1; i >= 0; i--) {
            char value = before.charAt(i);
            if (value == '\n') {
                return true;
            }
            if (Character.isWhitespace(value)) {
                continue;
            }
            return value == '.' || value == '?' || value == '!';
        }
        return true;
    }

    private boolean shouldTypingAssistance() {
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
        return !isTypingAssistanceBlockedVariation(variation);
    }

    private boolean shouldAutoCorrectTyping() {
        if (recording || processing || symbolsMode) {
            return false;
        }
        return shouldTypingAssistance();
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
        return !isPasswordVariation(variation);
    }

    private boolean isTypingAssistanceBlockedVariation(int variation) {
        return isPasswordVariation(variation)
                || variation == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
                || variation == InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS
                || variation == InputType.TYPE_TEXT_VARIATION_URI;
    }

    private boolean isPasswordVariation(int variation) {
        return variation == InputType.TYPE_TEXT_VARIATION_PASSWORD
                || variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                || variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD;
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
        for (PhraseReplacement replacement : Prefs.allPhraseReplacements(this)) {
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
        clearLastVoiceInsertion();
        boolean corrected = applyPendingAutoCorrection(true);
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
            if (corrected) {
                lastAutoCorrectSeparator = "\n";
            }
            clearAutoCorrection();
            updateAutoCapitalization();
            return;
        }
        clearAutoCorrection();
        updateAutoCapitalization();
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
        autoShift = false;
        lastShiftTapMs = 0;
        populateKeyboardPanel(keyboardPanel);
        if (!symbolsMode) {
            updateAutoCapitalization();
        }
        setStatus(symbolsMode ? "Symbols" : capsLock ? "Caps lock" : "Ready");
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
        if (recording) {
            if (translationCapture || creationCapture || instructionCapture) {
                setStatus(translationCapture
                        ? "Tap the translate button to finish"
                        : creationCapture
                                ? "Tap the create button to finish"
                                : "Tap the instruction button to finish");
                return;
            }
            if (offlineRecordingSession) {
                stopOfflineRecordingAndTranscribe();
            } else {
                stopCloudRecordingAndTranscribe();
            }
            return;
        }
        if (!recording && !shouldAllowVoiceCapture()) {
            setStatus("Voice disabled in this field");
            return;
        }
        if (historyMode) {
            hideHistoryPanel();
        }
        String provider = Prefs.transcriptionProvider(this);
        if (isOfflineTranscriptionProvider(provider)) {
            toggleOfflineRecording(provider);
        } else if (!hasNetworkConnectivity()) {
            toggleOfflineFallbackRecording(provider);
        } else {
            toggleCloudRecording();
        }
    }

    private void toggleCreationCapture() {
        if (processing) {
            return;
        }
        if (recording) {
            if (!creationCapture || translationCapture || instructionCapture) {
                setStatus("Finish or cancel the current recording first");
                return;
            }
            if (offlineRecordingSession) {
                stopOfflineRecordingAndTranscribe();
            } else {
                stopCloudRecordingAndTranscribe();
            }
            return;
        }
        if (!shouldAllowVoiceCapture()) {
            setStatus("Voice disabled in password fields");
            return;
        }
        if (!hasNetworkConnectivity()) {
            setStatus("Text creation requires a connection");
            return;
        }
        String transformProvider = Prefs.transformProvider(this);
        if (!Prefs.hasApiKeyForProvider(this, transformProvider)) {
            setStatus("Add a " + Prefs.providerLabel(transformProvider) + " key for text creation");
            return;
        }
        if (historyMode) {
            hideHistoryPanel();
        }
        creationCapture = true;
        String provider = Prefs.transcriptionProvider(this);
        if (isOfflineTranscriptionProvider(provider)) {
            toggleOfflineRecording(provider);
        } else {
            toggleCloudRecording();
        }
        if (!recording && !processing) {
            creationCapture = false;
            setCreateVisual(false, true);
            setMicVisual(false, true);
        }
    }

    private void toggleInstructionCapture() {
        if (processing) {
            return;
        }
        if (recording) {
            if (!instructionCapture || translationCapture || creationCapture) {
                setStatus("Finish or cancel the current recording first");
                return;
            }
            if (offlineRecordingSession) {
                stopOfflineRecordingAndTranscribe();
            } else {
                stopCloudRecordingAndTranscribe();
            }
            return;
        }
        if (!shouldAllowVoiceCapture()) {
            setStatus("Voice disabled in password fields");
            return;
        }
        if (!hasNetworkConnectivity()) {
            setStatus("Voice instructions require a connection");
            return;
        }
        String transformProvider = Prefs.transformProvider(this);
        if (!Prefs.hasApiKeyForProvider(this, transformProvider)) {
            setStatus("Add a " + Prefs.providerLabel(transformProvider) + " key for voice instructions");
            return;
        }
        String sourceText = currentEditableFieldText();
        if (sourceText.trim().isEmpty()) {
            setStatus("Add text to the field first");
            return;
        }
        if (historyMode) {
            hideHistoryPanel();
        }
        instructionCapture = true;
        instructionSourceText = sourceText;
        String provider = Prefs.transcriptionProvider(this);
        if (isOfflineTranscriptionProvider(provider)) {
            toggleOfflineRecording(provider);
        } else {
            toggleCloudRecording();
        }
        if (!recording && !processing) {
            instructionCapture = false;
            instructionSourceText = "";
            setInstructionVisual(false, true);
            setMicVisual(false, true);
        }
    }

    private void toggleTranslationCapture() {
        if (processing) {
            return;
        }
        if (recording) {
            if (!translationCapture) {
                setStatus("Finish or cancel the current recording first");
                return;
            }
            if (offlineRecordingSession) {
                stopOfflineRecordingAndTranscribe();
            } else {
                stopCloudRecordingAndTranscribe();
            }
            return;
        }
        if (!shouldAllowVoiceCapture()) {
            setStatus("Voice disabled in password fields");
            return;
        }
        if (!hasNetworkConnectivity()) {
            setStatus("Translation requires a connection");
            return;
        }
        String transformProvider = Prefs.transformProvider(this);
        if (!Prefs.hasApiKeyForProvider(this, transformProvider)) {
            setStatus("Add a " + Prefs.providerLabel(transformProvider) + " key for translation");
            return;
        }
        if (historyMode) {
            hideHistoryPanel();
        }
        translationCapture = true;
        translationTargetLanguage = Prefs.translationTargetLanguage(this);
        String provider = Prefs.transcriptionProvider(this);
        if (isOfflineTranscriptionProvider(provider)) {
            toggleOfflineRecording(provider);
        } else {
            toggleCloudRecording();
        }
        if (!recording && !processing) {
            translationCapture = false;
            translationTargetLanguage = "";
            setTranslationVisual(false, true);
            setInstructionVisual(false, true);
            setMicVisual(false, true);
        }
    }

    private String currentEditableFieldText() {
        InputConnection connection = getCurrentInputConnection();
        if (connection == null) {
            return "";
        }
        ExtractedText extracted = connection.getExtractedText(new ExtractedTextRequest(), 0);
        if (extracted != null && extracted.text != null) {
            return extracted.text.toString();
        }
        CharSequence before = connection.getTextBeforeCursor(200000, 0);
        CharSequence selected = connection.getSelectedText(0);
        CharSequence after = connection.getTextAfterCursor(200000, 0);
        return String.valueOf(before == null ? "" : before)
                + String.valueOf(selected == null ? "" : selected)
                + String.valueOf(after == null ? "" : after);
    }

    private void cancelRecording() {
        if (!recording || processing) {
            return;
        }
        File audio = currentAudioFile;
        File pcm = currentPcmFile;
        if (offlineRecordingSession) {
            stopOfflineRecorderOnly();
        } else {
            stopCloudRecorderOnly();
        }
        deleteTempFile(audio);
        deleteTempFile(pcm);
        finishProcessingState("Recording canceled.");
    }

    private void toggleOfflineFallbackRecording(String selectedProvider) {
        if (recording) {
            stopOfflineRecordingAndTranscribe();
            return;
        }
        if (!hasAudioPermission()) {
            setStatus("Open settings and grant microphone permission.");
            openSettings();
            return;
        }
        String fallbackProvider = installedOfflineFallbackProvider();
        if (fallbackProvider == null) {
            setStatus("No connection. Connect once to download offline model.");
            return;
        }
        startOfflineRecordingNow("Offline fallback from " + Prefs.providerLabel(selectedProvider), fallbackProvider);
    }

    private void toggleCloudRecording() {
        if (recording) {
            stopCloudRecordingAndTranscribe();
            return;
        }
        if (!hasAudioPermission()) {
            setStatus("Open settings and grant microphone permission.");
            openSettings();
            return;
        }
        try {
            selectedPreset = Prefs.activePreset(this);
            selectedExpression = Prefs.expressionForPreset(this, selectedPreset);
            currentAudioFile = File.createTempFile("voiceflow-keyboard-", ".m4a", getCacheDir());
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
            offlineRecordingSession = false;
            setKeyboardLocked(true);
            showCaptureRecordingState();
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

    private void stopCloudRecordingAndTranscribe() {
        File audio = currentAudioFile;
        stopCloudRecorderOnly();
        if (audio == null || !audio.exists() || audio.length() == 0) {
            finishProcessingState("No audio captured.");
            return;
        }
        processing = true;
        setKeyboardLocked(true);
        showCaptureProcessingState();
        startStatusSpinner(creationCapture
                ? "Transcribing creation request"
                : instructionCapture ? "Transcribing instruction" : translationCapture ? "Transcribing for translation" : "Transcribing");
        String presetForThisRecording = selectedPreset;
        int expressionForThisRecording = selectedExpression;
        boolean creationForThisRecording = creationCapture;
        boolean instructionForThisRecording = instructionCapture;
        String sourceForThisInstruction = instructionSourceText;
        boolean translationForThisRecording = translationCapture;
        String targetForThisTranslation = translationTargetLanguage;
        executor.execute(() -> {
            try {
                String transcript = TranscriptionClient.transcribe(this, audio);
                processTranscribedCapture(
                        transcript,
                        presetForThisRecording,
                        expressionForThisRecording,
                        creationForThisRecording,
                        instructionForThisRecording,
                        sourceForThisInstruction,
                        translationForThisRecording,
                        targetForThisTranslation
                );
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
        return Prefs.enableTransform(this)
                && !Prefs.PRESET_RAW.equals(preset)
                && hasNetworkConnectivity();
    }

    private void toggleOfflineRecording(String provider) {
        if (recording) {
            stopOfflineRecordingAndTranscribe();
            return;
        }
        if (!hasAudioPermission()) {
            setStatus("Open settings and grant microphone permission.");
            openSettings();
            return;
        }
        if (!isOfflineModelReady(provider)) {
            prepareOfflineModel(provider);
            return;
        }
        startOfflineRecordingNow("Recording offline", provider);
    }

    private void prepareOfflineModel(String provider) {
        processing = true;
        setKeyboardLocked(true);
        micButton.setEnabled(false);
        setMicVisual(true, false);
        startStatusSpinner("Downloading " + Prefs.providerLabel(provider));
        executor.execute(() -> {
            try {
                ensureOfflineModel(provider);
                mainHandler.post(() -> finishProcessingState(Prefs.providerLabel(provider) + " ready. Tap mic to record."));
            } catch (Exception e) {
                mainHandler.post(() -> finishProcessingState("Offline setup failed: " + concise(e)));
            }
        });
    }

    private void startOfflineRecordingNow(String statusPrefix, String provider) {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            setStatus("Open settings and grant microphone permission.");
            return;
        }
        int minBuffer = AudioRecord.getMinBufferSize(
                offlineSampleRate(provider),
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
        );
        if (minBuffer <= 0) {
            setStatus("Offline recorder unavailable.");
            return;
        }
        int bufferSize = Math.max(minBuffer, offlineSampleRate(provider) * 2);
        selectedPreset = Prefs.activePreset(this);
        selectedExpression = Prefs.expressionForPreset(this, selectedPreset);
        try {
            currentPcmFile = File.createTempFile("voiceflow-keyboard-", ".pcm", getCacheDir());
            offlineRecorder = new AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    offlineSampleRate(provider),
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize
            );
            if (offlineRecorder.getState() != AudioRecord.STATE_INITIALIZED) {
                stopOfflineRecorderOnly();
                setStatus("Offline recorder failed to initialize.");
                return;
            }
            offlineRecordLoop = true;
            offlineRecorder.startRecording();
            File target = currentPcmFile;
            AudioRecord activeRecorder = offlineRecorder;
            offlineRecordThread = new Thread(
                    () -> writeOfflinePcm(activeRecorder, target, bufferSize),
                    "VoiceFlowOfflineRecorder"
            );
            offlineRecordThread.start();
            recording = true;
            offlineRecordingSession = true;
            offlineRecordingProvider = provider;
            setKeyboardLocked(true);
            showCaptureRecordingState();
        } catch (Exception e) {
            stopOfflineRecorderOnly();
            setStatus("Offline recording failed: " + concise(e));
        }
    }

    private void writeOfflinePcm(AudioRecord activeRecorder, File target, int bufferSize) {
        byte[] buffer = new byte[bufferSize];
        try (FileOutputStream out = new FileOutputStream(target)) {
            while (offlineRecordLoop) {
                int read = activeRecorder.read(buffer, 0, buffer.length);
                if (read > 0) {
                    out.write(buffer, 0, read);
                }
            }
        } catch (Exception e) {
            if (offlineRecordLoop) {
                postStatus("Offline recording interrupted.");
            }
        }
    }

    private void stopOfflineRecordingAndTranscribe() {
        File pcm = currentPcmFile;
        stopOfflineRecorderOnly();
        if (pcm == null || !pcm.exists() || pcm.length() == 0) {
            finishProcessingState("No audio captured.");
            return;
        }
        processing = true;
        setKeyboardLocked(true);
        showCaptureProcessingState();
        startStatusSpinner(creationCapture
                ? "Transcribing creation request"
                : instructionCapture ? "Transcribing instruction" : translationCapture ? "Transcribing for translation" : "Transcribing");
        String presetForThisRecording = selectedPreset;
        int expressionForThisRecording = selectedExpression;
        String providerForThisRecording = offlineRecordingProvider;
        boolean creationForThisRecording = creationCapture;
        boolean instructionForThisRecording = instructionCapture;
        String sourceForThisInstruction = instructionSourceText;
        boolean translationForThisRecording = translationCapture;
        String targetForThisTranslation = translationTargetLanguage;
        executor.execute(() -> {
            try {
                String transcript = transcribeOfflinePcm(providerForThisRecording, pcm);
                processTranscribedCapture(
                        transcript,
                        presetForThisRecording,
                        expressionForThisRecording,
                        creationForThisRecording,
                        instructionForThisRecording,
                        sourceForThisInstruction,
                        translationForThisRecording,
                        targetForThisTranslation
                );
            } catch (Exception e) {
                mainHandler.post(() -> finishProcessingState(concise(e)));
            } finally {
                if (!pcm.delete()) {
                    pcm.deleteOnExit();
                }
            }
        });
    }

    private void processTranscribedCapture(
            String transcript,
            String preset,
            int expression,
            boolean creation,
            boolean instruction,
            String instructionSource,
            boolean translation,
        String targetLanguage
    ) throws Exception {
        if (translation) {
            String statusLanguage = compactLanguageName(targetLanguage);
            postStatusSpinner("Translating to " + statusLanguage);
            String result = TransformClient.translate(this, transcript, targetLanguage, preset, expression);
            if (result == null || result.trim().isEmpty()) {
                throw new IllegalStateException("Translation returned no text.");
            }
            mainHandler.post(() -> {
                String historyId = Prefs.addTranscriptHistory(
                        this,
                        transcript,
                        result,
                        preset,
                        expression,
                        VoiceHistoryItem.OPERATION_TRANSLATION,
                        targetLanguage
                );
                String inserted = insertVoiceText(result);
                rememberLastVoiceInsertion(
                        transcript,
                        inserted,
                        preset,
                        expression,
                        VoiceHistoryItem.OPERATION_TRANSLATION,
                        targetLanguage,
                        historyId
                );
                finishProcessingState("Translated - " + labelForPreset(preset));
            });
            return;
        }
        if (creation) {
            postStatusSpinner("Creating text");
            String result = TransformClient.createText(this, transcript, preset, expression);
            if (result == null || result.trim().isEmpty()) {
                throw new IllegalStateException("Text creation returned no text.");
            }
            mainHandler.post(() -> {
                String historyId = Prefs.addTranscriptHistory(
                        this,
                        transcript,
                        result,
                        preset,
                        expression,
                        VoiceHistoryItem.OPERATION_CREATION,
                        ""
                );
                String inserted = appendCreatedText(result);
                if (inserted.isEmpty()) {
                    finishProcessingState("Could not append created text");
                    return;
                }
                rememberLastVoiceInsertion(
                        transcript,
                        inserted,
                        preset,
                        expression,
                        VoiceHistoryItem.OPERATION_CREATION,
                        "",
                        historyId
                );
                finishProcessingState("Created and appended");
            });
            return;
        }
        if (instruction) {
            postStatusSpinner("Applying instruction");
            String result = TransformClient.applyInstruction(this, instructionSource, transcript);
            mainHandler.post(() -> {
                if (replaceWholeFieldText(result)) {
                    finishProcessingState("Text updated");
                } else {
                    finishProcessingState("Could not replace this field");
                }
            });
            return;
        }

        String finalText = transcript;
        String finalStatus = "Inserted";
        if (shouldTransform(preset)) {
            postStatusSpinner("Transforming: " + labelForPreset(preset));
            try {
                finalText = TransformClient.transform(this, transcript, preset, expression);
            } catch (Exception transformError) {
                finalStatus = "Inserted raw";
            }
        }
        String result = finalText;
        String status = finalStatus;
        mainHandler.post(() -> {
            String historyId = Prefs.addTranscriptHistory(
                    this,
                    transcript,
                    result,
                    preset,
                    expression,
                    VoiceHistoryItem.OPERATION_DICTATION,
                    ""
            );
            String inserted = insertVoiceText(result);
            rememberLastVoiceInsertion(
                    transcript,
                    inserted,
                    preset,
                    expression,
                    VoiceHistoryItem.OPERATION_DICTATION,
                    "",
                    historyId
            );
            finishProcessingState(status);
        });
    }

    private void cyclePreset(int direction) {
        String[] presets = Prefs.selectablePresetValues(this);
        int index = Prefs.presetIndex(presets, selectedPreset);
        int next = (index + direction + presets.length) % presets.length;
        selectedPreset = presets[next];
        selectedExpression = Prefs.expressionForPreset(this, selectedPreset);
        Prefs.setActivePreset(this, selectedPreset);
        showVoiceStyleOverlay();
        setStatus(captureStyleStatus("Recording"));
    }

    private int presetIndex(String preset) {
        return Prefs.presetIndex(Prefs.selectablePresetValues(this), preset);
    }

    private String selectedPresetLabel() {
        return labelForPreset(selectedPreset);
    }

    private String labelForPreset(String preset) {
        return Prefs.labelForPreset(this, preset);
    }

    private void deleteTempFile(File file) {
        if (file != null && file.exists() && !file.delete()) {
            file.deleteOnExit();
        }
    }

    private void stopRecorderSilently() {
        stopCloudRecorderOnly();
        stopOfflineRecorderOnly();
        finishProcessingState("Ready");
    }

    private void stopCloudRecorderOnly() {
        if (recorder != null) {
            try {
                recorder.stop();
            } catch (RuntimeException ignored) {
            }
            recorder.release();
            recorder = null;
        }
        recording = false;
        offlineRecordingSession = false;
        offlineRecordingProvider = Prefs.PROVIDER_OFFLINE_VOSK;
        currentAudioFile = null;
    }

    private void stopOfflineRecorderOnly() {
        offlineRecordLoop = false;
        AudioRecord activeRecorder = offlineRecorder;
        offlineRecorder = null;
        if (activeRecorder != null) {
            try {
                activeRecorder.stop();
            } catch (Exception ignored) {
            }
            activeRecorder.release();
        }
        Thread thread = offlineRecordThread;
        offlineRecordThread = null;
        if (thread != null && thread != Thread.currentThread()) {
            try {
                thread.join(700);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        recording = false;
        offlineRecordingSession = false;
        offlineRecordingProvider = Prefs.PROVIDER_OFFLINE_VOSK;
        currentPcmFile = null;
    }

    private void finishProcessingState(String status) {
        stopStatusSpinner();
        recording = false;
        processing = false;
        retoneMode = false;
        translationCapture = false;
        translationTargetLanguage = "";
        creationCapture = false;
        instructionCapture = false;
        instructionSourceText = "";
        stopDeleteHold();
        hideVoiceStyleOverlay();
        setKeyboardLocked(false);
        setRetoneTopControls(false);
        if (translationButton != null) {
            setTranslationVisual(false, true);
        }
        if (createButton != null) {
            setCreateVisual(false, true);
        }
        if (instructionButton != null) {
            setInstructionVisual(false, true);
        }
        if (micButton != null) {
            micButton.setEnabled(true);
            setMicVisual(false, true);
        }
        if (chipStrip != null) {
            showIdleChips();
        }
        setStatus(status);
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

    private boolean isOfflineTranscriptionProvider(String provider) {
        return Prefs.PROVIDER_OFFLINE_VOSK.equals(provider)
                || Prefs.PROVIDER_OFFLINE_PARAKEET.equals(provider);
    }

    private String installedOfflineFallbackProvider() {
        if (OfflineParakeetClient.isModelReady(this)) {
            return Prefs.PROVIDER_OFFLINE_PARAKEET;
        }
        if (OfflineVoskClient.isModelReady(this)) {
            return Prefs.PROVIDER_OFFLINE_VOSK;
        }
        return null;
    }

    private boolean isOfflineModelReady(String provider) {
        if (Prefs.PROVIDER_OFFLINE_PARAKEET.equals(provider)) {
            return OfflineParakeetClient.isModelReady(this);
        }
        return OfflineVoskClient.isModelReady(this);
    }

    private void ensureOfflineModel(String provider) throws Exception {
        if (Prefs.PROVIDER_OFFLINE_PARAKEET.equals(provider)) {
            OfflineParakeetClient.ensureModel(this);
        } else {
            OfflineVoskClient.ensureModel(this);
        }
    }

    private String transcribeOfflinePcm(String provider, File pcm) throws Exception {
        if (Prefs.PROVIDER_OFFLINE_PARAKEET.equals(provider)) {
            return OfflineParakeetClient.transcribePcm(this, pcm);
        }
        return OfflineVoskClient.transcribePcm(this, pcm);
    }

    private int offlineSampleRate(String provider) {
        if (Prefs.PROVIDER_OFFLINE_PARAKEET.equals(provider)) {
            return OfflineParakeetClient.SAMPLE_RATE;
        }
        return OfflineVoskClient.SAMPLE_RATE;
    }

    private boolean hasNetworkConnectivity() {
        ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivityManager == null) {
            return false;
        }
        Network network = connectivityManager.getActiveNetwork();
        if (network == null) {
            return false;
        }
        NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(network);
        return capabilities != null
                && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
    }

    private void postStatus(String status) {
        mainHandler.post(() -> setStatus(status));
    }

    private void startStatusSpinner(String base) {
        hideChipStrip();
        if (translationCapture) {
            startTranslationLoading();
        } else if (creationCapture) {
            startCreateLoading();
        } else if (instructionCapture) {
            startInstructionLoading();
        } else {
            startMicLoading();
        }
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
        stopMicLoading();
        stopCreateLoading();
        stopInstructionLoading();
        stopTranslationLoading();
    }

    private void startMicLoading() {
        if (micButton == null) {
            return;
        }
        micButton.setVisibility(View.VISIBLE);
        micButton.setEnabled(false);
        micButton.setImageResource(R.drawable.ic_loading_24);
        micButton.setColorFilter(colors.text);
        micButton.setBackground(ovalBackground(colors.key, false));
        micButton.setAlpha(1f);
        if (micLoadingAnimator == null) {
            micLoadingAnimator = ObjectAnimator.ofFloat(micButton, View.ROTATION, 0f, 360f);
            micLoadingAnimator.setDuration(850);
            micLoadingAnimator.setInterpolator(new LinearInterpolator());
            micLoadingAnimator.setRepeatCount(ValueAnimator.INFINITE);
        }
        if (!micLoadingAnimator.isStarted()) {
            micLoadingAnimator.start();
        }
    }

    private void stopMicLoading() {
        if (micLoadingAnimator != null) {
            micLoadingAnimator.cancel();
        }
        if (micButton != null) {
            micButton.setRotation(0f);
            micButton.setImageResource(R.drawable.ic_mic_24);
        }
    }

    private void startInstructionLoading() {
        if (instructionButton == null) {
            return;
        }
        instructionButton.setVisibility(View.VISIBLE);
        instructionButton.setEnabled(false);
        instructionButton.setImageResource(R.drawable.ic_loading_24);
        instructionButton.setColorFilter(colors.text);
        instructionButton.setBackground(ovalBackground(colors.key, false));
        instructionButton.setAlpha(1f);
        if (instructionLoadingAnimator == null) {
            instructionLoadingAnimator = ObjectAnimator.ofFloat(instructionButton, View.ROTATION, 0f, 360f);
            instructionLoadingAnimator.setDuration(850);
            instructionLoadingAnimator.setInterpolator(new LinearInterpolator());
            instructionLoadingAnimator.setRepeatCount(ValueAnimator.INFINITE);
        }
        if (!instructionLoadingAnimator.isStarted()) {
            instructionLoadingAnimator.start();
        }
    }

    private void stopInstructionLoading() {
        if (instructionLoadingAnimator != null) {
            instructionLoadingAnimator.cancel();
        }
        if (instructionButton != null) {
            instructionButton.setRotation(0f);
            instructionButton.setImageResource(R.drawable.ic_wand_24);
        }
    }

    private void startCreateLoading() {
        if (createButton == null) {
            return;
        }
        createButton.setVisibility(View.VISIBLE);
        createButton.setEnabled(false);
        createButton.setImageResource(R.drawable.ic_loading_24);
        createButton.setColorFilter(colors.text);
        createButton.setBackground(ovalBackground(colors.key, false));
        createButton.setAlpha(1f);
        if (createLoadingAnimator == null) {
            createLoadingAnimator = ObjectAnimator.ofFloat(createButton, View.ROTATION, 0f, 360f);
            createLoadingAnimator.setDuration(850);
            createLoadingAnimator.setInterpolator(new LinearInterpolator());
            createLoadingAnimator.setRepeatCount(ValueAnimator.INFINITE);
        }
        if (!createLoadingAnimator.isStarted()) {
            createLoadingAnimator.start();
        }
    }

    private void stopCreateLoading() {
        if (createLoadingAnimator != null) {
            createLoadingAnimator.cancel();
        }
        if (createButton != null) {
            createButton.setRotation(0f);
            createButton.setImageResource(R.drawable.ic_create_text_24);
        }
    }

    private void startTranslationLoading() {
        if (translationButton == null) {
            return;
        }
        translationButton.setVisibility(View.VISIBLE);
        translationButton.setEnabled(false);
        translationButton.setImageResource(R.drawable.ic_loading_24);
        translationButton.setColorFilter(colors.text);
        translationButton.setBackground(ovalBackground(colors.key, false));
        translationButton.setAlpha(1f);
        if (translationLoadingAnimator == null) {
            translationLoadingAnimator = ObjectAnimator.ofFloat(translationButton, View.ROTATION, 0f, 360f);
            translationLoadingAnimator.setDuration(850);
            translationLoadingAnimator.setInterpolator(new LinearInterpolator());
            translationLoadingAnimator.setRepeatCount(ValueAnimator.INFINITE);
        }
        if (!translationLoadingAnimator.isStarted()) {
            translationLoadingAnimator.start();
        }
    }

    private void stopTranslationLoading() {
        if (translationLoadingAnimator != null) {
            translationLoadingAnimator.cancel();
        }
        if (translationButton != null) {
            translationButton.setRotation(0f);
            translationButton.setImageResource(R.drawable.ic_translate_24);
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
        if (!processing) {
            micButton.setImageResource(R.drawable.ic_mic_24);
        }
        micButton.setEnabled(enabled);
        micButton.setColorFilter(active ? colors.onDanger : colors.text);
        micButton.setBackground(ovalBackground(active ? colors.danger : colors.key, active));
        micButton.setAlpha(enabled ? 1f : 0.72f);
        updateRecordingControls();
    }

    private void setInstructionVisual(boolean active, boolean enabled) {
        if (instructionButton == null) {
            return;
        }
        if (!processing) {
            instructionButton.setImageResource(R.drawable.ic_wand_24);
        }
        instructionButton.setEnabled(enabled);
        instructionButton.setColorFilter(active ? colors.onDanger : colors.text);
        instructionButton.setBackground(ovalBackground(active ? colors.danger : colors.key, active));
        instructionButton.setAlpha(enabled ? 1f : 0.55f);
        updateRecordingControls();
    }

    private void setCreateVisual(boolean active, boolean enabled) {
        if (createButton == null) {
            return;
        }
        if (!processing) {
            createButton.setImageResource(R.drawable.ic_create_text_24);
        }
        createButton.setEnabled(enabled);
        createButton.setColorFilter(active ? colors.onDanger : colors.text);
        createButton.setBackground(ovalBackground(active ? colors.danger : colors.key, active));
        createButton.setAlpha(enabled ? 1f : 0.55f);
        updateRecordingControls();
    }

    private void setTranslationVisual(boolean active, boolean enabled) {
        if (translationButton == null) {
            return;
        }
        if (!processing) {
            translationButton.setImageResource(R.drawable.ic_translate_24);
        }
        translationButton.setEnabled(enabled);
        translationButton.setColorFilter(active ? colors.onDanger : colors.text);
        translationButton.setBackground(ovalBackground(active ? colors.danger : colors.key, active));
        translationButton.setAlpha(enabled ? 1f : 0.55f);
        updateRecordingControls();
    }

    private void showCaptureRecordingState() {
        if (translationCapture) {
            hideChipStrip();
            setMicVisual(false, false);
            setCreateVisual(false, false);
            setInstructionVisual(false, false);
            setTranslationVisual(true, true);
            showVoiceStyleOverlay();
            setStatus(captureStyleStatus("Recording"));
            return;
        }
        if (creationCapture) {
            hideChipStrip();
            setMicVisual(false, false);
            setTranslationVisual(false, false);
            setInstructionVisual(false, false);
            setCreateVisual(true, true);
            showVoiceStyleOverlay();
            setStatus(captureStyleStatus("Creating"));
            return;
        }
        if (instructionCapture) {
            hideChipStrip();
            hideVoiceStyleOverlay();
            setMicVisual(false, false);
            setTranslationVisual(false, false);
            setCreateVisual(false, false);
            setInstructionVisual(true, true);
            setStatus("Recording instruction");
            return;
        }
        setTranslationVisual(false, false);
        setCreateVisual(false, false);
        setInstructionVisual(false, false);
        setMicVisual(true, true);
        hideChipStrip();
        showVoiceStyleOverlay();
        setStatus(captureStyleStatus("Recording"));
    }

    private void showCaptureProcessingState() {
        hideVoiceStyleOverlay();
        if (translationCapture) {
            setMicVisual(false, false);
            setCreateVisual(false, false);
            setInstructionVisual(false, false);
            setTranslationVisual(true, false);
            return;
        }
        if (creationCapture) {
            setMicVisual(false, false);
            setTranslationVisual(false, false);
            setInstructionVisual(false, false);
            setCreateVisual(true, false);
            return;
        }
        if (instructionCapture) {
            setMicVisual(false, false);
            setTranslationVisual(false, false);
            setCreateVisual(false, false);
            setInstructionVisual(true, false);
            return;
        }
        setTranslationVisual(false, false);
        setCreateVisual(false, false);
        setInstructionVisual(false, false);
        setMicVisual(true, false);
    }

    private void updateRecordingControls() {
        if (cancelRecordingButton == null) {
            return;
        }
        boolean canCancel = !historyMode && (recording || retoneMode) && !processing;
        cancelRecordingButton.setVisibility(canCancel ? View.VISIBLE : View.INVISIBLE);
        cancelRecordingButton.setEnabled(canCancel);
    }

    private void haptic(View view) {
        if (view != null) {
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
        }
    }

    private String captureStyleStatus(String prefix) {
        return prefix + ": " + selectedPresetLabel() + " - " + Prefs.expressionLabel(selectedExpression);
    }

    private String compactLanguageName(String language) {
        int qualifierStart = language.indexOf(" (");
        return qualifierStart > 0 ? language.substring(0, qualifierStart) : language;
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

        static Palette from(VoiceFlowKeyboardService service) {
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

    private static String[] commonCompletions() {
        return new String[]{
                "there", "their", "they", "then", "these", "them", "themselves",
                "that", "than", "thank", "thanks", "thing", "think", "thinking",
                "this", "those", "through", "though", "thought", "thoughts",
                "with", "without", "within", "would", "work", "working", "works",
                "what", "when", "where", "which", "while", "will", "well", "were",
                "about", "actually", "after", "again", "against", "already", "also",
                "because", "before", "being", "between", "business",
                "can", "can't", "could", "couldn't", "current", "currently",
                "definitely", "different", "does", "doesn't", "doing",
                "email", "everything", "example", "actually",
                "from", "first", "format", "function",
                "going", "great", "grok",
                "have", "haven't", "help", "here", "how",
                "important", "instead", "into", "issue",
                "keyboard", "kind",
                "like", "little", "local",
                "maybe", "model", "more", "much",
                "need", "new", "next", "not", "now",
                "openai", "option", "other",
                "please", "probably", "prompt", "professional",
                "really", "recording", "replace", "review", "right",
                "settings", "should", "something", "still", "sure",
                "text", "transcript", "transcription", "transform", "typing",
                "using", "usually",
                "voice", "voiceflow",
                "want", "was", "way", "we", "what", "when", "where", "which", "who",
                "yeah", "you", "your"
        };
    }

    private final class SwipeRootLayout extends LinearLayout {
        SwipeRootLayout(Context context) {
            super(context);
        }

        @Override
        public boolean dispatchTouchEvent(MotionEvent event) {
            boolean wasTracking = rootSwipeTracking;
            if (handleRootSwipe(event)) {
                if (!wasTracking && rootSwipeTracking) {
                    MotionEvent cancel = MotionEvent.obtain(event);
                    cancel.setAction(MotionEvent.ACTION_CANCEL);
                    super.dispatchTouchEvent(cancel);
                    cancel.recycle();
                }
                return true;
            }
            return super.dispatchTouchEvent(event);
        }
    }
}
