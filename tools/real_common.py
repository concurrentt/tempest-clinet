"""
Shared building blocks for Tempest's realistic art (tools/make_menu_real.py, tools/make_sky_real.py).

Everything is rendered in linear light (float32, "photons"), then bloomed, tone-mapped with a filmic curve and
converted to sRGB at the very end - the way a camera / renderer would - which is most of what makes it look real
rather than painted. All art is original and procedural (numpy + scipy + Pillow).
"""
import math

import numpy as np
from scipy.ndimage import gaussian_filter, map_coordinates


# ------------------------------------------------------------------ small helpers
def smoothstep(e0, e1, x):
    t = np.clip((x - e0) / (e1 - e0), 0.0, 1.0)
    return t * t * (3.0 - 2.0 * t)


def mix(a, b, t):
    return a + (b - a) * t


def rgb(*c):
    """sRGB 0-255 (or 0-1 floats) -> linear RGB array."""
    c = np.asarray(c, np.float32)
    if c.max() > 1.0:
        c = c / 255.0
    return np.where(c <= 0.04045, c / 12.92, ((c + 0.055) / 1.055) ** 2.4).astype(np.float32)


# ------------------------------------------------------------------ noise
def fft_noise(shape, beta=2.0, seed=0, aniso=(1.0, 1.0), lowcut=0.0):
    """
    Tileable power-law noise (mean 0, std 1). beta ~ 1.5 rough, 2.5 smooth. aniso=(ay, ax): features come out
    stretched along the axis with the larger factor - (0.1, 1) gives streaks running along x, (1, 0.1) along y.
    """
    r = np.random.default_rng(seed)
    h, w = shape
    fy = np.fft.fftfreq(h)[:, None] * aniso[0]
    fx = np.fft.rfftfreq(w)[None, :] * aniso[1]
    f = np.sqrt(fx * fx + fy * fy)
    f[0, 0] = 1.0
    amp = f ** (-beta / 2.0 - 0.5)
    if lowcut > 0:
        amp *= 1.0 - np.exp(-(f / lowcut) ** 2)
    amp[0, 0] = 0.0
    spec = (r.normal(size=f.shape) + 1j * r.normal(size=f.shape)) * amp
    n = np.fft.irfft2(spec, s=shape)
    n = (n - n.mean()) / (n.std() + 1e-9)
    return n.astype(np.float32)


def noise1d(n, beta=2.0, seed=0):
    r = np.random.default_rng(seed)
    f = np.fft.rfftfreq(n)
    f[0] = 1.0
    amp = f ** (-beta / 2.0)
    amp[0] = 0.0
    spec = (r.normal(size=f.shape) + 1j * r.normal(size=f.shape)) * amp
    x = np.fft.irfft(spec, n=n)
    return ((x - x.mean()) / (x.std() + 1e-9)).astype(np.float32)


def ridged(n):
    """Sharp ridges where the noise crosses zero (filaments, dust lanes, cracks)."""
    return (1.0 - np.abs(np.tanh(n * 0.9))).astype(np.float32)


def warp(img, dx, dy, mode="wrap"):
    h, w = img.shape[:2]
    yy, xx = np.mgrid[0:h, 0:w].astype(np.float32)
    coords = [yy + dy, xx + dx]
    if img.ndim == 2:
        return map_coordinates(img, coords, order=1, mode=mode).astype(np.float32)
    return np.stack([map_coordinates(img[..., c], coords, order=1, mode=mode) for c in range(img.shape[2])], -1).astype(np.float32)


def sample(img, y, x, mode="wrap", order=1):
    """Bilinear lookup of img at float coords (any shape)."""
    if img.ndim == 2:
        return map_coordinates(img, [y.ravel(), x.ravel()], order=order, mode=mode).reshape(y.shape).astype(np.float32)
    return np.stack([map_coordinates(img[..., c], [y.ravel(), x.ravel()], order=order, mode=mode).reshape(y.shape)
                     for c in range(img.shape[2])], -1).astype(np.float32)


# ------------------------------------------------------------------ colour of hot things
# CIE 1931 colour matching functions, multi-lobe Gaussian fit (Wyman, Sloan & Shirley 2013)
def _cie(lam):
    t1 = (lam - 442.0) * np.where(lam < 442.0, 0.0624, 0.0374)
    t2 = (lam - 599.8) * np.where(lam < 599.8, 0.0264, 0.0323)
    t3 = (lam - 501.1) * np.where(lam < 501.1, 0.0490, 0.0382)
    x = 0.362 * np.exp(-0.5 * t1 * t1) + 1.056 * np.exp(-0.5 * t2 * t2) - 0.065 * np.exp(-0.5 * t3 * t3)
    t1 = (lam - 568.8) * np.where(lam < 568.8, 0.0213, 0.0247)
    t2 = (lam - 530.9) * np.where(lam < 530.9, 0.0613, 0.0322)
    y = 0.821 * np.exp(-0.5 * t1 * t1) + 0.286 * np.exp(-0.5 * t2 * t2)
    t1 = (lam - 437.0) * np.where(lam < 437.0, 0.0845, 0.0278)
    t2 = (lam - 459.0) * np.where(lam < 459.0, 0.0385, 0.0725)
    z = 1.217 * np.exp(-0.5 * t1 * t1) + 0.681 * np.exp(-0.5 * t2 * t2)
    return x, y, z


