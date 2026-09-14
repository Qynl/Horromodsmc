package dev.qynl.backrooms.level0;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Owns one {@link Level0Layout} per (world seed, Backrooms level), plus the reality drift for it.
 * Chunk generation and the horror director both go through here so they always agree on the same
 * solved layout. Each Backrooms level (0, 1, 2, ...) is a separate deterministic maze.
 */
public final class Level0Holder {

    private record Key(long seed, int level) {}

    private static final Map<Key, Level0Layout> LAYOUTS = new ConcurrentHashMap<>();
    private static final Map<Long, Long> DRIFT = new ConcurrentHashMap<>();

    public static Level0Layout get(long seed) {
        return get(seed, 0);
    }

    public static Level0Layout get(long seed, int level) {
        Level0Layout layout = LAYOUTS.computeIfAbsent(new Key(seed, level),
                k -> new Level0Layout(k.seed(), 0, k.level()));
        long drift = drift(seed, level);
        if (layout.drift() != drift) {
            layout.setDrift(drift);
        }
        return layout;
    }

    public static long drift(long seed) {
        return drift(seed, 0);
    }

    public static long drift(long seed, int level) {
        return DRIFT.getOrDefault(driftKey(seed, level), 0L);
    }

    public static void advanceDrift(long seed) {
        advanceDrift(seed, 0);
    }

    /** Advances the drift for a (seed, level) and re-solves its layout. Loaded chunks keep their blocks. */
    public static void advanceDrift(long seed, int level) {
        long key = driftKey(seed, level);
        DRIFT.merge(key, 1L, Long::sum);
        Level0Layout layout = LAYOUTS.get(new Key(seed, level));
        if (layout != null) {
            layout.setDrift(DRIFT.get(key));
        }
    }

    private static long driftKey(long seed, int level) {
        return seed ^ (((long) level) * 0x9E3779B97F4A7C15L);
    }

    private Level0Holder() {
    }
}
