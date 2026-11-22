"""
Generates Tempest's UI icon atlas: src/main/resources/assets/tempest/textures/gui/icons.png

Every icon is an original white line glyph drawn here from simple shapes, on a 64x64 cell
(drawn at 4x and downsampled for smooth edges). The Java side (gui/Icons.java) refers to icons
by name; the order of GLYPHS below is the atlas order, 8 icons per row.

Run:  python tools/make_icons.py      (needs Pillow)
After adding an icon here, add its name to Icons.NAMES in the same position.
"""
import math
import os
import sys
from PIL import Image, ImageDraw

CELL = 64
SS = 4                     # supersampling
S = CELL * SS              # 256 px working canvas per icon
W = 7.0                    # default stroke width, in 0..100 units
COLS = 8


class Pen:
    """Draws in a 0..100 coordinate space with round-capped strokes."""

    def __init__(self):
        self.img = Image.new("L", (S, S), 0)
        self.d = ImageDraw.Draw(self.img)

    def p(self, x, y):
        return (x * S / 100.0, y * S / 100.0)

    def w(self, w):
        return max(1, int(round(w * S / 100.0)))

    def cap(self, x, y, w):
        r = w * S / 200.0
        cx, cy = self.p(x, y)
        self.d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=255)

    def line(self, pts, w=W, closed=False):
        if closed:
            pts = list(pts) + [pts[0]]
        self.d.line([self.p(x, y) for x, y in pts], fill=255, width=self.w(w), joint="curve")
        for x, y in pts:
            self.cap(x, y, w)

    def circle(self, cx, cy, r, w=W):
        self.arc(cx, cy, r, 0, 360, w)

    def arc(self, cx, cy, r, a0, a1, w=W, steps=None):
        steps = steps or max(8, int(abs(a1 - a0) / 6))
        pts = [(cx + r * math.cos(math.radians(a0 + (a1 - a0) * i / steps)),
                cy + r * math.sin(math.radians(a0 + (a1 - a0) * i / steps))) for i in range(steps + 1)]
        self.line(pts, w)

    def disc(self, cx, cy, r):
        a, b = self.p(cx - r, cy - r), self.p(cx + r, cy + r)
        self.d.ellipse([a[0], a[1], b[0], b[1]], fill=255)

    def hole(self, cx, cy, r):
        a, b = self.p(cx - r, cy - r), self.p(cx + r, cy + r)
        self.d.ellipse([a[0], a[1], b[0], b[1]], fill=0)

    def poly(self, pts, fill=255):
        self.d.polygon([self.p(x, y) for x, y in pts], fill=fill)

    def rrect(self, x0, y0, x1, y1, r, w=W):
        a, b = self.p(x0, y0), self.p(x1, y1)
        self.d.rounded_rectangle([a[0], a[1], b[0], b[1]], radius=r * S / 100.0, outline=255, width=self.w(w))

    def frect(self, x0, y0, x1, y1, r=0, fill=255):
        a, b = self.p(x0, y0), self.p(x1, y1)
        self.d.rounded_rectangle([a[0], a[1], b[0], b[1]], radius=r * S / 100.0, fill=fill)

    def star(self, cx, cy, ro, ri, filled, w=W, rot=-90):
        pts = []
        for i in range(10):
            r = ro if i % 2 == 0 else ri
            a = math.radians(rot + i * 36)
            pts.append((cx + r * math.cos(a), cy + r * math.sin(a)))
        if filled:
            self.poly(pts)
        else:
            self.line(pts, w, closed=True)

    def sparkle(self, cx, cy, r):
        k = r * 0.28
        self.poly([(cx, cy - r), (cx + k, cy - k), (cx + r, cy), (cx + k, cy + k),
                   (cx, cy + r), (cx - k, cy + k), (cx - r, cy), (cx - k, cy - k)])


# ---------------------------------------------------------------------------- glyphs
def g_star_outline(p): p.star(50, 52, 40, 17, False, 6)
def g_star_filled(p): p.star(50, 52, 40, 17, True)