_LAM = np.linspace(380.0, 780.0, 81)
_CX, _CY, _CZ = _cie(_LAM)
_XYZ2RGB = np.array([[3.2406, -1.5372, -0.4986], [-0.9689, 1.8758, 0.0415], [0.0557, -0.2040, 1.0570]])


def blackbody(T):
    """Linear sRGB colour of a black body at temperature T (kelvin, any shape), scaled to luminance 1."""
    T = np.asarray(T, np.float64)
    lam = _LAM * 1e-9
    with np.errstate(over="ignore"):
        planck = 1.0 / (lam ** 5 * (np.exp(1.4388e-2 / (lam * T[..., None])) - 1.0))
    X = (planck * _CX).sum(-1)
    Y = (planck * _CY).sum(-1)
    Z = (planck * _CZ).sum(-1)
    xyz = np.stack([X, Y, Z], -1) / np.maximum(Y, 1e-30)[..., None]
    out = xyz @ _XYZ2RGB.T
    out = np.maximum(out, 0.0)
    return (out / np.maximum((out * [0.2126, 0.7152, 0.0722]).sum(-1, keepdims=True), 1e-9)).astype(np.float32)


# ------------------------------------------------------------------ stars
def star_temps(r, n):
    """Temperatures that look like a real sky: mostly yellow-white, some orange giants, a few blue-white."""
    u = r.random(n)
    T = np.where(u < 0.18, r.uniform(3200, 4500, n),            # K/M giants (orange)
        np.where(u < 0.62, r.uniform(4500, 6500, n),            # G/F (yellow-white)
        np.where(u < 0.90, r.uniform(6500, 9500, n),            # A/F (white)
                 r.uniform(9500, 25000, n))))                    # B (blue-white)
    return T


def starfield(h, w, n, seed=1, density=None, bright=1.0, max_flux=60.0, sigma=0.62, spikes=0, spike_len=40.0):
    """
    n stars with a real brightness distribution (lots of faint ones, a handful of bright ones) and blackbody
    colours, as point light splatted onto the grid and blurred by a small seeing disc. density (h, w) >= 0
    makes stars likelier where it is high (e.g. along the Milky Way). Returns linear RGB.
    """
    r = np.random.default_rng(seed)
    if density is not None:
        # rejection sampling against the density map
        d = density / density.max()
        ys, xs = [], []
        need = n
        while need > 0:
            m = need * 3
            y = r.random(m) * h
            x = r.random(m) * w
            keep = r.random(m) < d[np.clip(y.astype(int), 0, h - 1), np.clip(x.astype(int), 0, w - 1)]
            ys.append(y[keep][:need]); xs.append(x[keep][:need])
            need -= len(ys[-1])
        y = np.concatenate(ys); x = np.concatenate(xs)
    else:
        y = r.random(n) * h
        x = r.random(n) * w
    # magnitudes: N(<m) ~ 10^(0.45 m) -> flux follows a steep power law
    flux = (1.0 - r.random(n)) ** (-1.0 / 0.95) * 0.06          # Pareto, min 0.06
    flux = np.minimum(flux, max_flux) * bright
    col = blackbody(star_temps(r, n)) * flux[:, None]
    img = np.zeros((h, w, 3), np.float32)
    # bilinear splat (keeps sub-pixel positions)
    y0 = np.floor(y - 0.5).astype(int); x0 = np.floor(x - 0.5).astype(int)
    fy = (y - 0.5 - y0)[:, None]; fx = (x - 0.5 - x0)[:, None]
    for dy, wy in ((0, 1 - fy), (1, fy)):
        for dx, wx in ((0, 1 - fx), (1, fx)):
            yy = np.clip(y0 + dy, 0, h - 1); xx = np.clip(x0 + dx, 0, w - 1)
            for c in range(3):
                np.add.at(img[..., c], (yy, xx), (col[:, c:c + 1] * wy * wx)[:, 0])
    img = np.stack([gaussian_filter(img[..., c], sigma) for c in range(3)], -1)
    # a faint wider wing (atmosphere / lens) so bright stars have a soft halo
    img += 0.06 * np.stack([gaussian_filter(img[..., c], 2.2) for c in range(3)], -1)
    if spikes:
        # thin diffraction spikes on the brightest stars only
        idx = np.argsort(flux)[-spikes:]
        yy, xx = np.mgrid[0:h, 0:w]
        for i in idx:
            cy, cx = y[i], x[i]
            L = spike_len * math.sqrt(flux[i] / max_flux)
            y0_, y1_ = int(max(0, cy - L)), int(min(h, cy + L + 1))
            x0_, x1_ = int(max(0, cx - L)), int(min(w, cx + L + 1))
            if y1_ <= y0_ or x1_ <= x0_:
                continue
            sy = yy[y0_:y1_, x0_:x1_] + 0.5 - cy; sx = xx[y0_:y1_, x0_:x1_] + 0.5 - cx
            s = (np.exp(-np.abs(sx) / (L * 0.28)) * np.exp(-(sy * sy) / 0.5) +
                 np.exp(-np.abs(sy) / (L * 0.28)) * np.exp(-(sx * sx) / 0.5))
            img[y0_:y1_, x0_:x1_] += s[..., None] * col[i] * 0.05
    return img


