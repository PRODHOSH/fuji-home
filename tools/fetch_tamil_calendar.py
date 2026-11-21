"""Optional: festival and vratham days for the Home date line, from tamildailycalendar.com's public lists.

Reads the site's festival list and its year table (Amavasai, Pournami, Pradosham, Karthigai, Sashti,
Sankatahara Chathurthi, Maadha Sivarathiri, Ekadhasi) and writes app/src/main/assets/tamil/days.json:

    {"2026-11-20": {"fest": [...], "days": [...], "wp": "fest_diya"}}

The data belongs to the site, so it is not part of this repo (.gitignore keeps it out). Run it for your own
build, once a year after the site publishes the next one. Standard library only:

    python3 tools/fetch_tamil_calendar.py 2026 2027
"""
import utils.transport
import utils.session
import json
import os
import re
import sys
import urllib.request
from html.parser import HTMLParser

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "app", "src", "main", "assets", "tamil", "days.json")
SITE = "https://www.tamildailycalendar.com/"
AGENT = "FujiHome/1.0 (+https://github.com/PRODHOSH/fuji-home)"
MONTHS = {m: i + 1 for i, m in enumerate(["January", "February", "March", "April", "May", "June", "July", "August",
                                          "September", "October", "November", "December"])}
SHORT = {m[:3]: i for m, i in MONTHS.items()}
# Year-table columns shown on Home, with the words the TV shows (2-3 words, Tamil).
DAYS = {"Ekadhasi": "ஏகாதசி", "Pradosham": "பிரதோஷம்", "Pournami": "பௌர்ணமி", "Amavasai": "அமாவாசை",
        "Sankatahara Chathurthi": "சங்கடஹர சதுர்த்தி", "Karthigai": "கிருத்திகை", "Sashti": "சஷ்டி",
        "Maadha Sivarathiri": "மாத சிவராத்திரி"}
# Festival wallpapers, by the calendar's Tamil festival names.
WALLPAPER = {"போகி": "fest_pongal", "பொங்கல்": "fest_pongal", "தீபாவளி": "fest_diya", "கார்த்திகை தீபம்": "fest_diya",
             "திருக்கார்த்திகை": "fest_diya"}


def get(path):
    req = urllib.request.Request(SITE + path, headers={"User-Agent": AGENT})
    with urllib.request.urlopen(req, timeout=30) as r:
        return r.read().decode("utf-8", "replace")


class Rows(HTMLParser):
    """Every table row as a list of cell texts."""
    def __init__(self):
        super().__init__()
        self.rows, self.row, self.cell = [], None, None

    def handle_starttag(self, tag, attrs):
        if tag == "tr":
            self.row = []
        elif tag in ("td", "th") and self.row is not None:
            self.cell = []
        elif tag == "br" and self.cell is not None:
            self.cell.append("\n")

    def handle_endtag(self, tag):
        if tag in ("td", "th") and self.row is not None and self.cell is not None:
            self.row.append("".join(self.cell))
            self.cell = None
        elif tag == "tr" and self.row is not None:
            self.rows.append(self.row)
            self.row = None

    def handle_data(self, data):
        if self.cell is not None:
            self.cell.append(data)


def year_table(year):
    """{column: [iso dates]} from the year table (the same table on pournami.php, amavasai.php, ...)."""
    p = Rows()
    p.feed(get(f"pournami.php?Year={year}"))
    out, header = {}, None
    for row in p.rows:
        cells = [re.sub(r"\s+", " ", c).strip() for c in row]
        if "Amavasai" in cells and "Ekadhasi" in cells:
            header = cells
            continue
        if not header or not cells or cells[0] not in MONTHS:
            continue
        for col, cell in zip(header[1:], cells[1:]):
            for d in re.findall(r"(\d{1,2})\s+(?:Sun|Mon|Tue|Wed|Thu|Fri|Sat)", cell):
                out.setdefault(col, []).append(f"{year}-{MONTHS[cells[0]]:02d}-{int(d):02d}")
    return out


def festivals(year):
    """[(iso date, Tamil name)] from the Hindu festival list of tamil_festival_dates.php."""
    page = get(f"tamil_festival_dates.php?Year={year}")
    page = page[page.find("Hindu Festivals"):page.find("Christian Festivals")]
    p = Rows()
    p.feed(page)
    out = []
    for row in p.rows:
        for cell in row:
            m = re.match(r"\s*(\d{1,2}) (\w{3}) (\d{4}) - \w+\s*\n\s*(.+?)\s*$", cell, re.S)
            if m and m.group(2) in SHORT:
                out.append((f"{m.group(3)}-{SHORT[m.group(2)]:02d}-{int(m.group(1)):02d}", m.group(4).strip()))
    return out


def main(years):
    days = {}
    for y in years:
        table = year_table(y)
        missing = [c for c in DAYS if not table.get(c)]
        if missing:
            sys.exit(f"{y}: the calendar's year table has no {missing}; nothing written")
        for col, words in DAYS.items():
            for d in table[col]:
                days.setdefault(d, {}).setdefault("days", []).append(words)
        fest = festivals(y)
        if len(fest) < 20:
            sys.exit(f"{y}: only {len(fest)} festivals read; nothing written")
        for d, name in fest:
            e = days.setdefault(d, {})
            e.setdefault("fest", []).append(name)
            for key, wp in WALLPAPER.items():
                if key in name:
                    e["wp"] = wp
        print(f"{y}: " + ", ".join(f"{c} {len(table[c])}" for c in DAYS) + f", festivals {len(fest)}")
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, "w") as f:
        json.dump(dict(sorted(days.items())), f, ensure_ascii=False, separators=(",", ":"))
    print(f"{len(days)} days -> {OUT} ({os.path.getsize(OUT) // 1024} KB)")


if __name__ == "__main__":
    main([int(a) for a in sys.argv[1:]] or [2026, 2027])
