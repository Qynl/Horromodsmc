package com.horromods.hollow.block;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;

/**
 * Full-block lantern that shines with light level 15. Its glow naturally
 * prevents hostile spawns nearby (monsters need darkness), slowly calms
 * Dread, and The Watcher refuses to manifest close to it.
 */
public class HallowedLanternBlock extends Block {
    public HallowedLanternBlock(Settings settings) {
        super(settings);
    }

    @Override
    public void randomDisplayTick(BlockState state, World world, BlockPos pos, Random random) {
        if (random.nextInt(4) == 0) {
            world.addParticle(ParticleTypes.END_ROD,
                    pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.7,
                    pos.getY() + 1.02,
                    pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.7,
                    0.0, 0.03, 0.0);
        }
    }
}
