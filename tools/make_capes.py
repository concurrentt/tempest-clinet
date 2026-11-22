"""
Generates extra Tempest capes (original procedural art):
  src/main/resources/assets/tempest/textures/cape/<name>.png

Layout is the vanilla cape texture (64x32) at 4x = 256x128:
  outer face (what others see on your back)  cape px x1..11, y1..17  -> texture x4..44, y4..68
  inner face                                  x12..22, y1..17
  edges: top x1..11 y0, bottom x11..21 y0, sides x0 / x11 (y1..17)
  elytra                                      x22..46, y0..22
Run:  python tools/make_capes.py    (numpy + Pillow)
"""
import math
import os
import sys

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

OUT = sys.argv[1] if len(sys.argv) > 1 else os.path.join(os.path.dirname(__file__), "..",
                                                        "src/main/resources/assets/tempest/textures/cape")
os.makedirs(OUT, exist_ok=True)
S = 4                      # texture pixels per cape pixel
FW, FH = 10 * S, 16 * S    # face size in texture pixels (40 x 64)
SS = 4                     # supersampling for the art
AW, AH = FW * SS, FH * SS  # art canvas (160 x 256)
rng = np.random.default_rng(7)


def grid():
    y, x = np.mgrid[0:AH, 0:AW]
    return (x + 0.5) / AW, (y + 0.5) / AH


def vgrad(top, bottom):
    _, v = grid()
    t = v[..., None]
    return np.array(top, np.float32) / 255 * (1 - t) + np.array(bottom, np.float32) / 255 * t


def stars(img, n, col=(255, 255, 255), max_r=1.6, seed=1):
    r = np.random.default_rng(seed)
    d = ImageDraw.Draw(img)
    for _ in range(n):
        x, y, s = r.uniform(0, AW), r.uniform(0, AH), r.uniform(0.6, max_r) * SS / 2
        a = int(r.uniform(120, 255))
        d.ellipse([x - s, y - s, x + s, y + s], fill=col + (a,))


def to_img(arr):
    return Image.fromarray((np.clip(arr, 0, 1) * 255).astype(np.uint8), "RGB").convert("RGBA")


def noise(scale, seed, octaves=3):
    """Smooth value noise on the art canvas."""
    r = np.random.default_rng(seed)
    out = np.zeros((AH, AW), np.float32)
    amp, tot = 1.0, 0.0
    for o in range(octaves):
        gh, gw = int(scale * (2 ** o) * AH / AW) + 2, int(scale * (2 ** o)) + 2
        g = r.random((gh, gw)).astype(np.float32)
        im = Image.fromarray((g * 255).astype(np.uint8)).resize((AW, AH), Image.BICUBIC)
        out += amp * np.asarray(im, np.float32) / 255
        tot += amp
        amp *= 0.5
    return out / tot


# ------------------------------------------------------------------ the designs (each returns an RGBA art image)
def aurora():
    a = vgrad((6, 10, 30), (2, 3, 10))
    u, v = grid()
    for k, (col, base, amp, freq, ph) in enumerate([((60, 255, 170), 0.30, 0.06, 2.2, 0.0),
                                                    ((120, 90, 255), 0.42, 0.05, 1.7, 1.3),
                                                    ((40, 220, 255), 0.55, 0.04, 2.8, 2.1)]):
        center = base + amp * np.sin(u * math.pi * freq + ph)
        band = np.exp(-((v - center) / 0.05) ** 2)
        curtain = np.clip((center - v + 0.18) / 0.18, 0, 1) * (v < center + 0.02)          # rays hanging above the band
        streak = 0.55 + 0.45 * np.sin(u * 70 + k * 2 + 3 * np.sin(u * 9))
        glow = (band * 0.9 + curtain * 0.45 * streak) * 0.85
        a += glow[..., None] * (np.array(col) / 255)
    img = to_img(a)
    stars(img, 40, seed=3, max_r=1.3)
    # mountains along the bottom
    d = ImageDraw.Draw(img)
    pts = [(0, AH)] + [(x, AH * (0.86 - 0.06 * math.sin(x / AW * 7) - 0.04 * math.sin(x / AW * 17))) for x in range(0, AW + 1, 4)] + [(AW, AH)]
    d.polygon(pts, fill=(3, 6, 12, 255))
    return img


