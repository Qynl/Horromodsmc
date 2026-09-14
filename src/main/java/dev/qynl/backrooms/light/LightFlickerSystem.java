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
import net.minecraft.world.World;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gives the "flickering" fixtures their behaviour. The layout core marks some lights as FLICKERING;
 * the build feature registers their positions here, and this system toggles a sparse, random subset
 * of the ones near a player so the hum and the light both stutter.
 *
 * <p>Only a handful of lights are ever touched per tick, so the cost stays bounded no matter how
 * large the explored level gets.
 */
public final class LightFlickerSystem {

    private static final Map<RegistryKey<World>, Set<BlockPos>> FLICKERS = new ConcurrentHashMap<>();

    public static void registerFlicker(RegistryKey<World> dim, BlockPos pos) {
        FLICKERS.computeIfAbsent(dim, k -> ConcurrentHashMap.newKeySet()).add(pos.toImmutable());
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
            Set<BlockPos> flickers = FLICKERS.get(theme.dimensionKey());
            if (flickers == null || flickers.isEmpty()) {
                continue;
            }
            for (ServerPlayerEntity player : world.getPlayers()) {
                BlockPos origin = player.getBlockPos();
                for (BlockPos pos : flickers) {
                    if (pos.getSquaredDistance(origin) > 24 * 24) {
                        continue;
                    }
                    if (world.getRandom().nextInt(30) != 0) {
                        continue;   // sparse
                    }
                    BlockState state = world.getBlockState(pos);
                    if (!state.isOf(ModBlocks.FLUORESCENT_LIGHT)) {
                        flickers.remove(pos);
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
        }
    }

    private LightFlickerSystem() {
    }
}
