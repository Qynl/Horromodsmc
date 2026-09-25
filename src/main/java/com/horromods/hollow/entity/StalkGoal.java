package com.horromods.hollow.entity;

import java.util.EnumSet;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.Goal;

/**
 * Keeps The Watcher at a menacing distance: it closes in while far away,
 * then stops just outside striking range and slowly circles its prey until
 * the melee goal takes over.
 */
public class StalkGoal extends Goal {
    private static final double APPROACH_DISTANCE = 16.0;
    private static final double HOLD_DISTANCE = 12.0;

    private final WatcherEntity watcher;
    private int stalkTicks;

    public StalkGoal(WatcherEntity watcher) {
        this.watcher = watcher;
        this.setControls(EnumSet.of(Goal.Control.MOVE, Goal.Control.LOOK));
    }

    @Override
    public boolean canStart() {
        LivingEntity target = this.watcher.getTarget();
        if (target == null || !target.isAlive()) {
            return false;
        }
        if (this.watcher.squaredDistanceTo(target) < HOLD_DISTANCE * HOLD_DISTANCE) {
            return false;
        }
        return this.watcher.getRandom().nextInt(5) == 0;
    }

    @Override
    public boolean shouldContinue() {
        LivingEntity target = this.watcher.getTarget();
        if (target == null || !target.isAlive()) {
            return false;
        }
        double distance = this.watcher.squaredDistanceTo(target);
        return distance > (HOLD_DISTANCE - 4.0) * (HOLD_DISTANCE - 4.0) && distance < 48.0 * 48.0;
    }

    @Override
    public void start() {
        this.stalkTicks = 0;
    }

    @Override
    public void tick() {
        LivingEntity target = this.watcher.getTarget();
        if (target == null) {
            return;
        }
        this.stalkTicks++;
        this.watcher.getLookControl().lookAt(target);

        double distance = this.watcher.squaredDistanceTo(target);
        if (distance > APPROACH_DISTANCE * APPROACH_DISTANCE) {
            this.watcher.getNavigation().startMovingTo(target, 1.0);
        } else {
            this.watcher.getNavigation().stop();
            // Occasionally shuffle sideways, circling the prey.
            if (this.stalkTicks % 40 == 0) {
                double angle = this.watcher.getRandom().nextDouble() * Math.PI * 2;
                double x = target.getX() + Math.cos(angle) * APPROACH_DISTANCE * 0.75;
                double z = target.getZ() + Math.sin(angle) * APPROACH_DISTANCE * 0.75;
                this.watcher.getNavigation().startMovingTo(x, target.getY(), z, 0.8);
            }
        }
    }

    @Override
    public void stop() {
        this.watcher.getNavigation().stop();
    }
}
