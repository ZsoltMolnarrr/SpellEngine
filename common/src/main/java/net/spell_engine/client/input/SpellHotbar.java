package net.spell_engine.client.input;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Options;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Holder;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.spell_engine.api.spell.Spell;
import net.spell_engine.client.SpellEngineClient;
import net.spell_engine.client.gui.HudMessages;
import net.spell_engine.internals.casting.SpellCast;
import net.spell_engine.internals.casting.SpellCaster;
import net.spell_engine.mixin.client.control.KeybindingAccessor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;

public class SpellHotbar {
    public static SpellHotbar INSTANCE = new SpellHotbar();

    /// A castable {@link SpellCast.Option} decorated with client-only input state (keybinding).
    /// A null option is the item-use bypass slot (right-click usage of the held item).
    public record Slot(@Nullable SpellCast.Option option, @Nullable ItemStack itemStack, @Nullable WrappedKeybinding keybinding, @Nullable KeyMapping modifier) {
        @Nullable public Holder<Spell> spell() {
            return option != null ? option.spell() : null;
        }
        public SpellCast.Mode castMode() {
            return option != null ? option.mode() : SpellCast.Mode.ITEM_USE;
        }
        @Nullable public KeyMapping getKeyBinding(Options options) {
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

    public boolean update(LocalPlayer player, Options options) {
        var changed = false;
        var initialSlotCount = slots.size();
        // Server-declared options — with a client-side prediction bridging the round-trip gap
        // after local equipment changes, so the hotbar reacts on the same frame as the swap
        var castOptions = ((SpellCaster.Client) player).getCastController().displayOptions();

        var slots = new ArrayList<Slot>();
        var otherSlots = new ArrayList<Slot>();
        Slot onUseKey = null;

        var allBindings = Keybindings.Wrapped.all();
        var useKey = ((KeybindingAccessor) options.keyUse).spellEngine_getBoundKey();
        var useKeyBinding = new WrappedKeybinding(options.keyUse, WrappedKeybinding.VanillaAlternative.USE_KEY);

        if (!castOptions.isEmpty()) {
            var itemUseExpectation = expectedUseStack(player);
            if (itemUseExpectation != null)  {
                onUseKey = new Slot(null, itemUseExpectation.itemStack, useKeyBinding, null);
            }

            int keyBindingIndex = 0;
            for (SpellCast.Option option : castOptions) {
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
                var slot = new Slot(option, null, keyBinding, null);

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

    @Nullable public Handle handleAll(LocalPlayer player, Options options, List<KeyMapping> exclude) {
        return handleSlotsInternal(player, this.slots, options, exclude);
    }

    @Nullable public Handle handleUseKey(LocalPlayer player, Options options) {
        return handleSlotsInternal(player, this.structuredSlots.onUseKey != null ? List.of(this.structuredSlots.onUseKey) : List.of(), options, List.of());
    }

    @Nullable public Handle handleOther(LocalPlayer player, Options options, List<KeyMapping> exclude) {
        return handleSlotsInternal(player, this.structuredSlots.other(), options, exclude);
    }

    @Nullable public Handle handleSome(LocalPlayer player, @Nullable Slot slot, Options options, List<KeyMapping> exclude) {
        if (slot == null) { return null; }
        return handleSlotsInternal(player, List.of(slot), options, exclude);
    }

    @Nullable public Handle lastHandled() {
        return handledThisTick;
    }

    public record Handle(Holder<Spell> spell, KeyMapping keyBinding, @Nullable WrappedKeybinding.Category category, SpellCast.Attempt attempt) {
        public static Handle from(Slot slot, KeyMapping keyBinding, @Nullable WrappedKeybinding.Category category) {
            return new Handle(slot.spell(), keyBinding, category, null);
        }
        public Handle withAttempt(SpellCast.Attempt attempt) {
            return new Handle(this.spell, this.keyBinding, this.category, attempt);
        }
        public boolean isSuccessfulAttempt() {
            return attempt != null && attempt.isSuccess();
        }
        public boolean isUseKey(Options options) {
            return keyBinding == null ? false : keyBinding.same(options.keyUse);
        }
    }

    @Nullable private Handle handleSlotsInternal(LocalPlayer player, List<Slot> slots, Options options, List<KeyMapping> exclude) {
        if (handledThisTick != null || player.isSpectator()) { return null; }
        if (Keybindings.bypass_spell_hotbar.isDown()
                || (SpellEngineClient.config.sneakingByPassSpellHotbar && options.keyShift.isDown())) {
            return null;
        }
//        if (itemUseCooldown > 0) {
//            return null;
//        }
        for(var slot: slots) {
            if (slot.keybinding != null) {
                var unwrapped = slot.keybinding.get(options);
                if (unwrapped == null) { continue; }
                var keyBinding = unwrapped.keyBinding();
                if (exclude.contains(keyBinding)) {
                    continue;
                }
                var pressed = keyBinding.isDown();
                var handle = Handle.from(slot, keyBinding, unwrapped.vanillaHandle());
                if (pressed) {
                    this.lastPressed = handle;
                }

                if (slot.castMode() == SpellCast.Mode.ITEM_USE) {
                    if (options.keyUse.isDown()) {
                        return null;
                    }
                } else if (pressed) {
                    // Forward the control event; the controller owns what it means for this
                    // cast mode. The hotbar only keeps the edge memory (debounce) it reports.
                    var reaction = ((SpellCaster.Client) player).getCastController().keyHeld(slot.option(),
                            isReleased(keyBinding, UseCase.STOP), isReleased(keyBinding, UseCase.START));
                    switch (reaction.type()) {
                        case STARTED -> {
                            debounce(keyBinding, UseCase.START);
                            var handledWithAttempt = handle.withAttempt(reaction.attempt());
                            handledThisTick = handledWithAttempt;
                            displayAttempt(reaction.attempt(), slot.spell());
                            return handledWithAttempt;
                        }
                        case STOPPED, RELEASED -> {
                            debounce(keyBinding, UseCase.STOP);
                            handledThisTick = handle;
                            return handle;
                        }
                        case NONE -> { }
                    }
                } else {
                    if (((SpellCaster.Client) player).getCastController().keyUp(slot.option())) {
                        handledThisTick = handle;
                        return handle;
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

    private Holder<Spell> attemptedSpell = null;
    private void displayAttempt(SpellCast.Attempt attempt, Holder<Spell> spell) {
        if (Objects.equals(spell, attemptedSpell)) {
            return;
        }
        if (attempt.isFail()) {
            HudMessages.INSTANCE.castAttemptError(attempt);
        }
        this.attemptedSpell = spell;
    }

    private enum UseCase { START, STOP }
    private final HashMap<KeyMapping, UseCase> debounced = new HashMap<>();

    private boolean isReleased(KeyMapping keybinding, UseCase use) {
        return debounced.get(keybinding) != use;
    }

    private void debounce(KeyMapping keybinding, UseCase use) {
        debounced.put(keybinding, use);
    }

    private void updateDebounced() {
         debounced.entrySet().removeIf(entry -> !entry.getKey().isDown());
    }


    public record ItemUseExpectation(InteractionHand hand, ItemStack itemStack) {
        public boolean isMainHand() {
            return hand == InteractionHand.MAIN_HAND;
        }
    }

    public static ItemUseExpectation expectedUseStack(Player player) {
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack itemStack = player.getItemInHand(hand);
            if (itemStack.getUseAnimation() != ItemUseAnimation.NONE) {
                return new ItemUseExpectation(hand, itemStack);
            }
        }
        return null;
    }

    public boolean isShowingItemUse() {
        return structuredSlots.onUseKey != null && structuredSlots.onUseKey.itemStack != null;
    }
}
