"""
Realistic textures for Tempest's Custom Sky (render/SkyRenderer.java):
  src/main/resources/assets/tempest/textures/sky/*.png

Same names, sizes-by-convention and layouts the renderer expects (it reads them by UV), so scenes.json keeps
working; only the look changes:
  moon          the near side of the Moon: maria, cratered highlands, ray systems (greyscale, tinted in game)
  milky_way     a 360-degree band: star clouds, dust lanes that redden what they cover, a warm core at u = 0.25
  nebula        a wispy cloud puff with filaments (greyscale, tinted)
  aurora        a curtain strip: sharp bright lower ribbon, fine rays fading upward (greyscale, tinted)
  star / star_bright   a seeing-disc point / a bright star with halo and faint diffraction spikes
  disk / disk_core     accretion disk: fine turbulent streaks, hotter toward the hole (radial UV)
  lens          the far side of the disk bent up over (and under) the shadow - taken from a real ray trace
  photon_ring   the thin ring of light at the shadow's edge
  planet_saturn / planet_ice / planet_lava   lit, shaded planets with rings and ring shadows

Run:  python tools/make_sky_real.py      (needs numpy, scipy, Pillow)
"""
import math
import os
import sys

import numpy as np
from PIL import Image
from scipy.ndimage import gaussian_filter

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import real_common as rc  # noqa: E402
from real_common import mix, rgb, smoothstep  # noqa: E402

OUT = os.environ.get("TEMPEST_OUT") or os.path.join(HERE, "..", "src", "main", "resources", "assets", "tempest",
                                                    "textures", "sky")


def save_rgba(name, rgb_srgb, alpha):
    os.makedirs(OUT, exist_ok=True)
    a = np.clip(alpha, 0, 1)
    out = np.dstack([np.clip(rgb_srgb, 0, 1), a])
    Image.fromarray((out * 255 + 0.5).astype(np.uint8), "RGBA").save(os.path.join(OUT, name + ".png"), optimize=True)
    print("  " + name, out.shape[1], "x", out.shape[0])


def save_white(name, alpha):
    h, w = alpha.shape
    save_rgba(name, np.ones((h, w, 3), np.float32), alpha)


def emission_to_rgba(lin, exposure=1.0):
    """Light on black -> colour + alpha, so alpha-blending it over a dark sky gives back the same light."""
    c = rc.to_srgb(rc.aces(lin * exposure))
    a = c.max(-1)
    col = c / np.maximum(a, 1e-4)[..., None]
    return col, a


def grid(n):
    y, x = np.mgrid[0:n, 0:n].astype(np.float32)
    return (x + 0.5) / n * 2 - 1, 1 - (y + 0.5) / n * 2


# ------------------------------------------------------------------ moon
def make_moon():
    alb = rc.moon_albedo(2048)
    shade, a = rc.moon_disc(512, alb, fill=0.9, limb=0.15)
    v = rc.to_srgb(np.clip(shade * 0.92, 0, 1))
    save_rgba("moon", np.dstack([v, v, v]), a)


