"""
Generates Tempest's cosmetic textures (original procedural art):
  src/main/resources/assets/tempest/textures/cosmetics/*.png

Wings are drawn as ONE right wing: the root (where it meets the back) is at the left edge, about a third of
the way down; the game mirrors it for the left wing. Run:  python tools/make_cosmetics.py   (numpy + Pillow)
"""
import math
import os
import sys

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

OUT = sys.argv[1] if len(sys.argv) > 1 else os.path.join(os.path.dirname(__file__), "..",
                                                        "src/main/resources/assets/tempest/textures/cosmetics")
os.makedirs(OUT, exist_ok=True)
N = 256
SS = 3


def canvas():
    return Image.new("RGBA", (N * SS, N * SS), (0, 0, 0, 0))


def P(x, y):
    return (x * N * SS, y * N * SS)


def finish(img, name, blur=0.0):
    if blur:
        img = img.filter(ImageFilter.GaussianBlur(blur * SS))
    img = img.resize((N, N), Image.LANCZOS)
    img.save(os.path.join(OUT, name + ".png"))
    print("  " + name)


def lerp(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(len(a)))


ROOT = (0.02, 0.32)


# ------------------------------------------------------------------ angel: layered white feathers
def angel():
    feathered("wings_angel", [(226, 233, 248), (240, 244, 255), (255, 255, 255)], (180, 192, 220), (170, 182, 210),
              (196, 206, 228), (255, 255, 255))


def feathered(name, row_cols, shade_to, outline, quill, bone, tip_col=None):
    img = canvas()
    d = ImageDraw.Draw(img)
    # three rows of feathers: long primaries at the bottom/outside, then secondaries, then small coverts
    rows = [
        (15, 0.22, 0.97, 0.62, 0.17, row_cols[0]),   # primaries: count, start, end along wing, length, width
        (13, 0.12, 0.85, 0.42, 0.14, row_cols[1]),
        (11, 0.04, 0.68, 0.24, 0.12, row_cols[2]),
    ]
    for count, a0, a1, length, width, col in rows:
        for i in range(count):
            t = i / (count - 1)
            # base point along the top edge of the wing (an arc rising toward the tip)
            bx = ROOT[0] + (a0 + (a1 - a0) * t) * 0.92
            by = ROOT[1] - 0.18 * math.sin(math.pi * 0.5 * (a0 + (a1 - a0) * t)) + 0.04
            ang = math.radians(98 - 62 * t)            # feathers point down, sweeping outward toward the tip
            L = length * (0.75 + 0.5 * t)
            tipx, tipy = bx + math.cos(ang) * L, by + math.sin(ang) * L
            px, py = -math.sin(ang) * width / 2, math.cos(ang) * width / 2
            shade = lerp(col, shade_to, 0.25 * (1 - t))
            poly = [P(bx + px * 0.6, by + py * 0.6), P(bx + (tipx - bx) * 0.55 + px, by + (tipy - by) * 0.55 + py),
                    P(tipx, tipy), P(bx + (tipx - bx) * 0.55 - px, by + (tipy - by) * 0.55 - py), P(bx - px * 0.6, by - py * 0.6)]
            d.polygon(poly, fill=shade + (255,), outline=outline + (255,))
            if tip_col is not None:      # bright feather tips
                tip = [P(bx + (tipx - bx) * 0.72 + px * 0.7, by + (tipy - by) * 0.72 + py * 0.7), P(tipx, tipy),
                       P(bx + (tipx - bx) * 0.72 - px * 0.7, by + (tipy - by) * 0.72 - py * 0.7)]
                d.polygon(tip, fill=tip_col + (255,))
            d.line([P(bx, by), P(tipx, tipy)], fill=quill + (255,), width=SS)
    # soft arm / bone along the top
    pts = [P(ROOT[0] + s * 0.92, ROOT[1] - 0.18 * math.sin(math.pi * 0.5 * s) + 0.02) for s in np.linspace(0, 1, 30)]
    d.line(pts, fill=bone + (255,), width=int(0.05 * N * SS), joint="curve")
    finish(img, name, 0.4)


