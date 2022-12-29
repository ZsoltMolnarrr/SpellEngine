package net.spell_engine.mixin.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;
import net.spell_engine.api.spell.Spell;
import net.spell_engine.api.spell.SpellContainer;
import net.spell_engine.client.SpellEngineClient;
import net.spell_engine.internals.*;
import net.spell_engine.network.Packets;
import net.spell_engine.utils.TargetHelper;
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

    @Shadow public abstract boolean isUsingItem();

    @Shadow @Final protected MinecraftClient client;
    @Shadow private boolean usingItem;
    private int selectedSpellIndex = 0;

    private List<Entity> targets = List.of();

    private ClientPlayerEntity player() {
        return (ClientPlayerEntity) ((Object) this);
    }

    private Entity firstTarget() {
        return targets.stream().findFirst().orElse(null);
    }

    public void changeSelectedSpellIndex(int delta) {
        selectedSpellIndex += delta;
    }

    public void setSelectedSpellIndex(int index) {
        selectedSpellIndex = index;
    }

    public int getSelectedSpellIndex(SpellContainer container) {
        return container.cappedIndex(selectedSpellIndex);
    }

    public SpellContainer getCurrentContainer() {
        var container = containerFromItemStack(player().getMainHandStack());
        if (container == null) {
            container = containerFromItemStack(player().getOffHandStack());
        }
        return container;
    }

    private SpellContainer containerFromItemStack(ItemStack itemStack) {
        if (itemStack.isEmpty()) {
            return null;
        }
        var object = (Object)itemStack;
        if (object instanceof SpellCasterItemStack stack) {
            var container = stack.getSpellContainer();
            if (container != null && container.isValid()) {
                return container;
            }
        }
        return null;
    }

    private Identifier spellIdFromItemStack(ItemStack itemStack) {
        var container = containerFromItemStack(itemStack);
        if (container == null || !container.isUsable()) {
            return null;
        }
        return new Identifier(container.spellId(selectedSpellIndex));
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

    public boolean isOnCooldown(SpellContainer container) {
        var spellId = SpellRegistry.spellId(container, selectedSpellIndex);
        return getCooldownManager().isCoolingDown(spellId);
    }

    public boolean hasAmmoToStart(SpellContainer container, ItemStack itemStack) {
        var spell = SpellRegistry.spell(container, selectedSpellIndex);
        return spell != null && SpellHelper.ammoForSpell(player(), spell, itemStack).satisfied();
    }

    @Override
    public void castStart(SpellContainer container, ItemStack itemStack, int remainingUseTicks) {
        var caster = player();
        var slot = findSlot(caster, itemStack);
        var spellId = SpellRegistry.spellId(container, selectedSpellIndex);
        ClientPlayNetworking.send(
                Packets.SpellRequest.ID,
                new Packets.SpellRequest(SpellCastAction.START, spellId, slot, remainingUseTicks, new int[]{}).write());
        setCurrentSpell(spellId);
    }

    @Override
    public void castTick(ItemStack itemStack, int remainingUseTicks) {
        var currentSpell = getCurrentSpell();
        if (currentSpell == null
                || (SpellEngineClient.config.restartCastingWhenSwitchingSpell
                    && !getCurrentSpellId().equals(spellIdFromItemStack(itemStack)))
        ) {
            stopItemUsage();
            return;
        }

        updateTargets();
        var progress = SpellHelper.getCastProgress(player(), remainingUseTicks, currentSpell);
        if (SpellHelper.isChanneled(currentSpell)) {
            var action = (progress >= 1) ? SpellCastAction.RELEASE : SpellCastAction.CHANNEL;
            cast(currentSpell, action, itemStack, remainingUseTicks);
        } else {
            if (SpellEngineClient.config.autoRelease
                    && SpellHelper.getCastProgress(player(), remainingUseTicks, currentSpell) >= 1) {
                //cast(currentSpell, SpellCastAction.RELEASE, itemStack, remainingUseTicks);
                stopItemUsage();
            }
        }
    }

    @Override
    public void castRelease(ItemStack itemStack, int remainingUseTicks) {
        updateTargets();
        cast(getCurrentSpell(), SpellCastAction.RELEASE, itemStack, remainingUseTicks);
    }

    private void cast(Spell spell, SpellCastAction action, ItemStack itemStack, int remainingUseTicks) {
        if (spell == null) {
            return;
        }
        var caster = player();
        var progress = SpellHelper.getCastProgress(caster, remainingUseTicks, spell);
        var isChannelled = SpellHelper.isChanneled(spell);
        boolean shouldEndCasting = false;
        switch (action) {
            case CHANNEL -> {
                if (!isChannelled
                        || !SpellHelper.isChannelTickDue(spell, remainingUseTicks)) {
                    return;
                }
                shouldEndCasting = progress >= 1;
            }
            case RELEASE -> {
//                if (!isChannelled && progress < 1) {
//                    return;
//                }
                shouldEndCasting = true;
            }
        }

        var slot = findSlot(caster, itemStack);
        var release = spell.release.target;
        int[] targetIDs = new int[]{};
        switch (release.type) {
            case PROJECTILE, CURSOR -> {
                var firstTarget = firstTarget();
                if (firstTarget != null) {
                    targetIDs = new int[]{ firstTarget.getId() };
                }
            }
            case AREA, BEAM -> {
                targetIDs = new int[targets.size()];
                int i = 0;
                for (var target : targets) {
                    targetIDs[i] = target.getId();
                    i += 1;
                }
            }
        }
        var spellId = getCurrentSpellId();
        // System.out.println("Sending spell cast packet: " + new Packets.SpellRequest(action, spellId, slot, remainingUseTicks, targetIDs));
        ClientPlayNetworking.send(
                Packets.SpellRequest.ID,
                new Packets.SpellRequest(action, spellId, slot, remainingUseTicks, targetIDs).write());

        if (shouldEndCasting) {
            endCasting();
        }
    }

    private void stopItemUsage() {
        var client = MinecraftClient.getInstance();
        client.interactionManager.stopUsingItem(client.player);
    }

    private void endCasting() {
        player().clearActiveItem();
        setCurrentSpell(null);
    }

    private void clearCasting() {
        setCurrentSpell(null);
    }

    private int findSlot(PlayerEntity player, ItemStack stack) {
        for(int i = 0; i < player.getInventory().size(); ++i) {
            ItemStack itemStack = player.getInventory().getStack(i);
            if (stack == itemStack) {
                return i;
            }
        }
        return -1;
    }

    private void updateTargets() {
        targets = findTargets(getCurrentSpell());
    }

    private List<Entity> findTargets(Spell currentSpell) {
        var caster = player();
        List<Entity> targets = List.of();
        if (currentSpell == null) {
            return targets;
        }
        switch (currentSpell.release.target.type) {
            case AREA -> {
                targets = TargetHelper.targetsFromArea(caster, currentSpell.range, currentSpell.release.target.area);
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
                var cursor = currentSpell.release.target.cursor;
                if (cursor != null) {
                    var firstTarget = firstTarget();
                    if (firstTarget == null && cursor.use_caster_as_fallback) {
                        targets = List.of(caster);
                    }
                }
            }
        }
        return targets;
    }

    @Inject(method = "tick", at = @At("TAIL"))
    public void tick_TAIL_SpellEngine(CallbackInfo ci) {
        var player = player();
        var spellIdFromActiveStack = spellIdFromItemStack(player.getActiveItem());
        boolean usingItem = player.isUsingItem();
        if (!usingItem || spellIdFromActiveStack == null) {
            targets = List.of();
        }
        if (spellIdFromActiveStack == null) {
            clearCasting();
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