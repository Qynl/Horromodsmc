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
    count = 0
    for root, _, files in os.walk(OUT):
        count += sum(1 for f in files if f.endswith(".png"))
    print(f"wrote {count} textures under {OUT}")


if __name__ == "__main__":
    main()
