package net.spell_engine.mixin.entity;

import net.minecraft.entity.attribute.AttributeContainer;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.player.PlayerEntity;
import net.spell_engine.api.item.set.EquipmentSet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import java.util.List;

@Mixin(PlayerEntity.class)
public class PlayerEquipmentSetMixin implements EquipmentSet.Owner {
    @Unique
    private List<EquipmentSet.Result> activeEquipmentSets = List.of();
    @Override
    public List<EquipmentSet.Result> getActiveEquipmentSets() {
        return activeEquipmentSets;
    }

    @Override
    public void setActiveEquipmentSets(List<EquipmentSet.Result> results) {
        /// Remove attribute bonuses of previous sets from player
        var player = (PlayerEntity) (Object) this;
        AttributeContainer attributeContainer = player.getAttributes();
        for(var bonus: EquipmentSet.attributesFrom(activeEquipmentSets)) {
            for (var modifier: bonus.modifiers()) {
                var attribute = modifier.attributeValue();
                if (attribute == null) { continue; } // Attribute not registered on this runtime (optional mod)
                EntityAttributeInstance entityAttributeInstance = attributeContainer.getCustomInstance(attribute);
                if (entityAttributeInstance != null) {
                    entityAttributeInstance.tryRemoveModifier(modifier.modifier().getId());
                }
            }
        }
        this.activeEquipmentSets = results;
        /// Add attribute bonuses of new sets to player
        for(var bonus: EquipmentSet.attributesFrom(activeEquipmentSets)) {
            for (var modifier: bonus.modifiers()) {
                var attribute = modifier.attributeValue();
                if (attribute == null) { continue; } // Attribute not registered on this runtime (optional mod)
                EntityAttributeInstance entityAttributeInstance = attributeContainer.getCustomInstance(attribute);
                if (entityAttributeInstance != null) {
                    entityAttributeInstance.tryRemoveModifier(modifier.modifier().getId());
                    entityAttributeInstance.addTemporaryModifier(modifier.modifier());
                }
            }
        }
    }
}
