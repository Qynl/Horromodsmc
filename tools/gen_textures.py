#!/usr/bin/env python3
"""Procedurally generates every PNG the mod ships.

Run from the repo root:  python3 tools/gen_textures.py

All textures are deterministic (fixed RNG seed) so repeated runs produce byte-identical files.

Art direction
-------------
"a real abandoned office that Minecraft accidentally kept", at 16x16 where it has to read from a
metre away. Concretely, every texture here obeys five rules the old generator did not:

  1. **Readable structure first.** Every material has a silhouette: wallpaper stripes, carpet tuft,
     ceiling perforation grid, brick courses with mortar, plate seams and rivets, pipes that are
     actually pipes. Noise is seasoning, never the subject.
  2. **Tileable.** All noise is a sum of integer-frequency sines, so opposite edges match exactly -
     no seams on a 40-block wall.
  3. **One light direction.** Light comes from the top-left: 1px highlight on top/left edges of a
     shape, 1px shadow on bottom/right. This is what makes 16x16 read as 3D.
  4. **Ordered dithering** on every gradient, so fades look like Minecraft pixels instead of blur.
  5. **Value contrast budget.** Each material keeps a dark and a light anchor at least ~35/255
     apart, otherwise the block turns to mush at distance.

Entity textures are 64px+ silhouettes (the old ones were 16px stretched over 2.6m bodies). The
Skin-Stealer keeps its UV contract with SkinStealerRenderer: head lives in x[0.25,0.75],
y[0,0.35]; body in y[0.35,1].
"""

import os

import numpy as np
from PIL import Image

OUT = os.path.join("src", "main", "resources", "assets", "backrooms", "textures")
BLOCK = os.path.join(OUT, "block")
ENTITY = os.path.join(OUT, "entity")
S = 16
rng = np.random.default_rng(1234)

# ---------------------------------------------------------------- helpers ---


def save(arr, path):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    a = np.clip(np.rint(arr), 0, 255).astype(np.uint8)
    Image.fromarray(a, "RGBA" if a.shape[-1] == 4 else "RGB").save(path)


def fbm(size, seed, octaves=3, base=2):
    """Tileable value-ish noise in [0,1]: sum of integer-frequency sines."""
    ys, xs = np.mgrid[0:size, 0:size] / float(size)
    out = np.zeros((size, size))
    r = np.random.default_rng(seed)
    amp, f = 1.0, base
    for _ in range(octaves):
        for _ in range(3):
            ph = r.random() * 2 * np.pi
            kx, ky = r.integers(1, f + 1, 2)
            out += amp * np.sin(2 * np.pi * (kx * xs + ky * ys) + ph)
        amp *= 0.55
        f *= 2
    out -= out.min()
    return out / max(out.max(), 1e-9)


def grain(size, seed, amount):
    return (np.random.default_rng(seed).random((size, size)) - 0.5)[..., None] * amount


def dither(img, strength=2.2):
    b = np.array([[0, 8, 2, 10], [12, 4, 14, 6], [3, 11, 1, 9], [15, 7, 13, 5]]) / 16.0 - 0.5
    ty = (img.shape[0] + 3) // 4
    tx = (img.shape[1] + 3) // 4
    tile = np.tile(b, (ty, tx))[: img.shape[0], : img.shape[1]]
    return img + tile[..., None] * strength


def base(size, rgb):
    return np.tile(np.array(rgb, float), (size, size, 1))


def bevel(img, x0, y0, x1, y1, light=26, dark=30):
    """1px top-left highlight / bottom-right shade on a rectangle (inclusive coords)."""
    img[y0, x0:x1 + 1] += light
    img[y0:y1 + 1, x0] += light * 0.7
    img[y1, x0:x1 + 1] -= dark
    img[y0:y1 + 1, x1] -= dark * 0.7
    return img


def rect(img, x0, y0, x1, y1, rgb):
    img[y0:y1 + 1, x0:x1 + 1] = rgb
    return img


# ----------------------------------------------------------------- level 0 ---


