package net.spell_engine.internals.cost;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.entry.RegistryEntry;
import net.spell_engine.SpellEngineMod;
import net.spell_engine.api.spell.Spell;
import net.spell_engine.api.tags.SpellEngineItemTags;
import net.spell_engine.internals.SpellParameters;
import net.spell_engine.internals.casting.SpellCasterEntity;
import net.spell_engine.internals.container.SpellContainerSource;
import net.spell_engine.utils.ItemCooldownManagerExtension;

/// Imposing spell cooldowns as part of a cast's cost: the spell's own cooldown (tracked by
/// {@link SpellCooldownManager}), plus the optional item-level lock on the hosting item.
public class SpellCooldowns {

    public static void imposeCooldown(PlayerEntity player, SpellContainerSource.SourcedContainer source, RegistryEntry<Spell> spellEntry, float progress) {
        var spell = spellEntry.value();
        var duration = cooldownToSet(player, spellEntry, progress);
        var durationTicks = Math.round(duration * 20F);
        if (duration > 0) {
            ((SpellCasterEntity) player).getCooldownManager().set(spellEntry, durationTicks);
        }
        if (SpellEngineMod.config.spell_item_cooldown_lock && spell.cost.cooldown.hosting_item && source.itemStack() != null) {
            var hostingItem = source.itemStack().getItem();
            var itemCooldowns = player.getItemCooldownManager();
            if (source.itemStack().isIn(SpellEngineItemTags.SPELL_BOOK)) {
                durationTicks += (int) (SpellEngineMod.config.spell_book_additional_cooldown * 20F);
            }
            var durationLeft = ((ItemCooldownManagerExtension)itemCooldowns).SE_getLastCooldownDuration(hostingItem)
                    * itemCooldowns.getCooldownProgress(hostingItem, 0);
            if (durationTicks > durationLeft) {
                itemCooldowns.set(hostingItem, durationTicks);
            }
        }
    }

    private static float cooldownToSet(LivingEntity caster, RegistryEntry<Spell> spellEntry, float progress) {
        var spell = spellEntry.value();
        if (spell.cost.cooldown.proportional) {
            return SpellParameters.getCooldownDuration(caster, spellEntry) * progress;
        } else {
            return SpellParameters.getCooldownDuration(caster, spellEntry);
        }
    }

    /// The `attempt_duration` cooldown, set at the moment a delivery is attempted (as opposed to the
    /// full cooldown above, which is only imposed once the delivery reports success).
    public static void imposeAttemptCooldown(PlayerEntity player, RegistryEntry<Spell> spellEntry) {
        var spell = spellEntry.value();
        if (spell.cost.cooldown != null) {
            var attemptCooldown = spell.cost.cooldown.attempt_duration;
            if (attemptCooldown > 0) {
                var durationTicks = Math.round(attemptCooldown * 20F);
                ((SpellCasterEntity) player).getCooldownManager().set(spellEntry, durationTicks);
            }
        }
    }
}
