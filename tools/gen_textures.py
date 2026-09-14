#!/usr/bin/env python3
"""Procedurally generates every PNG the mod ships.

Run from the repo root:  python3 tools/gen_textures.py

All textures are 16x16 and deterministic (fixed RNG seed) so repeated runs produce byte-identical
files. The look targets "a real abandoned office that Minecraft accidentally kept": faded yellows,
grime, water damage, and fluorescent fixtures.
"""

import os

import numpy as np
from PIL import Image

OUT = os.path.join("src", "main", "resources", "assets", "backrooms", "textures")
BLOCK = os.path.join(OUT, "block")
ENTITY = os.path.join(OUT, "entity")
S = 16
rng = np.random.default_rng(1234)


def save(arr, path):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    arr = np.clip(arr, 0, 255).astype(np.uint8)
    if arr.shape[-1] == 3:
        img = Image.fromarray(arr, "RGB")
    else:
        img = Image.fromarray(arr, "RGBA")
    img.save(path)


def noise(scale=1.0):
    return rng.random((S, S)) * scale


def grime(strength):
    """Large soft mottled dark patches."""
    base = rng.random((4, 4))
    big = np.kron(base, np.ones((4, 4)))[:S, :S]
    return big * strength


def write_wallpaper(idx):
    base = np.array([201, 178, 88], float)
    img = np.tile(base, (S, S, 1))
    img += (noise() - 0.5)[:, :, None] * 14
    # vertical stripes
    for x in range(S):
        if x % 4 in (0, 1):
            img[x, :] *= 0.93
    g = grime(0.10 + 0.10 * idx)
    img *= (1.0 - g)[:, :, None]
    if idx >= 2:  # damp streaks
        img[:, 4:7] *= 0.86
    if idx == 3:  # mouldy
        m = (noise() > 0.75)
        img[m, 0] *= 0.6
        img[m, 1] *= 0.85
        img[m, 2] *= 0.6
    save(img, os.path.join(BLOCK, f"wallpaper_{idx}.png"))


def write_carpet(idx):
    base = np.array([196, 168, 84], float)
    img = np.tile(base, (S, S, 1))
    img += (noise() - 0.5)[:, :, None] * 40   # fibre
    img -= (noise() > 0.8)[:, :, None] * 30   # dark specks
    g = grime(0.08 + 0.10 * idx)
    img *= (1.0 - g)[:, :, None]
    if idx == 3:  # damp / wrong colour
        img[:, :, 0] *= 0.72
        img[:, :, 1] *= 0.82
        img[:, :, 2] *= 1.05
    save(img, os.path.join(BLOCK, f"carpet_{idx}.png"))


def write_ceiling(idx):
    base = np.array([222, 214, 180], float)
    img = np.tile(base, (S, S, 1))
    img += (noise() - 0.5)[:, :, None] * 10
    for x in range(S):
        for z in range(S):
            if x % 4 == 0 or z % 4 == 0:
                img[x, z] *= 0.80            # perforation grid
    g = grime(0.10 + 0.14 * idx)
    img *= (1.0 - g)[:, :, None]
    if idx >= 2:
        img[8:12, :] *= 0.80                 # water ring
    save(img, os.path.join(BLOCK, f"ceiling_tile_{idx}.png"))


def write_light():
    on = np.zeros((S, S, 3))
    off = np.zeros((S, S, 3))
    for x in range(S):
        for z in range(S):
            border = x in (0, S - 1) or z in (0, S - 1)
            if border:
                on[x, z] = [110, 110, 110]
                off[x, z] = [110, 110, 110]
            else:
                tube = (z % 8) < 5
                on[x, z] = [245, 245, 235] if tube else [200, 200, 195]
                off[x, z] = [95, 95, 92]
    save(on, os.path.join(BLOCK, "fluorescent_light_on.png"))
    save(off, os.path.join(BLOCK, "fluorescent_light_off.png"))


def write_void():
    img = np.zeros((S, S, 3))
    for z in range(S):
        t = z / (S - 1)
        img[:, z] = [245 - 45 * t, 248 - 33 * t, 255 - 15 * t]
    save(img, os.path.join(BLOCK, "void.png"))


def write_glimpse():
    img = np.zeros((S, S, 4))
    for x in range(S):
        for z in range(S):
            cx = abs(x - 7.5)
            head = z < 5 and cx < 2.5
            torso = 5 <= z < 14 and cx < (4 - z * 0.15)
            if head or torso:
                img[x, z] = [10, 10, 12, 235]
    save(img, os.path.join(ENTITY, "glimpse.png"))


