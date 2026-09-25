package com.horromods.hollow.util;

import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldView;
import org.jetbrains.annotations.Nullable;

/** Small shared helpers. */
public final class HollowUtil {
    private HollowUtil() {
    }

    /**
     * Searches a column around {@code pos} for the first solid, dry block that
     * has air directly above it, and returns that air position (a place where
     * an entity can stand). Searches a few blocks up and {@code searchDown}
     * blocks down. Returns {@code null} when no suitable spot is found.
     */
    @Nullable
    public static BlockPos findGround(WorldView world, BlockPos pos, int searchDown) {
        for (int dy = 3; dy >= -searchDown; dy--) {
            BlockPos candidate = pos.up(dy);
            BlockState state = world.getBlockState(candidate);
            if (!state.isAir() && state.getFluidState().isEmpty()
                    && world.getBlockState(candidate.up()).isAir()) {
                return candidate.up();
            }
        }
        return null;
    }
}
