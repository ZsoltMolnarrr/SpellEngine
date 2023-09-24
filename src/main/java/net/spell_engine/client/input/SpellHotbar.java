package net.spell_engine.client.input;

import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.spell_engine.api.spell.SpellContainer;
import net.spell_engine.api.spell.SpellInfo;
import net.spell_engine.client.SpellEngineClient;
import net.spell_engine.internals.SpellCasterClient;
import net.spell_engine.internals.SpellContainerHelper;
import net.spell_engine.internals.SpellHelper;
import net.spell_engine.internals.SpellRegistry;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class SpellHotbar {
    public static SpellHotbar INSTANCE = new SpellHotbar();

    public List<Slot> slots = List.of();
    public record Slot(SpellInfo spell, @Nullable KeyBinding keybinding) { }

    public void update(ClientPlayerEntity player) {
        var slots = new ArrayList<Slot>();
        var held = player.getMainHandStack();
        var container = container(player, held);
        if (container != null) {
            var spellIds = container.spell_ids;
            for (int i = 0; i < spellIds.size(); i++) {
                var spellId = new Identifier(spellIds.get(i));
                var spell = SpellRegistry.getSpell(spellId);
                slots.add(new Slot(new SpellInfo(spell, spellId), keyBinding(i)));
            }
        }
        this.slots = slots;
    }

    public boolean handle(ClientPlayerEntity player) {
        var caster = ((SpellCasterClient) player);
        var casted = caster.v2_getSpellCastProgress();
        if (casted != null) {
            var slot = slotForSpell(casted.id());
            var needsToBeHeld = SpellHelper.isChanneled(casted.spell()) ?
                    SpellEngineClient.config.holdToCastChannelled :
                    SpellEngineClient.config.holdToCastCasted;
            if (needsToBeHeld
                    && slot != null
                    && slot.keybinding != null
                    && !slot.keybinding.isPressed()) {
                caster.v2_cancelSpellCast();
            }
        }

        for(var slot: slots) {
            if (slot.keybinding != null && slot.keybinding.isPressed()) {
                caster.v2_startSpellCast(player.getMainHandStack(), slot.spell.id());
                return true;
            }
        }
        return false;
    }

    private Slot slotForSpell(Identifier spellId) {
        for (var slot: slots) {
            if (slot.spell.id().equals(spellId)) {
                return slot;
            }
        }
        return null;
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
