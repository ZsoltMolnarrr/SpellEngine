package net.spell_engine.api.spell.container;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record SpellChoice(String pool) {
    public static final Codec<SpellChoice> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.optionalFieldOf("pool", "").forGetter(x -> x.pool)
    ).apply(instance, SpellChoice::new));

    public static final SpellChoice EMPTY = new SpellChoice("");
}
