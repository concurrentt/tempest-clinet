"""
Tempest Editor icons: original, full-colour artwork (soft shading, dark outline, highlight) for the editor's main
page: one per group tile and one per shortcut button. Output: src/main/resources/assets/tempest/textures/gui/editor_icons.png (64px cells, 8 per row).
The NAMES list printed at the end goes into gui/EditorIcons.java.
Run:  python tools/make_editor_icons.py   (numpy + Pillow)
"""
import math
import os
import sys

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

CELL, SS = 64, 4
S = CELL * SS                     # drawing canvas per icon
OUTLINE = (24, 20, 34)


def hexc(h):
    h = h.lstrip("#")
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


def P(x, y):
    return (x * S / 100.0, y * S / 100.0)


class Icon:
    def __init__(self):
        self.img = Image.new("RGBA", (S, S), (0, 0, 0, 0))

    # -- core: fill a mask with a gradient, then composite
    def _fill(self, mask, c1, c2=None, angle=90, alpha=255):
        c1 = hexc(c1) if isinstance(c1, str) else c1
        c2 = c1 if c2 is None else (hexc(c2) if isinstance(c2, str) else c2)
        y, x = np.mgrid[0:S, 0:S].astype(np.float32) / S
        a = math.radians(angle)
        t = np.clip((x - 0.5) * math.cos(a) + (y - 0.5) * math.sin(a) + 0.5, 0, 1)[..., None]
        rgb = np.array(c1, np.float32) * (1 - t) + np.array(c2, np.float32) * t
        m = np.asarray(mask, np.float32)[..., None] / 255.0 * (alpha / 255.0)
        layer = np.concatenate([rgb, m * 255], -1).astype(np.uint8)
        self.img.alpha_composite(Image.fromarray(layer, "RGBA"))

    def _mask(self):
        m = Image.new("L", (S, S), 0)
        return m, ImageDraw.Draw(m)

    def poly(self, pts, c1, c2=None, angle=90, outline=True, ow=3.2, alpha=255):
        pts = [P(*p) for p in pts]
        if outline:
            m, d = self._mask()
            d.polygon(pts, fill=255)
            d.line(pts + [pts[0]], fill=255, width=int(ow * S / 100), joint="curve")
            self._fill(m, OUTLINE)
        m, d = self._mask()
        d.polygon(pts, fill=255)
        self._fill(m, c1, c2, angle, alpha)

    def ellipse(self, cx, cy, rx, ry, c1, c2=None, angle=90, outline=True, ow=3.2, alpha=255):
        if outline:
            m, d = self._mask()
            o = ow / 2
            d.ellipse([*P(cx - rx - o, cy - ry - o), *P(cx + rx + o, cy + ry + o)], fill=255)
            self._fill(m, OUTLINE)
        m, d = self._mask()
        d.ellipse([*P(cx - rx, cy - ry), *P(cx + rx, cy + ry)], fill=255)
        self._fill(m, c1, c2, angle, alpha)

    def rrect(self, x0, y0, x1, y1, r, c1, c2=None, angle=90, outline=True, ow=3.2, alpha=255):
        if outline:
            m, d = self._mask()
            o = ow / 2
            d.rounded_rectangle([*P(x0 - o, y0 - o), *P(x1 + o, y1 + o)], radius=(r + o) * S / 100, fill=255)
            self._fill(m, OUTLINE)
        m, d = self._mask()
        d.rounded_rectangle([*P(x0, y0), *P(x1, y1)], radius=r * S / 100, fill=255)
        self._fill(m, c1, c2, angle, alpha)

    def line(self, pts, w, color, outline=True, ow=3.2, alpha=255):
        pts = [P(*p) for p in pts]

        def stroke(width, col, al):
            m, d = self._mask()
            wpx = int(width * S / 100)
            d.line(pts, fill=255, width=wpx, joint="curve")
            for p in pts:
                d.ellipse([p[0] - wpx / 2, p[1] - wpx / 2, p[0] + wpx / 2, p[1] + wpx / 2], fill=255)
            self._fill(m, col, alpha=al)
        if outline:
            stroke(w + ow, OUTLINE, 255)
        stroke(w, color, alpha)

    def arc(self, cx, cy, r, a0, a1, w, color, outline=True, steps=40, alpha=255):
        pts = [(cx + r * math.cos(math.radians(a0 + (a1 - a0) * i / steps)),
                cy + r * math.sin(math.radians(a0 + (a1 - a0) * i / steps))) for i in range(steps + 1)]
        self.line(pts, w, color, outline, alpha=alpha)

    def glow(self, cx, cy, r, color, strength=0.8):
        y, x = np.mgrid[0:S, 0:S].astype(np.float32) / S * 100
        d = np.sqrt((x - cx) ** 2 + (y - cy) ** 2) / r
        a = np.clip(np.exp(-d * d * 2.2) * strength, 0, 1)
        c = np.array(hexc(color), np.float32)
        layer = np.dstack([np.broadcast_to(c, (S, S, 3)), a[..., None] * 255]).astype(np.uint8)
        under = Image.fromarray(layer, "RGBA")
        under.alpha_composite(self.img)
        self.img = under

    def shine(self, pts, alpha=90):
        """A soft white highlight shape (no outline)."""
        m, d = self._mask()
        d.polygon([P(*p) for p in pts], fill=255)
        m = m.filter(ImageFilter.GaussianBlur(S / 160))
        self._fill(m, (255, 255, 255), alpha=alpha)

    def sparkle(self, cx, cy, r, color="#ffffff", alpha=255):
        k = r * 0.26
        pts = [(cx, cy - r), (cx + k, cy - k), (cx + r, cy), (cx + k, cy + k), (cx, cy + r), (cx - k, cy + k), (cx - r, cy), (cx - k, cy - k)]
        self.poly(pts, color, outline=False, alpha=alpha)

    def paste(self, other, cx, cy, size):
        """Draws another finished icon, scaled, centred on (cx, cy)."""
        im = other.img.resize((int(size * S / 100), int(size * S / 100)), Image.LANCZOS)
        self.img.alpha_composite(im, (int(cx * S / 100 - im.width / 2), int(cy * S / 100 - im.height / 2)))

    def done(self):
        # a soft contact shadow under everything, then downsample
        a = np.asarray(self.img)[..., 3].astype(np.float32)
        sh = Image.fromarray(a.astype(np.uint8)).filter(ImageFilter.GaussianBlur(S / 70))
        shadow = Image.new("RGBA", (S, S), (0, 0, 0, 0))
        shadow.putalpha(sh.point(lambda v: int(v * 0.35)))
        base = Image.new("RGBA", (S, S), (0, 0, 0, 0))
        base.alpha_composite(shadow, (0, int(S * 0.025)))
        base.alpha_composite(self.img)
        return base.resize((CELL, CELL), Image.LANCZOS)


