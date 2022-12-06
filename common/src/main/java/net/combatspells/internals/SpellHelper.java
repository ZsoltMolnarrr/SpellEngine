package net.combatspells.internals;

import net.combatspells.CombatSpells;
import net.combatspells.api.Enchantments_CombatSpells;
import net.combatspells.api.spell.Spell;
import net.combatspells.entity.SpellProjectile;
import net.combatspells.mixin.LivingEntityAccessor;
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
import net.spelldamage.api.SpellDamage;
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
        var haste = (float) SpellDamage.getHaste(caster, provisionedWeapon);
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

    public static boolean isChannelTickDue(Spell spell, int remainingUseTicks) {
        return (remainingUseTicks % spell.cast.channel_ticks) == 0;
    }

    public static boolean isChanneled(Spell spell) {
        return channelValueMultiplier(spell) != 0;
    }

    public static float channelValueMultiplier(Spell spell) {
        var ticks = spell.cast.channel_ticks;
        if (ticks <= 0) {
            return 0;
        }
        return ((float)ticks) / 20F;
    }

    public static void performSpell(World world, LivingEntity caster, List<Entity> targets, ItemStack itemStack, SpellCastAction action, int remainingUseTicks) {
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
        var channelMultiplier = 1F;
        boolean shouldPerformImpact = true;
        switch (action) {
            case CHANNEL -> {
                channelMultiplier = channelValueMultiplier(spell);
            }
            case RELEASE -> {
                if (isChanneled(spell)) {
                    shouldPerformImpact = false;
                    channelMultiplier = 1;
                } else {
                    channelMultiplier = (progress >= 1) ? 1 : 0;
                }
            }
        }
        if (channelMultiplier > 0 && ammoResult.satisfied()) {
            var targeting = spell.on_release.target;
            boolean released = action == SpellCastAction.RELEASE;
            System.out.println("Beam action: " + action);
            if (shouldPerformImpact) {
                switch (targeting.type) {
                    case AREA -> {
                        areaImpact(world, caster, targets, spell, channelMultiplier);
                    }
                    case BEAM -> {
                        System.out.println("Beam impact channelMultiplier: " + channelMultiplier);
                        areaImpact(world, caster, targets, spell, channelMultiplier);
                    }
                    case CURSOR -> {
                        var target = targets.stream().findFirst();
                        if (target.isPresent()) {
                            directImpact(world, caster, target.get(), spell, channelMultiplier);
                        } else {
                            released = false;
                        }
                    }
                    case PROJECTILE -> {
                        Entity target = null;
                        var entityFound = targets.stream().findFirst();
                        if (entityFound.isPresent()) {
                            target = entityFound.get();
                        }
                        shootProjectile(world, caster, target, spell);
                    }
                }
            }
            if (released) {
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

    private static void directImpact(World world, LivingEntity caster, Entity target, Spell spell, float channelMultiplier) {
        performImpacts(world, caster, target, spell, channelMultiplier);
    }

    private static void areaImpact(World world, LivingEntity caster, List<Entity> targets, Spell spell, float channelMultiplier) {
        for(var target: targets) {
            performImpacts(world, caster, target, spell, channelMultiplier);
        }
    }

    public static float launchHeight(LivingEntity livingEntity) {
        var eyeHeight = livingEntity.getStandingEyeHeight();
        var shoulderDistance = livingEntity.getHeight() * 0.15;
        return (float) ((eyeHeight - shoulderDistance) * livingEntity.getScaleFactor());
    }

    public static Vec3d launchPoint(LivingEntity caster) {
        return launchPoint(caster, 0.5F);
    }

    public static Vec3d launchPoint(LivingEntity caster, float forward) {
        Vec3d look = caster.getRotationVector().multiply(forward * caster.getScaleFactor());
        return caster.getPos().add(0, launchHeight(caster), 0).add(look);
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

    public static boolean performImpacts(World world, LivingEntity caster, Entity target, Spell spell, float channelMultiplier) {
        var performed = false;
        for (var impact: spell.on_impact) {
            var result = performImpact(world, caster, target, spell.school, impact, channelMultiplier);
            performed = performed || result;
        }
        return performed;
    }

    private static boolean performImpact(World world, LivingEntity caster, Entity target, MagicSchool school, Spell.Impact impact, float channelMultiplier) {
        if (!target.isAttackable()) {
            return false;
        }
        var success = false;
        try {
            double particleMultiplier = 1 * channelMultiplier;
            var relation = TargetHelper.getRelation(caster, target);
            switch (impact.action.type) {
                case DAMAGE -> {
                    if(!TargetHelper.actionAllowed(false, relation, caster, target)) {
                        return false;
                    }
                    var damageData = impact.action.damage;
                    var damage = SpellDamage.getSpellDamage(school, caster);
                    particleMultiplier = damage.criticalMultiplier();
                    var amount = damage.randomValue();
                    var source = SpellDamageSource.create(school, caster);
                    amount *= damageData.multiplier;
                    amount *= channelMultiplier;
                    if (CombatSpells.config.bypass_iframes && target instanceof LivingEntityAccessor livingEntityAccessor) {
                        livingEntityAccessor.setLastAttackedTicks(20);
                    }
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
                        var healing = SpellDamage.getSpellDamage(school, caster);
                        particleMultiplier = healing.criticalMultiplier();
                        var amount = healing.randomValue();
                        amount *= healData.multiplier;
                        amount *= channelMultiplier;
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
                        // duration *= progressMultiplier; // ?????
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
                    var result = SpellDamage.getSpellDamage(school, caster, itemStack);
                    var damage = new EstimatedValue(result.nonCriticalValue(), result.forcedCriticalValue())
                            .multiply(damageData.multiplier);
                    damageEffects.add(damage);
                }
                case HEAL -> {
                    var healData = impact.action.heal;
                    var result = SpellDamage.getSpellDamage(school, caster, itemStack);
                    var healing = new EstimatedValue(result.nonCriticalValue(), result.forcedCriticalValue())
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
