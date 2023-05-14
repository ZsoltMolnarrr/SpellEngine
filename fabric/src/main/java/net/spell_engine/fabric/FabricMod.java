package net.spell_engine.fabric;

import com.mojang.datafixers.util.Function3;
import dev.emi.trinkets.api.SlotReference;
import dev.emi.trinkets.api.TrinketsApi;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.util.TriState;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.spell_engine.SpellEngineMod;
import net.spell_engine.api.effect.RemoveOnHit;
import net.spell_engine.api.item.trinket.SpellBookItem;
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
        SpellEngineMod.registerItems();
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