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
                EntityAttributeInstance entityAttributeInstance = attributeContainer.getCustomInstance(modifier.attribute());
                if (entityAttributeInstance != null) {
                    entityAttributeInstance.removeModifier(modifier.modifier());
                }
            }
        }
        this.activeEquipmentSets = results;
        /// Add attribute bonuses of new sets to player
        for(var bonus: EquipmentSet.attributesFrom(activeEquipmentSets)) {
            for (var modifier: bonus.modifiers()) {
                EntityAttributeInstance entityAttributeInstance = attributeContainer.getCustomInstance(modifier.attribute());
                if (entityAttributeInstance != null) {
                    entityAttributeInstance.removeModifier(modifier.modifier().id());
                    entityAttributeInstance.addTemporaryModifier(modifier.modifier());
                }
            }
        }
    }
}
