package dev.qynl.backrooms.level0;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;

/**
 * The procedural heart of Level 0.
 *
 * <p>This class intentionally has <strong>no Minecraft dependency</strong>. Everything it needs is
 * {@link Random} and integer maths, so it can be unit tested on its own, reused by the design-time
 * preview tool ({@code tools/layout_preview.py}, which is a deliberate line-for-line port of this
 * file), and profiled without a running client.
 *
 * <h2>Stitching an endless interior out of chunk-sized pieces</h2>
 *
 * The world is cut into <em>districts</em> of {@link #DISTRICT_TILES}&times;{@link #DISTRICT_TILES}
 * tiles (one tile is one block column). A district is a pure function of
 * {@code (seed, drift, districtX, districtZ)} and is regenerated from scratch whenever a chunk asks
 * for it, so a chunk generator never needs to know about its neighbours.
 *
 * <p>Connectivity across district borders is the part that normally leaks. It is solved with a
 * <em>shared edge hash</em>: the gateways on the border between district A and district B are
 * derived from a hash of the canonical (order-normalised) pair {@code (A, B)}, so both districts
 * independently compute the same gateway positions and the corridors meet exactly on the seam.
 * Because every border always gets at least one gateway, and every gateway is tunnelled back to a
 * trunk corridor that spans the whole district, the plane is connected everywhere &mdash; you can
 * walk forever without hitting a sealed region.
 *
 * <h2>Why it does not look like a repeating structure</h2>
 *
 * Nothing is stamped from a template. Trunk positions, trunk widths, room rectangles, door sides,
 * pillar positions, alcoves, dead ends, stains and light fixtures are all drawn from the district's
 * own RNG stream, so two districts of the same {@link Style} still never match. The handcrafted part
 * is not geometry but <em>grammar</em>: {@link Style} is a small set of authored rules ("grid of
 * small rooms", "one impossible long hall", "dark zone", "the hub", "the poolroom teaser") and the
 * generator improvises inside those rules.
 *
 * <h2>Drift</h2>
 *
 * {@code drift} is folded into the hash of every district. Advancing it &mdash; which
 * {@code dev.qynl.backrooms.horror.HorrorDirector} does slowly, and only for chunks the player is
 * not looking at &mdash; makes the level re-solve itself. That is what produces "that room was not
 * there before" and "this corridor used to be shorter" without any block being changed while it is
 * on screen.
 *
 * <p>Thread safety: chunk generation runs on worker threads. The district cache is a
 * {@link java.util.Collections#synchronizedMap synchronized} LRU map; plan construction is pure and
 * idempotent, so a duplicate build under contention is harmless.
 */
public final class Level0Layout {

    /** District edge length in tiles. 16 tiles == 16 blocks, so a district is 256&times;256 blocks. */
    public static final int DISTRICT_TILES = 16;

    /** Y of the walkable carpet block. Players stand on top of it. */
    public static final int FLOOR_Y = 0;
    /** First row of interior air / walls above the floor. */
    public static final int WALL_MIN_Y = 1;

    private static final int N = DISTRICT_TILES;

    // ---- tile bit layout -------------------------------------------------
    // One byte per tile:
    //   bit 0    SOLID   - the column is wall/pillar and gets filled floor-to-ceiling
    //   bit 1    PILLAR  - open floor, but a free-standing column rises through the room
    //   bit 2    DOOR    - this solid-looking tile was deliberately carved as an opening
    //   bits 3-4 SURFACE - 0 clean, 1 stained, 2 damp/water damage, 3 mouldy
    //   bits 5-6 LIGHT   - 0 none, 1 working, 2 flickering, 3 dead
    public static final int BIT_SOLID = 1;
    public static final int BIT_PILLAR = 2;
    public static final int BIT_DOOR = 4;
    private static final int SURFACE_SHIFT = 3;
    private static final int SURFACE_MASK = 3;
    private static final int LIGHT_SHIFT = 5;
    private static final int LIGHT_MASK = 3;

    public static final int SURFACE_CLEAN = 0;
    public static final int SURFACE_STAINED = 1;
    public static final int SURFACE_DAMP = 2;
    public static final int SURFACE_MOULDY = 3;

    public static final int LIGHT_NONE = 0;
    public static final int LIGHT_WORKING = 1;
    public static final int LIGHT_FLICKERING = 2;
    public static final int LIGHT_DEAD = 3;

    /** Authored district "grammars". The generator improvises inside each one. */
    public enum Style {
        /** Even-ish grid of corridors with small rooms hung off them. The classic Level 0 look. */
        GRID,
        /** Fewer, larger, lopsided rooms; corridors wander. Feels like the back of an old office. */
        ORGANIC,
        /** Mostly open floor on a pillar grid. Rare, and unsettling because there is nowhere to hide. */
        HALL,
        /** Lights mostly or entirely absent. Reliability collapses here. */
        DARK,
        /** A single corridor runs the whole district. Reads as "endless hallway". */
        LONG,
        /** One very large room with a forest of pillars. A landmark. */
        HUB,
        /** Geometry that should not be possible: rooms inside rooms, walls that meet nothing. */
        IMPOSSIBLE,
        /** Very rare teaser towards somewhere else. Wet floor, no lights, a different colour. */
        POOLROOM
    }

    /** A solved district. Immutable once built. */
    public static final class DistrictPlan {
        public final int districtX;
        public final int districtZ;
        public final Style style;
        /** Y of the ceiling block for this district. Varies so ceilings are not uniformly flat. */
        public final int ceilingY;
        /** Spacing of the ceiling light lattice, in tiles. */
        public final int lightSpacing;
        /** Sub-tile offset of the light lattice, so the grid never lines up between districts. */
        public final int lightOffsetX;
        public final int lightOffsetZ;
        final byte[] bits;

        DistrictPlan(int districtX, int districtZ, Style style, int ceilingY,
                     int lightSpacing, int lightOffsetX, int lightOffsetZ, byte[] bits) {
            this.districtX = districtX;
            this.districtZ = districtZ;
            this.style = style;
            this.ceilingY = ceilingY;
            this.lightSpacing = lightSpacing;
            this.lightOffsetX = lightOffsetX;
            this.lightOffsetZ = lightOffsetZ;
            this.bits = bits;
        }

        /** Fraction of tiles that are walkable. Used by tests and by the preview tool. */
        public float openFraction() {
            int open = 0;
            for (byte b : bits) {
                if ((b & BIT_SOLID) == 0) open++;
            }
            return open / (float) bits.length;
        }
    }

    private final long seed;
    private volatile long drift;
    private final int level;
    private final Map<Long, DistrictPlan> cache;

    public Level0Layout(long seed) {
        this(seed, 0L, 0);
    }

    public Level0Layout(long seed, long drift) {
        this(seed, drift, 0);
    }

