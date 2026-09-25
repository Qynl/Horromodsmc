#!/usr/bin/env python3
"""Generates every Hollow texture as a PNG using a tiny built-in PNG encoder.

No external dependencies — run with plain Python 3:
    python3 tools/gen_textures.py

Texture layout notes (watcher.png / apparition.png, 64x64):
  head  8x8x8   uv (0, 0)   — classic cross layout, face at (8, 8)-(16, 16)
  torso 7x14x3  uv (0, 16)  — box spans (0, 16)-(20, 33)
  legs  2x20x2  uv (20, 16) and (28, 16) — boxes span 8x22 each
  arms  2x18x2  uv (36, 16) and (44, 16) — boxes span 8x20 each
"""

import os
import random
import struct
import zlib

ASSETS = os.path.join(
    os.path.dirname(os.path.abspath(__file__)),
    "..", "src", "main", "resources", "assets", "hollow",
)


# ---------------------------------------------------------------------------
# Minimal PNG writer
# ---------------------------------------------------------------------------
def save_png(path, width, height, rows):
    def chunk(tag, data):
        block = tag + data
        return struct.pack(">I", len(data)) + block + struct.pack(">I", zlib.crc32(block) & 0xFFFFFFFF)

    def pixel_bytes(px):
        if len(px) == 3:
            return struct.pack("4B", px[0], px[1], px[2], 255)
        return struct.pack("4B", *px)

    raw = b"".join(b"\x00" + b"".join(pixel_bytes(px) for px in row) for row in rows)
    png = (
        b"\x89PNG\r\n\x1a\n"
        + chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0))
        + chunk(b"IDAT", zlib.compress(raw, 9))
        + chunk(b"IEND", b"")
    )
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as handle:
        handle.write(png)


class Canvas:
    def __init__(self, width, height):
        self.width = width
        self.height = height
        self.pixels = [[(0, 0, 0, 0) for _ in range(width)] for _ in range(height)]

    def set(self, x, y, color):
        if 0 <= x < self.width and 0 <= y < self.height:
            self.pixels[y][x] = tuple(color)

    def get(self, x, y):
        return self.pixels[y][x]

    def rect(self, x0, y0, x1, y1, color):
        """Fill inclusive rect (x0, y0)-(x1, y1)."""
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                self.set(x, y, color)

    def outline(self, x0, y0, x1, y1, color):
        for x in range(x0, x1 + 1):
            self.set(x, y0, color)
            self.set(x, y1, color)
        for y in range(y0, y1 + 1):
            self.set(x0, y, color)
            self.set(x1, y, color)

    def save(self, path):
        save_png(path, self.width, self.height, self.pixels)


def clamp_channel(value):
    return max(0, min(255, int(value)))


