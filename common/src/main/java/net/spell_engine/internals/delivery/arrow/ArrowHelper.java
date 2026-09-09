package net.spell_engine.internals.delivery.arrow;

import com.google.common.base.Suppliers;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.item.*;
import net.fabric_extras.ranged_weapon.api.BowMechanics;
import net.fabric_extras.ranged_weapon.api.CustomRangedWeapon;
import net.fabric_extras.ranged_weapon.api.EntityAttributes_RangedWeapon;
import net.fabric_extras.ranged_weapon.internal.ScalingUtil;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import net.spell_engine.Platform;
import net.spell_engine.api.spell.Spell;
import net.spell_engine.api.spell.fx.Fx;
import net.spell_engine.fx.ModelEffectHelper;
import net.spell_engine.fx.ParticleHelper;
import net.spell_engine.internals.SpellModifiers;
import net.spell_engine.internals.SpellTriggers;
import net.spell_engine.internals.cost.Ammo;
import net.spell_engine.utils.SoundHelper;
import net.spell_engine.utils.WorldScheduler;
import net.spell_engine.internals.casting.SpellCaster;
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

        // 1.20.1 has no `RangedWeaponItem.shootAll(...)` pipeline (bow shooting is inline in `BowItem.onStoppedUsing`,
        // crossbow shooting is private static in `CrossbowItem`), so the crossbow-style launch is reproduced here.
        if (shoot_arrow != null && (world instanceof ServerWorld serverWorld)) {
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
            var loadedAmmo = load(weaponStack, ammo, shooter);
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
            var projectile = createArrow(serverWorld, shooter, weaponStack, loadedAmmo, shoot_arrow.arrow_critical_strike);
            if (projectile == null) {
                if (shooter instanceof SpellCaster.Player caster) {
                    caster.setArrowShootContext(ArrowShootContext.empty());
                }
                return;
            }
            var look = shooter.getRotationVec(1.0F);
            // RangedWeaponAPI parity: on 1.21.1 the spell path runs through `RangedWeaponItem.shootAll`,
            // where RWA's mixin scales velocity by the shooter's `ranged_weapon:velocity` attribute and
            // damage by `ranged_weapon:damage` against the weapon's baseline. The hand-rolled launch here
            // bypasses every RWA hook (they wrap `BowItem.onStoppedUsing` / `CrossbowItem.shoot` /
            // `ProjectileUtil.createArrowProjectile`), so the same scaling is applied explicitly.
            var velocityMultiplier = rangedWeaponApiLoaded()
                    ? RangedWeaponApiBridge.velocityMultiplier(shooter, weaponStack)
                    : 1.0;
            var velocity = (float)(shoot_arrow.launch_properties.velocity * velocityMultiplier);
            projectile.setVelocity(look.x, look.y, look.z, velocity, divergence);
            serverWorld.spawnEntity(projectile);
            if (rangedWeaponApiLoaded()) {
                RangedWeaponApiBridge.applyDamageScaling(projectile, shooter, weaponStack, velocityMultiplier);
            }
            // Vanilla plays the launch sound inside `CrossbowItem.shoot` (both 1.20.1 and 1.21.1), which
            // this hand-rolled launch replaces — so it is played here, with vanilla's pitch sequencing.
            serverWorld.playSound(null, shooter.getX(), shooter.getY(), shooter.getZ(),
                    SoundEvents.ITEM_CROSSBOW_SHOOT, SoundCategory.PLAYERS, 1.0F,
                    shootSoundPitch(shooter.getRandom(), sequenceIndex));
            // Arrow perks applied via the shoot context (same path as the bow/crossbow mixins)
            onArrowSpawned(projectile, shooter, null);

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

    /// Takes one projectile for a spell-fired arrow, mirroring vanilla's creative / Infinity rules:
    /// creative players and Infinity bows (plain arrows only) shoot a free arrow; otherwise one item is
    /// taken — from a quiver-like container first, then from the stack itself.
    private static ItemStack load(ItemStack weaponStack, ItemStack ammo, LivingEntity shooter) {
        var player = shooter instanceof PlayerEntity p ? p : null;
        var creative = player != null && player.getAbilities().creativeMode;
        if (ammo.isEmpty()) {
            return creative ? new ItemStack(Items.ARROW) : ItemStack.EMPTY;
        }
        var infinity = EnchantmentHelper.getLevel(Enchantments.INFINITY, weaponStack) > 0 && ammo.isOf(Items.ARROW);
        if (creative || infinity || player == null) {
            return ammo.copyWithCount(1);
        }
        var loaded = takeOne(player, ammo);
        if (loaded == null) {
            loaded = ammo.split(1);
            if (ammo.isEmpty()) {
                player.getInventory().removeOne(ammo);
            }
        }
        return loaded;
    }

    /// Takes one item matching `ammo` out of a quiver-like container, or `null` if no container holds one.
    @Nullable
    public static ItemStack takeOne(PlayerEntity player, ItemStack ammo) {
        var item = ammo.getItem();
        var predicate = new Ammo.Searched(null, item).asPredicate();
        var source = Ammo.findContainer(player, predicate, 1);
        if (source != null && Ammo.takeFromContainer(source.itemStack(), predicate, 1) == 1) {
            return ammo.copyWithCount(1);
        }
        return null;
    }

    /// Copy of `CrossbowItem.getSoundPitch` (1.21.1): the first projectile of a volley fires at pitch 1,
    /// follow-ups alternate around two slightly detuned pitches, like vanilla multishot.
    private static float shootSoundPitch(Random random, int index) {
        if (index == 0) {
            return 1.0F;
        }
        var base = ((index & 1) == 1) ? 0.63F : 0.43F;
        return 1.0F / (random.nextFloat() * 0.5F + 1.8F) + base;
    }

    // MARK: RangedWeaponAPI bridge
    //
    // RangedWeaponAPI (2.3.4.x+1.20.1) is compile-only and optional at runtime (same arrangement as
    // `ExternalSpellSchools`). Its classes are only touched behind `isModLoaded("ranged_weapon_api")`,
    // inside the nested holder below, so a runtime without RWA never resolves them.

    private static final String RANGED_WEAPON_API_MOD_ID = "ranged_weapon_api";

    private static boolean rangedWeaponApiLoaded() {
        return Platform.util().isModLoaded(RANGED_WEAPON_API_MOD_ID);
    }

    /// The only place RWA types are dereferenced. Never load this class unless {@link #rangedWeaponApiLoaded()}.
    ///
    /// Mirrors what a normal shot receives from RWA's own hooks (`BowItemMixin` on 1.20.1, the
    /// `shootAll` wrap on 1.21.1): velocity scaled by the `ranged_weapon:velocity` bonus over the weapon
    /// type's baseline, damage scaled by `ranged_weapon:damage` over the weapon's baseline (velocity
    /// boost counteracted, since vanilla hit damage multiplies by arrow speed), and percentage-based
    /// Power — which on 1.21.1 rides the `ranged_weapon:damage` attribute, but on 1.20.1 is applied at
    /// the (bypassed) vanilla Power site. A weapon that is not a `CustomRangedWeapon` gets no scaling,
    /// exactly like its normal shots.
    private static final class RangedWeaponApiBridge {
        static double velocityMultiplier(LivingEntity shooter, ItemStack weaponStack) {
            if (!(weaponStack.getItem() instanceof CustomRangedWeapon)) {
                return 1.0;
            }
            var bonusVelocity = shooter.getAttributeValue(EntityAttributes_RangedWeapon.VELOCITY.attribute);
            return ScalingUtil.arrowVelocityMultiplier(weaponStack.getItem(), bonusVelocity);
        }

        static void applyDamageScaling(PersistentProjectileEntity projectile, LivingEntity shooter,
                                       ItemStack weaponStack, double velocityMultiplier) {
            if (!(weaponStack.getItem() instanceof CustomRangedWeapon rangedWeapon)) {
                return;
            }
            var arrow = (net.fabric_extras.ranged_weapon.internal.ArrowExtension) projectile;
            if (arrow.rwa_isModified()) {
                return;
            }
            var baselineDamage = rangedWeapon.getTypeBaseline().damage();
            if (baselineDamage <= 0) {
                return;
            }
            var rangedDamage = shooter.getAttributeValue(EntityAttributes_RangedWeapon.DAMAGE.attribute);
            if (rangedDamage <= 0) {
                return;
            }
            var multiplier = ScalingUtil.arrowDamageMultiplier(baselineDamage, rangedDamage, velocityMultiplier)
                    * BowMechanics.Power.damageMultiplier(weaponStack);
            projectile.setDamage(projectile.getDamage() * multiplier);
            arrow.rwa_markModified(true);
        }
    }

    /// Copy of `CrossbowItem.createArrow` (private static on 1.20.1) with the spell's own crit flag.
    @Nullable
    private static PersistentProjectileEntity createArrow(World world, LivingEntity shooter, ItemStack weaponStack, ItemStack ammo, boolean critical) {
        if (ammo.isOf(Items.FIREWORK_ROCKET)) {
            return null; // Spell-fired arrows are arrows only
        }
        var arrowItem = (ArrowItem)(ammo.getItem() instanceof ArrowItem ? ammo.getItem() : Items.ARROW);
        var projectile = arrowItem.createArrow(world, ammo, shooter);
        projectile.setCritical(critical);
        projectile.setSound(SoundEvents.ITEM_CROSSBOW_HIT);
        projectile.setShotFromCrossbow(true);
        var pierce = EnchantmentHelper.getLevel(Enchantments.PIERCING, weaponStack);
        if (pierce > 0) {
            projectile.setPierceLevel((byte) pierce);
        }
        var player = shooter instanceof PlayerEntity p ? p : null;
        var creative = player != null && player.getAbilities().creativeMode;
        var infinity = EnchantmentHelper.getLevel(Enchantments.INFINITY, weaponStack) > 0 && ammo.isOf(Items.ARROW);
        if (creative || infinity) {
            projectile.pickupType = PersistentProjectileEntity.PickupPermission.CREATIVE_ONLY;
        }
        return projectile;
    }

    /// Hook shared by the bow/crossbow shoot mixins and {@link #shootArrow}: fires the ARROW_SHOT triggers,
    /// then stamps the shooter's pending {@link ArrowShootContext} (spell-fired arrows, passive arrow perks)
    /// onto the freshly spawned projectile. Does not clear the context — multi-shot callers clear it once
    /// after all projectiles left.
    public static void onArrowSpawned(ProjectileEntity projectile, LivingEntity shooter, @Nullable ItemStack weaponStack) {
        if (!(shooter instanceof PlayerEntity player) || !(projectile instanceof ArrowExtension arrow)) {
            return;
        }
        arrow.setWeaponStack_SpellEngine(weaponStack);
        var caster = (SpellCaster.Player) player;
        var shotContext = caster.getArrowShootContext();

        // First run triggers to enable modifying the arrow by passive spells
        // (by appending the arrow shot context)
        final var firedBySpell = shotContext.firedBySpell;
        SpellTriggers.onArrowShot(arrow, player, firedBySpell);

        // Apply arrow modification
        var trackers = Suppliers.memoize(() -> Platform.tracking(shooter));
        for (var spellEntry: shotContext.activeSpells) {
            onArrowShot(arrow, shooter, spellEntry, trackers);
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