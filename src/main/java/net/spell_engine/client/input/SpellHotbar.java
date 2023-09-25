package net.spell_engine.client.input;

import net.minecraft.client.network.ClientPlayerEntity;
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
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class SpellHotbar {
    public static SpellHotbar INSTANCE = new SpellHotbar();

    public List<Slot> slots = List.of();
    public record Slot(SpellInfo spell, SpellCast.Mode castMode, @Nullable KeyBinding keybinding) { }

    public void update(ClientPlayerEntity player) {
        var slots = new ArrayList<Slot>();
        var held = player.getMainHandStack();
        var container = container(player, held);
        if (container != null) {
            var spellIds = container.spell_ids;
            for (int i = 0; i < spellIds.size(); i++) {
                var spellId = new Identifier(spellIds.get(i));
                var spell = SpellRegistry.getSpell(spellId);
                slots.add(new Slot(new SpellInfo(spell, spellId), SpellCast.Mode.from(spell), keyBinding(i)));
            }
        }
        this.slots = slots;
    }

    public boolean handle(ClientPlayerEntity player) {
        var caster = ((SpellCasterClient) player);
        var casted = caster.v2_getSpellCastProgress();
        var casterStack = player.getMainHandStack();
        updateDebounced();
        for(var slot: slots) {
            if (slot.keybinding != null) {
                var isPressed = slot.keybinding.isPressed();

                switch (slot.castMode()) {
                    case INSTANT -> {
                        if (isPressed) {
                            caster.v2_startSpellCast(casterStack, slot.spell.id());
                            return true;
                        }
                    }
                    case CHARGE, CHANNEL -> {
                        if (casted != null && casted.process().id().equals(slot.spell.id())) {
                            // The spell is already being casted
                            var needsToBeHeld = SpellHelper.isChanneled(casted.process().spell()) ?
                                    SpellEngineClient.config.holdToCastChannelled :
                                    SpellEngineClient.config.holdToCastCharged;
                            if (needsToBeHeld) {
                                if (!isPressed) {
                                    caster.v2_cancelSpellCast();
                                    return true;
                                }
                            } else {
                                if (isPressed && isReleased(slot.keybinding, Use.START)) {
                                    caster.v2_cancelSpellCast();
                                    debounce(slot.keybinding, Use.STOP);
                                    return true;
                                }
                            }
                        } else {
                            // A different spell or no spell is being casted
                            if (isPressed && isReleased(slot.keybinding, Use.STOP)) {
                                caster.v2_startSpellCast(casterStack, slot.spell.id());
                                debounce(slot.keybinding, Use.START);
                                return true;
                            }
                        }
                    }
                }
            }
        }

        return false;
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
        debounced.entrySet().removeIf(entry -> !entry.getKey().isPressed());
    }

    private static @Nullable KeyBinding keyBinding(int index) {
        return switch (index) {
            case 0 -> Keybindings.spell_hotbar_1;
            case 1 -> Keybindings.spell_hotbar_2;
            case 2 -> Keybindings.spell_hotbar_3;
            case 3 -> Keybindings.spell_hotbar_4;
            default -> null;
        };
    }

    private SpellContainer container(PlayerEntity player, ItemStack held) {
        return SpellContainerHelper.containerWithProxy(held, player);
    }
}