# ------------------------------------------------------------------ camera-ish finishing
def bloom(img, strength=1.0):
    """Glow from the lens: a few wide blurs of the image added back (bright things bleed the most)."""
    out = img.copy()
    for s, k in ((2.0, 0.035), (7.0, 0.025), (22.0, 0.018), (60.0, 0.012)):
        out += strength * k * np.stack([gaussian_filter(img[..., c], s) for c in range(3)], -1)
    return out


def aces(x):
    a, b, c, d, e = 2.51, 0.03, 2.43, 0.59, 0.14
    return np.clip((x * (a * x + b)) / (x * (c * x + d) + e), 0.0, 1.0)


def to_srgb(lin):
    lin = np.clip(lin, 0.0, 1.0)
    return np.where(lin <= 0.0031308, lin * 12.92, 1.055 * lin ** (1 / 2.4) - 0.055)


def finish(img, exposure=1.0, grain=0.012, seed=7, bloom_strength=1.0, vignette=0.22, alpha=None):
    """Linear HDR -> 8-bit sRGB (bloom, filmic curve, vignette, fine grain/dither)."""
    h, w = img.shape[:2]
    x = bloom(img, bloom_strength) * exposure
    if vignette:
        yy, xx = np.mgrid[0:h, 0:w].astype(np.float32)
        rr = ((xx - w / 2) / (w / 2)) ** 2 + ((yy - h / 2) / (h / 2)) ** 2
        x *= (1.0 - vignette * smoothstep(0.3, 2.0, rr))[..., None]
    x = to_srgb(aces(x))
    r = np.random.default_rng(seed)
    x = x + (r.random(x.shape).astype(np.float32) - 0.5) * (1.0 / 255.0) + r.normal(0, grain, x.shape[:2])[..., None] * (0.35 + 0.65 * (1 - x))
    x = np.clip(x * 255.0 + 0.5, 0, 255).astype(np.uint8)
    if alpha is not None:
        a = np.clip(alpha * 255 + 0.5, 0, 255).astype(np.uint8)
        return np.dstack([x, a])
    return x


def blur3(img, s):
    return np.stack([gaussian_filter(img[..., c], s) for c in range(img.shape[2])], -1)