# ------------------------------------------------------------------ phoenix: fire feathers with glowing tips
def phoenix():
    feathered("wings_phoenix", [(225, 60, 20), (245, 110, 25), (255, 170, 40)], (150, 20, 10), (120, 20, 8),
              (255, 200, 90), (255, 220, 120), tip_col=(255, 235, 120))


# ------------------------------------------------------------------ crystal: translucent ice shards
def crystal():
    img = canvas()
    d = ImageDraw.Draw(img, "RGBA")
    r = np.random.default_rng(17)
    for i in range(22):
        t = i / 21
        bx = ROOT[0] + t * 0.80
        by = ROOT[1] - 0.16 * math.sin(math.pi * 0.5 * t) + 0.03
        ang = math.radians(100 - 70 * t + r.uniform(-8, 8))
        L = (0.30 + 0.45 * math.sin(math.pi * (0.25 + 0.6 * t))) * r.uniform(0.85, 1.1)
        w = 0.05 + 0.03 * r.random()
        tipx, tipy = bx + math.cos(ang) * L, by + math.sin(ang) * L
        px, py = -math.sin(ang) * w, math.cos(ang) * w
        mid = 0.35
        shard = [P(bx, by), P(bx + (tipx - bx) * mid + px, by + (tipy - by) * mid + py), P(tipx, tipy),
                 P(bx + (tipx - bx) * mid - px, by + (tipy - by) * mid - py)]
        tint = lerp((120, 220, 255), (200, 160, 255), t)
        d.polygon(shard, fill=tint + (150,), outline=(235, 250, 255, 255))
        d.line([P(bx, by), P(tipx, tipy)], fill=(255, 255, 255, 170), width=SS)       # facet highlight
    pts = [P(ROOT[0] + s * 0.82, ROOT[1] - 0.16 * math.sin(math.pi * 0.5 * s) + 0.02) for s in np.linspace(0, 1, 30)]
    d.line(pts, fill=(210, 245, 255, 255), width=int(0.035 * N * SS), joint="curve")
    finish(img, "wings_crystal", 0.25)


# ------------------------------------------------------------------ fairy: two long translucent iridescent lobes
def fairy():
    y, x = np.mgrid[0:N * SS, 0:N * SS] / (N * SS)
    arr = np.zeros((N * SS, N * SS, 4))

    def lobe(cx, cy, rx, ry, rot):
        c, s_ = math.cos(rot), math.sin(rot)
        dx, dy = x - cx, y - cy
        u = (dx * c + dy * s_) / rx
        v = (-dx * s_ + dy * c) / ry
        return u * u + v * v
    up = lobe(0.52, 0.24, 0.50, 0.16, math.radians(-22))
    lo = lobe(0.40, 0.56, 0.40, 0.13, math.radians(20))
    inside = (up < 1) | (lo < 1)
    r = np.minimum(up, lo)
    hue = (x * 0.9 + y * 0.5) % 1.0
    col = np.stack([0.75 + 0.25 * np.cos(2 * np.pi * (hue + 0.00)),
                    0.75 + 0.25 * np.cos(2 * np.pi * (hue + 0.33)),
                    0.85 + 0.15 * np.cos(2 * np.pi * (hue + 0.66))], -1)
    edge = r > 0.86
    ang = np.arctan2(y - 0.40, x - 0.02)
    veins = (np.abs(np.sin(ang * 7)) < 0.05) & (r < 0.95)
    col = np.where((edge | veins)[..., None], np.array([1.0, 1.0, 1.0]), col)
    arr[..., :3] = col
    arr[..., 3] = np.where(inside, np.where(edge | veins, 0.95, 0.45), 0)
    img = Image.fromarray((arr * 255).astype(np.uint8), "RGBA")
    finish(img, "wings_fairy", 0.3)


