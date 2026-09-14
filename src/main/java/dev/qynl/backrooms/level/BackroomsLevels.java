package dev.qynl.backrooms.level;

import java.util.HashMap;
import java.util.Map;

/**
 * The level registry. Levels 0, 1 and 2 ship today; the mod is organised so that a further level is:
 *
 * <ol>
 *   <li>a new {@link LevelTheme} registered here,</li>
 *   <li>a data-driven pair {@code data/backrooms/dimension/&lt;path&gt;.json} and
 *       {@code worldgen/biome/&lt;path&gt;.json} (the biome lists {@code backrooms:level0_build} or a
 *       new build feature),</li>
 *   <li>optionally a different palette/grammar hook.</li>
 * </ol>
 *
 * Nothing else changes &mdash; entry, drift, horror and lighting are all level-agnostic.
 */
public final class BackroomsLevels {

    public static final LevelTheme LEVEL_0 = new LevelTheme(0, "Level 0", "level0", "level0", 12497528, 12497528);

    /** Level 1 - "Habitable Zone": damp concrete warehouse halls. */
    public static final LevelTheme LEVEL_1 = new LevelTheme(1, "Level 1", "level1", "level1", 7827824, 5526612);

    /** Level 2 - "Abandoned Utility Halls": narrow brick-and-steel service tunnels. */
    public static final LevelTheme LEVEL_2 = new LevelTheme(2, "Level 2", "level2", "level2", 1443094, 789516);

    private static final Map<Integer, LevelTheme> LEVELS = new HashMap<>();

    static {
        register(LEVEL_0);
        register(LEVEL_1);
        register(LEVEL_2);
    }

    public static void register(LevelTheme theme) {
        LEVELS.put(theme.id(), theme);
    }

    public static LevelTheme get(int id) {
        return LEVELS.get(id);
    }

    public static boolean isRegistered(int id) {
        return LEVELS.containsKey(id);
    }

    public static java.util.Collection<LevelTheme> all() {
        return LEVELS.values();
    }

    private BackroomsLevels() {
    }
}
