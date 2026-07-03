# Privacy Policy

This project is an open-source Android keyboard prototype. It is designed for users who bring their own API key and run the app on their own device.

This document describes the default behavior of the app in this repository. If you fork, modify, or distribute it, review and update this policy for your version.

## What The App Can Access

As an Android keyboard, the app may receive text typed through the keyboard and may insert text into the active app.

With microphone permission, the app can record audio while the microphone button is active.

## What Is Sent To External Services

The app does not send typed keystrokes to a server for normal keyboard typing.

When a cloud transcription provider is selected, such as OpenAI, Grok / xAI, or Deepgram:

- audio is recorded locally while the microphone is active,
- after recording stops, the audio file is sent to the selected provider's transcription API,
- the returned transcript is used inside the keyboard.

When transcript cleanup is enabled:

- the transcript text is sent to the selected transform provider, such as OpenAI, Grok / xAI, or Claude / Anthropic,
- the returned cleaned text is inserted into the active text field.

When offline Vosk transcription is selected:

- the app downloads a local speech model on first use,
- audio is recorded locally while the microphone is active,
- transcription runs on the device after recording stops,
- transcript cleanup may still send text to a cloud transform provider if cleanup is enabled.

If a cloud transcription provider is selected but the phone has no validated internet connection, the app falls back to offline Vosk transcription when the local model is already installed. In that case, the recorded audio is not sent to the cloud for transcription.

## API Keys

The app does not include bundled provider API keys.

Users enter their own API keys in the app settings. In the current prototype, keys are stored locally in Android app preferences. This is convenient for development but is not the strongest available storage option.

For a production app, use encrypted preferences or Android Keystore.

## Local Data

Temporary audio files are created in the app cache while processing a recording. The app attempts to delete each temporary audio file after processing.

Offline speech models are stored locally in the app's private files directory.

Settings such as providers, model names, selected preset, prompts, and API keys are stored locally on the device.

## What This Project Does Not Do

The default app does not:

- include a developer-owned API key,
- collect analytics,
- run an app-owned backend server,
- upload normal typed keystrokes,
- sell user data.

## Important Warning

Keyboard apps are inherently sensitive software. Review the source before installing, especially if you use this project as a base for your own public app.
