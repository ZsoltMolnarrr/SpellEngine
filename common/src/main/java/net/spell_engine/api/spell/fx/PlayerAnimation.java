package net.spell_engine.api.spell.fx;

import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryKeys;
import net.spell_engine.utils.PatternMatching;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public class PlayerAnimation {
    public static class Override { public Override() { }
        public static class Equipment { public Equipment() { }
            /// Specific equipment slot to check
            public EquipmentSlot slot;
            /// Item ID matcher (universal pattern matching)
            public String item;
            public Equipment(EquipmentSlot slot, String item) {
                this.slot = slot;
                this.item = item;
            }
        }
        @Nullable public Equipment equipment;
        @Nullable public Boolean two_handed_weapon;
        public String id = "";
    }
    public String id = "";
    public float speed = 1F;
    public List<Override> overrides = List.of();
    public PlayerAnimation() { }
    public PlayerAnimation(String id) {
        this.id = id;
    }
    public PlayerAnimation(String id, float speed) {
        this.id = id;
        this.speed = speed;
    }

    // Builders

    public static PlayerAnimation of(String id) {
        return new PlayerAnimation(id);
    }

    public PlayerAnimation withEquipmentOverride(EquipmentSlot slot, String itemMatcher, String animationId) {
        var override = new Override();
        override.equipment = new Override.Equipment(slot, itemMatcher);
        override.id = animationId;
        this.overrides = new ArrayList<>(this.overrides);
        this.overrides.add(override);
        return this;
    }

    // Validator

    public String resolve(LivingEntity entity) {
        for (var override : this.overrides) {
            if (override.equipment != null) {
                var slot = override.equipment.slot;
                var itemMatcher = override.equipment.item;
                if (slot == null && itemMatcher == null) {
                    continue;
                }
                var equippedItem = entity.getEquippedStack(slot);
                if (PatternMatching.matches(equippedItem.getRegistryEntry(), RegistryKeys.ITEM, itemMatcher)) {
                    return override.id;
                }
            }
            if (override.two_handed_weapon != null) {
                if (twoHandedChecker.apply(entity.getMainHandStack()) == override.two_handed_weapon) {
                    return override.id;
                }
            }
        }
        return this.id;
    }

    public static Function<ItemStack, Boolean> twoHandedChecker = itemStack -> {
        return false;
    };
}
