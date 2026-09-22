package net.spell_engine.neoforge.compat;

import org.jetbrains.annotations.Nullable;

public class NeoForgeCompatFeatures {
    /// The Curios integration (`compat/curios/**`) is excluded from the compile when `enable_curios=false`
    /// (see gradle.properties — the kill switch for game versions Curios has no build for yet), so it is
    /// reached reflectively rather than by a direct reference; with the flag on this resolves the real class.
    private static final String CURIOS_COMPAT = "net.spell_engine.neoforge.compat.curios.CuriosCompat";
    private static final String CURIOS_MOD_ID = "curios";

    public static void init() {
        initSlotCompat();
    }

    /// Initializes slot mod (Curios) integration, if available. Idempotent.
    /// Returns the id of the active slot mod, or `null` if none.
    /// Mirrors the Fabric counterpart, whose String return is ecosystem API.
    @Nullable
    public static String initSlotCompat() {
        try {
            var compat = Class.forName(CURIOS_COMPAT);
            var enabled = (boolean) compat.getMethod("init").invoke(null);
            return enabled ? CURIOS_MOD_ID : null;
        } catch (ClassNotFoundException e) {
            return null; // built without the Curios integration
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Failed to initialize Curios compat", e);
        }
    }
}
