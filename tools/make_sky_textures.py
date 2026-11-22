"""
Pre-renders the textures used by Tempest's Custom Sky (gui/SkyRenderer.java):
  src/main/resources/assets/tempest/textures/sky/*.png

Everything here is original procedural art. Run:  python tools/make_sky_textures.py   (needs numpy + Pillow)
Most textures are white / greyscale so the game can tint them; the planets are baked in colour.
"""
import math
import os
import sys

import numpy as np
from PIL import Image

OUT = sys.argv[1] if len(sys.argv) > 1 else os.path.join(os.path.dirname(__file__), "..",
                                                        "src/main/resources/assets/tempest/textures/sky")
os.makedirs(OUT, exist_ok=True)
rng = np.random.default_rng(1337)


def save(name, rgba):
    rgba = np.clip(rgba, 0, 1)
    Image.fromarray((rgba * 255 + 0.5).astype(np.uint8), "RGBA").save(os.path.join(OUT, name + ".png"))
    print("  " + name, rgba.shape[1], "x", rgba.shape[0])


def white(alpha):
    h, w = alpha.shape
    out = np.ones((h, w, 4))
    out[..., 3] = alpha
    return out


def grid(n):
    y, x = np.mgrid[0:n, 0:n]
    return (x + 0.5) / n * 2 - 1, (y + 0.5) / n * 2 - 1      # -1..1, y down


def smooth(e0, e1, x):
    t = np.clip((x - e0) / (e1 - e0), 0, 1)
    return t * t * (3 - 2 * t)


def value_noise(shape, scale, octaves=5, seed=0, wrap_x=False):
    """Fractal value noise in 0..1 (bilinear-interpolated random lattices)."""
    r = np.random.default_rng(seed)
    h, w = shape
    total = np.zeros(shape)
    amp, norm = 1.0, 0.0
    for o in range(octaves):
        cells_x = max(2, int(w / scale * (2 ** o)))
        cells_y = max(2, int(h / scale * (2 ** o)))
        lat = r.random((cells_y + 1, cells_x + 1))
        if wrap_x:
            lat[:, -1] = lat[:, 0]
        ys = np.linspace(0, cells_y, h, endpoint=False)
        xs = np.linspace(0, cells_x, w, endpoint=False)
        y0 = ys.astype(int); x0 = xs.astype(int)
        fy = (ys - y0)[:, None]; fx = (xs - x0)[None, :]
        fy = fy * fy * (3 - 2 * fy); fx = fx * fx * (3 - 2 * fx)
        a = lat[y0][:, x0]; b = lat[y0][:, x0 + 1]
        c = lat[y0 + 1][:, x0]; d = lat[y0 + 1][:, x0 + 1]
        total += amp * ((a * (1 - fx) + b * fx) * (1 - fy) + (c * (1 - fx) + d * fx) * fy)
        norm += amp
        amp *= 0.5
    return total / norm


# ------------------------------------------------------------------ simple sprites
def glow():
    x, y = grid(128)
    r = np.sqrt(x * x + y * y)
    save("glow", white(np.clip(1 - r, 0, 1) ** 2.2))


def star():
    x, y = grid(32)
    r = np.sqrt(x * x + y * y)
    core = np.exp(-(r / 0.16) ** 2)
    halo = 0.35 * np.clip(1 - r, 0, 1) ** 3
    save("star", white(np.clip(core + halo, 0, 1)))


def streak():
    h, w = 16, 128
    y, x = np.mgrid[0:h, 0:w]
    u = (x + 0.5) / w                       # 0 tail .. 1 head
    v = (y + 0.5) / h * 2 - 1
    width = 0.15 + 0.85 * u ** 2
    a = np.exp(-(v / (0.35 * width)) ** 2) * u ** 1.6
    a += np.exp(-((u - 0.97) / 0.03) ** 2 - (v / 0.5) ** 2) * 0.8
    save("streak", white(np.clip(a, 0, 1)))


