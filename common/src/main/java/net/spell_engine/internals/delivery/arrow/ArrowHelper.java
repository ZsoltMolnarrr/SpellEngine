package net.spell_engine.internals.delivery.arrow;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.*;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;
import net.minecraft.world.World;
import net.spell_engine.api.spell.Spell;
import net.spell_engine.api.spell.fx.Fx;
import net.spell_engine.fx.ModelEffectHelper;
import net.spell_engine.fx.ParticleHelper;
import net.spell_engine.internals.SpellModifiers;
import net.spell_engine.utils.SoundHelper;
import net.spell_engine.utils.WorldScheduler;
import net.spell_engine.internals.casting.SpellCaster;
import net.spell_engine.mixin.item.RangedWeaponAccessor;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.function.Supplier;
import net.spell_engine.internals.SpellExecution;

public class ArrowHelper {
    public static void shootArrow(World world, LivingEntity shooter, RegistryEntry<Spell> spellEntry, SpellExecution.ImpactContext context) {
        shootArrow(world, shooter, spellEntry, context, 0);
    }

    public static void shootArrow(World world, LivingEntity shooter, RegistryEntry<Spell> spellEntry, SpellExecution.ImpactContext context, int sequenceIndex) {
        var spell = spellEntry.value();
        var shoot_arrow = spell.deliver.shoot_arrow;
        var weaponStack = shooter.getMainHandStack();

        // var weapon = weaponStack.getItem();
        // Using CROSSBOW statically, so arrows fired behave consistently
        // When using any bow, divergence is applied in an unhelpful manner
        var weapon = Items.CROSSBOW;
        if (shoot_arrow != null
                && (world instanceof ServerWorld serverWorld)
                && (weapon instanceof RangedWeaponItem rangedWeapon)) {
            var mutableLaunchProperties = shoot_arrow.launch_properties.copy();
            if (shooter instanceof PlayerEntity player) {
                var spellModifiers = SpellModifiers.of(player, spellEntry);
                for (var modifier: spellModifiers) {
                    if (modifier.projectile_launch != null) {
                        mutableLaunchProperties.mutatingCombine(modifier.projectile_launch);
                    }
                }
            }

            ItemStack ammo;
            if (shooter instanceof PlayerEntity player) {
                ammo = player.getProjectileType(weaponStack);
            } else {
                ammo = new ItemStack(Items.ARROW);
            }
            var loadedAmmo = RangedWeaponAccessor.load_SpellEngine(weaponStack, ammo, shooter);
            if (loadedAmmo.isEmpty()) {
                return;
            }

            // Save as active spell
            if (shooter instanceof SpellCaster.Player caster) {
                var shotContext = caster.getArrowShootContext();
                shotContext.firedBySpell = true;
                shotContext.activeSpells.add(spellEntry);
            }
            var divergence = (sequenceIndex == 0) ? 0F : shoot_arrow.divergence;
            // Perform shoot
            ((RangedWeaponAccessor) rangedWeapon).shootAll_SpellEngine(
                    serverWorld,
                    shooter,
                    Hand.MAIN_HAND,
                    weaponStack,
                    loadedAmmo,
                    shoot_arrow.launch_properties.velocity,
                    divergence,
                    shoot_arrow.arrow_critical_strike,
                    null);
            // Arrow perks applied by `RangedWeaponItemMixin`

            // Fixing inconsistent Vanille code, shoot sound is played by `BOW` outside of `shootAll`
            if (weapon instanceof BowItem) {
                world.playSound(
                        null,
                        shooter.getX(),
                        shooter.getY(),
                        shooter.getZ(),
                        SoundEvents.ENTITY_ARROW_SHOOT,
                        SoundCategory.PLAYERS,
                        1.0F,
                        1.0F / (world.getRandom().nextFloat() * 0.4F + 1.2F) + 1 * 0.5F
                );
            }

            if (shooter instanceof SpellCaster.Player caster) {
                caster.setArrowShootContext(ArrowShootContext.empty());
            }

            var extra_launch = mutableLaunchProperties.extra_launch_count;
            if (sequenceIndex == 0 && extra_launch > 0) {
                for (int i = 0; i < extra_launch; i++) {
                    var ticks = (i + 1) * mutableLaunchProperties.extra_launch_delay;
                    var nextSequenceIndex = i + 1;
                    ((WorldScheduler)world).schedule(ticks, () -> {
                        if (shooter == null || !shooter.isAlive()) {
                            return;
                        }
                        shootArrow(world, shooter, spellEntry, context, nextSequenceIndex);
                    });
                }
            }
        }
    }

    /// Resolves the perks an arrow is actually launched with: the spell's own `arrow_perks` with every
    /// applicable modifier's `arrow_perks` merged on top. Returns null when neither defines any.
    ///
    /// Only the launch-time perks (`pierce`, `damage_multiplier`, `velocity_multiplier`) take effect from
    /// a modifier, because those are stamped onto the arrow entity here. The hit-time fields
    /// (`knockback`, `bypass_iframes`, `iframe_to_set`, `skip_arrow_damage`) are re-read straight off the
    /// spell by `PersistentProjectileEntityMixin` once the arrow lands, so a modifier cannot reach them.
    @Nullable
    public static Spell.ArrowPerks effectiveArrowPerks(LivingEntity shooter, RegistryEntry<Spell> spellEntry) {
        var base = spellEntry.value().arrow_perks;
        if (!(shooter instanceof PlayerEntity player)) {
            return base;
        }
        Spell.ArrowPerks effective = null;
        for (var modifier: SpellModifiers.of(player, spellEntry)) {
            if (modifier.arrow_perks == null) {
                continue;
            }
            if (effective == null) {
                // Copy, so the modifier never mutates the shared spell definition
                effective = (base != null) ? base.copy() : Spell.ArrowPerks.EMPTY();
            }
            effective.mutatingCombine(modifier.arrow_perks);
        }
        return (effective != null) ? effective : base;
    }

    public static void onArrowShot(ArrowExtension arrow, LivingEntity shooter, RegistryEntry<Spell> spellEntry,
                                   Supplier<Collection<ServerPlayerEntity>> trackers) {
        var arrowPerks = effectiveArrowPerks(shooter, spellEntry);
        if (arrowPerks != null) {
            var world = shooter.getWorld();
            arrow.applyArrowPerks(spellEntry, arrowPerks);
            var launchVisuals = arrowPerks.launch_visuals.resolved(Fx.Context.NONE);
            ParticleHelper.sendBatches(shooter, launchVisuals.particles, 1F, trackers.get());
            ModelEffectHelper.spawn(world, shooter.getPos(), shooter.getYaw(), launchVisuals.models, shooter);
            SoundHelper.playSound(world, shooter, arrowPerks.launch_sound);
        }
    }
}