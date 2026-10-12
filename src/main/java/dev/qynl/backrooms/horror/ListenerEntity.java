package dev.qynl.backrooms.horror;

import dev.qynl.backrooms.config.BackroomsConfig;
import dev.qynl.backrooms.registry.ModEntityTypes;
import dev.qynl.backrooms.registry.ModSoundEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * The Listener. It is blind. It only knows you by sound.
 *
 * <p>Behaviour, per the design brief: it is <em>not</em> always present; it wanders, and it only
 * becomes interested when it hears you. Running (sprinting) is loud and draws it from far away;
 * walking is audible up close; <strong>sneaking is silent</strong>, so a sneaking player simply does
 * not exist to it. Once it hears you it pursues your last known position; lose it by going quiet and
 * it gives up and drifts back to wandering. It is never a guaranteed kill &mdash; contact hurts and
 * it recoils, giving you a window to run (loudly) or hide (quietly).
 */
public class ListenerEntity extends Entity {

    private static final int STATE_WANDER = 0;
    private static final int STATE_CHASE = 1;

    private int age = 0;
    private int state = STATE_WANDER;
    private double targetX;
    private double targetZ;
    private int loseTimer = 0;
    private int wanderTimer = 0;
    private double wanderAngle;
    private int attackCooldown = 0;
    private int stepSound = 0;
    private int farTimer = 0;

    public ListenerEntity(EntityType<? extends Entity> type, World world) {
        super(type, world);
        this.targetX = getX();
        this.targetZ = getZ();
        this.wanderAngle = random.nextDouble() * Math.PI * 2;
    }

    public ListenerEntity(World world, double x, double y, double z) {
        super(ModEntityTypes.LISTENER, world);
        setPos(x, y, z);
        this.targetX = x;
        this.targetZ = z;
        this.wanderAngle = random.nextDouble() * Math.PI * 2;
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
    }

    @Override
    protected void readCustomDataFromNbt(NbtCompound nbt) {
    }

    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) {
    }

    @Override
    public boolean isCollidable() {
        return true;
    }

    /** Loudness of a player, 0..1. Sneaking is silent; sprinting is a drum. */
    private float loudness(PlayerEntity player) {
        if (player.isSneaking()) return 0f;
        double hSpeed = Math.hypot(player.getVelocity().x, player.getVelocity().z);
        if (player.isSprinting()) return 1f;
        if (hSpeed > 0.11) return 0.55f;   // walking
        return hSpeed > 0.02 ? 0.15f : 0f; // shifting in place is nearly silent
    }

    @Override
    public void tick() {
        super.tick();
        // The server owns this entity's simulation; the client copy only interpolates. Running the
        // AI on both sides made it move twice as fast and rubber-band against the server position.
        if (getWorld().isClient()) {
            return;
        }
        age++;
        if (attackCooldown > 0) attackCooldown--;

        PlayerEntity quarry = null;
        float heard = 0f;
        double nearest = Double.MAX_VALUE;
        for (PlayerEntity player : getWorld().getPlayers()) {
            double dist = player.distanceTo(this);
            if (dist < nearest) nearest = dist;
            float loud = loudness(player);
            if (loud <= 0f) continue;                       // sneaking: invisible to it
            float radius = BackroomsConfig.get().listenerHearing * (0.4f + loud);
            if (dist < radius && loud > heard) {
                heard = loud;
                quarry = player;
            }
        }
        if (nearest > 80 || nearest == Double.MAX_VALUE) {
            if (++farTimer > 20 * 20) discard();            // nobody left to haunt
            return;
        }
        farTimer = 0;

        if (quarry != null) {
            state = STATE_CHASE;
            loseTimer = 0;
            targetX = quarry.getX();
            targetZ = quarry.getZ();
            if (random.nextInt(40) == 0) {
                getWorld().playSound(null, getX(), getY(), getZ(),
                        ModSoundEvents.LISTENER_CHASE, SoundCategory.HOSTILE, 0.8f, 0.9f);
            }
        } else if (state == STATE_CHASE) {
            loseTimer++;
            if (loseTimer > 20 * 6) {   // six quiet seconds and it loses you
                state = STATE_WANDER;
            }
        }

        double speed = state == STATE_CHASE ? 0.34 : 0.10;
        double dx = targetX - getX();
        double dz = targetZ - getZ();
        double len = Math.hypot(dx, dz);

        if (state == STATE_WANDER) {
            wanderTimer--;
            if (wanderTimer <= 0) {
                wanderTimer = 40 + random.nextInt(120);
                wanderAngle = random.nextDouble() * Math.PI * 2;
                // sometimes stop entirely; stillness is part of it too
                if (random.nextInt(4) == 0) speed = 0;
            }
            dx = Math.cos(wanderAngle);
            dz = Math.sin(wanderAngle);
            len = 1;
        }

        // Plain Entities get no gravity from anywhere else: without this the vertical velocity
        // never decays and a wall bump while chasing sent it into the ceiling for good.
        double vy = (onGround || verticalCollision) ? 0.0 : Math.max(getVelocity().y - 0.06, -0.7);
        if (horizontalCollision && state == STATE_CHASE && onGround) {
            vy = 0.42;   // step up when it bumps a wall it is determined about
        }
        if (len > 0.001) {
            setVelocity((dx / len) * speed, vy, (dz / len) * speed);
        } else {
            setVelocity(0, vy, 0);
        }
        move(MovementType.SELF, getVelocity());

        // Audible footsteps while it moves, so you can tell it is coming.
        if (speed > 0.05 && ++stepSound > (state == STATE_CHASE ? 9 : 16)) {
            stepSound = 0;
            getWorld().playSound(null, getX(), getY(), getZ(),
                    ModSoundEvents.LISTENER_STEP, SoundCategory.HOSTILE,
                    state == STATE_CHASE ? 0.7f : 0.35f, 1.0f);
        }
        if (random.nextInt(120) == 0) {
            getWorld().playSound(null, getX(), getY(), getZ(),
                    ModSoundEvents.LISTENER_IDLE, SoundCategory.HOSTILE, 0.3f, 1.0f);
        }

        // Contact: hurt, screech, recoil. Never a guaranteed kill.
        if (attackCooldown <= 0) {
            for (PlayerEntity player : getWorld().getPlayers()) {
                if (player.distanceTo(this) < 1.4) {
                    player.damage(player.getDamageSources().generic(), 6f);
                    Vec3d away = player.getPos().subtract(getPos()).normalize().multiply(0.6).add(0, 0.4, 0);
                    player.setVelocity(away);
                    attackCooldown = 30;
                    state = STATE_WANDER;   // it flinches back too
                    getWorld().playSound(null, getX(), getY(), getZ(),
                            ModSoundEvents.LISTENER_CHASE, SoundCategory.HOSTILE, 1.0f, 0.7f);
                    break;
                }
            }
        }

        // It was never meant to stay: wander off and despawn after a while.
        if (age > 20 * 90 && state == STATE_WANDER) {
            discard();
        }
    }
}
