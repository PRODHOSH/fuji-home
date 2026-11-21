"""Draw each prepared wallpaper as the TV shows it (date, big clock, depth cut-out on top) into one contact sheet,
build/wallpaper-check/preview.jpg, to judge the depth effect and how well the clock reads before installing.

Run after make_wallpapers.py:  tools/venv/bin/python -I tools/preview_wallpapers.py
"""
import json
import os

from PIL import Image, ImageDraw, ImageFont

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
WP = os.path.join(ROOT, "app", "src", "main", "assets", "wallpapers")
FONT = os.path.join(ROOT, "app", "src", "main", "assets", "outfit.ttf")
OUT = os.path.join(ROOT, "build", "wallpaper-check", "preview.jpg")


def font(size, weight):
    f = ImageFont.truetype(FONT, size)
    try:
        f.set_variation_by_axes([weight])
    except Exception:
        pass
    return f


def main():
    index = json.load(open(os.path.join(WP, "index.json")))
    clock, date = font(470, 150), font(34, 450)
    tiles = []
    for e in index:
        im = Image.open(os.path.join(WP, e["id"] + ".webp")).convert("RGBA")
        layer = Image.new("RGBA", im.size, (0, 0, 0, 0))
        d = ImageDraw.Draw(layer)
        d.text((960, 112), "Saturday 21 November", font=date, fill=(255, 255, 255, 242), anchor="ms")
        top = d.textbbox((960, 0), "10:47", font=clock, anchor="ms")[1]
        d.text((960, 150 - top), "10:47", font=clock, fill=(255, 255, 255, 222), anchor="ms")
        im.alpha_composite(layer)
        fg = os.path.join(WP, e["id"] + "_fg.webp")
        if os.path.exists(fg):
            im.alpha_composite(Image.open(fg).convert("RGBA"))
        t = im.convert("RGB").resize((640, 360))
        ImageDraw.Draw(t).text((8, 340), e["name"] + ("  [depth]" if e["depth"] else ""), fill="yellow")
        tiles.append(t)
    cols = 3
    rows = (len(tiles) + cols - 1) // cols
    sheet = Image.new("RGB", (640 * cols, 360 * rows))
    for i, t in enumerate(tiles):
        sheet.paste(t, ((i % cols) * 640, (i // cols) * 360))
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    sheet.save(OUT, quality=85)
    print(OUT, sheet.size)


if __name__ == "__main__":
    main()
