"""
High-detail wing textures for Tempest cosmetics (original procedural art), 512x512:
  src/main/resources/assets/tempest/textures/cosmetics/wings_<name>.png

Each texture is ONE right wing: the root (where it meets the back) is at the left edge about a third of the way
down; the game mirrors it for the left wing and hinges it at u = 0.34 and 0.68 (so keep big shapes flowing across
those lines). Run:  python tools/make_wings_hq.py   (numpy, scipy, Pillow)
"""
import math
import os
import sys

import numpy as np
from PIL import Image
from scipy.ndimage import gaussian_filter, zoom

OUT = sys.argv[1] if len(sys.argv) > 1 else os.path.join(os.path.dirname(__file__), "..",
                                                        "src/main/resources/assets/tempest/textures/cosmetics")
N = 512          # final size
SS = 2           # supersampling
M = N * SS
ROOT = (0.02, 0.32)
YY, XX = np.mgrid[0:M, 0:M].astype(np.float32) / M


def smooth(e0, e1, x):
    t = np.clip((x - e0) / (e1 - e0), 0, 1)
    return t * t * (3 - 2 * t)


def fbm(seed, octaves=5, base=6):
    """Tileless value-noise fBm in [0,1] over the canvas."""
    r = np.random.default_rng(seed)
    out = np.zeros((M, M), np.float32)
    amp, tot = 1.0, 0.0
    for o in range(octaves):
        n = base * 2 ** o
        g = r.random((n + 1, n + 1)).astype(np.float32)
        up = zoom(g, M / (n + 1), order=3)[:M, :M]
        out += amp * up
        tot += amp
        amp *= 0.5
    return np.clip(out / tot, 0, 1)


class Canvas:
    def __init__(self):
        self.rgb = np.zeros((M, M, 3), np.float32)
        self.a = np.zeros((M, M), np.float32)

    def over(self, rgb, a, box=None):
        """Composite (rgb, alpha) over the canvas; rgb may be (3,) or (h,w,3)."""
        if box is None:
            box = (0, M, 0, M)
        y0, y1, x0, x1 = box
        if y1 <= y0 or x1 <= x0:
            return
        ca = self.a[y0:y1, x0:x1]
        cr = self.rgb[y0:y1, x0:x1]
        na = a + ca * (1 - a)
        safe = np.maximum(na, 1e-6)[..., None]
        self.rgb[y0:y1, x0:x1] = (np.asarray(rgb) * a[..., None] + cr * (ca * (1 - a))[..., None]) / safe
        self.a[y0:y1, x0:x1] = na

    def shade(self, factor, box=None):
        """Darken / tint existing colour where the canvas already has paint (ambient occlusion)."""
        y0, y1, x0, x1 = box or (0, M, 0, M)
        self.rgb[y0:y1, x0:x1] *= factor[..., None] if np.ndim(factor) == 2 else factor

    def save(self, name):
        img = np.dstack([np.clip(self.rgb, 0, 1), np.clip(self.a, 0, 1)])
        # un-premultiply-safe downsample: average premultiplied, then divide
        pm = img.copy()
        pm[..., :3] *= pm[..., 3:4]
        small = pm.reshape(N, SS, N, SS, 4).mean(axis=(1, 3))
        a = small[..., 3:4]
        small[..., :3] = np.where(a > 1e-4, small[..., :3] / np.maximum(a, 1e-4), 0)
        Image.fromarray((np.clip(small, 0, 1) * 255 + 0.5).astype(np.uint8), "RGBA").save(
            os.path.join(OUT, name + ".png"), optimize=True)
        print("  " + name)


def bbox(points, pad):
    xs = [p[0] for p in points]
    ys = [p[1] for p in points]
    x0 = max(0, int((min(xs) - pad) * M))
    x1 = min(M, int((max(xs) + pad) * M) + 1)
    y0 = max(0, int((min(ys) - pad) * M))
    y1 = min(M, int((max(ys) + pad) * M) + 1)
    return y0, y1, x0, x1


def lerp(a, b, t):
    a = np.asarray(a, np.float32)
    b = np.asarray(b, np.float32)
    t = np.asarray(t, np.float32)
    if t.ndim:
        t = t[..., None]
    return a + (b - a) * t


def c(hexs):
    hexs = hexs.lstrip("#")
    return np.array([int(hexs[i:i + 2], 16) / 255 for i in (0, 2, 4)], np.float32)