# ============================================================================ building blocks
def cube(ic, top, left, right, speck=None, seed=1, ore=None):
    """Isometric block. ore: colour of gem spots."""
    t = [(50, 12), (88, 31), (50, 50), (12, 31)]
    l = [(12, 31), (50, 50), (50, 92), (12, 72)]
    r = [(50, 50), (88, 31), (88, 72), (50, 92)]
    ic.poly(t + [], top[0], top[1], 120)
    ic.poly(l, left[0], left[1], 90)
    ic.poly(r, right[0], right[1], 90)
    rng = np.random.default_rng(seed)
    if speck:
        for face, (ox, oy) in ((l, (0, 0)), (r, (0, 0))):
            for _ in range(7):
                u, v = rng.uniform(0.15, 0.85), rng.uniform(0.15, 0.85)
                (x0, y0), (x1, y1), (x2, y2), (x3, y3) = face
                px = x0 + (x1 - x0) * u + (x3 - x0) * v
                py = y0 + (y1 - y0) * u + (y3 - y0) * v
                s = rng.uniform(2.2, 3.8)
                ic.poly([(px - s, py - s * 0.6), (px + s, py - s * 0.2), (px + s * 0.6, py + s * 0.8), (px - s * 0.8, py + s * 0.5)],
                        speck, outline=False, alpha=200)
    if ore:
        for (px, py) in ((30, 55), (24, 72), (40, 74), (64, 60), (74, 52), (70, 76)):
            ic.poly([(px, py - 4), (px + 4, py), (px, py + 4), (px - 4, py)], ore, outline=True, ow=1.6)
    ic.shine([(50, 15), (82, 31), (74, 35), (50, 22)], 70)


