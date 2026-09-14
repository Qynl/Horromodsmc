package dev.qynl.backrooms.hole;

import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.Block;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * The opening itself. Invisible and non-colliding, so a player simply walks into it, and its block entity draws
 * the pale blue-white void you see through the tear. Walking in or using it is the transition.
 */
public class RealityTearBlock extends Block implements BlockEntityProvider {

    public RealityTearBlock(Settings settings) {
        super(settings);
    }

    @Override
    public BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.INVISIBLE;
    }

    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new RealityTearBlockEntity(pos, state);
    }

    @Override
    public void onEntityCollision(BlockState state, World world, BlockPos pos, Entity entity) {
        if (!world.isClient() && entity instanceof ServerPlayerEntity player) {
            HoleEntry.enter(player, world);
        }
    }

    @Override
    protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        if (!world.isClient() && player instanceof ServerPlayerEntity sp) {
            HoleEntry.enter(sp, world);
            return ActionResult.SUCCESS;
        }
        return ActionResult.PASS;
    }
}
