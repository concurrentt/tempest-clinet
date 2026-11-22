"""
Main-menu art: the TEMPEST wordmark (textures/gui/wordmark.png) and the Custom Sky card banner
(textures/gui/promo_sky.png) and the menu backdrop (textures/gui/menu_bg.png), rendered with tools/preview_sky.py from the Black Hole scene).
Run:  python tools/make_menu_art.py      (numpy + Pillow; uses Poppins Bold if present)
"""
import os
import sys

import numpy as np
from PIL import Image, ImageDraw, ImageFont, ImageFilter

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "..", "src/main/resources/assets/tempest/textures/gui")
FONTS = ["/usr/share/fonts/truetype/google-fonts/Poppins-Bold.ttf", "/usr/share/fonts/opentype/inter/Inter-ExtraBold.otf"]


def wordmark():
    font = None
    for f in FONTS:
        if os.path.exists(f):
            font = ImageFont.truetype(f, 220)
            break
    text, spacing = "TEMPEST", 34
    widths = [font.getbbox(ch)[2] - font.getbbox(ch)[0] for ch in text]
    W = sum(widths) + spacing * (len(text) - 1) + 80
    H = 300
    mask = Image.new("L", (W, H), 0)
    d = ImageDraw.Draw(mask)
    x = 40
    for ch, w in zip(text, widths):
        bx = font.getbbox(ch)
        d.text((x - bx[0], 40), ch, font=font, fill=255)
        x += w + spacing
    bb = mask.getbbox()
    mask = mask.crop((bb[0] - 12, bb[1] - 12, bb[2] + 12, bb[3] + 12))
    w, h = mask.size
    # white at the top, a faint lavender toward the bottom
    y = np.linspace(0, 1, h)[:, None]
    top = np.array([1.0, 1.0, 1.0]); bot = np.array([0.80, 0.76, 1.0])
    col = top * (1 - y[..., None] * 0.9) + bot * (y[..., None] * 0.9)
    rgba = np.zeros((h, w, 4))
    rgba[..., :3] = np.broadcast_to(col, (h, w, 3))
    rgba[..., 3] = np.asarray(mask) / 255.0
    img = Image.fromarray((rgba * 255).astype(np.uint8), "RGBA")
    img = img.resize((w // 2, h // 2), Image.LANCZOS)    # ~ 2x the size it's drawn at on a 1080p screen
    img.save(os.path.join(OUT, "wordmark.png"))
    print("wordmark", img.size)


def promo():
    sys.path.insert(0, HERE)
    import preview_sky as ps
    ps.W, ps.H, ps.FOV = 1040, 300, 38.0
    scene = next(s for s in ps.SCENES if s["name"] == "Black Hole")
    im = ps.render(scene, 160, 27, t=4.0)
    # darken the left side so text stays readable over the picture
    a = np.asarray(im).astype(np.float32) / 255
    x = np.linspace(0, 1, a.shape[1])[None, :, None]
    shade = 0.25 + 0.75 * np.clip((x - 0.15) / 0.55, 0, 1)
    a = a * shade
    Image.fromarray((np.clip(a, 0, 1) * 255).astype(np.uint8), "RGB").save(os.path.join(OUT, "promo_sky.png"))
    print("promo_sky", im.size)


def background():
    """Full-screen menu backdrop (textures/gui/menu_bg.png): the Nebula scene, darkened toward the middle."""
    sys.path.insert(0, HERE)
    import preview_sky as ps
    ps.W, ps.H, ps.FOV = 1280, 720, 72.0
    scene = next(s for s in ps.SCENES if s["name"] == "Nebula")
    im = ps.render(scene, 75, 34, t=6.0)
    a = np.asarray(im).astype(np.float32) / 255
    h, w = a.shape[:2]
    yy, xx = np.mgrid[0:h, 0:w]
    d = np.sqrt(((xx - w / 2) / (w / 2)) ** 2 + ((yy - h * 0.55) / (h / 2)) ** 2)
    a = a * (0.55 + 0.45 * np.clip(d, 0, 1))[..., None]       # quieter centre, where the buttons sit
    Image.fromarray((np.clip(a, 0, 1) * 255).astype(np.uint8), "RGB").save(os.path.join(OUT, "menu_bg.png"))
    print("menu_bg", im.size)


# extra menu backgrounds for the theme editor (Client -> GUI & Profiles -> Menu Background)
EXTRA_BACKGROUNDS = [
    ("menu_bg_galaxy.png", "Galaxy Core", 200, 30),
    ("menu_bg_aurora.png", "Northern Lights", 0, 25),
    ("menu_bg_blackhole.png", "Black Hole", 160, 27),
    ("menu_bg_planet.png", "Ringed Giant", 200, 28),
    ("menu_bg_bloodmoon.png", "Blood Moon", 100, 30),
]


def extra_backgrounds():
    sys.path.insert(0, HERE)
    import preview_sky as ps
    ps.W, ps.H, ps.FOV = 1280, 720, 72.0
    for fname, name, az, el in EXTRA_BACKGROUNDS:
        scene = next(s for s in ps.SCENES if s["name"] == name)
        im = ps.render(scene, az, el, t=6.0)
        a = np.asarray(im).astype(np.float32) / 255
        h, w = a.shape[:2]
        yy, xx = np.mgrid[0:h, 0:w]
        d = np.sqrt(((xx - w / 2) / (w / 2)) ** 2 + ((yy - h * 0.55) / (h / 2)) ** 2)
        a = a * (0.62 + 0.38 * np.clip(d, 0, 1))[..., None]
        Image.fromarray((np.clip(a, 0, 1) * 255).astype(np.uint8), "RGB").save(os.path.join(OUT, fname), optimize=True)
        print(fname)


if __name__ == "__main__":
    import sys as _sys
    if "--extra-only" in _sys.argv:
        extra_backgrounds()
    else:
        wordmark()
        promo()
        background()
        extra_backgrounds()