def smoke(canvas, x0, y0, x1, y1, base, variance, rng, alpha=255):
    """Noisy dark 'smoke' fill with faint vertical streaks."""
    column_offsets = [rng.randint(-variance, variance) // 2 for _ in range(canvas.width)]
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            value = rng.randint(-variance, variance) + column_offsets[x]
            if rng.random() < 0.04:
                value += 14
            color = tuple(clamp_channel(c + value) for c in base)
            canvas.set(x, y, (color[0], color[1], color[2], alpha))


# ---------------------------------------------------------------------------
# Individual textures
# ---------------------------------------------------------------------------
def shadow_fragment():
    rng = random.Random(1)
    canvas = Canvas(16, 16)
    rows = {
        1: (7, 8), 2: (6, 8), 3: (6, 9), 4: (5, 9), 5: (5, 10), 6: (5, 10),
        7: (6, 11), 8: (6, 11), 9: (7, 12), 10: (7, 12), 11: (8, 13),
        12: (8, 13), 13: (9, 14), 14: (10, 14),
    }
    mask = set()
    for y, (x0, x1) in rows.items():
        for x in range(x0, x1 + 1):
            mask.add((x, y))

    for (x, y) in sorted(mask):
        base = 18 + rng.randint(-6, 8)
        canvas.set(x, y, (base, base // 2 + 4, base + 14))

    # Purple veins running along the shard.
    for (x, y) in sorted(mask):
        if (x + y) % 4 == 0:
            canvas.set(x, y, (118, 68, 178))
        if (x + y) % 7 == 2 and rng.random() < 0.7:
            canvas.set(x, y, (168, 108, 232))

    # Dark outline around the shard.
    for (x, y) in sorted(mask):
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            if (x + dx, y + dy) not in mask:
                canvas.set(x, y, (6, 4, 10))
                break

    canvas.save(os.path.join(ASSETS, "textures", "item", "shadow_fragment.png"))


def warding_totem():
    canvas = Canvas(16, 16)
    bone = (216, 208, 190)
    bone_dark = (172, 162, 142)
    outline_color = (58, 52, 44)

    # Head and body.
    canvas.rect(5, 2, 10, 7, bone)
    canvas.rect(6, 8, 9, 14, bone)
    canvas.outline(5, 2, 10, 7, outline_color)
    canvas.outline(6, 8, 9, 14, outline_color)

    # Shading on the right side.
    for y in range(3, 7):
        canvas.set(10, y, bone_dark)
    for y in range(9, 14):
        canvas.set(9, y, bone_dark)

    # Face: two knowing eyes and a stitched mouth.
    canvas.set(6, 4, (124, 66, 198))
    canvas.set(9, 4, (124, 66, 198))
    canvas.set(7, 6, (96, 88, 74))
    canvas.set(8, 6, (96, 88, 74))

    # Belt and glowing rune.
    canvas.rect(6, 9, 9, 9, (148, 118, 58))
    canvas.set(7, 11, (140, 92, 220))
    canvas.set(8, 11, (140, 92, 220))
    canvas.set(7, 12, (88, 56, 148))
    canvas.set(8, 12, (88, 56, 148))

    canvas.save(os.path.join(ASSETS, "textures", "item", "warding_totem.png"))


def hallowed_lantern():
    rng = random.Random(7)
    canvas = Canvas(16, 16)

    for y in range(16):
        for x in range(16):
            distance = max(abs(x - 7.5), abs(y - 7.5)) / 7.5
            if distance < 0.35:
                color = (255, 236, 152)
            elif distance < 0.7:
                color = (250, 196, 92)
            else:
                color = (196, 128, 52)
            jitter = rng.randint(-8, 8)
            canvas.set(x, y, tuple(clamp_channel(c + jitter) for c in color))

    # Iron frame: border plus two bars.
    frame = (38, 30, 48)
    frame_light = (84, 66, 92)
    canvas.outline(0, 0, 15, 15, frame)
    canvas.outline(1, 1, 14, 14, frame_light)
    for y in range(2, 14):
        canvas.set(5, y, frame)
        canvas.set(10, y, frame)
    for x in range(2, 14):
        canvas.set(x, 1, frame_light)
        canvas.set(x, 14, frame_light)
    # Rivets.
    for (x, y) in ((1, 1), (14, 1), (1, 14), (14, 14)):
        canvas.set(x, y, (150, 120, 60))

    canvas.save(os.path.join(ASSETS, "textures", "block", "hallowed_lantern.png"))


def _watcher_layout_base():
    """Regions that receive the body texture, matching the model's UVs."""
    return [
        (0, 0, 31, 15),    # head cross
        (0, 16, 19, 32),   # torso
        (20, 16, 27, 37),  # left leg
        (28, 16, 35, 37),  # right leg
        (36, 16, 43, 35),  # left arm
        (44, 16, 51, 35),  # right arm
    ]


def watcher():
    rng = random.Random(3)
    canvas = Canvas(64, 64)
    for (x0, y0, x1, y1) in _watcher_layout_base():
        smoke(canvas, x0, y0, x1, y1, (13, 10, 18), 6, rng)

    # Face plate, slightly lighter.
    for y in range(8, 16):
        for x in range(8, 16):
            value = rng.randint(-4, 6)
            canvas.set(x, y, tuple(clamp_channel(c + value) for c in (22, 18, 30)))

    # Pale, knowing eyes with a violet halo.
    halo = [(8, 11), (11, 11), (12, 11), (15, 11),
            (9, 10), (10, 10), (13, 10), (14, 10),
            (9, 12), (10, 12), (13, 12), (14, 12)]
    for (x, y) in halo:
        canvas.set(x, y, (110, 75, 175))
    for (x, y) in ((9, 11), (10, 11), (13, 11), (14, 11)):
        canvas.set(x, y, (228, 208, 255))

    canvas.save(os.path.join(ASSETS, "textures", "entity", "watcher.png"))


def apparition():
    rng = random.Random(11)
    canvas = Canvas(64, 64)
    for (x0, y0, x1, y1) in _watcher_layout_base():
        smoke(canvas, x0, y0, x1, y1, (86, 88, 104), 10, rng, alpha=140)

    # Hollow eye sockets.
    for (x, y) in ((9, 11), (10, 11), (13, 11), (14, 11)):
        canvas.set(x, y, (16, 14, 24, 200))

    canvas.save(os.path.join(ASSETS, "textures", "entity", "apparition.png"))


def watcher_pale():
    """Rare variant: bone-pale body, black eye sockets ringed in dried red."""
    rng = random.Random(23)
    canvas = Canvas(64, 64)
    for (x0, y0, x1, y1) in _watcher_layout_base():
        smoke(canvas, x0, y0, x1, y1, (158, 156, 168), 10, rng)

    for y in range(8, 16):
        for x in range(8, 16):
            value = rng.randint(-5, 7)
            canvas.set(x, y, tuple(clamp_channel(c + value) for c in (178, 176, 188)))

    halo = [(8, 11), (11, 11), (12, 11), (15, 11),
            (9, 10), (10, 10), (13, 10), (14, 10),
            (9, 12), (10, 12), (13, 12), (14, 12)]
    for (x, y) in halo:
        canvas.set(x, y, (122, 28, 38))
    for (x, y) in ((9, 11), (10, 11), (13, 11), (14, 11)):
        canvas.set(x, y, (14, 10, 14))

    canvas.save(os.path.join(ASSETS, "textures", "entity", "watcher_pale.png"))


def third_eye():
    rng = random.Random(31)
    canvas = Canvas(16, 16)
    rows = {5: (6, 9), 6: (4, 11), 7: (3, 12), 8: (3, 12), 9: (4, 11), 10: (6, 9)}
    mask = set()
    for y, (x0, x1) in rows.items():
        for x in range(x0, x1 + 1):
            mask.add((x, y))

    for (x, y) in sorted(mask):
        jitter = rng.randint(-6, 6)
        canvas.set(x, y, tuple(clamp_channel(c + jitter) for c in (232, 228, 218)))

    # Iris and slit pupil.
    for (x, y) in sorted(mask):
        if (x - 7.5) ** 2 + (y - 7.5) ** 2 <= 6.5:
            canvas.set(x, y, (150, 95, 225))
    for y in range(6, 10):
        for x in (7, 8):
            if (x, y) in mask:
                canvas.set(x, y, (15, 10, 20))

    # Gilded lids and outline.
    for (x, y) in sorted(mask):
        if y in (5, 10) or (x, y) in ((4, 6), (11, 6), (4, 9), (11, 9)):
            canvas.set(x, y, (160, 128, 64))
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            if (x + dx, y + dy) not in mask:
                canvas.set(x, y, (40, 30, 50))
                break

    canvas.save(os.path.join(ASSETS, "textures", "item", "third_eye.png"))


def vignette():
    """256x256 radial dark-purple vignette; alpha grows toward the edges."""
    rng = random.Random(41)
    canvas = Canvas(256, 256)
    for y in range(256):
        for x in range(256):
            distance = ((x - 127.5) ** 2 + (y - 127.5) ** 2) ** 0.5 / 181.0
            t = max(0.0, min(1.0, (distance - 0.42) / 0.58))
            alpha = int(235 * (t ** 1.6)) + rng.randint(-4, 4)
            if alpha <= 0:
                continue
            canvas.set(x, y, (12, 4, 18, max(0, min(255, alpha))))
    canvas.save(os.path.join(ASSETS, "textures", "gui", "dread_vignette.png"))


def icon():
    rng = random.Random(13)
    canvas = Canvas(64, 64)

    # Night-sky gradient with grain.
    for y in range(64):
        t = y / 63.0
        base = (
            int(6 + 10 * t),
            int(5 + 6 * t),
            int(10 + 12 * t),
        )
        for x in range(64):
            jitter = rng.randint(-3, 3)
            canvas.set(x, y, tuple(clamp_channel(c + jitter) for c in base))

    # The silhouette, slightly narrower at the head.
    for y in range(12, 62):
        half = int(4 + (y - 12) * 0.13)
        for x in range(32 - half, 32 + half):
            canvas.set(x, y, (4, 3, 7))

    # Eyes.
    for (x, y) in ((27, 20), (28, 20), (35, 20), (36, 20)):
        canvas.set(x, y, (228, 208, 255))
    for (x, y) in ((26, 20), (29, 20), (34, 20), (37, 20),
                   (27, 19), (28, 19), (35, 19), (36, 19),
                   (27, 21), (28, 21), (35, 21), (36, 21)):
        canvas.set(x, y, (140, 95, 215))

    # Low mist.
    for _ in range(240):
        x = rng.randint(0, 63)
        y = rng.randint(50, 63)
        canvas.set(x, y, (90, 60, 140, rng.randint(40, 120)))

    canvas.save(os.path.join(ASSETS, "icon.png"))


def main():
    shadow_fragment()
    warding_totem()
    hallowed_lantern()
    watcher()
    watcher_pale()
    apparition()
    third_eye()
    vignette()
    icon()
    print("Textures written to", os.path.normpath(ASSETS))


if __name__ == "__main__":
    main()