def write_wallpaper(idx):
    img = base(S, [206, 182, 96])
    img += (fbm(S, 10 + idx) - 0.5)[..., None] * 16
    # two-tone vertical stripes, 2px wide, with a 1px shadow between pairs
    for x in range(S):
        if x % 4 in (0, 1):
            img[x, :] *= 0.90
        if x % 4 == 2:
            img[x, :] *= 1.05
    img[:, 0] *= 0.97  # subtle horizontal weave
    img[:, 8] *= 0.97
    if idx >= 1:  # grime creeping from the bottom
        g = fbm(S, 40 + idx, octaves=2)
        grad = np.linspace(0.25, 1.0, S)[:, None]
        img *= (1.0 - 0.20 * (0.10 * idx + 0.4) * (g * grad)[..., None])
    if idx >= 2:  # damp streaks running down from the ceiling
        for x in (3, 4, 11):
            img[x, :10] *= 0.84
            img[x, :6] *= 0.90
    if idx == 3:  # mould bloom, soft blotches
        m = fbm(S, 77, octaves=4, base=4)
        bloom = np.clip((m - 0.66) * 5.0, 0, 1)
        img[..., 0] *= 1.0 - 0.40 * bloom
        img[..., 1] *= 1.0 - 0.26 * bloom
        img[..., 2] *= 1.0 - 0.34 * bloom
    save(dither(img), os.path.join(BLOCK, f"wallpaper_{idx}.png"))


def write_carpet(idx):
    img = base(S, [188, 160, 82])
    tuft = fbm(S, 90 + idx, octaves=4, base=7)
    img += (tuft - 0.5)[..., None] * 24          # fibre clumps, isotropic
    img += grain(S, 91 + idx, 26)                # per-pixel tuft
    speck = np.random.default_rng(92 + idx).random((S, S))
    img[speck > 0.86] *= 0.72                    # trodden-down dark specks
    img[speck < 0.10] *= 1.10                    # raised pale fibre
    g = fbm(S, 93 + idx, octaves=2)
    img *= (1.0 - 0.16 * (0.3 + 0.35 * idx) * g)[..., None]
    if idx == 2:
        img[:, :, 0] *= 0.90
        img[:, :, 2] *= 1.03                     # water-logged cast
    if idx == 3:
        img[:, :, 0] *= 0.78
        img[:, :, 1] *= 0.86
        img[:, :, 2] *= 1.04
    save(dither(img), os.path.join(BLOCK, f"carpet_{idx}.png"))


def write_ceiling(idx):
    img = base(S, [226, 219, 188])
    img += (fbm(S, 120 + idx, octaves=3) - 0.5)[..., None] * 12
    # perforation: 2x2 dot clusters on a 4px grid, plus the tile seam
    for x in range(S):
        for y in range(S):
            if x % 4 == 0 or y % 4 == 0:
                img[x, y] *= 0.62               # seam shadow
            elif (x % 4 == 2) and (y % 4 == 2):
                img[x, y] *= 0.80               # perforation dot
    img[0, :] *= 1.06                            # lit top edge of the tile
    g = fbm(S, 124 + idx, octaves=2)
    img *= (1.0 - 0.18 * (0.25 + 0.35 * idx) * g)[..., None]
    if idx >= 2:  # water ring
        ys, xs = np.mgrid[0:S, 0:S]
        r = np.sqrt((xs - 10.0) ** 2 + (ys - 6.0) ** 2)
        ring = (r > 2.2) & (r < 4.2)
        img[ring] *= 0.78
        img[ring, 0] *= 0.92
    if idx == 3:
        img[5:9, 4:12] *= 0.70                   # collapsed, stained patch
    save(dither(img), os.path.join(BLOCK, f"ceiling_tile_{idx}.png"))


def write_light():
    on = base(S, [60, 60, 62])                   # housing
    off = base(S, [60, 60, 62])
    for y in range(1, 15):
        for x in range(1, 15):
            tube = (x % 8) < 5
            on[y, x] = [252, 252, 240] if tube else [214, 214, 206]
            off[y, x] = [104, 104, 100]
    on[1:15, 1] += 10
    on[1, 1:15] += 10
    off[1:15, 1] += 8
    off[1, 1:15] += 8
    off[8, 4:12] = [86, 86, 84]                  # a dead tube, darker
    on[14, 1:15] -= 18
    on[1:15, 14] -= 18
    off[14, 1:15] -= 18
    off[1:15, 14] -= 18
    save(dither(on, 1.0), os.path.join(BLOCK, "fluorescent_light_on.png"))
    save(dither(off, 1.0), os.path.join(BLOCK, "fluorescent_light_off.png"))