def chestplate(ic, c1, c2, trim):
    # body: wide chest narrowing to the waist, a V at the neck
    body = [(30, 30), (41, 25), (50, 36), (59, 25), (70, 30), (72, 58), (66, 90), (34, 90), (28, 58)]
    ic.poly(body, c1, c2, 90)
    ic.poly([(41, 25), (50, 36), (59, 25), (55, 25), (50, 31), (45, 25)], OUTLINE, outline=False, alpha=120)
    ic.line([(36, 66), (64, 66)], 2.4, trim, outline=False, alpha=140)        # belt line
    ic.line([(50, 38), (50, 64)], 2.0, trim, outline=False, alpha=110)        # centre seam
    # shoulder plates over the arms
    for sgn in (-1, 1):
        pad = [(50 + sgn * 9, 23), (50 + sgn * 22, 18), (50 + sgn * 40, 26), (50 + sgn * 42, 44), (50 + sgn * 30, 48), (50 + sgn * 22, 32)]
        ic.poly(pad, c1, c2, 90 if sgn < 0 else 90)
        ic.shine([(50 + sgn * 12, 23), (50 + sgn * 22, 20), (50 + sgn * 30, 24), (50 + sgn * 22, 26)], 110)
    ic.shine([(32, 34), (40, 30), (38, 56), (33, 56)], 80)


def sword(ic, blade1, blade2, hilt="#6b4423", guard="#e8b64a", angle=45, scale=1.0):
    a = math.radians(angle)
    c, s = math.cos(a), math.sin(a)

    def T(x, y):     # sword drawn vertically then rotated about the centre
        x, y = (x - 50) * scale, (y - 50) * scale
        return (50 + x * c - y * s, 50 + x * s + y * c)
    ic.poly([T(50, 2), T(58, 12), T(58, 64), T(42, 64), T(42, 12)], blade1, blade2, 0)
    ic.line([T(50, 10), T(50, 60)], 2, "#ffffff", outline=False, alpha=110)
    ic.line([T(34, 67), T(66, 67)], 7, guard)
    ic.line([T(50, 70), T(50, 88)], 7, hilt)
    ic.ellipse(*T(50, 93), 5, 5, guard)


def heart(ic, cx, cy, size, c1, c2):
    pts = []
    for i in range(64):
        t = i / 64 * 2 * math.pi
        x = 16 * math.sin(t) ** 3
        y = -(13 * math.cos(t) - 5 * math.cos(2 * t) - 2 * math.cos(3 * t) - math.cos(4 * t))
        pts.append((cx + x * size / 34, cy + y * size / 34))
    ic.poly(pts, c1, c2, 90)
    ic.ellipse(cx - size * 0.22, cy - size * 0.18, size * 0.1, size * 0.07, "#ffffff", outline=False, alpha=150)


def speaker(ic, color, color2):
    ic.poly([(14, 40), (30, 40), (50, 22), (50, 78), (30, 60), (14, 60)], color, color2, 90)
    ic.arc(54, 50, 14, -50, 50, 5, color)
    ic.arc(54, 50, 26, -50, 50, 5, color)


# ============================================================================ icons
def g_pvp(ic):
    ic.glow(50, 50, 40, "#ff4d6d", 0.35)
    sword(ic, "#e8eef8", "#9aa8c0", angle=40)
    sword(ic, "#e8eef8", "#9aa8c0", angle=-40)


def g_blocks(ic):
    cube(ic, ("#7bd651", "#4fa83a"), ("#a0714a", "#6d4a2e"), ("#7d5636", "#54371f"), speck="#5b3d22", seed=3)


def g_gear(ic):
    ic.glow(50, 50, 42, "#5ee6ff", 0.3)
    chestplate(ic, "#7ff0ff", "#2aa6c8", "#e8ffff")