# ------------------------------------------------------------------ the Moon (albedo, full disc)
def moon_albedo(n=2048, seed=11):
    """
    Lunar albedo on a lat/lon grid (n x n/2): dark maria, bright cratered highlands, a few young ray craters.
    Returned 0..1 (normal albedo, roughly 0.07 maria .. 0.18 highlands, scaled to ~0.35..1 for texture use).
    """
    h, w = n // 2, n
    r = np.random.default_rng(seed)
    lat = (0.5 - (np.arange(h) + 0.5) / h)[:, None] * math.pi          # +pi/2 top
    lon = ((np.arange(w) + 0.5) / w * 2 - 1)[None, :] * math.pi         # 0 = centre of the near side
    clat = np.cos(lat)
    # unit vectors on the sphere (x right, y up, z toward the viewer)
    X = clat * np.sin(lon); Y = np.sin(lat) * np.ones_like(lon); Z = clat * np.cos(lon)

    # maria: big dark lava plains, mostly on the near side (like the real Moon), with irregular, lobed shores
    base = fft_noise((h, w), 3.4, seed + 1)
    shore = fft_noise((h, w), 2.0, seed + 2)
    field = 0.32 * base
    for (la, lo, rad, k) in [(24, -22, 0.50, 1.0), (34, 14, 0.30, 0.95), (6, 30, 0.30, 0.9), (-16, -38, 0.40, 0.85),
                              (-6, 4, 0.22, 0.8), (44, -50, 0.26, 0.7), (-30, 20, 0.20, 0.7), (18, 56, 0.16, 0.8),
                              (-2, -62, 0.20, 0.7)]:
        la_, lo_ = math.radians(la), math.radians(lo)
        cx, cy, cz = math.cos(la_) * math.sin(lo_), math.sin(la_), math.cos(la_) * math.cos(lo_)
        d = np.arccos(np.clip(X * cx + Y * cy + Z * cz, -1, 1))
        field = field + k * np.exp(-(d / rad) ** 2 * 1.6)
    rough = fft_noise((h, w), 1.5, seed + 6)
    seas = smoothstep(0.44, 0.74, field + 0.20 * shore + 0.07 * rough)
    seas = seas * smoothstep(-0.2, 0.4, Z + 0.15 * base)             # the far side is nearly all highland
    depth = 0.50 + 0.10 * np.tanh(fft_noise((h, w), 2.6, seed + 8))  # some seas darker than others
    alb = mix(0.95, depth, seas)
    alb *= 1.0 + 0.08 * fft_noise((h, w), 2.4, seed + 3) + 0.06 * fft_noise((h, w), 1.3, seed + 4) + 0.05 * rough * (1 - seas)
    alb *= 1.0 - 0.07 * seas * smoothstep(-0.5, 1.5, fft_noise((h, w), 2.2, seed + 5))   # mare colour patches

    # craters: power-law sizes; bright rims and ejecta, darker floors (subtle at full moon, like reality)
    ncr = 7000
    sizes = (1.0 - r.random(ncr)) ** (-1 / 1.5) * 0.0028                 # radians
    sizes = np.minimum(sizes, 0.12)
    clat_c = np.arcsin(r.uniform(-1, 1, ncr)); clon_c = r.uniform(-math.pi, math.pi, ncr)
    young = r.random(ncr)
    for i in np.argsort(-sizes):
        rad = sizes[i]
        la_, lo_ = clat_c[i], clon_c[i]
        # window in grid cells
        dy = int(rad * 3.2 / math.pi * h) + 2
        cyi = int((0.5 - la_ / math.pi) * h)
        y0, y1 = max(0, cyi - dy), min(h, cyi + dy)
        if y1 <= y0:
            continue
        sub_lat = lat[y0:y1, 0][:, None]
        dlon = (lon[0][None, :] - lo_ + math.pi) % (2 * math.pi) - math.pi
        cosd = np.sin(sub_lat) * math.sin(la_) + np.cos(sub_lat) * math.cos(la_) * np.cos(dlon)
        d = np.arccos(np.clip(cosd, -1, 1)) / rad
        m = d < 3.0
        if not m.any():
            continue
        cols = np.where(m.any(0))[0]
        x0, x1 = cols.min(), cols.max() + 1
        d = d[:, x0:x1]
        maria_here = seas[y0:y1, x0:x1]
        fresh = young[i] ** 6                                          # a few are bright and fresh
        rim = np.exp(-((d - 1.0) / 0.22) ** 2)
        floor = 1 - smoothstep(0.5, 1.0, d)
        ejecta = np.exp(-np.maximum(d - 1.0, 0) / 0.8) * smoothstep(0.8, 1.05, d)
        k = 0.05 + 0.45 * fresh
        f = 1.0 + k * (0.35 * rim + 0.55 * ejecta) - (0.06 - 0.04 * fresh) * floor
        f = mix(f, 1.0 + 0.6 * (f - 1.0), maria_here)
        alb[y0:y1, x0:x1] *= f

    # ray systems from a few young craters (Tycho / Copernicus / Kepler-like)
    for (la, lo, L, k) in [(-43, -11, 0.9, 0.55), (10, -20, 0.45, 0.35), (8, -38, 0.3, 0.3), (-20, 40, 0.35, 0.25)]:
        la_, lo_ = math.radians(la), math.radians(lo)
        cx, cy, cz = math.cos(la_) * math.sin(lo_), math.sin(la_), math.cos(la_) * math.cos(lo_)
        d = np.arccos(np.clip(X * cx + Y * cy + Z * cz, -1, 1))
        # bearing from the crater
        bx = Y * cz - Z * cy; by = Z * cx - X * cz; bz = X * cy - Y * cx            # c x p ... any tangent
        e1 = np.array([math.cos(lo_), 0.0, -math.sin(lo_)])                          # east
        e2 = np.array([-math.sin(la_) * math.sin(lo_), math.cos(la_), -math.sin(la_) * math.cos(lo_)])  # north
        ang = np.arctan2(X * e2[0] + Y * e2[1] + Z * e2[2], X * e1[0] + Y * e1[1] + Z * e1[2])
        lobes = np.zeros_like(ang)
        rr = np.random.default_rng(int(abs(la * 100 + lo)))
        for _ in range(40):
            a0 = rr.uniform(-math.pi, math.pi); wdt = rr.uniform(0.02, 0.07); ln = L * rr.uniform(0.25, 1.0)
            da = (ang - a0 + math.pi) % (2 * math.pi) - math.pi
            lobes += np.exp(-(da / wdt) ** 2) * np.exp(-d / ln)
        lobes *= smoothstep(0.02, 0.06, d)
        alb *= 1.0 + 0.45 * k * np.clip(lobes, 0, 1.2) * np.clip(0.3 + 0.7 * fft_noise((h, w), 1.4, int(la + 200)), 0, 1.6)
    alb = np.clip(alb, 0.2, 1.6)
    return (alb / np.percentile(alb, 99.5)).astype(np.float32)