# ------------------------------------------------------------------ feathers
def feather(cv, base, tip, width, col_base, col_tip, edge, rachis, barb=0.10, shadow=0.30, gloss=0.0, glow=None):
    """One feather from base to tip: tapered vane, central shaft, barb striations, soft drop shadow."""
    bx, by = base
    tx, ty = tip
    L = math.hypot(tx - bx, ty - by)
    ax, ay = (tx - bx) / L, (ty - by) / L
    box = bbox([base, tip], width * 1.6 + 0.03)
    y0, y1, x0, x1 = box
    X, Y = XX[y0:y1, x0:x1], YY[y0:y1, x0:x1]
    # shadow: same shape a little lower and wider, soft
    sx, sy = X - 0.006, Y - 0.012
    t = ((sx - bx) * ax + (sy - by) * ay) / L
    d = (-(sx - bx) * ay + (sy - by) * ax)
    hw = width * np.power(np.clip(np.sin(np.pi * np.clip(t, 0, 1) ** 0.75), 0, 1), 0.55)
    sh = shadow * smooth(hw + 0.012, hw - 0.004, np.abs(d)) * smooth(-0.05, 0.05, t) * smooth(1.08, 0.95, t)
    cv.shade(1 - sh * (cv.a[y0:y1, x0:x1] > 0), box)
    # the feather
    t = ((X - bx) * ax + (Y - by) * ay) / L
    d = (-(X - bx) * ay + (Y - by) * ax)
    side = np.where(d < 0, 0.78, 1.0)          # narrow leading vane
    hw = width * side * np.power(np.clip(np.sin(np.pi * np.clip(t, 0, 1) ** 0.75), 0, 1), 0.55)
    inside = (t >= 0) & (t <= 1)
    r = np.abs(d) / np.maximum(hw, 1e-5)
    a = smooth(1.0, 0.86, r) * inside
    col = lerp(col_base, col_tip, np.clip(t, 0, 1) ** 1.2)
    # roundness + barbs (stripes angled back along the vane)
    stripes = 0.5 + 0.5 * np.sin((t * L * 260 - np.abs(d) * 180))
    light = (1 - 0.22 * r ** 2) * (1 - barb + barb * stripes)
    light = light * (1 + gloss * np.exp(-((r - 0.35) / 0.18) ** 2) * (d > 0))
    col = col * light[..., None]
    col = lerp(col, edge, smooth(0.70, 1.0, r) * 0.65)
    shaft = smooth(0.0045, 0.0015, np.abs(d)) * smooth(1.0, 0.75, t)
    col = lerp(col, rachis, shaft * 0.9)
    if glow is not None:
        col = lerp(col, glow, smooth(0.65, 1.0, t) * 0.45)
    cv.over(col, a, box)


def feathered_wing(name, rows, edge, rachis, bone, bone_hi, shade_edge, glow=None, gloss=0.0, seed=1):
    cv = Canvas()
    rng = np.random.default_rng(seed)

    def arm(s):        # the leading edge (wrist bones) as a curve rising toward the tip
        return ROOT[0] + s * 0.74, ROOT[1] - 0.22 * math.sin(math.pi * 0.5 * s) + 0.035

    def fit(bx, by, ang, L):  # keep every feather tip inside the texture
        cx, cy = math.cos(ang), math.sin(ang)
        if cx > 0.01:
            L = min(L, (0.985 - bx) / cx)
        if cy > 0.01:
            L = min(L, (0.985 - by) / cy)
        return L

    for count, s0, s1, length, width, col_b, col_t, ang0, ang1 in rows:
        for i in range(count):
            t = i / max(1, count - 1)
            s = s0 + (s1 - s0) * t
            bx, by = arm(s)
            by += 0.015
            ang = math.radians(ang0 + (ang1 - ang0) * t + rng.uniform(-2.5, 2.5))
            L = fit(bx, by, ang, length * (0.72 + 0.55 * t ** 1.3) * rng.uniform(0.95, 1.04))
            tip = (bx + math.cos(ang) * L, by + math.sin(ang) * L)
            tint = rng.uniform(-0.05, 0.05)
            feather(cv, (bx, by), tip, width * rng.uniform(0.92, 1.06), np.clip(col_b + tint, 0, 1),
                    np.clip(col_t + tint * 0.5, 0, 1), edge, rachis, gloss=gloss, glow=glow)
    # marginal coverts: a row of small rounded scale feathers along the arm
    for i in range(15):
        s = 0.02 + 0.92 * i / 14
        bx, by = arm(s)
        ang = math.radians(96 - 36 * s)
        L = 0.11 + 0.02 * math.sin(i * 1.7)
        feather(cv, (bx, by - 0.012), (bx + math.cos(ang) * L, by + math.sin(ang) * L), 0.042,
                lerp(rows[-1][5], bone, 0.3), rows[-1][6], edge, rachis, barb=0.05, shadow=0.22, gloss=gloss)
    # the arm: one smooth rounded ridge along the leading edge with a highlight
    pts = [arm(s_) for s_ in np.linspace(0, 1, 40)]
    for k in range(len(pts) - 1):
        capsule(cv, pts[k], pts[k + 1], 0.026 * (1 - 0.55 * k / 39), 0.026 * (1 - 0.55 * (k + 1) / 39),
                bone, bone_hi, shade_edge, shine=0.5)
    return cv


