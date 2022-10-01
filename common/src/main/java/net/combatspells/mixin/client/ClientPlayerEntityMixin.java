package net.combatspells.mixin.client;

import net.combatspells.api.SpellHelper;
import net.combatspells.api.spell.Spell;
import net.combatspells.internals.SpellCasterClient;
import net.combatspells.network.Packets;
import net.combatspells.utils.TargetHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(ClientPlayerEntity.class)
public class ClientPlayerEntityMixin implements SpellCasterClient {
    private Spell currentSpell;
    private Entity target;
    private ClientPlayerEntity player() {
        return (ClientPlayerEntity)((Object)this);
    }

    @Override
    public Spell getCurrentSpell() {
        return currentSpell;
    }

    @Override
    public void setCurrentSpell(Spell spell) {
        currentSpell = spell;
    }

    @Override
    public void castStart(Spell spell) {
        currentSpell = spell;

        // Start player animation
        // Start sound
        // Start spawning particles
    }

    @Override
    public void castTick(int remainingUseTicks) {
        updateTarget();
    }

    @Override
    public void castRelease(int remainingUseTicks) {
        var progress = SpellHelper.getCastProgress(remainingUseTicks, currentSpell.cast_duration);
        if (progress >= 1) {
            var action = currentSpell.on_release.action;
            switch (action.type) {
                case SHOOT_PROJECTILE -> {
                    updateTarget();
                    var targets = new int[]{ };
                    if (target != null) {
                        targets = new int[]{ target.getId() };
                    }
                    ClientPlayNetworking.send(
                            Packets.ReleaseRequest.ID,
                            new Packets.ReleaseRequest("currentSpell.id", targets).write());
                }
            }
            // Lookup target by target mode
        }
        System.out.println("Cast release");
    }

    private void updateTarget() {
        target = TargetHelper.raycastForTarget(player(), currentSpell.range);
        System.out.println("Targeting " + (target != null ? target.getEntityName() : "nothing" ) );
    }
}