    public Level0Layout(long seed, long drift, int level) {
        this.seed = seed;
        this.drift = drift;
        this.level = level;
        // Small LRU: a single chunk touches at most 4 districts, so 256 entries is a lot of slack.
        final int maxEntries = 512;
        this.cache = java.util.Collections.synchronizedMap(
                new LinkedHashMap<Long, DistrictPlan>(64, 0.75f, true) {
                    @Override
                    protected boolean removeEldestEntry(Map.Entry<Long, DistrictPlan> eldest) {
                        return size() > maxEntries;
                    }
                });
    }

    public long seed() {
        return seed;
    }

    public long drift() {
        return drift;
    }

    /** Which Backrooms level this layout solves (0 = yellow rooms, 1 = habital concrete, 2 = pipes). */
    public int level() {
        return level;
    }

    /**
     * Advances the drift counter. Every district re-solves, so chunks that are regenerated later
     * come back subtly different. Cheap: this only clears a cache.
     */
    public void setDrift(long newDrift) {
        if (newDrift != this.drift) {
            this.drift = newDrift;
            this.cache.clear();
        }
    }

    public void invalidateCache() {
        this.cache.clear();
    }

    // ---- queries ---------------------------------------------------------

    public DistrictPlan plan(int x, int z) {
        int dx = Math.floorDiv(x, N);
        int dz = Math.floorDiv(z, N);
        long key = (((long) dx) << 32) ^ (dz & 0xFFFFFFFFL);
        DistrictPlan plan = cache.get(key);
        if (plan == null) {
            plan = buildPlan(dx, dz);
            cache.put(key, plan);
        }
        return plan;
    }

    /** Raw encoded tile bits for a world column. */
    public int tileBits(int x, int z) {
        DistrictPlan plan = plan(x, z);
        return plan.bits[Math.floorMod(x, N) * N + Math.floorMod(z, N)] & 0xFF;
    }

    /** True when the column is filled floor-to-ceiling with wall blocks. */
    public boolean isSolid(int x, int z) {
        return (tileBits(x, z) & BIT_SOLID) != 0;
    }

    /** True when the column is walkable but carries a free-standing pillar. */
    public boolean isPillar(int x, int z) {
        return (tileBits(x, z) & BIT_PILLAR) != 0;
    }

    /** 0 clean, 1 stained, 2 damp, 3 mouldy. Drives blockstate variant + stain decals. */
    public int surface(int x, int z) {
        return (tileBits(x, z) >> SURFACE_SHIFT) & SURFACE_MASK;
    }

    /** {@link #LIGHT_NONE}, {@link #LIGHT_WORKING}, {@link #LIGHT_FLICKERING} or {@link #LIGHT_DEAD}. */
    public int lightState(int x, int z) {
        return (tileBits(x, z) >> LIGHT_SHIFT) & LIGHT_MASK;
    }

    // ---- clutter ---------------------------------------------------------

    public static final int PROP_NONE = 0;
    public static final int PROP_CHAIR = 1;
    public static final int PROP_DESK = 2;
    public static final int PROP_BARREL = 3;
    public static final int PROP_BOX = 4;
    public static final int PROP_VENDING = 5;
    public static final int PROP_COUNT = 5;

    private static final long PROP_SALT = 0xA5A5A5A5C0FFEE01L;
    private static final long CAMERA_SALT = 0xC4C4C4C4D10CA2L;

    /**
     * Sparse, deterministic clutter. Returns one of the {@code PROP_*} ids, or {@link #PROP_NONE}.
     *
     * <p>Props only ever land on open floor, never on doors or pillars, and never inside a 1-wide
     * passage (detected by opposing solid neighbours), so a chair can never seal a corridor. Density
     * is deliberately low: about 2-3% of eligible tiles, which reads as "someone left things here"
     * rather than a furniture store.
     */
    public int propAt(int x, int z) {
        int bits = tileBits(x, z);
        if ((bits & BIT_SOLID) != 0) return PROP_NONE;
        if ((bits & BIT_PILLAR) != 0) return PROP_NONE;
        if ((bits & BIT_DOOR) != 0) return PROP_NONE;
        boolean l = isSolid(x - 1, z);
        boolean r = isSolid(x + 1, z);
        boolean u = isSolid(x, z - 1);
        boolean d = isSolid(x, z + 1);
        if ((l && r) || (u && d)) return PROP_NONE;   // 1-wide passage: keep it clear

        long h = mix(seed, drift ^ PROP_SALT, x, z);
        int roll = (int) Math.floorMod(h >>> 17, 1000);
        // Crates and drums are the "resources" of the deeper levels, so clutter is denser there.
        int base = level == 1 ? 42 : level == 2 ? 34 : level == 3 ? 30 : level == 4 ? 20 : level == 5 ? 26 : level == 6 ? 12 : level == 7 ? 12 : level == 8 ? 22 : 26;
        int density = style(x, z) == Style.DARK ? base / 2 : base;
        if (roll >= density) return PROP_NONE;
        return 1 + (int) Math.floorMod(h >>> 29, PROP_COUNT);
    }

    private static final long PUDDLE_SALT = 0x9D1E0000AA11L;

    /** Level 1 only: a shallow puddle of stagnant liquid on open floor. Sparse and deniable. */
    public boolean puddleAt(int x, int z) {
        if (level != 1 && level != 3 && level != 8) return false;
        int bits = tileBits(x, z);
        if ((bits & BIT_SOLID) != 0 || (bits & BIT_PILLAR) != 0) return false;
        long h = mix(seed, drift ^ PUDDLE_SALT, x, z);
        return Math.floorMod(h >>> 21, 1000) < 40;
    }

    /** Rare hanging security camera on the ceiling. Purely atmospheric. */
    public boolean cameraAt(int x, int z) {
        int bits = tileBits(x, z);
        if ((bits & BIT_SOLID) != 0 || (bits & BIT_PILLAR) != 0) return false;
        long h = mix(seed, drift ^ CAMERA_SALT, x, z);
        return Math.floorMod(h >>> 19, 1000) < 5;
    }

    /** Y of the ceiling block at this column. */
    public int ceilingY(int x, int z) {
        return plan(x, z).ceilingY;
    }

    public Style style(int x, int z) {
        return plan(x, z).style;
    }

    /**
     * Lighting reliability at a column, 0..1. Starts near-perfect at the arrival point and decays
     * with distance, so the deeper a player walks the less they can trust the lights. District hash
     * adds local variation so the falloff is patchy rather than a clean ring.
     */
    public float reliability(int x, int z) {
        DistrictPlan plan = plan(x, z);
        return reliabilityFor(x, z, plan.districtX, plan.districtZ, plan.style);
    }

    /**
     * Same as {@link #reliability(int, int)} but without the plan lookup, so it can be called from
     * inside {@link #buildPlan} while the plan is still being constructed. Calling the public
     * variant there would re-enter the cache for a district that is not cached yet and recurse
     * forever.
     */
    private float reliabilityFor(int x, int z, int dx, int dz, Style style) {
        double dist = Math.sqrt((double) x * x + (double) z * z);
        float base = (float) (1.0 - dist / 9000.0);
        long h = mix(seed, drift, dx, dz);
        float local = (Math.floorMod(h >>> 11, 1000) / 1000.0f - 0.5f) * 0.35f;
        if (style == Style.DARK) local -= 0.45f;
        if (style == Style.HUB) local += 0.2f;
        return clamp01(base + local);
    }

