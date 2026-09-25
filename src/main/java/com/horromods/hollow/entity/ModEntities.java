package com.horromods.hollow.entity;

import com.horromods.hollow.Hollow;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.SpawnRestriction;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import net.minecraft.world.Heightmap;

public final class ModEntities {
    public static final EntityType<WatcherEntity> WATCHER = Registry.register(
            Registries.ENTITY_TYPE,
            Identifier.of(Hollow.MOD_ID, "watcher"),
            EntityType.Builder.create(WatcherEntity::new, SpawnGroup.MONSTER)
                    .dimensions(EntityDimensions.changing(0.8f, 2.7f))
                    .maxTrackingRange(64)
                    .build());

    public static final EntityType<ApparitionEntity> APPARITION = Registry.register(
            Registries.ENTITY_TYPE,
            Identifier.of(Hollow.MOD_ID, "apparition"),
            EntityType.Builder.create(ApparitionEntity::new, SpawnGroup.MISC)
                    .dimensions(EntityDimensions.changing(0.8f, 2.7f))
                    .maxTrackingRange(64)
                    .build());

    private ModEntities() {
    }

    public static void register() {
        FabricDefaultAttributeRegistry.register(WATCHER, WatcherEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(APPARITION, ApparitionEntity.createAttributes());

        SpawnRestriction.register(WATCHER, SpawnRestriction.Location.ON_GROUND,
                Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, WatcherEntity::canSpawn);
    }
}
