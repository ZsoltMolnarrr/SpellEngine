package net.spell_engine.utils;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.AttributeModifiersComponent;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.entry.RegistryEntry;
import org.jetbrains.annotations.NotNull;

public class AttributeModifierUtil {
    public static @NotNull Multimap<RegistryEntry<EntityAttribute>, EntityAttributeModifier> modifierMultimap(ItemStack itemStack) {
        var modifiers = itemStack.getOrDefault(DataComponentTypes.ATTRIBUTE_MODIFIERS, AttributeModifiersComponent.DEFAULT);
        Multimap<RegistryEntry<EntityAttribute>, EntityAttributeModifier> modifiersMap = HashMultimap.create();
        for (var entry : modifiers.modifiers()) {
            modifiersMap.put(entry.attribute(), entry.modifier());
        }
        return modifiersMap;
    }

    public static boolean hasModifier(ItemStack itemStack, RegistryEntry<EntityAttribute> attribute) {
        var modifiers = itemStack.getOrDefault(DataComponentTypes.ATTRIBUTE_MODIFIERS, AttributeModifiersComponent.DEFAULT);
        for (var entry : modifiers.modifiers()) {
            if (entry.attribute().equals(attribute)) {
                return true;
            }
        }
        return false;
    }

    public static double flatBonusFrom(ItemStack itemStack, RegistryEntry<EntityAttribute> attribute) {
        var modifiers = itemStack.getOrDefault(DataComponentTypes.ATTRIBUTE_MODIFIERS, AttributeModifiersComponent.DEFAULT);
        for (var entry : modifiers.modifiers()) {
            if (entry.attribute().equals(attribute) && entry.modifier().operation() == EntityAttributeModifier.Operation.ADD_VALUE) {
                return entry.modifier().value();
            }
        }
        return 0;
    }

    public static boolean isItemStackEquipped(ItemStack itemStack, PlayerEntity player) {
        if (player.getMainHandStack().equals(itemStack)) {
            return true;
        }
        for (var armorSlot: player.getInventory().armor) {
            if (armorSlot.equals(itemStack)) {
                return true;
            }
        }
        for (var offhandSlot: player.getInventory().offHand) {
            if (offhandSlot.equals(itemStack)) {
                return true;
            }
        }
        return false;
    }
}
