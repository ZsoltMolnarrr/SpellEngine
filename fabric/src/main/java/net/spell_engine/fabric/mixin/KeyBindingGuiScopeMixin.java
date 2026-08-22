package net.spell_engine.fabric.mixin;

import net.minecraft.client.option.KeyBinding;
import net.spell_engine.client.input.GuiKeyBinding;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Map;

/**
 * Keeps `GuiKeyBinding`s out of vanilla's key routing map.
 *
 * That map holds a single binding per key, so a GUI scoped binding sharing a key with
 * a vanilla one (Left Shift / sneak) would evict it and disable the vanilla action.
 * GUI scoped bindings are read by polling the key state instead, and key presses are
 * not routed to bindings while a screen is open anyway, so leaving them out costs nothing.
 *
 * Fabric only: NeoForge replaces this map with a conflict context aware multi map,
 * where nothing is evicted, and declares the scope through `KeyConflictContext.GUI` instead.
 */
@Mixin(KeyBinding.class)
public class KeyBindingGuiScopeMixin {
    @Redirect(method = "updateKeysByCode",
            at = @At(value = "INVOKE", target = "Ljava/util/Map;put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"))
    private static Object spellEngine_skipGuiScoped(Map<Object, Object> keyToBindings, Object key, Object binding) {
        if (binding instanceof GuiKeyBinding) {
            return null; // Return value is unused by the caller
        }
        return keyToBindings.put(key, binding);
    }
}
