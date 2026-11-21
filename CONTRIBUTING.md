# Contributing to Fuji Home

Thanks for wanting to help! Fuji Home is small on purpose: a calm home screen that runs well on modest TVs.
Contributions that keep it that way are very welcome.

## Good first contributions

- **Tell us it works on your TV.** Open a "Works on my TV" issue with the model and Android version.
- **Wallpapers.** A great photo with a clean skyline makes a great depth wallpaper. Use photos you're allowed to
  share (your own, or Unsplash/Pexels), and add the source to `CREDITS.md`.
- **Fixes and translations** of the strings in `app/src/main/res/values/strings.xml`.
- **Add-ons.** A new widget (cricket, F1, a different calendar…) is welcome **as an add-on that is off by
  default**, switched on from `config.xml`, like the NBA and Tamil calendar ones.

## Ground rules

- **Off by default.** The default build is just the home screen. New features go behind a `config.xml` switch.
- **Light.** No video backgrounds, no live blur, no new libraries without an issue first. Test on a real TV if you
  can; many have 1–2 GB of RAM.
- **Private.** No analytics, no tracking, no accounts. Any new network call must be listed in the README's
  Privacy table.
- **Never delete anything on someone's TV.** Scripts and docs switch apps off (`pm disable-user`), never
  uninstall, and never clear app data. Safety rules come from
  [CleanYourTV](https://github.com/Gathin23/cleanyourtv); don't weaken them.
- **Respect other people's data.** Don't commit data scraped from third-party sites (the Tamil calendar data
  stays local and gitignored for that reason).
- **No personal info** in commits, issues or screenshots: no IP addresses, Wi-Fi names, emails, serial numbers,
  account names, or Settings screens. Blur them first.

## Building

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 17)   # or your JDK 17 path
./gradlew assembleRelease
```

Install with `adb install -r app/build/outputs/apk/release/app-release.apk`. Check with the D-pad from every
edge (Up, Down, Left, Right) and with Back and Home.

## Pull requests

- One topic per PR. Small is great.
- Title like `area: what changed`, e.g. `wallpapers: add Lofoten`, `nba: show overtime`.
- Say which TV and Android version you tested on.
- Every visual change gets a screenshot from a real TV in the PR description (not in the repo).

By contributing you agree your work is released under the [MIT License](LICENSE).
