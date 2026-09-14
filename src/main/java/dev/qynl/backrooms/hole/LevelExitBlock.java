package dev.qynl.backrooms.hole;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * A way down, dressed as part of the level. Per the wiki, Level 0 is left by throwing yourself
 * through a <em>flickering wall</em>, and Level 1 by walking a corridor that goes on far longer than
 * it should. Both are modelled as an ordinary-looking, non-colliding block: it reads as wall or
 * corridor, you step into it, and you are somewhere deeper. The {@code target} is the level it drops
 * you into.
 */
public class LevelExitBlock extends Block {

    private final int target;

    public LevelExitBlock(Settings settings, int target) {
        super(settings);
        this.target = target;
    }

    @Override
    public void onEntityCollision(BlockState state, World world, BlockPos pos, Entity entity) {
        if (!world.isClient() && entity instanceof ServerPlayerEntity player) {
            HoleEntry.goToLevel(player, target);
        }
    }
}