def write_void():
    img = np.zeros((S, S, 3))
    ys, xs = np.mgrid[0:S, 0:S]
    t = ys / (S - 1)
    img[:, :, 0] = 246 - 40 * t
    img[:, :, 1] = 249 - 28 * t
    img[:, :, 2] = 255 - 10 * t
    img += (fbm(S, 200, octaves=3) - 0.5)[..., None] * 10   # faint clouds of nothing
    save(dither(img, 1.2), os.path.join(BLOCK, "void.png"))


# ------------------------------------------------------------------- props ---


def write_props():
    # chair: worn dark fabric with a seam and lighter worn crown
    c = base(S, [62, 60, 56]) + (fbm(S, 300, octaves=3) - 0.5)[..., None] * 26
    c[3, :] += 22                                 # worn crown
    c[12, :] -= 24                                # seat shadow
    c[:, 3] += 12
    c[:, 12] -= 14
    c[7:9, :] *= 0.86                             # seam between back and seat
    save(dither(c), os.path.join(BLOCK, "chair.png"))

    # desk: pale laminate, edge banding, scuffs and a coffee ring
    d = base(S, [176, 158, 116]) + (fbm(S, 301, octaves=2) - 0.5)[..., None] * 12
    for x in range(S):                            # grain lines
        if x % 5 == 2:
            d[:, x] *= 0.93
    ys, xs = np.mgrid[0:S, 0:S]
    ring = (np.sqrt((xs - 11) ** 2 + (ys - 5) ** 2) > 2.4) & (np.sqrt((xs - 11) ** 2 + (ys - 5) ** 2) < 3.4)
    d[ring] *= 0.86
    d[0, :] += 16
    d[:, 0] += 10
    d[15, :] -= 22
    d[:, 15] -= 16
    save(dither(d), os.path.join(BLOCK, "desk.png"))

    # barrel: dull steel, two rolling ribs, rust bloom, lit left edge
    b = base(S, [108, 110, 114]) + (fbm(S, 302, octaves=3) - 0.5)[..., None] * 20
    for y in (4, 11):
        b[y, :] = [142, 144, 148]
        b[y + 1, :] = [70, 72, 76]
    rust = fbm(S, 303, octaves=3, base=3) > 0.68
    b[rust, 0] *= 1.22
    b[rust, 1] *= 0.80
    b[rust, 2] *= 0.62
    b[:, 0:2] += 18
    b[:, 14:16] -= 20
    save(dither(b), os.path.join(BLOCK, "barrel.png"))

    # box: cardboard with flap seam, packing tape and a printed arrow
    x = base(S, [156, 120, 76]) + (fbm(S, 304, octaves=2) - 0.5)[..., None] * 14
    x[7, :] *= 0.78
    x[8, :] *= 0.78                               # flap seam
    x[6, :] = [196, 178, 140]                     # tape
    x[9, :] = [196, 178, 140]
    x[2:5, 3:5] = [96, 70, 44]                    # stencilled mark
    x[0, :] += 12
    x[:, 0] += 8
    x[15, :] -= 18
    x[:, 15] -= 14
    save(dither(x), os.path.join(BLOCK, "box.png"))

    # vending machine front: lit glass with two can rows, keypad, coin slot
    f = base(S, [148, 40, 46]) + (fbm(S, 305, octaves=2) - 0.5)[..., None] * 10
    rect(f, 2, 2, 10, 11, [26, 30, 34])           # glass cavity
    for cy in (4, 8):
        for cx in (3, 5, 7, 9):
            rect(f, cx, cy, cx, cy + 1, [196, 60, 54] if (cx + cy) % 4 else [80, 140, 190])
    f[2, 2:11] = [150, 190, 200]                  # glass glare
    f[3, 2:6] = [120, 160, 170]
    rect(f, 12, 2, 13, 8, [38, 38, 42])           # keypad
    for ky in range(3, 8, 2):
        f[ky, 12:14] = [120, 120, 126]
    rect(f, 12, 10, 13, 10, [206, 184, 84])       # coin slot
    rect(f, 2, 13, 10, 14, [30, 30, 34])          # delivery flap
    f[0, :] += 14
    f[:, 0] += 10
    f[15, :] -= 20
    save(dither(f), os.path.join(BLOCK, "vending_front.png"))

    s = base(S, [138, 36, 42]) + (fbm(S, 306, octaves=2) - 0.5)[..., None] * 12
    s[2:14, 3:5] *= 0.72                          # embossed logo band
    s[6:8, :] *= 1.18
    s[0, :] += 18
    s[:, 0] += 8
    s[15, :] -= 18
    s[:, 15] -= 14
    save(dither(s), os.path.join(BLOCK, "vending_side.png"))

    # camera: housing with a dark dome and a red tally light
    c = base(S, [74, 76, 80]) + (fbm(S, 307, octaves=2) - 0.5)[..., None] * 14
    rect(c, 5, 5, 10, 10, [26, 26, 30])           # dome
    rect(c, 6, 6, 8, 8, [12, 12, 16])
    c[6, 6] = [70, 70, 80]                        # glass glint
    rect(c, 11, 4, 12, 5, [190, 40, 36])         # tally
    c[4, 5:11] += 16
    c[11, 5:11] -= 18
    save(dither(c), os.path.join(BLOCK, "camera.png"))

    # crate: planks, corner posts, diagonal brace, stencil
    w = base(S, [124, 94, 58]) + (fbm(S, 308, octaves=2) - 0.5)[..., None] * 16
    for y in (4, 9, 14):
        w[y, :] *= 0.80                           # plank gaps
    rect(w, 0, 0, 2, 15, [92, 64, 36])
    rect(w, 13, 0, 15, 15, [92, 64, 36])
    rect(w, 0, 0, 15, 1, [92, 64, 36])
    rect(w, 0, 14, 15, 15, [92, 64, 36])
    for i in range(3, 13):
        w[i, i] = [100, 70, 40]
        w[i, 15 - i] = [100, 70, 40]
    w[0:2, :] += 12
    w[:, 0:2] += 8
    save(dither(w), os.path.join(BLOCK, "wood_crate.png"))

    # debris: rubble heap - concrete chunks, splinters, rebar
    d = base(S, [72, 68, 64]) + (fbm(S, 309, octaves=4, base=6) - 0.5)[..., None] * 34
    d += grain(S, 3091, 16)
    r = np.random.default_rng(310).random((S, S))
    d[r > 0.80] = [128, 124, 118]
    d[r < 0.16] = [46, 44, 42]
    d[6, 3:9] = [110, 70, 40]                     # splinter
    d[10, 8:13] = [96, 60, 34]
    d[4, 10:14] = [120, 96, 60]                   # rebar glint
    d[3, 10:14] = [80, 62, 40]
    save(dither(d), os.path.join(BLOCK, "debris_pile.png"))

    # machinery: dark steel cabinet, vents, two gauges, conduit, warning stripe
    m = base(S, [60, 60, 64]) + (fbm(S, 311, octaves=2) - 0.5)[..., None] * 14
    for y in (3, 4, 5):
        m[y, 2:8] = [40, 40, 44]                  # vent slots
        m[y, 2:8:2] = [86, 86, 92]
    rect(m, 10, 2, 13, 5, [150, 142, 96])         # gauge
    rect(m, 11, 3, 12, 4, [206, 60, 48])
    rect(m, 10, 8, 13, 11, [150, 142, 96])
    rect(m, 11, 9, 12, 10, [70, 70, 74])
    m[13, :] = [196, 168, 40]                     # hazard stripe
    m[13, 2:16:4] = [40, 40, 40]
    m[7, 0:16] = [44, 44, 48]                     # conduit
    m[8, 0:16] = [86, 86, 92]
    m[0, :] += 12
    m[:, 0] += 8
    m[15, :] -= 16
    save(dither(m), os.path.join(BLOCK, "machinery.png"))

    # puddle: murky water with a bright sky glint and dark edges
    p = base(S, [52, 62, 46]) + (fbm(S, 312, octaves=3) - 0.5)[..., None] * 20
    p[4:6, 3:9] = [108, 128, 116]                 # glint
    p[4, 4:8] = [150, 176, 160]
    p[11:13, 9:14] = [86, 104, 92]
    p[0, :] *= 0.7
    p[15, :] *= 0.7
    p[:, 0] *= 0.7
    p[:, 15] *= 0.7
    save(dither(p), os.path.join(BLOCK, "puddle.png"))


