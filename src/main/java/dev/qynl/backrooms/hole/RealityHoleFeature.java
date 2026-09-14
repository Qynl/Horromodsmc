package dev.qynl.backrooms.hole;

import dev.qynl.backrooms.config.BackroomsConfig;
import dev.qynl.backrooms.registry.ModBlocks;
import net.minecraft.block.Blocks;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.gen.feature.DefaultFeatureConfig;
import net.minecraft.world.gen.feature.Feature;
import net.minecraft.world.gen.feature.util.FeatureContext;

/**
 * Carves the extremely rare, broken-looking hole in the Overworld.
 *
 * <p>It is deliberately small, asymmetric and "wrong": a few blocks are knocked out in a short random
 * walk, the tear is dropped into the middle, and one floating block is left beside it so the whole
 * thing reads like a rendering glitch rather than a designed portal. There is no glow pillar, no
 * particles, no frame.
 */
public class RealityHoleFeature extends Feature<DefaultFeatureConfig> {

    public RealityHoleFeature() {
        super(DefaultFeatureConfig.CODEC);
    }

    @Override
    public boolean generate(FeatureContext<DefaultFeatureConfig> context) {
        BackroomsConfig config = BackroomsConfig.get();
        StructureWorldAccess world = context.getWorld();
        BlockPos origin = context.getOrigin();

        int rarity = config.holeRarity;
        if (!world.isSkyVisible(origin)) {
            rarity = Math.max(400, (int) (rarity / Math.max(0.1f, config.undergroundHoleFactor)));
        }
        if (rarity > 1 && context.getRandom().nextInt(rarity) != 0) {
            return false;
        }
        if (!world.getBlockState(origin).isSolidBlock(world, origin)) {
            return false;
        }

        Random rnd = context.getRandom();
        int x = origin.getX();
        int y = origin.getY();
        int z = origin.getZ();
        BlockPos.Mutable mutable = new BlockPos.Mutable();

        int dx = 0;
        int dy = 0;
        int dz = 0;
        int carved = 0;
        for (int i = 0; i < 5; i++) {
            mutable.set(x + dx, y + dy, z + dz);
            if (world.getBlockState(mutable).isSolidBlock(world, mutable)) {
                world.setBlockState(mutable, Blocks.AIR.getDefaultState(), 3);
                carved++;
            }
            int dir = rnd.nextInt(6);
            dx += (dir == 0 ? 1 : dir == 1 ? -1 : 0);
            dz += (dir == 2 ? 1 : dir == 3 ? -1 : 0);
            dy += (dir == 4 ? 1 : dir == 5 ? -1 : 0);
            dx = clampSmall(dx);
            dy = clampSmall(dy);
            dz = clampSmall(dz);
        }

        mutable.set(x, y, z);
        world.setBlockState(mutable, ModBlocks.REALITY_TEAR.getDefaultState(), 3);

        // One floating, slightly wrong block: the "Minecraft glitch" flourish.
        int side = rnd.nextInt(4);
        mutable.set(x + (side == 0 ? 1 : side == 1 ? -1 : 0), y + 1, z + (side == 2 ? 1 : side == 3 ? -1 : 0));
        if (world.getBlockState(mutable).isAir()) {
            world.setBlockState(mutable, Blocks.SMOOTH_STONE.getDefaultState(), 3);
        }
        return carved > 0;
    }

    private static int clampSmall(int v) {
        return Math.max(-1, Math.min(1, v));
    }
}
