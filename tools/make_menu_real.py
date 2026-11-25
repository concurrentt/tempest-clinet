"""
Photoreal main menu backgrounds for Tempest (textures/gui/menu_bg*.png, 1920x1080).

Same six themes as before - Nebula, Galaxy, Aurora, Black Hole, Ringed Planet, Blood Moon - rendered the way a
camera would see them: linear light, blackbody star colours, a Milky Way with dust lanes, a ray-traced black
hole (real Schwarzschild light bending, Doppler-brightened disk), lit and shadowed planets, mountain ranges that
fade into haze, then lens bloom, a filmic curve and fine grain.

Run:  python tools/make_menu_real.py [theme ...]      (needs numpy, scipy, Pillow; the black hole takes a few minutes)
      themes: nebula galaxy aurora blackhole planet bloodmoon   (default: all)
"""
import math
import os
import sys
import time

import numpy as np
from PIL import Image
from scipy.ndimage import gaussian_filter

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import real_common as rc  # noqa: E402
from real_common import mix, rgb, smoothstep  # noqa: E402

OUT = os.environ.get("TEMPEST_OUT") or os.path.join(HERE, "..", "src", "main", "resources", "assets", "tempest", "textures", "gui")
W, H = 1920, 1080


def save(name, img8):
    os.makedirs(OUT, exist_ok=True)
    path = os.path.join(OUT, name + ".png")
    Image.fromarray(img8).save(path, optimize=True)
    print("  wrote", os.path.normpath(path))


# ======================================================================== Milky Way
def band_coords(h, w, p0, angle_deg, curve=0.0):
    """(along, across) pixel coordinates for a gently curved band through p0 at angle (degrees, screen)."""
    yy, xx = np.mgrid[0:h, 0:w].astype(np.float32)
    a = math.radians(angle_deg)
    dx, dy = xx - p0[0], yy - p0[1]
    u = dx * math.cos(a) + dy * math.sin(a)
    v = -dx * math.sin(a) + dy * math.cos(a)
    v = v - curve * u * u
    return u, v


def milky_way(h, w, p0, angle, width, core_u=0.0, core=1.0, curve=0.0, seed=21, dust=1.0, bright=1.0):
    """
    Emission (linear RGB), star density and extinction for a Milky Way band.
    width = half-width of the bright band in pixels.
    """
    u, v = band_coords(h, w, p0, angle, curve)
    N = 1024
    clouds = rc.fft_noise((N, N), 1.9, seed)
    clumps = rc.fft_noise((N, N), 1.3, seed + 1)
    lanes = rc.fft_noise((N, N), 2.3, seed + 2, aniso=(0.35, 1.0))   # filaments stretched along the band
    lanes_f = rc.fft_noise((N, N), 1.7, seed + 3, aniso=(0.45, 1.0))
    # sample in band space so structure follows the band (scale: N px of noise ~ 3.2 band widths across)
    su = N / (width * 9.0)
    sv = N / (width * 3.2)
    gy = v * sv + N / 2
    gx = u * su + N / 2
    c = rc.sample(clouds, gy, gx)
    k = rc.sample(clumps, gy * 2.3, gx * 2.3)
    l = rc.sample(lanes, gy, gx)
    lf = rc.sample(lanes_f, gy * 1.9, gx * 1.9)

    across = v / width
    profile = np.exp(-across ** 2 * 1.0) + 0.35 * np.exp(-across ** 2 * 0.15)
    grain = rc.sample(rc.fft_noise((N, N), 0.6, seed + 9), gy * 5.0, gx * 5.0)       # unresolved stars
    star_clouds = np.clip(1.0 + 0.55 * c + 0.30 * k, 0.05, None) * np.clip(1.0 + 0.22 * grain, 0.3, None)
    glow = profile * star_clouds
    # the bulge: a warm swelling of the band around the core
    bu = np.exp(-((u - core_u) / (width * 2.6)) ** 2 - (across / 1.25) ** 2)
    bulge = core * bu * (1.0 + 0.3 * c)

    # dust: the Great Rift down the middle plus dark filaments and patches
    rift = np.exp(-((across + 0.08 * c) / 0.30) ** 2) * smoothstep(-0.4, 0.8, l)
    fil = rc.ridged(lf * 1.3) ** 4 * np.exp(-across ** 2 * 0.8)
    patches = smoothstep(0.6, 1.8, l) * np.exp(-across ** 2 * 0.6)
    tau = dust * (1.3 * rift + 0.9 * fil + 0.8 * patches) * (0.6 + 0.4 * profile)
    tau += dust * 1.4 * bu * np.exp(-((across - 0.05 + 0.06 * l) / 0.14) ** 2)      # dark lane across the core

    col_arm = rgb(222, 226, 240)
    col_core = rgb(255, 214, 166)
    warm = smoothstep(0.0, 1.0, bu * 2.2)[..., None]
    col = mix(col_arm, col_core, warm)
    emis = (0.055 * glow)[..., None] * col + (0.30 * bulge)[..., None] * col_core
    # hydrogen-alpha clouds (pink) scattered along the band
    ha = smoothstep(1.8, 2.8, rc.sample(clumps, gy * 1.6 + 300, gx * 1.6)) * profile
    emis += (0.035 * ha)[..., None] * rgb(255, 92, 120)
    ext = np.exp(-tau[..., None] * np.array([0.70, 1.0, 1.35], np.float32))   # dust reddens what it lets through
    emis = emis * ext * bright
    dens = 1.0 + 9.0 * profile * star_clouds + 12.0 * bulge
    dens = dens * np.exp(-tau * 1.1)
    return emis.astype(np.float32), dens.astype(np.float32), ext.astype(np.float32)


