package com.horromods.hollow.entity;

import com.horromods.hollow.Hollow;
import com.horromods.hollow.item.ModItems;
import com.horromods.hollow.util.HollowUtil;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityData;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.entity.ai.goal.RevengeGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.ai.goal.WanderAroundFarGoal;
import net.minecraft.entity.ai.pathing.PathNodeType;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Difficulty;
import net.minecraft.world.LightType;
import net.minecraft.world.LocalDifficulty;
import net.minecraft.world.ServerWorldAccess;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * The Watcher — a towering silhouette that hunts in the dark.
 *
 * <ul>
 *   <li>Stalks players from a distance before closing in.</li>
 *   <li>Stare at it and it vanishes in a blink of static... then reappears
 *       somewhere else. After it blinks too many times, it gives up and
 *       dissolves into smoke.</li>
 *   <li>Its proximity fills nearby players with dread and darkness.</li>
 *   <li>Daylight melts it away.</li>
 *   <li>A Warding Totem hides a player from its gaze entirely.</li>
 * </ul>
 */
public class WatcherEntity extends HostileEntity {
    private static final int STARE_TELEPORT_TICKS = 14;
    private static final int MAX_BLINKS = 6;

    private int stareTicks;
    private int blinkCount;
    private int fadeTicks;
    private int wardCooldown;
    private boolean pale;

