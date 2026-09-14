package dev.qynl.backrooms.light;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.Properties;

/**
 * A ceiling fluorescent fixture. {@link Properties#LIT} drives both the emissive model and the
 * block light level; {@link LightFlickerSystem} and the horror director toggle it to make lighting
 * feel unreliable.
 */
public class FluorescentLightBlock extends Block {

    public static final BooleanProperty LIT = Properties.LIT;

    public FluorescentLightBlock(Settings settings) {
        super(settings);
        setDefaultState(getStateManager().getDefaultState().with(LIT, true));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(LIT);
    }
}
