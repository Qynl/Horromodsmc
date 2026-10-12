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
 *
 * <p>Placement note: the {@code WORLD_SURFACE} placement modifier hands the feature the block
 * <em>above</em> the terrain (heightmaps store {@code topSolidY + 1}), so this feature first walks
 * down to the real surface before deciding anything. Without that step the "is this solid?" check
 * below always saw air and the hole never generated at all.
 */
public class RealityHoleFeature extends Feature<DefaultFeatureConfig> {

    /** How far down from the placement origin we are willing to look for real ground. */
    private static final int MAX_DROP = 24;

    public RealityHoleFeature() {
        super(DefaultFeatureConfig.CODEC);
    }

    @Override
    public boolean generate(FeatureContext<DefaultFeatureConfig> context) {
        BackroomsConfig config = BackroomsConfig.get();
        StructureWorldAccess world = context.getWorld();
        BlockPos origin = context.getOrigin();
        Random rnd = context.getRandom();

        // The heightmap modifier points one block above the terrain; walk down onto the ground.
        BlockPos.Mutable mutable = new BlockPos.Mutable();
        int x = origin.getX();
        int z = origin.getZ();
        int y = origin.getY();
        int bottom = world.getBottomY();
        int dropped = 0;
        while (y > bottom && dropped < MAX_DROP
                && !world.getBlockState(mutable.set(x, y, z)).isSolidBlock(world, mutable)) {
            y--;
            dropped++;
        }
        if (y <= bottom || !world.getBlockState(mutable.set(x, y, z)).isSolidBlock(world, mutable)) {
            return false;   // no ground under the placement point (ocean, void, ...): no hole here
        }

        boolean underground = !world.isSkyVisible(mutable.set(x, y + 1, z));
        int rarity = underground ? config.undergroundHoleRarity : config.holeRarity;
        if (rarity > 1 && rnd.nextInt(rarity) != 0) {
            return false;
        }

        // Knock a short, lopsided bite out of the ground so the tear is reachable and visible
        // instead of being sealed inside solid rock.
        int carved = 0;
        int dx = 0;
        int dy = 0;
        int dz = 0;
        for (int i = 0; i < 6; i++) {
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
            dy = Math.max(0, clampSmall(dy));   // never dig below the surface bite
            dz = clampSmall(dz);
        }

        // The tear itself sits in the bite, at or just above the ground, so walking in is trivial.
        mutable.set(x, y, z);
        world.setBlockState(mutable, ModBlocks.REALITY_TEAR.getDefaultState(), 3);

        // One floating, slightly wrong block: the "Minecraft glitch" flourish.
        int side = rnd.nextInt(4);
        mutable.set(x + (side == 0 ? 1 : side == 1 ? -1 : 0), y + 2, z + (side == 2 ? 1 : side == 3 ? -1 : 0));
        if (world.getBlockState(mutable).isAir()) {
            world.setBlockState(mutable, Blocks.SMOOTH_STONE.getDefaultState(), 3);
        }
        return carved > 0;
    }

    private static int clampSmall(int v) {
        return Math.max(-1, Math.min(1, v));
    }
}
