package dev.qynl.backrooms.level;

import dev.qynl.backrooms.BackroomsMod;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

/**
 * Everything that makes one Backrooms level <em>that</em> level: its dimension, its biome, its palette.
 * The layout grammar in {@code Level0Layout} is level-agnostic; a new level is a new theme plus a
 * data-driven dimension/biome pair, so Level 1, 2, ... slot in without touching the generator.
 */
public record LevelTheme(int id, String name, String dimensionPath, String biomePath,
                         int fogColor, int skyColor) {

    public RegistryKey<World> dimensionKey() {
        return RegistryKey.of(RegistryKeys.WORLD, Identifier.of(BackroomsMod.MOD_ID, dimensionPath));
    }

    public Identifier biomeId() {
        return Identifier.of(BackroomsMod.MOD_ID, biomePath);
    }
}
