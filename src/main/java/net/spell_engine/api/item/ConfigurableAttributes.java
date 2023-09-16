package net.spell_engine.api.item;

import com.google.common.collect.Multimap;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeModifier;

public interface ConfigurableAttributes {
    void setAttributes(Multimap<EntityAttribute, EntityAttributeModifier> attributes);
}