package net.spell_engine.api.item.weapon;

import net.minecraft.item.Item;
import net.minecraft.item.ToolMaterial;

/// Since 1.21.2 sword behaviour lives in data components (applied through {@link Item.Settings}),
/// so this is a plain item; the material parameter is kept for factory-signature compatibility.
public class SpellWeaponItem extends Item {
    public SpellWeaponItem(ToolMaterial toolMaterial, Settings settings) {
        super(settings);
    }
}
