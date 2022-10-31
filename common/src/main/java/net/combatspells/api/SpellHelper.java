package net.combatspells.api;

import net.combatspells.CombatSpells;
import net.combatspells.api.spell.Spell;
import net.combatspells.entity.SpellProjectile;
import net.combatspells.internals.SpellRegistry;
import net.combatspells.utils.AnimationHelper;
import net.combatspells.utils.ParticleHelper;
import net.combatspells.utils.SoundHelper;
import net.combatspells.utils.TargetHelper;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.registry.Registry;
import net.minecraft.world.World;
import net.spelldamage.api.MagicSchool;
import net.spelldamage.api.SpellDamageHelper;
import net.spelldamage.api.SpellDamageSource;

import java.util.ArrayList;
import java.util.List;

import static net.combatspells.internals.SpellAnimationType.RELEASE;

public class SpellHelper {
    public static int maximumUseTicks = 72000;

    public record AmmoResult(boolean satisfied, ItemStack ammo) { }
    public static AmmoResult ammoForSpell(PlayerEntity player, Spell spell, ItemStack itemStack) {
        boolean satisfied = true;
        ItemStack ammo = null;
        boolean ignoreAmmo = player.getAbilities().creativeMode || EnchantmentHelper.getLevel(Enchantments_CombatSpells.INFINITY, itemStack) > 0;
        if (!ignoreAmmo && spell.cost.item_id != null && !spell.cost.item_id.isEmpty()) {
            var id = new Identifier(spell.cost.item_id);
            var ammoItem = Registry.ITEM.get(id);
            if(ammoItem != null) {
                ammo = ammoItem.getDefaultStack();
                satisfied = player.getInventory().contains(ammo);
            }
        }
        return new AmmoResult(satisfied, ammo);
    }

    public static float hasteAffectedValue(LivingEntity caster, float value) {
        return hasteAffectedValue(caster, value, null);
    }

    public static float hasteAffectedValue(LivingEntity caster, float value, ItemStack provisionedWeapon) {
        var haste = (float) SpellDamageHelper.getHaste(caster, provisionedWeapon);
        return value / haste;
    }

    public static float getCastDuration(LivingEntity caster, Spell spell) {
        return getCastDuration(caster, spell, null);
    }

    public static float getCastDuration(LivingEntity caster, Spell spell, ItemStack provisionedWeapon) {
        if (spell.cast == null) {
            return 0;
        }
        return hasteAffectedValue(caster, spell.cast.duration, provisionedWeapon);
    }

    public static float getCastProgress(LivingEntity caster, int remainingUseTicks, Spell spell) {
        if (spell.cast == null || spell.cast.duration <= 0) {
            return 1F;
        }
        var elapsedTicks = maximumUseTicks - remainingUseTicks;
        var hasteAffectedDuration = getCastDuration(caster, spell);
        return Math.min(((float)elapsedTicks) / (hasteAffectedDuration * 20F), 1F);
    }

    public static float getCooldownDuration(LivingEntity caster, Spell spell) {
        return getCooldownDuration(caster, spell, null);
    }

    public static float getCooldownDuration(LivingEntity caster, Spell spell, ItemStack provisionedWeapon) {
        var duration = spell.cooldown_duration;
        if (duration > 0) {
            if (CombatSpells.config.haste_affects_cooldown) {
                duration = hasteAffectedValue(caster, spell.cooldown_duration, provisionedWeapon);
            }
        }
        return duration;
    }

