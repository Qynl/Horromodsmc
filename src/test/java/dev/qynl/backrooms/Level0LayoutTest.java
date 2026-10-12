package dev.qynl.backrooms;

import dev.qynl.backrooms.level0.BackroomsSpawn;
import dev.qynl.backrooms.level0.Level0Layout;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the invariants that {@code tools/layout_preview.py} measures over the design, but executed
 * against the shipped Java core. Kept cheap on purpose so the suite runs in a couple of seconds.
 */
public class Level0LayoutTest {

    private static final int N = Level0Layout.DISTRICT_TILES;

    @Test
    void hashFunctionMatchesThePythonPreviewPort() {
        // tools/layout_preview.py is the design-review port of this class; it used to pack the two
        // coordinates into one hash argument instead of passing them separately, which silently made
        // it preview a different world from the one the mod generates. These vectors are the contract
        // between the two files: if either side changes the mixer, both this test and the port's
        // --selftest have to be updated together.
        assertEquals(-4673721920094353327L, Level0Layout.mix(0L, 0L, 0L, 0L));
        assertEquals(-5797716211826552959L, Level0Layout.mix(1234567L, 0L, 0L, 0L));
        assertEquals(-3269322766031515143L, Level0Layout.mix(1234567L, 0L, 3L, -7L));
        assertEquals(4815040972024048069L, Level0Layout.mix(-1L, 2L, 17L, 99L));
    }

    @Test
    void deterministicForSeed() {
        Level0Layout a = new Level0Layout(1234567L);
        Level0Layout b = new Level0Layout(1234567L);
        int[][] probes = {{0, 0}, {17, -33}, {512, 512}, {-777, 42}};
        for (int[] p : probes) {
            assertEquals(a.tileBits(p[0], p[1]), b.tileBits(p[0], p[1]),
                    "same seed must give the same column at " + p[0] + "," + p[1]);
        }
    }

    @Test
    void differentSeedsDiffer() {
        Level0Layout a = new Level0Layout(1L);
        Level0Layout b = new Level0Layout(2L);
        int diff = 0;
        for (int x = -30; x < 30; x++) {
            for (int z = -30; z < 30; z++) {
                if (a.isSolid(x, z) != b.isSolid(x, z)) diff++;
            }
        }
        assertTrue(diff > 100, "different seeds should produce different layouts");
    }

    @Test
    void driftResolvesTheLevel() {
        Level0Layout a = new Level0Layout(424242L);
        Level0Layout b = new Level0Layout(424242L, 1L);
        int diff = 0;
        for (int x = -30; x < 30; x++) {
            for (int z = -30; z < 30; z++) {
                if (a.isSolid(x, z) != b.isSolid(x, z)) diff++;
            }
        }
        assertTrue(diff > 100, "advancing drift must change the layout");
    }

