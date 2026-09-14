package dev.qynl.backrooms.registry;

import dev.qynl.backrooms.BackroomsMod;
import dev.qynl.backrooms.hole.RealityTearBlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public final class ModBlockEntities {

    public static final BlockEntityType<RealityTearBlockEntity> REALITY_TEAR =
            Registry.register(Registries.BLOCK_ENTITY_TYPE,
                    Identifier.of(BackroomsMod.MOD_ID, "reality_tear"),
                    BlockEntityType.Builder.create(RealityTearBlockEntity::new, ModBlocks.REALITY_TEAR).build(null));

    private ModBlockEntities() {
    }
}
