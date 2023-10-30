package net.spell_engine.mixin.client.render;

import net.minecraft.client.item.ModelPredicateProvider;
import net.minecraft.client.item.ModelPredicateProviderRegistry;
import net.minecraft.item.Item;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

@Mixin(ModelPredicateProviderRegistry.class)
public interface ModelPredicateProviderRegistryAccessor {
    @Accessor("ITEM_SPECIFIC")
    public static Map<Item, Map<Identifier, ModelPredicateProvider>> itemSpecificPredicates_SpellEngine() {
        throw new AssertionError();
    }
}
