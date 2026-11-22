"""
Renders previews of the Custom Sky scenes (assets/tempest/sky/scenes.json) with the same textures, geometry
and draw order as render/SkyRenderer.java, so scenes can be tuned without launching the game.

Run:  python tools/preview_sky.py [out_dir]      (needs numpy + Pillow)
"""
import json
import math
import os
import sys

import numpy as np
from PIL import Image

ROOT = os.path.join(os.path.dirname(__file__), "..")
TEX = os.path.join(ROOT, "src/main/resources/assets/tempest/textures/sky")
SCENES = json.load(open(os.path.join(ROOT, "src/main/resources/assets/tempest/sky/scenes.json")))["scenes"]
OUT = sys.argv[1] if len(sys.argv) > 1 else os.path.join(ROOT, "build", "sky-previews")
os.makedirs(OUT, exist_ok=True)

W, H, FOV = 480, 270, 70.0
R = 100.0
_cache = {}


def tex(name):
    if name not in _cache:
        _cache[name] = np.asarray(Image.open(os.path.join(TEX, name + ".png")).convert("RGBA")).astype(np.float32) / 255
    return _cache[name]


def rgb(h):
    h = h.lstrip("#")
    return np.array([int(h[i:i + 2], 16) / 255 for i in (0, 2, 4)], dtype=np.float32)


def direction(az, el):
    a, e = math.radians(az), math.radians(el)
    return np.array([math.sin(a) * math.cos(e), math.sin(e), -math.cos(a) * math.cos(e)])


def basis(d):
    up = np.array([0, 1.0, 0]) if abs(d[1]) <= 0.98 else np.array([0, 0, -1.0])
    r = np.cross(d, up); r /= np.linalg.norm(r)
    u = np.cross(r, d)
    return r, u


def sample(t, u, v):
    h, w = t.shape[:2]
    x = np.clip(u * (w - 1), 0, w - 1); y = np.clip(v * (h - 1), 0, h - 1)
    x0 = np.floor(x).astype(int); y0 = np.floor(y).astype(int)
    x1 = np.minimum(x0 + 1, w - 1); y1 = np.minimum(y0 + 1, h - 1)
    fx = (x - x0)[..., None]; fy = (y - y0)[..., None]
    return (t[y0, x0] * (1 - fx) + t[y0, x1] * fx) * (1 - fy) + (t[y1, x0] * (1 - fx) + t[y1, x1] * fx) * fy


class Frame:
    def __init__(self, cam_az, cam_el, top, horizon):
        f = direction(cam_az, cam_el)
        r, u = basis(f)
        ys, xs = np.mgrid[0:H, 0:W]
        tv = math.tan(math.radians(FOV / 2)); th = tv * W / H
        nx = (xs + 0.5) / W * 2 - 1; ny = 1 - (ys + 0.5) / H * 2
        rays = f[None, None] + nx[..., None] * th * r[None, None] + ny[..., None] * tv * u[None, None]
        self.rays = rays / np.linalg.norm(rays, axis=-1, keepdims=True)
        self.f, self.r, self.u, self.tv, self.th = f, r, u, tv, th
        el = np.degrees(np.arcsin(self.rays[..., 1]))
        # vanilla-like dome: top colour high up, blending into the horizon / fog colour near the horizon
        k = np.clip(el / 25, 0, 1)[..., None] ** 0.6
        self.img = horizon[None, None] * (1 - k) + top[None, None] * k

    def over(self, col, a):
        a = np.clip(a, 0, 1)[..., None]
        self.img = self.img * (1 - a) + col * a

    def project(self, d):
        c = np.array([np.dot(d, self.r), np.dot(d, self.u), np.dot(d, self.f)])
        if c[2] <= 0.01: return None
        return ((c[0] / c[2] / self.th) * 0.5 + 0.5) * W, (0.5 - (c[1] / c[2] / self.tv) * 0.5) * H

    def sprite(self, name, d, half, color, alpha, rr=None, uu=None, uv_flip=False):
        if rr is None: rr, uu = basis(d)
        t = tex(name)
        dd = self.rays @ d
        ok = dd > 0.01
        tt = R / np.where(ok, dd, 1)
        p = self.rays * tt[..., None] - d * R
        lx = (p @ rr) / half; ly = (p @ uu) / half
        ok &= (np.abs(lx) <= 1) & (np.abs(ly) <= 1)
        if not ok.any(): return
        u = 0.5 + 0.5 * lx; v = 0.5 - 0.5 * ly
        if uv_flip: u = 1 - u
        s = sample(t, u, v)
        col = s[..., :3] * np.asarray(color)[None, None]
        self.over(col, np.where(ok, s[..., 3] * alpha, 0))