def night_sky(horizon_y, zenith=rgb(4, 6, 14), horizon=rgb(22, 30, 46), airglow=rgb(14, 22, 12), dome=None):
    sky = rc.sky_gradient(H, W, horizon_y, zenith * 0.05, horizon * 0.11, glow=airglow * 0.09, glow_h=0.10)
    if dome is not None:
        # light from a town far beyond the hills: an orange dome at one spot on the horizon
        x0, strength = dome
        yy, xx = np.mgrid[0:H, 0:W].astype(np.float32)
        d = ((xx - x0) / (W * 0.35)) ** 2 + ((yy - horizon_y) / (H * 0.22)) ** 2
        sky += (strength * np.exp(-d))[..., None] * rgb(255, 160, 95) * 0.018
    return sky


def extinction_near_horizon(img, horizon_y, height=0.22):
    """Stars and glow fade and redden low in the sky (more air to look through)."""
    yy = np.arange(H, dtype=np.float32)[:, None]
    e = np.clip((horizon_y - yy) / (H * height), 0, 1)
    k = (0.25 + 0.75 * e ** 0.8)[..., None]
    redden = mix(np.array([1.0, 0.85, 0.65], np.float32), np.ones(3, np.float32), e[..., None] ** 0.5)
    img *= k * redden
    return img


# ======================================================================== themes
def theme_galaxy():
    """The Milky Way's core rising over layered mountains and a forest edge."""
    hy = 880
    sky = night_sky(hy, dome=(380, 0.8))
    emis, dens, ext = milky_way(H, W, p0=(1250, 760), angle=-60, width=105, core_u=-30, core=1.0, curve=0.00010,
                                seed=31, dust=1.15, bright=1.0)
    stars = rc.starfield(H, W, 60000, seed=32, density=dens ** 1.2, bright=0.45, max_flux=40)
    img = sky + emis + stars * ext.mean(-1, keepdims=True) ** 0.6
    img = extinction_near_horizon(img, hy)
    horizon_col = rgb(60, 72, 98) * 0.16
    rc.layered_mountains(img, horizon_col, [(845, 160, 0.25, 0.55), (885, 150, 0.55, 0.45), (945, 120, 0.82, 0.35),
                                            (1030, 80, 0.97, 0.15)], seed=33,
                         fog_col=rgb(56, 64, 84) * 0.09, trees=dict(density=0.05, hmin=24, hmax=80))
    save("menu_bg_galaxy", rc.finish(img, exposure=1.7, grain=0.010, seed=34))