def moon_disc(size, alb=None, fill=0.9, light=None, limb=0.18):
    """
    Orthographic view of the near side, size x size, disc radius = fill * half-size.
    Full moon by default (lighting is almost flat - the Moon's surface throws light back toward the Sun);
    light=(x, y, z) gives a phase with a soft terminator.
    Returns (shade 0..1 float32, alpha 0..1).
    """
    if alb is None:
        alb = moon_albedo()
    h, w = alb.shape
    yy, xx = np.mgrid[0:size, 0:size].astype(np.float32)
    ss = 3 if size <= 1024 else 2
    # supersample the edge for a clean limb
    px = (xx + 0.5) / size * 2 - 1
    py = 1 - (yy + 0.5) / size * 2
    px /= fill; py /= fill
    r2 = px * px + py * py
    inside = r2 <= 1.0
    z = np.sqrt(np.clip(1 - r2, 0, 1))
    lat = np.arcsin(np.clip(py, -1, 1))
    lon = np.arctan2(px, z)
    gy = (0.5 - lat / math.pi) * h - 0.5
    gx = (lon / (2 * math.pi) + 0.5) * w - 0.5
    a = sample(alb, gy, gx, mode="wrap", order=3)
    # Lommel-Seeliger-ish: very little limb darkening at full moon
    mu = z
    shade = a * (1 - limb + limb * np.sqrt(np.clip(mu, 0, 1)))
    if light is not None:
        L = np.asarray(light, np.float32); L /= np.linalg.norm(L)
        cosi = px * L[0] + py * L[1] + z * L[2]
        shade *= np.clip(2 * cosi / (cosi + mu + 1e-3), 0, 2) * smoothstep(-0.03, 0.08, cosi)
    # anti-aliased edge
    edge = np.sqrt(r2)
    alpha = np.clip((1.0 - edge) * size * fill * 0.5 + 0.5, 0, 1)
    return np.where(inside, shade, 0).astype(np.float32), alpha.astype(np.float32)


