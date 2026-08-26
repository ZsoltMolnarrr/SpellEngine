package net.spell_engine.mixin.entity;

import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeMap;
import net.minecraft.world.entity.player.Player;
import net.spell_engine.api.item.set.EquipmentSet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import java.util.List;

@Mixin(Player.class)
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
        var player = (Player) (Object) this;
        AttributeMap attributeContainer = player.getAttributes();
        for(var bonus: EquipmentSet.attributesFrom(activeEquipmentSets)) {
            for (var modifier: bonus.modifiers()) {
                AttributeInstance entityAttributeInstance = attributeContainer.getInstance(modifier.attribute());
                if (entityAttributeInstance != null) {
                    entityAttributeInstance.removeModifier(modifier.modifier());
                }
            }
        }
        this.activeEquipmentSets = results;
        /// Add attribute bonuses of new sets to player
        for(var bonus: EquipmentSet.attributesFrom(activeEquipmentSets)) {
            for (var modifier: bonus.modifiers()) {
                AttributeInstance entityAttributeInstance = attributeContainer.getInstance(modifier.attribute());
                if (entityAttributeInstance != null) {
                    entityAttributeInstance.removeModifier(modifier.modifier().id());
                    entityAttributeInstance.addTransientModifier(modifier.modifier());
                }
            }
        }
    }
}