    public WatcherEntity(EntityType<? extends HostileEntity> entityType, World world) {
        super(entityType, world);
        this.setPathfindingPenalty(PathNodeType.WATER, -1.0f);
        this.setPathfindingPenalty(PathNodeType.DAMAGE_FIRE, -1.0f);
        this.setPathfindingPenalty(PathNodeType.DANGER_FIRE, 8.0f);
        this.experiencePoints = 12;
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return HostileEntity.createHostileAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 60.0)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.32)
                .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 7.0)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 48.0)
                .add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 0.85);
    }

    /**
     * Natural spawn rule: classic dark-spawn conditions, but only under a
     * near-black sky (caves, roofs, moonless nights) and never on Peaceful.
     */
    public static boolean canSpawn(EntityType<WatcherEntity> type, ServerWorldAccess world,
            SpawnReason spawnReason, BlockPos pos, Random random) {
        if (world.getDifficulty() == Difficulty.PEACEFUL) {
            return false;
        }
        if (world.getLightLevel(LightType.SKY, pos) > 4) {
            return false;
        }
        return HostileEntity.canSpawnInDark(type, world, spawnReason, pos, random);
    }

    @Override
    protected void initGoals() {
        this.goalSelector.add(1, new SwimGoal(this));
        this.goalSelector.add(3, new StalkGoal(this));
        this.goalSelector.add(4, new MeleeAttackGoal(this, 1.1, true));
        this.goalSelector.add(7, new WanderAroundFarGoal(this, 0.8));
        this.goalSelector.add(8, new LookAtEntityGoal(this, PlayerEntity.class, 12.0f));
        this.targetSelector.add(1, new RevengeGoal(this));
        this.targetSelector.add(2, new ActiveTargetGoal<>(this, PlayerEntity.class, true));
    }

    @Override
    public void setTarget(@Nullable LivingEntity target) {
        // A Warding Totem hides its bearer from The Watcher entirely —
        // but every such ward cracks the totem a little more.
        if (target instanceof PlayerEntity player && ModItems.hasWardingTotem(player)) {
            if (this.wardCooldown <= 0) {
                this.wardCooldown = 600;
                ModItems.wardAttempt(player);
            }
            return;
        }
        super.setTarget(target);
    }

    /** The rare Pale Watcher is bolder and tolerates being stared at longer. */
    public boolean isPale() {
        return this.pale;
    }

    private int maxBlinks() {
        return this.pale ? MAX_BLINKS + 3 : MAX_BLINKS;
    }

    @Override
    public EntityData initialize(ServerWorldAccess world, LocalDifficulty difficulty,
            SpawnReason spawnReason, @Nullable EntityData entityData) {
        this.pale = world.getRandom().nextFloat() < Hollow.CONFIG.paleWatcherChance;
        return super.initialize(world, difficulty, spawnReason, entityData);
    }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        nbt.putBoolean("pale", this.pale);
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        this.pale = nbt.getBoolean("pale");
    }

    @Override
    public void tick() {
        super.tick();
        if (this.getWorld().isClient) {
            return;
        }
        if (this.wardCooldown > 0) {
            this.wardCooldown--;
        }
        ServerWorld world = (ServerWorld) this.getWorld();

        // Daylight melts it away.
        if (world.isDay() && world.isSkyVisible(this.getBlockPos())) {
            this.fadeTicks++;
            if (this.fadeTicks % 6 == 0) {
                world.spawnParticles(ParticleTypes.LARGE_SMOKE, this.getX(), this.getY() + 1.2, this.getZ(),
                        8, 0.4, 0.9, 0.4, 0.02);
            }
            if (this.fadeTicks > 70) {
                world.spawnParticles(ParticleTypes.LARGE_SMOKE, this.getX(), this.getY() + 1.2, this.getZ(),
                        30, 0.5, 1.2, 0.5, 0.03);
                this.discard();
                return;
            }
        } else {
            this.fadeTicks = Math.max(0, this.fadeTicks - 2);
        }

        // Heartbeat and darkness aura for everyone nearby.
        if (this.age % 20 == 0) {
            for (PlayerEntity player : world.getPlayers()) {
                if (!this.canAffect(player)) {
                    continue;
                }
                double distance = Math.sqrt(player.squaredDistanceTo(this));
                if (distance < 24.0) {
                    float volume = MathHelper.clamp((float) (1.3 - distance / 24.0), 0.15f, 1.3f);
                    player.playSound(SoundEvents.ENTITY_WARDEN_HEARTBEAT, volume, 0.8f);
                }
                if (distance < 10.0) {
                    player.addStatusEffect(
                            new StatusEffectInstance(StatusEffects.DARKNESS, 140, 0, false, false, true), this);
                }
            }
        }

        // If someone stares at it for too long, it blinks somewhere else.
        PlayerEntity starer = this.findStaringPlayer();
        if (starer != null) {
            this.stareTicks++;
            if (this.stareTicks >= STARE_TELEPORT_TICKS) {
                this.stareTicks = 0;
                if (this.blinkCount >= this.maxBlinks()) {
                    world.spawnParticles(ParticleTypes.LARGE_SMOKE, this.getX(), this.getY() + 1.2, this.getZ(),
                            30, 0.5, 1.2, 0.5, 0.03);
                    this.discard();
                    return;
                }
                if (this.blinkNear(starer)) {
                    this.blinkCount++;
                }
            }
        } else {
            this.stareTicks = Math.max(0, this.stareTicks - 2);
        }
    }

    @Override
    public boolean tryAttack(Entity target) {
        boolean hit = super.tryAttack(target);
        if (hit && target instanceof LivingEntity living) {
            living.addStatusEffect(
                    new StatusEffectInstance(StatusEffects.DARKNESS, 140, 0, false, false, true), this);
            living.addStatusEffect(
                    new StatusEffectInstance(StatusEffects.SLOWNESS, 80, 0, false, false, true), this);
        }
        return hit;
    }

    @Override
    public boolean damage(DamageSource source, float amount) {
        boolean hurt = super.damage(source, amount);
        if (hurt && source.getAttacker() instanceof PlayerEntity player && this.getRandom().nextInt(3) == 0) {
            this.blinkNear(player);
        }
        return hurt;
    }

    private boolean canAffect(PlayerEntity player) {
        return !player.isCreative() && !player.isSpectator() && player.squaredDistanceTo(this) < 576.0;
    }

    /** Finds the nearest non-creative player currently staring straight at this entity. */
    @Nullable
    private PlayerEntity findStaringPlayer() {
        PlayerEntity nearest = null;
        double best = 40.0 * 40.0;
        for (PlayerEntity player : this.getWorld().getPlayers()) {
            if (player.isCreative() || player.isSpectator()) {
                continue;
            }
            double distance = player.squaredDistanceTo(this);
            if (distance < best && this.isStaring(player)) {
                best = distance;
                nearest = player;
            }
        }
        return nearest;
    }

    private boolean isStaring(PlayerEntity player) {
        if (!player.canSee(this)) {
            return false;
        }
        Vec3d look = player.getRotationVec(1.0f);
        Vec3d to = this.getEyePos().subtract(player.getEyePos()).normalize();
        return look.dotProduct(to) > 0.985;
    }

    /**
     * Teleports to a random solid spot 6-15 blocks around the given player.
     * Returns false when no valid spot was found (it simply stays put).
     */
    private boolean blinkNear(PlayerEntity player) {
        ServerWorld world = (ServerWorld) this.getWorld();
        Vec3d from = this.getPos();
        Random random = this.getRandom();

        for (int i = 0; i < 10; i++) {
            double angle = random.nextDouble() * MathHelper.TAU;
            double radius = 6.0 + random.nextDouble() * 9.0;
            BlockPos base = player.getBlockPos().add(
                    (int) (Math.cos(angle) * radius), 0, (int) (Math.sin(angle) * radius));
            BlockPos ground = HollowUtil.findGround(world, base, 6);
            if (ground == null) {
                continue;
            }
            double x = ground.getX() + 0.5;
            double y = ground.getY();
            double z = ground.getZ() + 0.5;
            if (!world.isSpaceEmpty(new Box(x - 0.4, y, z - 0.4, x + 0.4, y + this.getHeight(), z + 0.4))) {
                continue;
            }

            this.teleport(x, y, z, true);
            world.playSound(null, from.x, from.y, from.z,
                    SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.HOSTILE, 0.8f, 1.0f);
            world.playSound(null, x, y, z,
                    SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.HOSTILE, 0.8f, 1.0f);
            world.spawnParticles(ParticleTypes.PORTAL, from.x, from.y + 1.5, from.z,
                    20, 0.5, 1.0, 0.5, 0.4);
            return true;
        }
        return false;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.AMBIENT_CAVE.value();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.ENTITY_ENDERMAN_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.ENTITY_ENDERMAN_DEATH;
    }

    @Override
    protected float getSoundVolume() {
        return 0.6f;
    }
}
