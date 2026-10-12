package dev.qynl.backrooms.registry;

import com.mojang.serialization.MapCodec;
import dev.qynl.backrooms.BackroomsMod;
import dev.qynl.backrooms.level.BackroomsChunkGenerator;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

/**
 * Registers the mod's chunk generator codec, referenced as {@code "type": "backrooms:level_gen"}
 * by every {@code data/backrooms/dimension/levelN.json}.
 */
public final class ModChunkGenerators {

    public static final MapCodec<BackroomsChunkGenerator> LEVEL_GEN =
            Registry.register(Registries.CHUNK_GENERATOR,
                    Identifier.of(BackroomsMod.MOD_ID, "level_gen"),
                    BackroomsChunkGenerator.CODEC);

    private ModChunkGenerators() {
    }
}