# ------------------------------------------------------- structural palette ---


def write_level_blocks():
    # concrete: form-work lines, tie holes, aggregate
    c = base(S, [120, 120, 123]) + (fbm(S, 400, octaves=4, base=4) - 0.5)[..., None] * 26
    c[7, :] *= 0.88                               # pour line
    c[8, :] *= 1.05
    for px, py in ((3, 3), (12, 11)):
        c[py, px] = [86, 86, 90]                  # tie hole
        c[py, px + 1 if px + 1 < S else px] = [100, 100, 104]
    sp = np.random.default_rng(401).random((S, S))
    c[sp > 0.90] += 22
    c[sp < 0.08] -= 20
    save(dither(c), os.path.join(BLOCK, "concrete.png"))

    # utility brick: offset courses, mortar, soot at the bottom
    b = base(S, [78, 54, 46]) + (fbm(S, 402, octaves=3) - 0.5)[..., None] * 22
    ys, xs = np.mgrid[0:S, 0:S]
    row = ys // 4
    off = np.where(row % 2, 4, 0)
    mortar = (ys % 4 == 3) | ((xs + off) % 8 == 7)
    b[mortar] = [42, 38, 35]
    b[(ys % 4 == 0)] += 16                        # lit top of each course
    soot = np.linspace(1.0, 0.72, S)[:, None]
    b *= soot[..., None]
    save(dither(b), os.path.join(BLOCK, "brick.png"))

    # steel plate: seam cross, rivets, brushed streaks
    m = base(S, [94, 94, 98]) + (fbm(S, 403, octaves=2) - 0.5)[..., None] * 12
    m += grain(S, 404, 8)
    m[7, :] = [64, 64, 68]
    m[8, :] = [126, 126, 132]
    m[:, 7] = [64, 64, 68]
    m[:, 8] = [126, 126, 132]
    for rx in (3, 12):
        for ry in (3, 12):
            m[ry, rx] = [150, 150, 156]
            m[ry + 1, rx] = [70, 70, 74]
            m[ry, rx + 1] = [70, 70, 74]
    save(dither(m), os.path.join(BLOCK, "metal.png"))

    # pipe wall: two fat insulated pipes with brackets, plus a thin copper line
    pw = base(S, [70, 70, 73]) + (fbm(S, 405, octaves=2) - 0.5)[..., None] * 12
    for py, col in ((4, [152, 146, 138]), (10, [120, 122, 128])):
        pw[py - 1, :] = np.array(col) * 1.18
        pw[py, :] = col
        pw[py + 1, :] = np.array(col) * 0.55
    for bx in (2, 9):
        pw[3:6, bx] = [58, 58, 62]                # brackets
        pw[9:12, bx] = [58, 58, 62]
    pw[14, :] = [146, 96, 60]                     # copper line
    pw[15, :] = [92, 58, 36]
    save(dither(pw), os.path.join(BLOCK, "pipe_wall.png"))

    # metal bars: vertical bars with lit left edge and rust feet
    mb = base(S, [22, 22, 26])
    for x in (1, 5, 9, 13):
        mb[:, x] = [128, 122, 114]
        mb[:, x + 1] = [74, 70, 66]
    mb[6, :] = [96, 92, 86]                       # mid rail
    mb[7, :] = [52, 50, 48]
    mb[14:, 1:15:4] *= 1.5                        # rust at the feet
    mb[14:, 1:15:4, 1] *= 0.6
    mb[14:, 1:15:4, 2] *= 0.5
    save(dither(mb, 1.0), os.path.join(BLOCK, "metal_bars.png"))


