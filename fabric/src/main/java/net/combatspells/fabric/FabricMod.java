package net.combatspells.fabric;

import net.combatspells.CombatSpells;
import net.combatspells.entity.SpellProjectile;
import net.fabricmc.api.ModInitializer;
import net.combatspells.utils.SoundHelper;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;

import static net.minecraft.util.registry.Registry.ENTITY_TYPE;

public class FabricMod implements ModInitializer {
    static {
        CombatSpells.SPELL_PROJECTILE = Registry.register(
                ENTITY_TYPE,
                new Identifier(CombatSpells.MOD_ID, "spell_projectile"),
                FabricEntityTypeBuilder.<SpellProjectile>create(SpawnGroup.MISC, SpellProjectile::new)
                        .dimensions(EntityDimensions.fixed(0.25F, 0.25F)) // dimensions in Minecraft units of the projectile
                        .trackRangeBlocks(128)
                        .trackedUpdateRate(5) // necessary for all thrown projectiles (as it prevents it from breaking, lol)
                        .build()
        );
    }

    @Override
    public void onInitialize() {
        CombatSpells.init();
        SoundHelper.registerSounds();
    }
}