# ------------------------------------------------------------------ gas giants and their rings
def planet_render(size, kind="saturn", fill=0.42, light=(-0.55, 0.35, 0.76), tilt=-14.0, view_el=14.0,
                  seed=5, ring=True, center=None, out_w=None, out_h=None, cam_scale=None):
    """
    Ray-traced planet with optional rings, linear RGB + alpha. The planet's radius is fill * half of `size`
    (planet + rings fit inside the square). Bands are turbulent (warped latitude), the limb darkens, the lit
    limb has a thin atmosphere, the rings cast a shadow on the planet and the planet one on the rings.
    """
    W_ = out_w or size; H_ = out_h or size
    cx, cy = center if center is not None else (W_ / 2, H_ / 2)
    scale = cam_scale or (size / 2 * fill)               # pixels per planet radius
    yy, xx = np.mgrid[0:H_, 0:W_].astype(np.float32)
    # orthographic camera: rays along -z
    px = (xx + 0.5 - cx) / scale
    py = (cy - yy - 0.5) / scale
    # planet frame: rotate by tilt (roll on screen) and view elevation (we see the north pole tipped toward us)
    t = math.radians(tilt); e = math.radians(view_el)
    # screen -> body coordinates: roll then pitch
    bx = px * math.cos(t) + py * math.sin(t)
    by = -px * math.sin(t) + py * math.cos(t)
    L = np.asarray(light, np.float64); L /= np.linalg.norm(L)
    Lb = np.array([L[0] * math.cos(t) + L[1] * math.sin(t), -L[0] * math.sin(t) + L[1] * math.cos(t), L[2]])
    # pitch: body y-up axis tipped toward the viewer by e
    ce, se = math.cos(e), math.sin(e)
    Lp = np.array([Lb[0], Lb[1] * ce + Lb[2] * se, -Lb[1] * se + Lb[2] * ce])     # light in planet frame

    r2 = bx * bx + by * by
    hit = r2 <= 1.0
    zs = np.sqrt(np.clip(1 - r2, 0, 1))                     # sphere surface toward the viewer
    # surface point in view-pitched frame -> planet frame
    sx = bx
    sy = by * ce + zs * se
    sz = -by * se + zs * ce
    lat = np.arcsin(np.clip(sy, -1, 1))
    lon = np.arctan2(sx, sz)

    r = np.random.default_rng(seed)
    nlat, nlon = 512, 1024
    turb = fft_noise((nlat, nlon), 2.6, seed + 1, aniso=(0.35, 1.0))
    fine = fft_noise((nlat, nlon), 1.9, seed + 2, aniso=(0.25, 1.0))
    gy = (0.5 - lat / math.pi) * nlat
    gx = (lon / (2 * math.pi) + 0.5) * nlon
    w1 = sample(turb, gy, gx)
    w2 = sample(fine, gy, gx)
    phi = np.sin(lat) + 0.035 * w1 + 0.012 * w2               # warped latitude -> bands
    if kind == "saturn":
        band = (0.55 + 0.25 * np.sin(phi * 13.0) + 0.12 * np.sin(phi * 29.0 + 1.0) + 0.08 * np.sin(phi * 61.0))
        c_light, c_dark = rgb(236, 214, 170), rgb(176, 140, 92)
        col = mix(c_dark, c_light, np.clip(band, 0, 1)[..., None])
        col *= mix(1.0, 0.82, smoothstep(0.75, 1.0, np.abs(np.sin(lat))))[..., None] * mix(np.ones(3), rgb(200, 205, 215), smoothstep(0.8, 1.0, np.abs(np.sin(lat)))[..., None])
    elif kind == "ice":
        band = 0.5 + 0.18 * np.sin(phi * 9.0) + 0.08 * np.sin(phi * 23.0 + 2.0)
        col = mix(rgb(120, 175, 205), rgb(175, 220, 232), np.clip(band, 0, 1)[..., None])
        col *= mix(1.0, 1.08, smoothstep(0.6, 1.0, sy))[..., None]
    else:  # lava world: dark basalt crust with glowing cracks
        crust = fft_noise((nlat, nlon), 2.2, seed + 3)
        cr = sample(ridged(fft_noise((nlat, nlon), 2.0, seed + 4)), gy, gx)
        cc = sample(crust, gy, gx)
        col = mix(rgb(30, 24, 22), rgb(70, 58, 50), smoothstep(-1, 1.5, cc)[..., None])
        glow_k = smoothstep(0.80, 0.97, cr) * smoothstep(-0.2, 0.8, cc)
    # storms on gas giants
    if kind == "saturn":
        sp = np.exp(-(((lat - 0.55) / 0.05) ** 2 + ((lon - 0.7) / 0.14) ** 2))
        col = mix(col, rgb(245, 238, 220), (0.7 * sp)[..., None])

    # lighting: Minnaert-ish limb darkening and a soft terminator
    nx, ny, nz = sx, sy, sz
    cosi = nx * Lp[0] + ny * Lp[1] + nz * Lp[2]
    view = np.array([0, se, ce])                               # direction to the viewer in planet frame
    cosv = nx * view[0] + ny * view[1] + nz * view[2]
    k = 0.75
    lit = np.clip(cosi, 0, 1) ** k * np.clip(cosv, 1e-3, 1) ** (k - 1)
    lit *= smoothstep(-0.06, 0.12, cosi)
    # ring parameters (planet radii)
    R_IN, R_OUT = 1.24, 2.28
    rprof_n = 4096
    rr = np.linspace(R_IN, R_OUT, rprof_n)
    ringlets = noise1d(rprof_n, 1.4, seed + 7)
    dens = 0.55 + 0.25 * np.tanh(ringlets) + 0.15 * np.sin(rr * 90)
    dens *= 1 - 0.95 * np.exp(-((rr - 1.95) / 0.035) ** 2)     # Cassini division
    dens *= 1 - 0.6 * np.exp(-((rr - 2.16) / 0.01) ** 2)      # Encke gap
    dens *= smoothstep(R_IN, R_IN + 0.08, rr) * (1 - smoothstep(R_OUT - 0.10, R_OUT, rr))
    dens *= np.where(rr < 1.53, 0.35, 1.0)                     # faint inner C ring
    if kind == "ice":
        dens *= 0.25
    if kind == "lava" or not ring:
        dens *= 0.0
    ring_col = rgb(214, 196, 160) if kind != "ice" else rgb(180, 200, 210)
    ring_col = ring_col * (1 + 0.06 * np.sin(rr * 37))[:, None]

    # ring shadow on the planet: march from the surface point toward the light to the ring plane (y = 0)
    with np.errstate(divide="ignore", invalid="ignore"):
        tr = -sy / Lp[1] if abs(Lp[1]) > 1e-6 else np.full_like(sy, -1)
        qx = sx + Lp[0] * tr; qz = sz + Lp[2] * tr
        qr = np.sqrt(qx * qx + qz * qz)
        ridx = np.clip((qr - R_IN) / (R_OUT - R_IN) * (rprof_n - 1), 0, rprof_n - 1).astype(int)
        shadow = np.where((tr > 0) & (qr > R_IN) & (qr < R_OUT), dens[ridx] * 0.9, 0.0)
    lit = lit * (1 - shadow)

    # thin atmosphere on the limb, brightest on the lit side
    rim = np.exp(-np.clip(cosv, 0, 1) / 0.12) * smoothstep(-0.2, 0.4, cosi)
    planet = col * lit[..., None] + (rim * 0.35)[..., None] * (rgb(170, 200, 255) if kind == "ice" else rgb(255, 230, 190))
    if kind == "lava":
        planet = planet + (glow_k * (0.4 + 0.6 * (1 - np.clip(cosi * 2, 0, 1))))[..., None] * rgb(255, 110, 30) * 2.2
    # night side: faint ringshine / starlight
    planet += col * 0.012
    planet_alpha = np.clip((1 - np.sqrt(r2)) * scale + 0.5, 0, 1)

    # rings: intersect the ring plane. Ray in screen space travels along -z (view), in planet frame along -view
    # point on the ray: body coords (bx, by, s) for depth s, pitched into planet frame
    # plane y_p = 0: by*ce + s*se = 0  -> s = -by*ce/se
    out = np.zeros((H_, W_, 3), np.float32)
    alpha = np.zeros((H_, W_), np.float32)
    if dens.max() > 0:
        s = -by * ce / se
        qx = bx; qz = -by * se + s * ce
        qr = np.sqrt(qx * qx + qz * qz)
        ridx = np.clip((qr - R_IN) / (R_OUT - R_IN) * (rprof_n - 1), 0, rprof_n - 1).astype(int)
        on = (qr > R_IN) & (qr < R_OUT)
        ra = np.where(on, dens[ridx], 0.0)
        # lit side or back side of the rings
        lit_side = 1.0 if Lp[1] * se > 0 else 0.35
        rcol = ring_col[ridx] * (0.6 + 0.4 * abs(Lp[1])) * lit_side
        # planet's shadow on the rings: ray from ring point toward the light hits the sphere?
        b_ = qx * Lp[0] + qz * Lp[2]
        c_ = qx * qx + qz * qz - 1
        disc = b_ * b_ - c_
        in_shadow = (disc > 0) & (-b_ - np.sqrt(np.clip(disc, 0, None)) > 0)
        rcol = rcol * np.where(in_shadow, 0.04, 1.0)[..., None]
        ring_in_front = s > zs  # nearer to the viewer than the sphere surface (depth along +z toward viewer)
        # composite: back ring, planet, front ring
        back = on & ~(hit & ring_in_front) & ~hit
        front = on & (ring_in_front | ~hit)
        # far ring (behind planet or beside it)
        out += np.where(back[..., None], rcol * ra[..., None], 0)
        alpha = np.where(back, ra, alpha)
        # planet over
        pa = planet_alpha * hit
        out = out * (1 - pa[..., None]) + planet * pa[..., None]
        alpha = alpha * (1 - pa) + pa
        # ring in front of the planet
        fa = np.where(front & hit, ra, 0.0)
        out = out * (1 - fa[..., None]) + rcol * fa[..., None]
        alpha = alpha * (1 - fa) + fa
    else:
        pa = planet_alpha * hit
        out = planet * pa[..., None]
        alpha = pa
    return out.astype(np.float32), alpha.astype(np.float32)


