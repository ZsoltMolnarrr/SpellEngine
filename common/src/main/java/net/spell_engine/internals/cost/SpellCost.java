package net.spell_engine.internals.cost;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.spell_engine.SpellEngineMod;
import net.spell_engine.api.spell.Spell;
import net.spell_engine.api.spell.event.SpellEvents;
import net.spell_engine.internals.casting.SpellBatcher;
import net.spell_engine.internals.container.SpellContainerSource;
import net.spell_engine.utils.WorldScheduler;

/// The [COST] stage: everything a successful cast consumes — cooldown, exhaust, durability,
/// ammo/rune items, and cost status effects. Runs from the delivery completion callback, so a
/// delivery that never landed costs nothing (see SpellExecution's flow map).
public class SpellCost {

    public static void consume(Player player, float progress, SpellContainerSource.SourcedContainer spellSource, Identifier spellId, Holder<Spell> spellEntry, ItemStack heldItemStack, Ammo.Result ammoResult, boolean scheduled) {
        var spell = spellEntry.value();
        var batching = spell.cost.batching;
        if (batching && !scheduled) {
            if (((SpellBatcher)player).hasBatchedCost(spellId)) {
                return;
            }
            ((WorldScheduler)player.level()).schedule(0, () -> consume(player, progress, spellSource, spellId, spellEntry, heldItemStack, ammoResult, true));
            ((SpellBatcher)player).batchCost(spellId, true);
            return;
        }

        // Consume things
        // Cooldown
        SpellCooldowns.imposeCooldown(player, spellSource, spellEntry, progress);
        // Exhaust
        player.causeFoodExhaustion(spell.cost.exhaust * SpellEngineMod.config.spell_cost_exhaust_multiplier);
        // Durability
        if (SpellEngineMod.config.spell_cost_durability_allowed && spell.cost.durability > 0) {
            var stackToDamage = (spellSource.itemStack() != null && spellSource.itemStack().isDamageableItem()) ? spellSource.itemStack() : heldItemStack;
            stackToDamage.hurtAndBreak(spell.cost.durability, player, EquipmentSlot.MAINHAND);
        }
        // Item
        Ammo.consume(ammoResult, player);
        // Status effect
        if (spell.cost.effect_id != null) {
            var effect = BuiltInRegistries.MOB_EFFECT.get(Identifier.parse(spell.cost.effect_id));
            if (effect.isPresent()) {
                player.removeEffect(effect.get());
            }
        }
        if (SpellEvents.COST_CONSUME.isListened()) {
            var args = new SpellEvents.SpellCostConsumeEvent.Args(player, spellEntry, heldItemStack);
            SpellEvents.COST_CONSUME.invoke(l -> l.onSpellCostConsume(args));
        }
    }
}
