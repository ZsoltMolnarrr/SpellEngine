package net.spell_engine.rpg_series.item;

import net.minecraft.world.item.component.ItemAttributeModifiers;

/// Items whose `minecraft:attribute_modifiers` come from config, applied after construction.
///
/// Since 26.1 item components are bound to the registry holder at resource reload, so implementations must
/// publish the value through a delayed component step (see `Armor.CustomItem`), not by rebuilding a component
/// map. Call {@link #setAttributes} before the first reload (i.e. during registration); a later call only takes
/// effect on the next reload.
public interface ConfigurableAttributes {
    void setAttributes(ItemAttributeModifiers attributeModifiers);
}
