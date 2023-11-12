package net.spell_engine.client.input;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.UseAction;
import net.spell_engine.SpellEngineMod;
import net.spell_engine.api.item.trinket.SpellBookItem;
import net.spell_engine.api.spell.Spell;
import net.spell_engine.api.spell.SpellContainer;
import net.spell_engine.api.spell.SpellInfo;
import net.spell_engine.client.SpellEngineClient;
import net.spell_engine.client.gui.HudMessages;
import net.spell_engine.internals.SpellContainerHelper;
import net.spell_engine.internals.SpellHelper;
import net.spell_engine.internals.SpellRegistry;
import net.spell_engine.internals.casting.SpellCast;
import net.spell_engine.internals.casting.SpellCasterClient;
import net.spell_engine.mixin.client.control.KeybindingAccessor;
import net.spell_engine.network.Packets;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;

public class SpellHotbar {
    public static SpellHotbar INSTANCE = new SpellHotbar();

    private static KeyBinding deadKey = new KeyBinding(
            "keybindings." + SpellEngineMod.ID + ".dead",
            InputUtil.Type.KEYSYM,
            InputUtil.UNKNOWN_KEY.getCode(),
            SpellEngineMod.modName());

    public record Slot(SpellInfo spell, SpellCast.Mode castMode, @Nullable WrappedKeybinding keybinding) {
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
        var held = player.getMainHandStack();
        var container = SpellContainerHelper.containerWithProxy(held, player);

        var slots = new ArrayList<Slot>();
        var useKey = ((KeybindingAccessor) options.useKey).getBoundKey();
        Slot onUseKey = null;
        var otherSlots = new ArrayList<Slot>();

        var allBindings = Keybindings.Wrapped.all();

