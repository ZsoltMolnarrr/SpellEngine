package net.spell_engine.fabric;

import dev.emi.trinkets.api.TrinketsApi;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.fabricmc.fabric.api.util.TriState;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import net.spell_engine.SpellEngineMod;
import net.spell_engine.api.effect.RemoveOnHit;
import net.spell_engine.api.item.trinket.SpellBookItem;
import net.spell_engine.entity.SpellProjectile;
import net.spell_engine.utils.SoundHelper;

public class FabricMod implements ModInitializer {
    static {
        SpellEngineMod.SPELL_PROJECTILE = Registry.register(
                Registries.ENTITY_TYPE,
                new Identifier(SpellEngineMod.ID, "spell_projectile"),
                FabricEntityTypeBuilder.<SpellProjectile>create(SpawnGroup.MISC, SpellProjectile::new)
                        .dimensions(EntityDimensions.fixed(0.25F, 0.25F)) // dimensions in Minecraft units of the render
                        .trackRangeBlocks(128)
                        .trackedUpdateRate(2)
                        .build()
        );
    }

    @Override
    public void onInitialize() {
        SpellEngineMod.init();
        SpellEngineMod.registerEnchantments();
        SpellEngineMod.registerSpellBinding();
        SoundHelper.registerSounds();

        TrinketsApi.registerTrinketPredicate(new Identifier(SpellEngineMod.ID, "spell_book"), (itemStack, slotReference, livingEntity) -> {
            if (itemStack.getItem() instanceof SpellBookItem) {
                return TriState.TRUE;
            }
            return TriState.DEFAULT;
        });

        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            var attacker = source.getAttacker();
            if (amount > 0 && attacker != null) {
                for(var instance: entity.getStatusEffects()) {
                    var effect = instance.getEffectType();
                    if (RemoveOnHit.shouldRemoveOnDirectHit(effect)) {
                        entity.removeStatusEffect(effect);
                        break;
                    }
                }
            }
            return true;
        });
    }
}