package net.spell_engine.client.input;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.spell_engine.api.item.trinket.SpellBookItem;
import net.spell_engine.api.spell.SpellContainer;
import net.spell_engine.client.SpellEngineClient;
import net.spell_engine.internals.SpellCasterClient;

public class InputHelper {
    public static boolean isLocked = false;

    public static boolean shouldControlSpellHotbar() {
        return isLocked || Keybindings.hotbarModifier.isPressed();
    }

    public static boolean isLockAssigned() {
        return !Keybindings.hotbarLock.isUnbound();
    }

    public static void toggleLock() {
        toggleLock(false);
    }

    public static void toggleLock(boolean skipValidation) {
        var client = MinecraftClient.getInstance();
        if (isLocked) {
            isLocked = false;
        } else {
            if (skipValidation || hasLockableSpellContainer(client.player)) {
                isLocked = true;
            }
        }
    }

    public static boolean hasLockableSpellContainer(PlayerEntity player) {
        if (player != null) {
            var result = ((SpellCasterClient)player).getCurrentContainerWithHost();
            if (result.host().getItem() instanceof SpellBookItem) {
                return false;
            }
            return canLockOnContainer(result.container()); // And only lock if more than 1 spell
        }
        return false;
    }

    public static boolean canLockOnContainer(SpellContainer container) {
        return container != null
                && container.isUsable() // Usable
                && container.spell_ids.size() > 1; // And only lock if more than 1 spell
    }

    public static void showLockedMessage(String key) {
        var client = MinecraftClient.getInstance();
        MutableText component = Text.translatable("hud.leave_spell_hotbar", key);
        client.inGameHud.setOverlayMessage(component, false);
    }

    public record HotbarVisibility(boolean item, boolean spell) { }

    public static HotbarVisibility hotbarVisibility() {
        var config = SpellEngineClient.config;
        boolean item = true;
        boolean spell = true;
        if (config.showFocusedHotbarOnly) {
            spell = isLocked;
            item = !spell;
        }
        return new HotbarVisibility(item, spell);
    }
}
