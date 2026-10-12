package dev.qynl.backrooms.level;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.qynl.backrooms.level0.BackroomsBuildFeature;
import net.minecraft.world.gen.StructureAccessor;
import net.minecraft.world.ChunkRegion;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.chunk.FlatChunkGenerator;
import net.minecraft.world.gen.chunk.FlatChunkGeneratorConfig;
import net.minecraft.world.gen.noise.NoiseConfig;

/**
 * The Backrooms level generator: a superflat world whose bedrock shell is laid by
 * {@link FlatChunkGenerator}, with the level itself painted in the same pass.
 *
 * <p>Why not rely on biome decoration: a flat generator only runs biome features when its config
 * asks for it, on its own schedule, and - as shipped for a while - that left every level as a bare
 * bedrock floor. {@link #buildSurface} is the pass that lays that bedrock, so it cannot be skipped
 * by config or ordering; painting the level right after it makes a generated chunk correct by
 * construction, on the generation thread, before the chunk is ever saved.
 *
 * <p>{@code ChunkRegion} implements {@code ServerWorldAccess}, so the same paint path is used
 * during worldgen and by the live-world repair hooks.
 *
 * <p>{@code BackroomsLevelBuilder}'s chunk-load verifier stays as a second net: it repairs chunks
 * that older builds already saved as bedrock.
 */
public class BackroomsChunkGenerator extends FlatChunkGenerator {

    public static final MapCodec<BackroomsChunkGenerator> CODEC = RecordCodecBuilder.mapCodec(inst ->
            inst.group(
                    FlatChunkGeneratorConfig.CODEC.fieldOf("settings").forGetter(g -> g.config),
                    Codec.INT.fieldOf("level").forGetter(g -> g.levelId)
            ).apply(inst, BackroomsChunkGenerator::new));

    private final FlatChunkGeneratorConfig config;
    private final int levelId;

    public BackroomsChunkGenerator(FlatChunkGeneratorConfig config, int levelId) {
        super(config);
        this.config = config;
        this.levelId = levelId;
    }

    @Override
    protected MapCodec<? extends ChunkGenerator> getCodec() {
        return CODEC;
    }

    @Override
    public void buildSurface(ChunkRegion world, StructureAccessor structures,
                             NoiseConfig noiseConfig, Chunk chunk) {
        super.buildSurface(world, structures, noiseConfig, chunk);   // the bedrock shell
        new BackroomsBuildFeature(levelId).paintChunk(world, chunk.getPos().x, chunk.getPos().z);
    }
}
