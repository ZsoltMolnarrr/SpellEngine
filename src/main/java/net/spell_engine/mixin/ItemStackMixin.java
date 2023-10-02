package net.spell_engine.mixin;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.spell_engine.api.spell.SpellContainer;
import net.spell_engine.internals.SpellCasterItemStack;
import net.spell_engine.internals.SpellContainerHelper;
import net.spell_engine.internals.SpellRegistry;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(ItemStack.class)
public abstract class ItemStackMixin implements SpellCasterItemStack {
    @Shadow public abstract Item getItem();

    private ItemStack itemStack() {
        return (ItemStack) ((Object)this);
    }

    @Nullable
    private SpellContainer spellContainer() {
        var nbtContainer = spellContainerFromNBT();
        if (nbtContainer != null) {
            return nbtContainer;
        }
        return spellContainerDefault();
    }

    @Nullable
    private SpellContainer spellContainerFromNBT() {
        var itemStack = itemStack();
        if (!itemStack.hasNbt()) {
            return null;
        }
        return SpellContainerHelper.fromNBT(itemStack.getNbt());
    }

    @Nullable
    private SpellContainer spellContainerDefault() {
        var item = getItem();
        var id = Registries.ITEM.getId(item);
        return SpellRegistry.containerForItem(id);
    }

    // SpellCasterItemStack

    @Nullable
    public SpellContainer getSpellContainer() {
        return spellContainer();
    }
}