def write_levels34():
    ow = base(S, [216, 216, 212]) + (fbm(S, 500, octaves=2) - 0.5)[..., None] * 8
    ow[0, :] = [188, 188, 186]                    # panel seam
    ow[1, :] = [232, 232, 230]
    ow[8, :] = [192, 192, 190]
    ow[9, :] = [230, 230, 228]
    sc = np.random.default_rng(501).random((S, S))
    ow[sc > 0.94] -= 26                           # scuff marks
    save(dither(ow, 1.2), os.path.join(BLOCK, "office_wall.png"))

    oc = base(S, [92, 100, 118]) + (fbm(S, 502, octaves=4, base=7) - 0.5)[..., None] * 16
    oc += grain(S, 5021, 18)
    sp = np.random.default_rng(503).random((S, S))
    oc[sp > 0.88] *= 0.78
    oc[sp < 0.10] *= 1.14
    save(dither(oc), os.path.join(BLOCK, "office_carpet.png"))

    win = base(S, [12, 14, 20])
    win += (fbm(S, 504, octaves=2) - 0.5)[..., None] * 6
    win[3, 2:7] = [40, 46, 60]                    # faint reflection
    win[4, 3:6] = [30, 36, 48]
    win[0, :] = win[15, :] = [78, 80, 86]         # frame
    win[:, 0] = win[:, 15] = [78, 80, 86]
    win[:, 7:9] = [70, 72, 78]                    # mullion
    win[0, :] += 12
    win[:, 0] += 8
    save(dither(win, 1.0), os.path.join(BLOCK, "office_window.png"))

    wc = base(S, [214, 216, 218]) + (fbm(S, 505, octaves=2) - 0.5)[..., None] * 8
    rect(wc, 4, 1, 11, 6, [116, 168, 212])        # bottle
    rect(wc, 5, 2, 6, 3, [176, 214, 240])
    rect(wc, 4, 8, 11, 9, [40, 42, 46])           # tap recess
    rect(wc, 6, 8, 7, 9, [150, 152, 156])
    rect(wc, 4, 12, 11, 14, [178, 180, 184])      # cabinet door
    wc[12, 4:12] += 12
    wc[0, :] += 10
    wc[:, 0] += 8
    wc[15, :] -= 18
    save(dither(wc), os.path.join(BLOCK, "water_cooler.png"))

    ed = base(S, [152, 152, 158]) + (fbm(S, 506, octaves=2) - 0.5)[..., None] * 10
    ed[0, :] = ed[15, :] = [104, 104, 110]
    ed[:, 0] = ed[:, 15] = [104, 104, 110]
    ed[1, 1:15] += 14
    rect(ed, 2, 2, 13, 13, [166, 166, 172])       # recessed panel
    ed[2, 2:14] += 10
    ed[13, 2:14] -= 16
    rect(ed, 11, 7, 13, 8, [206, 200, 120])       # push bar
    rect(ed, 3, 3, 6, 5, [120, 140, 120])         # exit sign sticker
    save(dither(ed), os.path.join(BLOCK, "exit_door.png"))

    el = base(S, [170, 170, 176]) + (fbm(S, 507, octaves=2) - 0.5)[..., None] * 8
    el[:, 7] = [92, 92, 98]                       # door seam
    el[:, 8] = [120, 120, 126]
    el[:, 0:2] = [128, 128, 134]                  # jambs
    el[:, 14:16] = [128, 128, 134]
    rect(el, 1, 5, 2, 9, [206, 184, 84])          # call panel
    el[6, 1:3] = [240, 220, 120]
    el[0, :] += 12
    el[15, :] -= 18
    save(dither(el), os.path.join(BLOCK, "elevator.png"))


