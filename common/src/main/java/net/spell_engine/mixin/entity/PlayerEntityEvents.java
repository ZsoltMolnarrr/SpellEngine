package net.spell_engine.mixin.entity;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.spell_engine.api.event.CombatEvents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Player.class)
public class PlayerEntityEvents {
    /// Regular melee swing (`Player#attack`). `setLastHurtMob` is invoked exactly once there, with `Player`
    /// as the static receiver type on both the vanilla and the NeoForge-patched tree.
    @WrapOperation(
            method = "attack",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;setLastHurtMob(Lnet/minecraft/world/entity/Entity;)V"),
            require = 1
    )
    private void attack_WRAP_onAttacking(Player instance, Entity entity, Operation<Void> original) {
        original.call(instance, entity);
        fireMeleeAttack_SpellEngine(instance, entity);
    }

    /// Stab swing (`Player#stabAttack`, new in 26.1) — the melee path of the `PiercingWeapon` / `KineticWeapon`
    /// item components. It is a separate method with its own single `setLastHurtMob` call (same owner and
    /// descriptor as the one in `attack`), so it needs its own wrap or `PLAYER_MELEE_ATTACK` never fires for it.
    /// Kept as a second explicit `@WrapOperation` rather than `method = {"attack", "stabAttack"}` so that each
    /// target carries its own `require = 1` — an aggregated require cannot tell "both matched once" from
    /// "one matched twice".
    @WrapOperation(
            method = "stabAttack",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;setLastHurtMob(Lnet/minecraft/world/entity/Entity;)V"),
            require = 1
    )
    private void stabAttack_WRAP_onAttacking(Player instance, Entity entity, Operation<Void> original) {
        original.call(instance, entity);
        fireMeleeAttack_SpellEngine(instance, entity);
    }

    @Unique
    private static void fireMeleeAttack_SpellEngine(Player player, Entity target) {
        if (CombatEvents.PLAYER_MELEE_ATTACK.isListened()) {
            var args = new CombatEvents.PlayerAttack.Args(player, target);
            CombatEvents.PLAYER_MELEE_ATTACK.invoke(listener -> listener.onPlayerAttack(args));
        }
    }
}