def write_props():
    # chair: dark worn fabric
    chair = np.tile(np.array([58, 56, 52], float), (S, S, 1))
    chair += (noise() - 0.5)[:, :, None] * 22
    save(chair, os.path.join(BLOCK, "chair.png"))
    # desk: pale laminate with scuffs
    desk = np.tile(np.array([168, 150, 108], float), (S, S, 1))
    desk += (noise() - 0.5)[:, :, None] * 16
    desk[::5, :] *= 0.88
    save(desk, os.path.join(BLOCK, "desk.png"))
    # barrel: dull metal with rust
    barrel = np.tile(np.array([104, 106, 108], float), (S, S, 1))
    barrel += (noise() - 0.5)[:, :, None] * 18
    rust = (noise() > 0.8)
    barrel[rust, 0] *= 1.3; barrel[rust, 1] *= 0.7; barrel[rust, 2] *= 0.5
    save(barrel, os.path.join(BLOCK, "barrel.png"))
    # box: cardboard
    box = np.tile(np.array([150, 116, 74], float), (S, S, 1))
    box += (noise() - 0.5)[:, :, None] * 14
    box[:, 7:9] *= 0.8
    save(box, os.path.join(BLOCK, "box.png"))
    # vending: front with buttons + glow, side plain
    front = np.tile(np.array([150, 40, 46], float), (S, S, 1))
    front[2:12, 2:10] = [210, 220, 215]      # lit window
    front[2:12, 11:14] = [40, 40, 44]        # button panel
    front += (noise() - 0.5)[:, :, None] * 8
    save(front, os.path.join(BLOCK, "vending_front.png"))
    side = np.tile(np.array([140, 36, 42], float), (S, S, 1))
    side += (noise() - 0.5)[:, :, None] * 10
    save(side, os.path.join(BLOCK, "vending_side.png"))
    # camera: grey housing with red lens
    cam = np.tile(np.array([70, 72, 76], float), (S, S, 1))
    cam += (noise() - 0.5)[:, :, None] * 12
    cam[7:9, 7:9] = [180, 30, 30]
    save(cam, os.path.join(BLOCK, "camera.png"))


def write_listener():
    img = np.zeros((S, S, 4))
    for x in range(S):
        for z in range(S):
            cx = abs(x - 7.5)
            head = z < 4 and cx < 2
            torso = 4 <= z < 15 and cx < (3.5 - z * 0.12)
            if head or torso:
                img[x, z] = [8, 8, 10, 255]
    save(img, os.path.join(ENTITY, "listener.png"))


def write_level_blocks():
    """Level 1 / Level 2 palette: concrete, brick, steel, piping, crates, debris, machinery, water."""
    # concrete: cool grey with aggregate speckle
    c = np.tile(np.array([118, 118, 120], float), (S, S, 1)) + (noise() - 0.5)[:, :, None] * 26
    save(c, os.path.join(BLOCK, "concrete.png"))
    # brick: brown/black utility brick with offset courses
    b = np.tile(np.array([74, 52, 44], float), (S, S, 1)) + (noise() - 0.5)[:, :, None] * 18
    ys, xs = np.mgrid[0:S, 0:S]
    mortar = ((ys % 4) == 3) | (((xs + np.where((ys // 4) % 2, 4, 0)) % 8) == 7)
    b[mortar] = [58, 50, 46]
    save(b, os.path.join(BLOCK, "brick.png"))
    # metal: riveted steel plate
    m = np.tile(np.array([92, 92, 95], float), (S, S, 1)) + (noise() - 0.5)[:, :, None] * 16
    for rx in (2, 13):
        for ry in (2, 13):
            m[ry, rx] = [140, 140, 145]
    save(m, os.path.join(BLOCK, "metal.png"))
    # pipe_wall: steel with two horizontal pipes
    pw = np.tile(np.array([70, 70, 72], float), (S, S, 1)) + (noise() - 0.5)[:, :, None] * 12
    for py in (4, 11):
        pw[py - 1, :] = [150, 144, 138]
        pw[py, :] = [110, 104, 98]
        pw[py + 1, :] = [78, 72, 66]
    save(pw, os.path.join(BLOCK, "pipe_wall.png"))
    # wood_crate: planks with cross braces
    w = np.tile(np.array([120, 92, 56], float), (S, S, 1)) + (noise() - 0.5)[:, :, None] * 18
    w[0, :] = [90, 62, 36]; w[15, :] = [90, 62, 36]
    w[:, 0] = [90, 62, 36]; w[:, 15] = [90, 62, 36]
    for i in range(S):
        w[i, i] = [96, 66, 38]; w[i, 15 - i] = [96, 66, 38]
    save(w, os.path.join(BLOCK, "wood_crate.png"))
    # debris_pile: broken rubble and splinters
    d = np.tile(np.array([70, 66, 62], float), (S, S, 1)) + (noise() - 0.5)[:, :, None] * 30
    rn = noise()
    d[rn > 0.72] = [120, 92, 60]
    d[rn < 0.2] = [58, 56, 54]
    save(d, os.path.join(BLOCK, "debris_pile.png"))
    # machinery: dark steel with gauges and a conduit
    mc = np.tile(np.array([58, 58, 60], float), (S, S, 1)) + (noise() - 0.5)[:, :, None] * 14
    mc[8, 2:14] = [40, 40, 42]
    for cx, cy in ((4, 4), (11, 12)):
        mc[cy - 1:cy + 2, cx - 1:cx + 2] = [150, 140, 90]
        mc[cy, cx] = [200, 60, 50]
    save(mc, os.path.join(BLOCK, "machinery.png"))
    # puddle: murky stagnant water
    pu = np.tile(np.array([58, 66, 48], float), (S, S, 1)) + (noise() - 0.5)[:, :, None] * 18
    save(pu, os.path.join(BLOCK, "puddle.png"))


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
    count = 0
    for root, _, files in os.walk(OUT):
        count += sum(1 for f in files if f.endswith(".png"))
    print(f"wrote {count} textures under {OUT}")


if __name__ == "__main__":
    main()
