# SimpleFocusClock

A full-screen clock for Android: white time on a black screen, in portrait and landscape.

- Focus mode pins the app and silences notifications. It is on by default and can be switched on or off in Settings or from the clock.
- Seven coloured noises: white, pink, brown, blue, violet, grey and green.
- 28 clock fonts, an optional date, and 12 or 24-hour time.
- The screen stays on while the clock is open.

## Install

Requires Android 11 or newer.

1. Download `SimpleFocusClock-0.1.0.apk` from the [latest release](https://github.com/etrntyy/SimpleFocusClock/releases/latest) on your phone.
2. Open the file. If Android asks, allow installs from your browser or file manager.
3. Scan the app manually. The app is not on the Play Store, so Google Play Protect has not seen it before and may stop the install or warn that it is unscanned:
   - If the prompt offers **Scan app**, tap it and wait for the scan to finish, then tap **Install**.
   - Otherwise open the Play Store, tap your profile picture, then **Play Protect**, then **Scan**. Once it finishes, open the APK again.
4. Open **Focus Clock** and tap **Start**.

## Using it

- **Start** opens the clock. Touch the screen to show the controls: Focus mode, play or pause for the noise, and exit. They fade after a few seconds.
- Hold the time to exit.
- The gear icon at the top right opens Settings, where you can pick the font.
- Focus mode asks Android to pin the app the first time. To also silence notifications, allow Do Not Disturb access when Settings offers it.
- To leave a pinned screen with the system gesture, hold Back and Overview together.

## Build

```
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew :app:assembleDebug
```

Fonts are from Google Fonts and licensed under the SIL Open Font License or Apache 2.0.
