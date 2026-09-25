package com.horromods.hollow;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Simple JSON config stored at {@code config/hollow.json}.
 * Missing or corrupt files fall back to defaults (which are written back out).
 */
public class HollowConfig {
    /** Whether The Watcher can spawn at all. */
    public boolean watcherEnabled = true;
    /** Whether the Dread (fear) system ticks for players. */
    public boolean dreadEnabled = true;
    /** Whether Dread-triggered scare events (whispers, apparitions...) fire. */
    public boolean eventsEnabled = true;
    /** Spawn weight of The Watcher in dark overworld spots (zombies are 95). */
    public int watcherSpawnWeight = 6;
    /** Multiplier on all Dread gain (0 disables natural buildup). */
    public float dreadMultiplier = 1.0f;
    /** Chance that a naturally spawning Watcher is the rare Pale variant. */
    public float paleWatcherChance = 0.1f;

    public static HollowConfig load() {
        Path path = FabricLoader.getInstance().getConfigDir().resolve("hollow.json");
        Gson gson = new GsonBuilder().setPrettyPrinting().create();

        if (Files.exists(path)) {
            try (Reader reader = Files.newBufferedReader(path)) {
                HollowConfig config = gson.fromJson(reader, HollowConfig.class);
                if (config != null) {
                    return config;
                }
            } catch (Exception e) {
                Hollow.LOGGER.error("Failed to read hollow.json, falling back to defaults", e);
            }
        }

        HollowConfig defaults = new HollowConfig();
        try {
            if (path.getParent() != null) {
                Files.createDirectories(path.getParent());
            }
            try (Writer writer = Files.newBufferedWriter(path)) {
                gson.toJson(defaults, writer);
            }
        } catch (Exception e) {
            Hollow.LOGGER.error("Failed to write hollow.json", e);
        }
        return defaults;
    }
}
