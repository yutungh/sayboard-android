package com.voiceflowkeyboard.ime;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.TextView;

final class Ui {
    static final int BACKGROUND = 0xfff4f6f8;
    static final int SURFACE = 0xffffffff;
    static final int SURFACE_ALT = 0xffeef2f6;
    static final int TEXT = 0xff1f2328;
    static final int MUTED = 0xff667085;
    static final int DIVIDER = 0xffe5e7eb;
    static final int ACCENT = 0xff0f766e;
    static final int ACCENT_SOFT = 0xffd8f3ee;
    static final int DANGER = 0xffb42318;
    static final int DANGER_SOFT = 0xffffe4e0;

    private Ui() {
    }

    static void applyWindow(Activity activity) {
        Window window = activity.getWindow();
        window.setStatusBarColor(BACKGROUND);
        window.setNavigationBarColor(BACKGROUND);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        }
    }

    static void applySystemBarPadding(View view, int left, int top, int right, int bottom) {
        view.setPadding(left, top, right, bottom);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
            view.setOnApplyWindowInsetsListener((v, insets) -> {
                v.setPadding(
                        left + insets.getSystemWindowInsetLeft(),
                        top + insets.getSystemWindowInsetTop(),
                        right + insets.getSystemWindowInsetRight(),
                        bottom + insets.getSystemWindowInsetBottom()
                );
                return insets;
            });
            view.requestApplyInsets();
        }
    }

    static GradientDrawable rounded(Context context, int color, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(context, radiusDp));
        return drawable;
    }

    static GradientDrawable roundedStroke(Context context, int color, int radiusDp, int strokeColor) {
        GradientDrawable drawable = rounded(context, color, radiusDp);
        drawable.setStroke(Math.max(1, dp(context, 1)), strokeColor);
        return drawable;
    }

    static TextView text(Context context, String value, int sp, boolean bold, int color) {
        TextView text = new TextView(context);
        text.setText(value);
        text.setTextSize(sp);
        text.setTextColor(color);
        text.setIncludeFontPadding(false);
        if (bold) {
            text.setTypeface(Typeface.DEFAULT_BOLD);
        }
        return text;
    }

    static TextView topAction(Context context, String text, boolean primary) {
        TextView action = text(context, text, 15, true, primary ? Color.WHITE : TEXT);
        action.setGravity(Gravity.CENTER);
        action.setMinHeight(dp(context, 40));
        action.setPadding(dp(context, primary ? 16 : 10), 0, dp(context, primary ? 16 : 10), 0);
        action.setBackground(rounded(context, primary ? ACCENT : SURFACE_ALT, 20));
        action.setClickable(true);
        return action;
    }

    static View spacer(Context context, int heightDp) {
        View view = new View(context);
        view.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(context, heightDp)
        ));
        return view;
    }

    static int dp(Context context, int value) {
        return (int) (value * context.getResources().getDisplayMetrics().density + 0.5f);
    }
}