# ------------------------------------------------------------------ shared pieces for several themes
def lake_reflection(img, water_y, seed=5, dim=0.55, ripple=3.0):
    """Mirror everything above water_y into a calm lake: wind ripples, a little vertical smear, Fresnel dimming."""
    h, w = img.shape[:2]
    yy, xx = np.mgrid[0:h, 0:w].astype(np.float32)
    below = yy > water_y
    dist = np.clip(yy - water_y, 0, None)
    rip = rc.fft_noise((h, w), 2.2, seed, aniso=(0.6, 4.0))
    src_y = 2 * water_y - yy + rip * ripple * 0.6 * (1 + dist / 120)
    src_x = xx + rip * ripple * (0.5 + dist / 90)
    refl = rc.sample(img, np.clip(src_y, 0, h - 1), np.clip(src_x, 0, w - 1), mode="nearest")
    refl = np.stack([gaussian_filter(refl[..., c], (2.5, 0.6)) for c in range(3)], -1)
    fres = dim * (0.75 + 0.25 * np.exp(-dist / 60))[..., None]
    wind = 1 - 0.25 * smoothstep(0.8, 2.0, rc.fft_noise((h, w), 2.6, seed + 1, aniso=(0.5, 6.0)))[..., None]
    img[:] = np.where(below[..., None], refl * fres * wind, img)
    # a faint bright line where the water meets the shore
    img += (np.exp(-np.abs(yy - water_y - 1) / 0.8) * 0.0)[..., None]
    return img


def aurora_light(curtains, seed=41):
    """
    Aurora curtains: a sharp lower edge, fine vertical rays, green oxygen light fading upward into red,
    a thin pink nitrogen fringe at the bottom. Returns linear RGB emission.
    curtains: list of dicts(y0, arch, amp, height, bright, phase)
    """
    yy, xx = np.mgrid[0:H, 0:W].astype(np.float32)
    out = np.zeros((H, W, 3), np.float32)
    green, red, pink = rgb(120, 255, 140), rgb(200, 50, 110), rgb(255, 90, 190)
    for i, c in enumerate(curtains):
        r = np.random.default_rng(seed + i)
        xs = np.arange(W, dtype=np.float32)
        base = (c["y0"] - c["arch"] * np.sin(np.pi * (xs / W * 0.9 + 0.05)) + c.get("slope", 0.0) * (xs - W / 2) +
                c["amp"] * rc.noise1d(W * 2, 3.0, seed + 10 * i)[:W] * 0.6 +
                c["amp"] * 0.45 * rc.noise1d(W * 2, 3.4, seed + 10 * i + 1)[:W] + 6.0 * rc.noise1d(W * 2, 2.6, seed + 10 * i + 5)[:W])  # folds
        env = (0.62 + 0.32 * np.tanh(0.8 * rc.noise1d(W * 2, 2.6, seed + 10 * i + 2)[:W])) ** 1.3
        hgt = c["height"] * (0.6 + 0.4 * np.clip(0.5 + 0.4 * rc.noise1d(W * 2, 2.8, seed + 10 * i + 3)[:W], 0, 1))
        h_ = base[None, :] - yy                                   # pixels above the curtain's lower edge
        # rays lean toward a point far above the frame (perspective toward the magnetic zenith)
        lean = (xx - W * 0.5) * 0.10 * np.clip(h_ / 400.0, 0, None)
        rays_tex = rc.fft_noise((64, 8192), 1.2, seed + 10 * i + 4)
        rx = (xx - lean) * 4.0 + c.get("phase", 0) * 500
        ry = np.clip(h_ / 120.0, 0, 63)
        rays = rc.sample(rays_tex, ry, rx)
        rays = np.clip(0.55 + 0.45 * rays, 0, None) ** 2.2
        H_ = hgt[None, :]
        onset = 1.0 / (1.0 + np.exp(np.clip(-(h_ + 1.0) / 2.2, -60, 60)))
        decay = np.exp(-np.clip(h_, 0, None) / (H_ * 0.45))
        f = h_ / H_
        rmod = mix(0.85, 0.25 + 0.75 * rays, smoothstep(0.0, 45.0, h_))      # the bottom ribbon is smooth
        ribbon = 1.0 + 1.3 * np.exp(-np.clip(h_, 0, None) / 12.0)
        below = 0.10 * np.exp(np.clip(h_, -400, 0) / 22.0) * (h_ < 0)        # faint glow under the edge
        g_int = (onset * decay * rmod * ribbon + below) * env[None, :]
        r_int = onset * np.exp(-np.clip(h_, 0, None) / (H_ * 1.3)) * smoothstep(0.35, 1.1, f) * (0.4 + 0.6 * rays) * env[None, :]
        p_int = np.exp(-((h_ - 1.5) / 3.5) ** 2) * (0.3 + 0.7 * rays) * env[None, :]
        out += c["bright"] * (g_int[..., None] * green * 0.20 + r_int[..., None] * red * 0.08 + p_int[..., None] * pink * 0.05)
    # soft diffuse glow around it all
    out += rc.blur3(out, 30) * 0.6
    return out


