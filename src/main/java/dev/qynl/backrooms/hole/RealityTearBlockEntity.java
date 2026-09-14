package dev.qynl.backrooms.hole;

import dev.qynl.backrooms.registry.ModBlockEntities;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.util.math.BlockPos;

/**
 * Holds the pattern seed for the void you see through the tear. If the player looks away for long
 * enough, the seed rotates on the next render, so "the view is slightly different when you look
 * back" &mdash; one of the spec's quietest, creepiest details.
 */
public class RealityTearBlockEntity extends BlockEntity {

    private long patternSeed;
    private long lastRenderTick = -1;

    public RealityTearBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.REALITY_TEAR, pos, state);
        this.patternSeed = pos.asLong() * 0x9E3779B97F4A7C15L;
    }

    /** Returns the pattern seed, rotating it if the block has not been looked at for a while. */
    public long patternSeed(long nowTick) {
        if (lastRenderTick >= 0 && nowTick - lastRenderTick > 40) {
            patternSeed = Long.rotateLeft(patternSeed, 13) ^ (nowTick * 0x2545F4914F6CDD1DL);
        }
        lastRenderTick = nowTick;
        return patternSeed;
    }
}
