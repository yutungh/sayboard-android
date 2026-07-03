package com.voiceflowkeyboard.ime;

import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class ProviderPickerActivity extends Activity {
    static final String EXTRA_MODE = "mode";
    static final String MODE_TRANSCRIPTION = "transcription";
    static final String MODE_TRANSFORM = "transform";

    private boolean transcription;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Ui.applyWindow(this);
        transcription = MODE_TRANSCRIPTION.equals(getIntent().getStringExtra(EXTRA_MODE));
        setTitle(transcription ? "Voice provider" : "Transform provider");
        setContentView(buildContent());
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

        TextView note = Ui.text(this, transcription
                ? "Choose who turns recorded audio into raw text."
                : "Choose who cleans and formats the transcript.", 14, false, Ui.MUTED);
        note.setPadding(0, 0, 0, dp(8));
        root.addView(note);

        LinearLayout providers = section(root, transcription ? "Voice input providers" : "Transform providers");
        addProviderRows(providers);
        return screen;
    }

    private void addProviderRows(LinearLayout providers) {
        String[] providerIds = {
                Prefs.PROVIDER_OPENAI,
                Prefs.PROVIDER_XAI,
                Prefs.PROVIDER_ANTHROPIC,
                Prefs.PROVIDER_DEEPGRAM,
                Prefs.PROVIDER_OFFLINE_VOSK
        };
        for (int i = 0; i < providerIds.length; i++) {
            String provider = providerIds[i];
            providers.addView(providerRow(provider));
            if (i < providerIds.length - 1) {
                providers.addView(divider());
            }
        }
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

        TextView title = Ui.text(this, transcription ? "Voice provider" : "Transform provider", 18, true, Ui.TEXT);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        titleParams.setMargins(dp(12), 0, 0, 0);
        bar.addView(title, titleParams);
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
        root.addView(section);
        return section;
    }

    private View providerRow(String provider) {
        boolean enabled = transcription ? Prefs.supportsTranscription(provider) : Prefs.supportsTransform(provider);
        String current = transcription ? Prefs.transcriptionProvider(this) : Prefs.transformProvider(this);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), dp(14), dp(14), dp(14));
        row.setAlpha(enabled ? 1f : 0.56f);
        row.setClickable(true);
        row.setOnClickListener(v -> {
            if (!enabled) {
                Toast.makeText(this, capability(provider), Toast.LENGTH_SHORT).show();
                return;
            }
            if (transcription) {
                Prefs.setTranscriptionProvider(this, provider);
            } else {
                Prefs.setTransformProvider(this, provider);
            }
            Toast.makeText(this, Prefs.providerLabel(provider) + " selected", Toast.LENGTH_SHORT).show();
            finish();
        });

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.addView(Ui.text(this, Prefs.providerLabel(provider), 16, true, Ui.TEXT));
        TextView sub = Ui.text(this, capability(provider), 13, false, Ui.MUTED);
        sub.setPadding(0, dp(5), 0, 0);
        labels.addView(sub);
        row.addView(labels, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        String accessory = provider.equals(current) ? "Selected" : ">";
        row.addView(Ui.text(this, accessory, "Selected".equals(accessory) ? 13 : 20, true, "Selected".equals(accessory) ? Ui.ACCENT : Ui.MUTED));
        return row;
    }

    private String capability(String provider) {
        if (Prefs.PROVIDER_ANTHROPIC.equals(provider)) {
            return "Transform only";
        }
        if (Prefs.PROVIDER_DEEPGRAM.equals(provider)) {
            return "Transcription only";
        }
        if (Prefs.PROVIDER_OFFLINE_VOSK.equals(provider)) {
            return OfflineVoskClient.isModelReady(this)
                    ? "Transcription only, offline model installed"
                    : "Transcription only, downloads a local model";
        }
        return "Transcription and transform";
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

    private int dp(int value) {
        return Ui.dp(this, value);
    }
}
