package net.combatspells.mixin;

import net.combatspells.attribute_assigner.AttributeAssigner;
import net.combatspells.attribute_assigner.ItemAttributeApplier;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.registry.Registry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static net.minecraft.entity.EquipmentSlot.MAINHAND;

@Mixin(ItemStack.class)
public abstract class ItemStack_AttributeAssignMixin {
    @Shadow public abstract Item getItem();

    private boolean applied = false;

    @Inject(method = "updateEmptyState", at = @At("TAIL"))
    public void updateEmptyState_TAIL(CallbackInfo ci) {
        if (!applied) {

            // This will be applied multiple times (between relaunches) :D

            var item = getItem();
            var id = Registry.ITEM.getId(item);
            var additionalAttributes = AttributeAssigner.assignemnts.get(id);
            if (additionalAttributes != null) {
                var itemStack = (ItemStack) ((Object)this);
                var alreadyAppliedTag = "additional_attributes_applied";
                if (!itemStack.getNbt().contains(alreadyAppliedTag)) {
                    ItemAttributeApplier.applyModifiersForItemStack(
                            new EquipmentSlot[]{ EquipmentSlot.MAINHAND },
                            id.toString(),
                            itemStack,
                            additionalAttributes);
                    itemStack.getNbt().putBoolean(alreadyAppliedTag, true);
                }
            }

            applied = true;
        }
    }
}
