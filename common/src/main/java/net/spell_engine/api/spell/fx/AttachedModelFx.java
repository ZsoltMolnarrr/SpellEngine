package net.spell_engine.api.spell.fx;

import net.minecraft.entity.LivingEntity;

import java.util.List;

public class AttachedModelFx {
    public interface Provider {
        List<Entry> SpellEngine_getAttachedModelFx();
        void SpellEngine_attachModelFx(ModelEffect effect, long worldTime);
    }

    public static List<Entry> of(LivingEntity entity) {
        return ((Provider) entity).SpellEngine_getAttachedModelFx();
    }

    public record Entry(ModelEffect effect, long appliedAtWorldTime, int duration) {
        public long expiresAtWorldTime() { return appliedAtWorldTime + duration; }
    }
}
