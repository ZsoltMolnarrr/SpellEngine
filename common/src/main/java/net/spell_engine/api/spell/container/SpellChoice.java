package net.spell_engine.api.spell.container;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record SpellChoice(
        /// Pool (spell tag) of spells the choice refers to.
        /// For example: `#wizards:fire`
        String pool) {

    public static final Codec<SpellChoice> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.optionalFieldOf("pool", "").forGetter(x -> x.pool)
    ).apply(instance, SpellChoice::new));

    public static final SpellChoice EMPTY = new SpellChoice("");
    public static SpellChoice of(String pool) {
        return new SpellChoice(pool);
    }

    public boolean isEmpty() {
        return this.pool.isEmpty();
    }
}