def galaxy():
    a = vgrad((18, 6, 34), (4, 2, 12))
    u, v = grid()
    cx, cy = 0.5, 0.45
    x, y = (u - cx) * AW / AH, v - cy
    r = np.sqrt(x * x + y * y) + 1e-6
    th = np.arctan2(y, x)
    arms = np.cos(2 * (th - np.log(r) * 2.4)) * 0.5 + 0.5
    disk = np.exp(-r / 0.16) * (0.35 + 0.65 * arms ** 3)
    core = np.exp(-(r / 0.035) ** 2)
    a += disk[..., None] * np.array([0.75, 0.55, 1.0]) + core[..., None] * np.array([1.0, 0.92, 0.85])
    neb = noise(3, 11)
    a += (np.clip(neb - 0.55, 0, 1) * 0.7)[..., None] * np.array([0.9, 0.25, 0.6])
    img = to_img(a)
    stars(img, 70, seed=5, max_r=1.4)
    return img


def sakura():
    a = vgrad((255, 196, 214), (255, 238, 244))
    img = to_img(a)
    d = ImageDraw.Draw(img)
    # branch from the upper left
    pts = [(-10, AH * 0.18), (AW * 0.25, AH * 0.24), (AW * 0.48, AH * 0.36), (AW * 0.70, AH * 0.40), (AW * 0.95, AH * 0.52)]
    d.line(pts, fill=(70, 40, 40, 255), width=9, joint="curve")
    d.line([(AW * 0.48, AH * 0.36), (AW * 0.62, AH * 0.22), (AW * 0.80, AH * 0.16)], fill=(70, 40, 40, 255), width=6, joint="curve")
    d.line([(AW * 0.25, AH * 0.24), (AW * 0.30, AH * 0.42)], fill=(70, 40, 40, 255), width=5)

    def flower(cx, cy, s, col):
        for k in range(5):
            ang = k * 2 * math.pi / 5 - math.pi / 2
            px, py = cx + math.cos(ang) * s * 0.55, cy + math.sin(ang) * s * 0.55
            d.ellipse([px - s * 0.5, py - s * 0.5, px + s * 0.5, py + s * 0.5], fill=col + (255,))
        d.ellipse([cx - s * 0.22, cy - s * 0.22, cx + s * 0.22, cy + s * 0.22], fill=(255, 220, 120, 255))
    r = np.random.default_rng(9)
    for (bx, by) in [(0.2, 0.22), (0.36, 0.30), (0.52, 0.35), (0.64, 0.24), (0.78, 0.17), (0.72, 0.42), (0.88, 0.48), (0.29, 0.40), (0.45, 0.28)]:
        flower(AW * bx + r.uniform(-4, 4), AH * by + r.uniform(-4, 4), r.uniform(12, 18),
               (255, int(r.uniform(150, 190)), int(r.uniform(190, 215))))
    for _ in range(14):     # falling petals
        x, y, s = r.uniform(0, AW), r.uniform(AH * 0.45, AH), r.uniform(4, 7)
        d.ellipse([x - s, y - s * 0.6, x + s, y + s * 0.6], fill=(255, 160, 195, 230))
    return img


def inferno():
    u, v = grid()
    n = noise(4, 21, 4)
    flame = np.clip((v - 0.08) * 1.25 + (n - 0.5) * 0.65, 0, 1)            # hotter toward the bottom
    flame = flame ** 1.3
    col = np.stack([np.clip(flame * 2.2, 0, 1), np.clip(flame * 1.6 - 0.45, 0, 1), np.clip(flame * 1.4 - 1.0, 0, 1)], -1)
    tongues = np.clip(np.sin(u * math.pi * 6 + n * 6) * 0.5 + 0.5 - (1 - v) * 1.4 + 0.6, 0, 1)
    col = col * (0.55 + 0.45 * tongues[..., None])
    base = np.array([0.06, 0.01, 0.01])
    a = base + col
    img = to_img(a)
    r = np.random.default_rng(4)
    d = ImageDraw.Draw(img)
    for _ in range(26):     # embers
        x, y, s = r.uniform(0, AW), r.uniform(0, AH * 0.6), r.uniform(1.5, 3)
        d.ellipse([x - s, y - s, x + s, y + s], fill=(255, int(r.uniform(140, 220)), 60, 230))
    return img