    // ---- plan construction ----------------------------------------------

    private DistrictPlan buildPlan(int dx, int dz) {
        long base = mix(seed, drift, dx, dz);
        Random rng = new Random(base);

        Style style = chooseStyle(rng, dx, dz);

        int ceilingY;
        if (level == 1) {
            // Warehouse-scale concrete: taller, echoing.
            ceilingY = switch (style) {
                case HALL, HUB -> 2 + 5 + rng.nextInt(2);   // 7-8
                case LONG -> 2 + 2;                          // 4
                default -> 2 + 3 + rng.nextInt(2);           // 5-6
            };
        } else if (level == 2) {
            // Cramped utility tunnels: low.
            ceilingY = switch (style) {
                case HALL, HUB -> 2 + 2;   // 4
                default -> 3;              // interior of 2: pipes overhead
            };
        } else if (level == 3) {
            // Cramped service halls, but the occasional vast machine room towers overhead.
            ceilingY = switch (style) {
                case HALL, HUB -> 2 + 4 + rng.nextInt(2);   // 6-7
                case LONG -> 3;                              // crawl-height corridors
                default -> 3;
            };
        } else if (level == 4) {
            // Office floors: a comfortable, consistent ceiling.
            ceilingY = switch (style) {
                case HALL, HUB -> 2 + 3;                     // 5
                default -> 2 + 2;                            // 4
            };
        } else if (level == 5) {
            ceilingY = switch (style) {
                case HALL, HUB -> 2 + 4;                     // 6: ballroom height
                case LONG -> 3;
                default -> 2 + 3;                            // 5
            };
        } else if (level == 6) {
            ceilingY = switch (style) {
                case HALL, HUB -> 2 + 2;                     // 4
                default -> 3;
            };
        } else if (level == 7) {
            ceilingY = switch (style) {
                case HALL, HUB, POOLROOM -> 2 + 5;           // 7: deep water under a lid
                default -> 2 + 4;                            // 6
            };
        } else if (level == 8) {
            ceilingY = switch (style) {
                case HALL, HUB -> 2 + 4 + rng.nextInt(2);    // 6-7: cavernous
                case LONG -> 3;                              // tight crawl tunnels
                default -> 2 + 2 + rng.nextInt(2);           // 4-5
            };
        } else {
            ceilingY = switch (style) {
                case HALL, HUB -> 2 + 4 + rng.nextInt(2);   // 6-7: high, echoing
                case LONG -> 3;                              // interior of 2: oppressive
                default -> 2 + 2 + rng.nextInt(2);           // 4-5
            };
        }

        byte[] bits = new byte[N * N];
        Arrays.fill(bits, (byte) BIT_SOLID);
        boolean[] room = new boolean[N * N];

        if (level == 7) {
            // Thalassophobia: not a maze at all - an open ocean dotted with rock islands.
            buildOcean(bits, rng);
        } else if (level == 8) {
            // Cave System: organic caverns and meandering tunnels bored through solid rock.
            buildCaves(bits, rng);
        } else {
            switch (style) {
                case LONG -> buildLong(bits, rng);
                case HALL -> buildHall(bits, room, rng);
                case HUB -> buildHub(bits, room, rng);
                case POOLROOM -> buildPoolroom(bits, room, rng);
                default -> buildStandard(bits, room, rng, style);
            }
        }

        // Every district connects to all four neighbours, always. Done after the interior so the
        // tunnels punch through whatever the style left in the way.
        carveBorderGateways(bits, dx, dz);

        // Architectural imperfections. Maze levels only - the ocean and caves shape themselves.
        if (level != 7 && level != 8) {
            addAlcoves(bits, rng);
            addDeadEnds(bits, rng);
            addPillars(bits, room, rng, style);
            if (style == Style.IMPOSSIBLE) addImpossibleGeometry(bits, rng);
        }

        // Lighting. Depends on nothing but position + reliability, so it can be queried per block
        // without a second pass.
        int spacing;
        if (level == 1) {
            spacing = switch (style) {
                case HALL, HUB -> 7;
                case LONG -> 5;
                default -> 6 + rng.nextInt(3);   // sparser, warehouse lighting
            };
        } else if (level == 2) {
            spacing = switch (style) {
                case HALL, HUB -> 5;
                case LONG -> 4;
                default -> 5 + rng.nextInt(3);   // uneven industrial strips
            };
        } else if (level == 3) {
            spacing = switch (style) {
                case HALL, HUB -> 6;
                case LONG -> 4;
                default -> 5 + rng.nextInt(3);   // dim, unreliable strips
            };
        } else if (level == 4) {
            spacing = switch (style) {
                case HALL, HUB -> 5;
                case LONG -> 4;
                default -> 4;                    // bright, even office lighting
            };
        } else if (level == 5) {
            spacing = switch (style) {
                case HALL, HUB -> 6;
                case LONG -> 5;
                default -> 5 + rng.nextInt(2);   // warm, sparse hotel lighting
            };
        } else if (level == 6) {
            spacing = switch (style) {
                case HALL, HUB -> 10;
                case LONG -> 9;
                default -> 11 + rng.nextInt(3);  // almost never a working light
            };
        } else if (level == 7) {
            spacing = switch (style) {
                case HALL, HUB, POOLROOM -> 14;  // dim natural light over open water
                default -> 12 + rng.nextInt(3);
            };
        } else if (level == 8) {
            spacing = switch (style) {
                case HALL, HUB -> 8;
                case LONG -> 7;
                default -> 9 + rng.nextInt(3);   // dark caves, rare light
            };
        } else {
            spacing = switch (style) {
                case HALL, HUB -> 6;
                case LONG -> 4;
                default -> 4 + rng.nextInt(3);   // 4-6
            };
        }
        int offX = rng.nextInt(spacing);
        int offZ = rng.nextInt(spacing);
        applyLightingAndSurfaces(bits, dx, dz, spacing, offX, offZ, style);

        return new DistrictPlan(dx, dz, style, ceilingY, spacing, offX, offZ, bits);
    }

