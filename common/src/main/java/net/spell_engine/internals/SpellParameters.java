package net.spell_engine.internals;

import net.minecraft.core.Holder;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.spell_engine.SpellEngineMod;
import net.spell_engine.api.effect.InstantCast;
import net.spell_engine.api.spell.Spell;
import net.spell_engine.internals.casting.SpellCast;
import net.spell_power.api.SpellPower;
import net.spell_power.api.SpellSchool;
import org.jetbrains.annotations.Nullable;

/// Derived, read-only spell parameters: range, cast and cooldown durations, channel arithmetic and
/// charge scaling. Pure queries — nothing here touches world state, which is why the client
/// (tooltips, HUD, cast bar) can read the same numbers the server acts on.
public class SpellParameters {

    // MARK: Haste

    public static float hasteAffectedValue(float value, float haste) {
        return value / haste;
    }

    public static float hasteAffectedValue(LivingEntity caster, SpellSchool school, float value) {
        return hasteAffectedValue(caster, school, value, null);
    }

    public static float hasteAffectedValue(LivingEntity caster, SpellSchool school, float value, ItemStack provisionedWeapon) {
        var haste = SpellPower.getHaste(caster, school); // FIXME: ? Provisioned weapon
        return hasteAffectedValue(value, haste) ;
    }

    // MARK: Range
    //
    // The range family:
    // - `getRange(caster, entry)` — the resolved range at full charge, for everything not
    //   tracking a cast process (mob engagement, server lookups of non-charge casts).
    // - `getRange(caster, entry, chargeRatio)` — resolved range at a RAW charge hold ratio
    //   (0..1; the charge curve is applied here). The resolver of actual execution: fire-time
    //   target search, range validation and release FX pass the released ratio.
    // - `getRangeCurved(caster, entry, curvedRatio)` — delivery-stage variant for callers that
    //   carry the ALREADY-CURVED ratio (`ImpactContext.charge`, `AttackContext.charge`).
    //   Passing a raw ratio here skips the curve; passing a curved ratio above doubles it.
    //
    // Bonus combination: when a CHARGE spell's charge bonus scales range (`bonus.range_add` set),
    // ALL range bonuses — the caster's spell modifiers AND the charge bonus — scale together
    // with the curved ratio, so the whole reach grows with the hold. Otherwise modifier bonuses
    // apply flat and the ratio is ignored.

    public static float getRange(LivingEntity caster, Holder<Spell> spellEntry) {
        return getRangeCurved(caster, spellEntry, 1F);
    }

    public static float getRange(LivingEntity caster, Holder<Spell> spellEntry, float chargeRatio) {
        var charge = chargeConfigOf(spellEntry.value());
        return getRangeCurved(caster, spellEntry, charge != null ? charge.curve.apply(chargeRatio) : 1F);
    }

    /// The furthest this spell can ever reach for this caster: the resolved range at full
    /// charge. This is what `SpellCast.Option` flattens — the client runs its expensive
    /// targeting (raycasts, collision checks) at this bound, and the server filters down to
    /// the true (ratio-scaled) range at fire.
    public static float getMaxRange(LivingEntity caster, Holder<Spell> spellEntry) {
        return getRange(caster, spellEntry, 1F);
    }

    public static float getRangeCurved(LivingEntity caster, Holder<Spell> spellEntry, float curvedRatio) {
        var spell = spellEntry.value();
        var range = spell.range;
        if (spell.range_mechanic != null) {
            switch (spell.range_mechanic) {
                case MELEE -> {
                    double meleeRange = 3;
                    if (caster instanceof Player player) {
                        meleeRange = player.entityInteractionRange();
                    }
                    range = (float) (meleeRange + spell.range);
                }
            }
        }
        var bonus = 0F;
        if (caster instanceof Player player) {
            for (var modifier: SpellModifiers.of(player, spellEntry)) {
                bonus += modifier.range_add;
            }
        }
        var charge = chargeConfigOf(spell);
        if (charge != null && charge.bonus.range_add != 0) {
            bonus = (bonus + charge.bonus.range_add) * curvedRatio;
        }
        return range + bonus;
    }

    // MARK: Cast duration

    public static float getCastDuration(LivingEntity caster, Spell spell) {
        return getCastDuration(caster, spell, null);
    }