def ridge_band(img, base, amp, color, seed, beta=2.4, peaks=0.5):
    """One flat silhouette range (used for distant alien ridges)."""
    ridge = rc.ridge_line(W, base, amp, seed, beta=beta, peaks=peaks)
    yy = np.arange(H, dtype=np.float32)[:, None]
    cov = np.clip(yy - ridge[None, :] + 0.5, 0, 1)
    img[:] = mix(img, np.broadcast_to(color, img.shape), cov[..., None])
    return ridge


# ======================================================================== themes 2..6
def theme_aurora():
    """Northern lights over snowy peaks, mirrored in a still lake."""
    water = 840
    sky = night_sky(water, zenith=rgb(4, 8, 16), horizon=rgb(18, 34, 40), airglow=rgb(10, 30, 18))
    stars = rc.starfield(H, W, 20000, seed=42, bright=0.55, max_flux=45)
    au = aurora_light([dict(y0=500, arch=120, slope=-0.17, amp=70, height=300, bright=1.0, phase=0),
                       dict(y0=600, arch=50, slope=0.13, amp=55, height=190, bright=0.5, phase=1.7),
                       dict(y0=300, arch=90, slope=-0.05, amp=80, height=230, bright=0.3, phase=3.1)], seed=43)
    img = sky + stars + au
    img = extinction_near_horizon(img, water, 0.18)
    lit = rgb(30, 70, 56) * 0.10                                   # the snow and rock catch the aurora's green
    ridge = rc.layered_mountains(img, lit, [(770, 210, 0.20, 0.12), (805, 150, 0.50, 0.12), (835, 80, 0.85, 0.10)],
                                 seed=44, fog_col=rgb(26, 52, 50) * 0.09, snow=rgb(120, 190, 170) * 0.09)
    # a dark spruce shore just above the water line
    shore = np.full(W, water - 2.0, np.float32) - 6 * np.abs(rc.noise1d(W * 2, 2.5, 45)[:W])
    trees = rc.conifers(W, H, shore, 46, density=0.09, hmin=14, hmax=52)
    yy = np.arange(H, dtype=np.float32)[:, None]
    bank = np.clip(yy - shore[None, :] + 0.5, 0, 1)
    img = mix(img, np.broadcast_to(rgb(6, 10, 10) * 0.01, img.shape), np.maximum(trees, bank)[..., None])
    img = lake_reflection(img, water, seed=47, dim=0.85, ripple=2.5)
    save("menu_bg_aurora", rc.finish(img, exposure=1.8, grain=0.010, seed=48))


