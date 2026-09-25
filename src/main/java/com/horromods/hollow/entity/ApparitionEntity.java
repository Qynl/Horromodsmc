package com.horromods.hollow.entity;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvent;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * A silent, untouchable silhouette summoned by high Dread. It simply stands
 * there, staring, then dissolves into smoke after a few seconds.
 */
public class ApparitionEntity extends MobEntity {
    private int lifeTicks;

    public ApparitionEntity(EntityType<? extends MobEntity> entityType, World world) {
        super(entityType, world);
        this.lifeTicks = 60 + this.getRandom().nextInt(60);
        this.setInvulnerable(true);
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return MobEntity.createMobAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 1.0)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.0);
    }

    @Override
    protected void initGoals() {
        // It does nothing but watch.
    }

    @Override
    public boolean cannotDespawn() {
        return true;
    }

    @Nullable
    @Override
    protected SoundEvent getAmbientSound() {
        return null;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.getWorld().isClient) {
            return;
        }
        if (--this.lifeTicks <= 0) {
            ((ServerWorld) this.getWorld()).spawnParticles(ParticleTypes.LARGE_SMOKE,
                    this.getX(), this.getY() + 1.4, this.getZ(), 24, 0.4, 1.0, 0.4, 0.02);
            this.discard();
        }
    }
}
