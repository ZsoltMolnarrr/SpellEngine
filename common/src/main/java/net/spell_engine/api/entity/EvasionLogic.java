package net.spell_engine.api.entity;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;
import net.spell_engine.SpellEngineMod;
import net.spell_engine.api.event.CombatEvents;
import net.spell_engine.api.spell.fx.PlayerAnimation;
import net.spell_engine.api.spell.fx.Sound;
import net.spell_engine.api.tags.SpellEngineDamageTypeTags;
import net.spell_engine.fx.SpellEngineSounds;
import net.spell_engine.internals.casting.SpellCast;
import net.spell_engine.internals.casting.SpellCasterEntity;
import net.spell_engine.utils.AnimationHelper;
import net.spell_engine.utils.VectorHelper;

import java.util.Random;

public class EvasionLogic {
    public interface Evader {
        DamageSource getLastEvaded();
        void setLastEvaded(DamageSource source);
    }
    private static final Random RNG = new Random();
    public static boolean tryEvade(LivingEntity entity, float damage, DamageSource source) {
        if (entity.isSleeping()) {
            return false;
        }
        var config = SpellEngineMod.config;
        if (!config.attribute_evasion_allowed_while_spell_casting
                && entity instanceof SpellCasterEntity casterEntity
                && casterEntity.isCastingSpell()) {
            return false;
        }
        if (!config.attribute_evasion_allowed_while_item_usage && entity.isUsingItem()) {
            return false;
        }
        if (source.isIn(SpellEngineDamageTypeTags.EVADABLE)) {
            var chance = (float)SpellEngineAttributes.EVASION_CHANCE.asChance(entity.getAttributeValue(SpellEngineAttributes.EVASION_CHANCE.entry));

            var angleOfAttack = 0F;
            var evasionAngleLimit = config.attribute_evasion_angle;
            if (evasionAngleLimit > 0F && source.getSource() != null) {
                var sourcePos = source.getPosition() != null ? source.getPosition() : source.getSource().getPos();
                angleOfAttack = (float) VectorHelper.angleBetween(
                        entity.getRotationVec(1.0F),
                        new Vec3d(sourcePos.getX() - entity.getX(), 0, sourcePos.getZ() - entity.getZ())
                );
                angleOfAttack = Math.abs(angleOfAttack);
            }
            return chance > 0 && RNG.nextFloat() < chance && angleOfAttack <= evasionAngleLimit;
        }
        return false;
    }

    private static final Sound evadeSound = new Sound("spell_engine:dodge", 1.0F, 1.0F, 0.1F);
    private static final PlayerAnimation evadeAnimation = new PlayerAnimation("spell_engine:dodge");
    public static void onEvade(LivingEntity entity, float damage, DamageSource source) {
        // System.out.println("SpellEngine: " + entity.getName().getString() + " evaded damage from " + source.getName() + "!");
        if (entity instanceof ServerPlayerEntity player) {
            var tracker = PlayerLookup.tracking(player);
            AnimationHelper.sendAnimation(player, tracker, SpellCast.Animation.MISC, evadeAnimation, 1F);
        }
        entity.getWorld().playSound(null, entity.getX(), entity.getY(), entity.getZ(), SpellEngineSounds.DODGE.soundEvent(), entity.getSoundCategory(), 1.0F, evadeSound.randomizedPitch());
        CombatEvents.ENTITY_EVASION.invoke(listener -> listener.onEntityEvasion(new CombatEvents.EntityEvasion.Args(entity, damage, source)));
    }
}
