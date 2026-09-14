package dev.qynl.backrooms.registry;

import dev.qynl.backrooms.BackroomsMod;
import dev.qynl.backrooms.horror.GlimpseEntity;
import dev.qynl.backrooms.horror.HoundEntity;
import dev.qynl.backrooms.horror.ListenerEntity;
import dev.qynl.backrooms.horror.SmilerEntity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public final class ModEntityTypes {

    public static final EntityType<GlimpseEntity> GLIMPSE =
            Registry.register(Registries.ENTITY_TYPE, Identifier.of(BackroomsMod.MOD_ID, "glimpse"),
                    EntityType.Builder.<GlimpseEntity>create(GlimpseEntity::new, SpawnGroup.MISC)
                            .dimensions(0.6f, 1.8f)
                            .disableSaving()
                            .makeFireImmune()
                            .build("glimpse"));

    public static final EntityType<ListenerEntity> LISTENER =
            Registry.register(Registries.ENTITY_TYPE, Identifier.of(BackroomsMod.MOD_ID, "listener"),
                    EntityType.Builder.<ListenerEntity>create(ListenerEntity::new, SpawnGroup.MONSTER)
                            .dimensions(0.7f, 2.6f)
                            .makeFireImmune()
                            .build("listener"));

    public static final EntityType<SmilerEntity> SMILER =
            Registry.register(Registries.ENTITY_TYPE, Identifier.of(BackroomsMod.MOD_ID, "smiler"),
                    EntityType.Builder.<SmilerEntity>create(SmilerEntity::new, SpawnGroup.MONSTER)
                            .dimensions(0.9f, 1.4f)
                            .makeFireImmune()
                            .build("smiler"));

    public static final EntityType<HoundEntity> HOUND =
            Registry.register(Registries.ENTITY_TYPE, Identifier.of(BackroomsMod.MOD_ID, "hound"),
                    EntityType.Builder.<HoundEntity>create(HoundEntity::new, SpawnGroup.MONSTER)
                            .dimensions(1.2f, 1.0f)
                            .makeFireImmune()
                            .build("hound"));

    private ModEntityTypes() {
    }
}