def g_hud(ic):
    ic.rrect(8, 18, 92, 82, 10, "#2b2f44", "#171a28", 90)
    heart(ic, 32, 46, 30, "#ff5c6c", "#c41e3a")
    ic.rrect(54, 36, 84, 44, 3, "#ffd34d", "#e09b1a", outline=False)
    ic.rrect(54, 52, 76, 60, 3, "#6af09a", "#2fa866", outline=False)
    ic.rrect(16, 68, 84, 74, 3, "#8a5cff", "#5c34d6", outline=False)


def g_misc(ic):
    ic.glow(50, 50, 42, "#b06cff", 0.35)
    ic.poly([(18, 22), (50, 16), (82, 22), (82, 84), (50, 78), (18, 84)], "#8a3fe0", "#4b1d8a", 90)
    ic.poly([(50, 16), (50, 78)], "#2a1050", outline=False)
    ic.line([(50, 18), (50, 78)], 3, "#2a1050", outline=False)
    ic.poly([(22, 26), (48, 21), (48, 74), (22, 79)], "#f4ecd8", "#d8c9a6", 90, outline=False, alpha=60)
    ic.sparkle(34, 44, 10, "#fff6c8")
    ic.sparkle(66, 56, 7, "#e6ccff")
    ic.sparkle(70, 32, 5, "#ffffff")


def g_effects(ic):
    ic.glow(50, 50, 44, "#ffb347", 0.5)
    cols = ["#ff5c6c", "#ffd34d", "#6af09a", "#5ab4ff", "#c78bff", "#ff8ad8"]
    for i in range(12):
        a = math.radians(i * 30)
        col = cols[i % len(cols)]
        ic.line([(50 + 12 * math.cos(a), 50 + 12 * math.sin(a)), (50 + 38 * math.cos(a), 50 + 38 * math.sin(a))], 4, col, ow=2.2)
        ic.ellipse(50 + 42 * math.cos(a), 50 + 42 * math.sin(a), 3.4, 3.4, col, ow=1.8)
    ic.sparkle(50, 50, 13, "#ffffff")


def g_sounds(ic):
    ic.glow(48, 50, 42, "#5ab4ff", 0.3)
    speaker(ic, "#7cc4ff", "#2f6fe0")


# ---- PvP
def c_end_crystal(ic):
    ic.glow(50, 50, 40, "#ff6ad5", 0.6)
    ic.poly([(50, 6), (94, 50), (50, 94), (6, 50)], "#bfe6ff", "#7aa7d6", 135, alpha=110)
    ic.poly([(50, 18), (82, 50), (50, 82), (18, 50)], "#ffffff", "#bcd8f2", 135, alpha=90, ow=2.4)
    ic.poly([(50, 30), (70, 50), (50, 70), (30, 50)], "#ff8ae0", "#c21f9a", 135)
    ic.shine([(50, 32), (66, 48), (60, 50), (50, 40)], 120)


def c_respawn_anchor(ic):
    cube(ic, ("#3b2a58", "#22163a"), ("#2a1d42", "#160e26"), ("#1f1533", "#0f0a1c"))
    ic.glow(50, 31, 18, "#ff9a3d", 0.9)
    ic.poly([(50, 22), (64, 31), (50, 40), (36, 31)], "#ffd27a", "#ff7a1a", 90, ow=2.2)
    for x, y in ((30, 62), (70, 62)):
        ic.ellipse(x, y, 5, 7, "#c06cff", "#6a2bb0", ow=2)


def c_totem(ic):
    ic.glow(50, 50, 40, "#ffe066", 0.45)
    ic.rrect(30, 10, 70, 46, 6, "#ffe066", "#d99a1a")
    ic.rrect(36, 22, 46, 30, 2, "#2fd17a", outline=False)
    ic.rrect(54, 22, 64, 30, 2, "#2fd17a", outline=False)
    ic.rrect(40, 36, 60, 40, 1, "#8a5a10", outline=False)
    ic.poly([(14, 52), (86, 52), (78, 62), (22, 62)], "#ffd34d", "#c98a12", 90)
    ic.rrect(36, 50, 64, 90, 5, "#ffd34d", "#b8780e")
    ic.rrect(44, 62, 56, 74, 2, "#2fd17a", "#178a4a", ow=2)
    ic.shine([(33, 13), (44, 13), (36, 30)], 110)


