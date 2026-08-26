package net.spell_engine.mixin.loot;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;
import net.minecraft.world.level.storage.loot.entries.LootPoolSingletonContainer;
import net.minecraft.world.level.storage.loot.functions.LootItemFunction;

@Mixin(LootPoolSingletonContainer.class)
public interface LeafEntryAccessor {
    @Accessor("weight")
    int spellEngine_getWeight();

    @Accessor("functions")
    List<LootItemFunction> spellEngine_getFunctions();
}
