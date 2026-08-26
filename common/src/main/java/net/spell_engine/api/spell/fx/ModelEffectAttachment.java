package net.spell_engine.api.spell.fx;

import net.minecraft.world.entity.LivingEntity;
import net.spell_engine.internals.SpellEngineAttachments;

import java.util.ArrayList;
import java.util.List;

/// Model FX following a living entity, kept as a synced entity attachment
/// (`SpellEngineAttachments.MODEL_FX`): the server attaches and expires entries, every tracking
/// client renders the mirrored list.
public class ModelEffectAttachment {
    /// Immutable snapshot of the entity's attached model FX (both sides).
    public static List<Entry> of(LivingEntity entity) {
        return SpellEngineAttachments.MODEL_FX.get(entity);
    }

    /// Server side: attaches `effect` starting at `worldTime`, and syncs the new list.
    public static void attach(LivingEntity entity, ModelEffect effect, long worldTime) {
        var entries = new ArrayList<>(of(entity));
        entries.add(new Entry(effect, worldTime, effect.duration));
        SpellEngineAttachments.MODEL_FX.set(entity, List.copyOf(entries));
    }

    /// Server side: drops entries that expired at `worldTime`; syncs only when something changed.
    public static void expire(LivingEntity entity, long worldTime) {
        var entries = of(entity);
        if (entries.isEmpty()) { return; }
        var remaining = entries.stream().filter(e -> worldTime < e.expiresAtWorldTime()).toList();
        if (remaining.size() != entries.size()) {
            SpellEngineAttachments.MODEL_FX.set(entity, remaining);
        }
    }

    public record Entry(ModelEffect effect, long appliedAtWorldTime, int duration) {
        public long expiresAtWorldTime() { return appliedAtWorldTime + duration; }
    }
}