def c_ender_pearl(ic):
    ic.glow(50, 50, 40, "#3fe0c5", 0.5)
    ic.ellipse(50, 52, 34, 34, "#3fd9b8", "#0d5c4c", 120)
    ic.ellipse(50, 52, 18, 18, "#0b3f36", "#072a24", 120, outline=False)
    ic.ellipse(50, 52, 8, 8, "#1fa58a", outline=False)
    ic.ellipse(38, 38, 9, 6, "#ffffff", outline=False, alpha=150)


def c_golden_apple(ic):
    ic.glow(50, 54, 40, "#ffd84d", 0.5)
    ic.ellipse(50, 58, 34, 32, "#ffe680", "#d99a1a", 120)
    ic.poly([(48, 14), (54, 14), (54, 30), (48, 30)], "#6b4423")
    ic.poly([(54, 22), (74, 12), (80, 22), (60, 30)], "#7bd651", "#3f9a2e", 90)
    ic.ellipse(36, 46, 9, 12, "#ffffff", outline=False, alpha=150)


def c_shield(ic):
    pts = [(18, 12), (82, 12), (82, 50), (50, 92), (18, 50)]
    ic.poly(pts, "#c0c8d6", "#7a8498", 90)
    ic.poly([(26, 20), (74, 20), (74, 48), (50, 82), (26, 48)], "#a06a3a", "#6b4423", 90, ow=2)
    ic.line([(50, 22), (50, 80)], 3, "#c0c8d6", outline=False)
    ic.shine([(28, 22), (40, 22), (30, 46)], 70)


def c_bows(ic):
    ic.arc(30, 50, 40, -62, 62, 6, "#a06a3a")
    ic.line([(48.8, 14.7), (48.8, 85.3)], 1.6, "#f0f0f0", outline=False)
    ic.line([(16, 50), (84, 50)], 3.4, "#d9c39a")
    ic.poly([(84, 44), (96, 50), (84, 56)], "#c0c8d6", "#7a8498", 0, ow=2)
    ic.poly([(16, 44), (24, 50), (16, 56), (10, 50)], "#ff5c6c", outline=True, ow=1.8)


# ---- Blocks
def c_obsidian(ic):
    cube(ic, ("#3a2a5a", "#1c1230"), ("#241838", "#120b20"), ("#1a1129", "#0b0714"), speck="#5b3fa0", seed=7)


def c_glowstone(ic):
    ic.glow(50, 52, 46, "#ffd34d", 0.55)
    cube(ic, ("#ffe680", "#e0a830"), ("#e8b048", "#a8721e"), ("#c99032", "#8a5a14"), speck="#fff6c8", seed=9)


def c_cobweb(ic):
    for i in range(8):
        a = math.radians(i * 45 + 22)
        ic.line([(50, 50), (50 + 44 * math.cos(a), 50 + 44 * math.sin(a))], 2.4, "#f4f6fa", ow=1.6)
    for r in (12, 24, 36):
        pts = [(50 + r * math.cos(math.radians(i * 45 + 22)), 50 + r * math.sin(math.radians(i * 45 + 22))) for i in range(9)]
        ic.line(pts, 2.2, "#e6e9f0", ow=1.6)


def c_fire(ic):
    ic.glow(50, 60, 40, "#ff7a1a", 0.6)
    outer = [(50, 6), (66, 30), (74, 22), (86, 54), (78, 84), (50, 94), (22, 84), (14, 56), (28, 30), (36, 40)]
    ic.poly(outer, "#ffd34d", "#ff3d1a", 90)
    inner = [(50, 40), (60, 58), (64, 54), (68, 72), (60, 86), (40, 86), (32, 72), (38, 58), (44, 62)]
    ic.poly(inner, "#fff6c8", "#ffb347", 90, outline=False)


def c_bedrock(ic):
    cube(ic, ("#7a7f8c", "#4a4e58"), ("#5c606b", "#33363d"), ("#474a53", "#25272d"), speck="#1a1b1f", seed=11)


def c_ores(ic):
    cube(ic, ("#a8adb8", "#7d828e"), ("#8d929e", "#5f6470"), ("#757a86", "#4a4e58"), ore="#5ee6ff")


