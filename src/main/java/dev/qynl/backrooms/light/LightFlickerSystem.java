package dev.qynl.backrooms.light;

import dev.qynl.backrooms.level.BackroomsLevels;
import dev.qynl.backrooms.level.LevelTheme;
import dev.qynl.backrooms.registry.ModBlocks;
import dev.qynl.backrooms.registry.ModSoundEvents;
import net.minecraft.block.BlockState;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;

import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gives the "flickering" fixtures their behaviour. The layout core marks some lights as FLICKERING;
 * the build feature registers their positions here, and this system toggles a sparse, random subset
 * of the ones near a player so the hum and the light both stutter.
 *
 * <p>Positions are bucketed by chunk and pruned when their chunk unloads, so the index stays
 * proportional to what is actually loaded instead of growing for the whole session; and each tick
 * only ever looks at the chunks immediately around a player, so the cost is bounded by the player
 * count rather than by how much of an endless level has ever been generated.
 */
public final class LightFlickerSystem {

    /** Chunks kept around each player when choosing lights to flicker. */
    private static final int RADIUS = 2;
    /** Hard cap per dimension so a pathological world can never blow up the index. */
    private static final int MAX_PER_DIMENSION = 20_000;

    private static final Map<RegistryKey<World>, Map<Long, Set<BlockPos>>> FLICKERS = new ConcurrentHashMap<>();

    public static void registerFlicker(RegistryKey<World> dim, BlockPos pos) {
        Map<Long, Set<BlockPos>> byChunk =
                FLICKERS.computeIfAbsent(dim, k -> new ConcurrentHashMap<>());
        long key = ChunkPos.toLong(pos.getX() >> 4, pos.getZ() >> 4);
        Set<BlockPos> set = byChunk.computeIfAbsent(key, k -> ConcurrentHashMap.newKeySet());
        if (set.size() < MAX_PER_DIMENSION) {
            set.add(pos.toImmutable());
        }
    }

    /** Drops the index entries of a chunk that is no longer loaded. */
    public static void onChunkUnload(RegistryKey<World> dim, long chunkKey) {
        Map<Long, Set<BlockPos>> byChunk = FLICKERS.get(dim);
        if (byChunk != null) {
            byChunk.remove(chunkKey);
        }
    }

    public static void onServerTick(MinecraftServer server) {
        if ((server.getTicks() & 3) != 0) {
            return;   // only every 4th tick
        }
        for (LevelTheme theme : BackroomsLevels.all()) {
            ServerWorld world = server.getWorld(theme.dimensionKey());
            if (world == null) {
                continue;
            }
            Map<Long, Set<BlockPos>> byChunk = FLICKERS.get(theme.dimensionKey());
            if (byChunk == null || byChunk.isEmpty()) {
                continue;
            }
            for (ServerPlayerEntity player : world.getPlayers()) {
                ChunkPos pc = player.getChunkPos();
                for (int dx = -RADIUS; dx <= RADIUS; dx++) {
                    for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                        Set<BlockPos> set = byChunk.get(ChunkPos.toLong(pc.x + dx, pc.z + dz));
                        if (set == null || set.isEmpty()) {
                            continue;
                        }
                        flickerSome(world, player, set);
                    }
                }
            }
        }
    }

    private static void flickerSome(ServerWorld world, ServerPlayerEntity player, Set<BlockPos> set) {
        BlockPos origin = player.getBlockPos();
        Iterator<BlockPos> it = set.iterator();
        while (it.hasNext()) {
            BlockPos pos = it.next();
            if (pos.getSquaredDistance(origin) > 24 * 24) {
                continue;
            }
            if (world.getRandom().nextInt(30) != 0) {
                continue;   // sparse
            }
            BlockState state = world.getBlockState(pos);
            if (!state.isOf(ModBlocks.FLUORESCENT_LIGHT)) {
                it.remove();
                continue;
            }
            boolean lit = state.get(FluorescentLightBlock.LIT);
            world.setBlockState(pos, state.with(FluorescentLightBlock.LIT, !lit), 3);
            if (lit) {
                world.playSound(null, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                        ModSoundEvents.FLICKER, SoundCategory.BLOCKS, 0.15f, 1.0f);
            }
        }
    }

    private LightFlickerSystem() {
    }
}
