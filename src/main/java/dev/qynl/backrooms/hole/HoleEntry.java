package dev.qynl.backrooms.hole;

import dev.qynl.backrooms.level.BackroomsLevels;
import dev.qynl.backrooms.level.LevelTheme;
import dev.qynl.backrooms.level0.BackroomsSpawn;
import dev.qynl.backrooms.level0.Level0Holder;
import dev.qynl.backrooms.level0.Level0Layout;
import dev.qynl.backrooms.registry.ModSoundEvents;
import net.minecraft.block.BlockState;
import net.minecraft.network.packet.s2c.play.PositionFlag;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.EnumSet;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The quiet transition. No particles, no portal sound, no screen effect &mdash; one moment the player
 * is touching the tear, the next they are standing on Level 0's carpet with a barely audible thump.
 * That absence of ceremony is the whole point.
 */
public final class HoleEntry {

    public static final RegistryKey<World> LEVEL0 = BackroomsLevels.LEVEL_0.dimensionKey();

    /** The deepest level reachable. */
    public static final int MAX_LEVEL = 8;

    /** Ticks between transitions for one player. Without it, standing in a trigger block that also
     *  exists at the destination chained several levels in a single second. */
    private static final int TRANSITION_COOLDOWN = 40;
    private static final Map<UUID, Long> LAST_TRANSITION = new ConcurrentHashMap<>();

    /** True when this player may transition right now; records the transition when it returns true. */
    public static boolean canTransition(ServerPlayerEntity player) {
        long now = player.getServer().getTicks();
        Long last = LAST_TRANSITION.get(player.getUuid());
        if (last != null && now - last < TRANSITION_COOLDOWN) {
            return false;
        }
        LAST_TRANSITION.put(player.getUuid(), now);
        return true;
    }

    public static void forgetPlayer(UUID id) {
        LAST_TRANSITION.remove(id);
    }

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
        BlockPos spawn = safeArrival(dest, layout);
        player.teleport(dest, spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5,
                EnumSet.noneOf(PositionFlag.class), player.getYaw(), player.getPitch());
        player.setVelocity(Vec3d.ZERO);   // do not carry Overworld momentum into a wall
        player.playSound(ModSoundEvents.THUMP, 0.12f, 0.7f);
    }

    /**
     * The layout is a pure function of (seed, drift) while the blocks on disk are whatever was built
     * when the chunk was generated. After a reality-drift advance the two can disagree, so the arrival
     * point is verified against the world's real blocks instead of trusted from the layout alone;
     * otherwise a player can materialise inside solid wall.
     */
    private static BlockPos safeArrival(ServerWorld world, Level0Layout layout) {
        BlockPos guess = BackroomsSpawn.find(layout, 0, 0);
        for (int r = 0; r < 16; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
                    int x = guess.getX() + dx;
                    int z = guess.getZ() + dz;
                    if (isStandable(world, x, z)) {
                        return new BlockPos(x, Level0Layout.FLOOR_Y + 1, z);
                    }
                }
            }
        }
        return guess;
    }

    private static boolean isStandable(ServerWorld world, int x, int z) {
        BlockPos.Mutable mut = new BlockPos.Mutable();
        BlockState floor = world.getBlockState(mut.set(x, Level0Layout.FLOOR_Y, z));
        if (!floor.isSolidBlock(world, mut)) {
            return false;
        }
        for (int y = Level0Layout.WALL_MIN_Y; y <= Level0Layout.WALL_MIN_Y + 1; y++) {
            BlockState state = world.getBlockState(mut.set(x, y, z));
            if (state.isSolidBlock(world, mut)) {
                return false;
            }
            if (state.getBlock() instanceof LevelExitBlock || state.getBlock() instanceof RealityTearBlock) {
                return false;   // never drop someone straight onto another transition
            }
        }
        return true;
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
        int x = spawn.getX();
        int z = spawn.getZ();
        int y = over.getTopY() - 1;
        BlockPos.Mutable mut = new BlockPos.Mutable(x, y, z);
        while (y > over.getBottomY()) {
            BlockState state = over.getBlockState(mut.set(x, y, z));
            if (state.isAir() || state.isReplaceable()) {   // air, water, plants, snow: keep falling
                y--;
                continue;
            }
            break;
        }
        player.teleport(over, x + 0.5, y + 1, z + 0.5,
                EnumSet.noneOf(PositionFlag.class), player.getYaw(), player.getPitch());
        player.setVelocity(Vec3d.ZERO);
        player.playSound(ModSoundEvents.THUMP, 0.16f, 1.0f);
    }

    private HoleEntry() {
    }
}
