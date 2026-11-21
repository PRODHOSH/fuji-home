# The prompt

Paste this into **Claude Code** (or another coding agent with a terminal), opened in a folder where it can
clone repos, on a computer on the **same Wi-Fi as the TV**.

Works on Android TV and Google TV (Sony, TCL, Xiaomi, Hisense, Philips, OnePlus, Nvidia Shield and
most others), Android 8.0 or newer. Not on Samsung Tizen, LG webOS, Roku or Fire TV.

**Before you start, on the TV:** Settings → Device Preferences → About → press OK on **Build** 7 times. Go back
→ **Developer options** → turn on **USB debugging**, and **Network debugging** if your TV shows it. Keep the TV
on, and plan to do it in one sitting.

---

I want to debloat my Android TV and give it a clean, lock-screen style home screen with Fuji Home.
My TV model is: **<MODEL NAME HERE>**.

Work on the real TV, step by step, and check each step with a screenshot pulled from the TV. Ask me before
anything that depends on my preferences. Tell me in plain words what you are about to do before each change.

**Use these two repos, don't write your own versions of them:**
- `git clone https://github.com/Gathin23/cleanyourtv` for connecting, backing up and debloating. Read its
  `PROMPT.md` and `AGENTS.md` first and follow their safety rules exactly. Where its steps install its own
  launcher (`launcher/`), install Fuji Home instead (below). Everything else, including its Home guard, stays.
- `git clone https://github.com/PRODHOSH/fuji-home` for the home screen.

**1. Connect and back up**
- Find the TV on my network (or ask me for its IP) and connect over ADB on port 5555, as CleanYourTV's
  `PROMPT.md` describes. Tell me when to accept the "Allow USB debugging?" prompt on the TV.
- Before changing anything: save the package list, the disabled list, `getprop` and all settings to a backup
  folder, and write a restore script that undoes every change. Show me where they are.

**2. Debloat, gently**
- Follow CleanYourTV's procedure and use `tools/disable.sh`. Disable (`pm disable-user`), never uninstall.
  Never clear any app's data or cache: my apps must stay logged in.
- Ask me in one checklist which streaming apps, the voice assistant, Chromecast and the firmware updater I
  use, and leave those alone.
- Work in small batches with a screenshot between each. Keep a list of everything you switched off, with why.

**3. Install Fuji Home**
- Ask me for my city (for the weather), then set `weather_latitude` and `weather_longitude` in
  `fuji-home/app/src/main/res/values/config.xml`. Find my TV maker's Live TV app
  (`pm list packages | grep -i tv`) and set `live_tv_package`.
- Ask me which apps I want in the dock and in what order, and set the `DOCK` list at the top of
  `app/src/main/java/com/protv/fujihome/HomeActivity.java` to their package names (only ones installed on my TV).
- Build it: JDK 17 and Android SDK platform 34 (on a Mac, with Homebrew), then `./gradlew assembleRelease`
  in `fuji-home/`. Install `app/build/outputs/apk/release/app-release.apk`; if `adb install` hangs, push it and
  run `pm install -r` over the shell. If the TV is below Android 8.0 (SDK 26), stop and tell me.
- Open Fuji Home once and show me a screenshot. Show me the wallpaper picker (••• → Wallpapers) and let me
  pick one.

**4. Make it the home screen** (only after I say OK)
- Explain the two options from the Fuji Home README ("Making it the home screen"): the gentle accessibility
  helper, or replacing the stock launcher. Let me choose.
- For replacing: only switch the stock launcher off once Fuji Home is installed and confirmed as Home, with
  CleanYourTV's Home guard. Then press Home, reboot once, and confirm with a screenshot that Fuji Home comes
  up and ADB comes back.
- Set the **Fuji clock** screensaver if I want it (Settings → Device Preferences → Screen saver).

**5. Finish**
- Write me a short guide: what changed, how to switch everything back with only the remote
  (Settings → Apps → Show system apps → Enable), and how to run the restore script.
- Switch debugging off again and confirm with CleanYourTV's `tools/adb-closed.py <tv ip>` that port 5555 is
  closed. Remind me to switch Developer options off.

Don't add anything I didn't ask for.

---

## Add-ons (only if you want them)

Fuji Home's default is just the home screen. To add one, paste the matching line **after** the main prompt has
finished, or add it to the end of the prompt above.

**NBA scores**
> Turn on Fuji Home's NBA add-on for my team: **<TEAM, e.g. Warriors>**. Set `nba_team` in `config.xml` to
> ESPN's abbreviation for it, rebuild, reinstall over the existing app (keep its data), and show me Home.

**Tamil calendar**
> Turn on Fuji Home's Tamil calendar add-on: set `addon_tamil_calendar` to `true` in `config.xml`, run
> `python3 tools/fetch_tamil_calendar.py <this year> <next year>` for the festival line, rebuild, reinstall over
> the existing app (keep its data), and show me ••• → தமிழ் நாட்காட்டி.

**My own wallpaper**
> Add this photo as a Fuji Home wallpaper: **<path to photo>**. Follow "Make it yours → Wallpapers" in the
> README, use the depth effect if it has a clear skyline, show me the preview sheet before installing, then
> rebuild and reinstall over the existing app.