def theme_nebula():
    """A deep-space photograph: a glowing hydrogen cloud lit by a young cluster, cut by dark dust pillars."""
    r = np.random.default_rng(51)
    yy, xx = np.mgrid[0:H, 0:W].astype(np.float32)
    N = 1024
    big = rc.fft_noise((N, N), 3.0, 52)
    mid = rc.fft_noise((N, N), 2.2, 53)
    fine = rc.fft_noise((N, N), 1.5, 54)
    wx = rc.fft_noise((N, N), 3.2, 55); wy = rc.fft_noise((N, N), 3.2, 56)
    sx, sy = xx / W * 1.6 * N / 2, yy / H * 0.9 * N / 2
    dx = rc.sample(wx, sy, sx) * 60; dy = rc.sample(wy, sy, sx) * 60
    gx, gy = sx + dx, sy + dy
    cl = (1180, 470)                                                   # the cluster that lights the gas
    rr = np.sqrt(((xx - cl[0]) / 1.2) ** 2 + (yy - cl[1]) ** 2)
    env = np.exp(-(rr / 700) ** 2)
    n_big = rc.sample(big, gy, gx); n_mid = rc.sample(mid, gy * 1.8, gx * 1.8); n_fine = rc.sample(fine, gy * 3.5, gx * 3.5)
    # wispy, layered gas: brighter where folds of the cloud line up, with fine filaments on top
    gas = np.clip(0.60 + 0.40 * n_big + 0.28 * n_mid, 0, None) ** 2.0
    gas *= np.clip(0.8 + 0.35 * n_fine, 0.2, None) * (0.5 + 1.2 * rc.ridged(n_mid * 0.8) ** 2)
    gas *= env
    light = np.exp(-(rr / 330) ** 2)                                   # closer to the hot stars = brighter
    halpha = gas * (0.20 + 0.40 * light)
    oiii = gas * np.exp(-(rr / 220) ** 2) * 0.30
    sii = gas * smoothstep(0.4, 1.6, n_mid) * 0.04
    emis = halpha[..., None] * rgb(255, 92, 128) + oiii[..., None] * rgb(90, 215, 220) + sii[..., None] * rgb(255, 150, 70)
    emis += (np.exp(-(rr / 190) ** 2) * 0.025 * np.clip(0.6 + 0.6 * n_mid, 0, None))[..., None] * rgb(130, 170, 255)   # blue reflection haze
    # dust: soft dark clouds and a few pillars pointing at the cluster; their edges glow where starlight eats them
    ang = np.arctan2(yy - cl[1], xx - cl[0])
    pil = rc.sample(rc.fft_noise((256, 4096), 2.0, 57, aniso=(1.0, 0.18)), np.clip(rr / 6, 0, 255), (ang / np.pi + 1) * 2048)
    pillars = smoothstep(1.0, 2.1, pil) * smoothstep(110, 240, rr) * (1 - smoothstep(330, 560, rr))
    clouds = smoothstep(0.2, 1.4, rc.sample(big, gy * 1.4 + 300, gx * 1.4 + 200) + 0.35 * n_mid)
    tau = 2.4 * pillars + 1.1 * clouds * (0.3 + 0.7 * env)
    rim = np.clip(gaussian_filter(tau, 2.5) - gaussian_filter(tau, 8.0), 0, None) * (0.3 + light)
    ext = np.exp(-tau[..., None] * np.array([0.8, 1.0, 1.25], np.float32))
    emis = emis * ext + (rim * 0.10)[..., None] * rgb(255, 170, 140)
    stars = rc.starfield(H, W, 70000, seed=58, bright=0.42, max_flux=40, spikes=7, spike_len=55)
    # the cluster: a few dozen hot blue-white stars
    cst = np.zeros((H, W, 3), np.float32)
    n = 90
    cy = cl[1] + r.normal(0, 50, n); cx = cl[0] + r.normal(0, 65, n)
    flux = (1 - r.random(n)) ** -1.2 * 0.6
    cols = rc.blackbody(r.uniform(12000, 30000, n)) * np.minimum(flux, 40)[:, None]
    for i in range(n):
        y_, x_ = int(cy[i]), int(cx[i])
        if 0 <= y_ < H and 0 <= x_ < W:
            cst[y_, x_] += cols[i]
    cst = rc.blur3(cst, 0.8) + rc.blur3(cst, 4) * 0.05
    img = emis + stars * ext.mean(-1, keepdims=True) + cst + rgb(4, 5, 10) * 0.01
    save("menu_bg", rc.finish(img, exposure=1.35, grain=0.009, seed=59, vignette=0.28))


def theme_planet():
    """A ringed giant hanging over the frozen ridges of one of its moons."""
    sky = night_sky(900, zenith=rgb(3, 4, 9), horizon=rgb(12, 16, 26), airglow=rgb(6, 8, 14))
    stars = rc.starfield(H, W, 22000, seed=61, bright=0.5, max_flux=35)
    emis, dens, ext = milky_way(H, W, p0=(500, 300), angle=28, width=80, core=0.0, seed=62, dust=0.9, bright=0.5)
    img = sky + stars * ext.mean(-1, keepdims=True) + emis
    col, al = rc.planet_render(0, "saturn", light=(-0.62, 0.30, 0.72), tilt=-16, view_el=16, seed=63,
                               center=(1150, 520), out_w=W, out_h=H, cam_scale=250)
    col = col * 0.55
    img = img * (1 - al[..., None]) + col
    # two small moons, lit from the same side
    for (mx, my, mr, sd) in [(1640, 170, 26, 64), (330, 210, 14, 65)]:
        s_, a_ = rc.moon_disc(mr * 2 + 4, ALB(), fill=mr / (mr + 2), light=(-0.7, 0.25, 0.6))
        tint = rgb(220, 210, 196) * 0.40
        y0, x0 = my - mr - 2, mx - mr - 2
        sub = img[y0:y0 + s_.shape[0], x0:x0 + s_.shape[1]]
        sub[:] = sub * (1 - a_[..., None]) + (s_[..., None] * tint) * a_[..., None]
    img = extinction_near_horizon(img, 900, 0.12)
    rock = rgb(90, 80, 72) * 0.10
    rc.layered_mountains(img, rock, [(900, 190, 0.45, 0.10), (955, 130, 0.75, 0.06), (1015, 80, 0.95, 0.0)], seed=66,
                         fog_col=rgb(60, 58, 64) * 0.05, rim=rgb(255, 220, 170) * 0.025)
    save("menu_bg_planet", rc.finish(img, exposure=1.7, grain=0.010, seed=67))


