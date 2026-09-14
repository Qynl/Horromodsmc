package dev.qynl.backrooms.light;

import dev.qynl.backrooms.hole.HoleEntry;
import dev.qynl.backrooms.registry.ModBlocks;
import dev.qynl.backrooms.registry.ModSoundEvents;
import net.minecraft.block.BlockState;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.math.BlockPos;

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

    private static final Set<BlockPos> FLICKERS = ConcurrentHashMap.newKeySet();

    public static void registerFlicker(BlockPos pos) {
        FLICKERS.add(pos.toImmutable());
    }

    public static void onServerTick(MinecraftServer server) {
        if ((server.getTicks() & 3) != 0) {
            return;   // only every 4th tick
        }
        ServerWorld level0 = server.getWorld(HoleEntry.LEVEL0);
        if (level0 == null || FLICKERS.isEmpty()) {
            return;
        }
        for (ServerPlayerEntity player : level0.getPlayers()) {
            BlockPos origin = player.getBlockPos();
            for (BlockPos pos : FLICKERS) {
                if (pos.getSquaredDistance(origin) > 24 * 24) {
                    continue;
                }
                if (level0.getRandom().nextInt(30) != 0) {
                    continue;   // sparse
                }
                BlockState state = level0.getBlockState(pos);
                if (!state.isOf(ModBlocks.FLUORESCENT_LIGHT)) {
                    FLICKERS.remove(pos);
                    continue;
                }
                boolean lit = state.get(FluorescentLightBlock.LIT);
                level0.setBlockState(pos, state.with(FluorescentLightBlock.LIT, !lit), 3);
                if (lit) {
                    level0.playSound(null, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5,
                            ModSoundEvents.FLICKER, SoundCategory.BLOCKS, 0.15f, 1.0f);
                }
            }
        }
    }

    private LightFlickerSystem() {
    }
}
