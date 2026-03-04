package net.spell_engine.client.input;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.UseAction;
import net.spell_engine.api.spell.Spell;
import net.spell_engine.api.spell.registry.SpellRegistry;
import net.spell_engine.client.SpellEngineClient;
import net.spell_engine.client.gui.HudMessages;
import net.spell_engine.internals.SpellHelper;
import net.spell_engine.internals.casting.SpellCast;
import net.spell_engine.internals.casting.SpellCasterClient;
import net.spell_engine.internals.container.SpellContainerSource;
import net.spell_engine.mixin.client.control.KeybindingAccessor;
import net.spell_engine.network.Packets;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;

public class SpellHotbar {
    public static SpellHotbar INSTANCE = new SpellHotbar();

    public record Slot(RegistryEntry<Spell> spell, SpellCast.Mode castMode, @Nullable ItemStack itemStack, @Nullable WrappedKeybinding keybinding, @Nullable KeyBinding modifier) {
        @Nullable public KeyBinding getKeyBinding(GameOptions options) {
            if (keybinding != null) {
                var unwrapped = keybinding.get(options);
                if (unwrapped != null) {
                    return unwrapped.keyBinding();
                }
            }
            return null;
        }
    }
    public List<Slot> slots = List.of();
    public StructuredSlots structuredSlots = new StructuredSlots(null, List.of());
    public record StructuredSlots(@Nullable Slot onUseKey, List<Slot> other) { }

    public boolean update(ClientPlayerEntity player, GameOptions options) {
        var changed = false;
        var initialSlotCount = slots.size();
        var mergedContainer = SpellContainerSource.activeContainerOf(player);
                //SpellContainerHelper.getAvailable(player);

        var slots = new ArrayList<Slot>();
        var otherSlots = new ArrayList<Slot>();
        Slot onUseKey = null;

        var allBindings = Keybindings.Wrapped.all();
        var useKey = ((KeybindingAccessor) options.useKey).spellEngine_getBoundKey();
        var useKeyBinding = new WrappedKeybinding(options.useKey, WrappedKeybinding.VanillaAlternative.USE_KEY);

        if (mergedContainer != null
                && !mergedContainer.spell_ids().isEmpty()) {
            var itemUseExpectation = expectedUseStack(player);
            if (itemUseExpectation != null)  {
                onUseKey = new Slot(null, SpellCast.Mode.ITEM_USE, itemUseExpectation.itemStack, useKeyBinding, null);
            }

            var spellIds = mergedContainer.spell_ids();
            var spellEntryList = spellIds.stream()
                    .map(idString -> {
                        var id = Identifier.of(idString);
                        return SpellRegistry.from(player.getWorld()).getEntry(id).orElse(null);
                    })
                    .filter(Objects::nonNull)
                    .toList();

            int keyBindingIndex = 0;
            for (RegistryEntry<Spell> spellEntry : spellEntryList) {
                var spell = spellEntry.value();
                if (spell == null) {
                    continue;
                }

                WrappedKeybinding keyBinding = null;
                if (keyBindingIndex < allBindings.size()) {
                    keyBinding = allBindings.get(keyBindingIndex);
                    keyBindingIndex += 1;
                } else {
                    continue;
                }

                // Override keybinding with UseKey if available
                if (SpellEngineClient.config.spellHotbarUseKey) {
                    if (onUseKey == null) {
                        keyBinding = useKeyBinding;
                    }
                }

                // Create slot
                var slot = new Slot(spellEntry, SpellCast.Mode.from(spell), null, keyBinding, null);

                // Try to categorize slot based on keybinding
                if (keyBinding != null) {
                    var unwrapped = keyBinding.get(options);
                    if (unwrapped != null) {
                        var hotbarKey = ((KeybindingAccessor) unwrapped.keyBinding()).spellEngine_getBoundKey();

                        if (hotbarKey.equals(useKey)) {
                            onUseKey = slot;
                        } else {
                            otherSlots.add(slot);
                        }
                    }
                }

                // Save to all slots
                slots.add(slot);
            }

            if (itemUseExpectation != null) {
                if (itemUseExpectation.isMainHand()) {
                    slots.addFirst(onUseKey);
                } else {
                    slots.addLast(onUseKey);
                }
            }
        }

        changed = initialSlotCount != slots.size();
        this.structuredSlots = new StructuredSlots(onUseKey, otherSlots);
        this.slots = slots;
        return changed;
    }


