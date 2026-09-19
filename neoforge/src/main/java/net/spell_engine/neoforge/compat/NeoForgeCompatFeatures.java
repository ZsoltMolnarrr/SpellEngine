package net.spell_engine.neoforge.compat;

import org.jetbrains.annotations.Nullable;

public class NeoForgeCompatFeatures {
    /// TODO 26.3 (Curios): the Curios integration (`compat/curios/**`) is excluded from the compile while
    /// `enable_curios=false` (see gradle.properties), so it is reached reflectively instead of by a direct
    /// reference. With the gate restored the class is present again and this resolves it as before.
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
