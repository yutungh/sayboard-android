# VoiceFlow Keyboard

Open-source Android voice keyboard for people who want a Typeless-style mobile dictation workflow.

Record a voice note from the keyboard, transcribe it, optionally clean it with an LLM prompt, and insert the final text into any app. This is built as a native Android IME, so it works anywhere a normal keyboard works.

> Not affiliated with Typeless, Voiceflow, Apple, Google, Anthropic, or OpenAI. "Typeless-style" is used only to describe the product category: voice-first mobile typing with AI cleanup.

![VoiceFlow Keyboard letters layout](docs/keyboard-letters.png)

## Try It

Download the latest prototype APK from the [GitHub Releases page](https://github.com/yutungh/voiceflow-keyboard-android/releases/latest).

This is a debug-signed prototype build for sideloading and testing. For production use, build and sign your own release APK.

## Why This Exists

Most mobile voice typing tools either insert raw dictation immediately or live inside one app. This project is a base for an Android keyboard that can:

- capture a full recording instead of live-inserting partial text,
- transcribe the recording,
- rewrite or lightly clean the transcript with a configurable prompt,
- insert the final result into the active text field,
- let users bring their own API key and model choices.

Good search terms for this project: Android voice keyboard, VoiceFlow Keyboard, Typeless alternative, AI dictation keyboard, OpenAI transcription keyboard, voice-to-text IME, prompt-based dictation cleanup.

## Features

- Native Android input method service.
- Apple-inspired key layout and spacing.
- Permanent microphone button above the keys.
- Whole-clip recording: record first, transcribe after stop, then insert.
- OpenAI audio transcription via `/v1/audio/transcriptions`.
- Optional Android device speech recognition fallback.
- Optional transcript cleanup via OpenAI Responses API.
- Editable API key, transcription model, transform model, and cleanup prompts.
- Presets: Raw, Clean, Casual, Professional, Bullets, Email.
- Low-latency GPT-5 transform settings with safe fallback retry.
- Minimal autocorrect layer using Android spell checker suggestions when available.
- Custom phrase replacement example: `Cloud Code` -> `Claude Code`.
- Smart spacing for voice inserts.
- Short voice outputs under 5 words do not get a forced trailing period.
- Haptics for keyboard taps.
- Symbols and expanded symbols views.

## Current Status

This is a working prototype and a base for other developers, not a finished consumer keyboard.

Known tradeoffs:

- API keys are stored locally in app preferences. For production, migrate to encrypted storage.
- Audio is sent to the configured transcription provider when using OpenAI transcription.
- Realtime streaming transcription is not implemented yet. The current OpenAI path records the full clip and uploads after stop.
- Autocorrect is intentionally conservative and much simpler than Gboard or Apple Keyboard.
- The UI is tuned for a modern Samsung/Android phone but is not exhaustively tested across devices.

## Build

Requirements:

- Android Studio or Android SDK
- JDK 17+

Build the debug APK:

```powershell
.\gradlew.bat assembleDebug
```

On macOS/Linux:

```bash
./gradlew assembleDebug
```

Output:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Install On A Phone

Enable Developer Options and USB Debugging on your Android phone. On Samsung devices, you may also need to disable Auto Blocker for sideloading.

If you downloaded an APK from Releases, install that APK. If you built from source, install the generated debug APK:

Install:

```powershell
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Then:

1. Open **VoiceFlow Keyboard**.
2. Grant microphone permission.
3. Add your OpenAI API key if using OpenAI transcription/cleanup.
4. Choose your transcription and transform models.
5. Open Android keyboard settings and enable **VoiceFlow Keyboard**.
6. Choose it from the keyboard picker.

## Recommended Model Setup

The default OpenAI flow is:

- transcription: `gpt-4o-transcribe`
- transform: a GPT-5 model

The transform request uses low-latency options for GPT-5-style cleanup tasks:

- `reasoning.effort: none`
- `text.verbosity: low`
- prompt caching key/retention
- fallback retry without optional latency fields if a selected model rejects them

## Prompt Presets

The most useful preset is usually **Casual**. It lightly cleans raw speech-to-text while preserving wording, tone, intent, hedging, and order.

You can edit every preset prompt inside the app settings. This makes the project useful as a base for:

- personal dictation cleanup,
- professional message drafting,
- email rewriting,
- bullet-point conversion,
- domain-specific terminology cleanup.

## Privacy Notes

This keyboard can read what you type and can record microphone audio while active, because that is how Android keyboards and dictation tools work.

Read [PRIVACY.md](PRIVACY.md) before using or modifying this app.

Current privacy model:

- The app does not include a bundled API key.
- Users bring their own OpenAI API key.
- API keys are saved locally on the device in app preferences.
- When OpenAI transcription is enabled, recorded audio is sent to OpenAI after recording stops.
- When transcript cleanup is enabled, transcript text is sent to the configured OpenAI transform model.

For a production release, you should:

- use encrypted preferences or Android Keystore for API keys,
- disable voice/network features in password, OTP, payment, and other sensitive fields,
- publish a clear privacy policy,
- avoid collecting logs that contain dictated text,
- consider a provider abstraction so users can choose local or cloud transcription.

## Roadmap Ideas

- Realtime streaming transcription to reduce perceived latency.
- Better custom dictionary and replacement UI.
- Encrypted API key storage.
- Undo last voice insert.
- Per-field safety rules.
- Better tablet/foldable layouts.
- Optional local transcription model support.
- Release builds and signing instructions.

## Contributing

Issues and pull requests are welcome. See [CONTRIBUTING.md](CONTRIBUTING.md).

## License

MIT. See [LICENSE](LICENSE).
