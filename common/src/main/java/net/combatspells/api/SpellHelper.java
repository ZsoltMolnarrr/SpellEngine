package net.combatspells.api;

import net.combatspells.api.spell.Spell;
import net.combatspells.entity.SpellProjectile;
import net.combatspells.utils.ParticleHelper;
import net.combatspells.utils.SoundHelper;
import net.combatspells.utils.TargetHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;
import net.minecraft.world.World;
import net.spelldamage.api.MagicSchool;
import net.spelldamage.api.SpellDamageHelper;
import net.spelldamage.api.SpellDamageSource;;

public class SpellHelper {
    public static int maximumUseTicks = 72000;

    public static float getCastProgress(int remainingUseTicks, float duration) {
        if (duration <= 0) {
            return 1F;
        }
        var elapsedTicks = maximumUseTicks - remainingUseTicks;
        return Math.min(((float)elapsedTicks) / (duration * 20F), 1F);
    }

    public static void castRelease(World world, LivingEntity caster, Spell spell, int remainingUseTicks) {
        var progress = getCastProgress(remainingUseTicks, spell.cast_duration);
        if (progress >= 1) {
            var action = spell.on_release.action;
            boolean success = false;
            switch (action.type) {
                case SHOOT_PROJECTILE -> {
                    shootProjectile(world, caster, spell.range, action.projectile, spell.on_impact);
                    success = true;
                }
            }

            if (success) {
                ParticleHelper.sendBatches(caster, spell.on_release.particles);
                SoundHelper.playSound(world, caster, spell.on_release.sound);
            }
        }
    }

    private static void shootProjectile(World world, LivingEntity caster, float range,
                                        Spell.ProjectileData projectileData, Spell.Impact impact) {
        // Send target packet
        if (world.isClient) {
            return;
        }

        var x = caster.getX();
        var y  = caster.getEyeY();
        var z  = caster.getZ();
        var projectile = new SpellProjectile(world, caster, x, y, z, projectileData, impact);
        projectile.range = range;

        world.spawnEntity(projectile);
        System.out.println("Spawning projectile");
    }

    public static boolean performImpact(World world, LivingEntity caster, Entity target, Spell.Impact impact) {
        if (!target.isAttackable()) {
            return false;
        }
        var success = false;
        try {
            var relation = TargetHelper.getRelation(caster, target);
            switch (impact.action.type) {
                case DAMAGE -> {
                    if(!TargetHelper.actionAllowed(false, relation)) {
                        return false;
                    }
                    var damageData = impact.action.damage;
                    var attributeId = new Identifier(damageData.attribute);
                    var school = MagicSchool.fromAttributeId(attributeId);
                    double amount = 0;
                    DamageSource source;
                    if (school != null) {
                        amount = SpellDamageHelper.getSpellDamage(school, caster);
                        source = SpellDamageSource.create(school, caster);
                    } else {
                        var attribute = Registry.ATTRIBUTE.get(attributeId);
                        amount = caster.getAttributeValue(attribute);
                        if (caster instanceof PlayerEntity player) {
                            source = DamageSource.player(player);
                        } else {
                            source = DamageSource.mob(caster);
                        }
                    }
                    amount *= damageData.multiplier;
                    caster.onAttacking(target);
                    target.damage(source, (float) amount);
                    success = true;
                }
                case HEAL -> {
                    if(!TargetHelper.actionAllowed(true, relation)) {
                        return false;
                    }
                    if (target instanceof LivingEntity livingTarget) {
                        var healData = impact.action.heal;
                        var attributeId = new Identifier(healData.attribute);
                        var school = MagicSchool.fromAttributeId(attributeId);
                        double amount = 0;
                        if (school != null) {
                            amount = SpellDamageHelper.getSpellDamage(school, caster);
                        } else {
                            var attribute = Registry.ATTRIBUTE.get(attributeId);
                            amount = caster.getAttributeValue(attribute);
                        }
                        amount *= healData.multiplier;
                        livingTarget.heal((float) amount);
                        success = true;
                    }
                }
                case STATUS_EFFECT -> {
                    if (target instanceof LivingEntity livingTarget) {
                        var data = impact.action.statusEffect;
                        var id = new Identifier(data.effect_id);
                        var effect = Registry.STATUS_EFFECT.get(id);
                        if(!TargetHelper.actionAllowed(effect.isBeneficial(), relation)) {
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