def write_levels56():
    hc = base(S, [124, 32, 38]) + (fbm(S, 600, octaves=4, base=7) - 0.5)[..., None] * 14
    hc += grain(S, 6001, 16)
    for y in range(S):                            # worn runner pattern
        if y % 8 in (0, 1):
            hc[y, :] *= 0.70
        if y % 8 == 4:
            hc[y, :] *= 1.22
    hc[:, 0] *= 0.9
    hc[:, 8] *= 0.9
    sp = np.random.default_rng(601).random((S, S))
    hc[sp > 0.90] *= 0.72
    save(dither(hc), os.path.join(BLOCK, "hotel_carpet.png"))

    hw = base(S, [200, 180, 144]) + (fbm(S, 602, octaves=2) - 0.5)[..., None] * 10
    for x in range(S):                            # damask-ish verticals
        if x % 8 in (0, 1):
            hw[x, :] *= 0.90
        if x % 8 == 4:
            hw[x, :] *= 1.06
    hw[0, :] = [168, 146, 112]                    # picture-rail shadow
    hw[1, :] = [222, 204, 168]
    hw[15, :] = [150, 128, 96]                    # skirting shadow
    g = fbm(S, 603, octaves=2)
    hw *= (1.0 - 0.14 * g)[..., None]
    save(dither(hw), os.path.join(BLOCK, "hotel_wall.png"))


