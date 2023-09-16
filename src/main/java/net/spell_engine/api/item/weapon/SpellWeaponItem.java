package net.spell_engine.api.item.weapon;

import com.google.common.collect.Multimap;
import net.minecraft.block.BlockState;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SwordItem;
import net.minecraft.item.ToolMaterial;
import net.spell_engine.api.item.ConfigurableAttributes;

public class SpellWeaponItem extends SwordItem implements ConfigurableAttributes {
    private Multimap<EntityAttribute, EntityAttributeModifier> attributes;

    public SpellWeaponItem(ToolMaterial material, Settings settings) {
        this(material, 1,2.4F, settings);
    }

    public SpellWeaponItem(ToolMaterial material, int attackDamage, float attackSpeed, Settings settings) {
        super(material, attackDamage,attackSpeed, settings);
    }

    /**
     * Erase special capabilities of swords (super class)
     * So instances act as generic weapons
     */

    @Override
    public float getMiningSpeedMultiplier(ItemStack stack, BlockState state) {
        return 1.0f;
    }

    @Override
    public boolean isSuitableFor(BlockState state) {
        return false;
    }

    /**
     * ConfigurableAttributes
     */

    public void setAttributes(Multimap<EntityAttribute, EntityAttributeModifier> attributes) {
        this.attributes = attributes;
    }

    @Override
    public Multimap<EntityAttribute, EntityAttributeModifier> getAttributeModifiers(EquipmentSlot slot) {
        if (attributes == null) {
            return super.getAttributeModifiers(slot);
        }
        return slot == EquipmentSlot.MAINHAND ? attributes : super.getAttributeModifiers(slot);
    }
}
