package net.spell_engine.utils;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import org.jetbrains.annotations.NotNull;

public class AttributeModifierUtil {
    public static @NotNull Multimap<Holder<Attribute>, AttributeModifier> modifierMultimap(ItemStack itemStack) {
        var modifiers = itemStack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
        Multimap<Holder<Attribute>, AttributeModifier> modifiersMap = HashMultimap.create();
        for (var entry : modifiers.modifiers()) {
            modifiersMap.put(entry.attribute(), entry.modifier());
        }
        return modifiersMap;
    }

    public static boolean hasModifier(ItemStack itemStack, Holder<Attribute> attribute) {
        var modifiers = itemStack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
        for (var entry : modifiers.modifiers()) {
            if (entry.attribute().equals(attribute)) {
                return true;
            }
        }
        return false;
    }

    public static double flatBonusFrom(ItemStack itemStack, Holder<Attribute> attribute) {
        var modifiers = itemStack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
        double value = 0;
        for (var entry : modifiers.modifiers()) {
            if (entry.attribute().equals(attribute) && entry.modifier().operation() == AttributeModifier.Operation.ADD_VALUE) {
                value += entry.modifier().amount();
            }
        }
        return value;
    }

    public static double multipliersOf(Holder<Attribute> attribute, LivingEntity entity) {
        double value = 1;
        double totalMultiplier = 1;
        var attributeInstance = entity.getAttributes().getInstance(attribute);
        if (attributeInstance != null) {
            for (var modifier: attributeInstance.getModifiers()) {
                switch (modifier.operation()) {
                    case ADD_VALUE -> {
                        break;
                    }
                    case ADD_MULTIPLIED_BASE -> {
                        value += modifier.amount();
                    }
                    case ADD_MULTIPLIED_TOTAL -> {
                        totalMultiplier += modifier.amount();
                    }
                }
            }
        }
        return value * totalMultiplier;
    }

    public static boolean isItemStackEquipped(ItemStack itemStack, Player player) {
        if (player.getMainHandItem().equals(itemStack)) {
            return true;
        }
        for (var slot: EquipmentSlot.VALUES) {
            if (slot == EquipmentSlot.MAINHAND) continue;
            if (player.getItemBySlot(slot).equals(itemStack)) {
                return true;
            }
        }
        return false;
    }
}
