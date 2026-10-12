package dev.qynl.backrooms;

import dev.qynl.backrooms.command.BackroomsCommands;
import dev.qynl.backrooms.horror.HorrorDirector;
import dev.qynl.backrooms.level.BackroomsLevelBuilder;
import dev.qynl.backrooms.level.BackroomsLevels;
import dev.qynl.backrooms.light.LightFlickerSystem;
import dev.qynl.backrooms.registry.ModBlockEntities;
import dev.qynl.backrooms.registry.ModChunkGenerators;
import dev.qynl.backrooms.registry.ModBlocks;
import dev.qynl.backrooms.registry.ModEntityTypes;
import dev.qynl.backrooms.registry.ModFeatures;
import dev.qynl.backrooms.registry.ModItems;
import dev.qynl.backrooms.registry.ModSoundEvents;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.event.lifecycle.v1.ChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.text.Text;
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
        BackroomsLevelBuilder.register();
        registerItemGroup();
        ChunkEvents.CHUNK_UNLOAD.register((chunk, world) ->
                LightFlickerSystem.onChunkUnload(world.getRegistryKey(), chunk.getPos().toLong()));

        ServerTickEvents.END_SERVER_TICK.register(HorrorDirector::onServerTick);
        ServerTickEvents.END_SERVER_TICK.register(LightFlickerSystem::onServerTick);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                HorrorDirector.forgetPlayer(handler.getPlayer().getUuid()));
    }

    /** Without a group the mod's items appear nowhere in the creative inventory, only via /give. */
    private static void registerItemGroup() {
        RegistryKey<ItemGroup> group =
                RegistryKey.of(RegistryKeys.ITEM_GROUP, Identifier.of(MOD_ID, "main"));
        Registry.register(Registries.ITEM_GROUP, group, FabricItemGroup.builder()
                .displayName(Text.translatable("itemGroup.backrooms.main"))
                .icon(() -> new ItemStack(ModItems.WALLPAPER))
                .build());
        ItemGroupEvents.modifyEntriesEvent(group).register(entries -> {
            for (net.minecraft.item.Item item : ModItems.ALL) {
                entries.add(item);
            }
        });
    }

    /** Referencing each registry's statics forces its class initialiser (and thus its registrations). */
    private static void forceRegistration() {
        Object ignored = ModSoundEvents.BUZZ;
        ignored = ModBlocks.WALLPAPER;
        ignored = ModItems.WALLPAPER;
        ignored = ModBlockEntities.REALITY_TEAR;
        ignored = ModFeatures.LEVEL0_BUILD;
        ignored = ModEntityTypes.GLIMPSE;
        ignored = ModChunkGenerators.LEVEL_GEN;
        if (ignored == null) {
            throw new IllegalStateException("backrooms registration failed");
        }
    }
}