def write_exits():
    base_w = base(S, [206, 182, 96])
    frames = []
    for k, mul in enumerate([1.0, 0.72, 0.22, 1.14]):
        f = base_w * mul + (fbm(S, 700 + k, octaves=2) - 0.5)[..., None] * 18
        for x in range(S):
            if x % 4 in (0, 1):
                f[x, :] *= 0.92
        if k in (2, 3):
            f[5:7, :] *= 0.40                     # tear band
            f[6, :] *= 0.5
        if k == 3:
            f[10:11, :] *= 0.7
        frames.append(f)
    save(dither(np.vstack(frames), 1.4), os.path.join(BLOCK, "flicker_wall.png"))

    d = base(S, [48, 48, 52]) + (fbm(S, 705, octaves=3) - 0.5)[..., None] * 14
    ys, xs = np.mgrid[0:S, 0:S]
    cx = (xs - 7.5) / 7.5
    cy = (ys - 7.5) / 7.5
    r = np.clip(np.sqrt(cx ** 2 + cy ** 2), 0, 1)
    d *= (0.18 + 0.82 * r)[..., None]
    d[6:10, 6:10] *= 0.35                         # the dark it leads to
    save(dither(d, 1.4), os.path.join(BLOCK, "deep_exit.png"))


# ----------------------------------------------------------------- entities ---


def _canvas(w, h):
    return np.zeros((h, w, 4))


def write_listener():
    """Tall wrong-proportioned figure: too-long arms, no face, two pinpricks."""
    W, H = 64, 128
    img = _canvas(W, H)
    cx = W // 2
    # head: small, slightly tilted
    for y in range(6, 26):
        w = int(7 * np.sin(np.pi * min(1.0, (y - 6) / 19.0)) + 2)
        img[y, cx - w:cx + w] = [8, 8, 10, 255]
    img[14, cx - 4:cx - 2] = [210, 220, 235, 255]   # pinprick eyes
    img[14, cx + 2:cx + 4] = [210, 220, 235, 255]
    # shoulders + torso, narrowing
    for y in range(26, 78):
        t = (y - 26) / 52.0
        w = int(15 - 5 * t)
        img[y, cx - w:cx + w] = [9, 9, 11, 255]
    # arms: hang past the knees, slightly out
    for y in range(30, 96):
        t = (y - 30) / 66.0
        ax = int(15 + 4 * t)
        img[y, cx - ax - 3:cx - ax] = [8, 8, 10, 255]
        img[y, cx + ax:cx + ax + 3] = [8, 8, 10, 255]
    # legs
    for y in range(78, 126):
        t = (y - 78) / 48.0
        w = int(6 - 2 * t)
        img[y, cx - 8 - w:cx - 8 + w] = [9, 9, 11, 255]
        img[y, cx + 8 - w:cx + 8 + w] = [9, 9, 11, 255]
    # rim light on the left edge so it is not a flat hole in the image
    alpha = img[..., 3] > 0
    edge = alpha & ~np.roll(alpha, 1, axis=1)
    img[edge, 0] += 26
    img[edge, 1] += 26
    img[edge, 2] += 30
    save(img, os.path.join(ENTITY, "listener.png"))


def write_glimpse():
    W, H = 32, 64
    img = _canvas(W, H)
    cx = W // 2
    for y in range(4, 16):
        w = int(4 * np.sin(np.pi * (y - 4) / 12.0) + 1)
        img[y, cx - w:cx + w] = [10, 10, 12, 210]
    for y in range(16, 44):
        t = (y - 16) / 28.0
        w = int(8 - 3 * t)
        img[y, cx - w:cx + w] = [10, 10, 12, int(210 - 60 * t)]
    for y in range(44, 62):
        t = (y - 44) / 18.0
        w = int(4 - 3 * t)
        img[y, cx - 3 - w:cx - 3 + w] = [10, 10, 12, int(150 - 120 * t)]
        img[y, cx + 3 - w:cx + 3 + w] = [10, 10, 12, int(150 - 120 * t)]
    save(img, os.path.join(ENTITY, "glimpse.png"))


def write_smiler():
    """A grin with too many teeth, hanging in the dark."""
    W, H = 64, 64
    img = np.zeros((H, W, 3))
    # eyes: wide, uneven
    for ex, ew in ((18, 6), (40, 7)):
        img[16:22, ex:ex + ew] = [240, 240, 214]
        img[17:21, ex + 1:ex + ew - 1] = [255, 255, 236]
    # crescent grin with teeth
    for x in range(10, 54):
        t = (x - 10) / 43.0
        y = int(34 + 10 * np.sin(np.pi * t))
        img[y:y + 3, x] = [238, 238, 212]
        if x % 4 in (1, 2):
            img[y + 3:y + 6, x] = [214, 214, 190]   # teeth
    img[34 + 10:34 + 12, 30:34] = [238, 238, 212]
    save(img, os.path.join(ENTITY, "smiler.png"))