def nebula():
    n = 256
    x, y = grid(n)
    r = np.sqrt(x * x + y * y)
    cloud = value_noise((n, n), 90, 6, seed=11)
    wisps = value_noise((n, n), 40, 5, seed=12)
    a = smooth(0.38, 0.75, cloud * 0.75 + wisps * 0.45) * np.clip(1 - r, 0, 1) ** 1.4
    a = a * 0.85 + 0.25 * np.clip(1 - r, 0, 1) ** 3
    save("nebula", white(np.clip(a, 0, 1)))


def star_bright():
    """Brightest stars: a core, a halo and four soft diffraction spikes."""
    x, y = grid(64)
    r = np.sqrt(x * x + y * y)
    core = np.exp(-(r / 0.08) ** 2)
    halo = 0.45 * np.clip(1 - r, 0, 1) ** 4
    spikes = (np.exp(-(np.abs(y) / 0.025) ** 2) * np.clip(1 - np.abs(x), 0, 1) ** 2.5 +
              np.exp(-(np.abs(x) / 0.025) ** 2) * np.clip(1 - np.abs(y), 0, 1) ** 2.5) * 0.8
    save("star_bright", white(np.clip(core + halo + spikes, 0, 1)))


def horizon():
    """Vertical glow strip: strongest at the bottom (just under the horizon), gone by the top."""
    h, w = 128, 8
    f = 1 - (np.mgrid[0:h, 0:w][0] + 0.5) / h          # 0 bottom .. 1 top
    a = np.where(f < 0.2, 0.75 + 0.25 * f / 0.2, (1 - (f - 0.2) / 0.8) ** 2.2)
    save("horizon", white(np.clip(a, 0, 1)))


def moon():
    """Big cratered moon, greyscale so it can be tinted (white moon, blood moon...)."""
    n = 512
    x, y = grid(n)
    R = 0.9
    px, py = x / R, y / R
    pr = np.sqrt(px * px + py * py)
    nz = np.sqrt(np.clip(1 - px * px - py * py, 0, 1))
    lit = np.clip(px * -0.45 + py * -0.35 + nz * 0.82, 0, 1)
    maria = value_noise((n, n), 140, 4, seed=71)
    lum = 0.62 + 0.38 * smooth(0.35, 0.7, maria)
    r2 = np.random.default_rng(72)
    for _ in range(90):
        cx, cy = r2.uniform(-1, 1, 2)
        if cx * cx + cy * cy > 0.95:
            continue
        rad = r2.uniform(0.02, 0.12) * (1 if r2.random() > 0.15 else 1.8)
        d = np.sqrt((px - cx) ** 2 + (py - cy) ** 2) / rad
        lum = lum - 0.16 * np.exp(-(d / 0.8) ** 4) + 0.10 * np.exp(-((d - 1.0) / 0.18) ** 2)
    shade = 0.18 + 0.9 * lit ** 0.7
    v = np.clip(lum * shade, 0, 1)
    out = np.ones((n, n, 4))
    out[..., 0] = v; out[..., 1] = v; out[..., 2] = v
    out[..., 3] = 1 - smooth(0.985, 1.0, pr)
    save("moon", out)


# ------------------------------------------------------------------ black hole parts
def disk():
    """Face-on accretion disk, white; the game tints it and squashes it into a tilted ellipse."""
    n = 512
    x, y = grid(n)
    r = np.sqrt(x * x + y * y)
    th = np.arctan2(y, x)
    inner, outer = 0.36, 1.0
    t = np.clip((r - inner) / (outer - inner), 0, 1)
    band = smooth(inner - 0.02, inner + 0.05, r) * (1 - smooth(0.82, 1.0, r))
    # concentric streaked bands: vary quickly with radius, slowly around the disk
    rings = (0.5 + 0.5 * np.sin(r * 70 + 1.5 * np.sin(th * 3 + 1) + 0.8 * np.sin(th * 7))) ** 2
    fine = 0.5 + 0.5 * np.sin(r * 190 + 2.0 * np.sin(th * 5 + 2) + 0.6 * np.sin(th * 11))
    clumps = 0.6 + 0.4 * np.sin(th * 4 + r * 9) * np.sin(th * 9 - r * 5)
    st = np.clip((0.6 * rings + 0.4 * fine) * clumps, 0, 1)
    lum = (0.22 + 1.0 * st) * (1.0 - 0.72 * t)
    a = band * np.clip(lum, 0, 1)
    save("disk", white(a))
    # hot inner rim, drawn untinted on top
    core = smooth(inner - 0.02, inner + 0.03, r) * (1 - smooth(inner + 0.06, inner + 0.22, r)) * (0.6 + 0.6 * st)
    save("disk_core", white(np.clip(core, 0, 1)))