    private @Nullable Handle handledThisTick = null;
    private @Nullable Handle lastPressed = null;
    private int itemUseCooldown = 0;
    public void prepare(int itemUseCooldown) {
        this.itemUseCooldown = itemUseCooldown;
        this.handledThisTick = null;
        if (lastPressed == null) {
            attemptedSpell = null;
        }
        this.updateDebounced();
    }

    @Nullable public Handle handleAll(ClientPlayerEntity player, GameOptions options, List<KeyBinding> exclude) {
        return handleSlotsInternal(player, this.slots, options, exclude);
    }

    @Nullable public Handle handleUseKey(ClientPlayerEntity player, GameOptions options) {
        return handleSlotsInternal(player, this.structuredSlots.onUseKey != null ? List.of(this.structuredSlots.onUseKey) : List.of(), options, List.of());
    }

    @Nullable public Handle handleOther(ClientPlayerEntity player, GameOptions options, List<KeyBinding> exclude) {
        return handleSlotsInternal(player, this.structuredSlots.other(), options, exclude);
    }

    @Nullable public Handle handleSome(ClientPlayerEntity player, @Nullable Slot slot, GameOptions options, List<KeyBinding> exclude) {
        if (slot == null) { return null; }
        return handleSlotsInternal(player, List.of(slot), options, exclude);
    }

    @Nullable public Handle lastHandled() {
        return handledThisTick;
    }

    public record Handle(RegistryEntry<Spell> spell, KeyBinding keyBinding, @Nullable WrappedKeybinding.Category category, SpellCast.Attempt attempt) {
        public static Handle from(Slot slot, KeyBinding keyBinding, @Nullable WrappedKeybinding.Category category) {
            return new Handle(slot.spell, keyBinding, category, null);
        }
        public Handle withAttempt(SpellCast.Attempt attempt) {
            return new Handle(this.spell, this.keyBinding, this.category, attempt);
        }
        public boolean isSuccessfulAttempt() {
            return attempt != null && attempt.isSuccess();
        }
        public boolean isUseKey(GameOptions options) {
            return keyBinding == null ? false : keyBinding.equals(options.useKey);
        }
    }

