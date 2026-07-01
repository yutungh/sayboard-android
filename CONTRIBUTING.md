# Contributing

Thanks for helping improve Sayboard.

This project is intended to be a practical open-source base for Android voice dictation keyboards. Keep changes understandable, privacy-conscious, and useful for people who want to build or test the app themselves.

## Good First Contributions

- Device compatibility reports.
- Setup documentation fixes.
- Safer API-key storage.
- Better custom dictionary and replacement UI.
- Realtime transcription experiments.
- Keyboard layout fixes for different screen sizes.
- Accessibility improvements.

## Development Setup

Requirements:

- JDK 17+
- Android SDK or Android Studio

Build:

```bash
./gradlew assembleDebug
```

On Windows:

```powershell
.\gradlew.bat assembleDebug
```

Install on a connected Android device:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Pull Requests

Before opening a pull request:

- Run `./gradlew assembleDebug`.
- Do not commit API keys, keystores, APKs, local SDK folders, or generated build output.
- Keep privacy implications explicit in the PR description.
- Include screenshots for keyboard layout changes when possible.

## Security And Privacy

Do not add telemetry, analytics, or server calls without clear documentation and an opt-in path.

Do not hardcode API keys. Users should provide their own keys.
