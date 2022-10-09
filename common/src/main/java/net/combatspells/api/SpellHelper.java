package net.combatspells.api;

import net.combatspells.api.spell.Spell;
import net.combatspells.entity.SpellProjectile;
import net.combatspells.internals.SpellRegistry;
import net.combatspells.utils.AnimationHelper;
import net.combatspells.utils.ParticleHelper;
import net.combatspells.utils.SoundHelper;
import net.combatspells.utils.TargetHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;
import net.minecraft.world.World;
import net.spelldamage.api.MagicSchool;
import net.spelldamage.api.SpellDamageHelper;
import net.spelldamage.api.SpellDamageSource;

import java.util.List;

import static net.combatspells.internals.SpellAnimationType.RELEASE;

public class SpellHelper {
    public static int maximumUseTicks = 72000;

    public static float getCastingSpeed(LivingEntity caster) {
        return (float) SpellDamageHelper.getHaste(caster);
    }

    public static float getCastProgress(LivingEntity caster, int remainingUseTicks, float duration) {
        if (duration <= 0) {
            return 1F;
        }
        var elapsedTicks = maximumUseTicks - remainingUseTicks;
        var haste = getCastingSpeed(caster);
        return Math.min(((float)elapsedTicks) / ((duration / haste) * 20F), 1F);
    }

    public static void castRelease(World world, LivingEntity caster, List<Entity> targets, ItemStack itemStack, int remainingUseTicks) {
        var item = itemStack.getItem();
        var spell = SpellRegistry.spells.get(Registry.ITEM.getId(item));
        var progress = getCastProgress(caster, remainingUseTicks, spell.cast.duration);
        if (progress >= 1) {
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
                    if (spell.cooldown_duration > 0) {
                        player.getItemCooldownManager().set(item, Math.round(spell.cooldown_duration * 20F));
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

    private static void shootProjectile(World world, LivingEntity caster, Entity target, Spell spell) {
        // Send target packet
        if (world.isClient) {
            return;
        }

        var x = caster.getX();
        var y  = caster.getEyeY();
        var z  = caster.getZ();
        var projectile = new SpellProjectile(world, caster, x, y, z, spell, target);
        projectile.range = spell.range;

        world.spawnEntity(projectile);
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
            var relation = TargetHelper.getRelation(caster, target);
            switch (impact.action.type) {
                case DAMAGE -> {
                    if(!TargetHelper.actionAllowed(false, relation, caster, target)) {
                        return false;
                    }
                    var damageData = impact.action.damage;
                    var amount = SpellDamageHelper.getSpellDamage(school, caster);
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
                        var amount = SpellDamageHelper.getSpellDamage(school, caster);
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
                ParticleHelper.sendBatches(target, impact.particles);
                SoundHelper.playSound(world, target, impact.sound);
            }
        } catch (Exception e) {
            System.err.println("Failed to perform impact effect");
            System.err.println(e.getMessage());
        }
        return success;
    }
}
