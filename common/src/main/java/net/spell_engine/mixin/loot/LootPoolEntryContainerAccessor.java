package net.spell_engine.mixin.loot;

import net.minecraft.core.Holder;
import net.minecraft.world.level.storage.loot.entries.LootPoolEntryContainer;
import net.minecraft.world.level.storage.loot.functions.LootItemFunction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Optional;

/// The single `modifier` (item function) holder of a loot entry (26.3; was the `functions` list of the leaf).
@Mixin(LootPoolEntryContainer.class)
public interface LootPoolEntryContainerAccessor {
    @Accessor("modifier")
    Optional<Holder<LootItemFunction>> spellEngine_getModifier();
}
