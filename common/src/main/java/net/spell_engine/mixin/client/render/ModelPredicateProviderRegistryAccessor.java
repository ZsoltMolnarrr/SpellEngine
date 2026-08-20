package net.spell_engine.mixin.client.render;

import net.minecraft.client.item.ClampedModelPredicateProvider;
import net.minecraft.client.item.ModelPredicateProvider;
import net.minecraft.client.item.ModelPredicateProviderRegistry;
import net.minecraft.item.Item;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.Map;

@Mixin(ModelPredicateProviderRegistry.class)
public interface ModelPredicateProviderRegistryAccessor {
    @Accessor("ITEM_SPECIFIC")
    public static Map<Item, Map<Identifier, ModelPredicateProvider>> itemSpecificPredicates_SpellEngine() {
        throw new AssertionError();
    }

    /// The per-item registration is package-private in vanilla; this invoker replaces the access-widener
    /// entry, so no loader networking/rendering API (nor a widener) is needed.
    @Invoker("register")
    public static void register_SpellEngine(Item item, Identifier id, ClampedModelPredicateProvider provider) {
        throw new AssertionError();
    }
}