def theme_bloodmoon():
    """A total lunar eclipse: the Moon copper-red in Earth's shadow over dark hills and forest."""
    hy = 900
    sky = night_sky(hy, zenith=rgb(3, 5, 12), horizon=rgb(26, 24, 34), airglow=rgb(14, 16, 10))
    emis, dens, ext = milky_way(H, W, p0=(560, 420), angle=-70, width=95, core=0.25, seed=71, dust=1.0, bright=0.6)
    stars = rc.starfield(H, W, 40000, seed=72, density=dens, bright=0.5, max_flux=40)
    img = sky + emis + stars * ext.mean(-1, keepdims=True) ** 0.6
    # the eclipsed Moon: albedo x the colour of sunlight bent through Earth's atmosphere (red in the middle of the
    # shadow, orange toward the edge nearest the Sun, a thin blue-turquoise fringe from ozone at the very rim)
    mr, mx, my = 118, 1360, 330
    sz = mr * 2 + 8
    shade, a = rc.moon_disc(sz, ALB(), fill=mr / (mr + 4), limb=0.25)
    yy, xx = np.mgrid[0:sz, 0:sz].astype(np.float32)
    ux, uy = (xx - sz / 2) / mr, (yy - sz / 2) / mr
    d = np.sqrt((ux + 1.7) ** 2 + (uy - 1.2) ** 2)                      # distance from the shadow's centre
    t = smoothstep(1.3, 2.9, d)
    col = mix(rgb(120, 22, 10), rgb(255, 120, 46), t[..., None]) * (0.35 + 0.9 * t[..., None] ** 2)
    col += (np.exp(-((d - 2.95) / 0.07) ** 2) * 0.25)[..., None] * rgb(110, 190, 255)
    moon = shade[..., None] * col * 0.20
    y0, x0 = my - sz // 2, mx - sz // 2
    sub = img[y0:y0 + sz, x0:x0 + sz]
    sub[:] = sub * (1 - a[..., None]) + moon * a[..., None]
    # faint red glow around it from the lens
    yy2, xx2 = np.mgrid[0:H, 0:W].astype(np.float32)
    img += (np.exp(-np.sqrt((xx2 - mx) ** 2 + (yy2 - my) ** 2) / 90) * 0.006)[..., None] * rgb(255, 90, 50)
    img = extinction_near_horizon(img, hy)
    rc.layered_mountains(img, rgb(56, 50, 64) * 0.16, [(860, 140, 0.35, 0.45), (905, 120, 0.68, 0.40),
                                                        (990, 80, 0.96, 0.15)], seed=73,
                         fog_col=rgb(50, 44, 56) * 0.08, trees=dict(density=0.07, hmin=22, hmax=85))
    save("menu_bg_bloodmoon", rc.finish(img, exposure=1.8, grain=0.011, seed=74))


_ALB = None


def ALB():
    global _ALB
    if _ALB is None:
        _ALB = rc.moon_albedo(2048)
    return _ALB