def angel():
    rows = [  # count, along from, to, length, width, base col, tip col, angle at root, angle at tip
        (19, 0.28, 1.00, 0.66, 0.046, c("d4dcf0"), c("ffffff"), 100, 18),
        (16, 0.08, 0.90, 0.44, 0.044, c("e2e8f8"), c("ffffff"), 98, 34),
        (14, 0.02, 0.76, 0.25, 0.040, c("eef2ff"), c("ffffff"), 96, 50),
    ]
    cv = feathered_wing("wings_angel", rows, edge=c("aab6d6"), rachis=c("ffffff"), bone=c("f4f6ff"),
                        bone_hi=c("ffffff"), shade_edge=c("b8c2e0"), gloss=0.12, seed=11)
    # faint holy shimmer near the tips
    sh = smooth(0.55, 0.95, XX) * (0.5 + 0.5 * np.sin(XX * 80 + YY * 40)) * 0.08
    cv.rgb = np.clip(cv.rgb + sh[..., None] * c("fff3c2"), 0, 1)
    cv.save("wings_angel")


def phoenix():
    rows = [
        (19, 0.28, 1.00, 0.68, 0.048, c("8e1208"), c("ff5a14"), 100, 18),
        (16, 0.08, 0.90, 0.46, 0.046, c("b8220a"), c("ff8a1c"), 98, 34),
        (14, 0.02, 0.76, 0.26, 0.042, c("e0480e"), c("ffc23a"), 96, 50),
    ]
    cv = feathered_wing("wings_phoenix", rows, edge=c("6e1206"), rachis=c("ffe39a"), bone=c("ffb347"),
                        bone_hi=c("fff0b0"), shade_edge=c("a3300a"), glow=c("fff2a0"), gloss=0.18, seed=23)
    # embers: hot glow bleeding off the tips
    heat = gaussian_filter(cv.a * smooth(0.45, 1.0, XX + (YY - 0.3) * 0.6), 6 * SS)
    cv.rgb = np.clip(cv.rgb + (heat * 0.22)[..., None] * c("ffb040"), 0, 1)
    rng = np.random.default_rng(5)
    for _ in range(70):
        x, y = rng.uniform(0.35, 1.0), rng.uniform(0.45, 1.0)
        if cv.a[int(y * M) - 1, int(x * M) - 1] > 0.5:
            continue
        r = rng.uniform(0.004, 0.009)
        box = bbox([(x, y)], r * 3)
        y0, y1, x0, x1 = box
        d = np.sqrt((XX[y0:y1, x0:x1] - x) ** 2 + (YY[y0:y1, x0:x1] - y) ** 2) / r
        cv.over(c("ffd66a"), np.exp(-d * d) * 0.9, box)
    cv.save("wings_phoenix")


# ------------------------------------------------------------------ membrane (bat / dragon)
def capsule(cv, p0, p1, r0, r1, col, hi, dark, shine=0.7):
    box = bbox([p0, p1], max(r0, r1) + 0.01)
    y0, y1, x0, x1 = box
    X, Y = XX[y0:y1, x0:x1], YY[y0:y1, x0:x1]
    vx, vy = p1[0] - p0[0], p1[1] - p0[1]
    L2 = vx * vx + vy * vy
    t = np.clip(((X - p0[0]) * vx + (Y - p0[1]) * vy) / L2, 0, 1)
    px, py = p0[0] + vx * t, p0[1] + vy * t
    rad = r0 + (r1 - r0) * t
    dx, dy = X - px, Y - py
    dist = np.sqrt(dx * dx + dy * dy)
    r = dist / rad
    a = smooth(1.0, 0.82, r)
    # cylinder lighting from above-left
    L = math.hypot(vx, vy)
    nx, ny = -vy / L, vx / L
    side = (dx * nx + dy * ny) / np.maximum(rad, 1e-5)
    lit = np.clip(0.55 - 0.55 * side * np.sign(ny if ny != 0 else 1), 0, 1)
    col_px = lerp(dark, col, lit)
    col_px = lerp(col_px, hi, np.exp(-((side + 0.45) / 0.22) ** 2) * shine)
    cv.over(col_px, a, box)


