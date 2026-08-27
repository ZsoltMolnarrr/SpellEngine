package net.spell_engine.fabric.compat.trinkets;

public class TrinketsCompatHeader {
    /// Slot-mod id reported to content mods by `FabricCompatFeatures.initSlotCompat()` and used for the
    /// built-in `spell_engine:trinkets_compat` data pack id. Trinkets Updated 4.0 (mod id `trinkets_updated`)
    /// declares `"provides": ["trinkets"]`, so this id keeps matching `FabricLoader.isModLoaded` and the
    /// `"trinkets"` comparisons in Archers / Relics / Jewelry.
    public static final String MOD_ID = "trinkets";
    /// The real mod id of Trinkets Updated 4.0 (`fabric.mod.json` `recommends` uses this one).
    public static final String MOD_ID_UPDATED = "trinkets_updated";
}