    public static void castRelease(World world, LivingEntity caster, List<Entity> targets, ItemStack itemStack, int remainingUseTicks) {
        var item = itemStack.getItem();
        var spell = SpellRegistry.resolveSpellByItem(Registry.ITEM.getId(item));
        if (spell == null) {
            return;
        }
        var progress = getCastProgress(caster, remainingUseTicks, spell);
        var ammoResult = new AmmoResult(true, null);
        if (caster instanceof PlayerEntity player) {
            ammoResult = ammoForSpell(player, spell, itemStack);
        }
        if (progress >= 1 && ammoResult.satisfied()) {
            var action = spell.on_release.target;
            boolean success = false;
            switch (action.type) {
                case PROJECTILE -> {
                    Entity target = null;
                    var entityFound = targets.stream().findFirst();
                    if (entityFound.isPresent()) {
                        target = entityFound.get();
                    }
                    shootProjectile(world, caster, target, spell);
                    success = true;
                }
                case CURSOR -> {
                    var target = targets.stream().findFirst();
                    if (target.isPresent()) {
                        directImpact(world, caster, target.get(), spell);
                        success = true;
                    }
                }
                case AREA -> {
                    areaImpact(world, caster, targets, spell);
                    success = true;
                }
            }
            if (success) {
                ParticleHelper.sendBatches(caster, spell.on_release.particles);
                SoundHelper.playSound(world, caster, spell.on_release.sound);
                if (caster instanceof PlayerEntity player) {
                    AnimationHelper.sendAnimation(player, RELEASE, spell.on_release.animation);
                    var duration = getCooldownDuration(caster, spell);
                    if (duration > 0) {
                        player.getItemCooldownManager().set(item, Math.round(duration * 20F));
                    }
                    player.addExhaustion(spell.cost.exhaust * CombatSpells.config.spell_cost_exhaust_multiplier);
                    if (CombatSpells.config.spell_cost_durability_allowed && spell.cost.durability > 0) {
                        itemStack.damage(spell.cost.durability, caster, (asd) -> {
                            asd.sendEquipmentBreakStatus(EquipmentSlot.MAINHAND);
                            asd.sendEquipmentBreakStatus(EquipmentSlot.OFFHAND);
                        });
                    }
                    if (CombatSpells.config.spell_cost_item_allowed && ammoResult.ammo != null) {
                        for(int i = 0; i < player.getInventory().size(); ++i) {
                            var stack = player.getInventory().getStack(i);
                            if (stack.isOf(ammoResult.ammo.getItem())) {
                                stack.decrement(1);
                                if (stack.isEmpty()) {
                                    player.getInventory().removeOne(stack);
                                }
                                break;
                            }
                        }
                    }
                }
            }
        }
    }

    private static void directImpact(World world, LivingEntity caster, Entity target, Spell spell) {
        performImpacts(world, caster, target, spell);
    }

    private static void areaImpact(World world, LivingEntity caster, List<Entity> targets, Spell spell) {
        for(var target: targets) {
            performImpacts(world, caster, target, spell);
        }
    }

    public static Vec3d launchPoint(LivingEntity caster) {
        double shoulderHeight = caster.getHeight() * 0.15 * caster.getScaleFactor();
        Vec3d look = caster.getRotationVector().multiply(0.5 * caster.getScaleFactor());
        return caster.getEyePos().subtract(0, shoulderHeight, 0).add(look);
    }

    private static void shootProjectile(World world, LivingEntity caster, Entity target, Spell spell) {
        // Send target packet
        if (world.isClient) {
            return;
        }

        var launchPoint = launchPoint(caster);
        var x = launchPoint.getX();
        var y  = launchPoint.getY();
        var z  = launchPoint.getZ();
        var projectile = new SpellProjectile(world, caster, x, y, z, spell, target);
        projectile.range = spell.range;

        world.spawnEntity(projectile);
        System.out.println("Shooting projectile");
    }

    public static boolean performImpacts(World world, LivingEntity caster, Entity target, Spell spell) {
        var performed = false;
        for (var impact: spell.on_impact) {
            var result = performImpact(world, caster, target, spell.school, impact);
            performed = performed || result;
        }
        return performed;
    }