# ------------------------------------------------------------------ bat-style membrane wings (demon / dragon)
def membrane(name, membrane_top, membrane_bottom, bone, edge, fingers=4, spikes=False):
    img = canvas()
    d = ImageDraw.Draw(img)
    wrist = (0.42, 0.12)
    tips = [(0.98, 0.10), (0.92, 0.46), (0.74, 0.78), (0.46, 0.92), (0.20, 0.84)][:fingers + 1]
    # membrane: between consecutive finger tips, with a scalloped (curved-in) edge
    outline = [P(*ROOT), P(*wrist)]
    for i, tp in enumerate(tips):
        outline.append(P(*tp))
        if i + 1 < len(tips):
            nx, ny = tips[i + 1]
            mx, my = (tp[0] + nx) / 2, (tp[1] + ny) / 2
            cx, cy = (wrist[0] + mx) / 2 + (mx - wrist[0]) * 0.25, (wrist[1] + my) / 2 + (my - wrist[1]) * 0.25
            for s in np.linspace(0.15, 0.85, 6):
                qx = (1 - s) ** 2 * tp[0] + 2 * (1 - s) * s * cx + s * s * nx
                qy = (1 - s) ** 2 * tp[1] + 2 * (1 - s) * s * cy + s * s * ny
                outline.append(P(qx, qy))
    outline.append(P(ROOT[0] + 0.02, ROOT[1] + 0.20))
    mask = Image.new("L", img.size, 0)
    ImageDraw.Draw(mask).polygon(outline, fill=255)
    # vertical gradient fill, a little translucent
    grad = Image.new("RGBA", img.size)
    gd = ImageDraw.Draw(grad)
    for y in range(img.size[1]):
        t = y / img.size[1]
        gd.line([(0, y), (img.size[0], y)], fill=lerp(membrane_top, membrane_bottom, t) + (235,))
    img.paste(grad, (0, 0), mask)
    d = ImageDraw.Draw(img)
    # veins inside the membrane
    for tp in tips:
        for k in (0.35, 0.65):
            vx, vy = wrist[0] + (tp[0] - wrist[0]) * k, wrist[1] + (tp[1] - wrist[1]) * k
            d.line([P(vx, vy), P(vx + (tp[1] - wrist[1]) * 0.08, vy + 0.06)], fill=edge + (120,), width=SS)
    # arm and finger bones
    d.line([P(*ROOT), P(*wrist)], fill=bone + (255,), width=int(0.045 * N * SS), joint="curve")
    for tp in tips:
        d.line([P(*wrist), P(*tp)], fill=bone + (255,), width=int(0.022 * N * SS))
    # claw at the wrist and spikes on the finger tips
    d.polygon([P(wrist[0] - 0.02, wrist[1]), P(wrist[0] - 0.05, wrist[1] - 0.09), P(wrist[0] + 0.03, wrist[1] - 0.01)], fill=edge + (255,))
    if spikes:
        for tp in tips[:-1]:
            d.polygon([P(tp[0], tp[1]), P(tp[0] + 0.035, tp[1] - 0.05), P(tp[0] + 0.02, tp[1] + 0.02)], fill=edge + (255,))
    finish(img, name, 0.3)


