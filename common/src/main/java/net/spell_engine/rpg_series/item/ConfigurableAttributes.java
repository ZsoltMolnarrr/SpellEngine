package net.spell_engine.rpg_series.item;

import net.spell_engine.api.item.ItemAttributeModifiers;

/// Items whose attribute modifiers are assigned after construction (from config), the 1.20.1 replacement for
/// `Item.Settings#attributeModifiers`. Implementors override `Item#getAttributeModifiers(EquipmentSlot)` to serve
/// the assigned value; items that do not implement this still get the value through
/// `AttributeModifierUtil.setItemModifiers` + `ItemStackAttributeModifiersMixin`.
public interface ConfigurableAttributes {
    void setAttributes(ItemAttributeModifiers attributeModifiers);
}