def ocean():
    a = vgrad((18, 40, 90), (3, 14, 40))
    u, v = grid()
    moon = np.exp(-(((u - 0.68) * AW / AH) ** 2 + (v - 0.16) ** 2) / 0.0025)
    a += (moon * 0.9)[..., None] * np.array([0.95, 0.95, 1.0])
    halo = np.exp(-(((u - 0.68) * AW / AH) ** 2 + (v - 0.16) ** 2) / 0.02)
    a += (halo * 0.25)[..., None] * np.array([0.6, 0.7, 1.0])
    img = to_img(a)
    stars(img, 25, seed=8, max_r=1.2)
    d = ImageDraw.Draw(img)
    for k in range(6):     # wave rows, back to front: each one a little bigger and brighter
        y0 = AH * (0.46 + k * 0.09)
        amp, freq = 2.5 + k * 1.2, 1.5 + k * 0.25
        col = (int(10 + 8 * k), int(40 + 14 * k), int(90 + 16 * k))
        pts = [(x, y0 + amp * math.sin(x / AW * math.pi * 2 * freq + k * 1.7)) for x in range(0, AW + 1, 2)]
        d.polygon(pts + [(AW, AH), (0, AH)], fill=col + (255,))
        crest = [(x, y) for (x, y) in pts if math.sin(x / AW * math.pi * 2 * freq + k * 1.7) < -0.3]
        for (x, y) in crest:
            d.ellipse([x - 1.5, y - 1.5, x + 1.5, y + 1.5], fill=(210, 240, 255, 230))
    return img


def void():
    a = vgrad((10, 4, 20), (0, 0, 0))
    u, v = grid()
    path = 0.5 + 0.08 * np.sin(v * 13) + 0.04 * np.sin(v * 31 + 1)
    dist = np.abs(u - path) * AW / AH
    rift = np.exp(-(dist / 0.012) ** 2)
    glow = np.exp(-(dist / 0.07) ** 2)
    fade = np.clip(np.sin(v * math.pi) * 1.4, 0, 1)
    a += (glow * 0.7 * fade)[..., None] * np.array([0.6, 0.15, 1.0])
    a += (rift * fade)[..., None] * np.array([1.0, 0.85, 1.0])
    img = to_img(a)
    r = np.random.default_rng(13)
    d = ImageDraw.Draw(img)
    for _ in range(40):     # particles drifting out of the rift
        y = r.uniform(0.1, 0.9) * AH
        x = (0.5 + 0.08 * math.sin(y / AH * 13) + r.normal(0, 0.15)) * AW
        s = r.uniform(1, 2.6)
        d.rectangle([x - s, y - s, x + s, y + s], fill=(190, 120, 255, int(r.uniform(140, 255))))
    return img


def royal():
    a = vgrad((24, 30, 96), (8, 10, 40))
    u, v = grid()
    a *= (0.85 + 0.15 * np.sin(u * 50) ** 2)[..., None]      # velvet sheen
    img = to_img(a)
    d = ImageDraw.Draw(img)
    gold, dark = (255, 205, 80, 255), (170, 120, 20, 255)
    m = 10
    d.rectangle([m, m, AW - m, AH - m], outline=gold, width=5)
    d.rectangle([m + 9, m + 9, AW - m - 9, AH - m - 9], outline=dark, width=2)
    # shield crest
    cx, cy = AW / 2, AH * 0.48
    sw, sh = AW * 0.42, AH * 0.30
    shield = [(cx - sw / 2, cy - sh / 2), (cx + sw / 2, cy - sh / 2), (cx + sw / 2, cy), (cx, cy + sh / 2), (cx - sw / 2, cy)]
    d.polygon(shield, fill=(150, 20, 40, 255), outline=gold)
    d.line([(cx, cy - sh / 2), (cx, cy + sh / 2)], fill=gold, width=4)
    d.line([(cx - sw / 2, cy - sh / 8), (cx + sw / 2, cy - sh / 8)], fill=gold, width=4)
    # crown above
    ky, kw, kh = cy - sh / 2 - 26, AW * 0.34, 22
    pts = [(cx - kw / 2, ky + kh), (cx - kw / 2, ky + 4), (cx - kw / 4, ky + 14), (cx, ky), (cx + kw / 4, ky + 14), (cx + kw / 2, ky + 4), (cx + kw / 2, ky + kh)]
    d.polygon(pts, fill=gold)
    for px in (cx - kw / 2, cx, cx + kw / 2):
        d.ellipse([px - 4, ky - 2, px + 4, ky + 6], fill=(255, 235, 150, 255))
    # corner diamonds
    for (px, py) in [(m + 20, m + 20), (AW - m - 20, m + 20), (m + 20, AH - m - 20), (AW - m - 20, AH - m - 20)]:
        d.polygon([(px, py - 7), (px + 7, py), (px, py + 7), (px - 7, py)], fill=gold)
    return img


