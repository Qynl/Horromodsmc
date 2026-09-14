#!/usr/bin/env python3
"""Design-time preview and validation for the Level 0 layout.

THIS IS A PORT, NOT THE SHIPPED CODE.

The generator the game actually runs is
``src/main/java/dev/qynl/backrooms/level0/Level0Layout.java``. This file is a deliberate
line-for-line port of it, down to emulating ``java.util.Random``'s 48-bit LCG and 64-bit
two's-complement hashing, so that the two produce identical districts for identical seeds.

Its job is to answer questions that need the algorithm to be *executed*, which cannot happen
in a sandbox with no Minecraft classpath:

  * does the shared-edge-hash trick really keep the infinite plane connected?
  * does every district open onto all four neighbours?
  * what is the open/wall ratio, and does it stay in a playable band?
  * how do style, light state and stain frequencies distribute, near vs deep?

Run ``python3 tools/layout_preview.py --selftest`` for the invariant checks and
``--map out.png`` for a rendered plan.

If you change Level0Layout.java, change this file too. The selftest will not catch drift
between them; keeping them in sync is a human obligation.
"""

from __future__ import annotations

import argparse
import math
import sys
from collections import Counter, deque

N = 16  # Level0Layout.DISTRICT_TILES

BIT_SOLID = 1
BIT_PILLAR = 2
BIT_DOOR = 4
SURFACE_SHIFT = 3
SURFACE_MASK = 3
LIGHT_SHIFT = 5
LIGHT_MASK = 3

SURFACE_CLEAN, SURFACE_STAINED, SURFACE_DAMP, SURFACE_MOULDY = 0, 1, 2, 3
LIGHT_NONE, LIGHT_WORKING, LIGHT_FLICKERING, LIGHT_DEAD = 0, 1, 2, 3

PROP_NONE, PROP_CHAIR, PROP_DESK, PROP_BARREL, PROP_BOX, PROP_VENDING = 0, 1, 2, 3, 4, 5
PROP_COUNT = 5
PROP_SALT = 0xA5A5A5A5C0FFEE01
CAMERA_SALT = 0xC4C4C4C4D10CA2

STYLES = ["GRID", "ORGANIC", "HALL", "DARK", "LONG", "HUB", "IMPOSSIBLE", "POOLROOM"]


# --------------------------------------------------------------------------
# java.util.Random, faithfully
# --------------------------------------------------------------------------
M64 = (1 << 64) - 1
MASK48 = (1 << 48) - 1
MULT = 0x5DEECE66D


class JavaRandom:
    def __init__(self, seed: int):
        self.seed = ((seed & M64) ^ MULT) & MASK48

    def _next(self, bits: int) -> int:
        self.seed = (self.seed * MULT + 0xB) & MASK48
        return self.seed >> (48 - bits)

    def next_int(self, bound=None) -> int:
        if bound is None:
            v = self._next(32)
            return v - (1 << 32) if v >= (1 << 31) else v
        if bound <= 0:
            raise ValueError("bound must be positive")
        if (bound & -bound) == bound:  # power of two
            return (bound * self._next(31)) >> 31
        while True:
            bits = self._next(31)
            val = bits % bound
            if bits - val + (bound - 1) < (1 << 31):
                return val

    def next_double(self) -> float:
        return ((self._next(26) << 27) + self._next(27)) / float(1 << 53)

    def next_boolean(self) -> bool:
        return self._next(1) != 0


def rotl64(x: int, n: int) -> int:
    x &= M64
    return ((x << n) | (x >> (64 - n))) & M64


def mix(a: int, b: int, c: int, d: int) -> int:
    h = 0x9E3779B97F4A7C15
    h = rotl64(((h ^ (a & M64)) * 0xBF58476D1CE4E5B9) & M64, 31)
    h = rotl64(((h ^ (b & M64)) * 0x94D049BB133111EB) & M64, 27)
    h = rotl64(((h ^ (c & M64)) * 0xBF58476D1CE4E5B9) & M64, 31)
    h = rotl64(((h ^ (d & M64)) * 0x94D049BB133111EB) & M64, 27)
    h ^= h >> 33
    h = (h * 0xFF51AFD7ED558CCD) & M64
    h ^= h >> 33
    h = (h * 0xC4CEB9FE1A85EC53) & M64
    h ^= h >> 33
    return h


def s32(x: int) -> int:
    """Packed (dx << 32) ^ (dz & 0xFFFFFFFF), kept as a 64-bit pattern like the Java long."""
    return ((((x & 0xFFFFFFFF) << 32) ^ (z & 0xFFFFFFFF)) if False else (((x & M64) << 32) ^ (z & 0xFFFFFFFF))) & M64


