package dev.qynl.backrooms.level0;

import dev.qynl.backrooms.light.FluorescentLightBlock;
import dev.qynl.backrooms.light.LightFlickerSystem;
import dev.qynl.backrooms.registry.ModBlocks;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.state.property.IntProperty;
import net.minecraft.structure.StructureWorldAccess;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.gen.feature.DefaultFeatureConfig;
import net.minecraft.world.gen.feature.Feature;
import net.minecraft.world.gen.feature.util.FeatureContext;

/**
 * Materialises one chunk of Level 0 from {@link Level0Layout}.
 *
 * <p>Everything the level is (walls, rooms, pillars, lights, stains) is decided by the layout core;
 * this feature is a dumb painter that turns its per-column answers into blocks. It runs once per
 * chunk in the flat dimension, and because the layout is a pure function of (seed, drift, column),
 * chunks agree on their shared borders without any cross-chunk state.
 */
public class BackroomsBuildFeature extends Feature<DefaultFeatureConfig> {

    private static final IntProperty VARIANT = VariantBlock.VARIANT;

    public BackroomsBuildFeature() {
        super(DefaultFeatureConfig.CODEC);
    }

    @Override
    public boolean generate(FeatureContext<DefaultFeatureConfig> context) {
        StructureWorldAccess world = context.getWorld();
        BlockPos origin = context.getOrigin();
        Level0Layout layout = Level0Holder.get(world.getSeed());

        int cx = origin.getX() >> 4;
        int cz = origin.getZ() >> 4;
        BlockPos.Mutable mutable = new BlockPos.Mutable();

        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                int x = (cx << 4) + lx;
                int z = (cz << 4) + lz;
                paintColumn(world, mutable, layout, x, z);
            }
        }
        return true;
    }

    private void paintColumn(StructureWorldAccess world, BlockPos.Mutable mutable,
                             Level0Layout layout, int x, int z) {
        int bits = layout.tileBits(x, z);
        boolean solid = (bits & Level0Layout.BIT_SOLID) != 0;
        boolean pillar = (bits & Level0Layout.BIT_PILLAR) != 0;
        int surface = layout.surface(x, z);
        int lightState = layout.lightState(x, z);
        int ceilingY = layout.ceilingY(x, z);

        // The walkable floor always exists, even under walls, so there is never a hole.
        mutable.set(x, Level0Layout.FLOOR_Y, z);
        world.setBlockState(mutable, withVariant(ModBlocks.CARPET.getDefaultState(), surface), 3);

        if (solid || pillar) {
            BlockState wall = withVariant(ModBlocks.WALLPAPER.getDefaultState(), surface);
            for (int y = Level0Layout.WALL_MIN_Y; y <= ceilingY; y++) {
                mutable.set(x, y, z);
                world.setBlockState(mutable, wall, 3);
            }
            return;
        }

        // Open space: air inside, ceiling on top.
        for (int y = Level0Layout.WALL_MIN_Y; y < ceilingY; y++) {
            mutable.set(x, y, z);
            world.setBlockState(mutable, Blocks.AIR.getDefaultState(), 3);
        }
        mutable.set(x, ceilingY, z);
        if (lightState != Level0Layout.LIGHT_NONE) {
            boolean lit = lightState != Level0Layout.LIGHT_DEAD;
            world.setBlockState(mutable,
                    ModBlocks.FLUORESCENT_LIGHT.getDefaultState().with(FluorescentLightBlock.LIT, lit), 3);
            if (lightState == Level0Layout.LIGHT_FLICKERING) {
                LightFlickerSystem.registerFlicker(mutable);
            }
        } else {
            world.setBlockState(mutable, withVariant(ModBlocks.CEILING_TILE.getDefaultState(), surface), 3);
        }
    }

    private static BlockState withVariant(BlockState state, int variant) {
        return state.with(VARIANT, Math.max(0, Math.min(3, variant)));
    }
}
