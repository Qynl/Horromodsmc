package dev.qynl.backrooms.audio;

import dev.qynl.backrooms.config.BackroomsConfig;
import dev.qynl.backrooms.level.BackroomsLevels;
import dev.qynl.backrooms.level.LevelTheme;
import dev.qynl.backrooms.registry.ModSoundEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.registry.RegistryKey;
import net.minecraft.sound.SoundCategory;
import net.minecraft.world.World;
import net.minecraft.util.math.random.Random;

/**
 * The client-side soundscape. There is deliberately almost no music; the level is carried by the
 * fluorescent buzz, a distant electrical hum and rare, deniable one-shots (drips, wall sounds).
 * Volume is tied to how many lights are near the player, so dark areas get quieter &mdash; and
 * therefore scarier.
 */
public final class AmbientDirector {

    private static SoundInstance buzz;
    private static SoundInstance hum;
    private static long nextAmbientTick = 0;
    private static final Random random = Random.create();

    public static void onClientTick(MinecraftClient client) {
        if (client.world == null || client.player == null) {
            stopAll(client);
            return;
        }
        boolean inLevel = isBackrooms(client.world.getRegistryKey());
        float master = BackroomsConfig.get().ambienceVolume;

        if (inLevel) {
            int nearLights = countNearLights(client);
            float buzzVol = (0.03f + 0.05f * Math.min(1, nearLights / 6f)) * master;
            ensureLoop(client, true, buzzVol);
        } else {
            ensureLoop(client, false, 0f);
        }

        if (inLevel && client.world.getTime() >= nextAmbientTick) {
            nextAmbientTick = client.world.getTime() + 400 + random.nextInt(1400);
            playOneShot(client);
        }
    }

    private static int countNearLights(MinecraftClient client) {
        var pos = client.player.getBlockPos();
        int count = 0;
        for (int dx = -6; dx <= 6; dx += 2) {
            for (int dy = -2; dy <= 4; dy += 2) {
                for (int dz = -6; dz <= 6; dz += 2) {
                    var state = client.world.getBlockState(pos.add(dx, dy, dz));
                    if (state.getBlock() instanceof dev.qynl.backrooms.light.FluorescentLightBlock
                            && state.get(dev.qynl.backrooms.light.FluorescentLightBlock.LIT)) {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    private static void ensureLoop(MinecraftClient client, boolean on, float buzzVol) {
        var sm = client.getSoundManager();
        if (on) {
            if (buzz == null) {
                buzz = new BuzzSoundInstance(ModSoundEvents.BUZZ, buzzVol, 1.0f);
                sm.play(buzz);
            }
            if (hum == null) {
                hum = new BuzzSoundInstance(ModSoundEvents.HUM, 0.02f * BackroomsConfig.get().ambienceVolume, 1.0f);
                sm.play(hum);
            }
        } else {
            stopAll(client);
        }
    }

    private static void stopAll(MinecraftClient client) {
        var sm = client.getSoundManager();
        if (buzz != null) {
            sm.stop(buzz);
            buzz = null;
        }
        if (hum != null) {
            sm.stop(hum);
            hum = null;
        }
    }

    private static void playOneShot(MinecraftClient client) {
        var player = client.player;
        double dx = random.nextBetween(-10, 10);
        double dz = random.nextBetween(-10, 10);
        int roll = random.nextInt(4);
        switch (roll) {
            case 0 -> client.world.playSound(player.getX() + dx, player.getY() + 2, player.getZ() + dz,
                    ModSoundEvents.DRIP, SoundCategory.AMBIENT, 0.25f, 0.9f + random.nextFloat() * 0.3f, true);
            case 1 -> client.world.playSound(player.getX() + dx, player.getY(), player.getZ() + dz,
                    ModSoundEvents.WALL_SOUND, SoundCategory.AMBIENT, 0.15f, 1.0f, true);
            default -> {
                // silence is also an event
            }
        }
    }

    private static boolean isBackrooms(RegistryKey<World> key) {
        for (LevelTheme theme : BackroomsLevels.all()) {
            if (theme.dimensionKey().equals(key)) {
                return true;
            }
        }
        return false;
    }

    private AmbientDirector() {
    }
}