    private Style chooseStyle(Random rng, int dx, int dz) {
        double dist = Math.sqrt((double) (dx * N) * (dx * N) + (double) (dz * N) * (dz * N));
        // Deep level: the lights give up and the geometry stops pretending to be a building.
        float deep = clamp01((float) (dist / 12000.0));

        double wGrid, wOrganic, wHall, wDark, wLong, wHub, wImpossible, wPoolroom;
        if (level == 1) {
            // Habitable Zone: wide pillar halls and warehouse rooms, fewer dead-ends.
            wGrid = 0.30 - 0.10 * deep;
            wOrganic = 0.18;
            wHall = 0.24;
            wDark = 0.10 + 0.20 * deep;
            wLong = 0.06;
            wHub = 0.07;
            wImpossible = 0.03;
            wPoolroom = 0.02;
        } else if (level == 2) {
            // Utility halls: narrow corridors and dark machinery runs, almost no open halls.
            wGrid = 0.30 - 0.10 * deep;
            wOrganic = 0.08;
            wHall = 0.02;
            wDark = 0.16 + 0.22 * deep;
            wLong = 0.32 + 0.06 * deep;
            wHub = 0.01;
            wImpossible = 0.06 + 0.08 * deep;
            wPoolroom = 0.02;
        } else if (level == 3) {
            // Electrical Station: cramped brick hallways, dark machinery runs, the odd vast room.
            wGrid = 0.20 - 0.06 * deep;
            wOrganic = 0.08;
            wHall = 0.10;
            wDark = 0.22 + 0.20 * deep;
            wLong = 0.30 + 0.06 * deep;
            wHub = 0.02;
            wImpossible = 0.06 + 0.06 * deep;
            wPoolroom = 0.0;
        } else if (level == 4) {
            // Abandoned Office: an organised, well-lit grid of rooms and pillar halls. Calm.
            wGrid = 0.40 - 0.10 * deep;
            wOrganic = 0.22;
            wHall = 0.14;
            wDark = 0.02;
            wLong = 0.10;
            wHub = 0.08;
            wImpossible = 0.02;
            wPoolroom = 0.02;
        } else if (level == 5) {
            // Terror Hotel: long carpeted hallways, the odd ballroom, warm and dim.
            wGrid = 0.34 - 0.10 * deep;
            wOrganic = 0.20;
            wHall = 0.12;
            wDark = 0.06 + 0.10 * deep;
            wLong = 0.16;
            wHub = 0.06;
            wImpossible = 0.04;
            wPoolroom = 0.02;
        } else if (level == 6) {
            // Lights Out: pitch-black metal corridors, almost no light at all.
            wGrid = 0.18;
            wOrganic = 0.06;
            wHall = 0.04;
            wDark = 0.34 + 0.20 * deep;
            wLong = 0.28 + 0.06 * deep;
            wHub = 0.02;
            wImpossible = 0.06 + 0.06 * deep;
            wPoolroom = 0.0;
        } else if (level == 7) {
            // Thalassophobia: open dark water broken by rock islands.
            wGrid = 0.10;
            wOrganic = 0.16;
            wHall = 0.24;
            wDark = 0.10;
            wLong = 0.14;
            wHub = 0.04;
            wImpossible = 0.04;
            wPoolroom = 0.18;
        } else if (level == 8) {
            // Cave System: twisting rocky tunnels and chambers, dark.
            wGrid = 0.16;
            wOrganic = 0.24;
            wHall = 0.14;
            wDark = 0.20 + 0.16 * deep;
            wLong = 0.16;
            wHub = 0.04;
            wImpossible = 0.06 + 0.06 * deep;
            wPoolroom = 0.0;
        } else {
            wGrid = 0.32 - 0.14 * deep;
            wOrganic = 0.26 - 0.08 * deep;
            wHall = 0.09;
            wDark = 0.10 + 0.24 * deep;
            wLong = 0.09 + 0.04 * deep;
            wHub = 0.05;
            wImpossible = 0.04 + 0.10 * deep;
            wPoolroom = 0.02;
        }

        double total = wGrid + wOrganic + wHall + wDark + wLong + wHub + wImpossible + wPoolroom;
        double roll = rng.nextDouble() * total;
        if ((roll -= wGrid) < 0) return Style.GRID;
        if ((roll -= wOrganic) < 0) return Style.ORGANIC;
        if ((roll -= wHall) < 0) return Style.HALL;
        if ((roll -= wDark) < 0) return Style.DARK;
        if ((roll -= wLong) < 0) return Style.LONG;
        if ((roll -= wHub) < 0) return Style.HUB;
        if ((roll -= wImpossible) < 0) return Style.IMPOSSIBLE;
        return Style.POOLROOM;
    }

    /** GRID / ORGANIC / DARK / IMPOSSIBLE: trunk cross, then rooms hung off the corridors. */
    private void buildStandard(byte[] bits, boolean[] room, Random rng, Style style) {
        int trunkRow = 2 + rng.nextInt(N - 4);
        int trunkCol = 2 + rng.nextInt(N - 4);
        int trunkWidth = rng.nextInt(10) < 2 ? 2 : 1;

        carveH(bits, trunkRow, trunkWidth);
        carveV(bits, trunkCol, trunkWidth);

        // A second, offset cross in about half the districts breaks the pinwheel look.
        if (rng.nextInt(100) < 50) {
            if (rng.nextBoolean()) {
                int r = 2 + rng.nextInt(N - 4);
                if (Math.abs(r - trunkRow) >= 4) carveH(bits, r, rng.nextInt(10) < 15 ? 2 : 1);
            } else {
                int c = 2 + rng.nextInt(N - 4);
                if (Math.abs(c - trunkCol) >= 4) carveV(bits, c, rng.nextInt(10) < 15 ? 2 : 1);
            }
        }

        int roomCount = switch (style) {
            case GRID -> 5 + rng.nextInt(4);          // 5-8 small rooms
            case ORGANIC -> 3 + rng.nextInt(3);       // 3-5 big lopsided ones
            case IMPOSSIBLE -> 6 + rng.nextInt(4);
            default -> 4 + rng.nextInt(3);            // DARK
        };

        int attempts = 0;
        int placed = 0;
        int[] rects = new int[16 * 4];
        int rectCount = 0;
        while (placed < roomCount && attempts++ < 120) {
            int w = style == Style.ORGANIC ? 5 + rng.nextInt(6) : 4 + rng.nextInt(4);
            int h = style == Style.ORGANIC ? 5 + rng.nextInt(6) : 4 + rng.nextInt(4);
            if (w > N - 3 || h > N - 3) continue;

            // Rooms are only placed inside fully solid ground, which means a plain random site very
            // often lands nowhere near a corridor and would come out doorless. Probe a handful of
            // candidate sites and prefer one whose wall ring already touches open floor.
            int chosenX = -1;
            int chosenZ = -1;
            int fallbackX = -1;
            int fallbackZ = -1;
            for (int probe = 0; probe < 12; probe++) {
                int cx0 = 1 + rng.nextInt(N - w - 1);
                int cz0 = 1 + rng.nextInt(N - h - 1);
                if (!allSolid(bits, cx0, cz0, w, h)) continue;
                if (fallbackX < 0) {
                    fallbackX = cx0;
                    fallbackZ = cz0;
                }
                if (touchesCorridor(bits, room, cx0, cz0, w, h)) {
                    chosenX = cx0;
                    chosenZ = cz0;
                    break;
                }
            }
            int x0 = chosenX >= 0 ? chosenX : fallbackX;
            int z0 = chosenX >= 0 ? chosenZ : fallbackZ;
            if (x0 < 0) continue;

            // Leave the outermost ring solid; that ring is the room's wall.
            carveRect(bits, x0 + 1, z0 + 1, w - 2, h - 2);
            markRoom(room, x0 + 1, z0 + 1, w - 2, h - 2);
            carveRoomDoors(bits, room, x0, z0, w, h, rng);
            if (rectCount + 4 <= rects.length) {
                rects[rectCount++] = x0;
                rects[rectCount++] = z0;
                rects[rectCount++] = w;
                rects[rectCount++] = h;
            }
            placed++;
        }

        // A room whose wall ring still touches no corridor would come out sealed. Being unable to
        // reach a room is fine as a deliberate oddity in tiny doses, but not by accident, so tunnel
        // any such room out to the nearest real floor.
        for (int r = 0; r < rectCount; r += 4) {
            ensureRoomAccess(bits, room, rects[r], rects[r + 1], rects[r + 2], rects[r + 3]);
        }
    }