# ------------------------------------------------------------------ landscapes
def ridge_line(w, base, amp, seed, beta=2.2, peaks=0.6):
    """A mountain ridge: broad smooth massifs, a few sharper summits, fine rocky detail - pixels from the top."""
    big = noise1d(w * 2, 3.0, seed)[:w]
    summits = 1 - np.abs(np.tanh(0.8 * noise1d(w * 2, 3.2, seed + 2)[:w]))           # low-frequency cusps only
    rocky = noise1d(w * 2, 1.9, seed + 1)[:w]
    hgt = big * 0.6 + peaks * summits ** 2 * 1.2 + 0.035 * rocky
    hgt = (hgt - hgt.min()) / (hgt.max() - hgt.min() + 1e-9)
    return (base - amp * hgt).astype(np.float32)


def conifers(w, h, ground, seed, density=0.06, hmin=18, hmax=70, lean=0.0):
    """Silhouette mask of spruce trees standing on the ground line (pixel y per x)."""
    r = np.random.default_rng(seed)
    mask = np.zeros((h, w), np.float32)
    x = 0.0
    while x < w:
        x += r.exponential(1.0 / density)
        if x >= w:
            break
        th = r.uniform(hmin, hmax) * (0.6 + 0.4 * r.random())
        tw = th * r.uniform(0.18, 0.28)
        gx = int(x)
        gy = ground[min(gx, w - 1)] + r.uniform(1, 4)
        top = gy - th
        y0, y1 = int(max(0, top - 2)), int(min(h, gy + 2))
        x0, x1 = int(max(0, x - tw - 3)), int(min(w, x + tw + 3))
        if y1 <= y0 or x1 <= x0:
            continue
        yy, xx = np.mgrid[y0:y1, x0:x1].astype(np.float32)
        f = np.clip((yy - top) / th, 0, 1)                      # 0 at the tip, 1 at the base
        tiers = 0.75 + 0.25 * np.abs(np.sin(f * th * 0.33 + r.random() * 6))   # drooping branch tiers
        half = tw * f * tiers * (0.9 + 0.2 * r.random()) + 0.6
        jag = 0.8 * np.sin(yy * 1.7 + xx * 0.3)
        d = np.abs(xx + 0.5 - x - lean * (yy - gy)) - half - jag * f
        trunk = (np.abs(xx + 0.5 - x) < max(0.8, tw * 0.07)) & (yy > gy - th * 0.12)
        m = np.clip(0.5 - d, 0, 1) * (yy >= top)
        m = np.maximum(m, trunk.astype(np.float32))
        mask[y0:y1, x0:x1] = np.maximum(mask[y0:y1, x0:x1], m)
    return mask


