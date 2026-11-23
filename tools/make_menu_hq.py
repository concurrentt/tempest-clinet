"""
High-quality main menu backgrounds (textures/gui/menu_bg*.png, 1920x1080).

Same scenes, framing and draw order as preview_sky.py (so each theme still looks like itself), but rendered at
1.5x and scaled down for clean edges, with extra detail the small in-game sky textures can't give:
  - wispy fine structure inside nebulae and the Milky Way (tileable fractal noise)
  - real stars: thousands of soft round points in a few colours and sizes, sparkle crosses on the brightest
  - a natural mountain ridge with distant haze instead of a smooth sine hill
  - soft bloom on bright things, dithering so dark gradients don't band

Run:  python tools/make_menu_hq.py            (needs numpy, scipy, Pillow)
"""
import math
import os
import sys

import numpy as np
from PIL import Image
from scipy.ndimage import gaussian_filter

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
ARGS = sys.argv[1:]
sys.argv = sys.argv[:1]
import preview_sky as ps  # noqa: E402

OUT = os.path.join(HERE, "..", "src", "main", "resources", "assets", "tempest", "textures", "gui")
FINAL_W, FINAL_H, SS = 1920, 1080, 1.5
ps.W, ps.H, ps.FOV = int(FINAL_W * SS), int(FINAL_H * SS), 72.0
W, H = ps.W, ps.H
rng = np.random.default_rng(424242)


# ------------------------------------------------------------------ tileable fractal noise
def fractal(n=1024, beta=1.9, seed=1):
    r = np.random.default_rng(seed)
    fx = np.fft.fftfreq(n)[:, None]; fy = np.fft.fftfreq(n)[None, :]
    f = np.sqrt(fx * fx + fy * fy); f[0, 0] = 1
    spec = (r.normal(size=(n, n)) + 1j * r.normal(size=(n, n))) / f ** beta
    spec[0, 0] = 0
    img = np.real(np.fft.ifft2(spec))
    img = (img - img.min()) / (img.max() - img.min())
    return img.astype(np.float32)


WISP = fractal(1024, 1.75, 7)                  # soft clouds
RIDGE = 1 - np.abs(2 * fractal(1024, 1.55, 11) - 1)   # thin filaments
RIDGE = RIDGE ** 3


def tiled(t, u, v):
    """Bilinear sample of a tileable noise texture at any u, v (wraps)."""
    h, w = t.shape
    x = (u % 1.0) * w; y = (v % 1.0) * h
    x0 = np.floor(x).astype(int) % w; y0 = np.floor(y).astype(int) % h
    x1 = (x0 + 1) % w; y1 = (y0 + 1) % h
    fx = x - np.floor(x); fy = y - np.floor(y)
    return (t[y0, x0] * (1 - fx) + t[y0, x1] * fx) * (1 - fy) + (t[y1, x0] * (1 - fx) + t[y1, x1] * fx) * fy


# nebula / milky way textures get fine wispy structure: wrap the shared sampler
_orig_sample = ps.sample


def detailed_sample(t, u, v):
    s = _orig_sample(t, u, v)
    if t is ps.tex("nebula"):
        d = 0.55 * tiled(WISP, u * 2.3 + 0.37, v * 2.3 + 0.11) + 0.45 * tiled(RIDGE, u * 3.1, v * 3.1)
        s = s.copy()
        s[..., 3] *= np.clip(0.2 + 1.55 * d ** 1.3, 0, 1.7)
    elif t is ps.tex("milky_way"):
        d = 0.5 * tiled(WISP, u * 22, v * 2.7) + 0.5 * tiled(RIDGE, u * 34, v * 4.1)
        s = s.copy()
        s[..., 3] *= np.clip(0.3 + 1.4 * d ** 1.2, 0, 1.6)
        # dark dust lane through the middle of the band
        lane = np.exp(-((v - 0.5 - 0.06 * np.sin(u * 40)) / 0.07) ** 2) * (0.35 + 0.65 * tiled(WISP, u * 15, v * 3 + 0.5))
        s[..., 3] *= 1 - 0.55 * lane
    return s


ps.sample = detailed_sample