def membrane_wing(name, top, bottom, vein, bone, bone_hi, bone_dark, edge, claws, spikes, rim=None, seed=3):
    cv = Canvas()
    wrist = (0.40, 0.13)
    tips = [(0.985, 0.08), (0.95, 0.44), (0.80, 0.76), (0.54, 0.93), (0.25, 0.86)]
    # membrane polygon with scalloped trailing edge between finger tips
    from PIL import ImageDraw
    outline = [ROOT, wrist]
    for i, tp in enumerate(tips):
        outline.append(tp)
        if i + 1 < len(tips):
            nx, ny = tips[i + 1]
            mx, my = (tp[0] + nx) / 2, (tp[1] + ny) / 2
            cx, cy = wrist[0] + (mx - wrist[0]) * 0.80, wrist[1] + (my - wrist[1]) * 0.80
            for s in np.linspace(0.08, 0.92, 14):
                outline.append(((1 - s) ** 2 * tp[0] + 2 * (1 - s) * s * cx + s * s * nx,
                                (1 - s) ** 2 * tp[1] + 2 * (1 - s) * s * cy + s * s * ny))
    outline.append((ROOT[0] + 0.03, ROOT[1] + 0.22))
    mimg = Image.new("L", (M, M), 0)
    ImageDraw.Draw(mimg).polygon([(x * M, y * M) for x, y in outline], fill=255)
    mask = gaussian_filter(np.asarray(mimg, np.float32) / 255, 0.8 * SS)
    # membrane colour: gradient + mottled skin + fine vein network
    n1, n2 = fbm(seed, 5, 5), fbm(seed + 1, 4, 18)
    col = lerp(top, bottom, smooth(0.05, 0.95, YY * 0.8 + XX * 0.2))
    col = col * (0.82 + 0.30 * n1)[..., None]
    veins = np.abs(fbm(seed + 2, 5, 7) - 0.5)
    vmask = smooth(0.018, 0.0, veins)
    col = lerp(col, vein, vmask * 0.32)
    col = col * (0.92 + 0.10 * n2)[..., None]
    # thinner (lighter, more see-through) away from the bones, darker near the edge
    edge_d = gaussian_filter(mask, 10 * SS)
    col = lerp(col, edge, smooth(0.65, 0.2, edge_d) * 0.55)
    if rim is not None:
        col = lerp(col, rim, smooth(0.5, 0.15, edge_d) * smooth(0.0, 0.1, mask) * 0.8)
    alpha = mask * (0.80 + 0.15 * n1)
    cv.over(col, alpha)
    # soft shadow lines where finger bones sit
    for tp in tips:
        box = bbox([wrist, tp], 0.04)
        y0, y1, x0, x1 = box
        X, Y = XX[y0:y1, x0:x1], YY[y0:y1, x0:x1]
        vx, vy = tp[0] - wrist[0], tp[1] - wrist[1]
        tt = np.clip(((X - wrist[0]) * vx + (Y - wrist[1]) * vy) / (vx * vx + vy * vy), 0, 1)
        d = np.hypot(X - wrist[0] - vx * tt, Y - wrist[1] - vy * tt)
        cv.shade(1 - 0.35 * smooth(0.035, 0.0, d) * (cv.a[y0:y1, x0:x1] > 0), box)
    # bones: upper arm, forearm, fingers (tapering), knuckle at the wrist
    capsule(cv, (ROOT[0] - 0.01, ROOT[1] + 0.02), wrist, 0.030, 0.024, bone, bone_hi, bone_dark)
    for k, tp in enumerate(tips):
        mid = (wrist[0] + (tp[0] - wrist[0]) * 0.45, wrist[1] + (tp[1] - wrist[1]) * 0.45 - 0.01 * (k < 2))
        capsule(cv, wrist, mid, 0.017, 0.012, bone, bone_hi, bone_dark)
        capsule(cv, mid, tp, 0.012, 0.004, bone, bone_hi, bone_dark)
    capsule(cv, (wrist[0] - 0.005, wrist[1]), (wrist[0] + 0.005, wrist[1]), 0.03, 0.03, bone, bone_hi, bone_dark)
    # claw on the thumb, spikes at finger tips
    def claw(base, tip, w, col):
        from PIL import ImageDraw as D
        im = Image.new("L", (M, M), 0)
        bx, by = base
        tx, ty = tip
        L = math.hypot(tx - bx, ty - by)
        px, py = -(ty - by) / L * w, (tx - bx) / L * w
        D.Draw(im).polygon([((bx + px) * M, (by + py) * M), (tx * M, ty * M), ((bx - px) * M, (by - py) * M)], fill=255)
        a = gaussian_filter(np.asarray(im, np.float32) / 255, 0.6 * SS)
        shade = lerp(col, c("ffffff"), smooth(0.4, 1.0, (XX - bx) * (tx - bx) / L / L + (YY - by) * (ty - by) / L / L) * 0.25)
        cv.over(shade, a)
    claw((wrist[0] - 0.01, wrist[1] - 0.01), (wrist[0] - 0.06, wrist[1] - 0.10), 0.018, claws)
    if spikes:
        for tp in tips[:-1]:
            claw(tp, (tp[0] + 0.035, tp[1] - 0.05), 0.010, claws)
        for s in np.linspace(0.15, 0.85, 4):     # ridge spikes along the forearm
            bx, by = ROOT[0] + (wrist[0] - ROOT[0]) * s, ROOT[1] + (wrist[1] - ROOT[1]) * s
            claw((bx, by - 0.01), (bx + 0.01, by - 0.055), 0.012, claws)
    cv.save(name)


