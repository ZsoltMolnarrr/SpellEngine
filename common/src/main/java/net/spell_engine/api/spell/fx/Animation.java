package net.spell_engine.api.spell.fx;

import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.registry.RegistryKeys;
import net.spell_engine.utils.PatternMatching;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class Animation {
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
        public String id = "";
    }
    public String id = "";
    public List<Override> overrides = List.of();
    public Animation() { }
    public Animation(String id) {
        this.id = id;
    }

    // Builders

    public static Animation of(String id) {
        return new Animation(id);
    }

    public Animation withEquipmentOverride(EquipmentSlot slot, String itemMatcher, String animationId) {
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
        }
        return this.id;
    }
}