    /** True if any tile of the room's wall ring has open, non-room floor immediately outside it. */
    private static boolean touchesCorridor(byte[] bits, boolean[] room, int x0, int z0, int w, int h) {
        for (int x = x0; x < x0 + w; x++) {
            for (int z = z0; z < z0 + h; z++) {
                boolean ring = x == x0 || z == z0 || x == x0 + w - 1 || z == z0 + h - 1;
                if (!ring) continue;
                for (int d = 0; d < 4; d++) {
                    int nx = x + DX[d];
                    int nz = z + DZ[d];
                    if (nx < 0 || nx >= N || nz < 0 || nz >= N) continue;
                    int ni = nx * N + nz;
                    if ((bits[ni] & BIT_SOLID) == 0 && !room[ni]) return true;
                }
            }
        }
        return false;
    }

    private static final int[] DX = {-1, 1, 0, 0};
    private static final int[] DZ = {0, 0, -1, 1};

    /**
     * Guarantees a room is reachable. Floods outward from the room's wall ring through solid
     * ground until it reaches open floor that is not another room's interior, then carves the
     * shortest such tunnel.
     *
     * <p>An earlier version dug straight north and stopped at the district border, which quietly
     * sealed every doorless room sitting north of the trunk corridor &mdash; measured at roughly
     * 5% of all floor tiles being unreachable. Flooding to a real target cannot do that: the trunk
     * corridors span the whole district, so open floor is always found before the border.
     */
    private static void ensureRoomAccess(byte[] bits, boolean[] room, int x0, int z0, int w, int h) {
        for (int x = x0; x < x0 + w; x++) {
            for (int z = z0; z < z0 + h; z++) {
                if (x <= 0 || x >= N - 1 || z <= 0 || z >= N - 1) continue;
                if ((bits[x * N + z] & BIT_SOLID) != 0) continue;
                if (room[x * N + z]) continue;   // that is this room's own interior
                // An open corner of the ring does not actually open the room, so it does not count
                // as access.
                if ((x == x0 || x == x0 + w - 1) && (z == z0 || z == z0 + h - 1)) continue;
                return;                          // already opens onto real floor
            }
        }

        int cap = N * N;
        int[] queue = new int[cap];
        int[] prev = new int[cap];

        // First pass: only accept corridor floor, so we never burrow through a neighbouring room.
        int tail = seedRing(queue, prev, x0, z0, w, h);
        if (floodToFloor(bits, room, queue, prev, 0, tail, false)) return;

        // Second pass: nothing but other rooms is reachable. Joining two rooms is still better than
        // sealing one, so accept any open floor.
        Arrays.fill(prev, -1);
        tail = seedRing(queue, prev, x0, z0, w, h);
        floodToFloor(bits, room, queue, prev, 0, tail, true);
    }

    /**
     * Queues the room's wall ring as BFS sources, skipping corner tiles. A corner of the ring only
     * touches the room's interior diagonally, so a tunnel that surfaces at a corner carves the ring
     * without actually opening the room &mdash; which is exactly how sealed rooms slipped through an
     * earlier version of this method.
     */
    private static int seedRing(int[] queue, int[] prev, int x0, int z0, int w, int h) {
        Arrays.fill(prev, -1);
        int tail = 0;
        for (int x = x0; x < x0 + w; x++) {
            for (int z = z0; z < z0 + h; z++) {
                boolean ring = x == x0 || z == z0 || x == x0 + w - 1 || z == z0 + h - 1;
                boolean corner = (x == x0 || x == x0 + w - 1) && (z == z0 || z == z0 + h - 1);
                if (!ring || corner) continue;
                if (x < 0 || x >= N || z < 0 || z >= N) continue;
                int i = x * N + z;
                if (prev[i] == -1) {
                    prev[i] = -2;
                    if (tail < queue.length) queue[tail++] = i;
                }
            }
        }
        return tail;
    }

    /** BFS over solid tiles from a seeded ring; carves the path when open floor is reached. */
    private static boolean floodToFloor(byte[] bits, boolean[] room, int[] queue, int[] prev,
                                        int head, int tail, boolean acceptRoomFloor) {
        while (head < tail) {
            int cur = queue[head++];
            int cx = cur / N;
            int cz = cur % N;
            for (int d = 0; d < 4; d++) {
                int nx = cx + DX[d];
                int nz = cz + DZ[d];
                if (nx < 0 || nx >= N || nz < 0 || nz >= N) continue;
                int ni = nx * N + nz;
                if ((bits[ni] & BIT_SOLID) == 0) {
                    if (acceptRoomFloor || !room[ni]) {
                        carvePath(bits, prev, cur);
                        return true;
                    }
                    continue;   // do not tunnel through another room on the strict pass
                }
                if (prev[ni] != -1) continue;
                prev[ni] = cur;
                if (tail < queue.length) queue[tail++] = ni;
            }
        }
        return false;
    }

    /** Clears BIT_SOLID along the parent chain back to the seeded ring. */
    private static void carvePath(byte[] bits, int[] prev, int start) {
        int i = start;
        int guard = 0;
        while (i >= 0 && guard++ < N * N + 1) {
            bits[i] &= (byte) ~BIT_SOLID;
            i = prev[i] == -2 ? -1 : prev[i];
        }
    }

    /** One corridor, the full length of the district. */
    private void buildLong(byte[] bits, Random rng) {
        int row = 4 + rng.nextInt(N - 8);
        int width = rng.nextInt(100) < 30 ? 2 : 1;
        carveH(bits, row, width);
        // A couple of short side stubs so it is not literally a straight line.
        int stubs = 1 + rng.nextInt(3);
        for (int i = 0; i < stubs; i++) {
            int col = 2 + rng.nextInt(N - 4);
            int len = 2 + rng.nextInt(3);
            boolean up = rng.nextBoolean();
            for (int s = 1; s <= len; s++) {
                int z = up ? row - s : row + width + s - 1;
                if (z >= 0 && z < N) set(bits, col, z, (byte) 0);
            }
        }
    }

