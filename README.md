# SimpleFocusClock

A full-screen clock for Android: white time on a black screen, in portrait and landscape.

- Focus mode pins the app and silences notifications. It is on by default and can be switched on or off in Settings or from the clock.
- Fourteen noises: white, pink, brown, blue, violet, grey and green noise, plus real recordings of rain, thunderstorm, ocean waves, wind, a stream, a campfire and a fan.
- 15 drawn clock faces (a plain white disc with an hour tick and a minute tick, rings, dots, hands and more) as well as 28 digital fonts, an optional date, optional seconds, and 12 or 24-hour time.
- Statistics: focus hours by day, week, month, year and all time, with a days grid for the whole history, and daily, weekly, monthly and yearly targets. The history is a plain CSV in a `SimpleFocusClock` folder in your Documents, which the app makes itself, so no account and no permission is needed.
- A full-screen timer and stopwatch, and alarms and reminders that go off with the app closed. A reminder appears as a banner over the clock.
- Fractals: 35 live GPU fractals (Mandelbrot dives, Julia sets, Burning Ship, Celtic knots, Newton basins, Mandelbulb, Menger sponge and more) in colour or monochrome, plus a Hilbert curve, fractal tree, Sierpinski triangle and dragon curve. The monochrome ones flip in the light theme.
- The screen stays on while the clock is open.

## Install

Requires Android 11 or newer.

1. Download `SimpleFocusClock-0.4.1.apk` from the [latest release](https://github.com/etrntyy/SimpleFocusClock/releases/latest) on your phone.
2. Open the file. If Android asks, allow installs from your browser or file manager.
3. Scan the app manually. The app is not on the Play Store, so Google Play Protect has not seen it before and may stop the install or warn that it is unscanned:
   - If the prompt offers **Scan app**, tap it and wait for the scan to finish, then tap **Install**.
   - Otherwise open the Play Store, tap your profile picture, then **Play Protect**, then **Scan**. Once it finishes, open the APK again.
4. Open **Focus Clock** and tap **Start**.

## Using it

- **Start** opens the clock. Touch the screen to show the controls: Focus mode, play or pause for the noise, and exit. They fade after a few seconds.
- Hold the time to exit.
- A row of four icons across the top of the home screen opens Statistics, the time tools, Fractals and Settings. The gear opens Settings, where you can set the app and clock themes, the clock style (a drawn face or a font) and your focus targets. 
- On the first launch the app opens Android's Do Not Disturb page once. It saves its history to `Documents/SimpleFocusClock/sessions.csv`, one line per session, and reads it back after a reinstall.
- When a daily target is set, the rings around Start fill as you focus: they start at 3 o'clock and go clockwise, and coming back round to 3 o'clock means the target is done. Each extra lap is a slightly greyer white. Today's focus time shows under Start.
- Statistics counts the time the clock is on screen. Sessions shorter than ten seconds are ignored. The Day chart has one bar for each hour, and the other charts scroll sideways. A dashed line marks your target; it appears only when that target is set.
- Noise plays on your headphones only while any are connected, and stops if they are taken off. Switch on **Speaker with headphones** in Settings to hear it on the phone's speaker as well.
- The All tab of Statistics shows every day as a square, from a faint dark grey for no focus to white at six hours, with the months across the top. It scrolls sideways, and tapping a day shows what you spent on it.
- Settings has an **App theme** (Dark, Light or System) and a **Clock theme** (Dark, Light or Same as app). Light flips every colour: white becomes black, and the days grid runs from a faint off-white to true black. The clock theme covers the clock, timer and stopwatch.
- The hourglass icon opens the time tools: **Timer** (scroll the wheels or tap 5, 10, 25 or 45 minutes), **Stopwatch** with laps, and **Alarms**, which has an Alarms tab and a Reminders tab. The timer and stopwatch fill the screen like the clock, and their time counts as focus time. Alarms can repeat on chosen days, with their own label, sound and snooze length. A reminder can repeat daily, weekly or monthly. Android asks for the notification permission the first time you save one.
- A reminder that goes off while the app is open shows as a banner at the top of the clock for a minute: tap it to snooze for ten minutes, or use the cross to dismiss it. With the app closed it arrives as a notification. An alarm or the timer ending takes over the whole screen, even over the lock screen.
- **Update app** in Settings checks GitHub for a newer release and, if there is one, opens its download in your browser. Open the downloaded file to install it; Android only accepts it as an update if it is signed with the same key. This is the only thing the app does with the internet, and the app never installs anything itself.
- The launcher icon follows the app theme: a white curve on black for dark, black on white for light. Some launchers drop the icon from the home screen when it switches; it stays in the app drawer.
- Focus mode asks Android to pin the app the first time. To also silence notifications, allow Do Not Disturb access when Settings offers it.
- To leave a pinned screen with the system gesture, hold Back and Overview together.

- The fractal shaders come from the author's FractalWalls project. Pick Colour or Monochrome at the top of Fractals, or Shuffle to see every one for a minute each. Tap a row to play it on the whole screen (tap or drag to nudge it, hold anywhere to leave). The switch on the right of a row makes that fractal the clock's background; switch it off to get the plain clock back.

## Sound credits

Rain, thunderstorm, ocean waves, wind, stream, campfire and fan are real recordings from Wikimedia Commons, trimmed to loop. White, pink, brown, blue, violet, grey and green noise are generated by the app.

| Sound | Recording | Author | Licence |
| --- | --- | --- | --- |
| Rain | [Light Rain Distant Thunder July 5th 2016](https://commons.wikimedia.org/wiki/File:Light_Rain_Distant_Thunder_July_5th_2016.wav) | kvgarlic | CC0 |
| Thunderstorm | [Thunderstorm after hot summer day, part 1](https://commons.wikimedia.org/wiki/File:Thunderstorm_after_hot_summer_day_17_minutes_01_of_04.ogg) | stephan | Public domain |
| Ocean waves | [Ocean Waves on a Tropical Beach](https://commons.wikimedia.org/wiki/File:Ocean_Waves_on_a_Tropical_Beach.ogg) | Jarrod stanley | CC0 |
| Wind | [245286 dbspin grunewald](https://commons.wikimedia.org/wiki/File:245286_dbspin_grunewald.wav) | dbspin | CC0 |
| Stream | [433589 jackthemurray stream-river-water-up-close](https://commons.wikimedia.org/wiki/File:433589_jackthemurray_stream-river-water-up-close.wav) | jackthemurray | CC0 |
| Campfire | [Campfire sound ambience](https://commons.wikimedia.org/wiki/File:Campfire_sound_ambience.ogg) | Glaneur de sons | [CC BY 3.0](https://creativecommons.org/licenses/by/3.0) |
| Fan | [Fan (Gravity Sound)](https://commons.wikimedia.org/wiki/File:Fan_(Gravity_Sound).wav) | Gravity Sound | [CC BY 4.0](https://creativecommons.org/licenses/by/4.0) |

The recordings were cut to length, high-pass filtered, matched in loudness and encoded as Opus.

## Build

```
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew :app:assembleDebug
```

Fonts are from Google Fonts and licensed under the SIL Open Font License or Apache 2.0.