        if (!(held.getItem() instanceof SpellBookItem) // Disable hotbar directly for spell books
                && container != null) {
            var spellIds = container.spell_ids;
            var spellInfoList = spellIds.stream()
                    .map(idString -> {
                        var id = new Identifier(idString);
                        var spell = SpellRegistry.getSpell(id);
                        if (spell == null) {
                            return null;
                        }
                        return new SpellInfo(spell, id);
                    })
                    .filter(Objects::nonNull)
                    .toList();

            // Try to find binding for UseKey first
            SpellInfo skillForUseKey = null;
            for (var info: spellInfoList) {
                if (info.spell().mode == Spell.Mode.ITEM_USE) {
                    if (info.spell().item_use.requires_offhand_item && player.getOffHandStack().isEmpty()) { continue; }
                    skillForUseKey = info;
                }
            }

            // boolean allowUseKeyForCastable = skillForUseKey == null || container.content == SpellContainer.ContentType.ARCHERY;
            if (skillForUseKey != null || container.content == SpellContainer.ContentType.ARCHERY) { // Don't allow use key for castables
                // Filtering out assignable keybindings for Archery content
                // So item use can stay intact
                allBindings = allBindings.stream()
                        .filter(wrappedKeybinding -> {
                            var vanillaKeybinding = wrappedKeybinding.alternative.keyBindingFrom(options);
                            return vanillaKeybinding == null || !vanillaKeybinding.equals(options.useKey);
                        })
                        .toList();
            }

            // int keyBindingOffset = 0;
            int keyBindingIndex = 0;
            for (SpellInfo spellInfo : spellInfoList) {
                var spellId = spellInfo.id();
                var spell = spellInfo.spell();
                if (spell == null) {
                    continue;
                }
                WrappedKeybinding keyBinding = null;
                switch (spell.mode) {
                    case CAST -> {
                        if (keyBindingIndex < allBindings.size()) {
                            keyBinding = allBindings.get(keyBindingIndex);
                            keyBindingIndex += 1;
                        }
                    }
                    case ITEM_USE -> {
                        if (spell.item_use.requires_offhand_item && player.getOffHandStack().isEmpty()) { continue; }
                        // Dead (unbound, unregistered) keybinding is given,
                        // so it is forced to fall back to vanilla keybinding
                        keyBinding = new WrappedKeybinding(deadKey, WrappedKeybinding.VanillaAlternative.USE_KEY);
                        // keyBindingOffset -= 1; // Keybindings are taken in order, do not consume in this case
                    }
                }

                // Create slot
                var slot = new Slot(new SpellInfo(spell, spellId), SpellCast.Mode.from(spell), keyBinding);

                // Try to categorize slot based on keybinding
                if (keyBinding != null) {
                    var unwrapped = keyBinding.get(options);
                    if (unwrapped != null) {
                        var hotbarKey = ((KeybindingAccessor) unwrapped.keyBinding()).getBoundKey();

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
        }
        changed = initialSlotCount != slots.size();
        this.structuredSlots = new StructuredSlots(onUseKey, otherSlots);
        this.slots = slots;
        return changed;
    }

    private @Nullable Handle handledThisTick = null;
    private @Nullable Handle handledPreviousTick = null;
    private boolean skipHandling = false;
    public void prepare(int itemUseCooldown) {
        this.handledPreviousTick = this.handledThisTick;
        this.handledThisTick = null;
        this.updateDebounced();
        this.skipHandling = !lastHandledWasItemBypass() && itemUseCooldown > 0;
    }

    public boolean lastHandledWasItemBypass() {
        return handledPreviousTick != null
                && handledPreviousTick.spell().spell().mode == Spell.Mode.ITEM_USE;
    }

    @Nullable public Handle handle(ClientPlayerEntity player, GameOptions options) {
        return handle(player, this.slots, options);
    }

    @Nullable public Handle handle(ClientPlayerEntity player, @Nullable Slot slot, GameOptions options) {
        if (slot == null) { return null; }
        return handle(player, List.of(slot), options);
    }

    public record Handle(SpellInfo spell, KeyBinding keyBinding, @Nullable WrappedKeybinding.Category category) {
        public static Handle from(Slot slot, KeyBinding keyBinding, @Nullable WrappedKeybinding.Category category) {
            return new Handle(slot.spell, keyBinding, category);
        }
    }

    @Nullable public Handle handle(ClientPlayerEntity player, List<Slot> slots, GameOptions options) {
        if (handledThisTick != null || skipHandling) { return null; }
        if (Keybindings.bypass_spell_hotbar.isPressed()
                || (SpellEngineClient.config.sneakingByPassSpellHotbar && options.sneakKey.isPressed())) {
            return null;
        }
        var caster = ((SpellCasterClient) player);
        var casted = caster.getSpellCastProgress();
        var casterStack = player.getMainHandStack();
        for(var slot: slots) {
            if (slot.keybinding != null) {
                var unwrapped = slot.keybinding.get(options);
                if (unwrapped == null) { continue; }
                var keyBinding = unwrapped.keyBinding();
                var pressed = keyBinding.isPressed();
                var handle = Handle.from(slot, keyBinding, unwrapped.vanillaHandle());

                switch (slot.castMode()) {
                    case INSTANT, ITEM_USE -> {
                        if (pressed) {
                            var attempt = caster.startSpellCast(casterStack, slot.spell.id());
                            handledThisTick = handle;
                            displayAttempt(attempt);
                            return handle;
                        }
                    }
                    case CHARGE, CHANNEL -> {
                        if (casted != null && casted.process().id().equals(slot.spell.id())) {
                            // The spell is already being casted
                            var needsToBeHeld = SpellHelper.isChanneled(casted.process().spell()) ?
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
                                var attempt = caster.startSpellCast(casterStack, slot.spell.id());
                                debounce(keyBinding, UseCase.START);
                                handledThisTick = handle;
                                displayAttempt(attempt);
                                return handle;
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

        lastDisplayedAttempt = null; // Clearing last displayed attempt when no key is pressed
        return null;
    }

    private SpellCast.Attempt lastDisplayedAttempt = null;
    private void displayAttempt(SpellCast.Attempt attempt) {
        if (lastDisplayedAttempt != null) { // Require releasing hotbar keys before displaying another attempt
            return;
        }
        if (attempt.isFail()) {
            HudMessages.INSTANCE.castAttemptError(attempt);
        }
        lastDisplayedAttempt = attempt;
    }

    private Identifier lastSyncedSpellId = null;
    public void syncItemUseSkill(ClientPlayerEntity player) {
        Identifier idToSync = null;
        if (player.isUsingItem()
                && handledThisTick != null
                && handledThisTick.spell().spell().mode == Spell.Mode.ITEM_USE) {
            idToSync = handledThisTick.spell().id();
        }
        if (!Objects.equals(idToSync, lastSyncedSpellId)) {
            // System.out.println("Syncing item use skill: " + idToSync);
            ClientPlayNetworking.send(
                    Packets.SpellCastSync.ID,
                    new Packets.SpellCastSync(idToSync, 1, 1000).write()
            );
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

    public static ItemStack expectedUseStack(PlayerEntity player) {
        for (Hand hand : Hand.values()) {
            ItemStack itemStack = player.getStackInHand(hand);
            if (itemStack.getUseAction() != UseAction.NONE) {
                return itemStack;
            }
        }
        return ItemStack.EMPTY;
    }
}