# --------------------------------------------------------------------------
# Level0Layout
# --------------------------------------------------------------------------
class Level0Layout:
    def __init__(self, seed: int, drift: int = 0, level: int = 0):
        self.seed = seed & M64
        self.drift = drift & M64
        self.level = level
        self.cache: dict[tuple[int, int], "DistrictPlan"] = {}

    def set_drift(self, drift: int) -> None:
        if drift != self.drift:
            self.drift = drift & M64
            self.cache.clear()

    # -- queries --------------------------------------------------------
    def plan(self, x: int, z: int) -> "DistrictPlan":
        dx, dz = x // N, z // N   # Python // floors, matching Math.floorDiv
        key = (dx, dz)
        p = self.cache.get(key)
        if p is None:
            p = self._build_plan(dx, dz)
            if len(self.cache) > 4096:
                self.cache.clear()
            self.cache[key] = p
        return p

    def tile_bits(self, x: int, z: int) -> int:
        p = self.plan(x, z)
        return p.bits[mod(x, N) * N + mod(z, N)] & 0xFF

    def is_solid(self, x: int, z: int) -> bool:
        return (self.tile_bits(x, z) & BIT_SOLID) != 0

    def is_pillar(self, x: int, z: int) -> bool:
        return (self.tile_bits(x, z) & BIT_PILLAR) != 0

    def style(self, x: int, z: int) -> int:
        return self.plan(x, z).style

    def surface(self, x: int, z: int) -> int:
        return (self.tile_bits(x, z) >> SURFACE_SHIFT) & SURFACE_MASK

    def light_state(self, x: int, z: int) -> int:
        return (self.tile_bits(x, z) >> LIGHT_SHIFT) & LIGHT_MASK

    def prop_at(self, x: int, z: int) -> int:
        b = self.tile_bits(x, z)
        if b & BIT_SOLID or b & BIT_PILLAR or b & BIT_DOOR:
            return PROP_NONE
        l = self.is_solid(x - 1, z); r = self.is_solid(x + 1, z)
        u = self.is_solid(x, z - 1); d = self.is_solid(x, z + 1)
        if (l and r) or (u and d):
            return PROP_NONE
        h = mix(self.seed, self.drift ^ PROP_SALT, pack(x, z), 0)
        roll = (h >> 17) % 1000
        base = 42 if self.level == 1 else (34 if self.level == 2 else (30 if self.level == 3 else (20 if self.level == 4 else (26 if self.level == 5 else (12 if self.level == 6 else 26)))))
        density = base // 2 if self.style(x, z) == 3 else base
        if roll >= density:
            return PROP_NONE
        return 1 + ((h >> 29) % PROP_COUNT)

    def camera_at(self, x: int, z: int) -> bool:
        b = self.tile_bits(x, z)
        if b & BIT_SOLID or b & BIT_PILLAR:
            return False
        h = mix(self.seed, self.drift ^ CAMERA_SALT, pack(x, z), 0)
        return ((h >> 19) % 1000) < 5

    def puddle_at(self, x: int, z: int) -> bool:
        if self.level != 1 and self.level != 3:
            return False
        b = self.tile_bits(x, z)
        if b & BIT_SOLID or b & BIT_PILLAR:
            return False
        h = mix(self.seed, self.drift ^ 0x9D1E0000AA11, pack(x, z), 0)
        return ((h >> 21) % 1000) < 40

    def reliability(self, x: int, z: int) -> float:
        p = self.plan(x, z)
        return self._reliability_for(x, z, p.district_x, p.district_z, p.style)

    def edge_hash(self, dx: int, dz: int, edge: int) -> int:
        ax, az, bx, bz = dx, dz, dx, dz
        if edge == 0:
            bz = dz - 1
        elif edge == 1:
            bz = dz + 1
        elif edge == 2:
            bx = dx - 1
        else:
            bx = dx + 1
        a = pack(ax, az)
        b = pack(bx, bz)
        if a <= b:
            lo, hi = a, b
        else:
            lo, hi = b, a
        return mix(self.seed, self.drift, lo, hi ^ 0x5DEECE66D)

    # -- construction ---------------------------------------------------
    def _reliability_for(self, x: int, z: int, dx: int, dz: int, style: int) -> float:
        dist = math.sqrt(x * x + z * z)
        base = 1.0 - dist / 9000.0
        h = mix(self.seed, self.drift, pack(dx, dz), 0)
        local = ((h >> 11) % 1000 / 1000.0 - 0.5) * 0.35
        if style == 3:  # DARK
            local -= 0.45
        if style == 5:  # HUB
            local += 0.2
        return max(0.0, min(1.0, base + local))

    def _build_plan(self, dx: int, dz: int) -> "DistrictPlan":
        base = mix(self.seed, self.drift, pack(dx, dz), 0)
        rng = JavaRandom(base)
        style = self._choose_style(rng, dx, dz)

        if self.level == 1:
            if style in (2, 5):
                ceiling_y = 2 + 5 + rng.next_int(2)
            elif style == 4:
                ceiling_y = 2 + 2
            else:
                ceiling_y = 2 + 3 + rng.next_int(2)
        elif self.level == 2:
            ceiling_y = 2 + 2 if style in (2, 5) else 3
        elif self.level == 3:
            ceiling_y = (2 + 4 + rng.next_int(2)) if style in (2, 5) else 3
        elif self.level == 4:
            ceiling_y = (2 + 3) if style in (2, 5) else (2 + 2)
        elif self.level == 5:
            ceiling_y = (2 + 4) if style in (2, 5) else (3 if style == 4 else (2 + 3))
        elif self.level == 6:
            ceiling_y = (2 + 2) if style in (2, 5) else 3
        else:
            if style in (2, 5):  # HALL, HUB
                ceiling_y = 2 + 4 + rng.next_int(2)
            elif style == 4:  # LONG
                ceiling_y = 3
            else:
                ceiling_y = 2 + 2 + rng.next_int(2)

        bits = bytearray([BIT_SOLID] * (N * N))
        room = [False] * (N * N)

        if style == 4:
            self._build_long(bits, rng)
        elif style == 2:
            self._build_hall(bits, room, rng)
        elif style == 5:
            self._build_hub(bits, room, rng)
        elif style == 7:
            self._build_poolroom(bits, room, rng)
        else:
            self._build_standard(bits, room, rng, style)

        self._carve_border_gateways(bits, dx, dz)
        _add_alcoves(bits, rng)
        _add_dead_ends(bits, rng)
        _add_pillars(bits, room, rng, style)
        if style == 6:
            _add_impossible_geometry(bits, rng)

        if self.level == 1:
            spacing = 7 if style in (2, 5) else (5 if style == 4 else 6 + rng.next_int(3))
        elif self.level == 2:
            spacing = 5 if style in (2, 5) else (4 if style == 4 else 5 + rng.next_int(3))
        elif self.level == 3:
            spacing = 6 if style in (2, 5) else (4 if style == 4 else 5 + rng.next_int(3))
        elif self.level == 4:
            spacing = 5 if style in (2, 5) else (4 if style == 4 else 4)
        elif self.level == 5:
            spacing = 6 if style in (2, 5) else (5 if style == 4 else 5 + rng.next_int(2))
        elif self.level == 6:
            spacing = 10 if style in (2, 5) else (9 if style == 4 else 11 + rng.next_int(3))
        else:
            if style in (2, 5):
                spacing = 6
            elif style == 4:
                spacing = 4
            else:
                spacing = 4 + rng.next_int(3)
        off_x = rng.next_int(spacing)
        off_z = rng.next_int(spacing)
        self._apply_lighting_and_surfaces(bits, dx, dz, spacing, off_x, off_z, style)

        return DistrictPlan(dx, dz, style, ceiling_y, spacing, off_x, off_z, bytes(bits))

    def _choose_style(self, rng: JavaRandom, dx: int, dz: int) -> int:
        dist = math.sqrt((dx * N) ** 2 + (dz * N) ** 2)
        deep = max(0.0, min(1.0, dist / 12000.0))

        if self.level == 1:
            weights = [0.30 - 0.10 * deep, 0.18, 0.24, 0.10 + 0.20 * deep, 0.06, 0.07, 0.03, 0.02]
        elif self.level == 2:
            weights = [0.30 - 0.10 * deep, 0.08, 0.02, 0.16 + 0.22 * deep, 0.32 + 0.06 * deep, 0.01, 0.06 + 0.08 * deep, 0.02]
        elif self.level == 3:
            weights = [0.20 - 0.06 * deep, 0.08, 0.10, 0.22 + 0.20 * deep, 0.30 + 0.06 * deep, 0.02, 0.06 + 0.06 * deep, 0.0]
        elif self.level == 4:
            weights = [0.40 - 0.10 * deep, 0.22, 0.14, 0.02, 0.10, 0.08, 0.02, 0.02]
        elif self.level == 5:
            weights = [0.34 - 0.10 * deep, 0.20, 0.12, 0.06 + 0.10 * deep, 0.16, 0.06, 0.04, 0.02]
        elif self.level == 6:
            weights = [0.18, 0.06, 0.04, 0.34 + 0.20 * deep, 0.28 + 0.06 * deep, 0.02, 0.06 + 0.06 * deep, 0.0]
        else:
            weights = [
                0.32 - 0.14 * deep,   # GRID
                0.26 - 0.08 * deep,   # ORGANIC
                0.09,                 # HALL
                0.10 + 0.24 * deep,   # DARK
                0.09 + 0.04 * deep,   # LONG
                0.05,                 # HUB
                0.04 + 0.10 * deep,   # IMPOSSIBLE
                0.02,                 # POOLROOM
            ]
        total = sum(weights)
        roll = rng.next_double() * total
        for i, w in enumerate(weights):
            roll -= w
            if roll < 0:
                return i
        return 7

    def _build_standard(self, bits: bytearray, room: list, rng: JavaRandom, style: int) -> None:
        trunk_row = 2 + rng.next_int(N - 4)
        trunk_col = 2 + rng.next_int(N - 4)
        trunk_width = 2 if rng.next_int(10) < 2 else 1
        _carve_h(bits, trunk_row, trunk_width)
        _carve_v(bits, trunk_col, trunk_width)

        if rng.next_int(100) < 50:
            if rng.next_boolean():
                r = 2 + rng.next_int(N - 4)
                if abs(r - trunk_row) >= 4:
                    _carve_h(bits, r, 2 if rng.next_int(10) < 15 else 1)
            else:
                c = 2 + rng.next_int(N - 4)
                if abs(c - trunk_col) >= 4:
                    _carve_v(bits, c, 2 if rng.next_int(10) < 15 else 1)

        if style == 0:
            room_count = 5 + rng.next_int(4)
        elif style == 1:
            room_count = 3 + rng.next_int(3)
        elif style == 6:
            room_count = 6 + rng.next_int(4)
        else:
            room_count = 4 + rng.next_int(3)

        rects: list[int] = []
        attempts = 0
        placed = 0
        while placed < room_count and attempts < 120:
            attempts += 1
            if style == 1:
                w = 5 + rng.next_int(6)
                h = 5 + rng.next_int(6)
            else:
                w = 4 + rng.next_int(4)
                h = 4 + rng.next_int(4)
            if w > N - 3 or h > N - 3:
                continue
            chosen_x = chosen_z = -1
            fallback_x = fallback_z = -1
            for _probe in range(12):
                cx0 = 1 + rng.next_int(N - w - 1)
                cz0 = 1 + rng.next_int(N - h - 1)
                if not _all_solid(bits, cx0, cz0, w, h):
                    continue
                if fallback_x < 0:
                    fallback_x, fallback_z = cx0, cz0
                if _touches_corridor(bits, room, cx0, cz0, w, h):
                    chosen_x, chosen_z = cx0, cz0
                    break
            x0 = chosen_x if chosen_x >= 0 else fallback_x
            z0 = chosen_z if chosen_x >= 0 else fallback_z
            if x0 < 0:
                continue
            _carve_rect(bits, x0 + 1, z0 + 1, w - 2, h - 2)
            _mark_room(room, x0 + 1, z0 + 1, w - 2, h - 2)
            _carve_room_doors(bits, room, x0, z0, w, h, rng)
            if len(rects) + 4 <= 64:
                rects += [x0, z0, w, h]
            placed += 1

        for r in range(0, len(rects), 4):
            _ensure_room_access(bits, room, rects[r], rects[r + 1], rects[r + 2], rects[r + 3])

    def _build_long(self, bits: bytearray, rng: JavaRandom) -> None:
        row = 4 + rng.next_int(N - 8)
        width = 2 if rng.next_int(100) < 30 else 1
        _carve_h(bits, row, width)
        stubs = 1 + rng.next_int(3)
        for _ in range(stubs):
            col = 2 + rng.next_int(N - 4)
            length = 2 + rng.next_int(3)
            up = rng.next_boolean()
            for s in range(1, length + 1):
                z = row - s if up else row + width + s - 1
                if 0 <= z < N:
                    bits[col * N + z] = 0

    def _build_hall(self, bits: bytearray, room: list, rng: JavaRandom) -> None:
        _carve_rect(bits, 1, 1, N - 2, N - 2)
        _mark_room(room, 1, 1, N - 2, N - 2)
        step = 3 + rng.next_int(2)
        phase = rng.next_int(step)
        for x in range(1 + phase, N - 1, step):
            for z in range(1 + phase, N - 1, step):
                bits[x * N + z] |= BIT_PILLAR

    def _build_hub(self, bits: bytearray, room: list, rng: JavaRandom) -> None:
        inset = 1 + rng.next_int(2)
        _carve_rect(bits, inset, inset, N - inset * 2, N - inset * 2)
        _mark_room(room, inset, inset, N - inset * 2, N - inset * 2)
        step = 2 + rng.next_int(2)
        phase = rng.next_int(step)
        for x in range(inset + phase + 1, N - inset - 1, step):
            for z in range(inset + phase + 1, N - inset - 1, step):
                bits[x * N + z] |= BIT_PILLAR

    def _build_poolroom(self, bits: bytearray, room: list, rng: JavaRandom) -> None:
        w = 8 + rng.next_int(5)
        h = 8 + rng.next_int(5)
        x0 = (N - w) // 2
        z0 = (N - h) // 2
        _carve_rect(bits, x0, z0, w, h)
        _mark_room(room, x0, z0, w, h)
        for x in range(x0, x0 + w):
            for z in range(z0, z0 + h):
                i = x * N + z
                bits[i] = (bits[i] & ~(SURFACE_MASK << SURFACE_SHIFT)) | (SURFACE_DAMP << SURFACE_SHIFT)
        gx = x0 + 1 + rng.next_int(w - 2)
        for z in range(0, z0):
            bits[gx * N + z] = 0

    def _carve_border_gateways(self, bits: bytearray, dx: int, dz: int) -> None:
        for edge in range(4):
            eh = self.edge_hash(dx, dz, edge)
            er = JavaRandom(eh)
            count = 2 if ((eh >> 17) % 100) < 40 else 1
            first = 2 + er.next_int(N - 4)
            _carve_gateway(bits, edge, first)
            if count == 2:
                second = 2 + er.next_int(N - 4)
                if abs(second - first) < 4:
                    second = (second + N // 2) % N
                _carve_gateway(bits, edge, second)

    def _apply_lighting_and_surfaces(self, bits, dx, dz, spacing, off_x, off_z, style) -> None:
        for x in range(N):
            for z in range(N):
                i = x * N + z
                b = bits[i] & 0xFF
                if b & BIT_SOLID:
                    b = b & ~(LIGHT_MASK << LIGHT_SHIFT)
                else:
                    wx, wz = dx * N + x, dz * N + z
                    on_lattice = mod(wx - off_x, spacing) == 0 and mod(wz - off_z, spacing) == 0
                    light = LIGHT_NONE
                    if on_lattice:
                        rel = self._reliability_for(wx, wz, dx, dz, style)
                        h = mix(self.seed, self.drift ^ 0x2545F4914F6CDD1D, pack(wx, wz), 0)
                        roll = ((h >> 13) % 10000) / 10000.0
                        if style == 7:
                            light = LIGHT_NONE
                        elif style == 3:
                            light = LIGHT_FLICKERING if roll < 0.12 else LIGHT_DEAD
                        elif roll < 0.55 + 0.40 * rel:
                            light = LIGHT_WORKING
                        elif roll < 0.68 + 0.25 * rel:
                            light = LIGHT_FLICKERING
                        else:
                            light = LIGHT_DEAD
                    b = (b & ~(LIGHT_MASK << LIGHT_SHIFT)) | (light << LIGHT_SHIFT)

                wx, wz = dx * N + x, dz * N + z
                sh = mix(self.seed ^ 0x9E3779B97F4A7C15, self.drift, pack(wx, wz), 0)
                s_roll = ((sh >> 21) % 10000) / 10000.0
                surface = SURFACE_CLEAN
                wall_neighbours = 0
                if x > 0 and bits[(x - 1) * N + z] & BIT_SOLID:
                    wall_neighbours += 1
                if x < N - 1 and bits[(x + 1) * N + z] & BIT_SOLID:
                    wall_neighbours += 1
                if z > 0 and bits[x * N + (z - 1)] & BIT_SOLID:
                    wall_neighbours += 1
                if z < N - 1 and bits[x * N + (z + 1)] & BIT_SOLID:
                    wall_neighbours += 1
                stain_chance = 0.05 + 0.07 * wall_neighbours
                if s_roll < stain_chance * 0.45:
                    surface = SURFACE_MOULDY
                elif s_roll < stain_chance:
                    surface = SURFACE_DAMP
                elif s_roll < stain_chance + 0.13:
                    surface = SURFACE_STAINED
                bits[i] = (b & ~(SURFACE_MASK << SURFACE_SHIFT)) | (surface << SURFACE_SHIFT)


class DistrictPlan:
    __slots__ = ("district_x", "district_z", "style", "ceiling_y",
                 "light_spacing", "light_offset_x", "light_offset_z", "bits")

    def __init__(self, dx, dz, style, ceiling_y, spacing, off_x, off_z, bits):
        self.district_x = dx
        self.district_z = dz
        self.style = style
        self.ceiling_y = ceiling_y
        self.light_spacing = spacing
        self.light_offset_x = off_x
        self.light_offset_z = off_z
        self.bits = bits

    def open_fraction(self) -> float:
        return sum(1 for b in self.bits if not (b & BIT_SOLID)) / len(self.bits)


# --------------------------------------------------------------------------
# carving helpers (mirror of the Java statics)
# --------------------------------------------------------------------------
def mod(a: int, b: int) -> int:
    return a - b * math.floor(a / b)


def pack(x: int, z: int) -> int:
    return (((x & M64) << 32) ^ (z & 0xFFFFFFFF)) & M64


def _carve_h(bits: bytearray, row: int, width: int) -> None:
    for x in range(N):
        for w in range(width):
            z = row + w
            if 0 <= z < N:
                bits[x * N + z] &= ~BIT_SOLID & 0xFF


def _carve_v(bits: bytearray, col: int, width: int) -> None:
    for z in range(N):
        for w in range(width):
            x = col + w
            if 0 <= x < N:
                bits[x * N + z] &= ~BIT_SOLID & 0xFF


def _carve_rect(bits: bytearray, x0: int, z0: int, w: int, h: int) -> None:
    for x in range(x0, x0 + w):
        for z in range(z0, z0 + h):
            if 0 <= x < N and 0 <= z < N:
                bits[x * N + z] &= ~BIT_SOLID & 0xFF


def _mark_room(room: list, x0: int, z0: int, w: int, h: int) -> None:
    for x in range(x0, x0 + w):
        for z in range(z0, z0 + h):
            if 0 <= x < N and 0 <= z < N:
                room[x * N + z] = True


def _all_solid(bits: bytearray, x0: int, z0: int, w: int, h: int) -> bool:
    for x in range(x0, x0 + w):
        for z in range(z0, z0 + h):
            if x < 0 or x >= N or z < 0 or z >= N:
                return False
            if not (bits[x * N + z] & BIT_SOLID):
                return False
    return True


def _carve_room_doors(bits: bytearray, room: list, x0: int, z0: int, w: int, h: int,
                      rng: JavaRandom) -> None:
    if w < 3 or h < 3:
        return
    wanted = 1 + rng.next_int(2)
    attempts = 0
    while wanted > 0 and attempts < 32:
        attempts += 1
        side = rng.next_int(4)
        if side == 0:
            px = x0 + 1 + rng.next_int(w - 2); pz = z0;         ox, oz = px, pz - 1
        elif side == 1:
            px = x0 + 1 + rng.next_int(w - 2); pz = z0 + h - 1; ox, oz = px, pz + 1
        elif side == 2:
            px = x0; pz = z0 + 1 + rng.next_int(h - 2);         ox, oz = px - 1, pz
        else:
            px = x0 + w - 1; pz = z0 + 1 + rng.next_int(h - 2); ox, oz = px + 1, pz
        if ox < 0 or ox >= N or oz < 0 or oz >= N:
            continue
        if bits[ox * N + oz] & BIT_SOLID:
            continue
        if room[ox * N + oz]:
            continue
        if px < 0 or px >= N or pz < 0 or pz >= N:
            continue
        bits[px * N + pz] = ((bits[px * N + pz] & ~BIT_SOLID) | BIT_DOOR) & 0xFF
        wanted -= 1


DX = (-1, 1, 0, 0)
DZ = (0, 0, -1, 1)


def _touches_corridor(bits: bytearray, room: list, x0: int, z0: int, w: int, h: int) -> bool:
    for x in range(x0, x0 + w):
        for z in range(z0, z0 + h):
            if not (x == x0 or z == z0 or x == x0 + w - 1 or z == z0 + h - 1):
                continue
            for d in range(4):
                nx, nz = x + DX[d], z + DZ[d]
                if nx < 0 or nx >= N or nz < 0 or nz >= N:
                    continue
                ni = nx * N + nz
                if not (bits[ni] & BIT_SOLID) and not room[ni]:
                    return True
    return False


def _carve_path(bits: bytearray, prev: list, start: int) -> None:
    i = start
    guard = 0
    while i >= 0 and guard <= N * N:
        guard += 1
        bits[i] &= ~BIT_SOLID & 0xFF
        i = -1 if prev[i] == -2 else prev[i]


def _flood_to_floor(bits, room, queue, prev, head, tail, accept_room_floor) -> bool:
    while head < tail:
        cur = queue[head]
        head += 1
        cx, cz = cur // N, cur % N
        for d in range(4):
            nx, nz = cx + DX[d], cz + DZ[d]
            if nx < 0 or nx >= N or nz < 0 or nz >= N:
                continue
            ni = nx * N + nz
            if not (bits[ni] & BIT_SOLID):
                if accept_room_floor or not room[ni]:
                    _carve_path(bits, prev, cur)
                    return True
                continue
            if prev[ni] != -1:
                continue
            prev[ni] = cur
            if tail < len(queue):
                queue[tail] = ni
                tail += 1
    return False


def _ensure_room_access(bits: bytearray, room: list, x0: int, z0: int, w: int, h: int) -> None:
    for x in range(x0, x0 + w):
        for z in range(z0, z0 + h):
            if x <= 0 or x >= N - 1 or z <= 0 or z >= N - 1:
                continue
            if bits[x * N + z] & BIT_SOLID:
                continue
            if room[x * N + z]:
                continue
            if (x == x0 or x == x0 + w - 1) and (z == z0 or z == z0 + h - 1):
                continue
            return

    def seed():
        queue = [0] * (N * N)
        prev = [-1] * (N * N)
        tail = 0
        for x in range(x0, x0 + w):
            for z in range(z0, z0 + h):
                ring = x == x0 or z == z0 or x == x0 + w - 1 or z == z0 + h - 1
                corner = (x == x0 or x == x0 + w - 1) and (z == z0 or z == z0 + h - 1)
                if not ring or corner:
                    continue
                if x < 0 or x >= N or z < 0 or z >= N:
                    continue
                i = x * N + z
                if prev[i] == -1:
                    prev[i] = -2
                    if tail < len(queue):
                        queue[tail] = i
                        tail += 1
        return queue, prev, 0, tail

    queue, prev, head, tail = seed()
    if _flood_to_floor(bits, room, queue, prev, head, tail, False):
        return
    queue, prev, head, tail = seed()
    _flood_to_floor(bits, room, queue, prev, head, tail, True)


def _carve_gateway(bits: bytearray, edge: int, pos: int) -> None:
    pos = max(0, min(N - 1, pos))
    if edge == 0:
        for z in range(N):
            if not (bits[pos * N + z] & BIT_SOLID):
                break
            bits[pos * N + z] &= ~BIT_SOLID & 0xFF
    elif edge == 1:
        for z in range(N - 1, -1, -1):
            if not (bits[pos * N + z] & BIT_SOLID):
                break
            bits[pos * N + z] &= ~BIT_SOLID & 0xFF
    elif edge == 2:
        for x in range(N):
            if not (bits[x * N + pos] & BIT_SOLID):
                break
            bits[x * N + pos] &= ~BIT_SOLID & 0xFF
    else:
        for x in range(N - 1, -1, -1):
            if not (bits[x * N + pos] & BIT_SOLID):
                break
            bits[x * N + pos] &= ~BIT_SOLID & 0xFF


def _add_alcoves(bits: bytearray, rng: JavaRandom) -> None:
    count = 1 + rng.next_int(4)
    attempts = 0
    while count > 0 and attempts < 60:
        attempts += 1
        x = 1 + rng.next_int(N - 2)
        z = 1 + rng.next_int(N - 2)
        if not (bits[x * N + z] & BIT_SOLID):
            continue
        open_n = sum(1 for dx, dz in ((-1, 0), (1, 0), (0, -1), (0, 1))
                     if not (bits[(x + dx) * N + (z + dz)] & BIT_SOLID))
        if open_n != 1:
            continue
        bits[x * N + z] &= ~BIT_SOLID & 0xFF
        count -= 1


def _add_dead_ends(bits: bytearray, rng: JavaRandom) -> None:
    count = rng.next_int(3)
    attempts = 0
    while count > 0 and attempts < 60:
        attempts += 1
        x = 1 + rng.next_int(N - 2)
        z = 1 + rng.next_int(N - 2)
        if not (bits[x * N + z] & BIT_SOLID):
            continue
        open_n = sum(1 for dx, dz in ((-1, 0), (1, 0), (0, -1), (0, 1))
                     if not (bits[(x + dx) * N + (z + dz)] & BIT_SOLID))
        if open_n != 1:
            continue
        bits[x * N + z] &= ~BIT_SOLID & 0xFF
        d = rng.next_int(4)
        length = 1 + rng.next_int(3)
        for s in range(1, length + 1):
            nx = x + (s if d == 0 else -s if d == 1 else 0)
            nz = z + (s if d == 2 else -s if d == 3 else 0)
            if nx <= 0 or nx >= N - 1 or nz <= 0 or nz >= N - 1:
                break
            bits[nx * N + nz] &= ~BIT_SOLID & 0xFF
        count -= 1


def _add_pillars(bits: bytearray, room: list, rng: JavaRandom, style: int) -> None:
    if style in (2, 5):
        return
    chance = 12 if style == 1 else 7
    for x in range(1, N - 1):
        for z in range(1, N - 1):
            i = x * N + z
            if bits[i] & BIT_SOLID or not room[i]:
                continue
            if any(not (bits[(x + dx) * N + (z + dz)] & BIT_SOLID)
                   for dx, dz in ((-1, 0), (1, 0), (0, -1), (0, 1))):
                continue
            if rng.next_int(100) < chance:
                bits[i] |= BIT_PILLAR


def _add_impossible_geometry(bits: bytearray, rng: JavaRandom) -> None:
    count = 1 + rng.next_int(3)
    attempts = 0
    while count > 0 and attempts < 80:
        attempts += 1
        x = 2 + rng.next_int(N - 4)
        z = 2 + rng.next_int(N - 4)
        if bits[x * N + z] & BIT_SOLID:
            continue
        if rng.next_boolean():
            bits[x * N + z] |= BIT_SOLID
            length = 1 + rng.next_int(2)
            horizontal = rng.next_boolean()
            for s in range(1, length + 1):
                nx = x + (s if horizontal else 0)
                nz = z + (0 if horizontal else s)
                if nx < N - 1 and nz < N - 1:
                    bits[nx * N + nz] |= BIT_SOLID
        else:
            w = 2 + rng.next_int(2)
            h = 2 + rng.next_int(2)
            if x + w >= N - 1 or z + h >= N - 1:
                continue
            for bx in range(x, x + w + 1):
                for bz in range(z, z + h + 1):
                    border = bx in (x, x + w) or bz in (z, z + h)
                    if border:
                        bits[bx * N + bz] |= BIT_SOLID
                    else:
                        bits[bx * N + bz] &= ~BIT_SOLID & 0xFF
        count -= 1


# --------------------------------------------------------------------------
# analysis
# --------------------------------------------------------------------------
def region_grid(layout: Level0Layout, tiles_x: int, tiles_z: int, ox: int = 0, oz: int = 0):
    """Solidity grid over a tile rectangle, sampled straight from the layout."""
    solid = [[layout.is_solid(ox + x, oz + z) for z in range(tiles_z)] for x in range(tiles_x)]
    return solid


def largest_open_component(solid):
    tx, tz = len(solid), len(solid[0])
    seen = [[False] * tz for _ in range(tx)]
    best = 0
    total_open = 0
    for sx in range(tx):
        for sz in range(tz):
            if solid[sx][sz]:
                continue
            total_open += 1
            if seen[sx][sz]:
                continue
            q = deque([(sx, sz)])
            seen[sx][sz] = True
            size = 0
            while q:
                cx, cz = q.popleft()
                size += 1
                for dx, dz in ((-1, 0), (1, 0), (0, -1), (0, 1)):
                    nx, nz = cx + dx, cz + dz
                    if 0 <= nx < tx and 0 <= nz < tz and not seen[nx][nz] and not solid[nx][nz]:
                        seen[nx][nz] = True
                        q.append((nx, nz))
            best = max(best, size)
    return best, total_open


def run_selftest(seed: int, districts: int, region: int) -> int:
    failures: list[str] = []
    layout = Level0Layout(seed)

    # --- per-district invariants over a wide scatter ----------------------
    style_counts: Counter = Counter()
    open_fracs: list[float] = []
    light_counts: Counter = Counter()
    surface_counts: Counter = Counter()
    edge_closed = 0
    fully_solid = 0

    import random as _r
    rr = _r.Random(seed)
    samples = []
    for _ in range(districts):
        dx = rr.randint(-60, 60)
        dz = rr.randint(-60, 60)
        samples.append((dx, dz))
        plan = layout.plan(dx * N + 1, dz * N + 1)
        style_counts[STYLES[plan.style]] += 1
        open_fracs.append(plan.open_fraction())
        if plan.open_fraction() < 0.15:
            fully_solid += 1
        for x in range(N):
            for z in range(N):
                b = plan.bits[x * N + z]
                if not (b & BIT_SOLID):
                    light_counts[(b >> LIGHT_SHIFT) & LIGHT_MASK] += 1
                surface_counts[(b >> SURFACE_SHIFT) & SURFACE_MASK] += 1

        # every district must open onto all four neighbours
        wx0, wz0 = dx * N, dz * N
        for edge in range(4):
            if edge == 0:
                ok = any(not layout.is_solid(wx0 + x, wz0) for x in range(N))
            elif edge == 1:
                ok = any(not layout.is_solid(wx0 + x, wz0 + N - 1) for x in range(N))
            elif edge == 2:
                ok = any(not layout.is_solid(wx0, wz0 + z) for z in range(N))
            else:
                ok = any(not layout.is_solid(wx0 + N - 1, wz0 + z) for z in range(N))
            if not ok:
                edge_closed += 1

    # --- connectivity over one contiguous multi-district region -----------
    solid = region_grid(layout, region * N, region * N, -(region * N) // 2, -(region * N) // 2)
    best, total_open = largest_open_component(solid)
    connected = best / total_open if total_open else 0.0

    # --- gateway agreement: both sides of a border pick the same slots ----
    mismatches = 0
    for _ in range(200):
        dx = rr.randint(-40, 40)
        dz = rr.randint(-40, 40)
        for edge in range(4):
            a = layout.edge_hash(dx, dz, edge)
            if edge == 0:
                b = layout.edge_hash(dx, dz - 1, 1)
            elif edge == 1:
                b = layout.edge_hash(dx, dz + 1, 0)
            elif edge == 2:
                b = layout.edge_hash(dx - 1, dz, 3)
            else:
                b = layout.edge_hash(dx + 1, dz, 2)
            if a != b:
                mismatches += 1

    # --- determinism + drift behaviour ------------------------------------
    l2 = Level0Layout(seed)
    determinism_ok = all(
        l2.tile_bits(x, z) == layout.tile_bits(x, z)
        for x, z in ((0, 0), (17, -33), (512, 512), (-777, 42))
    )
    l2.set_drift(1)
    drift_changes = sum(
        1 for x in range(-40, 40) for z in range(-40, 40)
        if l2.is_solid(x, z) != Level0Layout(seed).is_solid(x, z)
    )

    # --- reliability decays with depth ------------------------------------
    rel_near = layout.reliability(0, 0)
    rel_far = layout.reliability(6000, 6000)

    def check(name, cond, detail):
        status = "PASS" if cond else "FAIL"
        print(f"  [{status}] {name}: {detail}")
        if not cond:
            failures.append(name)

    print(f"\nLevel 0 layout selftest  (seed={seed}, {districts} districts, "
          f"{region}x{region}-district region)")
    print("-" * 78)
    check("districts never come out sealed shut", fully_solid == 0,
          f"{fully_solid} districts under 15% open of {districts}")
    check("every district opens onto all 4 neighbours", edge_closed == 0,
          f"{edge_closed} closed borders")
    check("shared edge hash agrees from both sides", mismatches == 0,
          f"{mismatches} mismatches of 800")
    check("region is essentially one connected space", connected > 0.985,
          f"{connected:.4f} of open floor reachable ({best}/{total_open} tiles); "
          f"{total_open - best} unreachable, which should only be the sealed boxes "
          f"IMPOSSIBLE districts build on purpose")
    lo, hi = min(open_fracs), max(open_fracs)
    mean = sum(open_fracs) / len(open_fracs)
    check("open/wall ratio stays in a playable band", 0.25 < mean < 0.75 and hi < 0.98,
          f"mean={mean:.3f} min={lo:.3f} max={hi:.3f}")
    check("layout is deterministic for a seed", determinism_ok, "4 probe columns identical")
    check("advancing drift actually re-solves the level", drift_changes > 100,
          f"{drift_changes} of 6400 columns changed")
    check("lighting reliability decays with depth", rel_far < rel_near,
          f"origin={rel_near:.3f} deep={rel_far:.3f}")

    print("\n  style mix      : " + ", ".join(
        f"{k}={v / districts:.0%}" for k, v in style_counts.most_common()))
    tl = sum(light_counts.values())
    print("  ceiling lights : " + ", ".join(
        f"{['none','working','flickering','dead'][k]}={v / tl:.1%}"
        for k, v in sorted(light_counts.items())))
    ts = sum(surface_counts.values())
    print("  floor surfaces : " + ", ".join(
        f"{['clean','stained','damp','mouldy'][k]}={v / ts:.1%}"
        for k, v in sorted(surface_counts.items())))

    print("-" * 78)
    if failures:
        print(f"FAILED: {len(failures)} check(s): {', '.join(failures)}")
        return 1
    print("ALL CHECKS PASSED")
    return 0


def render_map(layout: Level0Layout, path: str, tiles: int, scale: int, centre: int = 0) -> None:
    from PIL import Image

    img_w = tiles * scale
    img = Image.new("RGB", (img_w, img_w), (12, 11, 8))
    px = img.load()

    WALL = (58, 47, 20)
    FLOOR = (196, 168, 84)
    FLOOR_STAIN = (172, 143, 66)
    FLOOR_DAMP = (140, 118, 58)
    FLOOR_MOULD = (104, 92, 52)
    PILLAR = (120, 100, 46)
    LIGHT_ON = (255, 252, 224)
    LIGHT_FLICKER = (226, 220, 170)
    LIGHT_DEAD = (120, 118, 104)
    POOL = (96, 140, 150)

    ox = centre - tiles // 2
    oz = centre - tiles // 2
    for x in range(tiles):
        for z in range(tiles):
            wx, wz = ox + x, oz + z
            b = layout.tile_bits(wx, wz)
            if b & BIT_SOLID:
                c = WALL
            else:
                surf = (b >> SURFACE_SHIFT) & SURFACE_MASK
                c = (FLOOR, FLOOR_STAIN, FLOOR_DAMP, FLOOR_MOULD)[surf]
                if layout.style(wx, wz) == 7:
                    c = POOL
                if b & BIT_PILLAR:
                    c = PILLAR
                light = (b >> LIGHT_SHIFT) & LIGHT_MASK
                if light == 1:
                    c = LIGHT_ON
                elif light == 2:
                    c = LIGHT_FLICKER
                elif light == 3:
                    c = LIGHT_DEAD
                prop = layout.prop_at(wx, wz)
                if prop == 1:
                    c = (110, 70, 40)      # chair
                elif prop == 2:
                    c = (140, 100, 60)     # desk
                elif prop == 3:
                    c = (120, 60, 50)      # barrel
                elif prop == 4:
                    c = (170, 140, 90)     # box
                elif prop == 5:
                    c = (180, 40, 50)      # vending
                if layout.camera_at(wx, wz):
                    c = (255, 60, 60)      # camera
            for sx in range(scale):
                for sz in range(scale):
                    px[x * scale + sx, z * scale + sz] = c

    # district grid, faint, so the seams are visible
    for gx in range(0, tiles + 1, N):
        for i in range(img_w):
            for t in range(1):
                if 0 <= gx * scale + t < img_w:
                    px[gx * scale + t, i] = (30, 26, 14)
                if 0 <= gx * scale + t < img_w:
                    px[i, gx * scale + t] = (30, 26, 14)

    img.save(path)
    print(f"wrote {path}  ({img_w}x{img_w}px, {tiles}x{tiles} tiles, "
          f"{tiles * scale}px across {tiles} blocks)")


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--seed", type=int, default=1234567)
    ap.add_argument("--drift", type=int, default=0)
    ap.add_argument("--selftest", action="store_true", help="run the layout invariant checks")
    ap.add_argument("--districts", type=int, default=400)
    ap.add_argument("--region", type=int, default=6, help="region edge in districts for connectivity")
    ap.add_argument("--map", metavar="PATH", help="render a plan to a PNG")
    ap.add_argument("--tiles", type=int, default=192, help="map edge in tiles")
    ap.add_argument("--scale", type=int, default=4)
    ap.add_argument("--centre", type=int, default=0, help="map centre, in tiles from origin")
    args = ap.parse_args()

    rc = 0
    if args.selftest:
        rc = run_selftest(args.seed, args.districts, args.region)
    if args.map:
        layout = Level0Layout(args.seed, args.drift)
        render_map(layout, args.map, args.tiles, args.scale, args.centre)
    if not args.selftest and not args.map:
        ap.print_help()
    return rc


if __name__ == "__main__":
    sys.exit(main())