    /** Open floor on a pillar grid; almost no walls. */
    private void buildHall(byte[] bits, boolean[] room, Random rng) {
        carveRect(bits, 1, 1, N - 2, N - 2);
        markRoom(room, 1, 1, N - 2, N - 2);
        int step = 3 + rng.nextInt(2);
        int phase = rng.nextInt(step);
        for (int x = 1 + phase; x < N - 1; x += step) {
            for (int z = 1 + phase; z < N - 1; z += step) {
                bits[x * N + z] |= BIT_PILLAR;
            }
        }
    }

    /** A landmark: one huge room, dense pillars, bright. */
    private void buildHub(byte[] bits, boolean[] room, Random rng) {
        int inset = 1 + rng.nextInt(2);
        carveRect(bits, inset, inset, N - inset * 2, N - inset * 2);
        markRoom(room, inset, inset, N - inset * 2, N - inset * 2);
        int step = 2 + rng.nextInt(2);
        int phase = rng.nextInt(step);
        for (int x = inset + phase + 1; x < N - inset - 1; x += step) {
            for (int z = inset + phase + 1; z < N - inset - 1; z += step) {
                bits[x * N + z] |= BIT_PILLAR;
            }
        }
    }

    /** The rare teaser: wet, dark, wrong-coloured, and it does not explain itself. */
    private void buildPoolroom(byte[] bits, boolean[] room, Random rng) {
        int w = 8 + rng.nextInt(5);
        int h = 8 + rng.nextInt(5);
        int x0 = (N - w) / 2;
        int z0 = (N - h) / 2;
        carveRect(bits, x0, z0, w, h);
        markRoom(room, x0, z0, w, h);
        for (int x = x0; x < x0 + w; x++) {
            for (int z = z0; z < z0 + h; z++) {
                int i = x * N + z;
                bits[i] = (byte) ((bits[i] & ~((byte) (SURFACE_MASK << SURFACE_SHIFT)))
                        | (byte) (SURFACE_DAMP << SURFACE_SHIFT));
            }
        }
        // One way in, off-centre, easy to walk past.
        int gx = x0 + 1 + rng.nextInt(w - 2);
        for (int z = 0; z < z0; z++) set(bits, gx, z, (byte) 0);
    }

    // ---- carving helpers -------------------------------------------------

    /**
     * Level 7 - Thalassophobia. The wiki's ocean: an open body of water (every tile open) broken only
     * by a scatter of rock islands. Nothing like the room-grid of the other levels - you swim.
     */
    private void buildOcean(byte[] bits, Random rng) {
        Arrays.fill(bits, (byte) 0);                       // all open water
        int islands = 3 + rng.nextInt(4);                  // 3-6 islands per district
        for (int i = 0; i < islands; i++) {
            int cx = 2 + rng.nextInt(N - 4);
            int cz = 2 + rng.nextInt(N - 4);
            int r = 1 + rng.nextInt(3);                    // radius 1-3
            stampDisc(bits, cx, cz, r, true);
        }
    }

    /**
     * Level 8 - Cave System. The wiki's caves: start as solid rock and bore a connected network of
     * meandering worm tunnels out from a central cavern, widening here and there into chambers.
     * Every tunnel emanates from the centre, so the whole system is one connected space.
     */
    private void buildCaves(byte[] bits, Random rng) {
        int cx = N / 2, cz = N / 2;
        stampDisc(bits, cx, cz, 2, false);                 // central cavern
        int worms = 5 + rng.nextInt(3);                    // 5-7 tunnels
        for (int w = 0; w < worms; w++) {
            double ang = rng.nextDouble() * Math.PI * 2;
            int x = cx, z = cz;
            int steps = N + rng.nextInt(N);
            for (int s = 0; s < steps; s++) {
                ang += (rng.nextDouble() - 0.5) * 1.1;     // meander
                int sx = (int) Math.round(Math.cos(ang));
                int sz = (int) Math.round(Math.sin(ang));
                if (sx == 0 && sz == 0) sx = 1;
                x = Math.max(1, Math.min(N - 2, x + sx));
                z = Math.max(1, Math.min(N - 2, z + sz));
                bits[x * N + z] &= (byte) ~BIT_SOLID;
                if (x + 1 <= N - 2) bits[(x + 1) * N + z] &= (byte) ~BIT_SOLID;  // 2-wide tunnel
                if (rng.nextInt(14) == 0) stampDisc(bits, x, z, 1 + rng.nextInt(2), false);
            }
        }
    }

    /** Fills (solid=true) or clears (solid=false) a rough disc of radius r at (cx,cz). */
    private static void stampDisc(byte[] bits, int cx, int cz, int r, boolean solid) {
        for (int x = Math.max(0, cx - r); x <= Math.min(N - 1, cx + r); x++) {
            for (int z = Math.max(0, cz - r); z <= Math.min(N - 1, cz + r); z++) {
                int dx = x - cx, dz = z - cz;
                if (dx * dx + dz * dz <= r * r) {
                    if (solid) bits[x * N + z] |= (byte) BIT_SOLID;
                    else bits[x * N + z] &= (byte) ~BIT_SOLID;
                }
            }
        }
    }

    private static void carveH(byte[] bits, int row, int width) {
        for (int x = 0; x < N; x++) {
            for (int w = 0; w < width; w++) {
                int z = row + w;
                if (z >= 0 && z < N) bits[x * N + z] &= (byte) ~BIT_SOLID;
            }
        }
    }

    private static void carveV(byte[] bits, int col, int width) {
        for (int z = 0; z < N; z++) {
            for (int w = 0; w < width; w++) {
                int x = col + w;
                if (x >= 0 && x < N) bits[x * N + z] &= (byte) ~BIT_SOLID;
            }
        }
    }

    private static void carveRect(byte[] bits, int x0, int z0, int w, int h) {
        for (int x = x0; x < x0 + w; x++) {
            for (int z = z0; z < z0 + h; z++) {
                if (x >= 0 && x < N && z >= 0 && z < N) bits[x * N + z] &= (byte) ~BIT_SOLID;
            }
        }
    }

    private static void markRoom(boolean[] room, int x0, int z0, int w, int h) {
        for (int x = x0; x < x0 + w; x++) {
            for (int z = z0; z < z0 + h; z++) {
                if (x >= 0 && x < N && z >= 0 && z < N) room[x * N + z] = true;
            }
        }
    }

    private static void set(byte[] bits, int x, int z, byte value) {
        if (x >= 0 && x < N && z >= 0 && z < N) bits[x * N + z] = value;
    }

    private static boolean allSolid(byte[] bits, int x0, int z0, int w, int h) {
        for (int x = x0; x < x0 + w; x++) {
            for (int z = z0; z < z0 + h; z++) {
                if (x < 0 || x >= N || z < 0 || z >= N) return false;
                if ((bits[x * N + z] & BIT_SOLID) == 0) return false;
            }
        }
        return true;
    }