# ---- Gear
def c_netherite_armor(ic):
    ic.glow(50, 50, 40, "#9a8fb0", 0.25)
    chestplate(ic, "#7a7184", "#3a3540", "#b2a6c0")


def c_diamond_armor(ic):
    ic.glow(50, 50, 40, "#5ee6ff", 0.3)
    chestplate(ic, "#7ff0ff", "#2aa6c8", "#e8ffff")


def c_elytra(ic):
    for sgn in (-1, 1):
        pts = [(50 + sgn * 3, 18), (50 + sgn * 34, 8), (50 + sgn * 44, 30), (50 + sgn * 38, 60), (50 + sgn * 26, 92), (50 + sgn * 16, 80), (50 + sgn * 8, 58)]
        ic.poly(pts, "#c9b8e6", "#6a5a8a", 90)
        for k in range(3):     # feather lines
            ic.line([(50 + sgn * (10 + k * 7), 24 + k * 2), (50 + sgn * (18 + k * 6), 72 - k * 10)], 1.4, "#ece4fa", outline=False, alpha=150)
        ic.shine([(50 + sgn * 6, 20), (50 + sgn * 30, 12), (50 + sgn * 22, 22)], 100)


def c_swords(ic):
    ic.glow(50, 50, 40, "#b8a8ff", 0.3)
    sword(ic, "#a89cb8", "#57505f", guard="#d4c8e2", hilt="#4a3524")


def c_axes(ic):
    ic.line([(26, 92), (62, 24)], 6.5, "#a06a3a")
    blade = [(52, 16), (66, 8), (84, 12), (94, 30), (90, 50), (74, 46), (62, 32)]
    ic.poly(blade, "#b0a8bc", "#57505f", 120)
    ic.poly([(52, 16), (44, 10), (40, 22), (50, 28)], "#8a8094", "#4a4452", 120, ow=2.4)   # back spike
    ic.line([(70, 12), (90, 46)], 2.2, "#ffffff", outline=False, alpha=120)                # sharpened edge


def c_pickaxes(ic):
    ic.line([(26, 90), (66, 30)], 6, "#8a5a2e")
    ic.arc(60, 46, 38, 205, 335, 9, "#7ff0ff")
    ic.arc(60, 46, 38, 215, 325, 3, "#e8ffff", outline=False, alpha=150)


# ---- HUD
def c_crosshair(ic):
    ic.arc(50, 50, 30, 0, 360, 5, "#ff5c6c", steps=80)
    for (x0, y0, x1, y1) in ((50, 6, 50, 34), (50, 66, 50, 94), (6, 50, 34, 50), (66, 50, 94, 50)):
        ic.line([(x0, y0), (x1, y1)], 6, "#ffffff")
    ic.ellipse(50, 50, 4, 4, "#ff5c6c")


def c_gui(ic):
    ic.rrect(8, 14, 92, 86, 6, "#d8dce6", "#a8aebb", 90)
    for r in range(3):
        for col in range(5):
            x, y = 16 + col * 15, 26 + r * 16
            ic.rrect(x, y, x + 12, y + 12, 1.5, "#5a5f6b", "#454a55", outline=False)
    ic.rrect(46, 26, 58, 38, 1.5, "#ff5c6c", outline=False)
    ic.rrect(16, 74, 84, 80, 2, "#8b909c", outline=False)


def c_hearts(ic):
    ic.glow(50, 52, 40, "#ff4d6d", 0.4)
    heart(ic, 50, 54, 76, "#ff6b7a", "#c41e3a")


def c_hunger(ic):
    ic.line([(66, 34), (86, 14)], 9, "#f4ead6")
    ic.ellipse(88, 12, 6, 6, "#f4ead6", ow=2.4)
    ic.ellipse(40, 60, 30, 26, "#d98a4a", "#8a4a1e", 120)
    ic.ellipse(32, 50, 10, 6, "#ffffff", outline=False, alpha=120)