# ------------------------------------------------------------------ stars
def draw_stars(fr, st, scale_density=1.0):
    n = int(st["count"] * scale_density)
    r = np.random.default_rng(90210)
    z = -0.12 + r.random(n) * 1.12
    a = r.random(n) * 2 * math.pi
    sxy = np.sqrt(np.clip(1 - z * z, 0, 1))
    dirs = np.stack([np.cos(a) * sxy, z, np.sin(a) * sxy], 1)
    mag = r.random(n) ** 5                       # mostly faint, few bright
    temp = r.random(n)                           # colour: blue-white .. white .. warm
    cam = np.stack([dirs @ fr.r, dirs @ fr.u, dirs @ fr.f], 1)
    vis = cam[:, 2] > 0.05
    px = ((cam[:, 0] / cam[:, 2] / fr.th) * 0.5 + 0.5) * W
    py = (0.5 - (cam[:, 1] / cam[:, 2] / fr.tv) * 0.5) * H
    ok = vis & (px >= -20) & (px < W + 20) & (py >= -20) & (py < H + 20)
    tint = ps.rgb(st["tint"])
    blue, warm = np.array([0.72, 0.82, 1.0]), np.array([1.0, 0.86, 0.68])
    layer = np.zeros((H, W, 3), np.float32)
    bright = st.get("brightness", 1.0)
    for i in np.nonzero(ok)[0]:
        m = mag[i]
        col = tint * (blue * (1 - temp[i]) * 0.5 + 0.5) if temp[i] < 0.5 else tint * (warm * (temp[i] - 0.5) * 2 + (1 - (temp[i] - 0.5) * 2))
        col = col * 0.5 + tint * 0.5 if temp[i] > 0.2 and temp[i] < 0.8 else col
        rad = (0.55 + 2.4 * m ** 1.4) * SS            # gaussian sigma in pixels
        amp = (0.18 + 1.6 * m) * bright
        k = int(math.ceil(rad * 3))
        cx, cy = px[i], py[i]
        x0, x1 = max(0, int(cx) - k), min(W, int(cx) + k + 2)
        y0, y1 = max(0, int(cy) - k), min(H, int(cy) + k + 2)
        if x0 >= x1 or y0 >= y1:
            continue
        yy, xx = np.mgrid[y0:y1, x0:x1]
        d2 = (xx + 0.5 - cx) ** 2 + (yy + 0.5 - cy) ** 2
        g = np.exp(-d2 / (2 * rad * rad)) * amp
        layer[y0:y1, x0:x1] += g[..., None] * col
        if m > 0.93:                                   # sparkle cross on the very brightest
            L = int((10 + 28 * (m - 0.93) / 0.07) * SS)
            for dx, dy in ((1, 0), (0, 1)):
                for t in range(-L, L + 1):
                    x, y = int(cx + dx * t), int(cy + dy * t)
                    if 0 <= x < W and 0 <= y < H:
                        layer[y, x] += col * amp * 0.5 * math.exp(-abs(t) / (L * 0.28))
    fr.img = fr.img + layer * (1 - np.clip(fr.img.mean(-1, keepdims=True) * 1.4, 0, 0.7))


# ------------------------------------------------------------------ ground: a ridge with distant haze
def hq_ground(fr, horizon_col):
    az = np.degrees(np.arctan2(fr.rays[..., 0], -fr.rays[..., 2]))
    el = np.degrees(np.arcsin(fr.rays[..., 1]))
    u = (az + 180) / 360
    def ridge(seed_u, amp, base, rough):
        hgt = base + amp * (np.sin(np.radians(az) * 3 + seed_u) * 0.5 + 0.5 * np.sin(np.radians(az) * 7 + 1.3 * seed_u))
        hgt += rough * (tiled(WISP, u * 5 + seed_u, np.full_like(u, 0.3 + seed_u * 0.1)) - 0.5) * 7     # big shapes
        hgt += rough * (tiled(WISP, u * 24 + seed_u, np.full_like(u, 0.62)) - 0.5) * 1.4                # rocky detail
        hgt += rough * (tiled(WISP, u * 90 + seed_u, np.full_like(u, 0.81)) - 0.5) * 0.35               # fine texture
        return hgt
    px_per_deg = H / ps.FOV
    far = ridge(2.1, 2.6, 2.4, 1.0)
    near = ridge(0.4, 4.2, 0.6, 1.6)
    haze = horizon_col * 0.55 + np.array([0.05, 0.06, 0.08])
    # distant ridge: hazy, lit a little by the sky
    a_far = np.clip((far - el) * px_per_deg + 0.5, 0, 1)[..., None]
    fr.img = fr.img * (1 - a_far * 0.82) + (fr.img * 0.25 + haze * 0.35) * a_far * 0.82
    # glow hugging the horizon line
    glow = np.exp(-np.clip(el - far, 0, None) / 3.5)[..., None] * (1 - a_far)
    fr.img = fr.img + haze * glow * 0.18
    # near ridge: almost black, anti-aliased edge
    a_near = np.clip((near - el) * px_per_deg + 0.5, 0, 1)[..., None]
    fr.img = fr.img * (1 - a_near) + np.array([0.012, 0.014, 0.02]) * a_near