def g_gear(p):
    for i in range(8):
        a = i * math.pi / 4
        ca, sa = math.cos(a), math.sin(a)
        # a tooth: a short wide bar sticking out along angle a
        pts = []
        for dx, dy in ((26, -8), (42, -7), (42, 7), (26, 8)):
            pts.append((50 + dx * ca - dy * sa, 50 + dx * sa + dy * ca))
        p.poly(pts)
    p.disc(50, 50, 31)
    p.hole(50, 50, 13)

def g_person(p):
    p.disc(50, 30, 15)
    p.d.pieslice([*p.p(20, 52), *p.p(80, 112)], 180, 360, fill=255)


def g_people(p):
    p.disc(38, 32, 13)
    p.d.pieslice([*p.p(12, 52), *p.p(64, 104)], 180, 360, fill=255)
    p.disc(68, 28, 11)
    p.hole(54, 46, 1)
    p.d.pieslice([*p.p(52, 44), *p.p(90, 86)], 180, 360, fill=255)
    # gap between the two figures
    p.d.pieslice([*p.p(9, 48), *p.p(67, 108)], 180, 360, outline=0, width=p.w(4))
    p.d.pieslice([*p.p(12, 52), *p.p(64, 104)], 180, 360, fill=255)
    p.disc(38, 32, 13)


def g_grid(p):
    for x in (18, 54):
        for y in (18, 54):
            p.frect(x, y, x + 28, y + 28, 6)


def g_power(p):
    p.arc(50, 54, 30, -50, 230, 8)
    p.line([(50, 14), (50, 48)], 8)


def g_globe(p):
    p.circle(50, 50, 36)
    p.d.ellipse([*p.p(34, 14), *p.p(66, 86)], outline=255, width=p.w(6))
    p.line([(15, 50), (85, 50)], 6)


def g_arrow_circle(p):
    p.disc(50, 50, 38)
    p.line([(32, 50), (66, 50)], 9)
    p.poly([(56, 34), (72, 50), (56, 66)])
    p.line([(32, 50), (66, 50)], 0.1)
    p.d.line([p.p(32, 50), p.p(64, 50)], fill=0, width=p.w(8))
    p.d.polygon([p.p(54, 36), p.p(70, 50), p.p(54, 64)], fill=0)


def g_runner(p):
    p.disc(58, 16, 9)
    p.line([(54, 30), (44, 56)], 9)
    p.line([(44, 56), (60, 70), (56, 90)], 8)
    p.line([(44, 56), (32, 72), (16, 74)], 8)
    p.line([(52, 36), (68, 48), (80, 42)], 7)
    p.line([(52, 36), (36, 38), (28, 50)], 7)


def g_shirt(p):
    p.poly([(34, 16), (44, 16), (50, 26), (56, 16), (66, 16), (90, 32), (80, 48), (70, 42), (70, 86),
            (30, 86), (30, 42), (20, 48), (10, 32)])


def g_search(p):
    p.circle(42, 42, 24, 8)
    p.line([(60, 60), (82, 82)], 10)


def g_gauge(p):
    p.arc(50, 58, 36, 180, 360, 7)
    for a in (200, 240, 270, 300, 340):
        r0, r1 = 26, 34
        p.line([(50 + r0 * math.cos(math.radians(a)), 58 + r0 * math.sin(math.radians(a))),
                (50 + r1 * math.cos(math.radians(a)), 58 + r1 * math.sin(math.radians(a)))], 5)
    p.line([(50, 58), (68, 36)], 7)
    p.disc(50, 58, 7)


def g_mouse(p):
    p.rrect(28, 12, 72, 88, 22)
    p.line([(50, 14), (50, 38)], 6)
    p.line([(30, 40), (70, 40)], 6)


def g_keyboard(p):
    p.rrect(8, 26, 92, 76, 8)
    for row, y in enumerate((38, 50)):
        for i in range(6):
            x = 20 + i * 12 + (row * 4)
            p.frect(x - 3, y - 3, x + 4, y + 4, 1)
    p.frect(30, 60, 70, 66, 2)


