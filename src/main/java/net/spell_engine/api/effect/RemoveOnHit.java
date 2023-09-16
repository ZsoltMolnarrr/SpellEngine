package net.spell_engine.api.effect;

import net.minecraft.entity.effect.StatusEffect;

public interface RemoveOnHit {
    boolean shouldRemoveOnDirectHit();
    StatusEffect removedOnDirectHit(boolean value);

    static void configure(StatusEffect effect, boolean removeOnHit) {
        ((RemoveOnHit)effect).removedOnDirectHit(removeOnHit);
    }

    static boolean shouldRemoveOnDirectHit(StatusEffect effect) {
        return ((RemoveOnHit)effect).shouldRemoveOnDirectHit();
    }
}