# ------------------------------------------------------------------ the scene (draw order as preview_sky.render)
def render(scene, cam_az, cam_el, t=6.0, rot=0.0):
    R = ps.R
    fr = ps.Frame(cam_az, cam_el, ps.rgb(scene["top"]), ps.rgb(scene["horizon"]))
    rays = fr.rays
    az = (np.degrees(np.arctan2(rays[..., 0], -rays[..., 2])) + 360) % 360
    el = np.degrees(np.arcsin(rays[..., 1]))
    # gentle large-scale variation in the sky itself so it isn't a flat gradient
    fr.img = fr.img * (0.9 + 0.2 * tiled(WISP, az / 360 * 3, el / 90 * 1.5)[..., None])

    g = scene.get("glow")
    if g:
        v = 1 - (el + 6) / 32
        okk = (v >= 0) & (v <= 1)
        s = ps.sample(ps.tex("horizon"), np.full_like(v, 0.5), np.clip(v, 0, 1))
        fr.over(ps.rgb(g["color"])[None, None], np.where(okk, s[..., 3] * g["alpha"], 0))

    mw = scene.get("milkyWay")
    if mw:
        ang = math.radians(mw["angle"] + rot)
        u0 = np.array([math.sin(ang), 0, -math.cos(ang)])
        v0 = np.array([math.cos(ang) * 0.5, math.sin(math.radians(60)), math.sin(ang) * 0.5]); v0 /= np.linalg.norm(v0)
        nrm = np.cross(u0, v0); nrm /= np.linalg.norm(nrm); v0 = np.cross(nrm, u0)
        dist = np.degrees(np.arcsin(np.clip(rays @ nrm, -1, 1)))
        phi = np.arctan2(rays @ v0, rays @ u0) % (2 * math.pi)
        hw = mw["width"]
        okk = np.abs(dist) <= hw
        s = ps.sample(ps.tex("milky_way"), phi / (2 * math.pi), 0.5 - 0.5 * dist / hw)
        tint_mw = ps.rgb(mw.get("tint", "#FFFFFF"))
        fr.over(s[..., :3] * tint_mw[None, None], np.where(okk, s[..., 3] * min(1.0, 0.85 * mw["alpha"]), 0))
        # dense faint star cloud inside the band
        band = np.clip(1 - np.abs(dist) / hw, 0, 1) ** 1.5
        dust = (rng.random((H, W)) ** 60) * band * 2.2
        fr.img = fr.img + dust[..., None] * tint_mw * 0.9
        if mw["alpha"] > 1.0:
            k = mw["alpha"] - 0.6
            for j in range(7):
                ph = math.pi / 2 + (j - 3) * math.radians(9)
                cd = u0 * math.cos(ph) + v0 * math.sin(ph)
                fall = math.exp(-((j - 3) / 2.2) ** 2)
                col = ps.rgb("#FFD9B0") if j % 2 == 0 else ps.rgb("#FF9A7A")
                fr.sprite("glow", cd, R * math.tan(math.radians(mw["width"] * (1.1 + 0.9 * fall))), col, 0.22 * k * fall)
            fr.sprite("glow", v0, R * math.tan(math.radians(mw["width"] * 0.9)), ps.rgb("#FFF1DC"), 0.35 * k)

    st = scene.get("stars")
    if st:
        draw_stars(fr, st, scale_density=2.6)

    for i, nb in enumerate(scene.get("nebulae", [])):
        for k in range(5):
            oaz = math.cos(k * 2.4 + i) * 0.45 * nb["size"]
            oel = math.sin(k * 1.7 + i) * 0.25 * nb["size"]
            psz = nb["size"] * (0.55 + 0.15 * (k % 3))
            d = ps.direction(nb["az"] + rot + oaz, nb["el"] + oel)
            breathe = 0.75 + 0.25 * math.sin(t * 0.25 + k + i)
            fr.sprite("nebula", d, R * math.tan(math.radians(psz * 0.5)), ps.rgb(nb["color"]), nb["alpha"] * breathe, uv_flip=(k % 2 == 1))

    for au in scene.get("auroras", []):
        c = [ps.rgb(x) for x in au["colors"]]
        uu = ((az - (au["az"] + rot - au["span"] / 2) + 540) % 360 - 180) / au["span"]
        tt = t * au.get("speed", 1.0)
        base = 12 + 4.5 * np.sin(uu * 7 + tt * 0.21) + 2.0 * np.sin(uu * 17 - tt * 0.37) + (au["base"] - 12)
        def wave(x): return 0.5 + 0.25 * np.sin(x) + 0.15 * np.sin(x * 2.37 + 1.3) + 0.10 * np.sin(x * 5.11 + 4.1)
        hh = au["height"] * (0.62 + 0.38 * wave(uu * 9 + tt * 0.17))
        f = (el - base) / hh
        okk = (uu >= 0) & (uu <= 1) & (f >= 0) & (f <= 1)
        ray = wave(uu * 31 - tt * 0.9)
        fine = 0.55 + 0.9 * tiled(RIDGE, uu * 18, f * 0.6)          # fine vertical curtain rays
        bright = np.minimum(1, 0.45 + 0.75 * ray ** 3 * 2) * 0.95 * np.sin(np.pi * np.clip(uu, 0, 1)) * fine
        s = ps.sample(ps.tex("aurora"), np.clip(uu, 0, 1), np.clip(1 - f, 0, 1))
        k1 = np.clip(f / 0.4, 0, 1)[..., None]; k2 = np.clip((f - 0.4) / 0.6, 0, 1)[..., None]
        col = np.where(f[..., None] < 0.4, c[0] * (1 - k1) + c[1] * k1, c[1] * (1 - k2) + c[2] * k2)
        fr.over(col, np.where(okk, s[..., 3] * bright * au["alpha"], 0))

    for p in scene.get("planets", []):
        d = ps.direction(p["az"] + rot, p["el"])
        half = R * math.tan(math.radians(p["size"] * 0.5)) / 0.42
        fr.sprite("glow", d, half * 1.6, ps.rgb(p["glow"]), 0.22)
        fr.sprite(p["texture"], d, half, np.ones(3), 1.0)
    for mo in scene.get("moons", []):
        d = ps.direction(mo["az"] + rot, mo["el"])
        half = R * math.tan(math.radians(mo["size"] * 0.5)) / 0.9
        fr.sprite("glow", d, half * 4.0, ps.rgb(mo["glow"]), mo.get("glowAlpha", 0.3))
        fr.sprite("moon", d, half, ps.rgb(mo["tint"]), 1.0)

    bh = scene.get("blackHole")
    if bh:
        col = [ps.rgb(x) for x in bh["colors"]]
        d = ps.direction(bh["az"] + rot, bh["el"])
        r_, u_ = ps.basis(d)
        rs = R * math.tan(math.radians(bh["size"] * 0.5))
        spin = t * 0.35 * bh.get("spin", 1.0)
        tilt = math.radians(bh.get("tilt", 12))
        a2 = -u_ * math.sin(tilt) + (-d) * math.cos(tilt)
        fr.sprite("glow", d, rs * 9, col[1], 0.30)
        fr.sprite("glow", d, rs * 5, col[0], 0.16)
        fr.sprite("glow", d, rs * 3.4, np.zeros(3), 0.55)

        def disk(near):
            c0 = d * R
            n = np.cross(r_, a2)
            den = rays @ n
            tt = (c0 @ n) / np.where(np.abs(den) < 1e-6, 1e-6, den)
            p = rays * tt[..., None] - c0
            x = p @ r_; y = p @ a2
            rad = np.sqrt(x * x + y * y)
            outer = rs * 4.2; inner = outer * 0.30
            th = np.arctan2(y, x)
            okk = (tt > 0) & (rad >= inner) & (rad <= outer) & ((np.sin(th) > 0) == near)
            uu = 0.5 + 0.5 * np.cos(th + spin) * rad / outer
            vv = 0.5 + 0.5 * np.sin(th + spin) * rad / outer
            dop = 0.55 + 0.45 * -np.cos(th)
            streaks = 0.6 + 0.8 * tiled(RIDGE, (th + spin) / (2 * math.pi) * 6, rad / outer * 9)   # orbiting gas streaks
            s = ps.sample(ps.tex("disk"), uu, vv)
            fr.over(col[1][None, None] * np.ones_like(s[..., :3]), np.where(okk, s[..., 3] * dop * streaks, 0))
            s2 = ps.sample(ps.tex("disk_core"), uu, vv)
            fr.over(col[0][None, None] * np.ones_like(s2[..., :3]), np.where(okk, s2[..., 3] * dop * 0.8, 0))

        disk(False)
        fr.sprite("lens", d, rs * 2.18, col[0], 0.95, r_, u_)
        fr.sprite("shadow", d, rs / 0.97, np.zeros(3), 1.0, r_, u_)
        fr.sprite("photon_ring", d, rs * 1.18, col[0], 0.9, r_, u_)
        disk(True)

    hq_ground(fr, ps.rgb(scene["horizon"]))
    return fr.img


