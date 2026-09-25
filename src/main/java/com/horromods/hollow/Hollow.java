package com.horromods.hollow;

import com.horromods.hollow.block.ModBlocks;
import com.horromods.hollow.dread.DreadManager;
import com.horromods.hollow.entity.ModEntities;
import com.horromods.hollow.item.ModItems;
import com.horromods.hollow.network.DreadPayload;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.tag.BiomeTags;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Hollow — a horror mod for Minecraft.
 *
 * <p>Something stalks the dark. It watches from the tree line, snuffs your
 * torches, and grows bolder the more afraid you are. Keep a Warding Totem on
 * you, stay in the light of a Hallowed Lantern, and whatever you do...
 * don't stare at it for too long.</p>
 */
public final class Hollow implements ModInitializer {
    public static final String MOD_ID = "hollow";
    public static final Logger LOGGER = LoggerFactory.getLogger("Hollow");

    public static HollowConfig CONFIG = new HollowConfig();

    @Override
    public void onInitialize() {
        CONFIG = HollowConfig.load();

        PayloadTypeRegistry.playS2C().register(DreadPayload.ID, DreadPayload.CODEC);

        ModItems.register();
        ModBlocks.register();
        ModEntities.register();
        DreadManager.register();

        // Rare natural spawns in dark corners of the overworld.
        if (CONFIG.watcherEnabled) {
            BiomeModifications.addSpawn(
                    biome -> biome.hasTag(BiomeTags.IS_OVERWORLD),
                    SpawnGroup.MONSTER,
                    ModEntities.WATCHER,
                    CONFIG.watcherSpawnWeight, 1, 1);
        }

        LOGGER.info("Hollow is watching.");
    }
}