def lens():
    """Light from the far side of the disk bent up and over the shadow (bright on top, faint underneath)."""
    n = 256
    x, y = grid(n)
    r = np.sqrt(x * x + y * y)
    up = np.clip(-y / np.maximum(r, 1e-6), -1, 1)
    shape = np.where(up > 0, np.abs(up) ** 0.6, 0.22 * up * up)
    ring = np.exp(-((r - 0.62) / 0.09) ** 2) + 0.5 * np.exp(-((r - 0.75) / 0.05) ** 2)
    streaks = value_noise((n, n), 30, 4, seed=31)
    save("lens", white(np.clip(ring * shape * (0.55 + 0.6 * streaks), 0, 1)))


def shadow():
    n = 256
    x, y = grid(n)
    r = np.sqrt(x * x + y * y)
    a = 1 - smooth(0.94, 1.0, r)
    out = np.zeros((n, n, 4))
    out[..., 3] = a
    save("shadow", out)
    ring = np.exp(-((r - 0.9) / 0.035) ** 2)
    save("photon_ring", white(np.clip(ring, 0, 1)))


# ------------------------------------------------------------------ planets (baked colour, rings included)
def planet(name, light_band, dark_band, ring_col, seed):
    n = 512
    x, y = grid(n)
    out = np.zeros((n, n, 4))
    R = 0.42
    px, py = x / R, y / R
    pr = np.sqrt(px * px + py * py)
    inside = pr <= 1
    nz = np.sqrt(np.clip(1 - px * px - py * py, 0, 1))
    lx, ly, lz = -0.55, -0.45, 0.70
    lit = np.clip(px * lx + py * ly + nz * lz, 0, 1)
    shade = 0.10 + 0.95 * lit ** 0.8
    wob = value_noise((n, n), 60, 4, seed=seed)
    band = 0.5 + 0.5 * np.sin(py * 11 + np.sin(py * 3) * 1.5 + (wob - 0.5) * 2.2)
    col = np.array(dark_band)[None, None, :] * (1 - band[..., None]) + np.array(light_band)[None, None, :] * band[..., None]
    col = col * shade[..., None]
    edge = 1 - smooth(0.985, 1.0, pr)
    # ring (tilted ellipse), split into the half behind the planet and the half in front
    tilt, roll = 0.32, math.radians(18)
    cr, sr = math.cos(roll), math.sin(roll)
    rx = (x * cr + y * sr)
    ry = (-x * sr + y * cr) / tilt
    rr = np.sqrt(rx * rx + ry * ry)
    rn = (rr - 0.55) / (0.97 - 0.55)
    ring_a = smooth(-0.02, 0.03, rn) * (1 - smooth(0.95, 1.02, rn))
    ring_a *= np.where((rn > 0.55) & (rn < 0.62), 0.12, 1.0)              # Cassini gap
    ring_a *= 0.45 + 0.35 * (0.5 + 0.5 * np.sin(rn * 60)) + 0.2 * value_noise((n, n), 25, 3, seed=seed + 1)
    ring_a = np.clip(ring_a, 0, 1)
    front = ry > 0
    ring_rgb = np.array(ring_col)[None, None, :] * (0.75 + 0.25 * np.clip(-rx / 0.97, -1, 1)[..., None])
    # back ring, then planet over it, then front ring over that
    def over(dst, rgb, a):
        a = a[..., None]
        dst[..., :3] = rgb * a + dst[..., :3] * (1 - a)
        dst[..., 3:4] = a + dst[..., 3:4] * (1 - a)
    over(out, ring_rgb, np.where(~front & ~inside, ring_a, 0) * 0.9)
    over(out, col, (inside * edge).astype(float))
    # planet shadow falling on the front ring is skipped for simplicity; the front ring sits on top
    over(out, ring_rgb, np.where(front, ring_a, 0) * 0.9)
    save(name, out)


