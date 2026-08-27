package net.spell_engine.api.effect;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.level.Level;
import net.spell_engine.entity.DamageSourceExtension;
import org.jetbrains.annotations.Nullable;

public interface RemoveOnHit {
    enum Trigger { ANY_HIT, DIRECT_HIT, INDIRECT_HIT; }
    /**
     * The removal condition for the status effect.
     * @param trigger   - when the effect should be removed
     * @param count     - how many stacks should be removed, (0 for all)
     * @param chance    - the chance of removal, 1.0F for always, 0.0F for never
     */
    record Args(Trigger trigger, int count, float chance) { }

    @Nullable Args getRemovalOnHit();
    MobEffect setRemovalOnHit(Args args);

    static void configure(MobEffect effect, Trigger condition) {
        configure(effect, condition, 1.0F);
    }

    static void configure(MobEffect effect, Trigger condition, float chance) {
        ((RemoveOnHit)effect).setRemovalOnHit(new Args(condition, 1, chance));
    }

    static void configure(MobEffect effect, Trigger condition, int count, float chance) {
        ((RemoveOnHit)effect).setRemovalOnHit(new Args(condition, count, chance));
    }

    static boolean shouldRemoveOnHit(Level world, MobEffect effect, DamageSource damageSource) {
        var args = ((RemoveOnHit)effect).getRemovalOnHit();
        if (args == null) {
            return false;
        }
        if (args.chance < 1.0F && world.getRandom().nextFloat() > args.chance) {
            return false;
        }
        var isInDirect = !damageSource.isDirect() || ((DamageSourceExtension)damageSource).isSpellIndirect();
        return switch (args.trigger) {
            case ANY_HIT -> true;
            case DIRECT_HIT -> !isInDirect;
            case INDIRECT_HIT -> isInDirect;
        };
    }

    /**
     * @return
     * -1 -> full removal
     * 0 -> no removal
     * 1+ -> number of stacks to remove
     */
    static int removeCount(Level world, MobEffect effect, DamageSource damageSource) {
        var args = ((RemoveOnHit)effect).getRemovalOnHit();
        return shouldRemoveOnHit(world, effect, damageSource)
                ? (args.count > 0 ? args.count : -1)
                : 0;
    }
}