    @Test
    void everyDistrictOpensOntoAllNeighbours() {
        Level0Layout layout = new Level0Layout(987654321L);
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                int x0 = dx * N;
                int z0 = dz * N;
                boolean north = false, south = false, west = false, east = false;
                for (int i = 0; i < N; i++) {
                    north |= !layout.isSolid(x0 + i, z0);
                    south |= !layout.isSolid(x0 + i, z0 + N - 1);
                    west |= !layout.isSolid(x0, z0 + i);
                    east |= !layout.isSolid(x0 + N - 1, z0 + i);
                }
                assertTrue(north && south && west && east,
                        "district " + dx + "," + dz + " must be open on all four borders");
            }
        }
    }

    /** Seeds used by the arrival-promise tests; fixed so a failure is reproducible. */
    private static final long[] ARRIVAL_SEEDS = {4242L, 1234567L, 987654321L, 31337L, -77L};

    @Test
    void pillarDistrictsCarryFreeStandingPillars() {
        // Regression: an inverted neighbour test once made pillars require four *solid* neighbours,
        // i.e. a sealed pocket, so none ever generated outside HALL/HUB districts. Pillars come only
        // from those two styles, so the invariants are stated per district rather than as one magic
        // count over a window that may contain no pillar districts at all.
        Level0Layout layout = new Level0Layout(1234567L);
        int pillarDistricts = 0;
        int pillars = 0;
        int smallest = Integer.MAX_VALUE;
        for (int dx = -4; dx < 4; dx++) {
            for (int dz = -4; dz < 4; dz++) {
                Level0Layout.Style style = layout.style(dx * N, dz * N);
                if (style != Level0Layout.Style.HALL && style != Level0Layout.Style.HUB) {
                    continue;
                }
                pillarDistricts++;
                int here = 0;
                for (int x = dx * N; x < dx * N + N; x++) {
                    for (int z = dz * N; z < dz * N + N; z++) {
                        if (layout.isSolid(x, z) || !layout.isPillar(x, z)) {
                            continue;
                        }
                        here++;
                        pillars++;
                        // Free-standing means you can walk around it. Pillars against the district's
                        // own wall ring lose one or two sides, so two open sides is the floor and a
                        // fully sealed one is the bug.
                        int open = (layout.isWalkable(x - 1, z) ? 1 : 0)
                                + (layout.isWalkable(x + 1, z) ? 1 : 0)
                                + (layout.isWalkable(x, z - 1) ? 1 : 0)
                                + (layout.isWalkable(x, z + 1) ? 1 : 0);
                        assertTrue(open >= 2,
                                "pillar at " + x + "," + z + " is sealed in (" + open + " open sides)");
                    }
                }
                assertTrue(here >= 6, "a pillar district must actually carry pillars, got " + here);
                smallest = Math.min(smallest, here);
            }
        }
        assertTrue(pillarDistricts >= 4,
                "an 8x8 district region must contain pillar districts, got " + pillarDistricts);
        // Measured on the shipped layout: 9 pillar districts and 157 pillars in this region.
        assertTrue(pillars > 50, "expected a landmark-rich maze, got " + pillars + " pillars");
    }

    @Test
    void youNeverArriveSomewhereUnlitOrIllegible() {
        // Noclip-in is the player's first sight of the mod. A dark, drowned or non-euclidean arrival
        // district reads as a broken world, so the style at the origin is constrained.
        for (long seed : ARRIVAL_SEEDS) {
            Level0Layout layout = new Level0Layout(seed);
            Level0Layout.Style arrival = layout.style(0, 0);
            assertNotEquals(Level0Layout.Style.DARK, arrival, "seed " + seed + ": you arrive in the dark");
            assertNotEquals(Level0Layout.Style.POOLROOM, arrival, "seed " + seed + ": you arrive underwater");
            assertNotEquals(Level0Layout.Style.IMPOSSIBLE, arrival,
                    "seed " + seed + ": you arrive in broken geometry");
            assertTrue(layout.reliability(0, 0) >= 0.85f,
                    "seed " + seed + ": lighting at the arrival point must be reliable, got "
                            + layout.reliability(0, 0));
        }
    }

    @Test
    void lightingIsReliableNearTheArrivalPoint() {
        // Measured over the whole arrival disc and several seeds: per-fixture rolls are noisy, one
        // district is not a sample. Design target is >= 85% lit and >= 75% fully working inside
        // ARRIVAL_RADIUS; the shipped layout measures ~95% and ~86%.
        int fixtures = 0;
        int lit = 0;
        int working = 0;
        int r = Level0Layout.ARRIVAL_RADIUS;
        for (long seed : ARRIVAL_SEEDS) {
            Level0Layout layout = new Level0Layout(seed);
            for (int x = -r; x <= r; x++) {
                for (int z = -r; z <= r; z++) {
                    if (x * x + z * z > r * r) {
                        continue;
                    }
                    int light = layout.lightState(x, z);
                    if (light == Level0Layout.LIGHT_NONE) {
                        continue;
                    }
                    fixtures++;
                    if (light == Level0Layout.LIGHT_WORKING) {
                        working++;
                        lit++;
                    } else if (light == Level0Layout.LIGHT_FLICKERING) {
                        lit++;
                    }
                }
            }
        }
        assertTrue(fixtures > 200, "the arrival area should carry fixtures, got " + fixtures);
        assertTrue(lit * 20 >= fixtures * 17,
                "at least 85% of arrival fixtures must give light, got " + lit + "/" + fixtures);
        assertTrue(working * 4 >= fixtures * 3,
                "at least 75% of arrival fixtures must actually work, got " + working + "/" + fixtures);
    }

    @Test
    void lightingGivesUpWithDepth() {
        // The other half of the promise: reliability is a function of distance, so the arrival area
        // being lit must not mean the whole level is lit.
        int nearFixtures = 0, nearLit = 0, deepFixtures = 0, deepLit = 0;
        for (long seed : ARRIVAL_SEEDS) {
            Level0Layout layout = new Level0Layout(seed);
            assertTrue(layout.reliability(0, 0) - layout.reliability(4000, 4000) > 0.30f,
                    "seed " + seed + ": reliability must fall off with distance, got "
                            + layout.reliability(0, 0) + " -> " + layout.reliability(4000, 4000));
            int[] near = litFixtures(layout, 0, 0, 32);
            int[] deep = litFixtures(layout, 4000, 4000, 32);
            nearFixtures += near[0];
            nearLit += near[1];
            deepFixtures += deep[0];
            deepLit += deep[1];
        }
        assertTrue(nearFixtures > 100 && deepFixtures > 100, "both samples need fixtures to compare");
        double nearRatio = (double) nearLit / nearFixtures;
        double deepRatio = (double) deepLit / deepFixtures;
        assertTrue(nearRatio - deepRatio > 0.15,
                "the deep level must be visibly less reliable than the arrival area: "
                        + nearRatio + " vs " + deepRatio);
    }

    /** {@code [fixtures, fixtures that give any light]} in a disc. */
    private static int[] litFixtures(Level0Layout layout, int cx, int cz, int r) {
        int fixtures = 0;
        int lit = 0;
        for (int x = cx - r; x <= cx + r; x++) {
            for (int z = cz - r; z <= cz + r; z++) {
                int dx = x - cx;
                int dz = z - cz;
                if (dx * dx + dz * dz > r * r) {
                    continue;
                }
                int light = layout.lightState(x, z);
                if (light == Level0Layout.LIGHT_NONE) {
                    continue;
                }
                fixtures++;
                if (light == Level0Layout.LIGHT_WORKING || light == Level0Layout.LIGHT_FLICKERING) {
                    lit++;
                }
            }
        }
        return new int[]{fixtures, lit};
    }

    @Test
    void arrivalAndEntitySpawnPointsAreNeverInsideAPillar() {
        // Regression: BackroomsSpawn only tested isSolid, and a pillar tile is open floor with a
        // column through the walking space - players and entities were placed inside the column.
        Random probe = new Random(20240612L);
        for (long seed : ARRIVAL_SEEDS) {
            Level0Layout layout = new Level0Layout(seed);
            for (int i = 0; i < 60; i++) {
                int tx = probe.nextInt(300) - 150;
                int tz = probe.nextInt(300) - 150;
                BlockPos pos = BackroomsSpawn.find(layout, tx, tz);
                assertTrue(layout.isWalkable(pos.getX(), pos.getZ()),
                        "seed " + seed + ": spawn for target " + tx + "," + tz + " landed at "
                                + pos.getX() + "," + pos.getZ() + ", which is not walkable");
            }
        }
    }

    @Test
    void clutterIsSparseButPresent() {
        Level0Layout layout = new Level0Layout(99L);
        int r = 4 * N;
        int open = 0;
        int props = 0;
        for (int x = -r / 2; x < r / 2; x++) {
            for (int z = -r / 2; z < r / 2; z++) {
                if (layout.isSolid(x, z)) continue;
                open++;
                if (layout.propAt(x, z) != Level0Layout.PROP_NONE) props++;
            }
        }
        assertTrue(props > 20, "an abandoned level still has things left in it, got " + props);
        assertTrue(props * 100 <= open * 12,
                "clutter must stay sparse (a furniture store is not horror): " + props + "/" + open);
    }

    @Test
    void regionIsMostlyConnected() {
        Level0Layout layout = new Level0Layout(1234567L);
        int R = 4 * N;
        int ox = -R / 2;
        boolean[][] solid = new boolean[R][R];
        int open = 0;
        for (int x = 0; x < R; x++) {
            for (int z = 0; z < R; z++) {
                solid[x][z] = layout.isSolid(ox + x, ox + z);
                if (!solid[x][z]) open++;
            }
        }
        boolean[][] seen = new boolean[R][R];
        Deque<int[]> queue = new ArrayDeque<>();
        // seed from the first open tile
        outer:
        for (int x = 0; x < R; x++) {
            for (int z = 0; z < R; z++) {
                if (!solid[x][z]) {
                    queue.add(new int[]{x, z});
                    seen[x][z] = true;
                    break outer;
                }
            }
        }
        int reached = 0;
        int[][] dirs = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
        while (!queue.isEmpty()) {
            int[] c = queue.poll();
            reached++;
            for (int[] d : dirs) {
                int nx = c[0] + d[0];
                int nz = c[1] + d[1];
                if (nx >= 0 && nx < R && nz >= 0 && nz < R && !seen[nx][nz] && !solid[nx][nz]) {
                    seen[nx][nz] = true;
                    queue.add(new int[]{nx, nz});
                }
            }
        }
        assertTrue(reached > open * 0.95,
                "the largest connected space should cover nearly all open floor (got "
                        + reached + "/" + open + ")");
    }
}
