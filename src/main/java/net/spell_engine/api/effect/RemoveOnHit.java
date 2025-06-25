package net.spell_engine.api.effect;

import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.world.World;
import net.spell_engine.entity.DamageSourceExtension;
import org.jetbrains.annotations.Nullable;

public interface RemoveOnHit {
    enum Mode { ANY_HIT, DIRECT_HIT, INDIRECT_HIT; }
    record Args(Mode mode, float chance) { }

    @Nullable Args getRemovalOnHit();
    StatusEffect setRemovalOnHit(Args args);

    @Deprecated
    static void configure(StatusEffect effect, boolean removeOnHit) {
        configure(effect, Mode.ANY_HIT);
    }

    static void configure(StatusEffect effect, Mode condition) {
        configure(effect, condition, 1.0F);
    }

    static void configure(StatusEffect effect, Mode condition, float chance) {
        ((RemoveOnHit)effect).setRemovalOnHit(new Args(condition, chance));
    }

    static boolean shouldRemoveOnHit(World world, StatusEffect effect, DamageSource damageSource) {
        var args = ((RemoveOnHit)effect).getRemovalOnHit();
        if (args == null) {
            return false;
        }
        if (args.chance < 1.0F && world.random.nextFloat() > args.chance) {
            return false;
        }
        var isInDirect = !damageSource.isDirect() || ((DamageSourceExtension)damageSource).isSpellIndirect();
        return switch (args.mode) {
            case ANY_HIT -> true;
            case DIRECT_HIT -> !isInDirect;
            case INDIRECT_HIT -> isInDirect;
        };
    }
}
