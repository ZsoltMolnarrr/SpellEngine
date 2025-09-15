package net.spell_engine.api.spell.fx;

import net.minecraft.util.Identifier;

import java.util.Objects;
import java.util.Random;

public final class Sound {
    /**
     * The id of the sound.
     * Must be specified with a non-null and non-empty value!
     * If using your own sounds, make sure you register in your mod initializer.
     *
     * Value must be an identifier, formula: "namespace:resource".
     * Example values:
     *   "bettercombat:sword-swing"
     *   "my-mod-id:my-sword-sound"
     */
    private String id = null;

    /**
     * Volume of the sound
     * Has default value, optional to specify.
     */
    private float volume = 1;

    /**
     * Pitch of the sound
     * Has default value, optional to specify.
     */
    private float pitch = 1;


    /**
     * Pitch randomness of the sound.
     * Has default value, optional to specify.
     * Example values:
     *   for additional pitch within a range of +/- 10%, use the value `0.1`
     */
    private float randomness = 0.1F;


    /**
     * This empty initializer is needed for GSON, to support parsing over default values
     */
    public Sound() { }

    public Sound(String id) {
        this.id = id;
    }

    public Sound(Identifier id) {
        this(id.toString());
    }

    public Sound(String id, float volume, float pitch, float randomness) {
        this.id = id;
        this.volume = volume;
        this.pitch = pitch;
        this.randomness = randomness;
    }

    public Sound(Identifier id, float randomness) {
        this(id.toString(), 1F, 1F, randomness);
    }

    public static Sound withRandomness(Identifier id, float randomness) {
        var sound = new Sound(id.toString());
        sound.randomness = randomness;
        return sound;
    }

    public static Sound withVolume(Identifier id, float volume) {
        var sound = new Sound(id.toString());
        sound.volume = volume;
        return sound;
    }

    public String id() {
        return id;
    }

    public float volume() {
        return volume;
    }

    public float pitch() {
        return pitch;
    }

    public float randomness() {
        return randomness;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        var that = (Sound) obj;
        return Objects.equals(this.id, that.id) &&
                Float.floatToIntBits(this.volume) == Float.floatToIntBits(that.volume) &&
                Float.floatToIntBits(this.pitch) == Float.floatToIntBits(that.pitch) &&
                Float.floatToIntBits(this.randomness) == Float.floatToIntBits(that.randomness);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, volume, pitch, randomness);
    }

    @Override
    public String toString() {
        return "Sound[" +
                "id=" + id + ", " +
                "volume=" + volume + ", " +
                "pitch=" + pitch + ", " +
                "randomness=" + randomness + ']';
    }

    // Helper
    public float randomizedPitch() {
        float pitch = (this.randomness() > 0)
                ? SoundRandom.rng.nextFloat(this.pitch() - this.randomness(), this.pitch() + this.randomness())
                : this.pitch();
        return pitch;
    }

    public Sound copy() {
        var copy = new Sound(this.id);
        copy.volume = this.volume;
        copy.pitch = this.pitch;
        copy.randomness = this.randomness;
        return copy;
    }
}