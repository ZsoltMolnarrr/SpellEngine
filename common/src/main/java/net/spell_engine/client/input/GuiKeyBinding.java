package net.spell_engine.client.input;

import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;

/**
 * A keybinding that is only ever polled while a `Screen` is open (tooltips, item catalogs, ...),
 * and so may share a key with an in-game binding without disabling it.
 *
 * Vanilla routes key presses through a map holding exactly ONE binding per key, and only feeds
 * it while no screen is open. A GUI scoped binding therefore gains nothing from being in that
 * map, but taking a key vanilla already uses (Left Shift / sneak) would evict the vanilla
 * binding and silently disable that action in-game.
 *
 * Marker only, kept out of the routing map per platform:
 * `KeyBindingGuiScopeMixin` on Fabric, `KeyConflictContext.GUI` on NeoForge.
 * Read these by polling the key state, see `SpellTooltip.isKeyPressed`.
 */
public class GuiKeyBinding extends KeyBinding {
    public GuiKeyBinding(String translationKey, InputUtil.Type type, int code, String category) {
        super(translationKey, type, code, category);
    }
}
