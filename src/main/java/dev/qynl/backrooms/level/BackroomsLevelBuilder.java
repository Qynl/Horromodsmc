package dev.qynl.backrooms.level;

import dev.qynl.backrooms.level0.BackroomsBuildFeature;
import dev.qynl.backrooms.level0.Level0Layout;
import dev.qynl.backrooms.registry.ModBlocks;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.minecraft.block.BlockState;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.WorldChunk;

/**
 * Guarantees the Backrooms levels are actually built.
 *
 * <p>Worldgen already paints every chunk through the biome's build feature, so this is only a
 * safety net: it runs when a chunk of a Backrooms level <em>loads</em> (not every tick) and, if the
 * chunk does not look like the level, paints it once from the deterministic layout. That also
 * repairs worlds created by older builds of this mod, whose Backrooms chunks were saved as bare
 * bedrock.
 *
 * <p>The old implementation repainted a 3x3 area around every player on every server tick on a live
 * world and kept an unbounded static set of "already built" chunk keys forever, which cost a lot of
 * TPS and leaked memory for the whole session.
 */
public final class BackroomsLevelBuilder {

    /** Registers the chunk-load hook. Called once from the mod initializer. */
    public static void register() {
        ServerChunkEvents.CHUNK_LOAD.register(BackroomsLevelBuilder::onChunkLoad);
    }

    private static void onChunkLoad(ServerWorld world, WorldChunk chunk) {
        int level = levelOf(world.getRegistryKey());
        if (level < 0) {
            return;
        }
        int cx = chunk.getPos().x;
        int cz = chunk.getPos().z;
        if (looksBuilt(world, cx, cz, level)) {
            return;
        }
        BackroomsBuildFeature.buildChunk(world, level, cx, cz);
    }

    /**
     * Samples a few columns of the chunk. A built chunk has one of this level's floor blocks at
     * {@link Level0Layout#FLOOR_Y}; an unbuilt (or pre-fix, bedrock-only) chunk does not.
     */
    private static boolean looksBuilt(ServerWorld world, int cx, int cz, int level) {
        int baseX = cx << 4;
        int baseZ = cz << 4;
        BlockPos.Mutable mut = new BlockPos.Mutable();
        for (int i = 0; i < 4; i++) {
            int x = baseX + ((i * 5 + 3) & 15);
            int z = baseZ + ((i * 7 + 11) & 15);
            BlockState floor = world.getBlockState(mut.set(x, Level0Layout.FLOOR_Y, z));
            if (isFloorOf(level, floor)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isFloorOf(int level, BlockState state) {
        if (level == 4) return state.isOf(ModBlocks.OFFICE_CARPET);
        if (level == 5 || level == 7) return state.isOf(ModBlocks.HOTEL_CARPET);
        if (level == 0) return state.isOf(ModBlocks.CARPET);
        return state.isOf(ModBlocks.CONCRETE);
    }

    private static int levelOf(RegistryKey<World> key) {
        for (LevelTheme theme : BackroomsLevels.all()) {
            if (theme.dimensionKey().equals(key)) {
                return theme.id();
            }
        }
        return -1;
    }

    private BackroomsLevelBuilder() {
    }
}
