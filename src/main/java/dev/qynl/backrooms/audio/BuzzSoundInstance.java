package dev.qynl.backrooms.audio;

import net.minecraft.client.sound.AbstractSoundInstance;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.math.random.Random;

/**
 * A non-positional, infinitely looping layer used for the fluorescent buzz and the distant hum.
 * {@code relative} detaches it from any position so it follows the player like room tone.
 */
public class BuzzSoundInstance extends AbstractSoundInstance {

    public BuzzSoundInstance(SoundEvent event, float volume, float pitch) {
        super(event, SoundCategory.AMBIENT, Random.create());
        this.repeatType = RepeatType.ALWAYS;
        this.volume = volume;
        this.pitch = pitch;
        this.relative = true;
        this.x = 0;
        this.y = 0;
        this.z = 0;
    }
}
