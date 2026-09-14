package dev.qynl.backrooms;

import dev.qynl.backrooms.command.BackroomsCommands;
import dev.qynl.backrooms.horror.HorrorDirector;
import dev.qynl.backrooms.level.BackroomsLevels;
import dev.qynl.backrooms.light.LightFlickerSystem;
import dev.qynl.backrooms.registry.ModBlockEntities;
import dev.qynl.backrooms.registry.ModBlocks;
import dev.qynl.backrooms.registry.ModEntityTypes;
import dev.qynl.backrooms.registry.ModFeatures;
import dev.qynl.backrooms.registry.ModItems;
import dev.qynl.backrooms.registry.ModSoundEvents;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.world.gen.GenerationStep;

public class BackroomsMod implements ModInitializer {

    public static final String MOD_ID = "backrooms";

    @Override
    public void onInitialize() {
        forceRegistration();

        BiomeModifications.addFeature(BiomeSelectors.foundInOverworld(),
                GenerationStep.Feature.SURFACE_STRUCTURES,
                RegistryKey.of(RegistryKeys.PLACED_FEATURE, Identifier.of(MOD_ID, "reality_hole_surface")));
        BiomeModifications.addFeature(BiomeSelectors.foundInOverworld(),
                GenerationStep.Feature.UNDERGROUND_DECORATION,
                RegistryKey.of(RegistryKeys.PLACED_FEATURE, Identifier.of(MOD_ID, "reality_hole_underground")));

        // The Backrooms do not let you dig your way out: blocks cannot be broken inside them.
        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) ->
                !BackroomsLevels.isBackrooms(world.getRegistryKey()));

        BackroomsCommands.register();

        ServerTickEvents.END_SERVER_TICK.register(HorrorDirector::onServerTick);
        ServerTickEvents.END_SERVER_TICK.register(LightFlickerSystem::onServerTick);
    }

    /** Referencing each registry's statics forces its class initialiser (and thus its registrations). */
    private static void forceRegistration() {
        Object ignored = ModSoundEvents.BUZZ;
        ignored = ModBlocks.WALLPAPER;
        ignored = ModItems.WALLPAPER;
        ignored = ModBlockEntities.REALITY_TEAR;
        ignored = ModFeatures.LEVEL0_BUILD;
        ignored = ModEntityTypes.GLIMPSE;
        if (ignored == null) {
            throw new IllegalStateException("backrooms registration failed");
        }
    }
}
