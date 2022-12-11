package net.spell_engine.fabric;

import net.spell_engine.SpellEngineMod;
import net.spell_engine.entity.SpellProjectile;
import net.fabricmc.api.ModInitializer;
import net.spell_engine.utils.SoundHelper;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;

import static net.minecraft.util.registry.Registry.ENTITY_TYPE;

public class FabricMod implements ModInitializer {
    static {
        SpellEngineMod.SPELL_PROJECTILE = Registry.register(
                ENTITY_TYPE,
                new Identifier(SpellEngineMod.ID, "spell_projectile"),
                FabricEntityTypeBuilder.<SpellProjectile>create(SpawnGroup.MISC, SpellProjectile::new)
                        .dimensions(EntityDimensions.fixed(0.25F, 0.25F)) // dimensions in Minecraft units of the projectile
                        .trackRangeBlocks(128)
                        .trackedUpdateRate(2)
                        .build()
        );
    }

    @Override
    public void onInitialize() {
        SpellEngineMod.init();
        SpellEngineMod.registerEnchantments();
        SpellEngineMod.registerItems();
        SpellEngineMod.registerRuneCrafting();
        SoundHelper.registerSounds();
    }
}