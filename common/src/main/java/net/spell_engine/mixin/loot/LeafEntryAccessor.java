package net.spell_engine.mixin.loot;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.world.level.storage.loot.entries.UniformContainerBase;

/// 26.3: `LootPoolSingletonContainer` became `UniformContainerBase` (weight/quality); the entry functions moved to
/// the single `modifier` holder on `LootPoolEntryContainer`, see [LootPoolEntryContainerAccessor].
@Mixin(UniformContainerBase.class)
public interface LeafEntryAccessor {
    @Accessor("weight")
    int spellEngine_getWeight();
}