# ------------------------------------------------------------------ milky way strip (u = 360 degrees, v = across)
def make_milky_way():
    h, w = 512, 4096
    N = 1024
    width = 105.0                                         # half-width of the bright band, in texture pixels
    yy, xx = np.mgrid[0:h, 0:w].astype(np.float32)
    u = xx - w * 0.25                                     # core at u = 0.25 (the band's highest point in game)
    u = (u + w / 2) % w - w / 2
    v = yy - h / 2
    across = v / width
    # noise sampled so it wraps a whole number of times around the strip. In game the strip is drawn about 1.7x
    # narrower per pixel along the band than across it, so the noise is squeezed along u to stay round on the sky.
    su = N / w * 12
    sv = N / (width * 3.2)
    gx = xx * su
    gy = v * sv + N / 2
    clouds = rc.fft_noise((N, N), 1.9, 301); clumps = rc.fft_noise((N, N), 1.3, 302)
    lanes = rc.fft_noise((N, N), 2.3, 303, aniso=(0.35, 1.0)); lanes_f = rc.fft_noise((N, N), 1.7, 304, aniso=(0.45, 1.0))
    grain_t = rc.fft_noise((N, N), 0.6, 305)
    c = rc.sample(clouds, gy, gx); k = rc.sample(clumps, gy * 2, gx * 2)
    l = rc.sample(lanes, gy, gx); lf = rc.sample(lanes_f, gy * 2, gx * 2)
    grain = rc.sample(grain_t, gy * 4, gx * 4)
    profile = np.exp(-across ** 2 * 1.0) + 0.35 * np.exp(-across ** 2 * 0.2)
    profile *= 1 - smoothstep(0.62, 1.0, np.abs(v) / (h / 2))          # fade to nothing at the strip edges
    star_clouds = np.clip(1.0 + 0.55 * c + 0.30 * k, 0.05, None) * np.clip(1.0 + 0.22 * grain, 0.3, None)
    glow = profile * star_clouds
    bu = np.exp(-(u / (width * 3.0)) ** 2 - (across / 1.25) ** 2)
    rift = np.exp(-((across + 0.08 * c) / 0.30) ** 2) * smoothstep(-0.4, 0.8, l)
    fil = rc.ridged(lf * 1.3) ** 4 * np.exp(-across ** 2 * 0.8)
    patches = smoothstep(0.6, 1.8, l) * np.exp(-across ** 2 * 0.6)
    tau = 1.15 * (1.3 * rift + 0.9 * fil + 0.8 * patches) * (0.6 + 0.4 * profile)
    tau += 1.6 * bu * np.exp(-((across - 0.05 + 0.06 * l) / 0.14) ** 2)
    col_arm, col_core = rgb(222, 226, 240), rgb(255, 214, 166)
    col = mix(col_arm, col_core, smoothstep(0.0, 1.0, bu * 2.2)[..., None])
    emis = (0.075 * glow)[..., None] * col + (0.40 * bu * (1 + 0.3 * c))[..., None] * col_core
    ha = smoothstep(1.8, 2.8, rc.sample(clumps, gy * 1.6 + 300, gx * 2)) * profile
    emis += (0.04 * ha)[..., None] * rgb(255, 92, 120)
    ext = np.exp(-tau[..., None] * np.array([0.70, 1.0, 1.35], np.float32))
    emis *= ext
    # faint resolved stars packed into the band (bright ones are drawn by the game as separate sprites)
    dens = (0.15 + 9.0 * profile * star_clouds + 10 * bu) * np.exp(-tau)
    st = rc.starfield(h, w, 60000, seed=306, density=dens, bright=0.30, max_flux=4.0, sigma=0.7)
    st *= ext.mean(-1, keepdims=True)
    lin = emis + st
    col, a = emission_to_rgba(lin, 0.85)
    save_rgba("milky_way", col, a)


# ------------------------------------------------------------------ nebula puff
def make_nebula():
    n = 512
    x, y = grid(n)
    r = np.sqrt(x * x + y * y)
    big = rc.fft_noise((n, n), 2.8, 311); mid = rc.fft_noise((n, n), 2.0, 312); fine = rc.fft_noise((n, n), 1.5, 313)
    wx = rc.fft_noise((n, n), 3.0, 314) * 18; wy = rc.fft_noise((n, n), 3.0, 315) * 18
    m = rc.warp(mid, wx, wy); f = rc.warp(fine, wx, wy)
    d = np.clip(0.55 + 0.35 * big + 0.30 * m, 0, None) ** 1.8 * np.clip(0.75 + 0.3 * f, 0.2, None)
    d *= 0.6 + 0.9 * rc.ridged(m * 0.9) ** 2                            # filaments
    d *= np.exp(-(r / 0.62) ** 2) * (1 - smoothstep(0.85, 1.0, r))
    dust = smoothstep(0.6, 1.6, rc.warp(big, wy, wx) + 0.4 * m)
    d *= np.exp(-1.2 * dust)
    a = np.clip(d / np.percentile(d, 99.7), 0, 1) ** 0.9
    save_white("nebula", a)


# ------------------------------------------------------------------ aurora strip
def make_aurora():
    h, w = 256, 1024
    yy, xx = np.mgrid[0:h, 0:w].astype(np.float32)
    hgt = (h - 1 - yy) / (h - 1)                                       # 0 at the bottom (v = 1), 1 at the top
    rays = rc.fft_noise((h, w), 1.3, 321, aniso=(1.0, 0.06))           # long vertical rays
    rays = np.clip(0.55 + 0.45 * rays, 0, None) ** 2.0
    env = 0.7 + 0.3 * np.tanh(rc.noise1d(w, 2.4, 322))[None, :]
    rmod = mix(0.85, 0.25 + 0.75 * rays, smoothstep(0.0, 0.18, hgt))
    ribbon = 1.0 + 1.2 * np.exp(-hgt / 0.05)
    onset = smoothstep(0.0, 0.03, hgt)
    decay = np.exp(-hgt / 0.62)
    a = onset * decay * rmod * ribbon * env
    a = np.clip(a / np.percentile(a, 99.5), 0, 1)
    save_white("aurora", a)


