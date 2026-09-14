package dev.qynl.backrooms.registry;

import dev.qynl.backrooms.BackroomsMod;
import dev.qynl.backrooms.horror.GlimpseEntity;
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

    private ModEntityTypes() {
    }
}
