# Nova TV

A custom AI home screen for a Fire TV (built for the Toshiba 50LF621U21, Fire OS 7). Night-sky look, an AI assistant panel powered by Claude, quick actions, your pinned apps, clock and weather.

## What it does

- **Assistant panel.** Tap a suggestion card or type a question. Nova answers in short, couch-readable replies and knows which apps are installed. Say "open Netflix" (or accept a suggestion) and it opens the app.
- **Your apps row.** Shows 7 pinned apps with their real artwork, plus All apps.
- **All apps screen.** Every installed app. Hold Select (or press the Menu button) on an app to pin or unpin it from the home row.
- **Clock, greeting and weather.** Weather comes from Open-Meteo, which is free and needs no key.
- **Launcher settings.** Your name, assistant name, Claude API key, AI model, weather location, °F/°C and glow color.
- **Back button** clears the conversation and brings the suggestion cards back.
- **Edit the home screen by asking.** "Make the glow amber", "rename yourself Jarvis", "pin Peacock", "put Netflix first", "hide the quick actions", "quick actions on the left", "make the text bigger", "24-hour clock", "set the weather to Culver City", "change my greeting to What's up", "reset the look". The same options are in Launcher settings → Home screen layout, in case the AI is offline.

## Get the app onto the TV

Two ways to build it. Pick one.

### Option A: GitHub builds it, no laptop needed

1. Make a free GitHub account and create a new **public** repository (for example `nova-tv`).
2. Upload this whole folder to it (or have Claude push it for you once GitHub is connected).
3. GitHub builds the app automatically (Actions tab, about 5 minutes).
4. On the TV, open **Downloader** and enter:
   `github.com/YOUR-USERNAME/nova-tv/releases/download/latest/nova-tv.apk`
5. Choose **Install**, then **Open**.

Every time the code changes, GitHub rebuilds it and the same link gets the new version.

### Option B: Android Studio on a laptop

1. Install [Android Studio](https://developer.android.com/studio) (free).
2. **File → Open** this folder and let it finish syncing.
3. On the TV: Settings → My Fire TV → Developer Options → turn on **ADB debugging**. Find the TV's IP address under Settings → My Fire TV → About → Network.
4. In Android Studio's terminal: `adb connect TV-IP-ADDRESS:5555`, then accept the prompt on the TV.
5. Pick the TV in the device menu and press **Run**.

## First-time setup on the TV

1. In your Fire TV apps list, highlight Nova TV, press the **Menu (☰)** button → **Move to front** so it's one click from Amazon's home.
2. Open **Launcher settings** (sliders icon, top right).
3. Paste your Claude API key from [console.anthropic.com](https://console.anthropic.com). Typing a long key with the remote is painful: the **Amazon Fire TV app** on your phone has a keyboard, so you can paste it from your phone.
4. Set your weather location (latitude and longitude) and **Save**.

## Costs

Each question uses a small amount of Claude API credit. Answers are capped short, and the default model (`claude-haiku-5-5`) is the fast, low-cost one. You can change the model in settings.

## Known limits

- **The Home button still goes to Amazon's home screen.** Fire OS doesn't let other launchers take over Home. Press Home, then click Nova TV.
- **The remote's mic button belongs to Alexa.** To talk instead of type, try holding the mic button while the on-screen keyboard is open. On many Fire TVs that dictates into the text box.
- **Nova can't see your watch history.** Streaming apps don't share it, so "keep watching" style picks are based on what you tell it.
- **Catalogs change.** Nova may suggest a title that has left an app. It's told to say "check" when unsure.

## Signing key

`app/nova.keystore` signs every build the same way, so new versions install over old ones. The password is in `app/build.gradle.kts`. That's fine for a personal app you sideload. If you ever publish it to a store, make a new private key.

## Project map

```
app/src/main/java/com/novatv/launcher/
  MainActivity.kt      Home screen: header, assistant panel, quick actions, apps row
  AppsActivity.kt      All apps grid + pin/unpin
  SettingsActivity.kt  Launcher settings form
  Clients.kt           Claude API + weather calls
  AppRepo.kt           Find, pin and open installed apps; app tiles
  Prefs.kt             Saved settings
  Ui.kt                Colors, fonts, sizes (scaled from the 1920x1080 design)
  Views.kt             Sky background, orb, line icons
```

Font: Sora, under the SIL Open Font License (`app/src/main/assets/fonts/OFL.txt`).