def write_hound():
    """Low, hairless, humanoid-dog silhouette with pale eyes."""
    W, H = 64, 48
    img = np.zeros((H, W, 3))
    body = [28, 26, 28]
    img[18:30, 12:48] = body                      # torso
    img[14:22, 44:58] = body                      # head
    img[10:15, 46:50] = body                      # ear
    img[10:15, 54:58] = body
    img[16, 48:50] = [196, 60, 56]                # eyes
    img[16, 54:56] = [196, 60, 56]
    img[22:24, 52:58] = [40, 36, 38]              # jaw
    for lx in (14, 22, 36, 44):                   # legs
        img[30:44, lx:lx + 4] = body
    img[16:22, 4:12] = body                       # tail
    img[14:17, 2:6] = body
    img[18, 12:48] = [44, 42, 44]                 # spine highlight
    save(img, os.path.join(ENTITY, "hound.png"))


def write_skin_stealer():
    """UV contract: head in x[0.25,0.75] y[0,0.35]; body in y[0.35,1]."""
    W, H = 64, 128
    img = np.zeros((H, W, 3))
    n = fbm(H, 800, octaves=2)
    skin = np.array([226, 208, 186], float)
    # head band (y 0..44)
    img[0:45, :] = skin + (n[0:45, :W] - 0.5)[..., None] * 10
    img[0:45, :16] = [0, 0, 0]
    img[0:45, 48:] = [0, 0, 0]
    img[14:20, 22:28] = [46, 34, 32]              # eyes, slightly too far apart
    img[14:20, 36:42] = [46, 34, 32]
    img[15:17, 23:27] = [16, 12, 12]
    img[15:17, 37:41] = [16, 12, 12]
    img[30:34, 26:38] = [110, 62, 60]             # mouth, a little too wide
    img[31:33, 28:36] = [60, 30, 30]
    # body (y 45..128): borrowed clothes, pale shirt
    shirt = np.array([196, 192, 182], float)
    img[45:128, :] = shirt + (n[45:128, :W] - 0.5)[..., None] * 12
    img[45:128, :10] *= 0.86                      # arm shading
    img[45:128, 54:] *= 0.86
    img[45:50, :] *= 0.92                         # collar shadow
    img[70:74, :] *= 0.94                         # belt line
    img[45:128, 30:34] *= 0.96                    # button placket
    for y in range(52, 120, 12):
        img[y, 31:33] = [120, 116, 108]
    save(dither(img, 1.2), os.path.join(ENTITY, "skin_stealer.png"))

    h = np.zeros((H, W, 3))
    m = fbm(H, 802, octaves=3)
    flesh = np.array([64, 24, 24], float)
    h[0:45, :] = flesh + (m[0:45, :W] - 0.5)[..., None] * 14
    h[0:45, :16] = [0, 0, 0]
    h[0:45, 48:] = [0, 0, 0]
    h[12:22, 21:29] = [228, 36, 36]               # eyes
    h[12:22, 35:43] = [228, 36, 36]
    h[14:19, 23:27] = [255, 120, 110]
    h[14:19, 37:41] = [255, 120, 110]
    h[28:42, 24:40] = [150, 18, 18]               # gaping mouth
    h[30:40, 26:38] = [70, 6, 6]
    for x in range(25, 39, 3):                    # teeth
        h[28:31, x] = [220, 200, 190]
    body = np.array([40, 16, 16], float)
    h[45:128, :] = body + (m[45:128, :W] - 0.5)[..., None] * 12
    h[45:128, 26:38] *= 1.25                      # raw chest
    save(dither(h, 1.2), os.path.join(ENTITY, "skin_stealer_hunt.png"))


# --------------------------------------------------------------------- main ---


def main():
    write_props()
    write_listener()
    for i in range(4):
        write_wallpaper(i)
        write_carpet(i)
        write_ceiling(i)
    write_light()
    write_void()
    write_glimpse()
    write_level_blocks()
    write_smiler()
    write_hound()
    write_exits()
    write_levels34()
    write_skin_stealer()
    write_levels56()
    count = 0
    for root, _, files in os.walk(OUT):
        count += sum(1 for f in files if f.endswith(".png"))
    print(f"wrote {count} textures under {OUT}")


if __name__ == "__main__":
    main()
