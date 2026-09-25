package com.horromods.hollow.dread;

import com.horromods.hollow.Hollow;
import com.horromods.hollow.block.ModBlocks;
import com.horromods.hollow.entity.ApparitionEntity;
import com.horromods.hollow.entity.ModEntities;
import com.horromods.hollow.entity.WatcherEntity;
import com.horromods.hollow.item.ModItems;
import com.horromods.hollow.network.DreadPayload;
import com.horromods.hollow.util.HollowUtil;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.WorldView;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The Dread system.
 *
 * <p>Every player carries a hidden Dread value (0-100). It creeps up at
 * night, in darkness, and when The Watcher is close; it drains in light and
 * near Hallowed Lanterns. As Dread rises, the world starts to feel wrong:
 * distant whispers, footsteps behind you, torches snuffing themselves out,
 * apparitions at the edge of your vision... and at the very top, The Watcher
 * itself manifests nearby.</p>
 */
public final class DreadManager {
    private static final Map<UUID, Float> DREAD = new ConcurrentHashMap<>();
    private static final Map<UUID, Boolean> NEAR_LANTERN = new ConcurrentHashMap<>();
    private static final Map<UUID, Boolean> WATCHER_NEAR = new ConcurrentHashMap<>();

    private DreadManager() {
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(DreadManager::onServerTick);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> reset(handler.getPlayer()));
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity instanceof PlayerEntity player) {
                reset(player);
            }
        });
    }

    /** Death and rejoining let a player catch their breath. */
    public static void reset(PlayerEntity player) {
        UUID id = player.getUuid();
        DREAD.remove(id);
        NEAR_LANTERN.remove(id);
        WATCHER_NEAR.remove(id);
    }

    public static float getDread(PlayerEntity player) {
        return DREAD.getOrDefault(player.getUuid(), 0.0f);
    }

    /** Scans a cubic radius for Hallowed Lantern blocks. */
    public static boolean lanternNearby(WorldView world, BlockPos center, int radius) {
        BlockPos.Mutable mutable = new BlockPos.Mutable();
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    if (world.getBlockState(mutable.set(center, x, y, z)).isOf(ModBlocks.HALLOWED_LANTERN)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static void onServerTick(MinecraftServer server) {
        if (!Hollow.CONFIG.dreadEnabled) {
            return;
        }
        long time = server.getOverworld().getTime();
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            updatePlayer(player, time);
        }
    }

    private static void updatePlayer(ServerPlayerEntity player, long time) {
        if (player.isCreative() || player.isSpectator()) {
            reset(player);
            return;
        }
        if (!(player.getWorld() instanceof ServerWorld world)) {
            return;
        }

        UUID id = player.getUuid();
        float dread = DREAD.getOrDefault(id, 0.0f);
        float before = dread;
        BlockPos pos = player.getBlockPos();
        int light = world.getLightLevel(pos);

        // Expensive proximity checks run once a second and are cached.
        if (time % 20L == 0L) {
            NEAR_LANTERN.put(id, lanternNearby(world, pos, 8));
            boolean anyWatcher = !world.getEntitiesByClass(WatcherEntity.class,
                    player.getBoundingBox().expand(24.0), watcher -> true).isEmpty();
            WATCHER_NEAR.put(id, anyWatcher);

            // The Third Eye reveals Watchers through the dark.
            if (ModItems.holdsThirdEye(player)) {
                for (WatcherEntity watcher : world.getEntitiesByClass(WatcherEntity.class,
                        player.getBoundingBox().expand(24.0), w -> true)) {
                    watcher.addStatusEffect(
                            new StatusEffectInstance(StatusEffects.GLOWING, 40, 0, false, false, false));
                }
            }
        }
        boolean nearLantern = NEAR_LANTERN.getOrDefault(id, false);
        boolean watcherNear = WATCHER_NEAR.getOrDefault(id, false);
        boolean warded = ModItems.hasWardingTotem(player);

        float gain = 0.0f;
        if (world.isNight() && light < 7) {
            gain += 0.05f;
        }
        if (light <= 2) {
            gain += 0.04f;
        }
        if (watcherNear) {
            gain += 0.1f;
        }
        if (warded) {
            gain *= 0.25f;
        }
        gain *= Hollow.CONFIG.dreadMultiplier;

        float drain = 0.0f;
        if (light >= 8) {
            drain += 0.06f;
        }
        if (nearLantern) {
            drain += 0.06f;
        }

        float rate = gain - drain;
        if (rate == 0.0f) {
            rate = -0.005f;
        }
        dread = MathHelper.clamp(dread + rate, 0.0f, 100.0f);
        DREAD.put(id, dread);

        // Keep the client HUD in the loop.
        if (time % 20L == 0L) {
            ServerPlayNetworking.send(player, new DreadPayload(dread));
        }

        // Whisper something when crossing a threshold.
        int from = tier(before);
        int to = tier(dread);
        if (to > from) {
            String key = switch (to) {
                case 1 -> "hollow.dread.begin";
                case 2 -> "hollow.dread.rising";
                default -> "hollow.dread.high";
            };
            player.sendMessage(Text.translatable(key).formatted(Formatting.GRAY, Formatting.ITALIC), false);
        }

        // Panic pulses of Darkness at high Dread.
        if (dread >= 60.0f && time % 200L == 0L && world.random.nextInt(2) == 0) {
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.DARKNESS, 100, 0, false, false, true));
        }

        if (!Hollow.CONFIG.eventsEnabled) {
            return;
        }

        // Scare events, gated by Dread thresholds.
        if (dread >= 25.0f && world.random.nextInt(700) == 0) {
            player.playSound(SoundEvents.AMBIENT_CAVE, 0.55f, 0.5f + world.random.nextFloat() * 0.5f);
        }
        if (dread >= 45.0f && world.random.nextInt(600) == 0) {
            snuffTorch(world, player);
        }
        if (dread >= 55.0f && world.random.nextInt(900) == 0) {
            player.playSound(SoundEvents.BLOCK_GRAVEL_STEP, 0.9f, 0.55f);
        }
        if (dread >= 70.0f && world.random.nextInt(1200) == 0) {
            summonApparition(world, player);
        }
        if (dread >= 85.0f && world.random.nextInt(800) == 0) {
            summonWatcher(world, player);
        }
    }

    private static int tier(float dread) {
        if (dread >= 75.0f) {
            return 3;
        }
        if (dread >= 50.0f) {
            return 2;
        }
        if (dread >= 25.0f) {
            return 1;
        }
        return 0;
    }

    /** Snuffs one random torch near the player. */
    private static void snuffTorch(ServerWorld world, PlayerEntity player) {
        BlockPos center = player.getBlockPos();
        List<BlockPos> torches = new ArrayList<>();
        BlockPos.Mutable mutable = new BlockPos.Mutable();
        for (int x = -6; x <= 6; x++) {
            for (int y = -3; y <= 3; y++) {
                for (int z = -6; z <= 6; z++) {
                    BlockState state = world.getBlockState(mutable.set(center, x, y, z));
                    if (state.isOf(Blocks.TORCH) || state.isOf(Blocks.WALL_TORCH)
                            || state.isOf(Blocks.SOUL_TORCH) || state.isOf(Blocks.SOUL_WALL_TORCH)) {
                        torches.add(mutable.toImmutable());
                    }
                }
            }
        }
        if (torches.isEmpty()) {
            return;
        }
        BlockPos target = torches.get(world.random.nextInt(torches.size()));
        world.setBlockState(target, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
        world.playSound(null, target, SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.BLOCKS, 0.6f, 1.2f);
        world.spawnParticles(ParticleTypes.LARGE_SMOKE,
                target.getX() + 0.5, target.getY() + 0.6, target.getZ() + 0.5,
                10, 0.15, 0.25, 0.15, 0.02);
    }

    /** Manifests a harmless-but-terrifying silhouette in front of the player. */
    private static void summonApparition(ServerWorld world, ServerPlayerEntity player) {
        Vec3d look = player.getRotationVec(1.0f);
        double distance = 8.0 + world.random.nextDouble() * 4.0;
        BlockPos base = player.getBlockPos()
                .add((int) (look.x * distance), 0, (int) (look.z * distance));
        BlockPos ground = HollowUtil.findGround(world, base, 6);
        if (ground == null) {
            return;
        }

        ApparitionEntity apparition = new ApparitionEntity(ModEntities.APPARITION, world);
        double dx = player.getX() - (ground.getX() + 0.5);
        double dz = player.getZ() - (ground.getZ() + 0.5);
        float yaw = (float) (MathHelper.atan2(dz, dx) * 180.0F / Math.PI) - 90.0F;
        apparition.refreshPositionAndAngles(ground.getX() + 0.5, ground.getY(), ground.getZ() + 0.5, yaw, 0.0f);

        if (world.spawnEntity(apparition)) {
            player.playSound(SoundEvents.ENTITY_GHAST_SCREAM, 0.5f, 1.5f);
            player.sendMessage(Text.translatable("hollow.dread.presence")
                    .formatted(Formatting.DARK_PURPLE, Formatting.ITALIC), true);
        }
    }

    /** At peak Dread, The Watcher itself steps out of the dark nearby. */
    private static void summonWatcher(ServerWorld world, ServerPlayerEntity player) {
        // Never stack more than two Watchers on a single player.
        int existing = world.getEntitiesByClass(WatcherEntity.class,
                player.getBoundingBox().expand(48.0), watcher -> true).size();
        if (existing >= 2) {
            return;
        }

        for (int attempt = 0; attempt < 10; attempt++) {
            double angle = world.random.nextDouble() * MathHelper.TAU;
            double distance = 10.0 + world.random.nextDouble() * 8.0;
            BlockPos base = player.getBlockPos().add(
                    (int) (Math.cos(angle) * distance), 0, (int) (Math.sin(angle) * distance));
            if (lanternNearby(world, base, 12)) {
                continue;
            }
            BlockPos ground = HollowUtil.findGround(world, base, 8);
            if (ground == null || world.getLightLevel(ground) > 10) {
                continue;
            }

            WatcherEntity watcher = new WatcherEntity(ModEntities.WATCHER, world);
            watcher.refreshPositionAndAngles(ground.getX() + 0.5, ground.getY(), ground.getZ() + 0.5,
                    world.random.nextFloat() * 360.0f, 0.0f);
            watcher.setPersistent();
            if (world.spawnEntity(watcher)) {
                player.playSound(SoundEvents.AMBIENT_CAVE, 0.8f, 0.4f);
                return;
            }
        }
    }
}
