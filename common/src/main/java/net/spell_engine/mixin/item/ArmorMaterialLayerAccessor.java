package net.spell_engine.mixin.item;

import net.minecraft.item.ArmorMaterial;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ArmorMaterial.Layer.class)
public interface ArmorMaterialLayerAccessor {
    @Accessor("id")
    Identifier getId();
}
