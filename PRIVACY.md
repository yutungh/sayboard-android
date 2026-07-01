# Privacy Policy

This project is an open-source Android keyboard prototype. It is designed for users who bring their own API key and run the app on their own device.

This document describes the default behavior of the app in this repository. If you fork, modify, or distribute it, review and update this policy for your version.

## What The App Can Access

As an Android keyboard, the app may receive text typed through the keyboard and may insert text into the active app.

With microphone permission, the app can record audio while the microphone button is active.

## What Is Sent To External Services

The app does not send typed keystrokes to a server for normal keyboard typing.

When OpenAI transcription is selected:

- audio is recorded locally while the microphone is active,
- after recording stops, the audio file is sent to OpenAI's audio transcription API,
- the returned transcript is used inside the keyboard.

When transcript cleanup is enabled:

- the transcript text is sent to OpenAI's Responses API,
- the returned cleaned text is inserted into the active text field.

If Android device speech recognition is selected instead of OpenAI transcription, speech handling depends on the Android speech recognizer configured on the user's device.

## API Keys

The app does not include a bundled OpenAI API key.

Users enter their own API key in the app settings. In the current prototype, the key is stored locally in Android app preferences. This is convenient for development but is not the strongest available storage option.

For a production app, use encrypted preferences or Android Keystore.

## Local Data

Temporary audio files are created in the app cache while processing a recording. The app attempts to delete each temporary audio file after processing.

Settings such as provider, model names, selected preset, prompts, and API key are stored locally on the device.

## What This Project Does Not Do

The default app does not:

- include a developer-owned API key,
- collect analytics,
- run an app-owned backend server,
- upload normal typed keystrokes,
- sell user data.

## Important Warning

Keyboard apps are inherently sensitive software. Review the source before installing, especially if you use this project as a base for your own public app.
