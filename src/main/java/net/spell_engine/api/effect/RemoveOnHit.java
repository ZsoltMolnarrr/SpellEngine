package net.spell_engine.api.effect;

import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffect;
import org.jetbrains.annotations.Nullable;

public interface RemoveOnHit {
    enum Mode { ANY_HIT, DIRECT_HIT, INDIRECT_HIT; }
    record Args(Mode mode) { }

    @Nullable Args getRemovalOnHit();
    StatusEffect setRemovalOnHit(Args args);

    @Deprecated
    static void configure(StatusEffect effect, boolean removeOnHit) {
        configure(effect, Mode.ANY_HIT);
    }

    static void configure(StatusEffect effect, Mode condition) {
        ((RemoveOnHit)effect).setRemovalOnHit(new Args(condition));
    }

    static boolean shouldRemoveOnHit(StatusEffect effect, DamageSource damageSource) {
        var args = ((RemoveOnHit)effect).getRemovalOnHit();
        if (args == null) {
            return false;
        }
        return switch (args.mode) {
            case ANY_HIT -> true;
            case DIRECT_HIT -> damageSource.isDirect();
            case INDIRECT_HIT -> !damageSource.isDirect();
        };
    }
}
