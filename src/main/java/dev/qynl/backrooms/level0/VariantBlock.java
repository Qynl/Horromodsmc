package dev.qynl.backrooms.level0;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.IntProperty;

/**
 * A block with a 4-way {@code variant} property. The Level 0 generator uses it to express the
 * "slightly different shades / stains" of wallpaper, carpet and ceiling tiles, so the decay of a
 * given room is decided by the layout core rather than left to random model weights.
 */
public class VariantBlock extends Block {

    public static final IntProperty VARIANT = IntProperty.of("variant", 0, 3);

    public VariantBlock(Settings settings) {
        super(settings);
        setDefaultState(getStateManager().getDefaultState().with(VARIANT, 0));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(VARIANT);
    }
}
