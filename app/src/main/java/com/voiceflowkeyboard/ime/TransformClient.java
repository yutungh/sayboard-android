package com.voiceflowkeyboard.ime;

import android.content.Context;

final class TransformClient {
    private TransformClient() {
    }

    static String transform(Context context, String transcript, String preset) throws Exception {
        String provider = Prefs.transformProvider(context);
        if (Prefs.PROVIDER_ANTHROPIC.equals(provider)) {
            return AnthropicClient.transform(context, transcript, preset);
        }
        if (Prefs.PROVIDER_XAI.equals(provider)) {
            return XAiClient.transform(context, transcript, preset);
        }
        return OpenAiClient.transform(context, transcript, preset);
    }
}
