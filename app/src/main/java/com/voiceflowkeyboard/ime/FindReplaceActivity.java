package com.voiceflowkeyboard.ime;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
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
        setTitle("Find and replace");
        replacements.addAll(Prefs.userPhraseReplacements(this));
        setContentView(buildContent());
    }

    private View buildContent() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(18), dp(20), dp(24));
        root.setBackgroundColor(0xfff8f9fa);
        scroll.addView(root);

        root.addView(text("Find and replace", 24, true));
        root.addView(text("Use this for names, nicknames, jargon, and phrases your speech model often hears wrong.", 14, false));

        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        root.addView(list);
        renderList();

        Button add = button("New replacement");
        add.setOnClickListener(v -> showEditor(-1));
        root.addView(add);

        Button save = button("Save replacements");
        save.setOnClickListener(v -> save());
        root.addView(save);

        Button back = button("Back to settings");
        back.setOnClickListener(v -> finish());
        root.addView(back);
        return scroll;
    }

    private void renderList() {
        list.removeAllViews();
        if (replacements.isEmpty()) {
            TextView empty = text("No personal replacements yet.", 14, false);
            empty.setPadding(0, dp(18), 0, 0);
            list.addView(empty);
            return;
        }
        for (int i = 0; i < replacements.size(); i++) {
            PhraseReplacement replacement = replacements.get(i);
            Button row = button(replacement.from + " -> " + replacement.to);
            final int index = i;
            row.setOnClickListener(v -> showEditor(index));
            list.addView(row);
        }
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
        TextView label = text(value, 13, true);
        label.setPadding(0, dp(14), 0, dp(6));
        return label;
    }

    private EditText input(String hint) {
        EditText input = new EditText(this);
        input.setHint(hint);
        input.setTextColor(0xff1f2328);
        input.setHintTextColor(0xff5f6368);
        input.setSingleLine(true);
        input.setPadding(dp(12), dp(8), dp(12), dp(8));
        input.setBackgroundColor(0xffffffff);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        return input;
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
        text.setTextColor(0xff1f2328);
        if (bold) {
            text.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        }
        return text;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
