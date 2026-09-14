package dev.qynl.backrooms.level0;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Owns one {@link Level0Layout} per world seed, plus the current reality drift for that seed.
 * Chunk generation and the horror director both go through here so they always agree on the same
 * solved layout.
 */
public final class Level0Holder {

    private static final Map<Long, Level0Layout> LAYOUTS = new ConcurrentHashMap<>();
    private static final Map<Long, Long> DRIFT = new ConcurrentHashMap<>();

    public static Level0Layout get(long seed) {
        Level0Layout layout = LAYOUTS.computeIfAbsent(seed, Level0Layout::new);
        long drift = DRIFT.getOrDefault(seed, 0L);
        if (layout.drift() != drift) {
            layout.setDrift(drift);
        }
        return layout;
    }

    public static long drift(long seed) {
        return DRIFT.getOrDefault(seed, 0L);
    }

    /** Advances the drift for a seed and re-solves its layout. Loaded chunks keep their blocks. */
    public static void advanceDrift(long seed) {
        DRIFT.merge(seed, 1L, Long::sum);
        Level0Layout layout = LAYOUTS.get(seed);
        if (layout != null) {
            layout.setDrift(DRIFT.get(seed));
        }
    }

    private Level0Holder() {
    }
}