# ------------------------------------------------------------------ stars
def make_stars():
    for name, n, core, halo, spikes in (("star", 32, 0.09, 0.0, 0.0), ("star_bright", 64, 0.045, 0.22, 0.25)):
        x, y = grid(n)
        r = np.sqrt(x * x + y * y)
        a = np.exp(-(r / core) ** 2 * 0.5)
        if halo:
            a += halo * np.exp(-r / 0.18) * (1 - smoothstep(0.7, 1.0, r))
        if spikes:
            sp = (np.exp(-np.abs(x) / 0.30) * np.exp(-(y / 0.012) ** 2) +
                  np.exp(-np.abs(y) / 0.30) * np.exp(-(x / 0.012) ** 2))
            a += spikes * sp * (1 - smoothstep(0.75, 1.0, r))
        a = np.clip(a / a.max(), 0, 1) * (1 - smoothstep(0.9, 1.0, r))
        save_white(name, a)


# ------------------------------------------------------------------ accretion disk (radial UV)
def make_disk():
    n = 512
    x, y = grid(n)
    r = np.sqrt(x * x + y * y)                                        # 1 = outer edge, 0.30 = inner edge
    phi = np.arctan2(y, x)
    tr, ta = 512, 2048
    streak = rc.fft_noise((tr, ta), 1.6, 331, aniso=(0.06, 1.0))
    clump = rc.fft_noise((tr, ta), 2.2, 332, aniso=(0.3, 1.0))
    lr = np.clip((r - 0.30) / 0.70, 0, 1)
    ty = lr * (tr - 1)
    tx = ((phi / (2 * math.pi) + 0.5) + 0.10 * lr) % 1.0 * ta
    s = rc.sample(streak, ty, tx); c = rc.sample(clump, ty * 0.5, tx * 0.5)
    dens = np.clip(0.70 + 0.55 * s + 0.30 * c, 0.03, None) ** 1.3
    inner = smoothstep(0.28, 0.34, r)
    outer = 1 - smoothstep(0.80, 1.0, r)
    heat = np.exp(-(r - 0.30) / 0.30)                                  # brighter toward the hole
    a_disk = np.clip(dens * inner * outer * (0.35 + 0.65 * heat), 0, 1)
    save_white("disk", a_disk)
    core = np.clip(dens * inner * np.exp(-((r - 0.36) / 0.10) ** 2) * 1.2, 0, 1)
    save_white("disk_core", core)


# ------------------------------------------------------------------ lens arc + photon ring (from the ray tracer)
def make_lens():
    import make_menu_real as mm
    n = 256
    # trace a small view of the hole: the shadow (radius ~5.2 M) must land at 1/2.18 of the sprite's half-size
    cam_dist = 60.0
    shadow_ang = math.atan(5.196 / cam_dist)
    half_ang = shadow_ang * 2.18
    fov = math.degrees(2 * half_ang)
    disk, trans, ex, ey, esc = mm.trace_black_hole(n, n, n / 2, n / 2, fov_deg=fov, cam_dist=cam_dist, cam_el_deg=12,
                                                   roll_deg=0, r_in=6.0, r_out=20.0, chunk=200000, far_only=True)
    lum = disk.mean(-1)
    x, y = grid(n)
    r = np.sqrt(x * x + y * y)
    a = lum / np.percentile(lum[lum > 0], 99.0)
    a = np.clip(a, 0, 1) * (1 - smoothstep(0.80, 1.0, r))
    save_white("lens", a ** 0.85)
    # photon ring: thin bright ring exactly at the shadow edge (r = 1/1.18 of this sprite), a faint second one inside
    rr = 1.0 / 1.18
    ring = np.exp(-((r - rr) / 0.012) ** 2) + 0.35 * np.exp(-((r - rr * 1.035) / 0.02) ** 2) + 0.15 * np.exp(-np.clip(r - rr, 0, None) / 0.05) * (r > rr)
    save_white("photon_ring", np.clip(ring, 0, 1))


# ------------------------------------------------------------------ planets
def make_planets():
    for name, kind, seed in (("planet_saturn", "saturn", 341), ("planet_ice", "ice", 342), ("planet_lava", "lava", 343)):
        col, a = rc.planet_render(512, kind, fill=0.42, light=(-0.55, 0.35, 0.76), tilt=-14, view_el=14, seed=seed)
        lin = col / np.maximum(a, 1e-4)[..., None] * 1.25
        c = rc.to_srgb(rc.aces(lin))
        save_rgba(name, c, a)


JOBS = {"moon": make_moon, "milky_way": make_milky_way, "nebula": make_nebula, "aurora": make_aurora,
        "stars": make_stars, "disk": make_disk, "lens": make_lens, "planets": make_planets}

if __name__ == "__main__":
    for j in (sys.argv[1:] or list(JOBS)):
        print(j)
        JOBS[j]()
