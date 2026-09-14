package dev.qynl.backrooms.hole;

import dev.qynl.backrooms.level.BackroomsLevels;
import dev.qynl.backrooms.level.LevelTheme;
import dev.qynl.backrooms.level0.BackroomsSpawn;
import dev.qynl.backrooms.level0.Level0Holder;
import dev.qynl.backrooms.level0.Level0Layout;
import dev.qynl.backrooms.registry.ModSoundEvents;
import net.minecraft.network.packet.s2c.play.PositionFlag;
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

    /** The deepest level reachable. */
    public static final int MAX_LEVEL = 8;

    /** The tear in the Overworld is the way into the Backrooms proper: it always opens on Level 0. */
    public static void enter(ServerPlayerEntity player, World from) {
        goToLevel(player, 0);
    }

    /** Drops the player into the given level at its spawn, with the same quiet, unceremonious thump. */
    public static void goToLevel(ServerPlayerEntity player, int levelId) {
        LevelTheme theme = BackroomsLevels.get(levelId);
        if (theme == null) {
            return;
        }
        ServerWorld dest = player.getServer().getWorld(theme.dimensionKey());
        if (dest == null) {
            return;
        }
        Level0Layout layout = Level0Holder.get(dest.getSeed(), levelId);
        BlockPos spawn = BackroomsSpawn.find(layout, 0, 0);
        player.teleport(dest, spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5,
                EnumSet.noneOf(PositionFlag.class), player.getYaw(), player.getPitch());
        player.playSound(ModSoundEvents.THUMP, 0.12f, 0.7f);
    }

    /**
     * The rare way home. A hatch on Level 8 lifts the player out of the Backrooms entirely and
     * sets them down on the Overworld spawn - the only true exit from the whole descent.
     */
    public static void escapeToOverworld(ServerPlayerEntity player) {
        ServerWorld over = player.getServer().getWorld(World.OVERWORLD);
        if (over == null) {
            return;
        }
        BlockPos spawn = over.getSpawnPos();
        int x = spawn.getX(), z = spawn.getZ();
        int y = over.getTopY();
        BlockPos.Mutable mut = new BlockPos.Mutable(x, y, z);
        while (y > over.getBottomY() && over.getBlockState(mut.set(x, y, z)).isAir()) {
            y--;
        }
        player.teleport(over, x + 0.5, y + 1, z + 0.5,
                EnumSet.noneOf(PositionFlag.class), player.getYaw(), player.getPitch());
        player.playSound(ModSoundEvents.THUMP, 0.16f, 1.0f);
    }

    private HoleEntry() {
    }
}