def demon():
    membrane_wing("wings_demon", top=c("6a0d14"), bottom=c("2a0208"), vein=c("ff3b3b"), bone=c("3a1216"),
                  bone_hi=c("a0474a"), bone_dark=c("12040a"), edge=c("1a0206"), claws=c("e8e0d0"), spikes=False,
                  rim=c("ff4d3d"), seed=31)


def dragon():
    membrane_wing("wings_dragon", top=c("244a78"), bottom=c("0f6a62"), vein=c("7fe0ff"), bone=c("1e2a44"),
                  bone_hi=c("6f8cc4"), bone_dark=c("0a0f1c"), edge=c("0a1626"), claws=c("dfe8f0"), spikes=True,
                  rim=c("5ef2d0"), seed=47)


# ------------------------------------------------------------------ butterfly / fairy (insect wings)
def lobe(cx, cy, rx, ry, rot, wobble=0.0, freq=7, phase=0.0):
    cr, sr = math.cos(rot), math.sin(rot)
    dx, dy = XX - cx, YY - cy
    u = (dx * cr + dy * sr) / rx
    v = (-dx * sr + dy * cr) / ry
    ang = np.arctan2(v, u)
    return np.sqrt(u * u + v * v) * (1 + wobble * np.sin(ang * freq + phase))