def g_ruler(p):
    p.line([(12, 22), (12, 78)], 7)
    p.line([(88, 22), (88, 78)], 7)
    p.line([(24, 50), (76, 50)], 7)
    p.poly([(20, 50), (36, 38), (36, 62)])
    p.poly([(80, 50), (64, 38), (64, 62)])

def g_bolt(p):
    p.poly([(56, 8), (22, 56), (46, 56), (38, 92), (78, 40), (54, 40)])


def g_heart(p):
    p.disc(34, 38, 19)
    p.disc(66, 38, 19)
    p.poly([(16.5, 44), (50, 86), (83.5, 44), (50, 30)])


def g_potion(p):
    p.line([(40, 12), (60, 12)], 7)
    p.line([(42, 14), (42, 36), (24, 60)], 7)
    p.line([(58, 14), (58, 36), (76, 60)], 7)
    p.arc(50, 64, 28, 0, 180, 7)
    p.line([(22, 64), (24, 60)], 7)
    p.line([(78, 64), (76, 60)], 7)
    p.d.chord([*p.p(26, 42), *p.p(74, 88)], 15, 165, fill=255)


def g_apple(p):
    p.disc(38, 56, 24)
    p.disc(62, 56, 24)
    p.disc(50, 70, 20)
    p.hole(50, 34, 6)
    p.line([(50, 32), (54, 14)], 6)
    p.poly([(56, 22), (72, 12), (76, 24), (62, 30)])


def g_chestplate(p):
    p.poly([(26, 14), (40, 14), (50, 26), (60, 14), (74, 14), (90, 30), (78, 44), (74, 40), (74, 88),
            (26, 88), (26, 40), (22, 44), (10, 30)])
    p.d.polygon([p.p(42, 32), p.p(58, 32), p.p(56, 70), p.p(44, 70)], fill=0)


def g_hourglass(p):
    p.line([(22, 12), (78, 12)], 8)
    p.line([(22, 88), (78, 88)], 8)
    p.line([(28, 12), (28, 26), (50, 50), (28, 74), (28, 88)], 6)
    p.line([(72, 12), (72, 26), (50, 50), (72, 74), (72, 88)], 6)
    p.poly([(36, 84), (64, 84), (50, 66)])


def g_shield(p):
    p.line([(50, 10), (82, 22), (80, 52), (50, 90), (20, 52), (18, 22)], 7, closed=True)
    p.line([(50, 24), (50, 76)], 5)


def g_pin(p):
    p.disc(50, 38, 26)
    p.poly([(28, 50), (50, 88), (72, 50)])
    p.hole(50, 38, 11)


def g_signal(p):
    for i, h in enumerate((22, 38, 56, 74)):
        x = 16 + i * 19
        p.frect(x, 86 - h, x + 12, 86, 3)


def g_backpack(p):
    p.rrect(22, 26, 78, 88, 12)
    p.arc(50, 26, 14, 180, 360, 6)
    p.line([(22, 54), (78, 54)], 6)
    p.frect(42, 50, 58, 64, 3)


def g_clock(p):
    p.circle(50, 50, 38)
    p.line([(50, 50), (50, 26)], 7)
    p.line([(50, 50), (66, 60)], 7)


def g_list(p):
    for y in (24, 50, 76):
        p.disc(20, y, 6)
        p.line([(34, y), (84, y)], 7)


def g_board(p):
    p.rrect(12, 14, 88, 86, 8)
    p.line([(26, 32), (74, 32)], 6)
    for y in (48, 62, 74):
        p.line([(26, y), (56, y)], 5)
        p.line([(66, y), (74, y)], 5)


def g_totem(p):
    p.rrect(32, 10, 68, 44, 8)
    p.frect(40, 22, 46, 28, 1)
    p.frect(54, 22, 60, 28, 1)
    p.line([(50, 46), (50, 84)], 10)
    p.line([(18, 54), (82, 54)], 8)
    p.line([(36, 88), (64, 88)], 8)


def g_chat(p):
    p.rrect(10, 14, 90, 70, 14)
    p.poly([(26, 66), (24, 90), (46, 66)])
    for x in (32, 50, 68):
        p.disc(x, 42, 6)


