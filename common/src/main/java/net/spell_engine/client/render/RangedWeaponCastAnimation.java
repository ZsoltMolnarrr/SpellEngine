package net.spell_engine.client.render;

import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.MathHelper;
import net.spell_engine.internals.casting.SpellCaster;
import org.jetbrains.annotations.Nullable;

/// Drives the held bow/crossbow pull animation from a spell cast, for spells that opt in with
/// `active.cast.animates_ranged_weapon`.
///
/// Before 1.21.4 this was done by injecting `pull`/`pulling`/`charged` model predicates onto every
/// registered ranged weapon. Item models are data-driven now, so instead the item model *properties*
/// those models dispatch on are patched to report the cast (see the `client.render.item` mixins).
/// Patching the properties rather than the models means vanilla, third-party and Ranged Weapon API
/// bows (which use the vanilla definition shape) animate too, without either side having to opt in.
///
/// Every entry point is best effort: a resolution failure returns `null` (or `false`) and the
/// caller falls through to untouched vanilla behaviour. Item model properties are evaluated every
/// frame for every rendered stack, so throwing here would be catastrophic — hence the blanket
/// catch. Historically this code path was a source of render crashes (#182).
public class RangedWeaponCastAnimation {

    /// Ticks vanilla assumes a full bow draw takes. Vanilla's `bow.json` dispatches on
    /// `minecraft:use_duration` with `"scale": 0.05` (= 1/20), so a cast ratio multiplied by this
    /// lands back in the 0..1 the model thresholds expect, whatever the spell's real cast time is.
    public static final float VANILLA_PULL_TICKS = 20F;

    /// The pull progress this stack should render at, in `[0, 1]`, or `null` when no
    /// ranged-weapon-animating cast is driving it.
    @Nullable public static Float pullRatio(@Nullable ItemStack stack, @Nullable LivingEntity entity) {
        try {
            if (stack == null || stack.isEmpty() || entity == null) {
                return null;
            }
            // `SpellCaster.Entity` is the read surface cast visualization keys on: players via the
            // interactor (or the local player's predicted process) and summons via their synced one.
            if (!(entity instanceof SpellCaster.Entity caster)) {
                return null;
            }
            // Only the weapon actually being cast with — never an off-hand or inventory copy.
            if (entity.getMainHandStack() != stack) {
                return null;
            }
            var process = caster.getSpellCastProcess();
            if (process == null) {
                return null;
            }
            var spellEntry = process.spell();
            if (spellEntry == null) {
                return null;
            }
            var spell = spellEntry.value();
            // A passive spell has no `active`, and an entry can be mid-reload: guard both.
            if (spell == null || spell.active == null || spell.active.cast == null) {
                return null;
            }
            if (!spell.active.cast.animates_ranged_weapon) {
                return null;
            }
            var world = entity.getEntityWorld();
            if (world == null) {
                return null;
            }
            var progress = process.progress(world.getTime());
            if (progress == null) {
                return null;
            }
            return MathHelper.clamp(progress.ratio(), 0F, 1F);
        } catch (Throwable ignored) {
            return null;
        }
    }

    /// Whether a ranged-weapon-animating cast is currently drawing this stack.
    public static boolean isAnimating(@Nullable ItemStack stack, @Nullable LivingEntity entity) {
        return pullRatio(stack, entity) != null;
    }
}
