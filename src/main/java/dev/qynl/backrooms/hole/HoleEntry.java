package dev.qynl.backrooms.hole;

import dev.qynl.backrooms.level.BackroomsLevels;
import dev.qynl.backrooms.level0.BackroomsSpawn;
import dev.qynl.backrooms.level0.Level0Holder;
import dev.qynl.backrooms.level0.Level0Layout;
import dev.qynl.backrooms.registry.ModSoundEvents;
import net.minecraft.entity.PositionFlag;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.EnumSet;

/**
 * The quiet transition. No particles, no portal sound, no screen effect &mdash; one moment the player
 * is touching the tear, the next they are standing on Level 0's carpet with a barely audible thump.
 * That absence of ceremony is the whole point.
 */
public final class HoleEntry {

    public static final RegistryKey<World> LEVEL0 = BackroomsLevels.LEVEL_0.dimensionKey();

    public static void enter(ServerPlayerEntity player, World from) {
        ServerWorld level0 = player.getServer().getWorld(LEVEL0);
        if (level0 == null) {
            return;
        }
        Level0Layout layout = Level0Holder.get(level0.getSeed());
        BlockPos spawn = BackroomsSpawn.find(layout, 0, 0);
        player.teleport(level0, spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5,
                EnumSet.noneOf(PositionFlag.class), player.getYaw(), player.getPitch());
        player.playSound(ModSoundEvents.THUMP, 0.12f, 0.7f);
    }

    private HoleEntry() {
    }
}
