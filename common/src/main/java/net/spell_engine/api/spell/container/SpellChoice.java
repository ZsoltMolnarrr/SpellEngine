package net.spell_engine.api.spell.container;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.component.ComponentChanges;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.Map;

public record SpellChoice(
        /// Pool (spell tag) of spells the choice refers to.
        /// For example: `#wizards:fire`
        String pool,
        /// Component changes to apply to the item once a spell is picked, keyed by the chosen spell id.
        /// Lets the selected spell drive the item's appearance (`custom_model_data`, `custom_name`, ...)
        /// or any other data component. Uses the vanilla component-map syntax, `!namespace:id` removes.
        Map<Identifier, ComponentChanges> apply_on_choice) {

    public static final Codec<SpellChoice> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.optionalFieldOf("pool", "").forGetter(SpellChoice::pool),
            Codec.unboundedMap(Identifier.CODEC, ComponentChanges.CODEC)
                    .optionalFieldOf("apply_on_choice", Map.of()).forGetter(SpellChoice::apply_on_choice)
    ).apply(instance, SpellChoice::new));

    public static final SpellChoice EMPTY = new SpellChoice("", Map.of());

    public static SpellChoice of(String pool) {
        return new SpellChoice(pool, Map.of());
    }

    public static SpellChoice of(String pool, Map<Identifier, ComponentChanges> applyOnChoice) {
        return new SpellChoice(pool, applyOnChoice);
    }

    /// Returns a copy with the given component changes registered for `spellId`.
    /// Applied to the item when that spell is chosen.
    public SpellChoice withApplyOnChoice(Identifier spellId, ComponentChanges changes) {
        var next = new HashMap<>(this.apply_on_choice);
        next.put(spellId, changes);
        return new SpellChoice(this.pool, Map.copyOf(next));
    }

    /// Component changes to apply when `spellId` is chosen, or `ComponentChanges.EMPTY` if none.
    public ComponentChanges applyOnChoiceFor(Identifier spellId) {
        return this.apply_on_choice.getOrDefault(spellId, ComponentChanges.EMPTY);
    }

    public boolean isEmpty() {
        return this.pool.isEmpty();
    }
}
