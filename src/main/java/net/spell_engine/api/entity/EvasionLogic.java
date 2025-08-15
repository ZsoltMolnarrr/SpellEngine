package net.spell_engine.api.entity;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;
import net.spell_engine.SpellEngineMod;
import net.spell_engine.api.tags.SpellEngineDamageTypeTags;
import net.spell_engine.internals.casting.SpellCast;
import net.spell_engine.utils.AnimationHelper;
import net.spell_engine.utils.VectorHelper;

import java.util.Random;

public class EvasionLogic {
    public interface Evader {
        DamageSource getLastEvaded();
        void setLastEvaded(DamageSource source);
    }
    private static final Random RNG = new Random();
    public static boolean tryEvade(LivingEntity entity, DamageSource source) {
        if (entity.isSleeping()) {
            return false;
        }
        if (source.isIn(SpellEngineDamageTypeTags.EVADABLE)) {
            var chance = (float)SpellEngineAttributes.EVASION_CHANCE.asChance(entity.getAttributeValue(SpellEngineAttributes.EVASION_CHANCE.entry));

            var angleOfAttack = 0F;
            var evasionAngleLimit = SpellEngineMod.config.attribute_evasion_angle;
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

    public static void onEvade(LivingEntity entity, DamageSource source) {
        // System.out.println("SpellEngine: " + entity.getName().getString() + " evaded damage from " + source.getName() + "!");
        if (entity instanceof ServerPlayerEntity player) {
            var tracker = PlayerLookup.tracking(player);
            AnimationHelper.sendAnimation(player, tracker, SpellCast.Animation.MISC, "spell_engine:dodge", 1F);
        }
        entity.getWorld().playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.ENTITY_PLAYER_ATTACK_NODAMAGE, entity.getSoundCategory(), 1.0F, 1.0F);
    }
}
