package dev.qynl.backrooms.horror;

import dev.qynl.backrooms.registry.ModEntityTypes;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * A faint figure at the edge of vision. It never approaches, makes no sound, and discards itself the
 * moment a player looks directly at it &mdash; so the player is left asking "did that actually happen?".
 */
public class GlimpseEntity extends Entity {

    private int age = 0;

    public GlimpseEntity(EntityType<? extends Entity> type, World world) {
        super(type, world);
        this.noClip = true;
    }

    public GlimpseEntity(World world, double x, double y, double z) {
        super(ModEntityTypes.GLIMPSE, world);
        this.noClip = true;
        setPos(x, y, z);
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
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public void tick() {
        super.tick();
        age++;
        if (age > 50) {   // ~2.5 seconds
            discard();
            return;
        }
        for (PlayerEntity player : getWorld().getPlayers()) {
            if (player.squaredDistanceTo(this) > 400) continue;
            Vec3d toEntity = this.getPos().add(0, 0.9, 0).subtract(player.getEyePos()).normalize();
            Vec3d look = player.getRotationVec(1.0f);
            if (toEntity.dotProduct(look) > 0.965) {   // looked straight at it: gone
                discard();
                return;
            }
        }
    }
}
