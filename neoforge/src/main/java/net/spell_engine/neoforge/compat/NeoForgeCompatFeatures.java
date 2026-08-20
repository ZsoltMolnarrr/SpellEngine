package net.spell_engine.neoforge.compat;

import net.spell_engine.neoforge.compat.curios.CuriosCompat;
import net.spell_engine.neoforge.compat.curios.CuriosCompatHeader;
import org.jetbrains.annotations.Nullable;

public class NeoForgeCompatFeatures {
    public static void init() {
        initSlotCompat();
    }

    /// Initializes slot mod (Curios) integration, if available. Idempotent.
    /// Returns the id of the active slot mod, or `null` if none.
    /// Mirrors the Fabric counterpart, whose String return is ecosystem API.
    @Nullable
    public static String initSlotCompat() {
        return CuriosCompat.init() ? CuriosCompatHeader.MOD_ID : null;
    }
}