def c_hotbar(ic):
    ic.rrect(4, 34, 96, 66, 4, "#3a3e4c", "#22252f", 90)
    for i in range(5):
        x = 9 + i * 17.6
        ic.rrect(x, 39, x + 14, 61, 2, "#5a5f6b", "#454a55", outline=False)
    ic.rrect(41.4, 35, 59.6, 65, 3, "#ffffff", outline=False, alpha=70)
    ic.rrect(43, 37, 58, 63, 3, "#6af09a", outline=False, alpha=60)
    ic.ellipse(50.5, 50, 4, 4, "#ffd34d", ow=1.6)


def c_xp_bar(ic):
    ic.rrect(6, 54, 94, 70, 5, "#1f2a1f", "#141a14", 90)
    ic.rrect(8, 56, 66, 68, 4, "#b6ff5c", "#4fd12e", 90, outline=False)
    ic.ellipse(50, 30, 16, 16, "#c6ff7a", "#4fd12e", 120)
    ic.line([(45, 30), (55, 30)], 3, "#1c3a12", outline=False)
    ic.line([(50, 25), (50, 35)], 3, "#1c3a12", outline=False)


# ---- Misc
def c_glint(ic):
    ic.glow(50, 50, 44, "#b06cff", 0.5)
    sword(ic, "#c9b6ff", "#7a4fe0", guard="#e6ccff", hilt="#4b2a7a")
    for i, (x, y) in enumerate(((24, 26), (74, 30), (30, 74), (72, 70))):
        ic.sparkle(x, y, 7 - i % 2 * 2, "#ffffff")


def c_arrows(ic):
    ic.line([(16, 84), (78, 22)], 3.6, "#d9c39a")
    ic.poly([(72, 16), (92, 8), (84, 28)], "#c0c8d6", "#7a8498", 0, ow=2)
    ic.poly([(14, 74), (26, 86), (12, 92), (8, 80)], "#ffffff", "#c8ccd6", 0, ow=2)


def c_xp_orbs(ic):
    ic.glow(50, 50, 42, "#b6ff5c", 0.6)
    ic.ellipse(50, 50, 28, 28, "#e8ff8a", "#4fd12e", 120)
    ic.ellipse(50, 50, 14, 14, "#ffffff", "#c6ff7a", 120, outline=False, alpha=150)
    ic.sparkle(74, 26, 7)


# ---- Effects
def c_crit(ic):
    ic.glow(50, 50, 40, "#ffd34d", 0.4)
    ic.sparkle(50, 50, 30, "#ffffff")
    ic.sparkle(22, 26, 12, "#ffd34d")
    ic.sparkle(78, 74, 10, "#ffd34d")
    ic.sparkle(76, 22, 7, "#c78bff")


def c_totem_particles(ic):
    ic.glow(50, 50, 44, "#ffe066", 0.4)
    for (x, y, r, c) in ((50, 48, 18, "#ffe066"), (22, 30, 9, "#6af09a"), (78, 32, 10, "#ffd34d"), (30, 76, 8, "#ffd34d"),
                         (74, 74, 11, "#6af09a"), (52, 14, 6, "#ffffff")):
        ic.sparkle(x, y, r, c)


def c_explosion(ic):
    ic.glow(50, 50, 44, "#ff7a1a", 0.6)
    for (x, y, r) in ((40, 56, 22), (62, 52, 20), (50, 36, 18), (30, 40, 12), (72, 34, 12)):
        ic.ellipse(x, y, r, r, "#d8d8d8", "#7a7a7a", 120, ow=2.6)
    ic.ellipse(50, 54, 14, 12, "#ffd34d", "#ff5c1a", 120, outline=False)


def c_sweep(ic):
    ic.arc(50, 64, 42, 200, 340, 10, "#f4f6fa")
    ic.arc(50, 64, 42, 210, 330, 4, "#ffffff", outline=False, alpha=160)
    ic.arc(50, 64, 28, 215, 325, 4, "#c9d2e6", outline=False, alpha=150)


def c_potion_icons(ic):
    ic.glow(50, 60, 38, "#ff5c6c", 0.4)
    ic.rrect(42, 8, 58, 22, 2, "#a06a3a", "#6b4423")
    ic.rrect(44, 20, 56, 34, 1, "#e6f0ff", "#b8c8e0", alpha=200)
    ic.ellipse(50, 62, 30, 30, "#e6f0ff", "#b8c8e0", 120, alpha=210)
    ic.ellipse(50, 66, 25, 22, "#ff6b7a", "#c41e3a", 120, outline=False)
    ic.ellipse(40, 54, 6, 8, "#ffffff", outline=False, alpha=150)


