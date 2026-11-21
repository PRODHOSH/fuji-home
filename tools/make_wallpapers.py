"""Prepare Fuji Home's wallpapers from the photos in wallpapers-src/.

For each entry in wallpapers.json this writes, into app/src/main/assets/wallpapers/:
  <id>.webp        the wallpaper, cropped to 16:9 at 1920x1080 (the TV's UI size)
  <id>_fg.webp     depth wallpapers only: the scenery cut out of the top of the same picture, drawn over the clock
  <id>_thumb.webp  a 400x225 preview for the picker
and index.json, the list the app reads (id, name, depth).

Entry fields: id, name, file (in wallpapers-src/), top (0 = keep the top of the photo, 1 = keep the bottom),
depth (true: cut the scenery out so it stands in front of the clock), shade (0-1: darken the top so the white
clock reads over a bright sky), festival (true: shown only on that festival's days, not in the picker), edge (depth only: how big a colour jump counts as the scenery's edge; default 22).

The cut-out traces the outline column by column: the sky is a smooth gradient, so in each column the scenery
starts at the first sharp colour change. That suits mountains and skylines (no overhangs), not trees or mist.

Run:  tools/venv/bin/python -I tools/make_wallpapers.py   (from the fujihome folder)
Previews of each cut-out over magenta go to build/wallpaper-check/ so the edge can be checked by eye.
"""
import json
import os
import sys

import numpy as np
from PIL import Image, ImageFilter
from scipy import ndimage

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, "wallpapers-src")
OUT = os.path.join(ROOT, "app", "src", "main", "assets", "wallpapers")
CHECK = os.path.join(ROOT, "build", "wallpaper-check")
W, H = 1920, 1080
FG_ROWS = 640          # the clock ends near row 480; nothing below this row is drawn over it


def crop_16x9(im, top):
    h = im.width * 9 // 16
    if h <= im.height:
        y0 = int((im.height - h) * top)
        im = im.crop((0, y0, im.width, y0 + h))
    else:                                            # wider than 16:9: crop the sides
        w = im.height * 16 // 9
        x0 = (im.width - w) // 2
        im = im.crop((x0, 0, x0 + w, im.height))
    return im.resize((W, H), Image.LANCZOS)


def shade_top(im, amount):
    if amount <= 0:
        return im
    arr = np.asarray(im).astype(np.float32)
    ys = np.arange(H, dtype=np.float32)
    fade = np.clip(1 - ys / (H * 0.6), 0, 1) ** 1.5 * amount     # strongest at the top, gone by 60%
    arr *= (1 - fade)[:, None, None]
    return Image.fromarray(arr.clip(0, 255).astype(np.uint8))


def silhouette(im, edge):
    """Mask of the scenery: in each column, everything below the first sharp colour change from the sky."""
    a = np.asarray(im.filter(ImageFilter.GaussianBlur(1.0))).astype(np.float32)
    gap = 4
    diff = np.sqrt(((a[gap:] - a[:-gap]) ** 2).sum(axis=2))
    diff = np.vstack([np.zeros((gap, W)), diff])
    hit = diff > edge
    hit[:20] = False                                 # ignore the frame's very top rows
    # The scenery must stay different from the sky above it for a stretch below the edge: a star, a speck of
    # dust or a thin wisp is a few pixels tall and does not.
    first = np.full(W, H)
    for x in range(W):
        for y in np.flatnonzero(hit[:, x]):
            sky = a[max(y - 8, 0), x]
            below = a[y + 2:min(y + 18, H), x]
            if len(below) and (np.sqrt(((below - sky) ** 2).sum(axis=1)) > edge).mean() > 0.8:
                first[x] = y
                break
    first = ndimage.median_filter(first, size=7, mode="nearest")
    # Remove spikes narrower than ~1% of the width (a wisp or a bird), keeping real summits.
    height = H - first
    first = H - ndimage.grey_opening(height, size=21, mode="nearest")
    first = np.maximum(first - 1, 0)
    mask = np.arange(H)[:, None] >= first[None, :]
    mask = ndimage.binary_erosion(mask, iterations=1)
    return mask, int(first.min())


def main():
    entries = json.load(open(os.path.join(ROOT, "wallpapers.json")))
    os.makedirs(OUT, exist_ok=True)
    os.makedirs(CHECK, exist_ok=True)
    index = []
    for e in entries:
        src = os.path.join(SRC, e["file"])
        if not os.path.isfile(src):
            # The repo ships the prepared wallpapers, not the original photos: keep the existing one as it is.
            if not os.path.isfile(os.path.join(OUT, e["id"] + ".webp")):
                sys.exit(f"{e['id']}: put {e['file']} in wallpapers-src/ first")
            index.append({"id": e["id"], "name": e["name"], "depth": bool(e.get("depth")),
                          "festival": bool(e.get("festival"))})
            print(e["id"] + "  (already prepared, kept)")
            continue
        im = Image.open(src).convert("RGB")
        im = shade_top(crop_16x9(im, e.get("top", 0.5)), e.get("shade", 0))
        im.save(os.path.join(OUT, e["id"] + ".webp"), "WEBP", quality=90, method=6)
        im.resize((400, 225), Image.LANCZOS).save(os.path.join(OUT, e["id"] + "_thumb.webp"), "WEBP", quality=85)
        line = e["id"]
        if e.get("depth"):
            mask, peak = silhouette(im, e.get("edge", 22))
            alpha = Image.fromarray((mask * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(0.8))
            fg = im.convert("RGBA")
            fg.putalpha(alpha)
            fg.crop((0, 0, W, FG_ROWS)).save(os.path.join(OUT, e["id"] + "_fg.webp"), "WEBP", quality=92, method=6)
            check = Image.new("RGBA", (W, H), (255, 0, 255, 255))
            check.alpha_composite(fg)
            check.convert("RGB").resize((960, 540)).save(os.path.join(CHECK, e["id"] + ".jpg"), quality=85)
            line += f"  depth, scenery top at row {peak} ({peak * 100 // H}%)"
        index.append({"id": e["id"], "name": e["name"], "depth": bool(e.get("depth")),
                      "festival": bool(e.get("festival"))})
        print(line)
    json.dump(index, open(os.path.join(OUT, "index.json"), "w"), indent=1)
    total = sum(os.path.getsize(os.path.join(OUT, f)) for f in os.listdir(OUT))
    print(f"{len(index)} wallpapers, {total // 1024} KB in the app")


if __name__ == "__main__":
    sys.exit(main())