def g_crystal(p):
    pts = [(50, 8), (82, 40), (50, 92), (18, 40)]
    p.line(pts, 6, closed=True)
    p.line([(18, 40), (82, 40)], 5)
    p.line([(34, 40), (50, 8), (66, 40)], 5)
    p.line([(34, 40), (50, 92), (66, 40)], 5)


def g_bell(p):
    p.d.chord([*p.p(22, 16), *p.p(78, 86)], 180, 360, fill=255)
    p.frect(22, 50, 78, 72, 2)
    p.frect(14, 68, 86, 78, 4)
    p.disc(50, 86, 8)
    p.disc(50, 14, 5)


def g_cube(p):
    a, b, c, d = (50, 12), (84, 30), (50, 48), (16, 30)
    p.line([a, b, c, d], 6, closed=True)
    p.line([(16, 30), (16, 70), (50, 88), (84, 70), (84, 30)], 6)
    p.line([(50, 48), (50, 88)], 6)


def g_person_plus(p):
    p.disc(40, 30, 14)
    p.d.pieslice([*p.p(12, 52), *p.p(68, 108)], 180, 360, fill=255)
    p.line([(78, 26), (78, 54)], 7)
    p.line([(64, 40), (92, 40)], 7)


def g_magnifier(p):
    g_search(p)
    p.line([(32, 42), (52, 42)], 6)
    p.line([(42, 32), (42, 52)], 6)


def g_crosshair(p):
    p.circle(50, 50, 26, 6)
    for a, b in (((50, 8), (50, 30)), ((50, 70), (50, 92)), ((8, 50), (30, 50)), ((70, 50), (92, 50))):
        p.line([a, b], 7)
    p.disc(50, 50, 5)


def g_sun(p):
    p.disc(50, 50, 18)
    for i in range(8):
        a = math.radians(i * 45)
        p.line([(50 + 28 * math.cos(a), 50 + 28 * math.sin(a)), (50 + 40 * math.cos(a), 50 + 40 * math.sin(a))], 7)


def g_eye(p):
    p.arc(50, 82, 50, 220, 320, 7)
    p.arc(50, 18, 50, 40, 140, 7)
    p.disc(50, 50, 15)
    p.hole(50, 50, 6)


def g_idcard(p):
    p.rrect(8, 20, 92, 80, 10)
    p.disc(32, 44, 9)
    p.d.pieslice([*p.p(18, 56), *p.p(46, 84)], 180, 360, fill=255)
    p.line([(56, 40), (80, 40)], 6)
    p.line([(56, 56), (74, 56)], 6)


def g_cape(p):
    p.line([(30, 14), (70, 14)], 7)
    p.poly([(28, 18), (72, 18), (84, 88), (66, 80), (50, 90), (34, 80), (16, 88)])


def g_film_camera(p):
    p.frect(10, 36, 66, 78, 8)
    p.poly([(66, 50), (92, 36), (92, 78), (66, 64)])
    p.circle(24, 22, 11, 6)
    p.circle(50, 22, 11, 6)


def g_box(p):
    p.poly([(50, 10), (88, 28), (50, 46), (12, 28)])
    p.poly([(12, 34), (47, 51), (47, 92), (12, 74)])
    p.poly([(88, 34), (53, 51), (53, 92), (88, 74)])


def g_moon(p):
    p.disc(50, 50, 36)
    p.hole(66, 38, 30)


def g_sun_moon(p):
    p.disc(36, 40, 16)
    for i in range(8):
        a = math.radians(i * 45)
        p.line([(36 + 22 * math.cos(a), 40 + 22 * math.sin(a)), (36 + 29 * math.cos(a), 40 + 29 * math.sin(a))], 5)
    p.hole(66, 66, 30)
    p.disc(66, 66, 24)
    p.hole(78, 56, 20)


def g_outline_square(p):
    p.rrect(16, 16, 84, 84, 6, 6)
    for x, y in ((16, 16), (84, 16), (16, 84), (84, 84)):
        p.frect(x - 8, y - 8, x + 8, y + 8, 2)


def g_motion(p):
    p.disc(66, 50, 22)
    p.line([(10, 34), (38, 34)], 7)
    p.line([(4, 50), (34, 50)], 7)
    p.line([(10, 66), (38, 66)], 7)


