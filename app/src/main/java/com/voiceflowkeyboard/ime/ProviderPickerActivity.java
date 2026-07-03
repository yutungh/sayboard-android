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
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Ui.applyWindow(this);
        setTitle("Voice provider");
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

        TextView note = Ui.text(this, "Choose who handles speech-to-text. Models are selected separately.", 14, false, Ui.MUTED);
        note.setPadding(0, 0, 0, dp(8));
        root.addView(note);

        LinearLayout available = section(root, "Available");
        available.addView(providerRow("OpenAI", "Cloud transcription using the selected OpenAI model", Prefs.PROVIDER_OPENAI, true));
        available.addView(divider());
        available.addView(providerRow("Android device speech", "Uses the phone's built-in speech recognizer", Prefs.PROVIDER_ANDROID, true));

        LinearLayout planned = section(root, "Planned providers");
        planned.addView(providerRow("Grok / xAI", "xAI has a speech-to-text API; adapter not wired yet", Prefs.PROVIDER_XAI, false));
        planned.addView(divider());
        planned.addView(providerRow("Deepgram", "Strong dedicated STT option; adapter not wired yet", "deepgram", false));
        planned.addView(divider());
        planned.addView(providerRow("Local offline", "Best privacy path; model packaging still needed", "local", false));

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

        TextView title = Ui.text(this, "Voice provider", 18, true, Ui.TEXT);
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

    private View providerRow(String title, String subtitle, String provider, boolean enabled) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), dp(14), dp(14), dp(14));
        row.setAlpha(enabled ? 1f : 0.62f);
        row.setClickable(true);
        row.setOnClickListener(v -> {
            if (!enabled) {
                Toast.makeText(this, title + " is planned but not wired yet.", Toast.LENGTH_SHORT).show();
                return;
            }
            Prefs.setTranscriptionProvider(this, provider);
            Toast.makeText(this, title + " selected", Toast.LENGTH_SHORT).show();
            finish();
        });

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.addView(Ui.text(this, title, 16, true, Ui.TEXT));
        TextView sub = Ui.text(this, subtitle, 13, false, Ui.MUTED);
        sub.setPadding(0, dp(5), 0, 0);
        labels.addView(sub);
        row.addView(labels, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        String current = Prefs.transcriptionProvider(this);
        row.addView(Ui.text(this, provider.equals(current) ? "Selected" : ">", provider.equals(current) ? 13 : 20, true, provider.equals(current) ? Ui.ACCENT : Ui.MUTED));
        return row;
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
