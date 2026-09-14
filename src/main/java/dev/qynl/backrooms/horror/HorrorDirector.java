package dev.qynl.backrooms.horror;

import dev.qynl.backrooms.config.BackroomsConfig;
import dev.qynl.backrooms.level.BackroomsLevels;
import dev.qynl.backrooms.level.LevelTheme;
import dev.qynl.backrooms.hole.HoleEntry;
import dev.qynl.backrooms.level0.BackroomsSpawn;
import dev.qynl.backrooms.level0.Level0Holder;
import dev.qynl.backrooms.level0.Level0Layout;
import dev.qynl.backrooms.light.FluorescentLightBlock;
import dev.qynl.backrooms.registry.ModEntityTypes;
import dev.qynl.backrooms.registry.ModBlocks;
import dev.qynl.backrooms.registry.ModSoundEvents;
import net.minecraft.block.BlockState;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * The psychological layer. It does almost nothing, most of the time &mdash; that is the design. Long
 * silences are punctuated by small, deniable events: footsteps that are not yours, a light dying
 * behind you, a figure at the edge of vision that is gone when you look.
 *
 * <p>Each event is cheap and individually rare; the per-player cooldown keeps the level quiet enough
 * that the player becomes paranoid about every sound rather than habituated to a noise loop.
 */
public final class HorrorDirector {

    private record Pending(ServerWorld world, long atTick, Vec3d pos, SoundEvent event, float volume) {
    }

    private static final Map<UUID, Long> lastEvent = new HashMap<>();
    private static final Map<UUID, Long> firstSeen = new HashMap<>();
    private static final List<Pending> pending = new ArrayList<>();
    private static long tick = 0;
    private static long lastDriftTick = -1;
    private static final Random random = new Random();

    public static void onServerTick(MinecraftServer server) {
        tick++;
        BackroomsConfig cfg = BackroomsConfig.get();
        runPending(server);

        boolean driftDue = false;
        if (cfg.realityDrift) {
            long interval = Math.max(1, cfg.driftMinMinutes) * 60L * 20L;
            if (lastDriftTick < 0) {
                lastDriftTick = tick;
            } else if (tick - lastDriftTick > interval) {
                lastDriftTick = tick;
                driftDue = true;
            }
        }

        // Every registered Backrooms level runs the same psychological layer on its own layout.
        for (LevelTheme theme : BackroomsLevels.all()) {
            ServerWorld world = server.getWorld(theme.dimensionKey());
            if (world == null) {
                continue;
            }
            if (driftDue) {
                Level0Holder.advanceDrift(world.getSeed(), theme.id());
            }
            for (ServerPlayerEntity player : world.getPlayers()) {
                maybeEvents(world, player, cfg, theme.id());
            }
        }
    }

    private static void runPending(MinecraftServer server) {
        if (pending.isEmpty()) {
            return;
        }
        for (int i = pending.size() - 1; i >= 0; i--) {
            Pending p = pending.get(i);
            if (tick >= p.atTick) {
                p.world.playSound(null, p.pos.x, p.pos.y, p.pos.z, p.event, SoundCategory.HOSTILE, p.volume, 1.0f);
                pending.remove(i);
            }
        }
    }

    private static double paranoia(ServerPlayerEntity player) {
        long seen = firstSeen.computeIfAbsent(player.getUuid(), u -> tick);
        long inLevel = tick - seen;
        return Math.min(1.0, inLevel / (20.0 * 60.0 * 6.0));   // ramps over ~6 minutes
    }

    private static void maybeEvents(ServerWorld world, ServerPlayerEntity player, BackroomsConfig cfg, int levelId) {
        long last = lastEvent.getOrDefault(player.getUuid(), 0L);
        if (tick - last < 20L * 15) {
            return;   // at least 15 quiet seconds between events
        }
        double paranoia = paranoia(player);

        if (cfg.phantomFootsteps && roll(0.0015 + 0.004 * paranoia)) {
            phantomFootsteps(world, player);
        } else if (roll(0.0012)) {
            distantNoise(world, player);
        } else if (cfg.lightsFailBehind && roll(0.0010)) {
            lightOffBehind(world, player);
        } else if (cfg.glimpses && roll(0.0007 + 0.002 * paranoia)) {
            spawnGlimpse(world, player);
        } else if (cfg.listenerEnabled && roll(0.0005 + 0.0012 * paranoia)) {
            spawnListener(world, player, levelId);
        } else {
            return;
        }
        lastEvent.put(player.getUuid(), tick);
    }

