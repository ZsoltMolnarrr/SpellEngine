package net.combatspells.mixin;

import com.google.common.collect.Multimap;
import net.combatspells.attribute_assigner.AttributeAssigner;
import net.combatspells.attribute_assigner.ItemAttributeApplier;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.registry.Registry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Mixin(ItemStack.class)
public abstract class AttributeAssigner_ItemStackMixin {
    @Shadow public abstract Item getItem();

    private boolean applied = false;

    @Inject(method = "updateEmptyState", at = @At("TAIL"))
    public void updateEmptyState_TAIL(CallbackInfo ci) {
        if (!applied) {

            // This will be applied multiple times (between relaunches) :D

            var item = getItem();
            var id = Registry.ITEM.getId(item);
            var assignment = AttributeAssigner.assignments.get(id);
            if (assignment != null) {
                var itemStack = (ItemStack) ((Object)this);
                var alreadyAppliedTag = "additional_attributes_applied";
                if (!itemStack.getNbt().contains(alreadyAppliedTag)) {
                    var slots = (assignment.slots != null && assignment.slots.length > 0) ? assignment.slots : defaultSlots();
                    ItemAttributeApplier.applyModifiersForItemStack(
                            slots,
                            id.toString(),
                            itemStack,
                            List.of(assignment.attributes));
                    itemStack.getNbt().putBoolean(alreadyAppliedTag, true);
                }
            }

            applied = true;
        }
    }

    private EquipmentSlot[] defaultSlots() {
        var item = getItem();
        if (item instanceof ArmorItem armor) {
            return new EquipmentSlot[]{ armor.getSlotType() };
        } else {
            return new EquipmentSlot[]{ EquipmentSlot.MAINHAND };
        }
    }

    // Attribute modifier caching

    private final Map<EquipmentSlot, Multimap<EntityAttribute, EntityAttributeModifier>> cachedAttributes = new HashMap<>();

    @Inject(method = "getAttributeModifiers", at = @At("RETURN"))
    private void getAttributeModifiers_RETURN(EquipmentSlot slot, CallbackInfoReturnable<Multimap<EntityAttribute, EntityAttributeModifier>> cir) {
        var parsedAttributes = cir.getReturnValue();
        // System.out.println("Attributes parsed");
        if (parsedAttributes != null && !parsedAttributes.isEmpty()) {
            cachedAttributes.put(slot, parsedAttributes);
        }
    }

    @Inject(method = "getAttributeModifiers", at = @At("HEAD"), cancellable = true)
    private void getAttributeModifiers_fromCache(EquipmentSlot slot, CallbackInfoReturnable<Multimap<EntityAttribute, EntityAttributeModifier>> cir) {
        var cached = cachedAttributes.get(slot);
        if (cached != null) {
            // System.out.println("Attributes from cache");
            cir.setReturnValue(cached);
            cir.cancel();
        }
    }

    @Inject(method = "addAttributeModifier", at = @At("HEAD"))
    private void addAttributeModifier_HEAD(EntityAttribute attribute, EntityAttributeModifier modifier, EquipmentSlot slot, CallbackInfo ci) {
        cachedAttributes.clear();
    }
}
