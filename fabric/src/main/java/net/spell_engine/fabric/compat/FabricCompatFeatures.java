package net.spell_engine.fabric.compat;

import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.ResourcePackActivationType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.Identifier;
import net.spell_engine.SpellEngineMod;
import net.spell_engine.fabric.compat.trinkets.TrinketsCompat;
import net.spell_engine.fabric.compat.trinkets.TrinketsCompatHeader;
import org.jetbrains.annotations.Nullable;

public class FabricCompatFeatures {
    public static void initialize() {
        initSlotCompat();
    }

    private static boolean slotCompatInitialized = false;
    @Nullable private static String selectedSlotMod = null;

    /// Initializes slot mod (Trinkets) integration, if available. Idempotent.
    /// Returns the id of the active slot mod, or `null` if none.
    /// The String return is consumed by content mods (Archers, Relics, Jewelry)
    /// to align their own slot mod compat — keep the signature stable.
    @Nullable
    public static String initSlotCompat() {
        if (slotCompatInitialized) {
            return selectedSlotMod;
        }
        slotCompatInitialized = true;
        if (TrinketsCompat.init()) {
            selectedSlotMod = TrinketsCompatHeader.MOD_ID;
            var container = FabricLoader.getInstance().getModContainer(SpellEngineMod.ID);
            ResourceManagerHelper.registerBuiltinResourcePack(
                    Identifier.fromNamespaceAndPath(SpellEngineMod.ID, TrinketsCompatHeader.MOD_ID + "_compat"),
                    container.get(), ResourcePackActivationType.ALWAYS_ENABLED);
        }
        return selectedSlotMod;
    }
}