def g_xray(p):
    p.rrect(12, 12, 88, 88, 10, 6)
    p.poly([(50, 26), (70, 50), (50, 74), (30, 50)])
    p.hole(50, 50, 6)


def g_lock(p):
    p.frect(18, 44, 82, 90, 10)
    p.arc(50, 44, 20, 180, 360, 8)
    p.line([(30, 44), (30, 48)], 8)
    p.line([(70, 44), (70, 48)], 8)
    p.hole(50, 62, 7)
    p.d.rectangle([*p.p(47, 64), *p.p(53, 78)], fill=0)


def g_refresh(p):
    p.arc(50, 50, 30, 200, 340, 8)
    p.poly([(84, 34), (82, 58), (64, 44)])
    p.arc(50, 50, 30, 20, 160, 8)
    p.poly([(16, 66), (18, 42), (36, 56)])

def g_swap(p):
    p.line([(18, 34), (78, 34)], 7)
    p.poly([(66, 20), (86, 34), (66, 48)])
    p.line([(22, 66), (82, 66)], 7)
    p.poly([(34, 52), (14, 66), (34, 80)])


def g_info(p):
    p.circle(50, 50, 38)
    p.disc(50, 30, 6)
    p.line([(50, 44), (50, 72)], 9)


def g_camera(p):
    p.frect(8, 30, 92, 84, 10)
    p.frect(34, 18, 66, 34, 4)
    p.hole(50, 57, 19)
    p.disc(50, 57, 12)


def g_broadcast(p):
    p.disc(50, 50, 9)
    p.arc(50, 50, 22, 140, 220, 7)
    p.arc(50, 50, 22, -40, 40, 7)
    p.arc(50, 50, 38, 140, 220, 7)
    p.arc(50, 50, 38, -40, 40, 7)


def g_servers(p):
    for y in (12, 40, 68):
        p.rrect(12, y, 88, y + 22, 6, 6)
        p.disc(26, y + 11, 4)
        p.line([(62, y + 11), (76, y + 11)], 5)


def g_flame(p):
    p.disc(50, 62, 28)
    p.poly([(22, 60), (50, 6), (78, 60)])
    p.hole(50, 70, 12)
    p.poly([(38, 70), (50, 44), (62, 70)], 0)


def g_hotbar(p):
    p.rrect(6, 34, 94, 66, 4, 5)
    for x in (34, 62):
        p.line([(x, 36), (x, 64)], 4)
    p.rrect(32, 30, 68, 70, 4, 7)


def g_sparkles(p):
    p.sparkle(40, 56, 30)
    p.sparkle(74, 24, 14)
    p.sparkle(78, 74, 10)


def g_fov(p):
    p.line([(50, 84), (14, 22)], 7)
    p.line([(50, 84), (86, 22)], 7)
    p.arc(50, 84, 46, 240, 300, 7)
    p.disc(50, 84, 6)


def g_orbs(p):
    p.disc(34, 40, 16)
    p.disc(66, 34, 11)
    p.disc(60, 68, 18)


def g_layers(p):
    p.poly([(50, 10), (90, 30), (50, 50), (10, 30)])
    p.line([(10, 50), (50, 70), (90, 50)], 7)
    p.line([(10, 70), (50, 90), (90, 70)], 7)


def g_at(p):
    p.circle(50, 50, 14, 8)
    p.arc(50, 50, 36, 15, 330, 8)
    p.line([(64, 40), (64, 58), (72, 66), (82, 60), (86, 50)], 8)


def g_stack(p):
    p.rrect(24, 10, 90, 46, 10, 6)
    p.frect(10, 40, 76, 78, 10)
    p.poly([(20, 74), (18, 92), (36, 76)])


def g_planet_stars(p):
    p.disc(40, 60, 26)
    p.hole(52, 50, 22)
    p.sparkle(70, 26, 14)
    p.sparkle(84, 56, 8)
    p.sparkle(44, 20, 6)


