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

    /// Set bonuses are re-applied on every spell-container update, so both loops must be able to take a
    /// modifier back off the attribute.
    ///
    /// **Use `removeModifier(UUID)`, never `tryRemoveModifier(UUID)`.** They are not variants of one
    /// another on 1.20.1: `tryRemoveModifier` removes only modifiers that are in `persistentModifiers`
    /// and is a silent no-op for everything else. Set bonuses are added with `addTemporaryModifier`, so
    /// `tryRemoveModifier` never removed one — the second update after a bonus became active then hit
    /// vanilla's "Modifier is already applied on this attribute!" guard in `addModifier`, killing the
    /// player tick every tick. (The 1.21.1 original calls `removeModifier(Identifier)`, which is
    /// unconditional; `removeModifier(UUID)` is its 1.20.1 counterpart.)
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
                    entityAttributeInstance.removeModifier(modifier.modifier().getId());
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
                    entityAttributeInstance.removeModifier(modifier.modifier().getId());
                    entityAttributeInstance.addTemporaryModifier(modifier.modifier());
                }
            }
        }
    }
}
