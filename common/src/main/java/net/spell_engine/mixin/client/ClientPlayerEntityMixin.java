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
import net.spell_engine.client.gui.HudMessages;
import net.spell_engine.client.input.InputHelper;
import net.spell_engine.client.input.Keybindings;
import net.spell_engine.internals.*;
import net.spell_engine.network.Packets;
import net.spell_engine.utils.TargetHelper;
import org.jetbrains.annotations.Nullable;
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

    // MARK: SpellCasterEntity overrides

    private Identifier currentSpell;

    @Override
    public void setCurrentSpellId(Identifier spellId) {
        currentSpell = spellId;
    }

    @Override
    public Identifier getCurrentSpellId() {
        if (player().isUsingItem()) {
            return currentSpell;
        }
        return null;
    }

    // MARK: SpellCasterClient

    public boolean isHotbarModifierPressed() {
        return Keybindings.hotbarModifier.isPressed();
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
        var player = player();
        var casterStack = player.getMainHandStack();
        var container = containerFromItemStack(casterStack);
        if (container == null && SpellEngineMod.config.offhand_casting_allowed) {
            container = containerFromItemStack(player().getOffHandStack());
        }
        return SpellContainerHelper.containerWithProxy(container, player);
    }

    private SpellContainer containerFromItemStack(ItemStack itemStack) {
        if (itemStack.isEmpty()) {
            return null;
        }
        return SpellContainerHelper.containerFromItemStack(itemStack);
    }

    @Nullable
    private Identifier spellIdFromItemStack(ItemStack itemStack) {
        var container = containerFromItemStack(itemStack);
        return SpellContainerHelper.spellId(container, selectedSpellIndex);
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

    @Nullable
    public Identifier getSelectedSpellId(SpellContainer container) {
        return SpellContainerHelper.spellId(container, selectedSpellIndex);
    }

    @Override
    public void castAttempt(SpellCast.Attempt result) {
        HudMessages.INSTANCE.castAttemptError(result);
    }

    @Override
    public void castStart(SpellContainer container, Hand hand, ItemStack itemStack, int remainingUseTicks) {
        var spellId = SpellContainerHelper.spellId(container, selectedSpellIndex);
        selectSpell(spellId, hand, itemStack, remainingUseTicks);
    }

    private void selectSpell(Identifier spellId, Hand hand, ItemStack itemStack, int remainingUseTicks) {
        var caster = player();
        var slot = findSlot(caster, itemStack);
        ClientPlayNetworking.send(
                Packets.SpellRequest.ID,
                new Packets.SpellRequest(hand, SpellCast.Action.START, spellId, slot, remainingUseTicks, new int[]{}).write());
        setCurrentSpellId(spellId);
    }

    @Override
    public void castTick(ItemStack itemStack, Hand hand, int remainingUseTicks) {
        // System.out.println("Spell cast tick: " + (SpellHelper.maximumUseTicks - remainingUseTicks));
        var caster = player();
        var currentSpellId = getCurrentSpellId();
        var currentSpell = getCurrentSpell();
        if (currentSpell == null
                || (SpellEngineClient.config.restartCastingWhenSwitchingSpell
                    && !currentSpellId.equals(spellIdFromItemStack(itemStack)))
        ) {
            stopItemUsage();
            return;
        }

        updateTargets();
        var progress = SpellHelper.getCastProgress(caster, remainingUseTicks, currentSpell);
        if (SpellHelper.isChanneled(currentSpell)) {
            cast(currentSpell, SpellCast.Action.CHANNEL, hand, itemStack, remainingUseTicks);
            if (progress >= 1) {
                // System.out.println("Channeled, stop");
                stopItemUsage(); // Triggers cast release (via: Player -> ItemStack(onStoppedUsing) -> castRelease)
            }
        } else {
            if (SpellEngineClient.config.autoRelease
                    && SpellHelper.getCastProgress(caster, remainingUseTicks, currentSpell) >= 1) {
                stopItemUsage(); // Triggers cast release (via: Player -> ItemStack(onStoppedUsing) -> castRelease)
            }
        }
    }

    @Override
    public void castRelease(ItemStack itemStack, Hand hand, int remainingUseTicks) {
        updateTargets();
        cast(getCurrentSpell(), SpellCast.Action.RELEASE, hand, itemStack, remainingUseTicks);
    }

    private void cast(Spell spell, SpellCast.Action action, Hand hand, ItemStack itemStack, int remainingUseTicks) {
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
            // endCasting();
            clearCasting();
        }
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
        setCurrentSpellId(null);
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
        var intents = SpellHelper.intents(currentSpell);
        Predicate<Entity> selectionPredicate = (target) -> {
            boolean intentAllows = false;
            for (var intent: intents) {
                intentAllows = intentAllows || TargetHelper.actionAllowed(targetingMode, intent, caster, target);
            }
            return !SpellEngineClient.config.filterInvalidTargets || intentAllows;
        };
        switch (targetType) {
            case AREA -> {
                targets = TargetHelper.targetsFromArea(caster, currentSpell.range, currentSpell.release.target.area, selectionPredicate);
                var area = currentSpell.release.target.area;
                if (area != null && area.include_caster) {
                    targets.add(caster);
                }
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

        var cursor = currentSpell.release.target.cursor;
        if (cursor != null) {
            if (cursor.use_caster_as_fallback && targets.isEmpty()) {
                targets = List.of(caster);
            }
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
            var container = SpellContainerHelper.containerWithProxy(player.getMainHandStack(), player);
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