package net.combatspells.mixin.client;

import net.combatspells.internals.SpellHelper;
import net.combatspells.api.spell.Spell;
import net.combatspells.client.CombatSpellsClient;
import net.combatspells.internals.SpellCasterClient;
import net.combatspells.network.Packets;
import net.combatspells.utils.TargetHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(ClientPlayerEntity.class)
public abstract class ClientPlayerEntityMixin implements SpellCasterClient {
    @Shadow @Final public ClientPlayNetworkHandler networkHandler;
    private List<Entity> targets = List.of();

    private ClientPlayerEntity player() {
        return (ClientPlayerEntity) ((Object) this);
    }

    private Entity firstTarget() {
        return targets.stream().findFirst().orElse(null);
    }

    public List<Entity> getCurrentTargets() {
        if (targets == null) {
            return List.of();
        }
        return targets;
    }

    public Entity getCurrentFirstTarget() {
        return firstTarget();
    }

    @Override
    public void castStart(Spell spell) {
        System.out.println("Spell casting - Start");
    }

    @Override
    public void castTick(ItemStack itemStack, int remainingUseTicks) {
        // System.out.println("Cast tick progress: " + progress + ", remainingUseTicks: " + remainingUseTicks);
        if (CombatSpellsClient.config.autoRelease) {
            var currentSpell = getCurrentSpell();
            if (currentSpell == null) {
                return;
            }
            var progress = SpellHelper.getCastProgress(player(), remainingUseTicks, currentSpell);
            if (progress >= 1) {
                castRelease(itemStack, remainingUseTicks);
                player().clearActiveItem();
                return;
            }
        }
        updateTarget();
    }

    @Override
    public void castRelease(ItemStack itemStack, int remainingUseTicks) {
        var currentSpell = getCurrentSpell();
        if (currentSpell == null) {
            return;
        }
        var progress = SpellHelper.getCastProgress(player(), remainingUseTicks, currentSpell);
        var caster = player();
        if (progress >= 1) {
            var slot = findSlot(caster, itemStack);
            var action = currentSpell.on_release.target;
            switch (action.type) {
                case PROJECTILE, CURSOR -> {
                    updateTarget();
                    var targets = new int[]{};
                    var firstTarget = firstTarget();
                    if (firstTarget != null) {
                        targets = new int[]{ firstTarget.getId() };
                    }
                    ClientPlayNetworking.send(
                            Packets.ReleaseRequest.ID,
                            new Packets.ReleaseRequest(slot, remainingUseTicks, targets).write());
                }
                case AREA -> {
                    var targets = TargetHelper.targetsFromArea(caster, currentSpell.range, currentSpell.on_release.target.area);
                    var targetIDs = new int[targets.size()];
                    int i = 0;
                    for (var target : targets) {
                        targetIDs[i] = target.getId();
                        i += 1;
                    }
                    ClientPlayNetworking.send(
                            Packets.ReleaseRequest.ID,
                            new Packets.ReleaseRequest(slot, remainingUseTicks, targetIDs).write());
                }
            }
            // Lookup target by target mode
        }
        System.out.println("Cast release");
    }

    private int findSlot(PlayerEntity player, ItemStack stack) {
        var inventory = player.getInventory().main;
        for(int i = 0; i < inventory.size(); ++i) {
            ItemStack itemStack = inventory.get(i);
            if (stack == itemStack) {
                return i;
            }
        }
        return -1;
    }

    private void updateTarget() {
        var caster = player();
        var currentSpell = getCurrentSpell();
        if (currentSpell == null) {
            targets = List.of();
            return;
        }
        switch (currentSpell.on_release.target.type) {
            case AREA -> {
                targets = TargetHelper.targetsFromArea(caster, currentSpell.range, currentSpell.on_release.target.area);
            }
            case BEAM -> {
                targets = TargetHelper.targetsFromRaycast(caster, currentSpell.range);
            }
            case CURSOR, PROJECTILE -> {
                var target = TargetHelper.targetFromRaycast(caster, currentSpell.range);
                if (target != null) {
                    targets = List.of(target);
                } else {
                    targets = List.of();
                }
                var cursor = currentSpell.on_release.target.cursor;
                if (cursor != null) {
                    var firstTarget = firstTarget();
                    if (firstTarget == null && cursor.use_caster_as_fallback) {
                        targets = List.of(caster);
                    }
                }
            }
        }
    }

//    @Inject(method = "clearActiveItem", at = @At("TAIL"))
//    private void clearCurrentSpell(CallbackInfo ci) {
//        System.out.println("Cast cancel");
//        currentSpell = null;
//    }

    @Inject(method = "tick", at = @At("TAIL"))
    public void tick_TAIL(CallbackInfo ci) {
        var player = player();
        if (!player.isUsingItem()) {
            targets = List.of();
        }
        if (isBeaming()) {
            networkHandler.sendPacket(new PlayerMoveC2SPacket.Full(
                    player.getX(), player.getY(), player.getZ(),
                    player.getYaw(), player.getPitch(),
                    player.isOnGround())
            );
        }
    }
}