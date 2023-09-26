package net.spell_engine.client.input;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
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

    public record Slot(SpellInfo spell, SpellCast.Mode castMode, @Nullable KeyBinding keybinding) { }
    public List<Slot> slots = List.of();
    public CategorizedSlots categorizedSlots = new CategorizedSlots(null, List.of());
    public record CategorizedSlots(@Nullable Slot onUseKey, List<Slot> other) { }
    public void update(ClientPlayerEntity player, GameOptions options) {
        var held = player.getMainHandStack();
        var container = container(player, held);

        var slots = new ArrayList<Slot>();
        var useKey = ((KeybindingAccessor) options.useKey).getBoundKey();
        Slot onUseKey = null;
        var otherSlots = new ArrayList<Slot>();

        if (container != null) {
            var spellIds = container.spell_ids;
            for (int i = 0; i < spellIds.size(); i++) {
                var spellId = new Identifier(spellIds.get(i));
                var spell = SpellRegistry.getSpell(spellId);
                var keyBinding = keyBinding(i);

                // Create slot
                var slot = new Slot(new SpellInfo(spell, spellId), SpellCast.Mode.from(spell), keyBinding);

                // Try to categorize slot based on keybinding
                if (keyBinding != null) {
                    var hotbarKey = ((KeybindingAccessor) keyBinding).getBoundKey();
                    if (hotbarKey.equals(useKey)) {
                        onUseKey = slot;
                    } else {
                        otherSlots.add(slot);
                    }
                }

                // Save to all slots
                slots.add(slot);
            }
        }

        this.slots = slots;
        this.categorizedSlots = new CategorizedSlots(onUseKey, otherSlots);
    }

    private boolean handledKeyThisTick = false;
    public void clearHandle() {
        this.handledKeyThisTick = false;
    }

    @Nullable public KeyBinding handle(ClientPlayerEntity player) {
        return handle(player, this.slots);
    }

    @Nullable public KeyBinding handle(ClientPlayerEntity player, @Nullable Slot slot) {
        if (slot == null) { return null; }
        return handle(player, List.of(slot));
    }

    @Nullable public KeyBinding handle(ClientPlayerEntity player, List<Slot> slots) {
        if (handledKeyThisTick) { return null; }
        var caster = ((SpellCasterClient) player);
        var casted = caster.v2_getSpellCastProgress();
        var casterStack = player.getMainHandStack();
        updateDebounced();
        for(var slot: slots) {
            if (slot.keybinding != null) {
                var hotbarKey = ((KeybindingAccessor) slot.keybinding).getBoundKey();
                var pressed = hotbarKey.getCode() < 10
                        ? slot.keybinding.isPressed()
                        : InputUtil.isKeyPressed(MinecraftClient.getInstance().getWindow().getHandle(), hotbarKey.getCode());
                // var pressed = slot.keybinding.isPressed();

                switch (slot.castMode()) {
                    case INSTANT -> {
                        if (pressed) {
                            caster.v2_startSpellCast(casterStack, slot.spell.id());
                            return slot.keybinding;
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
                                    caster.v2_cancelSpellCast();
                                    return slot.keybinding;
                                }
                            } else {
                                if (pressed && isReleased(slot.keybinding, Use.START)) {
                                    caster.v2_cancelSpellCast();
                                    debounce(slot.keybinding, Use.STOP);
                                    return slot.keybinding;
                                }
                            }
                        } else {
                            // A different spell or no spell is being casted
                            if (pressed && isReleased(slot.keybinding, Use.STOP)) {
                                caster.v2_startSpellCast(casterStack, slot.spell.id());
                                debounce(slot.keybinding, Use.START);
                                return slot.keybinding;
                            }
                        }
                    }
                }
                if (pressed) {
                    return slot.keybinding;
                }
            }
        }

        return null;
    }

    private enum Use { START, STOP }
    private HashMap<KeyBinding, Use> debounced = new HashMap<>();

    private boolean isReleased(KeyBinding keybinding, Use use) {
        return debounced.get(keybinding) != use;
    }

    private void debounce(KeyBinding keybinding, Use use) {
        debounced.put(keybinding, use);
    }

    private void updateDebounced() {
        // debounced.entrySet().removeIf(entry -> !entry.getKey().isPressed());

        debounced.entrySet().removeIf(entry -> {
            var keybinding = entry.getKey();
            var hotbarKey = ((KeybindingAccessor) keybinding).getBoundKey();
            var pressed = hotbarKey.getCode() < 10
                    ? keybinding.isPressed()
                    : InputUtil.isKeyPressed(MinecraftClient.getInstance().getWindow().getHandle(), hotbarKey.getCode());
            return !pressed;
        });
    }

    private static @Nullable KeyBinding keyBinding(int index) {
        if (index < Keybindings.spellHotbar.size()) {
            return Keybindings.spellHotbar.get(index);
        }
        return null;
    }

    private SpellContainer container(PlayerEntity player, ItemStack held) {
        return SpellContainerHelper.containerWithProxy(held, player);
    }
}