# ---- Sounds: a speaker with a little badge of what it's for
SOUND_BADGES = {}


def sound_icon(badge_fn):
    def f(ic):
        speaker(ic, "#7cc4ff", "#2f6fe0")
        b = Icon()
        badge_fn(b)
        ic.ellipse(74, 74, 22, 22, "#22263a", "#12141e", outline=True, ow=2.6)
        ic.paste(b, 74, 74, 38)
    return f


# ---- shortcuts
def s_configs(ic):
    for i in range(8):
        a = math.radians(i * 45)
        ic.rrect(50 + 32 * math.cos(a) - 7, 50 + 32 * math.sin(a) - 7, 50 + 32 * math.cos(a) + 7, 50 + 32 * math.sin(a) + 7, 2,
                 "#c0c8d6", "#7a8498", outline=True, ow=2.4)
    ic.ellipse(50, 50, 30, 30, "#c0c8d6", "#7a8498", 120)
    ic.ellipse(50, 50, 12, 12, "#2b2f44", outline=True, ow=2.4)


def s_packs(ic):
    for i, col in enumerate((("#5ab4ff", "#2f6fe0"), ("#6af09a", "#2fa866"), ("#ffd34d", "#e09b1a"))):
        o = i * 10
        ic.rrect(14 + o, 14 + o, 66 + o, 66 + o, 6, col[0], col[1], 120)
    ic.sparkle(60, 60, 8)


def s_cosmetics(ic):
    for sgn in (-1, 1):
        pts = [(50, 50), (50 + sgn * 40, 20), (50 + sgn * 44, 34), (50 + sgn * 36, 50), (50 + sgn * 40, 62), (50 + sgn * 22, 76)]
        ic.poly(pts, "#ffffff", "#b9c4e6", 90)
    ic.ellipse(50, 50, 8, 8, "#ffd34d", ow=2.4)


def s_painter(ic):
    ic.ellipse(44, 54, 36, 30, "#f4ead6", "#d8c9a6", 120)
    for (x, y, c) in ((30, 44, "#ff5c6c"), (44, 36, "#ffd34d"), (60, 42, "#6af09a"), (32, 62, "#5ab4ff")):
        ic.ellipse(x, y, 6, 6, c, ow=2)
    ic.ellipse(54, 64, 7, 6, "#2b2f44", outline=False)
    ic.line([(62, 70), (90, 12)], 5, "#a06a3a")
    ic.poly([(58, 64), (66, 68), (62, 82), (54, 80)], "#c41e3a", outline=True, ow=2)


GLYPHS = [
    ("group_pvp", g_pvp), ("group_blocks", g_blocks), ("group_gear", g_gear), ("group_hud", g_hud),
    ("group_misc", g_misc), ("group_effects", g_effects), ("group_sounds", g_sounds), ("short_configs", s_configs),
    ("short_packs", s_packs), ("short_cosmetics", s_cosmetics), ("short_painter", s_painter),
]


def main():
    out = sys.argv[1] if len(sys.argv) > 1 else os.path.join(os.path.dirname(__file__), "..",
                                                            "src/main/resources/assets/tempest/textures/gui/editor_icons.png")
    cols = 8
    rows = (len(GLYPHS) + cols - 1) // cols
    atlas = Image.new("RGBA", (cols * CELL, rows * CELL), (0, 0, 0, 0))
    for i, (name, fn) in enumerate(GLYPHS):
        ic = Icon()
        fn(ic)
        atlas.alpha_composite(ic.done(), ((i % cols) * CELL, (i // cols) * CELL))
    atlas.save(out, optimize=True)
    print("wrote %s (%d icons, %dx%d)" % (out, len(GLYPHS), atlas.width, atlas.height))
    print("NAMES = {" + ", ".join('"%s"' % n for n, _ in GLYPHS) + "};")


if __name__ == "__main__":
    main()
