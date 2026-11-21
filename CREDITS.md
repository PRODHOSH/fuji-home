# Credits

Fuji Home stands on other people's work. Thank you.

## Code and ideas

- **[CleanYourTV](https://github.com/Gathin23/cleanyourtv)** by Gathin23, MIT. The safe debloat procedure, the
  ADB tools and the Home guard. `HomeRedirectService` is adapted from its HomeKeyService.

## Data

- **[tamildailycalendar.com](https://www.tamildailycalendar.com)**: the Tamil daily calendar sheets and festival
  lists used by the Tamil calendar add-on. Their data is not included in this repo; the app loads their published
  page for the day you open, and `tools/fetch_tamil_calendar.py` reads their public lists on your own machine.
  There is no official API, so please keep requests light.
- **[Open-Meteo](https://open-meteo.com)**: weather, free and without a key (CC BY 4.0 data).
- **ESPN**: public NBA scoreboard and schedule data, used by the NBA add-on. Not affiliated with or endorsed by
  ESPN or the NBA; team logos belong to their owners.

## Stickers

- The stickers in the README (`docs/stickers/`) are from **[Stick It Up](https://stickitupx.com/stickers)** by
  Samiran De, under the MIT License ([`docs/stickers/LICENSE`](docs/stickers/LICENSE)).

## Font

- **[Outfit](https://fonts.google.com/specimen/Outfit)**, Copyright The Outfit Project Authors, under the
  [SIL Open Font License 1.1](FONT-LICENSE-OFL.txt).

## Wallpapers

Photos from [Unsplash](https://unsplash.com), under the [Unsplash License](https://unsplash.com/license). The
originals are in [`wallpapers-src/`](wallpapers-src); the app uses copies cropped and resized for the TV (and cut out
for the depth effect) in `app/src/main/assets/wallpapers/`.

| Wallpaper | id | Source |
|---|---|---|
| Mount Fuji | `fuji` | [Unsplash](https://unsplash.com/photos/KZSDCocsOEE) |
| Matterhorn Sunset | `matterhorn_sunset` | [Unsplash](https://images.unsplash.com/photo-1706953608349-4333b9707277) |
| Mount Kailash | `kailash` | [Unsplash](https://unsplash.com/photos/ZK0csdUtbK0) |
| Himalaya Dawn | `himalaya_dawn` | [Unsplash](https://unsplash.com/photos/RrtAqhtdJVE) |
| Himalaya Gold | `himalaya_gold` | [Unsplash](https://unsplash.com/photos/CXzGEG7UCQc) |
| Dolomites | `dolomites` | [Unsplash](https://images.unsplash.com/photo-1768834605031-10b64c6bc206) |
| Lago di Carezza | `carezza` | [Unsplash](https://images.unsplash.com/photo-1531275090635-95f0974c3073) |
| Lac Blanc, Mont Blanc | `lac_blanc` | [Unsplash](https://images.unsplash.com/photo-1752602131777-093ec5062ecf) |
| Lauterbrunnen | `lauterbrunnen` | [Unsplash](https://images.unsplash.com/photo-1630009574406-a9b21b77c015) |
| Provence Lavender | `lavender` | [Unsplash](https://images.unsplash.com/photo-1735653279088-c906bb6dc362) |
| Moraine Lake | `moraine` | [Unsplash](https://images.unsplash.com/photo-1539667547529-84c607280d20) |
| Beach Sunrise | `beach` | [Unsplash](https://unsplash.com/photos/KMn4VEeEPR8) |
| Palm Sunset | `palm` | [Unsplash](https://unsplash.com/photos/CXyz3qljaH8) |
| Deepavali | `fest_diya` | [Unsplash](https://images.unsplash.com/photo-1636619773834-c7e0762ddfe1) |
| Pongal | `fest_pongal` | [Unsplash](https://images.unsplash.com/photo-1732603891196-2b8cc24f39a5) |

If you took one of these photos and want a name credit, or want it removed, open an issue and it'll be done.
