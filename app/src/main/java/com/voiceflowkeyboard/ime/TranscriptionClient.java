package com.voiceflowkeyboard.ime;

import android.content.Context;

import java.io.File;

final class TranscriptionClient {
    private TranscriptionClient() {
    }

    static String transcribe(Context context, File audioFile) throws Exception {
        String provider = Prefs.transcriptionProvider(context);
        if (Prefs.PROVIDER_XAI.equals(provider)) {
            return XAiClient.transcribe(context, audioFile);
        }
        if (Prefs.PROVIDER_DEEPGRAM.equals(provider)) {
            return DeepgramClient.transcribe(context, audioFile);
        }
        return OpenAiClient.transcribe(context, audioFile);
    }
}
