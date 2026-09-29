package net.spell_engine.mixin.entity;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.Tameable;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.spell_engine.api.spell.summon.SpellSummoned;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/// Credits the summoner for the damage dealt by their spell summons.
///
/// Vanilla tracks the player responsible for a kill (`attackingPlayer` + `playerHitTimer`) in `damage`,
/// recognising only players and tamed wolves as attackers. Without the credit a kill landed by a summon
/// drops no `killed_by_player` loot (boss loot injections included), no experience, and grants no kill
/// advancement.
///
/// 1.21.11: vanilla resolves the credit in `setAttackingPlayer(DamageSource)`, called from `damage` past
/// every early return (so only applied damage counts). Hooked at its head: a summon is neither player nor
/// wolf, so the vanilla branches that follow leave the credit untouched and return it.
@Mixin(LivingEntity.class)
public abstract class LivingEntitySummonKillCredit {
    @Inject(method = "setAttackingPlayer", at = @At("HEAD"))
    private void setAttackingPlayer_SpellEngine_SummonKillCredit(DamageSource source, CallbackInfoReturnable<PlayerEntity> cir) {
        // Melee, spells, projectiles and clouds of a summon all name the summon as the attacker
        var attacker = source.getAttacker();
        if (attacker instanceof SpellSummoned && attacker instanceof Tameable summon
                && summon.getOwner() instanceof PlayerEntity summoner) {
            // Same memory as vanilla, covers lingering damage (fire, poison)
            ((LivingEntity) (Object) this).setAttacking(summoner, 100);
        }
    }
}
