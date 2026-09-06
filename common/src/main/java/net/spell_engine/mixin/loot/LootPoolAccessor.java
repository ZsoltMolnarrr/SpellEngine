package net.spell_engine.mixin.loot;

import net.minecraft.loot.LootPool;
import net.minecraft.loot.entry.LootPoolEntry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/// `LootPool.entries` is package-private on 1.20.1 (Fabric API widens it, Forge does not) — loader-neutral read access.
@Mixin(LootPool.class)
public interface LootPoolAccessor {
    @Accessor("entries")
    LootPoolEntry[] spellEngine_getEntries();
}
