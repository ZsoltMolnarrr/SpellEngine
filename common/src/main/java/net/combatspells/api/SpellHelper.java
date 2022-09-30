package net.combatspells.api;

import net.combatspells.api.spell.Spell;
import net.combatspells.entity.SpellProjectile;
import net.combatspells.utils.ParticleHelper;
import net.combatspells.utils.SoundHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
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
            switch (action.type) {
                case SHOOT_PROJECTILE -> {
                    shootProjectile(world, caster, spell.range, action.projectile, spell.on_impact);
                }
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

    public static void performImpact(World world, LivingEntity caster, Entity target, Spell.Impact impact) {
        switch (impact.action.type) {
            case DAMAGE -> {
                if (!target.isAttackable()) {
                    return;
                }
                // if (world.friend)
                var damageData = impact.action.damage;
                var attributeId = new Identifier(damageData.attribute);
                var school = MagicSchool.fromAttributeId(attributeId);
                double amount = 0;
                DamageSource source;
                if (school != null) {
                    amount = SpellDamageHelper.getSpellDamage(school, caster);
                    source = SpellDamageSource.create(school, caster);
                } else {
                    var attribute= Registry.ATTRIBUTE.get(attributeId);
                    amount = caster.getAttributeValue(attribute);
                    if (caster instanceof PlayerEntity player) {
                        source = DamageSource.player(player);
                    } else {
                        source = DamageSource.mob(caster);
                    }
                }
                caster.onAttacking(target);
                target.damage(source, (float) amount);
                ParticleHelper.sendBatches(target, target.getPos().add(0, target.getHeight() / 2F, 0), impact.particles);
                SoundHelper.playSound(world, target, impact.sound);
            }
            case HEAL -> {
            }
            case STATUS_EFFECT -> {
            }
        }
    }
}
