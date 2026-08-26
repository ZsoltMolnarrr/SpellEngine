package net.spell_engine.mixin.loot;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;
import net.minecraft.world.level.storage.loot.entries.CompositeEntryBase;
import net.minecraft.world.level.storage.loot.entries.LootPoolEntryContainer;

@Mixin(CompositeEntryBase.class)
public interface CombinedEntryAccessor {
    @Accessor("children")
    List<LootPoolEntryContainer> spellEngine_getChildren();
}