    @Nullable private Handle handleSlotsInternal(ClientPlayerEntity player, List<Slot> slots, GameOptions options, List<KeyBinding> exclude) {
        if (handledThisTick != null || player.isSpectator()) { return null; }
        if (Keybindings.bypass_spell_hotbar.isPressed()
                || (SpellEngineClient.config.sneakingByPassSpellHotbar && options.sneakKey.isPressed())) {
            return null;
        }
//        if (itemUseCooldown > 0) {
//            return null;
//        }
        var caster = ((SpellCasterClient) player);
        var casted = caster.getSpellCastProgress();
        var casterStack = player.getMainHandStack();
        for(var slot: slots) {
            if (slot.keybinding != null) {
                var unwrapped = slot.keybinding.get(options);
                if (unwrapped == null) { continue; }
                var keyBinding = unwrapped.keyBinding();
                if (exclude.contains(keyBinding)) {
                    continue;
                }
                var pressed = keyBinding.isPressed();
                var handle = Handle.from(slot, keyBinding, unwrapped.vanillaHandle());
                if (pressed) {
                    this.lastPressed = handle;
                }

                switch (slot.castMode()) {
                    case ITEM_USE -> {
                        if (options.useKey.isPressed()) {
                            return null;
                        }
                    }
                    case INSTANT -> {
                        if (pressed) {
                            var attempt = caster.startSpellCast(casterStack, slot.spell);
                            var handledWithAttempt = handle.withAttempt(attempt);
                            handledThisTick = handledWithAttempt;
                            displayAttempt(attempt, slot.spell);
                            return handledWithAttempt;
                        }
                    }
                    case CHARGE, CHANNEL -> {
                        if (casted != null && casted.process().id().equals(slot.spell.getKey().get().getValue())) {
                            // The spell is already being casted
                            var needsToBeHeld = SpellHelper.isChanneled(casted.process().spell().value()) ?
                                    SpellEngineClient.config.holdToCastChannelled :
                                    SpellEngineClient.config.holdToCastCharged;
                            if (needsToBeHeld) {
                                if (!pressed) {
                                    caster.cancelSpellCast();
                                    handledThisTick = handle;
                                    return handle;
                                }
                            } else {
                                if (pressed && isReleased(keyBinding, UseCase.START)) {
                                    caster.cancelSpellCast();
                                    debounce(keyBinding, UseCase.STOP);
                                    handledThisTick = handle;
                                    return handle;
                                }
                            }
                        } else {
                            // A different spell or no spell is being casted
                            if (pressed && isReleased(keyBinding, UseCase.STOP)) {
                                var attempt = caster.startSpellCast(casterStack, slot.spell);
                                debounce(keyBinding, UseCase.START);
                                var handledWithAttempt = handle.withAttempt(attempt);
                                handledThisTick = handledWithAttempt;
                                displayAttempt(attempt, slot.spell);
                                return handledWithAttempt;
                            }
                        }
                    }
                }
                if (pressed) {
                    handledThisTick = handle;
                    return handle;
                }
            }
        }

        this.lastPressed = null;
        return null;
    }

    private RegistryEntry<Spell> attemptedSpell = null;
    private void displayAttempt(SpellCast.Attempt attempt, RegistryEntry<Spell> spell) {
        if (Objects.equals(spell, attemptedSpell)) {
            return;
        }
        if (attempt.isFail()) {
            HudMessages.INSTANCE.castAttemptError(attempt);
        }
        this.attemptedSpell = spell;
    }

    private Identifier lastSyncedSpellId = null;
    public void syncItemUseSkill(ClientPlayerEntity player) {
        Identifier idToSync = null;
        if (!Objects.equals(idToSync, lastSyncedSpellId)) {
            // System.out.println("Syncing item use skill: " + idToSync);
            ClientPlayNetworking.send(new Packets.SpellCastSync(idToSync, 1, 1000));
            lastSyncedSpellId = idToSync;
        }
    }

    private enum UseCase { START, STOP }
    private final HashMap<KeyBinding, UseCase> debounced = new HashMap<>();

    private boolean isReleased(KeyBinding keybinding, UseCase use) {
        return debounced.get(keybinding) != use;
    }

    private void debounce(KeyBinding keybinding, UseCase use) {
        debounced.put(keybinding, use);
    }

    private void updateDebounced() {
         debounced.entrySet().removeIf(entry -> !entry.getKey().isPressed());
    }


    public record ItemUseExpectation(Hand hand, ItemStack itemStack) {
        public boolean isMainHand() {
            return hand == Hand.MAIN_HAND;
        }
    }

    public static ItemUseExpectation expectedUseStack(PlayerEntity player) {
        for (Hand hand : Hand.values()) {
            ItemStack itemStack = player.getStackInHand(hand);
            if (itemStack.getUseAction() != UseAction.NONE) {
                return new ItemUseExpectation(hand, itemStack);
            }
        }
        return null;
    }

    public boolean isShowingItemUse() {
        return structuredSlots.onUseKey != null && structuredSlots.onUseKey.itemStack != null;
    }
}