    public static float getCastDuration(LivingEntity caster, Spell spell, ItemStack provisionedWeapon) {
        if (spell.active != null && spell.active.cast == null) {
            return 0;
        }
        return hasteAffectedValue(caster, spell.school, spell.active.cast.duration, provisionedWeapon);
    }

    public static SpellCast.Duration getCastTimeDetails(LivingEntity caster, Spell spell) {
        if (spell.active == null) { return SpellCast.Duration.EMPTY; }
        var haste = spell.active.cast.haste_affected
                ? (float) SpellPower.getHaste(caster, spell.school)
                : 1F;
        var duration = hasteAffectedValue(spell.active.cast.duration, haste);
        return new SpellCast.Duration(haste, Math.round(duration * 20F));
    }

    public static boolean isInstantCast(Holder<Spell> spellEntry, LivingEntity caster) {
        var spell = spellEntry.value();
        if (spell.active == null) { return true; }
        return spell.active.cast.duration == 0
                || (!isChanneled(spell) && InstantCast.instantify(spellEntry, caster));
    }

    public static boolean isInstant(Spell spell) {
        if (spell.active == null) { return true; }
        return spell.active.cast.duration == 0;
    }

    // MARK: Channel

    public static int channelTicks(LivingEntity caster, Holder<Spell> spellEntry) {
        var ticks = spellEntry.value().active.cast.channelTicks();
        if (caster instanceof Player player) {
            var modifiers = SpellModifiers.of(player, spellEntry);
            for (var modifier: modifiers) {
                ticks += modifier.channel_ticks_add;
            }
        }
        return ticks;
    }

    public static boolean isChanneled(Spell spell) {
        return channelValueMultiplier(spell) != 0;
    }

    public static float channelValueMultiplier(Spell spell) {
        if (spell.active == null) { return 0F; }
        var ticks = spell.active.cast.channelTicks();
        if (ticks <= 0) {
            return 0;
        }
        var interval = (spell.active.cast.duration * 20F) / (float)ticks;
        return interval / 20F;
    }

    // MARK: Cooldown

    public static float getCooldownDuration(LivingEntity caster, Holder<Spell> spellEntry) {
        return getCooldownDuration(caster, spellEntry, null);
    }

    public static float getCooldownDuration(LivingEntity caster, Holder<Spell> spellEntry, ItemStack provisionedWeapon) {
        var spell = spellEntry.value();
        var duration = spell.cost.cooldown.duration;
        if (caster instanceof Player player) {
            duration -= SpellModifiers.cooldownDeduction(player, spellEntry);
        }
        if (duration > 0) {
            if (SpellEngineMod.config.haste_affects_cooldown && spell.cost.cooldown.haste_affected) {
                duration = hasteAffectedValue(caster, spell.school, duration, provisionedWeapon);
            }
        }
        return Math.max(duration, 0);
    }

    // MARK: Charge

    /// The `charge` config of a CHARGE cast, or null for every other spell and cast type.
    @Nullable public static Spell.Active.Cast.Charge chargeConfigOf(@Nullable Spell spell) {
        if (spell == null || spell.active == null || spell.active.cast == null
                || spell.active.cast.type != Spell.Active.Cast.Type.CHARGE) {
            return null;
        }
        return spell.active.cast.charge;
    }

    @Nullable public static Spell.Active.Cast.Charge chargeConfigOf(@Nullable Holder<Spell> spellEntry) {
        return spellEntry == null ? null : chargeConfigOf(spellEntry.value());
    }

    /// Innate output multiplier of a release at `curvedRatio` — the sole place `output_scaling` is
    /// applied, so the call sites (impacts, melee swings, tooltip estimates) cannot drift apart.
    /// Returns `1` for anything that does not charge.
    public static float chargeOutputMultiplier(@Nullable Spell spell, float curvedRatio) {
        var charge = chargeConfigOf(spell);
        return charge == null ? 1F : Mth.lerp(charge.output_scaling, 1F, curvedRatio);
    }

    public static float chargeOutputMultiplier(@Nullable Holder<Spell> spellEntry, float curvedRatio) {
        return chargeOutputMultiplier(spellEntry == null ? null : spellEntry.value(), curvedRatio);
    }
}
