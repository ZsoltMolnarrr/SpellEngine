package net.spell_engine.mixin.entity;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.Tameable;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.spell_engine.api.spell.summon.SpellSummoned;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
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
/// Hooked on the `getAttacker` lookup feeding vanilla's own player/wolf check: past every early return
/// (so only applied damage counts), and unconditional, unlike the `setAttacker` call next to it, which
/// is skipped for `no_anger` damage types. A summon is neither player nor wolf, so the vanilla branches
/// that follow leave the credit untouched.
@Mixin(LivingEntity.class)
public abstract class LivingEntitySummonKillCredit {
    @Shadow @Nullable protected PlayerEntity attackingPlayer;
    @Shadow protected int playerHitTimer;

    @Inject(method = "damage", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/entity/damage/DamageSource;getAttacker()Lnet/minecraft/entity/Entity;"))
    private void damage_getAttacker_SpellEngine_SummonKillCredit(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        // Melee, spells, projectiles and clouds of a summon all name the summon as the attacker
        var attacker = source.getAttacker();
        if (attacker instanceof SpellSummoned && attacker instanceof Tameable summon
                && summon.getOwner() instanceof PlayerEntity summoner) {
            this.attackingPlayer = summoner;
            this.playerHitTimer = 100; // Same memory as vanilla, covers lingering damage (fire, poison)
        }
    }
}
