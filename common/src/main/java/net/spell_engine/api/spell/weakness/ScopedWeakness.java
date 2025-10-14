package net.spell_engine.api.spell.weakness;

import net.spell_engine.api.spell.Spell;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public record ScopedWeakness(@Nullable Spell.Impact.Action.Type impact_type,
                             Spell.Impact.TargetModifier weakness) {
}