def g_galaxy(p):
    for k in range(2):
        pts = []
        for i in range(60):
            t = i / 59 * 3.6
            r = 4 + t * 11
            a = t * 1.9 + k * math.pi
            pts.append((50 + r * math.cos(a), 50 + r * math.sin(a) * 0.75))
        p.line(pts, 6)
    p.disc(50, 50, 7)


def g_cloud(p):
    p.disc(34, 58, 18)
    p.disc(54, 44, 24)
    p.disc(72, 60, 16)
    p.frect(18, 58, 86, 76, 9)


def g_aurora(p):
    for k, y in enumerate((28, 50, 72)):
        pts = [(8 + i * 2.8, y + 8 * math.sin(i * 0.38 + k)) for i in range(31)]
        p.line(pts, 7)


def g_black_hole(p):
    p.d.ellipse([*p.p(6, 40), *p.p(94, 62)], outline=255, width=p.w(6))
    p.disc(50, 50, 22)
    p.hole(50, 50, 15)
    p.d.ellipse([*p.p(6, 40), *p.p(94, 62)], outline=255, width=p.w(6))
    p.hole(50, 50, 15)


def g_ringed_planet(p):
    p.disc(50, 50, 24)
    p.d.ellipse([*p.p(4, 38), *p.p(96, 62)], outline=0, width=p.w(12))
    p.d.ellipse([*p.p(6, 40), *p.p(94, 60)], outline=255, width=p.w(6))
    p.d.chord([*p.p(26, 26), *p.p(74, 74)], 200, 340, fill=255)


def g_shooting_star(p):
    p.star(68, 32, 22, 9, True)
    p.line([(12, 88), (50, 50)], 7)
    p.line([(10, 66), (34, 46)], 5)
    p.line([(34, 92), (54, 70)], 5)


def g_fireflies(p):
    for x, y, r in ((30, 36, 9), (66, 28, 6), (60, 66, 11), (24, 74, 5), (84, 54, 4)):
        p.disc(x, y, r)


def g_aperture(p):
    p.circle(50, 50, 38)
    for i in range(6):
        a = math.radians(i * 60)
        b = math.radians(i * 60 + 75)
        p.line([(50 + 14 * math.cos(a), 50 + 14 * math.sin(a)), (50 + 36 * math.cos(b), 50 + 36 * math.sin(b))], 6)


def g_palette(p):
    p.disc(50, 50, 38)
    p.hole(62, 66, 10)
    for x, y in ((32, 36), (50, 26), (68, 34), (30, 56)):
        p.hole(x, y, 6)


def g_layout(p):
    p.rrect(10, 14, 90, 86, 8, 6)
    p.frect(20, 24, 48, 44, 3)
    p.frect(56, 24, 80, 34, 3)
    p.frect(20, 62, 80, 76, 3)


def g_compass(p):
    p.circle(50, 50, 38)
    p.poly([(50, 22), (60, 50), (50, 78), (40, 50)])
    p.d.polygon([p.p(50, 54), p.p(57, 50), p.p(50, 76), p.p(43, 50)], fill=0)


def g_play(p):
    p.poly([(30, 16), (84, 50), (30, 84)])


def g_plus(p):
    p.line([(50, 14), (50, 86)], 11)
    p.line([(14, 50), (86, 50)], 11)


def g_trash(p):
    p.line([(14, 24), (86, 24)], 8)
    p.line([(38, 22), (40, 12), (60, 12), (62, 22)], 7)
    p.line([(22, 30), (28, 88), (72, 88), (78, 30)], 7)
    p.line([(42, 40), (42, 76)], 6)
    p.line([(58, 40), (58, 76)], 6)


def g_back(p):
    p.line([(84, 50), (20, 50)], 10)
    p.line([(46, 22), (18, 50), (46, 78)], 10)


def g_pencil(p):
    p.poly([(66, 10), (90, 34), (38, 86), (14, 86), (14, 62)])
    p.d.polygon([p.p(66, 22), p.p(78, 34), p.p(36, 76), p.p(24, 64)], fill=0)
    p.poly([(68, 24), (76, 32), (36, 72), (28, 64)])


