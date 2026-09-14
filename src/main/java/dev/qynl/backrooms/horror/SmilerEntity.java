package dev.qynl.backrooms.horror;

import dev.qynl.backrooms.registry.ModEntityTypes;
import dev.qynl.backrooms.registry.ModSoundEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * The Smiler, after the wiki: a grinning face that hangs in the dark and is drawn to light. It is
 * not always present. It drifts, barely visible, until a player brings light (or noise) near it -
 * then it turns and charges, screeching. Contact hurts and it recoils, so a light is both a beacon
 * and a weapon: throw it away and you are in the dark with it.
 */
public class SmilerEntity extends Entity {

    private static final int STATE_DRIFT = 0;
    private static final int STATE_CHASE = 1;

    private int age = 0;
    private int state = STATE_DRIFT;
    private int attackCooldown = 0;
    private int loseTimer = 0;
    private double driftAngle;
    private double bob;

    public SmilerEntity(EntityType<? extends Entity> type, World world) {
        super(type, world);
        this.driftAngle = random.nextDouble() * Math.PI * 2;
    }

    public SmilerEntity(World world, double x, double y, double z) {
        super(ModEntityTypes.SMILER, world);
        setPos(x, y, z);
        this.driftAngle = random.nextDouble() * Math.PI * 2;
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

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public void tick() {
        super.tick();
        age++;
        if (attackCooldown > 0) attackCooldown--;
        bob += 0.06;

        // It is drawn to light and to noise. Find the brightest, loudest player within reach.
        PlayerEntity quarry = null;
        double bestScore = 0;
        for (PlayerEntity player : getWorld().getPlayers()) {
            double dist = player.distanceTo(this);
            if (dist > 20) continue;
            int light = getWorld().getLightLevel(player.getBlockPos());
            double hSpeed = Math.hypot(player.getVelocity().x, player.getVelocity().z);
            double score = light + (player.isSprinting() ? 8 : 0) + hSpeed * 20;
            if (score > 2 && score > bestScore) {
                bestScore = score;
                quarry = player;
            }
        }

        if (quarry != null) {
            if (state != STATE_CHASE && random.nextInt(3) == 0) {
                getWorld().playSound(null, getX(), getY(), getZ(),
                        ModSoundEvents.SMILER_CHASE, SoundCategory.HOSTILE, 0.8f, 1.0f);
            }
            state = STATE_CHASE;
            loseTimer = 0;
        } else if (state == STATE_CHASE) {
            if (++loseTimer > 20 * 5) state = STATE_DRIFT;   // five dark seconds and it loses interest
        }

        double speed;
        double dx, dz;
        if (state == STATE_CHASE && quarry != null) {
            speed = 0.30;
            dx = quarry.getX() - getX();
            dz = quarry.getZ() - getZ();
        } else {
            speed = 0.05;
            if (age % 90 == 0) driftAngle = random.nextDouble() * Math.PI * 2;
            dx = Math.cos(driftAngle);
            dz = Math.sin(driftAngle);
        }
        double len = Math.hypot(dx, dz);
        double vy = Math.sin(bob) * 0.02;   // it hovers, bobbing faintly
        if (len > 0.001) {
            setVelocity((dx / len) * speed, vy, (dz / len) * speed);
        } else {
            setVelocity(0, vy, 0);
        }
        move(MovementType.SELF, getVelocity());

        if (random.nextInt(100) == 0) {
            getWorld().playSound(null, getX(), getY(), getZ(),
                    ModSoundEvents.SMILER_IDLE, SoundCategory.HOSTILE, 0.4f, 1.0f);
        }

        if (attackCooldown <= 0) {
            for (PlayerEntity player : getWorld().getPlayers()) {
                if (player.distanceTo(this) < 1.5) {
                    player.damage(player.getDamageSources().generic(), 7f);
                    Vec3d away = player.getPos().subtract(getPos()).normalize().multiply(0.7).add(0, 0.4, 0);
                    player.setVelocity(away);
                    attackCooldown = 30;
                    state = STATE_DRIFT;
                    getWorld().playSound(null, getX(), getY(), getZ(),
                            ModSoundEvents.SMILER_CHASE, SoundCategory.HOSTILE, 1.0f, 0.8f);
                    break;
                }
            }
        }

        if (age > 20 * 120 && state == STATE_DRIFT) {
            discard();
        }
    }
}