    /**
     * Punches 1-2 doorways from a room's wall ring out into a corridor. A door is only carved where
     * the tile immediately outside the ring is already open <em>and</em> is not itself room
     * interior, so rooms open onto corridors rather than silently merging into each other.
     */
    private static void carveRoomDoors(byte[] bits, boolean[] room,
                                       int x0, int z0, int w, int h, Random rng) {
        if (w < 3 || h < 3) return;   // no non-corner perimeter to hang a door on
        int wanted = 1 + rng.nextInt(2);
        int attempts = 0;
        while (wanted > 0 && attempts++ < 32) {
            int px;
            int pz;
            int ox;
            int oz;
            // Door positions are deliberately kept off the corners. A corner tile of the wall ring
            // only touches the room's interior diagonally, so "carving a door" there opens nothing
            // and the room stays sealed.
            switch (rng.nextInt(4)) {
                case 0 -> { px = x0 + 1 + rng.nextInt(w - 2); pz = z0;         ox = px; oz = pz - 1; }
                case 1 -> { px = x0 + 1 + rng.nextInt(w - 2); pz = z0 + h - 1; ox = px; oz = pz + 1; }
                case 2 -> { px = x0; pz = z0 + 1 + rng.nextInt(h - 2);         ox = px - 1; oz = pz; }
                default -> { px = x0 + w - 1; pz = z0 + 1 + rng.nextInt(h - 2); ox = px + 1; oz = pz; }
            }
            if (ox < 0 || ox >= N || oz < 0 || oz >= N) continue;
            if ((bits[ox * N + oz] & BIT_SOLID) != 0) continue;   // outside is wall, not corridor
            if (room[ox * N + oz]) continue;                      // outside is another room
            if (px < 0 || px >= N || pz < 0 || pz >= N) continue;
            bits[px * N + pz] = (byte) ((bits[px * N + pz] & ~BIT_SOLID) | BIT_DOOR);
            wanted--;
        }
    }

    /**
     * Border gateways, derived from the shared edge hash so neighbouring districts always agree.
     * The connector tunnels straight inland until it reaches open floor, which is guaranteed
     * because the trunk corridors of every style span the district.
     */
    private void carveBorderGateways(byte[] bits, int dx, int dz) {
        for (int edge = 0; edge < 4; edge++) {
            long eh = edgeHash(dx, dz, edge);
            Random er = new Random(eh);
            int count = (Math.floorMod(eh >>> 17, 100) < 40) ? 2 : 1;
            int first = 2 + er.nextInt(N - 4);
            carveGateway(bits, edge, first);
            if (count == 2) {
                int second = 2 + er.nextInt(N - 4);
                if (Math.abs(second - first) < 4) second = (second + N / 2) % N;
                carveGateway(bits, edge, second);
            }
        }
    }

    private static void carveGateway(byte[] bits, int edge, int pos) {
        if (pos < 0) pos = 0;
        if (pos > N - 1) pos = N - 1;
        switch (edge) {
            case 0 -> { for (int z = 0; z < N && (bits[pos * N + z] & BIT_SOLID) != 0; z++) bits[pos * N + z] &= (byte) ~BIT_SOLID; }
            case 1 -> { for (int z = N - 1; z >= 0 && (bits[pos * N + z] & BIT_SOLID) != 0; z--) bits[pos * N + z] &= (byte) ~BIT_SOLID; }
            case 2 -> { for (int x = 0; x < N && (bits[x * N + pos] & BIT_SOLID) != 0; x++) bits[x * N + pos] &= (byte) ~BIT_SOLID; }
            default -> { for (int x = N - 1; x >= 0 && (bits[x * N + pos] & BIT_SOLID) != 0; x--) bits[x * N + pos] &= (byte) ~BIT_SOLID; }
        }
    }

    /** Canonical hash of the border between two districts; identical from either side. */
    public long edgeHash(int dx, int dz, int edge) {
        int ax = dx;
        int az = dz;
        int bx = dx;
        int bz = dz;
        switch (edge) {
            case 0 -> bz = dz - 1;
            case 1 -> bz = dz + 1;
            case 2 -> bx = dx - 1;
            default -> bx = dx + 1;
        }
        long lo;
        long hi;
        long a = (((long) ax) << 32) ^ (az & 0xFFFFFFFFL);
        long b = (((long) bx) << 32) ^ (bz & 0xFFFFFFFFL);
        if (Long.compareUnsigned(a, b) <= 0) {
            lo = a;
            hi = b;
        } else {
            lo = b;
            hi = a;
        }
        return mix(seed, drift, lo, hi ^ 0x5DEECE66DL);
    }

    // ---- imperfections ---------------------------------------------------

    /** One-tile recesses off corridors: the walls stop being perfectly straight. */
    private static void addAlcoves(byte[] bits, Random rng) {
        int count = 1 + rng.nextInt(4);
        int attempts = 0;
        while (count > 0 && attempts++ < 60) {
            int x = 1 + rng.nextInt(N - 2);
            int z = 1 + rng.nextInt(N - 2);
            if ((bits[x * N + z] & BIT_SOLID) == 0) continue;
            int openNeighbours = 0;
            if ((bits[(x - 1) * N + z] & BIT_SOLID) == 0) openNeighbours++;
            if ((bits[(x + 1) * N + z] & BIT_SOLID) == 0) openNeighbours++;
            if ((bits[x * N + (z - 1)] & BIT_SOLID) == 0) openNeighbours++;
            if ((bits[x * N + (z + 1)] & BIT_SOLID) == 0) openNeighbours++;
            if (openNeighbours != 1) continue;   // exactly one opening keeps it a recess, not a hole
            bits[x * N + z] &= (byte) ~BIT_SOLID;
            count--;
        }
    }

    /** Short corridors that go nowhere. Purely psychological. */
    private static void addDeadEnds(byte[] bits, Random rng) {
        int count = rng.nextInt(3);
        int attempts = 0;
        while (count > 0 && attempts++ < 60) {
            int x = 1 + rng.nextInt(N - 2);
            int z = 1 + rng.nextInt(N - 2);
            if ((bits[x * N + z] & BIT_SOLID) == 0) continue;
            int openNeighbours = 0;
            if ((bits[(x - 1) * N + z] & BIT_SOLID) == 0) openNeighbours++;
            if ((bits[(x + 1) * N + z] & BIT_SOLID) == 0) openNeighbours++;
            if ((bits[x * N + (z - 1)] & BIT_SOLID) == 0) openNeighbours++;
            if ((bits[x * N + (z + 1)] & BIT_SOLID) == 0) openNeighbours++;
            if (openNeighbours != 1) continue;
            bits[x * N + z] &= (byte) ~BIT_SOLID;
            int dir = rng.nextInt(4);
            int len = 1 + rng.nextInt(3);
            for (int s = 1; s <= len; s++) {
                int nx = x + (dir == 0 ? s : dir == 1 ? -s : 0);
                int nz = z + (dir == 2 ? s : dir == 3 ? -s : 0);
                if (nx <= 0 || nx >= N - 1 || nz <= 0 || nz >= N - 1) break;
                bits[nx * N + nz] &= (byte) ~BIT_SOLID;
            }
            count--;
        }
    }