def g_wings(p):
    for sgn in (-1, 1):
        c = 50
        pts = [(c + sgn * 4, 50), (c + sgn * 14, 30), (c + sgn * 30, 16), (c + sgn * 46, 10),
               (c + sgn * 44, 26), (c + sgn * 36, 32), (c + sgn * 42, 42), (c + sgn * 32, 48),
               (c + sgn * 36, 60), (c + sgn * 24, 62), (c + sgn * 24, 74), (c + sgn * 12, 66), (c + sgn * 6, 60)]
        p.poly(pts)

def g_halo(p):
    p.d.ellipse([*p.p(14, 16), *p.p(86, 42)], outline=255, width=p.w(9))
    p.disc(50, 66, 14)
    p.d.pieslice([*p.p(24, 80), *p.p(76, 120)], 180, 360, fill=255)


def g_horns(p):
    for sgn in (-1, 1):
        c = 50
        p.poly([(c + sgn * 10, 60), (c + sgn * 16, 44), (c + sgn * 24, 30), (c + sgn * 34, 16), (c + sgn * 44, 8),
                (c + sgn * 40, 22), (c + sgn * 32, 36), (c + sgn * 26, 50), (c + sgn * 24, 62)])
    p.d.pieslice([*p.p(24, 58), *p.p(76, 110)], 180, 360, fill=255)

def g_crown(p):
    p.poly([(12, 78), (12, 30), (32, 52), (50, 18), (68, 52), (88, 30), (88, 78)])
    p.d.rectangle([*p.p(12, 66), *p.p(88, 70)], fill=0)


def g_cat_ears(p):
    p.poly([(14, 70), (24, 14), (48, 50)])
    p.poly([(86, 70), (76, 14), (52, 50)])
    p.d.pieslice([*p.p(14, 44), *p.p(86, 116)], 180, 360, fill=255)


def g_bunny(p):
    for cx in (36, 64):
        p.d.ellipse([*p.p(cx - 8, 4), *p.p(cx + 8, 56)], fill=255)
    p.d.pieslice([*p.p(14, 50), *p.p(86, 122)], 180, 360, fill=255)


def g_antlers(p):
    for sgn in (-1, 1):
        c = 50
        p.line([(c + sgn * 8, 70), (c + sgn * 16, 44), (c + sgn * 34, 14)], w=7)
        p.line([(c + sgn * 14, 52), (c + sgn * 34, 44)], w=6)
        p.line([(c + sgn * 22, 34), (c + sgn * 14, 14)], w=6)
    p.d.pieslice([*p.p(26, 62), *p.p(74, 110)], 180, 360, fill=255)


def g_flower(p):
    for k in range(5):
        a = math.radians(k * 72 - 90)
        p.disc(50 + math.cos(a) * 20, 50 + math.sin(a) * 20, 14)
    p.d.ellipse([*p.p(38, 38), *p.p(62, 62)], fill=0)
    p.disc(50, 50, 8)


def g_witch_hat(p):
    p.poly([(28, 72), (46, 20), (72, 8), (60, 26), (72, 72)])
    p.d.rectangle([*p.p(8, 72), *p.p(92, 82)], fill=255)


def g_top_hat(p):
    p.d.rectangle([*p.p(28, 18), *p.p(72, 72)], fill=255)
    p.d.rectangle([*p.p(28, 58), *p.p(72, 64)], fill=0)
    p.d.rectangle([*p.p(10, 72), *p.p(90, 82)], fill=255)


def g_snowflake(p):
    for k in range(3):
        a = math.radians(k * 60)
        dx, dy = math.cos(a) * 38, math.sin(a) * 38
        p.line([(50 - dx, 50 - dy), (50 + dx, 50 + dy)], w=6)
        for sgn in (-1, 1):
            ex, ey = 50 + sgn * dx * 0.6, 50 + sgn * dy * 0.6
            b1 = a + math.radians(45 if sgn > 0 else 225)
            b2 = a - math.radians(45 if sgn > 0 else -135)
            p.line([(ex, ey), (ex + math.cos(b1) * 12, ey + math.sin(b1) * 12)], w=5)
            p.line([(ex, ey), (ex + math.cos(b2) * 12, ey + math.sin(b2) * 12)], w=5)