# ------------------------------------------------------------------ butterfly: two lobes with eye spots
def butterfly():
    img = canvas()
    y, x = np.mgrid[0:N * SS, 0:N * SS] / (N * SS)
    arr = np.zeros((N * SS, N * SS, 4))

    def lobe(cx, cy, rx, ry, rot):
        c, s = math.cos(rot), math.sin(rot)
        dx, dy = x - cx, y - cy
        u = (dx * c + dy * s) / rx
        v = (-dx * s + dy * c) / ry
        return u * u + v * v
    up = lobe(0.50, 0.30, 0.48, 0.30, math.radians(-18))
    lo = lobe(0.36, 0.70, 0.30, 0.25, math.radians(28))
    inside = (up < 1) | (lo < 1)
    r = np.minimum(up, lo)
    c1 = np.array([0.30, 0.55, 1.00]); c2 = np.array([0.85, 0.35, 1.00]); edge = np.array([0.08, 0.06, 0.18])
    t = np.clip(np.sqrt(np.clip(r, 0, 1)), 0, 1)[..., None]
    col = c1 * (1 - t) + c2 * t
    col = np.where((r > 0.80)[..., None], edge, col)
    for ex, ey, er in ((0.70, 0.26, 0.06), (0.40, 0.70, 0.05)):
        de = np.sqrt((x - ex) ** 2 + (y - ey) ** 2) / er
        col = np.where((de < 1)[..., None], np.array([1.0, 0.95, 0.6]), col)
        col = np.where((de < 0.5)[..., None], edge, col)
    # dark veins fanning out from the root
    ang = np.arctan2(y - 0.5, x - 0.02)
    veins = np.abs(np.sin(ang * 9)) < 0.06
    col = np.where((veins & (r < 0.8))[..., None], edge * 0.6 + col * 0.4, col)
    arr[..., :3] = col
    arr[..., 3] = np.where(inside, 0.88, 0)
    img = Image.fromarray((arr * 255).astype(np.uint8), "RGBA")
    finish(img, "wings_butterfly", 0.3)


# ------------------------------------------------------------------ head pieces
def halo():
    n = 128
    y, x = np.mgrid[0:n, 0:n]
    r = np.sqrt(((x + 0.5) / n * 2 - 1) ** 2 + ((y + 0.5) / n * 2 - 1) ** 2)
    core = np.exp(-((r - 0.78) / 0.06) ** 2)
    glow = 0.55 * np.exp(-((r - 0.78) / 0.18) ** 2)
    a = np.clip(core + glow, 0, 1)
    out = np.ones((n, n, 4)); out[..., 3] = a
    Image.fromarray((out * 255).astype(np.uint8), "RGBA").save(os.path.join(OUT, "halo.png"))
    print("  halo")


