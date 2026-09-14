package dev.qynl.backrooms;

import dev.qynl.backrooms.level0.Level0Layout;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.Deque;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the invariants that {@code tools/layout_preview.py} measures over the design, but executed
 * against the shipped Java core. Kept cheap on purpose so the suite runs in a couple of seconds.
 */
public class Level0LayoutTest {

    private static final int N = Level0Layout.DISTRICT_TILES;

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