def synthwave():
    a = vgrad((40, 10, 70), (255, 90, 120))
    u, v = grid()
    horizon = 0.58
    a = np.where((v > horizon)[..., None], np.array([0.05, 0.0, 0.12]), a)
    img = to_img(a)
    d = ImageDraw.Draw(img)
    # striped sun sitting on the horizon
    cx, cy, R = AW / 2, AH * horizon - 6, AW * 0.34
    sun = Image.new("RGBA", img.size, (0, 0, 0, 0))
    sd = ImageDraw.Draw(sun)
    for yy in range(int(cy - R), int(cy) + 1):
        t = (yy - (cy - R)) / R
        col = (255, int(230 - 130 * t), int(80 + 60 * t), 255)
        half = math.sqrt(max(0.0, R * R - (yy - cy) ** 2))
        gap = (yy - (cy - R)) > R * 0.45 and int(yy) % 12 < int(2 + 6 * t)
        if not gap:
            sd.line([(cx - half, yy), (cx + half, yy)], fill=col)
    img.alpha_composite(sun)
    d = ImageDraw.Draw(img)
    # perspective grid on the ground
    hy = AH * horizon
    for k in range(-8, 9):
        d.line([(cx + k * 6, hy), (cx + k * 60, AH)], fill=(255, 60, 200, 230), width=2)
    for k in range(1, 9):
        y = hy + (AH - hy) * (k / 8) ** 1.8
        d.line([(0, y), (AW, y)], fill=(255, 60, 200, 230), width=2)
    d.line([(0, hy), (AW, hy)], fill=(255, 150, 230, 255), width=3)
    return img


CAPES = [
    # name, art, edge colour, inner-face top, inner-face bottom
    ("aurora", aurora, (60, 230, 170), (10, 20, 36), (3, 6, 14)),
    ("galaxy", galaxy, (170, 110, 255), (22, 10, 40), (6, 3, 14)),
    ("sakura", sakura, (255, 150, 190), (90, 40, 60), (40, 16, 26)),
    ("inferno", inferno, (255, 120, 30), (40, 8, 4), (12, 2, 2)),
    ("ocean", ocean, (90, 190, 255), (8, 24, 60), (2, 8, 22)),
    ("void", void, (150, 70, 255), (14, 6, 24), (2, 1, 6)),
    ("royal", royal, (255, 205, 80), (22, 26, 80), (8, 10, 34)),
    ("synthwave", synthwave, (255, 70, 200), (40, 10, 60), (14, 2, 24)),
]


def build(name, art_fn, edge, in_top, in_bot):
    W, H = 64 * S, 32 * S
    canvas = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(canvas)

    def cell(x0, y0, x1, y1, col):
        d.rectangle([x0 * S, y0 * S, x1 * S - 1, y1 * S - 1], fill=col + (255,))

    art = art_fn().resize((FW, FH), Image.LANCZOS)
    # inner face: dark gradient in the cape's colours
    for yy in range(16 * S):
        t = yy / (16 * S)
        col = tuple(int(in_top[i] + (in_bot[i] - in_top[i]) * t) for i in range(3))
        d.line([(12 * S, S + yy), (22 * S - 1, S + yy)], fill=col + (255,))
    canvas.paste(art, (1 * S, 1 * S))
    cell(1, 0, 11, 1, edge)        # top edge
    cell(11, 0, 21, 1, edge)       # bottom edge
    cell(0, 1, 1, 17, edge)        # side
    cell(11, 1, 12, 17, edge)      # side
    # elytra: the art stretched over the wing, with an edge outline
    ely = art.resize((10 * S, 20 * S), Image.LANCZOS)
    canvas.paste(ely, (24 * S, 2 * S))
    canvas.paste(ely.transpose(Image.FLIP_LEFT_RIGHT), (34 * S, 2 * S))
    d.rectangle([24 * S, 2 * S, 44 * S - 1, 22 * S - 1], outline=edge + (255,), width=S)
    canvas.save(os.path.join(OUT, name + ".png"))
    print("  cape " + name)


if __name__ == "__main__":
    print("writing to", os.path.abspath(OUT))
    for c in CAPES:
        build(*c)