def ground(fr):
    az = np.degrees(np.arctan2(fr.rays[..., 0], -fr.rays[..., 2]))
    el = np.degrees(np.arcsin(fr.rays[..., 1]))
    hill = 1.5 + 4 * np.sin(np.radians(az) * 3) + 2.5 * np.sin(np.radians(az) * 7 + 1) + 1.2 * np.sin(np.radians(az) * 17)
    m = el < hill
    fr.img[m] = fr.img[m] * 0.08 + np.array([0.02, 0.025, 0.03]) * 0.92


def render(scene, cam_az, cam_el, t=12.0, rot=0.0):
    fr = Frame(cam_az, cam_el, rgb(scene["top"]), rgb(scene["horizon"]))
    rays = fr.rays
    az = (np.degrees(np.arctan2(rays[..., 0], -rays[..., 2])) + 360) % 360
    el = np.degrees(np.arcsin(rays[..., 1]))

    # horizon glow band: el -6 .. 26
    g = scene.get("glow")
    if g:
        v = 1 - (el + 6) / 32
        ok = (v >= 0) & (v <= 1)
        s = sample(tex("horizon"), np.full_like(v, 0.5), np.clip(v, 0, 1))
        fr.over(rgb(g["color"])[None, None], np.where(ok, s[..., 3] * g["alpha"], 0))

    # milky way
    mw = scene.get("milkyWay")
    if mw:
        ang = math.radians(mw["angle"] + rot)
        u0 = np.array([math.sin(ang), 0, -math.cos(ang)])
        v0 = np.array([math.cos(ang) * 0.5, math.sin(math.radians(60)), math.sin(ang) * 0.5]); v0 /= np.linalg.norm(v0)
        n = np.cross(u0, v0); n /= np.linalg.norm(n); v0 = np.cross(n, u0)
        dist = np.degrees(np.arcsin(np.clip(rays @ n, -1, 1)))
        phi = np.arctan2(rays @ v0, rays @ u0) % (2 * math.pi)
        hw = mw["width"]
        ok = np.abs(dist) <= hw
        s = sample(tex("milky_way"), phi / (2 * math.pi), 0.5 - 0.5 * dist / hw)
        tint_mw = rgb(mw.get("tint", "#FFFFFF"))
        fr.over(s[..., :3] * tint_mw[None, None], np.where(ok, s[..., 3] * min(1.0, 0.85 * mw["alpha"]), 0))
        if mw["alpha"] > 1.0:           # bright galactic core: warm dust glow along the band around its middle
            k = mw["alpha"] - 0.6
            for j in range(7):
                ph = math.pi / 2 + (j - 3) * math.radians(9)
                cd = u0 * math.cos(ph) + v0 * math.sin(ph)
                fall = math.exp(-((j - 3) / 2.2) ** 2)
                col = rgb("#FFD9B0") if j % 2 == 0 else rgb("#FF9A7A")
                fr.sprite("glow", cd, R * math.tan(math.radians(mw["width"] * (1.1 + 0.9 * fall))), col, 0.22 * k * fall)
            fr.sprite("glow", v0, R * math.tan(math.radians(mw["width"] * 0.9)), rgb("#FFF1DC"), 0.35 * k)

    # nebulae: 5 puffs per cloud at fixed offsets
    for i, nb in enumerate(scene.get("nebulae", [])):
        for k in range(5):
            oaz = math.cos(k * 2.4 + i) * 0.45 * nb["size"]
            oel = math.sin(k * 1.7 + i) * 0.25 * nb["size"]
            psz = nb["size"] * (0.55 + 0.15 * (k % 3))
            d = direction(nb["az"] + rot + oaz, nb["el"] + oel)
            breathe = 0.75 + 0.25 * math.sin(t * 0.25 + k + i)
            fr.sprite("nebula", d, R * math.tan(math.radians(psz * 0.5)), rgb(nb["color"]), nb["alpha"] * breathe, uv_flip=(k % 2 == 1))

    # stars (splatted)
    st = scene.get("stars")
    if st:
        rng = np.random.default_rng(90210)
        n = st["count"]
        z = -0.1 + rng.random(n) * 1.1
        a = rng.random(n) * 2 * math.pi
        sxy = np.sqrt(np.clip(1 - z * z, 0, 1))
        dirs = np.stack([np.cos(a) * sxy, z, np.sin(a) * sxy], 1)
        mag = rng.random(n) ** 4
        tint = rgb(st["tint"])
        cam = np.stack([dirs @ fr.r, dirs @ fr.u, dirs @ fr.f], 1)
        vis = cam[:, 2] > 0.05
        px = ((cam[:, 0] / cam[:, 2] / fr.th) * 0.5 + 0.5) * W
        py = (0.5 - (cam[:, 1] / cam[:, 2] / fr.tv) * 0.5) * H
        for i in np.nonzero(vis)[0]:
            x, y = int(px[i]), int(py[i])
            if 0 <= x < W and 0 <= y < H:
                al = (0.3 + 0.7 * mag[i]) * st.get("brightness", 1.0)
                fr.img[y, x] = fr.img[y, x] * (1 - al) + tint * al
                if mag[i] > 0.55:
                    for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                        xx, yy = x + dx, y + dy
                        if 0 <= xx < W and 0 <= yy < H:
                            fr.img[yy, xx] = fr.img[yy, xx] * (1 - al * 0.4) + tint * al * 0.4

    # auroras
    for au in scene.get("auroras", []):
        c = [rgb(x) for x in au["colors"]]
        uu = ((az - (au["az"] + rot - au["span"] / 2) + 540) % 360 - 180) / au["span"]
        tt = t * au.get("speed", 1.0)
        base = 12 + 4.5 * np.sin(uu * 7 + tt * 0.21) + 2.0 * np.sin(uu * 17 - tt * 0.37) + (au["base"] - 12)
        def wave(x): return 0.5 + 0.25 * np.sin(x) + 0.15 * np.sin(x * 2.37 + 1.3) + 0.10 * np.sin(x * 5.11 + 4.1)
        hh = au["height"] * (0.62 + 0.38 * wave(uu * 9 + tt * 0.17))
        f = (el - base) / hh
        ok = (uu >= 0) & (uu <= 1) & (f >= 0) & (f <= 1)
        ray = wave(uu * 31 - tt * 0.9)
        bright = np.minimum(1, 0.45 + 0.75 * ray ** 3 * 2) * 0.95 * np.sin(np.pi * np.clip(uu, 0, 1))
        s = sample(tex("aurora"), np.clip(uu, 0, 1), np.clip(1 - f, 0, 1))
        k1 = np.clip(f / 0.4, 0, 1)[..., None]; k2 = np.clip((f - 0.4) / 0.6, 0, 1)[..., None]
        col = np.where(f[..., None] < 0.4, c[0] * (1 - k1) + c[1] * k1, c[1] * (1 - k2) + c[2] * k2)
        fr.over(col, np.where(ok, s[..., 3] * bright * au["alpha"], 0))

    # planets and moons
    for p in scene.get("planets", []):
        d = direction(p["az"] + rot, p["el"])
        half = R * math.tan(math.radians(p["size"] * 0.5)) / 0.42
        fr.sprite("glow", d, half * 1.6, rgb(p["glow"]), 0.22)
        fr.sprite(p["texture"], d, half, np.ones(3), 1.0)
    for mo in scene.get("moons", []):
        d = direction(mo["az"] + rot, mo["el"])
        half = R * math.tan(math.radians(mo["size"] * 0.5)) / 0.9
        fr.sprite("glow", d, half * 4.0, rgb(mo["glow"]), mo.get("glowAlpha", 0.3))
        fr.sprite("moon", d, half, rgb(mo["tint"]), 1.0)

    # black hole
    bh = scene.get("blackHole")
    if bh:
        col = [rgb(x) for x in bh["colors"]]
        d = direction(bh["az"] + rot, bh["el"])
        r_, u_ = basis(d)
        rs = R * math.tan(math.radians(bh["size"] * 0.5))
        spin = t * 0.35 * bh.get("spin", 1.0)
        tilt = math.radians(bh.get("tilt", 12))
        a2 = -u_ * math.sin(tilt) + (-d) * math.cos(tilt)
        fr.sprite("glow", d, rs * 9, col[1], 0.30)
        fr.sprite("glow", d, rs * 5, col[0], 0.16)
        fr.sprite("glow", d, rs * 3.4, np.zeros(3), 0.55)

        def disk(near):
            c = d * R
            n = np.cross(r_, a2)
            den = rays @ n
            tt = (c @ n) / np.where(np.abs(den) < 1e-6, 1e-6, den)
            p = rays * tt[..., None] - c
            x = p @ r_; y = p @ a2
            rad = np.sqrt(x * x + y * y)
            outer = rs * 4.2; inner = outer * 0.30
            th = np.arctan2(y, x)
            ok = (tt > 0) & (rad >= inner) & (rad <= outer) & ((np.sin(th) > 0) == near)
            uu = 0.5 + 0.5 * np.cos(th + spin) * rad / outer
            vv = 0.5 + 0.5 * np.sin(th + spin) * rad / outer
            dop = 0.55 + 0.45 * -np.cos(th)
            s = sample(tex("disk"), uu, vv)
            fr.over(col[1][None, None] * np.ones_like(s[..., :3]), np.where(ok, s[..., 3] * dop, 0))
            s2 = sample(tex("disk_core"), uu, vv)
            fr.over(col[0][None, None] * np.ones_like(s2[..., :3]), np.where(ok, s2[..., 3] * dop * 0.8, 0))

        disk(False)
        fr.sprite("lens", d, rs * 2.18, col[0], 0.95, r_, u_)
        fr.sprite("shadow", d, rs / 0.97, np.zeros(3), 1.0, r_, u_)
        fr.sprite("photon_ring", d, rs * 1.18, col[0], 0.9, r_, u_)
        disk(True)

    ground(fr)
    return Image.fromarray((np.clip(fr.img, 0, 1) * 255).astype(np.uint8), "RGB")


# camera framing per scene (look at the main object)
FRAMING = {"Deep Space": (25, 32), "Black Hole": (160, 24), "Northern Lights": (5, 20), "Galaxy Core": (150, 38),
           "Ringed Giant": (215, 20), "Nebula": (15, 32), "Meteor Shower": (45, 35), "Blood Moon": (125, 24)}

if __name__ == "__main__":
    tiles = []
    for sc in SCENES:
        caz, cel = FRAMING.get(sc["name"], (0, 25))
        im = render(sc, caz, cel)
        im.save(os.path.join(OUT, sc["name"].lower().replace(" ", "_") + ".png"))
        tiles.append((sc["name"], im))
        print("rendered", sc["name"])
    cols = 2
    rows = (len(tiles) + cols - 1) // cols
    sheet = Image.new("RGB", (cols * W, rows * H), (0, 0, 0))
    for i, (n, im) in enumerate(tiles):
        sheet.paste(im, ((i % cols) * W, (i // cols) * H))
    sheet.save(os.path.join(OUT, "_all.png"))
    print("sheet:", os.path.join(OUT, "_all.png"))
