package net.combatspells.api.spell;

import java.util.Objects;

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
     * This empty initializer is needed for GSON, to support parsing over default values
     */
    public Sound() { }

    public Sound(String id) {
        this.id = id;
    }

    /**
     * Pitch randomness of the sound.
     * Has default value, optional to specify.
     * Example values:
     *   for additional pitch within a range of +/- 10%, use the value `0.1`
     */
    private float randomness = 0.1F;

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
        return "SoundV2[" +
                "id=" + id + ", " +
                "volume=" + volume + ", " +
                "pitch=" + pitch + ", " +
                "randomness=" + randomness + ']';
    }
}