package net.spell_engine.internals.casting;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.spell_engine.api.spell.event.SpellEvents;
import net.spell_engine.api.spell.registry.SpellRegistry;
import net.spell_engine.internals.cost.Ammo;
import net.spell_engine.utils.SoundHelper;

/// Management of the casting *process* — the timed part of a cast, before anything fires:
/// gating the attempt and starting the cast bar. The process state itself lives on
/// {@link SpellCasterEntity} and {@link SpellCast.Process}; what happens when the process
/// completes (or ticks, for channels) is {@link net.spell_engine.internals.SpellExecution}'s job.
public class SpellCasting {

    // MARK: Cast attempt

    public static SpellCast.Attempt attempt(PlayerEntity player, ItemStack itemStack, Identifier spellId) {
        return attempt(player, itemStack, spellId, true);
    }

    /// The gate in front of every cast, run on the server before starting or performing a spell, and
    /// on the client before a cast request is even sent. Composes the cooldown and ammo checks with
    /// the PRE/POST attempt events mods can inject a verdict through.
    public static SpellCast.Attempt attempt(PlayerEntity player, ItemStack itemStack, Identifier spellId, boolean checkAmmo) {
        var caster = (SpellCasterEntity)player;
        var spellEntry = SpellRegistry.from(player.getWorld()).getEntry(spellId).orElse(null);
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

    // MARK: Casting process

    public static void start(PlayerEntity player, Identifier spellId, float speed, int length) {
        var spellEntry = SpellRegistry.from(player.getWorld()).getEntry(spellId).orElse(null);
        if (spellEntry == null) {
            return;
        }
        var spell = spellEntry.value();
        if (spell.active == null) {
            return;
        }
        var itemStack = player.getMainHandStack();
        var attempt = attempt(player, itemStack, spellId);
        if (!attempt.isSuccess()) {
            return;
        }
        // Allow clients to specify their haste without validation
        // var details = SpellParameters.getCastTimeDetails(player, spell);
        var process = new SpellCast.Process(player, spellEntry, itemStack.getItem(), speed, length, player.getWorld().getTime());
        // Every channel starts counting from zero. `clearCasting` resets too, but it is not reached
        // on every path: cancelling a cast to start another one (`cancelSpellCast(false)`) sends no
        // cast sync packet, leaving only the RELEASE request, which early-returns when
        // `attemptCasting` fails. Resetting here makes a stale index impossible to inherit.
        ((SpellCasterEntity) player).setChannelTickIndex(0);
        SpellCastSyncHelper.setCasting(player, process);
        SoundHelper.playSound(player.getWorld(), player, spell.active.cast.start_sound);
    }
}