def butterfly():
    cv = Canvas()
    up = lobe(0.52, 0.30, 0.48, 0.29, math.radians(-16), 0.03, 9)
    lo = lobe(0.37, 0.70, 0.31, 0.25, math.radians(30), 0.05, 6, 1.0)
    r = np.minimum(up, lo)
    inside = smooth(1.0, 0.985, r)
    ang = np.arctan2(YY - 0.48, XX - 0.02)
    dist = np.hypot(XX - 0.02, YY - 0.48)
    # base colours: deep blue at the body to violet / magenta, with a soft iridescent sheen
    col = lerp(c("1d3fd6"), c("b03af0"), smooth(0.15, 0.85, dist))
    sheen = 0.5 + 0.5 * np.sin(dist * 22 + ang * 3)
    col = lerp(col, c("63e6ff"), smooth(0.2, 0.0, np.abs(dist - 0.38)) * 0.35 * sheen)
    col = col * (0.85 + 0.25 * fbm(5, 4, 30))[..., None]           # scale texture
    # veins: radial from the root with a few cross veins
    rv = np.abs(np.sin(ang * 8.5)) < 0.05 + 0.02 * dist
    cross = np.abs(np.sin(dist * 18)) < 0.03
    vein = rv & (r < 0.9)
    col = lerp(col, c("0b0820"), vein * 0.75)
    # black border band with white spots
    band = smooth(0.80, 0.86, r)
    col = lerp(col, c("0d0a18"), band)
    spots = np.zeros_like(r)
    for k in range(28):
        a0 = -0.9 + 2.6 * k / 27
        for base_l, cx0, cy0, rx0, ry0, rot0 in ((up, 0.52, 0.30, 0.48, 0.29, -16), (lo, 0.37, 0.70, 0.31, 0.25, 30)):
            pass
    # spots placed on the band by sampling points near r = 0.9 on each lobe
    for (cx0, cy0, rx0, ry0, rot0, n, a_from, a_to) in ((0.52, 0.30, 0.48, 0.29, -16, 13, -2.4, 1.2), (0.37, 0.70, 0.31, 0.25, 30, 9, -0.6, 2.6)):
        cr, sr = math.cos(math.radians(rot0)), math.sin(math.radians(rot0))
        for k in range(n):
            a0 = a_from + (a_to - a_from) * k / (n - 1)
            u, v = 0.91 * math.cos(a0), 0.91 * math.sin(a0)
            px = 0.0 + cx0 + u * rx0 * cr - v * ry0 * sr
            py = cy0 + u * rx0 * sr + v * ry0 * cr
            rr = 0.010 if k % 2 else 0.007
            spots = np.maximum(spots, smooth(rr, rr * 0.6, np.hypot(XX - px, YY - py)))
    col = lerp(col, c("fff6e0"), spots * band)
    # eye spots
    for ex, ey, er in ((0.71, 0.25, 0.065), (0.40, 0.71, 0.05)):
        de = np.hypot(XX - ex, YY - ey) / er
        col = lerp(col, c("ffd34d"), smooth(1.0, 0.9, de))
        col = lerp(col, c("ff7a1a"), smooth(0.78, 0.68, de))
        col = lerp(col, c("0b0820"), smooth(0.55, 0.45, de))
        col = lerp(col, c("ffffff"), smooth(0.16, 0.08, np.hypot(XX - ex + er * 0.18, YY - ey + er * 0.2) / er))
    # gentle lighting: lighter toward the top
    col = col * (1.05 - 0.2 * YY)[..., None]
    cv.over(col, inside * 0.96)
    cv.save("wings_butterfly")


def fairy():
    cv = Canvas()
    lobes = [lobe(0.53, 0.22, 0.50, 0.15, math.radians(-24), 0.02, 5), lobe(0.42, 0.40, 0.36, 0.11, math.radians(-4), 0.02, 5),
             lobe(0.38, 0.58, 0.33, 0.10, math.radians(18)), lobe(0.27, 0.72, 0.25, 0.08, math.radians(40))]
    hue_noise = fbm(9, 3, 4)
    for i, lb in enumerate(lobes):
        inside = smooth(1.0, 0.97, lb)
        hue = (XX * 0.8 + YY * 0.45 + hue_noise * 0.3 + i * 0.07) % 1.0
        col = np.stack([0.72 + 0.28 * np.cos(2 * np.pi * (hue + 0.00)),
                        0.74 + 0.26 * np.cos(2 * np.pi * (hue + 0.33)),
                        0.86 + 0.14 * np.cos(2 * np.pi * (hue + 0.66))], -1).astype(np.float32)
        # cell-like vein network
        cells = np.abs(fbm(60 + i, 4, 9) - 0.5)
        vein = smooth(0.02, 0.0, cells) * (lb < 0.95)
        ang = np.arctan2(YY - 0.42, XX - 0.02)
        main = (np.abs(np.sin(ang * 5 + i)) < 0.035) & (lb < 0.95)
        rim = smooth(0.86, 0.97, lb)
        col = lerp(col, c("ffffff"), np.clip(vein * 0.6 + main * 0.85 + rim * 0.9, 0, 1))
        a = inside * np.clip(0.34 + 0.45 * rim + 0.45 * vein + 0.5 * main + 0.12 * (1 - lb), 0, 1)
        cv.over(col, a)
    # sparkles
    rng = np.random.default_rng(14)
    for _ in range(26):
        x, y = rng.uniform(0.08, 0.95), rng.uniform(0.06, 0.82)
        if cv.a[int(y * M), int(x * M)] < 0.2:
            continue
        r = rng.uniform(0.006, 0.014)
        box = bbox([(x, y)], r * 3)
        y0, y1, x0, x1 = box
        dx, dy = np.abs(XX[y0:y1, x0:x1] - x) / r, np.abs(YY[y0:y1, x0:x1] - y) / r
        star = np.clip(np.exp(-dx * 6) * np.exp(-dy * 0.8) + np.exp(-dy * 6) * np.exp(-dx * 0.8), 0, 1)
        cv.over(c("ffffff"), star * 0.95, box)
    cv.save("wings_fairy")


