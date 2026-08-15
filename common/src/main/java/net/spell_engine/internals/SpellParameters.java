package net.spell_engine.internals;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.math.MathHelper;
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

    public static float getRange(LivingEntity caster, RegistryEntry<Spell> spellEntry) {
        return getRange(caster, spellEntry, null);
    }

    public static float getRange(LivingEntity caster, RegistryEntry<Spell> spellEntry, @Nullable Spell.Modifier chargeModifier) {
        var spell = spellEntry.value();
        var range = spell.range;
        if (spell.range_mechanic != null) {
            switch (spell.range_mechanic) {
                case MELEE -> {
                    double meleeRange = 3;
                    if (caster instanceof PlayerEntity player) {
                        meleeRange = player.getEntityInteractionRange();
                    }
                    range = (float) (meleeRange + spell.range);
                }
            }
        }
        if (caster instanceof PlayerEntity player) {
            for (var modifier: SpellModifiers.of(player, spellEntry)) {
                if (modifier.range_add != 0) {
                    range += modifier.range_add;
                }
            }
        }
        if (chargeModifier != null) {
            range += chargeModifier.range_add;
        }
        return range;
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

    public static boolean isInstantCast(RegistryEntry<Spell> spellEntry, LivingEntity caster) {
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

    public static int channelTicks(LivingEntity caster, RegistryEntry<Spell> spellEntry) {
        var ticks = spellEntry.value().active.cast.channelTicks();
        if (caster instanceof PlayerEntity player) {
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

    public static float getCooldownDuration(LivingEntity caster, RegistryEntry<Spell> spellEntry) {
        return getCooldownDuration(caster, spellEntry, null);
    }

    public static float getCooldownDuration(LivingEntity caster, RegistryEntry<Spell> spellEntry, ItemStack provisionedWeapon) {
        var spell = spellEntry.value();
        var duration = spell.cost.cooldown.duration;
        if (caster instanceof PlayerEntity player) {
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

    @Nullable public static Spell.Active.Cast.Charge chargeConfigOf(@Nullable RegistryEntry<Spell> spellEntry) {
        return spellEntry == null ? null : chargeConfigOf(spellEntry.value());
    }

    /// Innate output multiplier of a release at `curvedRatio` — the sole place `output_scaling` is
    /// applied, so the call sites (impacts, melee swings, tooltip estimates) cannot drift apart.
    /// Returns `1` for anything that does not charge.
    public static float chargeOutputMultiplier(@Nullable Spell spell, float curvedRatio) {
        var charge = chargeConfigOf(spell);
        return charge == null ? 1F : MathHelper.lerp(charge.output_scaling, 1F, curvedRatio);
    }

    public static float chargeOutputMultiplier(@Nullable RegistryEntry<Spell> spellEntry, float curvedRatio) {
        return chargeOutputMultiplier(spellEntry == null ? null : spellEntry.value(), curvedRatio);
    }
}
