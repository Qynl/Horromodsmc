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
 * The Hound, after the wiki: a humanoid dog with animalistic behaviour. It is not always present.
 * It trots until it spots you, then runs you down. The wiki's advice - intimidate it with visual
 * contact and noise - is modelled literally: meet its eyes up close and make noise and it hesitates,
 * giving you a moment to back away. Break eye contact or turn your back and it charges.
 */
public class HoundEntity extends Entity {

    private static final int STATE_WANDER = 0;
    private static final int STATE_CHASE = 1;

    private int age = 0;
    private int state = STATE_WANDER;
    private int attackCooldown = 0;
    private int loseTimer = 0;
    private int stepSound = 0;
    private int farTimer = 0;
    private double wanderAngle;

    public HoundEntity(EntityType<? extends Entity> type, World world) {
        super(type, world);
        this.wanderAngle = random.nextDouble() * Math.PI * 2;
    }

    public HoundEntity(World world, double x, double y, double z) {
        super(ModEntityTypes.HOUND, world);
        setPos(x, y, z);
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

    /** True when the player is close and looking straight at the hound (and thus intimidating it). */
    private boolean intimidated(PlayerEntity player) {
        if (player.distanceTo(this) > 6) return false;
        Vec3d toHound = getPos().add(0, 0.6, 0).subtract(player.getEyePos()).normalize();
        Vec3d look = player.getRotationVec(1.0f);
        boolean loud = player.isSprinting()
                || Math.hypot(player.getVelocity().x, player.getVelocity().z) > 0.1;
        return toHound.dotProduct(look) > 0.85 && loud;
    }

    @Override
    public void tick() {
        super.tick();
        if (getWorld().isClient()) {
            return;   // server-side simulation only; the client interpolates
        }
        age++;
        if (attackCooldown > 0) attackCooldown--;

        PlayerEntity quarry = null;
        double nearest = Double.MAX_VALUE;
        for (PlayerEntity player : getWorld().getPlayers()) {
            double dist = player.distanceTo(this);
            if (dist < nearest) nearest = dist;
            if (dist < 22 && quarry == null) {
                quarry = player;
            }
        }
        if (nearest > 80 || nearest == Double.MAX_VALUE) {
            if (++farTimer > 20 * 20) discard();
            return;
        }
        farTimer = 0;

        boolean intimidated = quarry != null && intimidated(quarry);
        if (quarry != null && !intimidated) {
            if (state != STATE_CHASE) {
                getWorld().playSound(null, getX(), getY(), getZ(),
                        ModSoundEvents.HOUND_BARK, SoundCategory.HOSTILE, 0.9f, 1.0f);
            }
            state = STATE_CHASE;
            loseTimer = 0;
        } else if (state == STATE_CHASE) {
            if (++loseTimer > 20 * 6) state = STATE_WANDER;
        }

        double speed;
        double dx, dz;
        if (state == STATE_CHASE && quarry != null && !intimidated) {
            speed = 0.40;
            dx = quarry.getX() - getX();
            dz = quarry.getZ() - getZ();
        } else {
            speed = intimidated ? 0.0 : 0.12;   // it paces, or freezes when intimidated
            if (age % 70 == 0) wanderAngle = random.nextDouble() * Math.PI * 2;
            dx = Math.cos(wanderAngle);
            dz = Math.sin(wanderAngle);
        }
        double len = Math.hypot(dx, dz);
        double vy = (onGround || verticalCollision) ? 0.0 : Math.max(getVelocity().y - 0.06, -0.7);
        if (len > 0.001) {
            setVelocity((dx / len) * speed, vy, (dz / len) * speed);
        } else {
            setVelocity(0, vy, 0);
        }
        move(MovementType.SELF, getVelocity());

        if (speed > 0.05 && ++stepSound > (state == STATE_CHASE ? 7 : 14)) {
            stepSound = 0;
            getWorld().playSound(null, getX(), getY(), getZ(),
                    ModSoundEvents.LISTENER_STEP, SoundCategory.HOSTILE,
                    state == STATE_CHASE ? 0.6f : 0.3f, 1.2f);
        }
        if (random.nextInt(90) == 0) {
            getWorld().playSound(null, getX(), getY(), getZ(),
                    ModSoundEvents.HOUND_GROWL, SoundCategory.HOSTILE, 0.6f, 1.0f);
        }

        if (attackCooldown <= 0) {
            for (PlayerEntity player : getWorld().getPlayers()) {
                if (player.distanceTo(this) < 1.5) {
                    player.damage(player.getDamageSources().generic(), 6f);
                    Vec3d away = player.getPos().subtract(getPos()).normalize().multiply(0.6).add(0, 0.4, 0);
                    player.setVelocity(away);
                    attackCooldown = 25;
                    getWorld().playSound(null, getX(), getY(), getZ(),
                            ModSoundEvents.HOUND_BARK, SoundCategory.HOSTILE, 1.0f, 0.8f);
                    break;
                }
            }
        }

        if (age > 20 * 100 && state == STATE_WANDER) {
            discard();
        }
    }
}
