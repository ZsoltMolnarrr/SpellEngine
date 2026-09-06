package net.spell_engine.api.item.weapon;

import com.google.common.collect.Multimap;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.item.SwordItem;
import net.minecraft.item.ToolMaterial;
import net.spell_engine.api.item.ItemAttributeModifiers;
import net.spell_engine.rpg_series.item.ConfigurableAttributes;
import org.jetbrains.annotations.Nullable;

public class SpellWeaponItem extends SwordItem implements ConfigurableAttributes {
    @Nullable private ItemAttributeModifiers attributeModifiers;

    public SpellWeaponItem(ToolMaterial toolMaterial, Settings settings) {
        super(toolMaterial, 0, 0F, settings);
    }

    @Override
    public void setAttributes(ItemAttributeModifiers attributeModifiers) {
        this.attributeModifiers = attributeModifiers;
    }

    @Override
    public Multimap<EntityAttribute, EntityAttributeModifier> getAttributeModifiers(EquipmentSlot slot) {
        if (attributeModifiers != null) {
            return attributeModifiers.forSlot(slot);
        }
        return super.getAttributeModifiers(slot);
    }
}
