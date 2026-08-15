package net.spell_engine.fx;

import net.minecraft.entity.LivingEntity;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.world.World;
import net.spell_engine.api.spell.Spell;
import net.spell_engine.api.spell.fx.Fx;
import net.spell_engine.internals.SpellModifiers;
import net.spell_engine.internals.SpellParameters;
import net.spell_engine.utils.SoundHelper;

/// The FX of a spell's release moment — the authored `spell.release` visuals and sound, plus any
/// release visuals contributed by the caster's spell modifiers, broadcast anchored on the caster.
public class ReleaseFx {

    public static void send(World world, LivingEntity caster, RegistryEntry<Spell> spellEntry, float progress) {
        var spell = spellEntry.value();
        // The caster's own reach is the one magnitude a release can honestly describe, so it is
        // the only one bound here; effects asking for anything else fall back to their authored size.
        var context = Fx.Context.ofRange(SpellParameters.getRange(caster, spellEntry));
        emitVisuals(world, caster, spell.release.visuals, context);
        for (var modifier: SpellModifiers.of(caster, spellEntry, null)) {
            if (modifier.release != null) {
                emitVisuals(world, caster, modifier.release, context);
            }
        }
        var releaseSound = spell.release.sound;
        // For CHARGE casts, shift the release sound's pitch by the charge ratio (`progress`).
        if (releaseSound != null
                && spell.release.pitch_shift != 0
                && spell.active != null
                && spell.active.cast.type == Spell.Active.Cast.Type.CHARGE) {
            releaseSound = releaseSound.shiftPitch(spell.release.pitch_shift * progress);
        }
        SoundHelper.playSound(world, caster, releaseSound);
    }

    /// Emits a visual bundle anchored on `caster`, resolving each effect's contextual scaling
    /// first. Scaled and unscaled effects go out together, in one particle batch.
    private static void emitVisuals(World world, LivingEntity caster, Fx.Visuals visuals, Fx.Context context) {
        if (visuals == null || visuals.isEmpty()) {
            return;
        }
        var resolved = visuals.resolved(context);
        ParticleHelper.sendBatches(caster, resolved.particles);
        ModelEffectHelper.spawn(world, caster.getPos(), caster.getYaw(), resolved.models, caster);
    }
}
