package dev.qynl.backrooms.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Player/author-facing tuning knobs, persisted as {@code config/backrooms.json}.
 *
 * <p>Deliberately tiny and Gson-based: Gson ships inside the Minecraft runtime, so there is no extra
 * dependency, and every field here is a plain value so the file is trivially hand-editable.
 */
public final class BackroomsConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static BackroomsConfig INSTANCE;

    /** 1-in-N chance a surface chunk gets a hole candidate. Higher = rarer. */
    public int holeRarity = 3000;
    /** Underground holes are this many times more common than surface ones. */
    public float undergroundHoleFactor = 3.0f;
    /** Whether "phantom footsteps" behind walls are enabled. */
    public boolean phantomFootsteps = true;
    /** Whether the level slowly re-solves itself while the player is elsewhere. */
    public boolean realityDrift = true;
    /** Minimum minutes between drift advances. */
    public int driftMinMinutes = 4;
    /** Whether edge-of-vision glimpses are enabled. */
    public boolean glimpses = true;
    /** Master volume for the fluorescent buzz / hum layers. */
    public float ambienceVolume = 1.0f;
    /** Whether distant lights can fail behind the player. */
    public boolean lightsFailBehind = true;

    public static BackroomsConfig get() {
        if (INSTANCE == null) {
            INSTANCE = load();
        }
        return INSTANCE;
    }

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("backrooms.json");
    }

    private static BackroomsConfig load() {
        Path file = path();
        try {
            if (Files.exists(file)) {
                BackroomsConfig cfg = GSON.fromJson(Files.readString(file), BackroomsConfig.class);
                if (cfg != null) {
                    return cfg;
                }
            }
        } catch (Exception ignored) {
            // fall through to defaults
        }
        BackroomsConfig fresh = new BackroomsConfig();
        fresh.save();
        return fresh;
    }

    public void save() {
        try {
            Files.writeString(path(), GSON.toJson(this));
        } catch (Exception ignored) {
            // config is optional; never crash over it
        }
    }
}