    /** At most one Listener at a time; it appears nearby and starts wandering, deaf until you make noise. */
    private static void spawnListener(ServerWorld world, ServerPlayerEntity player, int levelId) {
        if (!world.getEntitiesByType(ModEntityTypes.LISTENER, e -> true).isEmpty()) {
            return;
        }
        Level0Layout layout = Level0Holder.get(world.getSeed(), levelId);
        double ang = random.nextDouble() * Math.PI * 2;
        double dist = 22 + random.nextDouble() * 10;
        int tx = player.getBlockPos().getX() + (int) (Math.cos(ang) * dist);
        int tz = player.getBlockPos().getZ() + (int) (Math.sin(ang) * dist);
        BlockPos spawn = BackroomsSpawn.find(layout, tx, tz);
        ListenerEntity listener = new ListenerEntity(world, spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5);
        world.spawnEntity(listener);
    }

    private static boolean roll(double chance) {
        return random.nextDouble() < chance;
    }

    private static Vec3d randomOffsetBehind(ServerPlayerEntity player, double min, double max) {
        Vec3d look = player.getRotationVec(1.0f);
        double angle = random.nextDouble() * Math.PI * 2;
        double dist = min + random.nextDouble() * (max - min);
        // mostly behind / to the side, never straight ahead
        double side = Math.signum(Math.sin(angle)) * Math.abs(Math.cos(angle));
        Vec3d right = look.crossProduct(new Vec3d(0, 1, 0)).normalize();
        Vec3d back = look.negate();
        Vec3d dir = back.multiply(0.6 + 0.4 * Math.abs(side)).add(right.multiply(side)).normalize();
        return player.getPos().add(dir.multiply(dist)).add(0, 0.2, 0);
    }

    /** Two to four footsteps that are not the player's, spaced out, then silence. */
    private static void phantomFootsteps(ServerWorld world, ServerPlayerEntity player) {
        Vec3d base = randomOffsetBehind(player, 5, 9);
        int steps = 2 + random.nextInt(3);
        for (int i = 0; i < steps; i++) {
            Vec3d pos = base.add(random.nextDouble() - 0.5, 0, random.nextDouble() - 0.5);
            pending.add(new Pending(world, tick + i * 6L, pos, ModSoundEvents.PHANTOM_FOOTSTEP, 0.35f));
        }
    }

    private static void distantNoise(ServerWorld world, ServerPlayerEntity player) {
        Vec3d pos = randomOffsetBehind(player, 14, 24);
        world.playSound(null, pos.x, pos.y, pos.z, ModSoundEvents.DISTANT_NOISE, SoundCategory.AMBIENT, 0.3f, 1.0f);
    }

    /** Finds a lit fixture behind the player and lets it die. */
    private static void lightOffBehind(ServerWorld world, ServerPlayerEntity player) {
        Vec3d look = player.getRotationVec(1.0f);
        BlockPos origin = player.getBlockPos();
        for (BlockPos pos : BlockPos.iterate(origin.add(-7, -2, -7), origin.add(7, 3, 7))) {
            BlockState state = world.getBlockState(pos);
            if (!state.isOf(ModBlocks.FLUORESCENT_LIGHT) || !state.get(FluorescentLightBlock.LIT)) {
                continue;
            }
            Vec3d toLight = Vec3d.ofCenter(pos).subtract(player.getEyePos()).normalize();
            if (toLight.dotProduct(look) > -0.2) {
                continue;   // only lights behind the player
            }
            world.setBlockState(pos, state.with(FluorescentLightBlock.LIT, false), 3);
            return;
        }
    }

    private static void spawnGlimpse(ServerWorld world, ServerPlayerEntity player) {
        Vec3d look = player.getRotationVec(1.0f);
        Vec3d right = look.crossProduct(new Vec3d(0, 1, 0)).normalize();
        double sideSign = random.nextBoolean() ? 1 : -1;
        Vec3d pos = player.getEyePos()
                .add(look.multiply(7 + random.nextDouble() * 3))
                .add(right.multiply(sideSign * (2.5 + random.nextDouble() * 2)))
                .add(0, -0.9, 0);
        GlimpseEntity glimpse = new GlimpseEntity(world, pos.x, pos.y, pos.z);
        world.spawnEntity(glimpse);
    }
}
