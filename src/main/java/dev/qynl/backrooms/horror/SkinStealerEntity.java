package dev.qynl.backrooms.horror;

import dev.qynl.backrooms.registry.ModEntityTypes;
import dev.qynl.backrooms.registry.ModSoundEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.List;

/**
 * The Skin-Stealer, after the wiki: it wears a stolen skin and walks among you like a player.
 *
 * <p>Alone, it takes <em>your</em> skin: it follows you at a polite distance, silent, saying nothing
 * - just another wanderer, except you are the only wanderer. After a while its head begins to
 * <em>clack</em> over to the left, further and further, until the disguise splits and the real thing
 * underneath hunts you down.
 *
 * <p>In a group it is crueler: it wears the skin of someone who is not near you (or who has died),
 * so the others wave at a friend who is somewhere else entirely.
 */
public class SkinStealerEntity extends Entity {

    public static final int STALK = 0;
    public static final int CLACK = 1;
    public static final int HUNT = 2;

    private static final TrackedData<Integer> STATE =
            DataTracker.registerData(SkinStealerEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Integer> TILT =
            DataTracker.registerData(SkinStealerEntity.class, TrackedDataHandlerRegistry.INTEGER);

    private int age = 0;
    private int stalkTimer = 0;
    private int clackTimer = 0;
    private int attackCooldown = 0;
    private int farTimer = 0;
    private PlayerEntity quarry;

    public SkinStealerEntity(EntityType<? extends Entity> type, World world) {
        super(type, world);
    }

    public SkinStealerEntity(World world, double x, double y, double z) {
        super(ModEntityTypes.SKIN_STEALER, world);
        setPos(x, y, z);
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        builder.add(STATE, STALK);
        builder.add(TILT, 0);
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

    public int skinState() {
        return getDataTracker().get(STATE);
    }

    /** Head roll in degrees, for the renderer's clack. */
    public float headTilt() {
        return getDataTracker().get(TILT);
    }

    /** Picks who to wear: alone, you; in a group, whoever is furthest away (or already gone). */
    private PlayerEntity chooseQuarry() {
        List<? extends PlayerEntity> players = getWorld().getPlayers();
        if (players.isEmpty()) return null;
        if (players.size() == 1) return players.get(0);
        PlayerEntity farthest = players.get(0);
        double best = -1;
        for (PlayerEntity p : players) {
            double d = p.distanceTo(this);
            if (d > best) {
                best = d;
                farthest = p;
            }
        }
        return farthest;
    }

    @Override
    public void tick() {
        super.tick();
        // Server-side only. This tick used to run on the client too, where getWorld().getPlayers()
        // holds client players and the cast to ServerPlayerEntity crashed the game outright.
        if (getWorld().isClient()) {
            return;
        }
        age++;
        if (attackCooldown > 0) attackCooldown--;

        if (quarry == null || quarry.isRemoved() || quarry.distanceTo(this) > 64) {
            quarry = chooseQuarry();
        }
        double nearest = Double.MAX_VALUE;
        for (PlayerEntity p : getWorld().getPlayers()) {
            double d = p.distanceTo(this);
            if (d < nearest) nearest = d;
        }
        if (nearest > 80 || nearest == Double.MAX_VALUE) {
            if (++farTimer > 20 * 20) discard();
            return;
        }
        farTimer = 0;
        int state = getDataTracker().get(STATE);

        double speed;
        double dx, dz;
        if (state == HUNT && quarry != null) {
            speed = 0.42;
            dx = quarry.getX() - getX();
            dz = quarry.getZ() - getZ();
        } else if (quarry != null) {
            // Stalk: keep a polite, player-like distance and pace.
            speed = 0.13;
            dx = quarry.getX() - getX();
            dz = quarry.getZ() - getZ();
            double dist = Math.hypot(dx, dz);
            if (dist < 4.0) speed = 0;          // it waits, watching
        } else {
            speed = 0.05;
            dx = Math.cos(age * 0.01);
            dz = Math.sin(age * 0.01);
        }

        double len = Math.hypot(dx, dz);
        double vy = (onGround || verticalCollision) ? 0.0 : Math.max(getVelocity().y - 0.06, -0.7);
        if (len > 0.001) {
            setVelocity((dx / len) * speed, vy, (dz / len) * speed);
        } else {
            setVelocity(0, vy, 0);
        }
        move(MovementType.SELF, getVelocity());

        // Stalk -> Clack -> Hunt.
        if (state == STALK) {
            if (++stalkTimer > 20 * 30) {   // ~30 seconds of quiet following
                getDataTracker().set(STATE, CLACK);
            }
        } else if (state == CLACK) {
            int tilt = getDataTracker().get(TILT);
            if (++clackTimer % 8 == 0 && tilt < 75) {
                getDataTracker().set(TILT, tilt + 5);
                getWorld().playSound(null, getX(), getY(), getZ(),
                        ModSoundEvents.SKIN_STEALER_CLACK, SoundCategory.HOSTILE, 0.7f, 1.0f);
            }
            if (tilt >= 75) {
                getDataTracker().set(STATE, HUNT);
                getWorld().playSound(null, getX(), getY(), getZ(),
                        ModSoundEvents.SKIN_STEALER_REVEAL, SoundCategory.HOSTILE, 1.0f, 1.0f);
            }
        }

        // Only the revealed thing attacks.
        if (state == HUNT && attackCooldown <= 0 && quarry != null) {
            for (PlayerEntity player : getWorld().getPlayers()) {
                if (player.distanceTo(this) < 1.5) {
                    player.damage(player.getDamageSources().generic(), 8f);
                    Vec3d away = player.getPos().subtract(getPos()).normalize().multiply(0.7).add(0, 0.4, 0);
                    player.setVelocity(away);
                    attackCooldown = 25;
                    break;
                }
            }
        }

        if (age > 20 * 240 && state != HUNT) {
            discard();   // it never got close enough; slip away
        }
    }
}