def finish(img, centre_dark):
    h, w = img.shape[:2]
    # bloom: bright parts glow softly
    bright = np.clip(img - 0.55, 0, None)
    bloom = np.stack([gaussian_filter(bright[..., c], 6 * SS) for c in range(3)], -1) * 0.55
    bloom += np.stack([gaussian_filter(bright[..., c], 22 * SS) for c in range(3)], -1) * 0.35
    img = img + bloom
    # quieter centre where the buttons sit (same as before)
    yy, xx = np.mgrid[0:h, 0:w]
    d = np.sqrt(((xx - w / 2) / (w / 2)) ** 2 + ((yy - h * 0.55) / (h / 2)) ** 2)
    img = img * (centre_dark + (1 - centre_dark) * np.clip(d, 0, 1))[..., None]
    # soft filmic roll-off instead of hard clipping
    img = 1 - np.exp(-img * 1.15)
    img = np.clip(img, 0, 1)
    # scale down with a proper filter, per channel in float
    chans = [np.asarray(Image.fromarray(img[..., c].astype(np.float32), "F").resize((FINAL_W, FINAL_H), Image.LANCZOS)) for c in range(3)]
    out = np.stack(chans, -1)
    # triangular dither so the dark gradients don't band
    dither = (rng.random(out.shape) - rng.random(out.shape)) / 255.0
    out = np.clip(out + dither, 0, 1)
    return Image.fromarray(np.round(out * 255).astype(np.uint8), "RGB")


JOBS = [
    ("menu_bg.png", "Nebula", 75, 34, 0.55),
    ("menu_bg_galaxy.png", "Galaxy Core", 200, 30, 0.62),
    ("menu_bg_aurora.png", "Northern Lights", 0, 25, 0.62),
    ("menu_bg_blackhole.png", "Black Hole", 160, 27, 0.62),
    ("menu_bg_planet.png", "Ringed Giant", 200, 28, 0.62),
    ("menu_bg_bloodmoon.png", "Blood Moon", 100, 30, 0.62),
]

if __name__ == "__main__":
    only = set(ARGS)
    for fname, name, az, el, cd in JOBS:
        if only and fname not in only:
            continue
        scene = next(s for s in ps.SCENES if s["name"] == name)
        img = render(scene, az, el)
        finish(img, cd).save(os.path.join(OUT, fname), optimize=True)
        print("wrote", fname, os.path.getsize(os.path.join(OUT, fname)) // 1024, "KB")