    private static boolean performImpact(World world, LivingEntity caster, Entity target, MagicSchool school, Spell.Impact impact) {
        if (!target.isAttackable()) {
            return false;
        }
        var success = false;
        try {
            double particleMultiplier = 1;
            var relation = TargetHelper.getRelation(caster, target);
            switch (impact.action.type) {
                case DAMAGE -> {
                    if(!TargetHelper.actionAllowed(false, relation, caster, target)) {
                        return false;
                    }
                    var damageData = impact.action.damage;
                    var damage = SpellDamageHelper.getSpellDamage(school, caster);
                    particleMultiplier = damage.criticalMultiplier();
                    var amount = damage.value();
                    var source = SpellDamageSource.create(school, caster);
                    amount *= damageData.multiplier;
                    caster.onAttacking(target);
                    target.damage(source, (float) amount);
                    success = true;
                }
                case HEAL -> {
                    if(!TargetHelper.actionAllowed(true, relation, caster, target)) {
                        return false;
                    }
                    if (target instanceof LivingEntity livingTarget) {
                        var healData = impact.action.heal;
                        var healing = SpellDamageHelper.getSpellDamage(school, caster);
                        particleMultiplier = healing.criticalMultiplier();
                        var amount = healing.value();
                        amount *= healData.multiplier;
                        livingTarget.heal((float) amount);
                        success = true;
                    }
                }
                case STATUS_EFFECT -> {
                    if (target instanceof LivingEntity livingTarget) {
                        var data = impact.action.status_effect;
                        var id = new Identifier(data.effect_id);
                        var effect = Registry.STATUS_EFFECT.get(id);
                        if(!TargetHelper.actionAllowed(effect.isBeneficial(), relation, caster, target)) {
                            return false;
                        }
                        var duration = Math.round(data.duration * 20F);
                        var amplifier = data.amplifier;
                        livingTarget.addStatusEffect(
                                new StatusEffectInstance(effect, duration, amplifier),
                                caster);
                        success = true;
                    }
                }
            }
            if (success) {
                ParticleHelper.sendBatches(target, impact.particles, (float) particleMultiplier);
                SoundHelper.playSound(world, target, impact.sound);
            }
        } catch (Exception e) {
            System.err.println("Failed to perform impact effect");
            System.err.println(e.getMessage());
        }
        return success;
    }

    // DAMAGE/HEAL OUTPUT ESTIMATION

    public static EstimatedOutput estimate(Spell spell, PlayerEntity caster, ItemStack itemStack) {
        var school = spell.school;
        var damageEffects = new ArrayList<EstimatedValue>();
        var healEffects = new ArrayList<EstimatedValue>();

        var replaceAttributes = caster.getMainHandStack() != itemStack;
        var heldAttributes = caster.getMainHandStack().getAttributeModifiers(EquipmentSlot.MAINHAND);
        var itemAttributes = itemStack.getAttributeModifiers(EquipmentSlot.MAINHAND);
        if (replaceAttributes) {
            caster.getAttributes().removeModifiers(heldAttributes);
            caster.getAttributes().addTemporaryModifiers(itemAttributes);
        }

        for (var impact: spell.on_impact) {
            switch (impact.action.type) {
                case DAMAGE -> {
                    var damageData = impact.action.damage;
                    var damage = new EstimatedValue(
                            SpellDamageHelper.getSpellDamage(school, caster, SpellDamageHelper.CriticalStrikeMode.DISABLED, itemStack).value(),
                            SpellDamageHelper.getSpellDamage(school, caster, SpellDamageHelper.CriticalStrikeMode.FORCED, itemStack).value())
                            .multiply(damageData.multiplier);
                    damageEffects.add(damage);
                }
                case HEAL -> {
                    var healData = impact.action.heal;
                    var healing = new EstimatedValue(
                            SpellDamageHelper.getSpellDamage(school, caster, SpellDamageHelper.CriticalStrikeMode.DISABLED, itemStack).value(),
                            SpellDamageHelper.getSpellDamage(school, caster, SpellDamageHelper.CriticalStrikeMode.FORCED, itemStack).value())
                            .multiply(healData.multiplier);
                    healEffects.add(healing);
                }
                case STATUS_EFFECT -> {
                }
            }
        }

        if (replaceAttributes) {
            caster.getAttributes().removeModifiers(itemAttributes );
            caster.getAttributes().addTemporaryModifiers(heldAttributes);
        }

        return new EstimatedOutput(damageEffects, healEffects);
    }

    public record EstimatedValue(double min, double max) {
        public EstimatedValue multiply(double value) {
            return new EstimatedValue(min * value, max * value);
        }
    }
    public record EstimatedOutput(List<EstimatedValue> damage, List<EstimatedValue> heal) { }
}