def horn():
    img = Image.new("RGBA", (64 * SS, 128 * SS), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    pts = []
    for s in np.linspace(0, 1, 40):
        w = 0.62 * (1 - s) ** 0.9 + 0.03
        cx = 0.5 + 0.28 * s ** 2
        pts.append((s, cx, w))
    left = [((cx - w / 2) * 64 * SS, (1 - s) * 128 * SS) for s, cx, w in pts]
    right = [((cx + w / 2) * 64 * SS, (1 - s) * 128 * SS) for s, cx, w in reversed(pts)]
    d.polygon(left + right, fill=(120, 10, 16, 255))
    for k in range(6):                       # ridges
        s = 0.12 + k * 0.13
        cx = 0.5 + 0.28 * s ** 2; w = 0.62 * (1 - s) ** 0.9 + 0.03
        d.line([((cx - w / 2) * 64 * SS, (1 - s) * 128 * SS), ((cx + w / 2) * 64 * SS, (1 - s - 0.03) * 128 * SS)],
               fill=(70, 4, 8, 255), width=2 * SS)
    img = img.resize((64, 128), Image.LANCZOS)
    arr = np.asarray(img).astype(np.float32) / 255
    yy = np.linspace(1, 0, 128)[:, None]
    arr[..., 0] *= 0.6 + 0.6 * (1 - yy) ** 0.5   # lighter toward the tip
    Image.fromarray((np.clip(arr, 0, 1) * 255).astype(np.uint8), "RGBA").save(os.path.join(OUT, "horn.png"))
    print("  horn")


def crown():
    w, h = 256, 64
    img = Image.new("RGBA", (w * SS, h * SS), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    gold, dark = (255, 205, 70), (190, 130, 20)
    pts = [(0, h)]
    for i in range(4):
        x0 = i * w / 4
        pts += [(x0, h * 0.45), (x0 + w / 8, h * 0.02), (x0 + w / 4, h * 0.45)]
    pts += [(w, h)]
    d.polygon([(px * SS, py * SS) for px, py in pts], fill=gold + (255,))
    d.rectangle([0, h * 0.70 * SS, w * SS, h * SS], fill=dark + (255,))
    for i in range(4):
        cx = (i * w / 4 + w / 8) * SS
        d.ellipse([cx - 6 * SS, h * 0.78 * SS - 6 * SS, cx + 6 * SS, h * 0.78 * SS + 6 * SS], fill=(220, 30, 60, 255))
        d.ellipse([cx - 4 * SS, h * 0.06 * SS - 4 * SS, cx + 4 * SS, h * 0.06 * SS + 4 * SS], fill=(120, 200, 255, 255))
    img.resize((w, h), Image.LANCZOS).save(os.path.join(OUT, "crown.png"))
    print("  crown")


def cat_ear():
    n = 64
    img = Image.new("RGBA", (n * SS, n * SS), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.polygon([P2(0.05, 0.98, n), P2(0.48, 0.02, n), P2(0.95, 0.98, n)], fill=(40, 40, 46, 255))
    d.polygon([P2(0.22, 0.92, n), P2(0.48, 0.30, n), P2(0.78, 0.92, n)], fill=(255, 150, 180, 255))
    img.resize((n, n), Image.LANCZOS).save(os.path.join(OUT, "cat_ear.png"))
    print("  cat_ear")


def bunny_ear():
    w, h = 64, 128
    img = Image.new("RGBA", (w * SS, h * SS), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.ellipse([w * 0.08 * SS, 0, w * 0.92 * SS, h * 1.15 * SS], fill=(245, 245, 250, 255))
    d.ellipse([w * 0.30 * SS, h * 0.10 * SS, w * 0.70 * SS, h * 1.05 * SS], fill=(255, 170, 195, 255))
    img = img.resize((w, h), Image.LANCZOS)
    img.save(os.path.join(OUT, "bunny_ear.png"))
    print("  bunny_ear")


def antler():
    n = 128
    img = Image.new("RGBA", (n * SS, n * SS), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    col, tip = (120, 82, 50, 255), (215, 195, 160, 255)
    W = int(0.07 * n * SS)

    def seg(x0, y0, x1, y1, wd):
        d.line([P2(x0, y0, n), P2(x1, y1, n)], fill=col, width=wd)
        r = wd / 2
        d.ellipse([x1 * n * SS - r, y1 * n * SS - r, x1 * n * SS + r, y1 * n * SS + r], fill=col)
    seg(0.50, 1.00, 0.46, 0.55, W)          # main beam
    seg(0.46, 0.55, 0.62, 0.08, int(W * 0.8))
    seg(0.47, 0.70, 0.18, 0.52, int(W * 0.7))   # tines
    seg(0.50, 0.40, 0.84, 0.30, int(W * 0.6))
    seg(0.56, 0.24, 0.32, 0.06, int(W * 0.55))
    for (x, y) in [(0.62, 0.08), (0.18, 0.52), (0.84, 0.30), (0.32, 0.06)]:
        d.ellipse([P2(x - 0.025, y - 0.025, n), P2(x + 0.025, y + 0.025, n)], fill=tip)
    img.resize((n, n), Image.LANCZOS).save(os.path.join(OUT, "antler.png"))
    print("  antler")


def flower_crown():
    w, h = 256, 64
    img = Image.new("RGBA", (w * SS, h * SS), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.rectangle([0, h * 0.62 * SS, w * SS, h * 0.80 * SS], fill=(60, 130, 60, 255))           # vine band
    r = np.random.default_rng(5)
    for i in range(10):                                                                           # leaves
        cx = (i + 0.5) * w / 10
        d.ellipse([(cx - 8) * SS, h * 0.52 * SS, (cx + 8) * SS, h * 0.72 * SS], fill=(80, 160, 70, 255))
    cols = [(255, 120, 160), (255, 230, 120), (180, 140, 255), (255, 255, 255), (120, 200, 255)]
    for i in range(8):                                                                            # flowers
        cx, cy, s = ((i + 0.5) * w / 8) * SS, h * 0.45 * SS, 11 * SS
        c = cols[i % len(cols)]
        for k in range(5):
            a = k * 2 * math.pi / 5
            px, py = cx + math.cos(a) * s * 0.55, cy + math.sin(a) * s * 0.55
            d.ellipse([px - s * 0.5, py - s * 0.5, px + s * 0.5, py + s * 0.5], fill=c + (255,))
        d.ellipse([cx - s * 0.25, cy - s * 0.25, cx + s * 0.25, cy + s * 0.25], fill=(255, 200, 60, 255))
    img.resize((w, h), Image.LANCZOS).save(os.path.join(OUT, "flower_crown.png"))
    print("  flower_crown")


def hat(name, top, bottom, band, buckle=None):
    """64x64 hat texture: v=0 is the tip/top, v=1 the base; the band sits just above the base."""
    n = 64
    arr = np.zeros((n, n, 4), np.float32)
    t = np.linspace(0, 1, n)[:, None]
    for i in range(3):
        arr[..., i] = (top[i] + (bottom[i] - top[i]) * t) / 255
    arr[..., 3] = 1
    arr[:, :, :3] *= (0.85 + 0.15 * np.sin(np.linspace(0, 6 * np.pi, n))[None, :, None])     # subtle folds
    b0, b1 = int(n * 0.78), int(n * 0.92)
    arr[b0:b1, :, :3] = np.array(band) / 255
    if buckle is not None:
        arr[b0 - 1:b1 + 1, 28:36, :3] = np.array(buckle) / 255
        arr[b0 + 2:b1 - 2, 30:34, :3] = np.array(band) / 255
    Image.fromarray((np.clip(arr, 0, 1) * 255).astype(np.uint8), "RGBA").save(os.path.join(OUT, name + ".png"))
    print("  " + name)


# ------------------------------------------------------------------ aura sprites (drawn by Tempest, not particles)
def _save(arr, name):
    Image.fromarray((np.clip(arr, 0, 1) * 255).astype(np.uint8), "RGBA").save(os.path.join(OUT, name + ".png"))
    print("  " + name)


def _grid(n=64):
    y, x = np.mgrid[0:n, 0:n]
    return (x + 0.5) / n * 2 - 1, (y + 0.5) / n * 2 - 1


def aura_sprites():
    n = 64
    x, y = _grid(n)
    r = np.sqrt(x * x + y * y)
    # spark: 4-point star with a soft glow (white, tinted in game)
    star = np.exp(-np.abs(x) * 9) * np.exp(-np.abs(y) * 1.6) + np.exp(-np.abs(y) * 9) * np.exp(-np.abs(x) * 1.6)
    glow = np.exp(-(r / 0.35) ** 2) * 0.6
    a = np.clip(star * 1.1 + glow, 0, 1)
    out = np.ones((n, n, 4)); out[..., 3] = a
    _save(out, "aura_spark")
    # orb: bright core, soft halo
    a = np.clip(np.exp(-(r / 0.22) ** 2) + 0.45 * np.exp(-(r / 0.6) ** 2), 0, 1)
    out = np.ones((n, n, 4)); out[..., 3] = a
    _save(out, "aura_orb")
    # ring: thin glowing circle
    a = np.clip(np.exp(-((r - 0.82) / 0.05) ** 2) + 0.5 * np.exp(-((r - 0.82) / 0.16) ** 2), 0, 1)
    out = np.ones((n, n, 4)); out[..., 3] = a
    _save(out, "aura_ring")
    # flame: teardrop, white-hot core to orange to red edge (own colours)
    yy = (y + 1) / 2                                       # 0 top .. 1 bottom
    width = 0.55 * np.sqrt(np.clip(yy, 0, 1)) * (1.15 - yy * 0.35)
    d = np.abs(x) / np.maximum(width, 1e-3)
    inside = np.clip(1.2 - d, 0, 1) * np.clip(yy * 3.0, 0, 1) * np.clip((1 - yy) * 8, 0, 1)
    heat = np.clip(1 - d, 0, 1) * np.clip(yy * 1.4, 0, 1)
    col = np.stack([np.ones_like(heat), np.clip(0.25 + heat * 0.9, 0, 1), np.clip(heat * 1.6 - 0.9, 0, 1)], -1)
    out = np.zeros((n, n, 4)); out[..., :3] = col; out[..., 3] = np.clip(inside * 1.4, 0, 1)
    _save(out, "aura_flame")
    # heart (own pink)
    hx, hy = x * 1.15, -(y * 1.15) + 0.15
    heart = (hx * hx + hy * hy - 0.5) ** 3 - hx * hx * hy ** 3 * 1.2
    inside = heart < 0
    glow = np.exp(-(r / 0.75) ** 2) * 0.25
    out = np.zeros((n, n, 4)); out[..., 0] = 1.0; out[..., 1] = 0.35 + 0.2 * (1 - (y + 1) / 2); out[..., 2] = 0.55
    out[..., 3] = np.where(inside, 1.0, glow)
    _save(out, "aura_heart")
    # snowflake (white): six arms with side branches
    img = Image.new("L", (n * SS, n * SS), 0)
    d = ImageDraw.Draw(img)
    c0 = n * SS / 2
    for k in range(6):
        ang = k * math.pi / 3
        ex, ey = c0 + math.cos(ang) * c0 * 0.85, c0 + math.sin(ang) * c0 * 0.85
        d.line([(c0, c0), (ex, ey)], fill=255, width=3 * SS)
        for t in (0.45, 0.7):
            bx, by = c0 + math.cos(ang) * c0 * 0.85 * t, c0 + math.sin(ang) * c0 * 0.85 * t
            for sg in (-1, 1):
                a2 = ang + sg * math.pi / 4
                d.line([(bx, by), (bx + math.cos(a2) * c0 * 0.22, by + math.sin(a2) * c0 * 0.22)], fill=255, width=2 * SS)
    a = np.asarray(img.resize((n, n), Image.LANCZOS), np.float32) / 255
    a = np.clip(a + np.exp(-(r / 0.5) ** 2) * 0.2, 0, 1)
    out = np.ones((n, n, 4)); out[..., 3] = a
    _save(out, "aura_snow")
    # music note (white)
    img = Image.new("L", (n * SS, n * SS), 0)
    d = ImageDraw.Draw(img)
    S = n * SS
    d.ellipse([S * 0.18, S * 0.66, S * 0.46, S * 0.88], fill=255)
    d.ellipse([S * 0.56, S * 0.58, S * 0.84, S * 0.80], fill=255)
    d.rectangle([S * 0.40, S * 0.16, S * 0.46, S * 0.78], fill=255)
    d.rectangle([S * 0.78, S * 0.10, S * 0.84, S * 0.70], fill=255)
    d.polygon([(S * 0.40, S * 0.16), (S * 0.84, S * 0.10), (S * 0.84, S * 0.22), (S * 0.40, S * 0.28)], fill=255)
    a = np.asarray(img.resize((n, n), Image.LANCZOS), np.float32) / 255
    out = np.ones((n, n, 4)); out[..., 3] = a
    _save(out, "aura_note")


def P2(x, y, n):
    return (x * n * SS, y * n * SS)


if __name__ == "__main__":
    print("writing to", os.path.abspath(OUT))
    angel()
    membrane("wings_demon", (150, 14, 24), (40, 4, 8), (30, 4, 6), (12, 0, 2), fingers=4, spikes=True)
    membrane("wings_dragon", (60, 40, 140), (20, 130, 110), (24, 20, 40), (10, 8, 20), fingers=4, spikes=True)
    butterfly()
    phoenix(); crystal(); fairy()
    halo(); horn(); crown(); cat_ear()
    bunny_ear(); antler(); flower_crown()
    hat("witch_hat", (40, 20, 70), (70, 40, 110), (150, 60, 200), buckle=(255, 205, 80))
    hat("top_hat", (18, 18, 22), (30, 30, 36), (170, 20, 40))
    aura_sprites()
