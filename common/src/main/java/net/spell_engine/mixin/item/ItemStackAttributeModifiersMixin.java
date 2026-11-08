package net.spell_engine.mixin.item;

import com.google.common.collect.Multimap;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtElement;
import net.spell_engine.utils.AttributeModifierUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/// Serves the per-item attribute modifier defaults registered through `AttributeModifierUtil.setItemModifiers`
/// (the 1.20.1 stand-in for `Item.Settings#attributeModifiers`) for any item class, unless the stack carries its
/// own `AttributeModifiers` NBT. Same method on Fabric and Forge (Forge's `IForgeItem` variant is called inside it).
///
/// The defaults replace the item's base modifiers where `getAttributeModifiers` stores them, not the method's
/// result: the result already includes what other mods add on top of the base (Forge's
/// `ItemAttributeModifierEvent`, Fabric's `ModifyItemAttributeModifiersCallback`), and replacing it dropped those,
/// e.g. gem and affix attributes on RPG Series weapons.
///
/// Stores into the method's multimap local, on both loaders: ordinal 0 is the empty map of the NBT branch,
/// ordinal 1 is the item's base modifiers (the one Fabric API also modifies), and on Forge ordinal 2 is the result
/// of `ForgeHooks.getAttributeModifiers`, which must be left alone.
@Mixin(ItemStack.class)
public abstract class ItemStackAttributeModifiersMixin {
    @ModifyVariable(method = "getAttributeModifiers", at = @At(value = "STORE", ordinal = 1))
    private Multimap<EntityAttribute, EntityAttributeModifier> spellEngine_itemAttributeModifiers(
            Multimap<EntityAttribute, EntityAttributeModifier> original, EquipmentSlot slot) {
        var stack = (ItemStack) (Object) this;
        var modifiers = AttributeModifierUtil.itemModifiers(stack.getItem());
        if (modifiers == null) {
            return original;
        }
        // Stacks with their own NBT modifiers keep them (this also covers the store in the NBT branch).
        var nbt = stack.getNbt();
        if (nbt != null && nbt.contains("AttributeModifiers", NbtElement.LIST_TYPE)) {
            return original;
        }
        return modifiers.forSlot(slot);
    }
}