# ------------------------------------------------------------------ aurora and galaxy band
def aurora():
    """One curtain tile: bright lower edge, long fade upward, fine vertical rays. Tiles horizontally."""
    h, w = 256, 128
    y, x = np.mgrid[0:h, 0:w]
    f = 1 - (y + 0.5) / h                 # 0 bottom .. 1 top
    base = np.where(f < 0.10, 0.25 + 0.75 * f / 0.10, (1 - (f - 0.10) / 0.90) ** 1.6)
    rays = value_noise((h, w), 6, 3, seed=41, wrap_x=True)
    rays = rays.mean(axis=0, keepdims=True) * 0.7 + rays * 0.3       # mostly vertical streaks
    a = base * (0.35 + 0.95 * rays ** 1.5)
    save("aurora", white(np.clip(a, 0, 1)))


def milky_way():
    """Panoramic galaxy band, wraps horizontally: dust glow, dark lanes and dense stars."""
    h, w = 256, 2048
    y, x = np.mgrid[0:h, 0:w]
    v = (y + 0.5) / h * 2 - 1
    u = (x + 0.5) / w
    core = np.exp(-((u - 0.5) / 0.16) ** 2)
    width = 0.32 + 0.30 * core
    prof = np.exp(-(v / width) ** 2)
    dust = value_noise((h, w), 120, 6, seed=51, wrap_x=True)
    # dust lanes are long and thin: make the noise at a quarter of the width and stretch it
    lanes_small = value_noise((h, w // 4), 30, 5, seed=52, wrap_x=True)
    lanes = np.asarray(Image.fromarray((lanes_small * 255).astype(np.uint8)).resize((w, h), Image.BICUBIC)) / 255.0
    lane_cut = 1 - 0.75 * smooth(0.55, 0.75, lanes) * np.exp(-(v / 0.18) ** 2)
    glow_a = prof * (0.35 + 0.65 * dust) * lane_cut * (0.45 + 0.55 * core)
    out = np.zeros((h, w, 4))
    warm = np.array([1.0, 0.88, 0.74]); cool = np.array([0.74, 0.80, 1.0])
    mix = np.clip(core * 1.2, 0, 1)[..., None]
    out[..., :3] = cool * (1 - mix) + warm * mix
    out[..., 3] = glow_a * 0.55
    # stars, denser toward the middle of the band
    n_st = 26000
    sx = rng.random(n_st) * w
    sv = rng.normal(0, 0.45, n_st)
    sy = (np.clip(sv, -0.98, 0.98) + 1) / 2 * h
    bright = rng.random(n_st) ** 3
    for i in range(n_st):
        xi, yi = int(sx[i]) % w, int(sy[i])
        b = 0.35 + 0.65 * bright[i]
        out[yi, xi, :3] = 1.0
        out[yi, xi, 3] = max(out[yi, xi, 3], b)
        if bright[i] > 0.6:
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                xj, yj = (xi + dx) % w, min(h - 1, max(0, yi + dy))
                out[yj, xj, :3] = 1.0
                out[yj, xj, 3] = max(out[yj, xj, 3], b * 0.4)
    out[..., 3] *= 1 - smooth(0.75, 1.0, np.abs(v))
    save("milky_way", out)


if __name__ == "__main__":
    print("writing to", os.path.abspath(OUT))
    glow(); star(); star_bright(); horizon(); moon(); streak(); nebula()
    disk(); lens(); shadow()
    planet("planet_saturn", (0.95, 0.85, 0.66), (0.77, 0.60, 0.38), (0.90, 0.82, 0.69), 61)
    planet("planet_ice", (0.75, 0.94, 1.0), (0.35, 0.66, 0.88), (0.85, 0.91, 1.0), 62)
    planet("planet_lava", (1.0, 0.60, 0.29), (0.35, 0.10, 0.06), (1.0, 0.78, 0.56), 63)
    aurora(); milky_way()