    /** Free-standing columns inside rooms. Never placed in a 1-wide corridor. */
    private static void addPillars(byte[] bits, boolean[] room, Random rng, Style style) {
        if (style == Style.HALL || style == Style.HUB) return;   // already gridded
        int chance = style == Style.ORGANIC ? 12 : 7;
        for (int x = 1; x < N - 1; x++) {
            for (int z = 1; z < N - 1; z++) {
                int i = x * N + z;
                if ((bits[i] & BIT_SOLID) != 0) continue;
                if (!room[i]) continue;
                if ((bits[(x - 1) * N + z] & BIT_SOLID) == 0) continue;
                if ((bits[(x + 1) * N + z] & BIT_SOLID) == 0) continue;
                if ((bits[x * N + (z - 1)] & BIT_SOLID) == 0) continue;
                if ((bits[x * N + (z + 1)] & BIT_SOLID) == 0) continue;
                if (rng.nextInt(100) < chance) bits[i] |= BIT_PILLAR;
            }
        }
    }

    /**
     * Geometry with no structural reason to exist: wall fragments floating in open rooms, and a
     * sealed box inside a room. Reads as "this building was not designed, it accreted".
     */
    private static void addImpossibleGeometry(byte[] bits, Random rng) {
        int count = 1 + rng.nextInt(3);
        int attempts = 0;
        while (count > 0 && attempts++ < 80) {
            int x = 2 + rng.nextInt(N - 4);
            int z = 2 + rng.nextInt(N - 4);
            if ((bits[x * N + z] & BIT_SOLID) != 0) continue;
            if (rng.nextBoolean()) {
                // A wall stub that meets nothing.
                bits[x * N + z] |= BIT_SOLID;
                int len = 1 + rng.nextInt(2);
                boolean horizontal = rng.nextBoolean();
                for (int s = 1; s <= len; s++) {
                    int nx = x + (horizontal ? s : 0);
                    int nz = z + (horizontal ? 0 : s);
                    if (nx < N - 1 && nz < N - 1) bits[nx * N + nz] |= BIT_SOLID;
                }
            } else {
                // A sealed box. No door. Deliberately unreachable.
                int w = 2 + rng.nextInt(2);
                int h = 2 + rng.nextInt(2);
                if (x + w >= N - 1 || z + h >= N - 1) continue;
                for (int bx = x; bx <= x + w; bx++) {
                    for (int bz = z; bz <= z + h; bz++) {
                        boolean border = bx == x || bz == z || bx == x + w || bz == z + h;
                        if (border) bits[bx * N + bz] |= BIT_SOLID;
                        else bits[bx * N + bz] &= (byte) ~BIT_SOLID;
                    }
                }
            }
            count--;
        }
    }

    // ---- lighting & surfaces --------------------------------------------

    /**
     * Lights sit on a jittered lattice. Reliability decides whether each fixture works, buzzes, or
     * is dead; surfaces pick up stains and water damage, weighted towards corners and the deeper
     * parts of the level.
     */
    private void applyLightingAndSurfaces(byte[] bits, int dx, int dz,
                                          int spacing, int offX, int offZ, Style style) {
        for (int x = 0; x < N; x++) {
            for (int z = 0; z < N; z++) {
                int i = x * N + z;
                int b = bits[i] & 0xFF;
                if ((b & BIT_SOLID) != 0) {
                    bits[i] = (byte) (b & ~(LIGHT_MASK << LIGHT_SHIFT));
                } else {
                    int wx = dx * N + x;
                    int wz = dz * N + z;
                    boolean onLattice = Math.floorMod(wx - offX, spacing) == 0
                            && Math.floorMod(wz - offZ, spacing) == 0;
                    int light = LIGHT_NONE;
                    if (onLattice) {
                        float rel = reliabilityFor(wx, wz, dx, dz, style);
                        long h = mix(seed, drift ^ 0x2545F4914F6CDD1DL, wx, wz);
                        double roll = (Math.floorMod(h >>> 13, 10000) / 10000.0);
                        if (style == Style.POOLROOM) {
                            light = LIGHT_NONE;
                        } else if (style == Style.DARK) {
                            light = roll < 0.12 ? LIGHT_FLICKERING : LIGHT_DEAD;
                        } else if (roll < 0.55 + 0.40 * rel) {
                            light = LIGHT_WORKING;
                        } else if (roll < 0.68 + 0.25 * rel) {
                            light = LIGHT_FLICKERING;
                        } else {
                            light = LIGHT_DEAD;
                        }
                    }
                    b = (b & ~(LIGHT_MASK << LIGHT_SHIFT)) | (light << LIGHT_SHIFT);
                }

                // Stains: heavier near walls and in corners, heavier still the deeper you are.
                int wx = dx * N + x;
                int wz = dz * N + z;
                long sh = mix(seed ^ 0x9E3779B97F4A7C15L, drift, wx, wz);
                double sRoll = Math.floorMod(sh >>> 21, 10000) / 10000.0;
                int surface = SURFACE_CLEAN;
                int wallNeighbours = 0;
                if (x > 0 && (bits[(x - 1) * N + z] & BIT_SOLID) != 0) wallNeighbours++;
                if (x < N - 1 && (bits[(x + 1) * N + z] & BIT_SOLID) != 0) wallNeighbours++;
                if (z > 0 && (bits[x * N + (z - 1)] & BIT_SOLID) != 0) wallNeighbours++;
                if (z < N - 1 && (bits[x * N + (z + 1)] & BIT_SOLID) != 0) wallNeighbours++;
                double stainChance = 0.05 + 0.07 * wallNeighbours;
                if (sRoll < stainChance * 0.45) {
                    surface = SURFACE_MOULDY;
                } else if (sRoll < stainChance) {
                    surface = SURFACE_DAMP;
                } else if (sRoll < stainChance + 0.13) {
                    surface = SURFACE_STAINED;
                }
                bits[i] = (byte) ((b & ~(SURFACE_MASK << SURFACE_SHIFT)) | (surface << SURFACE_SHIFT));
            }
        }
    }

    // ---- hashing ---------------------------------------------------------

    /** SplitMix64-style avalanche. Deterministic across JVMs and across the Python port. */
    public static long mix(long a, long b, long c, long d) {
        long h = 0x9E3779B97F4A7C15L;
        h = (h ^ a) * 0xBF58476D1CE4E5B9L;
        h = Long.rotateLeft(h, 31);
        h = (h ^ b) * 0x94D049BB133111EBL;
        h = Long.rotateLeft(h, 27);
        h = (h ^ c) * 0xBF58476D1CE4E5B9L;
        h = Long.rotateLeft(h, 31);
        h = (h ^ d) * 0x94D049BB133111EBL;
        h = Long.rotateLeft(h, 27);
        h ^= h >>> 33;
        h *= 0xFF51AFD7ED558CCDL;
        h ^= h >>> 33;
        h *= 0xC4CEB9FE1A85EC53L;
        h ^= h >>> 33;
        return h;
    }

    private static float clamp01(float v) {
        return v < 0f ? 0f : (v > 1f ? 1f : v);
    }
}
