package net.spell_engine.mixin.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.toast.TutorialToast;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.spell_engine.SpellEngineMod;
import net.spell_engine.api.spell.Spell;
import net.spell_engine.api.spell.SpellContainer;
import net.spell_engine.client.SpellEngineClient;
import net.spell_engine.client.input.InputHelper;
import net.spell_engine.client.input.Keybindings;
import net.spell_engine.internals.*;
import net.spell_engine.network.Packets;
import net.spell_engine.utils.TargetHelper;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.function.Predicate;

@Mixin(ClientPlayerEntity.class)
public abstract class ClientPlayerEntityMixin implements SpellCasterClient {
    @Shadow @Final public ClientPlayNetworkHandler networkHandler;

    @Shadow @Final protected MinecraftClient client;
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
        if (container == null && SpellEngineMod.config.offhand_casting_allowed) {
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

    public Identifier getSelectedSpellId(SpellContainer container){
        return SpellRegistry.spellId(container, selectedSpellIndex);
    }

    @Override
    public void castStart(SpellContainer container, Hand hand, ItemStack itemStack, int remainingUseTicks) {
        var caster = player();
        var slot = findSlot(caster, itemStack);
        var spellId = SpellRegistry.spellId(container, selectedSpellIndex);
        ClientPlayNetworking.send(
                Packets.SpellRequest.ID,
                new Packets.SpellRequest(hand, SpellCastAction.START, spellId, slot, remainingUseTicks, new int[]{}).write());
        setCurrentSpell(spellId);
    }

    @Override
    public void castTick(ItemStack itemStack, int remainingUseTicks) {
        var caster = player();
        var currentSpellId = getCurrentSpellId();
        var currentSpell = getCurrentSpell();
        if (currentSpell == null
                || !SpellHelper.canContinueToCastSpell((SpellCasterEntity) caster, currentSpellId)
                || (SpellEngineClient.config.restartCastingWhenSwitchingSpell
                    && !getCurrentSpellId().equals(spellIdFromItemStack(itemStack)))
        ) {
            stopItemUsage();
            return;
        }

        updateTargets();
        var progress = SpellHelper.getCastProgress(caster, remainingUseTicks, currentSpell);
        if (SpellHelper.isChanneled(currentSpell)) {
            var action = (progress >= 1) ? SpellCastAction.RELEASE : SpellCastAction.CHANNEL;
            cast(currentSpell, action, Hand.MAIN_HAND, itemStack, remainingUseTicks);
        } else {
            if (SpellEngineClient.config.autoRelease
                    && SpellHelper.getCastProgress(caster, remainingUseTicks, currentSpell) >= 1) {
                //cast(currentSpell, SpellCastAction.RELEASE, itemStack, remainingUseTicks);
                stopItemUsage();
            }
        }
    }

    @Override
    public void castRelease(ItemStack itemStack, int remainingUseTicks) {
        updateTargets();
        cast(getCurrentSpell(), SpellCastAction.RELEASE, Hand.MAIN_HAND, itemStack, remainingUseTicks);
    }

    private void cast(Spell spell, SpellCastAction action, Hand hand, ItemStack itemStack, int remainingUseTicks) {
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
                shouldEndCasting = true;
            }
        }

        var slot = findSlot(caster, itemStack);
        var release = spell.release.target;
        int[] targetIDs = new int[]{};
        switch (release.type) {
            case PROJECTILE, CURSOR, METEOR -> {
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
            case SELF -> {
            }
        }
        var spellId = getCurrentSpellId();
        // System.out.println("Sending spell cast packet: " + new Packets.SpellRequest(action, spellId, slot, remainingUseTicks, targetIDs));
        ClientPlayNetworking.send(
                Packets.SpellRequest.ID,
                new Packets.SpellRequest(hand, action, spellId, slot, remainingUseTicks, targetIDs).write());

        if (shouldEndCasting) {
            endCasting();
        }
    }

    public void stopSpellCasting() {
        endCasting();
        stopItemUsage();
    }

    private void stopItemUsage() {
        var client = MinecraftClient.getInstance();
        client.interactionManager.stopUsingItem(client.player);
    }

    private void endCasting() {
        clearCasting();
        player().clearActiveItem();
    }

    @Override
    public void clearCasting() {
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
        var previousTargets = this.targets;
        List<Entity> targets = List.of();
        if (currentSpell == null) {
            return targets;
        }
        boolean fallbackToPreviousTargets = false;
        var targetingMode = SpellHelper.selectionTargetingMode(currentSpell);
        var targetType = currentSpell.release.target.type;
        var intent = SpellHelper.intent(currentSpell);
        Predicate<Entity> selectionPredicate = (target) -> {
            return !SpellEngineClient.config.filterInvalidTargets || TargetHelper.actionAllowed(targetingMode, intent, caster, target);
        };
        switch (targetType) {
            case AREA -> {
                targets = TargetHelper.targetsFromArea(caster, currentSpell.range, currentSpell.release.target.area, selectionPredicate);
            }
            case BEAM -> {
                targets = TargetHelper.targetsFromRaycast(caster, currentSpell.range, selectionPredicate);
            }
            case CURSOR, PROJECTILE, METEOR -> {
                fallbackToPreviousTargets = targetType != Spell.Release.Target.Type.PROJECTILE; // All of these except `PROJECTILE`
                var target = TargetHelper.targetFromRaycast(caster, currentSpell.range, selectionPredicate);
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
            case SELF -> {
                // Nothing to do
            }
        }
        if (fallbackToPreviousTargets && SpellEngineClient.config.stickyTarget
                && targets.isEmpty()) {
            targets = previousTargets.stream()
                    .filter(entity -> {
                        return TargetHelper.isInLineOfSight(caster, entity);
                    })
                    .toList();
        }
        return targets;
    }

    private int tutorialToastTicks = 0;
    private TutorialToast tutorialToast;

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

        if (!SpellEngineClient.tutorial.value.spell_hotbar_shown) {
            var container = SpellContainerHelper.containerFromItemStack(player.getMainHandStack());
            if (InputHelper.canLockOnContainer(container)) {
                var keybinding = Keybindings.hotbarLock;
                var description = Text.translatable("tutorial.spell_hotbar.unbound");
                if (!keybinding.isUnbound()) {
                    var key = Text.of(keybinding.getBoundKeyLocalizedText().getString().toUpperCase()).copy().formatted(Formatting.BOLD);
                    description = Text.translatable("tutorial.spell_hotbar.description", key);
                }
                this.tutorialToast = new TutorialToast(TutorialToast.Type.MOVEMENT_KEYS, Text.translatable("tutorial.spell_hotbar.title"), description, false);
                this.tutorialToastTicks = 140;
                this.client.getToastManager().add(tutorialToast);
                SpellEngineClient.tutorial.value.spell_hotbar_shown = true;
                SpellEngineClient.tutorial.save();
            }
        }
        if (tutorialToastTicks > 0) {
            tutorialToastTicks -= 1;
            if (tutorialToastTicks == 0 && tutorialToast != null) {
                tutorialToast.hide();
                tutorialToast = null;
            }
        }
    }
}