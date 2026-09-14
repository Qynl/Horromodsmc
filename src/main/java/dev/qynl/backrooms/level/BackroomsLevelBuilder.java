package dev.qynl.backrooms.level;

import dev.qynl.backrooms.hole.HoleEntry;
import dev.qynl.backrooms.level0.BackroomsBuildFeature;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Guarantees the Backrooms levels are actually built.
 *
 * <p>The level dimensions use a superflat generator, which does not reliably run biome worldgen
 * features - so relying on {@code BackroomsBuildFeature} alone left every level as bare bedrock.
 * This runs every server tick and paints any not-yet-built chunk around a player who is inside a
 * Backrooms level, straight from the deterministic layout. Idempotent and cheap once built.
 */
public final class BackroomsLevelBuilder {

    private static final Set<String> BUILT = ConcurrentHashMap.newKeySet();
    private static final int RADIUS = 1;

    public static void onServerTick(MinecraftServer server) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            RegistryKey<World> key = player.getWorld().getRegistryKey();
            int level = levelOf(key);
            if (level < 0) {
                continue;
            }
            ServerWorld world = server.getWorld(key);
            if (world == null) {
                continue;
            }
            ChunkPos c = player.getChunkPos();
            for (int dx = -RADIUS; dx <= RADIUS; dx++) {
                for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                    int cx = c.x + dx;
                    int cz = c.z + dz;
                    String id = world.getSeed() + ":" + level + ":" + cx + "," + cz;
                    if (BUILT.contains(id)) {
                        continue;
                    }
                    if (!world.getChunkManager().isChunkLoaded(cx, cz)) {
                        continue;
                    }
                    BackroomsBuildFeature.buildChunk(world, level, cx, cz);
                    BUILT.add(id);
                }
            }
        }
    }

    private static int levelOf(RegistryKey<World> key) {
        for (int i = 0; i <= HoleEntry.MAX_LEVEL; i++) {
            LevelTheme theme = BackroomsLevels.get(i);
            if (theme != null && theme.dimensionKey().equals(key)) {
                return i;
            }
        }
        return -1;
    }

    private BackroomsLevelBuilder() {
    }
}