# ------------------------------------------------------------------ crystal
def crystal():
    cv = Canvas()
    rng = np.random.default_rng(17)
    from PIL import ImageDraw
    shards = []
    for i in range(24):
        t = i / 23
        bx = ROOT[0] + t * 0.80
        by = ROOT[1] - 0.17 * math.sin(math.pi * 0.5 * t) + 0.03
        ang = math.radians(100 - 70 * t + rng.uniform(-7, 7))
        L = (0.30 + 0.46 * math.sin(math.pi * (0.25 + 0.6 * t))) * rng.uniform(0.85, 1.1)
        L = min(L, (0.985 - by) / max(0.05, math.sin(ang)), (0.985 - bx) / max(0.05, math.cos(ang)) if math.cos(ang) > 0.05 else 9)
        w = 0.045 + 0.03 * rng.random()
        shards.append((bx, by, ang, L, w, t))
    shards.sort(key=lambda s: -s[3])            # long ones at the back
    for bx, by, ang, L, w, t in shards:
        tx, ty = bx + math.cos(ang) * L, by + math.sin(ang) * L
        px, py = -math.sin(ang) * w, math.cos(ang) * w
        mid = 0.32
        mx, my = bx + (tx - bx) * mid, by + (ty - by) * mid
        poly_l = [(bx, by), (mx + px, my + py), (tx, ty), (mx, my)]
        poly_r = [(bx, by), (mx, my), (tx, ty), (mx - px, my - py)]
        tint = lerp(c("78dcff"), c("c8a0ff"), t)
        for k, poly in enumerate((poly_l, poly_r)):
            im = Image.new("L", (M, M), 0)
            ImageDraw.Draw(im).polygon([(x * M, y * M) for x, y in poly], fill=255)
            box = bbox(poly, 0.01)
            y0, y1, x0, x1 = box
            a = np.asarray(im, np.float32)[y0:y1, x0:x1] / 255
            a = gaussian_filter(a, 0.6 * SS)
            X, Y = XX[y0:y1, x0:x1], YY[y0:y1, x0:x1]
            along = np.clip(((X - bx) * (tx - bx) + (Y - by) * (ty - by)) / (L * L), 0, 1)
            facet = 1.12 if k == 0 else 0.78                          # one face lit, the other in shade
            col = lerp(tint * facet, c("ffffff"), smooth(0.55, 1.0, along) * 0.35)
            col = lerp(col, c("ffffff"), (0.5 + 0.5 * np.sin(along * 40 + k)) * 0.06)
            cv.over(np.clip(col, 0, 1), a * (0.55 + 0.25 * along), box)
        # bright edges
        for p0, p1 in (((bx, by), (tx, ty)), ((mx + px, my + py), (tx, ty)), ((mx - px, my - py), (tx, ty))):
            capsule(cv, p0, p1, 0.0035, 0.002, c("e8fbff"), c("ffffff"), c("b0e8ff"), shine=0.3)
    # inner glow
    glow = gaussian_filter(cv.a, 8 * SS)
    cv.rgb = np.clip(cv.rgb + (glow * 0.18)[..., None] * c("bfefff"), 0, 1)
    # frosted arm
    pts = [(ROOT[0] + s * 0.82, ROOT[1] - 0.17 * math.sin(math.pi * 0.5 * s) + 0.02) for s in np.linspace(0, 1, 40)]
    for k in range(len(pts) - 1):
        capsule(cv, pts[k], pts[k + 1], 0.022 * (1 - 0.5 * k / 39), 0.022 * (1 - 0.5 * (k + 1) / 39),
                c("cfeeff"), c("ffffff"), c("7cc6f0"))
    cv.save("wings_crystal")


if __name__ == "__main__":
    os.makedirs(OUT, exist_ok=True)
    which = sys.argv[2:] or ["angel", "phoenix", "demon", "dragon", "butterfly", "fairy", "crystal"]
    for w in which:
        globals()[w]()
