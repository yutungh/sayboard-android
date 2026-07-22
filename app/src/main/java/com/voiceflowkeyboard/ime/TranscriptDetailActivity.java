package com.voiceflowkeyboard.ime;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.view.Gravity;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class TranscriptDetailActivity extends Activity {
    static final String EXTRA_HISTORY_ID = "history_id";

    private VoiceHistoryItem historyItem;
    private String primaryVariant = Prefs.PRESET_RAW;
    private String comparisonVariant = Prefs.PRESET_RAW;
    private boolean comparing;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Ui.applyWindow(this);
        loadHistoryItem();
        if (historyItem == null) {
            Toast.makeText(this, "Transcript is no longer available", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        primaryVariant = historyItem.hasOutputForVariant(historyItem.preset, historyItem.expression)
                ? historyItem.selectedVariantKey()
                : Prefs.PRESET_RAW;
        comparisonVariant = comparisonVariantFor(primaryVariant);
        comparing = !primaryVariant.equals(comparisonVariant) && availableVariants().size() > 1;
        render();
    }

    private void loadHistoryItem() {
        String id = getIntent().getStringExtra(EXTRA_HISTORY_ID);
        for (VoiceHistoryItem item : Prefs.transcriptHistory(this)) {
            if (item.id.equals(id)) {
                historyItem = item;
                return;
            }
        }
    }

    private void render() {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(Ui.BACKGROUND);
        Ui.applySystemBarPadding(page, 0, 0, 0, 0);

        page.addView(topBar());

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(18), dp(12), dp(18), dp(30));
        scroll.addView(content);
        page.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
        ));

        content.addView(metadata());
        content.addView(compareControl());
        content.addView(sectionLabel(historyItem.isTranslation() ? "Translation style" : "Version"));
        content.addView(versionSelector(primaryVariant, variant -> {
            primaryVariant = variant;
            if (primaryVariant.equals(comparisonVariant)) {
                comparisonVariant = comparisonVariantFor(primaryVariant);
            }
            render();
        }));

        if (comparing) {
            content.addView(sectionLabel("Compare with"));
            content.addView(versionSelector(comparisonVariant, variant -> {
                comparisonVariant = variant;
                if (comparisonVariant.equals(primaryVariant)) {
                    primaryVariant = comparisonVariantFor(comparisonVariant);
                }
                render();
            }));
        }

        LinearLayout panels = new LinearLayout(this);
        boolean wide = getResources().getConfiguration().screenWidthDp >= 700;
        panels.setOrientation(wide && comparing ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
        LinearLayout.LayoutParams panelsParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        panelsParams.setMargins(0, dp(14), 0, 0);
        content.addView(panels, panelsParams);

        addTextPanel(panels, primaryVariant, wide && comparing);
        if (comparing) {
            addTextPanel(panels, comparisonVariant, wide);
        }

        setContentView(page);
    }

    private View topBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(10), dp(8), dp(18), dp(8));
        bar.setBackgroundColor(Ui.BACKGROUND);

        ImageButton back = new ImageButton(this);
        back.setImageResource(R.drawable.ic_arrow_back_24);
        back.setColorFilter(Ui.TEXT);
        back.setBackgroundColor(Color.TRANSPARENT);
        back.setPadding(dp(10), dp(10), dp(10), dp(10));
        back.setContentDescription("Back");
        back.setOnClickListener(v -> finish());
        bar.addView(back, new LinearLayout.LayoutParams(dp(48), dp(48)));

        TextView title = Ui.text(this, historyItem.isTranslation() ? "Translation" : "Transcript", 22, true, Ui.TEXT);
        title.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                0,
                dp(48),
                1f
        );
        titleParams.setMargins(dp(4), 0, 0, 0);
        bar.addView(title, titleParams);
        return bar;
    }

    private View metadata() {
        LinearLayout block = new LinearLayout(this);
        block.setOrientation(LinearLayout.VERTICAL);

        String date = historyItem.timestampMs > 0
                ? DateFormat.format("MMM d, yyyy 'at' h:mm a", historyItem.timestampMs).toString()
                : "Recent transcript";
        block.addView(Ui.text(this, date, 14, true, Ui.TEXT));

        if (historyItem.isTranslation() && !historyItem.targetLanguage.isEmpty()) {
            TextView language = Ui.text(this, "Translated to " + historyItem.targetLanguage, 13, true, Ui.ACCENT);
            language.setPadding(0, dp(5), 0, 0);
            block.addView(language);
        }

        String raw = historyItem.rawText.trim();
        String detail = wordCount(raw) + " words - " + raw.length() + " characters";
        TextView stats = Ui.text(this, detail, 13, false, Ui.MUTED);
        stats.setPadding(0, dp(5), 0, dp(4));
        block.addView(stats);
        return block;
    }

    private View compareControl() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14), dp(8), dp(10), dp(8));
        row.setBackground(Ui.roundedStroke(this, Ui.SURFACE, 8, Ui.DIVIDER));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, dp(14), 0, dp(2));
        row.setLayoutParams(params);

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.addView(Ui.text(this, historyItem.isTranslation() ? "Compare translation styles" : "Compare versions", 15, true, Ui.TEXT));
        TextView detail = Ui.text(this, availableVariants().size() > 1
                ? "View two saved versions together"
                : "Only the original is available", 12, false, Ui.MUTED);
        detail.setPadding(0, dp(3), 0, 0);
        labels.addView(detail);
        row.addView(labels, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        Switch toggle = new Switch(this);
        toggle.setChecked(comparing);
        toggle.setEnabled(availableVariants().size() > 1);
        toggle.setOnCheckedChangeListener((button, checked) -> {
            comparing = checked;
            if (checked && primaryVariant.equals(comparisonVariant)) {
                comparisonVariant = comparisonVariantFor(primaryVariant);
            }
            render();
        });
        row.addView(toggle);
        row.setOnClickListener(v -> {
            if (toggle.isEnabled()) {
                toggle.setChecked(!toggle.isChecked());
            }
        });
        return row;
    }

    private TextView sectionLabel(String value) {
        TextView label = Ui.text(this, value.toUpperCase(Locale.US), 12, true, Ui.MUTED);
        label.setLetterSpacing(0.04f);
        label.setPadding(0, dp(18), 0, dp(7));
        return label;
    }

    private View versionSelector(String selectedVariant, VariantListener listener) {
        HorizontalScrollView scroller = new HorizontalScrollView(this);
        scroller.setHorizontalScrollBarEnabled(false);
        LinearLayout options = new LinearLayout(this);
        options.setOrientation(LinearLayout.HORIZONTAL);
        scroller.addView(options);

        for (String variant : availableVariants()) {
            boolean selected = variant.equals(selectedVariant);
            TextView chip = Ui.text(this, labelForVariant(variant), 14, true, selected ? Color.WHITE : Ui.TEXT);
            chip.setGravity(Gravity.CENTER);
            chip.setSingleLine(true);
            chip.setEllipsize(TextUtils.TruncateAt.END);
            chip.setPadding(dp(14), 0, dp(14), 0);
            chip.setBackground(Ui.roundedStroke(this, selected ? Ui.ACCENT : Ui.SURFACE, 8, selected ? Ui.ACCENT : Ui.DIVIDER));
            chip.setOnClickListener(v -> listener.select(variant));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    dp(40)
            );
            params.setMargins(0, 0, dp(8), 0);
            options.addView(chip, params);
        }
        return scroller;
    }

    private void addTextPanel(LinearLayout parent, String variant, boolean weighted) {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(16), dp(14), dp(16), dp(18));
        panel.setBackground(Ui.roundedStroke(this, Ui.SURFACE, 8, Ui.DIVIDER));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(Ui.text(this, labelForVariant(variant), 15, true, Ui.TEXT), new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
        ));
        TextView copy = Ui.text(this, "Copy", 13, true, Ui.ACCENT);
        copy.setGravity(Gravity.CENTER);
        copy.setPadding(dp(12), 0, dp(12), 0);
        copy.setMinHeight(dp(36));
        copy.setBackground(Ui.rounded(this, Ui.ACCENT_SOFT, 8));
        copy.setOnClickListener(v -> copyText(historyItem.outputForVariantKey(variant)));
        header.addView(copy);
        panel.addView(header);

        TextView body = Ui.text(this, historyItem.outputForVariantKey(variant), 16, false, Ui.TEXT);
        body.setTextIsSelectable(true);
        body.setLineSpacing(0f, 1.16f);
        body.setPadding(0, dp(14), 0, 0);
        panel.addView(body);

        LinearLayout.LayoutParams params = weighted
                ? new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                : new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, weighted ? dp(10) : 0, dp(12));
        parent.addView(panel, params);
    }

    private List<String> availableVariants() {
        List<String> variants = new ArrayList<>();
        if (!historyItem.rawText.trim().isEmpty()) {
            variants.add(Prefs.PRESET_RAW);
        }
        for (String preset : Prefs.selectablePresetValues(this)) {
            String variant = Prefs.historyVariantKey(preset, Prefs.expressionForPreset(this, preset));
            if (historyItem.hasOutputForVariantKey(variant) && !variants.contains(variant)) {
                variants.add(variant);
            }
        }
        for (String variant : historyItem.outputs.keySet()) {
            if (historyItem.hasOutputForVariantKey(variant) && !variants.contains(variant)) {
                variants.add(variant);
            }
        }
        return variants;
    }

    private String comparisonVariantFor(String variant) {
        List<String> available = availableVariants();
        if (!Prefs.PRESET_RAW.equals(variant) && available.contains(Prefs.PRESET_RAW)) {
            return Prefs.PRESET_RAW;
        }
        for (String candidate : available) {
            if (!candidate.equals(variant)) {
                return candidate;
            }
        }
        return variant;
    }

    private String labelForVariant(String variant) {
        if (Prefs.PRESET_RAW.equals(variant)) {
            return historyItem.isTranslation() ? "Original source" : "Original";
        }
        String preset = Prefs.historyVariantPreset(variant);
        int expression = Prefs.historyVariantExpression(variant);
        return Prefs.PRESET_RAW.equals(preset)
                ? (historyItem.isTranslation() ? "Original source" : "Original")
                : Prefs.labelForPreset(this, preset) + " - " + Prefs.expressionLabel(expression);
    }

    private void copyText(String text) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (clipboard == null || text == null || text.trim().isEmpty()) {
            Toast.makeText(this, "Nothing to copy", Toast.LENGTH_SHORT).show();
            return;
        }
        clipboard.setPrimaryClip(ClipData.newPlainText("VoiceFlow transcript", text.trim()));
        Toast.makeText(this, "Copied", Toast.LENGTH_SHORT).show();
    }

    private int wordCount(String text) {
        String trimmed = text == null ? "" : text.trim();
        return trimmed.isEmpty() ? 0 : trimmed.split("\\s+").length;
    }

    private int dp(int value) {
        return Ui.dp(this, value);
    }

    private interface VariantListener {
        void select(String variant);
    }
}
