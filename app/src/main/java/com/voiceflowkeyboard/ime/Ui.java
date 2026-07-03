package com.voiceflowkeyboard.ime;

import android.os.Build;
import android.view.View;

final class Ui {
    static final int BACKGROUND = 0xfff5f6f8;
    static final int SURFACE = 0xffffffff;
    static final int TEXT = 0xff1f2328;
    static final int MUTED = 0xff667085;
    static final int DIVIDER = 0xffe5e7eb;
    static final int ACCENT = 0xff0f766e;

    private Ui() {
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
}
