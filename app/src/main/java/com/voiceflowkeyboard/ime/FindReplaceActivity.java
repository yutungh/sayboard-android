package com.voiceflowkeyboard.ime;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

public class FindReplaceActivity extends Activity {
    private final List<PhraseReplacement> replacements = new ArrayList<>();
    private LinearLayout list;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Ui.applyWindow(this);
        setTitle("Find and replace");
        replacements.addAll(Prefs.userPhraseReplacements(this));
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

        TextView note = Ui.text(this, "Use this for names, nicknames, jargon, and phrases your speech model often hears wrong.", 14, false, Ui.MUTED);
        note.setPadding(0, 0, 0, dp(8));
        root.addView(note);

        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        root.addView(list);
        renderList();
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

        TextView title = Ui.text(this, "Find and replace", 18, true, Ui.TEXT);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        titleParams.setMargins(dp(12), 0, dp(12), 0);
        bar.addView(title, titleParams);

        TextView save = Ui.topAction(this, "Save", true);
        save.setOnClickListener(v -> save());
        bar.addView(save);
        return bar;
    }

    private void renderList() {
        list.removeAllViews();
        LinearLayout section = new LinearLayout(this);
        section.setOrientation(LinearLayout.VERTICAL);
        section.setBackground(Ui.roundedStroke(this, Ui.SURFACE, 18, Ui.DIVIDER));
        list.addView(section);

        if (replacements.isEmpty()) {
            TextView empty = Ui.text(this, "No personal replacements yet.", 15, false, Ui.MUTED);
            empty.setPadding(dp(16), dp(18), dp(16), dp(18));
            section.addView(empty);
            section.addView(divider());
        } else {
            for (int i = 0; i < replacements.size(); i++) {
                PhraseReplacement replacement = replacements.get(i);
                final int index = i;
                section.addView(row(replacement.from, replacement.to, v -> showEditor(index)));
                section.addView(divider());
            }
        }
        section.addView(row("+ New replacement", "Add a correction", v -> showEditor(-1)));
    }

    private View row(String title, String value, View.OnClickListener listener) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), dp(13), dp(12), dp(13));
        row.setClickable(true);
        row.setOnClickListener(listener);

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.addView(Ui.text(this, title, 16, true, Ui.TEXT));
        TextView subtitle = Ui.text(this, value, 13, false, Ui.MUTED);
        subtitle.setPadding(0, dp(5), 0, 0);
        labels.addView(subtitle);
        row.addView(labels, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(Ui.text(this, ">", 20, true, Ui.MUTED));
        return row;
    }

    private void showEditor(int index) {
        LinearLayout fields = new LinearLayout(this);
        fields.setOrientation(LinearLayout.VERTICAL);
        fields.setPadding(dp(20), dp(8), dp(20), 0);

        EditText from = input("What VoiceFlow hears");
        EditText to = input("What you want instead");
        if (index >= 0) {
            from.setText(replacements.get(index).from);
            to.setText(replacements.get(index).to);
        }
        fields.addView(label("Find"));
        fields.addView(from);
        fields.addView(label("Replace with"));
        fields.addView(to);

        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle(index >= 0 ? "Edit replacement" : "New replacement")
                .setView(fields)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", (dialog, which) -> {
                    PhraseReplacement replacement = new PhraseReplacement(from.getText().toString(), to.getText().toString());
                    if (replacement.from.trim().isEmpty() || replacement.to.trim().isEmpty()) {
                        return;
                    }
                    if (index >= 0) {
                        replacements.set(index, replacement);
                    } else {
                        replacements.add(replacement);
                    }
                    renderList();
                });
        if (index >= 0) {
            builder.setNeutralButton("Delete", (dialog, which) -> {
                replacements.remove(index);
                renderList();
            });
        }
        builder.show();
    }

    private void save() {
        Prefs.saveUserPhraseReplacements(this, replacements);
        Toast.makeText(this, "Replacements saved", Toast.LENGTH_SHORT).show();
        finish();
    }

    private TextView label(String value) {
        TextView label = Ui.text(this, value, 13, true, Ui.MUTED);
        label.setPadding(0, dp(14), 0, dp(6));
        return label;
    }

    private EditText input(String hint) {
        EditText input = new EditText(this);
        input.setHint(hint);
        input.setTextColor(Ui.TEXT);
        input.setHintTextColor(Ui.MUTED);
        input.setSingleLine(true);
        input.setPadding(dp(12), dp(8), dp(12), dp(8));
        input.setBackground(Ui.roundedStroke(this, Ui.SURFACE, 12, Ui.DIVIDER));
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        return input;
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
