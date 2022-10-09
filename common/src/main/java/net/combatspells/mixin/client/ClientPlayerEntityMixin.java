package net.combatspells.mixin.client;

import net.combatspells.api.SpellHelper;
import net.combatspells.api.spell.Spell;
import net.combatspells.internals.SpellCasterClient;
import net.combatspells.network.Packets;
import net.combatspells.utils.TargetHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayerEntity.class)
public abstract class ClientPlayerEntityMixin implements SpellCasterClient {
    private Spell currentSpell;
    private Entity target;

    private ClientPlayerEntity player() {
        return (ClientPlayerEntity) ((Object) this);
    }

    @Override
    public Spell getCurrentSpell() {
        return currentSpell;
    }

    public Entity getCurrentTarget() {
        return target;
    }

    public float getCurrentCastProgress() {
        if (currentSpell != null) {
            return SpellHelper.getCastProgress(player(), player().getItemUseTimeLeft(), currentSpell.cast.duration);
        }
        return 0;
    }

    @Override
    public void castStart(Spell spell) {
        currentSpell = spell;
        System.out.println("Spell casting - Start");
    }

    @Override
    public void castTick(int remainingUseTicks) {
        updateTarget();
    }

    @Override
    public void castRelease(ItemStack itemStack, int remainingUseTicks) {
        var progress = SpellHelper.getCastProgress(player(), remainingUseTicks, currentSpell.cast.duration);
        var caster = player();
        if (progress >= 1) {
            var slot = caster.getInventory().indexOf(itemStack);
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

    private void updateTarget() {
        var caster = player();
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
            currentSpell = null;
            target = null;
        }
    }
}