def g_note(p):
    p.disc(34, 72, 12)
    p.disc(70, 64, 12)
    p.line([(44, 72), (44, 18), (80, 10), (80, 64)], w=7)
    p.line([(44, 30), (80, 22)], w=7)


GLYPHS = [
    ("star_outline", g_star_outline), ("star_filled", g_star_filled), ("gear", g_gear), ("person", g_person),
    ("people", g_people), ("grid", g_grid), ("power", g_power), ("globe", g_globe),
    ("arrow_circle", g_arrow_circle), ("runner", g_runner), ("shirt", g_shirt), ("search", g_search),
    ("gauge", g_gauge), ("mouse", g_mouse), ("keyboard", g_keyboard), ("ruler", g_ruler),
    ("bolt", g_bolt), ("heart", g_heart), ("potion", g_potion), ("apple", g_apple),
    ("chestplate", g_chestplate), ("hourglass", g_hourglass), ("shield", g_shield), ("pin", g_pin),
    ("signal", g_signal), ("backpack", g_backpack), ("clock", g_clock), ("list", g_list),
    ("board", g_board), ("totem", g_totem), ("chat", g_chat), ("crystal", g_crystal),
    ("bell", g_bell), ("cube", g_cube), ("person_plus", g_person_plus), ("magnifier", g_magnifier),
    ("crosshair", g_crosshair), ("sun", g_sun), ("eye", g_eye), ("idcard", g_idcard),
    ("cape", g_cape), ("film_camera", g_film_camera), ("box", g_box), ("moon", g_moon),
    ("sun_moon", g_sun_moon), ("outline_square", g_outline_square), ("motion", g_motion), ("xray", g_xray),
    ("lock", g_lock), ("refresh", g_refresh), ("swap", g_swap), ("info", g_info),
    ("camera", g_camera), ("broadcast", g_broadcast), ("servers", g_servers), ("flame", g_flame),
    ("hotbar", g_hotbar), ("sparkles", g_sparkles), ("fov", g_fov), ("orbs", g_orbs),
    ("layers", g_layers), ("at", g_at), ("stack", g_stack), ("planet_stars", g_planet_stars),
    ("galaxy", g_galaxy), ("cloud", g_cloud), ("aurora", g_aurora), ("black_hole", g_black_hole),
    ("ringed_planet", g_ringed_planet), ("shooting_star", g_shooting_star), ("fireflies", g_fireflies), ("aperture", g_aperture),
    ("palette", g_palette), ("layout", g_layout), ("compass", g_compass), ("play", g_play),
    ("plus", g_plus), ("trash", g_trash), ("back", g_back), ("pencil", g_pencil),
    ("wings", g_wings), ("halo", g_halo), ("horns", g_horns), ("crown", g_crown),
    ("cat_ears", g_cat_ears), ("bunny", g_bunny), ("antlers", g_antlers), ("flower", g_flower),
    ("witch_hat", g_witch_hat), ("top_hat", g_top_hat), ("snowflake", g_snowflake), ("note", g_note),
]


def main():
    out = sys.argv[1] if len(sys.argv) > 1 else os.path.join(os.path.dirname(__file__), "..",
                                                            "src/main/resources/assets/tempest/textures/gui/icons.png")
    rows = (len(GLYPHS) + COLS - 1) // COLS
    atlas = Image.new("RGBA", (COLS * CELL, rows * CELL), (255, 255, 255, 0))
    for i, (name, fn) in enumerate(GLYPHS):
        pen = Pen()
        fn(pen)
        mask = pen.img.resize((CELL, CELL), Image.LANCZOS)
        tile = Image.new("RGBA", (CELL, CELL), (255, 255, 255, 0))
        tile.putalpha(mask)
        atlas.paste(tile, ((i % COLS) * CELL, (i // COLS) * CELL))
    atlas.save(out)
    print("wrote %s (%d icons, %dx%d)" % (out, len(GLYPHS), atlas.width, atlas.height))
    print("NAMES = {" + ", ".join('"%s"' % n for n, _ in GLYPHS) + "}")


if __name__ == "__main__":
    main()