# ======================================================================== black hole (ray traced)
def trace_black_hole(w, h, cxp, cyp, fov_deg, cam_dist, cam_el_deg, roll_deg, r_in=6.0, r_out=24.0, seed=81,
                     chunk=350_000, far_only=False):
    """
    Follows light backward from the camera past a Schwarzschild black hole (mass 1, horizon r = 2).
    Uses the exact null-geodesic equation in Cartesian form: x'' = -1.5 h^2 x / r^5 (h = |x cross x'|),
    integrated with RK4 and steps that shrink near the hole. A thin Keplerian disk lies in the y = 0 plane;
    its light is Doppler/gravitationally shifted (g = sqrt(1 - 3/r) / (1 - Omega*lambda)), so one side is
    brighter and bluer. Rays may cross the disk several times (the arc over the top and the thin ring under it).

    far_only keeps just the light from the disk's far side (what lensing lifts over and under the shadow).
    Returns (disk_rgb, transmittance, escape direction in camera pixels (ex, ey), escaped mask).
    """
    F = (w / 2) / math.tan(math.radians(fov_deg) / 2)
    e = math.radians(cam_el_deg)
    cam = np.array([0.0, math.sin(e), math.cos(e)]) * cam_dist
    fwd = -cam / np.linalg.norm(cam)
    right = np.cross(fwd, [0.0, 1.0, 0.0]); right /= np.linalg.norm(right)
    up = np.cross(right, fwd)
    ro = math.radians(roll_deg)
    right, up = right * math.cos(ro) + up * math.sin(ro), -right * math.sin(ro) + up * math.cos(ro)

    yy, xx = np.mgrid[0:h, 0:w]
    px = (xx.ravel() + 0.5 - cxp).astype(np.float64)
    py = (cyp - yy.ravel() - 0.5).astype(np.float64)
    n_all = px.size
    disk = np.zeros((n_all, 3), np.float32)
    trans = np.ones(n_all, np.float32)
    ex = np.zeros(n_all, np.float32); ey = np.zeros(n_all, np.float32)
    esc = np.zeros(n_all, bool)

    # disk texture in (log r, angle): fine concentric streaks with a little spiral shear and clumps
    tex_r, tex_a = 512, 2048
    streak = rc.fft_noise((tex_r, tex_a), 1.6, seed, aniso=(0.06, 1.0))
    clump = rc.fft_noise((tex_r, tex_a), 2.2, seed + 1, aniso=(0.3, 1.0))

    def disk_light(p, v):
        rr = np.sqrt(p[:, 0] ** 2 + p[:, 2] ** 2)
        phi = np.arctan2(p[:, 2], p[:, 0])
        lr = np.log(rr / r_in) / math.log(r_out / r_in)                     # 0..1 across the disk
        ty = np.clip(lr, 0, 1) * (tex_r - 1)
        tx = ((phi / (2 * math.pi) + 0.5) + 0.12 * lr) % 1.0 * tex_a        # spiral shear
        s = map_c(streak, ty, tx)
        c = map_c(clump, ty * 0.5, tx * 0.5)
        dens = np.clip(0.80 + 0.30 * s + 0.25 * c, 0.08, None)
        dens *= smoothstep(r_in, r_in * 1.12, rr) * (1 - smoothstep(r_out * 0.55, r_out, rr))
        # temperature profile of a thin disk; colour/brightness after the Doppler + gravity shift
        T = 6200.0 * (rr / r_in) ** -0.75 * np.clip(1 - np.sqrt(r_in / rr), 0, 1) ** 0.25 / 0.488
        lam = -(p[:, 2] * v[:, 0] - p[:, 0] * v[:, 2])                    # photon's angular momentum about +y
        omega = rr ** -1.5
        g = np.sqrt(np.clip(1 - 3 / rr, 1e-3, 1)) / np.clip(1 - omega * lam, 0.05, None)
        Tobs = np.clip(T * g, 1200, 60000)
        lum = (T / 6200.0) ** 3 * g ** 3 * 0.65
        col = rc.blackbody(Tobs) * (lum * dens)[:, None]
        alpha = np.clip(dens * 0.85, 0, 0.97)
        return col.astype(np.float32), alpha.astype(np.float32)

    for s0 in range(0, n_all, chunk):
        s1 = min(n_all, s0 + chunk)
        d = (fwd[None, :] * F + right[None, :] * px[s0:s1, None] + up[None, :] * py[s0:s1, None])
        d /= np.linalg.norm(d, axis=1, keepdims=True)
        idx = np.arange(s0, s1)
        x = np.broadcast_to(cam, d.shape).copy()
        v = d.copy()
        h2 = np.sum(np.cross(x, v) ** 2, axis=1)
        for it in range(3000):
            if idx.size == 0:
                break
            r = np.sqrt(np.sum(x * x, axis=1))
            dt = np.clip(0.06 * r, 0.02, 4.0)[:, None]

            def acc(xx_, h2_):
                r2 = np.sum(xx_ * xx_, axis=1)
                return -1.5 * h2_[:, None] * xx_ / (r2 * r2 * np.sqrt(r2))[:, None]

            k1x, k1v = v, acc(x, h2)
            k2x, k2v = v + 0.5 * dt * k1v, acc(x + 0.5 * dt * k1x, h2)
            k3x, k3v = v + 0.5 * dt * k2v, acc(x + 0.5 * dt * k2x, h2)
            k4x, k4v = v + dt * k3v, acc(x + dt * k3x, h2)
            xn = x + dt / 6 * (k1x + 2 * k2x + 2 * k3x + k4x)
            vn = v + dt / 6 * (k1v + 2 * k2v + 2 * k3v + k4v)
            # crossed the disk plane?
            cross_ = (x[:, 1] * xn[:, 1]) < 0
            if cross_.any():
                ci = np.where(cross_)[0]
                f = (x[ci, 1] / (x[ci, 1] - xn[ci, 1]))[:, None]
                p = x[ci] + (xn[ci] - x[ci]) * f
                vv = v[ci] + (vn[ci] - v[ci]) * f
                rr = np.sqrt(p[:, 0] ** 2 + p[:, 2] ** 2)
                ok = (rr > r_in * 0.98) & (rr < r_out)
                if far_only:
                    ok &= (p @ cam) < 0                                       # only the side beyond the hole
                if ok.any():
                    ci2 = ci[ok]
                    col, al = disk_light(p[ok], vv[ok])
                    gi = idx[ci2]
                    disk[gi] += col * (trans[gi] * al)[:, None]
                    trans[gi] *= (1 - al)
            x, v = xn, vn
            rn = np.sqrt(np.sum(x * x, axis=1))
            captured = rn < 2.02
            escaped = (rn > 160) & (np.sum(x * v, axis=1) > 0)
            opaque = trans[idx] < 0.01
            done = captured | escaped | opaque
            if escaped.any():
                ei = np.where(escaped)[0]
                dv = v[ei] / np.linalg.norm(v[ei], axis=1, keepdims=True)
                zf = dv @ fwd
                good = zf > 0.05
                gi = idx[ei]
                ex[gi] = np.where(good, cxp + F * (dv @ right) / np.maximum(zf, 0.05), -1e6)
                ey[gi] = np.where(good, cyp - F * (dv @ up) / np.maximum(zf, 0.05), -1e6)
                esc[gi] = good
            if done.any():
                keep = ~done
                x, v, h2, idx = x[keep], v[keep], h2[keep], idx[keep]
        print("    traced %d / %d rays" % (s1, n_all))
    return disk.reshape(h, w, 3), trans.reshape(h, w), ex.reshape(h, w), ey.reshape(h, w), esc.reshape(h, w)


