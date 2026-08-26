package net.spell_engine.internals.casting;

import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.spell_engine.api.spell.event.SpellEvents;
import net.spell_engine.api.spell.registry.SpellRegistry;
import net.spell_engine.internals.cost.Ammo;

/// The gate in front of every cast attempt (cooldown, ammo, injected event verdicts), run on
/// the server before starting or performing a spell, and on the client before a cast request is
/// even sent. Starting/ending the casting process is {@link SpellCastInteractor}'s job; what
/// happens when the process completes (or ticks, for channels) is
/// {@link net.spell_engine.internals.SpellExecution}'s job.
public class SpellCasting {

    // MARK: Cast attempt

    public static SpellCast.Attempt attempt(Player player, ItemStack itemStack, Identifier spellId) {
        return attempt(player, itemStack, spellId, true);
    }

    /// The gate in front of every cast, run on the server before starting or performing a spell, and
    /// on the client before a cast request is even sent. Composes the cooldown and ammo checks with
    /// the PRE/POST attempt events mods can inject a verdict through.
    public static SpellCast.Attempt attempt(Player player, ItemStack itemStack, Identifier spellId, boolean checkAmmo) {
        var caster = (SpellCaster.Player)player;
        var spellEntry = SpellRegistry.from(player.level()).get(spellId).orElse(null);
        if (spellEntry == null) {
            return SpellCast.Attempt.none();
        }
        var spell = spellEntry.value();
        if (SpellEvents.CASTING_ATTEMPT.PRE.isListened()) {
            var args = new SpellEvents.CastingAttemptEvent.Args(player, spellEntry, itemStack);
            var injected = SpellEvents.CASTING_ATTEMPT.PRE.invokeWithResult(listener -> listener.onCastingAttempt(args));
            if (injected != null) {
                return injected;
            }
        }
        if (caster.getCooldownManager().isCoolingDown(spellEntry)) {
            return SpellCast.Attempt.failOnCooldown(new SpellCast.Attempt.OnCooldownInfo());
        }
        if (checkAmmo) {
            var ammoResult = Ammo.ammoForSpell(player, spell, itemStack);
            if (!ammoResult.satisfied()) {
                return SpellCast.Attempt.failMissingItem(new SpellCast.Attempt.MissingItemInfo(ammoResult.item()));
            }
        }
        if (SpellEvents.CASTING_ATTEMPT.POST.isListened()) {
            var args = new SpellEvents.CastingAttemptEvent.Args(player, spellEntry, itemStack);
            var injected = SpellEvents.CASTING_ATTEMPT.POST.invokeWithResult(listener -> listener.onCastingAttempt(args));
            if (injected != null) {
                return injected;
            }
        }
        return SpellCast.Attempt.success();
    }
}
