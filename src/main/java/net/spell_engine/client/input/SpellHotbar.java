package net.spell_engine.client.input;

import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.spell_engine.api.spell.SpellContainer;
import net.spell_engine.api.spell.SpellInfo;
import net.spell_engine.client.SpellEngineClient;
import net.spell_engine.internals.SpellContainerHelper;
import net.spell_engine.internals.SpellHelper;
import net.spell_engine.internals.SpellRegistry;
import net.spell_engine.internals.casting.SpellCast;
import net.spell_engine.internals.casting.SpellCasterClient;
import net.spell_engine.mixin.client.control.KeybindingAccessor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class SpellHotbar {
    public static SpellHotbar INSTANCE = new SpellHotbar();

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

    public void update(ClientPlayerEntity player, GameOptions options) {
        var held = player.getMainHandStack();
        var container = container(player, held);

        var slots = new ArrayList<Slot>();
        var useKey = ((KeybindingAccessor) options.useKey).getBoundKey();
        Slot onUseKey = null;
        var otherSlots = new ArrayList<Slot>();

        var allBindings = Keybindings.Wrapped.all();

        if (container != null) {
            var spellIds = container.spell_ids;
            for (int i = 0; i < spellIds.size(); i++) {
                var spellId = new Identifier(spellIds.get(i));
                var spell = SpellRegistry.getSpell(spellId);
                WrappedKeybinding keyBinding = null;
                if (i < allBindings.size()) {
                    keyBinding = allBindings.get(i);
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

        this.structuredSlots = new StructuredSlots(onUseKey, otherSlots);
        this.slots = slots;
    }

    private boolean handledKeyThisTick = false;
    public void prepare() {
        this.handledKeyThisTick = false;
        this.updateDebounced();
    }

    @Nullable public WrappedKeybinding.Category handle(ClientPlayerEntity player, GameOptions options) {
        return handle(player, this.slots, options);
    }

    @Nullable public WrappedKeybinding.Category handle(ClientPlayerEntity player, @Nullable Slot slot, GameOptions options) {
        if (slot == null) { return null; }
        return handle(player, List.of(slot), options);
    }

    @Nullable public WrappedKeybinding.Category handle(ClientPlayerEntity player, List<Slot> slots, GameOptions options) {
        if (handledKeyThisTick) { return null; }
        if (Keybindings.ignore_spell_hotbar.isPressed()) { return null; }
        var caster = ((SpellCasterClient) player);
        var casted = caster.getSpellCastProgress();
        var casterStack = player.getMainHandStack();
        for(var slot: slots) {
            if (slot.keybinding != null) {
                var unwrapped = slot.keybinding.get(options);
                if (unwrapped == null) { continue; }
                var keyBinding = unwrapped.keyBinding();
                var pressed = keyBinding.isPressed();
                var handle = unwrapped.vanillaHandle();

                switch (slot.castMode()) {
                    case INSTANT -> {
                        if (pressed) {
                            caster.startSpellCast(casterStack, slot.spell.id());
                            handledKeyThisTick = true;
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
                                    handledKeyThisTick = true;
                                    return handle;
                                }
                            } else {
                                if (pressed && isReleased(keyBinding, UseCase.START)) {
                                    caster.cancelSpellCast();
                                    debounce(keyBinding, UseCase.STOP);
                                    handledKeyThisTick = true;
                                    return handle;
                                }
                            }
                        } else {
                            // A different spell or no spell is being casted
                            if (pressed && isReleased(keyBinding, UseCase.STOP)) {
                                caster.startSpellCast(casterStack, slot.spell.id());
                                debounce(keyBinding, UseCase.START);
                                handledKeyThisTick = true;
                                return handle;
                            }
                        }
                    }
                }
                if (pressed) {
                    handledKeyThisTick = true;
                    return handle;
                }
            }
        }

        return null;
    }

    private enum UseCase { START, STOP }
    private HashMap<KeyBinding, UseCase> debounced = new HashMap<>();

    private boolean isReleased(KeyBinding keybinding, UseCase use) {
        return debounced.get(keybinding) != use;
    }

    private void debounce(KeyBinding keybinding, UseCase use) {
        debounced.put(keybinding, use);
    }

    private void updateDebounced() {
         debounced.entrySet().removeIf(entry -> !entry.getKey().isPressed());
    }

    private SpellContainer container(PlayerEntity player, ItemStack held) {
        return SpellContainerHelper.containerWithProxy(held, player);
    }
}