def layered_mountains(img, horizon_col, layers, seed=3, fog_col=None, snow=None, trees=None, rim=None):
    """
    Paints mountain ranges far to near over img (linear RGB, modified in place).
    layers = list of (base_y, amplitude, darkness 0..1 (1 = black), fog 0..1)
    Aerial perspective: far ranges are lit by the sky and fade into the horizon colour; each range sinks into
    valley mist at its foot. Returns the y of the nearest ridge (for trees / reflections).
    """
    h, w = img.shape[:2]
    yy = np.arange(h, dtype=np.float32)[:, None]
    fog_col = horizon_col if fog_col is None else fog_col
    last = None
    for i, (base, amp, dark, fog) in enumerate(layers):
        ridge = ridge_line(w, base, amp, seed + i * 17, beta=2.0 + 0.25 * i, peaks=0.5 + 0.15 * i)
        cov = np.clip(yy - ridge[None, :] + 0.5, 0, 1)
        depth = np.clip((yy - ridge[None, :]) / max(amp, 1.0), 0, 3)
        rock = horizon_col * (1 - dark)
        tex = 1 + 0.18 * fft_noise((h, w), 1.7, seed + 100 + i)[..., None] * (1 - dark)
        col = rock * tex
        if snow is not None and i < len(layers) - 1:
            alt = 1 - (yy - (base - amp)) / max(amp, 1.0)                  # 1 at the highest possible summit
            sn = smoothstep(0.45, 0.8, alt + 0.15 * fft_noise((h, w), 2.4, seed + 200 + i)) * smoothstep(0.0, 0.15, depth)
            col = mix(col, snow * (1 - 0.6 * dark), (sn * 0.7)[..., None])
        if rim is not None:
            col = col + rim * (np.exp(-np.maximum(yy - ridge[None, :], 0) / 2.0) * (1 - dark))[..., None]
        mist = smoothstep(0.2, 1.4, depth) * fog
        col = mix(col, fog_col, mist[..., None])
        img[:] = mix(img, col, cov[..., None])
        last = ridge
    if trees is not None:
        m = conifers(w, h, last, seed + 999, **trees)
        img[:] = mix(img, horizon_col * 0.008, m[..., None])
    return last


def sky_gradient(h, w, horizon_y, zenith, horizon, glow=None, glow_h=0.12):
    """Night sky: dark zenith, airglow-brightened band near the horizon."""
    yy = np.arange(h, dtype=np.float32)[:, None, None]
    t = np.clip((horizon_y - yy) / horizon_y, 0, 1)
    sky = mix(horizon, zenith, (t ** 0.55))
    if glow is not None:
        sky = sky + glow * np.exp(-np.clip((horizon_y - yy) / (h * glow_h), 0, None))
    return np.broadcast_to(sky, (h, w, 3)).astype(np.float32).copy()
