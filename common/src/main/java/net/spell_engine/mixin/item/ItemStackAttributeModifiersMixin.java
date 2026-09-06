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
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/// Serves the per-item attribute modifier defaults registered through `AttributeModifierUtil.setItemModifiers`
/// (the 1.20.1 stand-in for `Item.Settings#attributeModifiers`) for any item class, unless the stack carries its
/// own `AttributeModifiers` NBT. Same method on Fabric and Forge (Forge's `IForgeItem` variant is called inside it).
@Mixin(ItemStack.class)
public abstract class ItemStackAttributeModifiersMixin {
    @Inject(method = "getAttributeModifiers", at = @At("RETURN"), cancellable = true)
    private void spellEngine_itemAttributeModifiers(EquipmentSlot slot, CallbackInfoReturnable<Multimap<EntityAttribute, EntityAttributeModifier>> cir) {
        var stack = (ItemStack) (Object) this;
        var modifiers = AttributeModifierUtil.itemModifiers(stack.getItem());
        if (modifiers == null) {
            return;
        }
        var nbt = stack.getNbt();
        if (nbt != null && nbt.contains("AttributeModifiers", NbtElement.LIST_TYPE)) {
            return;
        }
        cir.setReturnValue(modifiers.forSlot(slot));
    }
}
