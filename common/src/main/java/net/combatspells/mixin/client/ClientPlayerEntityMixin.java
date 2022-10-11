package net.combatspells.mixin.client;

import net.combatspells.api.SpellHelper;
import net.combatspells.api.spell.Spell;
import net.combatspells.client.CombatSpellsClient;
import net.combatspells.internals.SpellCasterClient;
import net.combatspells.network.Packets;
import net.combatspells.utils.TargetHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayerEntity.class)
public abstract class ClientPlayerEntityMixin implements SpellCasterClient {
    private Entity target;

    private ClientPlayerEntity player() {
        return (ClientPlayerEntity) ((Object) this);
    }

    public Entity getCurrentTarget() {
        return target;
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
            var progress = SpellHelper.getCastProgress(player(), remainingUseTicks, currentSpell.cast.duration);
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
        var progress = SpellHelper.getCastProgress(player(), remainingUseTicks, currentSpell.cast.duration);
        var caster = player();
        if (progress >= 1) {
            var slot = findSlot(caster, itemStack);
            var action = currentSpell.on_release.target;
            switch (action.type) {
                case PROJECTILE, CURSOR -> {
                    updateTarget();
                    var targets = new int[]{};
                    if (target != null) {
                        targets = new int[]{target.getId()};
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
            return;
        }
        target = TargetHelper.targetFromRaycast(player(), currentSpell.range);
        switch (currentSpell.on_release.target.type) {
            case CURSOR -> {
                var cursor = currentSpell.on_release.target.cursor;
                if (target == null && cursor.use_caster_as_fallback) {
                    target = caster;
                }
            }
            default -> {
            }
        }
    }

//    @Inject(method = "clearActiveItem", at = @At("TAIL"))
//    private void clearCurrentSpell(CallbackInfo ci) {
//        System.out.println("Cast cancel");
//        currentSpell = null;
//    }

    @Inject(method = "tick", at = @At("TAIL"))
    public void post_Tick(CallbackInfo ci) {
        if (!player().isUsingItem()) {
            target = null;
        }
    }
}