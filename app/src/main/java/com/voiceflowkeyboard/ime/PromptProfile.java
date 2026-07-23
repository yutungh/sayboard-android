package com.voiceflowkeyboard.ime;

final class PromptProfile {
    final String id;
    final String name;
    final String icon;

    PromptProfile(String id, String name, String icon) {
        this.id = id;
        this.name = name;
        this.icon = icon == null ? "" : icon;
    }

    String displayName() {
        return icon.isEmpty() ? name : icon + " " + name;
    }
}
