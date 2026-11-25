"""
Builds Tempest's chat emoji font from Twemoji (graphics CC-BY 4.0, https://github.com/jdecked/twemoji) and GitHub's
gemoji names (MIT, https://github.com/github/gemoji), so ":sob:" or a real 😭 in chat shows the emoji image.

Output (under OUT):
  assets/tempest/font/emoji.json            bitmap font, glyphs on private-use characters U+E000...
  assets/tempest/textures/font/emoji_N.png  glyph sheets, 32 x 32 px per emoji, 32 x 32 emoji per sheet
  assets/tempest/emoji/emoji.tsv            char <TAB> emoji <TAB> names (comma separated)

Run:  python tools/make_emoji_font.py <twemoji repo> <gemoji repo> [out_dir]
"""
import json, os, sys
from PIL import Image

TW, GE = sys.argv[1], sys.argv[2]
OUT = sys.argv[3] if len(sys.argv) > 3 else os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources")
CELL, PER_ROW, ROWS = 32, 32, 32
BASE = 0xE000

# Discord names that differ from gemoji's
EXTRA = {"slight_smile": "slightly_smiling_face", "slight_frown": "slightly_frowning_face", "upside_down": "upside_down_face",
         "hugging": "hugs", "nerd": "nerd_face", "clown": "clown_face", "middle_finger": "fu", "thinking": "thinking",
         "rofl": "rofl", "lying_face": "lying_face", "zipper_mouth": "zipper_mouth_face", "head_bandage": "face_with_head_bandage",
         "thermometer_face": "face_with_thermometer", "robot": "robot", "money_mouth": "money_mouth_face", "sunglasses": "sunglasses",
         "face_with_monocle": "monocle_face", "shushing_face": "shushing_face", "face_with_hand_over_mouth": "hand_over_mouth",
         "face_vomiting": "vomiting_face", "exploding_head": "exploding_head", "star_struck": "star_struck",
         "skull_crossbones": "skull_and_crossbones", "heart": "heart", "hearts": "hearts", "thumbup": "+1", "thumbdown": "-1",
         "white_heart": "white_heart", "regional_indicator_a": None}

data = json.load(open(os.path.join(GE, "db", "emoji.json"), encoding="utf-8"))
png_dir = os.path.join(TW, "assets", "72x72")
have = set(f[:-4] for f in os.listdir(png_dir) if f.endswith(".png"))

def tw_name(e):
    cps = [f"{ord(c):x}" for c in e]
    full = "-".join(cps)
    nofe = "-".join(c for c in cps if c != "fe0f")
    return full if full in have else nofe if nofe in have else None

entries = []          # (emoji, file, [names])
by_alias = {}
for d in data:
    e = d["emoji"]; f = tw_name(e)
    if not f: continue
    names = list(dict.fromkeys(d.get("aliases", [])))
    entries.append([e, f, names])
    for a in names: by_alias[a] = len(entries) - 1
for discord, gem in EXTRA.items():
    if gem and gem in by_alias and discord not in by_alias:
        entries[by_alias[gem]][2].append(discord); by_alias[discord] = by_alias[gem]

per_sheet = PER_ROW * ROWS
sheets = (len(entries) + per_sheet - 1) // per_sheet
os.makedirs(os.path.join(OUT, "assets/tempest/textures/font"), exist_ok=True)
os.makedirs(os.path.join(OUT, "assets/tempest/font"), exist_ok=True)
os.makedirs(os.path.join(OUT, "assets/tempest/emoji"), exist_ok=True)
providers = []
for s in range(sheets):
    chunk = entries[s * per_sheet:(s + 1) * per_sheet]
    rows = (len(chunk) + PER_ROW - 1) // PER_ROW
    img = Image.new("RGBA", (PER_ROW * CELL, rows * CELL), (0, 0, 0, 0))
    lines = []
    for r in range(rows):
        line = ""
        for c in range(PER_ROW):
            i = s * per_sheet + r * PER_ROW + c
            if i < len(entries):
                g = Image.open(os.path.join(png_dir, entries[i][1] + ".png")).convert("RGBA")
                g = g.resize((CELL - 2, CELL - 2), Image.LANCZOS)    # 1 px clear border so glyphs don't bleed
                img.paste(g, (c * CELL + 1, r * CELL + 1))
                line += chr(BASE + i)
            else:
                line += "\u0000"
        lines.append(line)
    name = f"emoji_{s}.png"
    img.save(os.path.join(OUT, "assets/tempest/textures/font", name), optimize=True)
    providers.append({"type": "bitmap", "file": f"tempest:font/{name}", "ascent": 7, "height": 8, "chars": lines})
json.dump({"providers": providers}, open(os.path.join(OUT, "assets/tempest/font/emoji.json"), "w", encoding="utf-8"),
          ensure_ascii=True, indent=1)
with open(os.path.join(OUT, "assets/tempest/emoji/emoji.tsv"), "w", encoding="utf-8") as f:
    for i, (e, _, names) in enumerate(entries):
        f.write(f"{BASE + i:04X}\t{e}\t{','.join(names)}\n")
print(len(entries), "emoji on", sheets, "sheet(s);", sum(len(x[2]) for x in entries), "names")