def map_c(img, y, x):
    from scipy.ndimage import map_coordinates
    return map_coordinates(img, [y, x], order=1, mode="wrap").astype(np.float32)


def theme_blackhole():
    """A black hole and its glowing disk, ray traced, over the dark rim of a world."""
    cxp, cyp = 1090, 470
    disk, trans, ex, ey, esc = trace_black_hole(W, H, cxp, cyp, fov_deg=62, cam_dist=50, cam_el_deg=6.5,
                                                roll_deg=-9, r_in=6.0, r_out=20.0)
    # the sky behind it, on a canvas with a margin so bent light can reach past the frame edges
    M = 600
    ch, cw = H + 2 * M, W + 2 * M
    emis, dens, ext = milky_way(ch, cw, p0=(cw * 0.30, ch * 0.62), angle=-24, width=120, core=0.35, seed=82,
                                dust=1.0, bright=0.7)
    bg = (rc.starfield(ch, cw, 70000, seed=83, density=dens, bright=0.55, max_flux=40)
          * ext.mean(-1, keepdims=True) ** 0.6 + emis + rgb(3, 3, 8) * 0.004)
    bg = rc.blur3(bg, 0.35)
    by = np.clip(ey + M, 0, ch - 1); bx = np.clip(ex + M, 0, cw - 1)
    seen = rc.sample(bg, by, bx, mode="nearest")
    seen = np.where(esc[..., None], seen, 0.0)
    img = disk * 1.0 + seen * trans[..., None]
    # dark ridge of the world we stand on
    ridge_band(img, 1010, 70, rgb(5, 5, 8) * 0.003, seed=84)
    save("menu_bg_blackhole", rc.finish(img, exposure=1.25, grain=0.009, seed=85, bloom_strength=1.6))


THEMES = {"nebula": theme_nebula, "galaxy": theme_galaxy, "aurora": theme_aurora, "blackhole": theme_blackhole,
          "planet": theme_planet, "bloodmoon": theme_bloodmoon}


if __name__ == "__main__":
    which = sys.argv[1:] or list(THEMES)
    for t in which:
        t0 = time.time()
        print("rendering", t)
        THEMES[t]()
        print("  %.1fs" % (time.time() - t0))
