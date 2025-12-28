package net.spell_engine.neoforge;

import net.minecraft.registry.RegistryKeys;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

import net.neoforged.neoforge.registries.RegisterEvent;
import net.spell_engine.SpellEngineMod;
import net.spell_engine.api.effect.SpellEngineEffects;
import net.spell_engine.fx.SpellEngineParticles;
import net.spell_engine.fx.SpellEngineSounds;
import net.spell_engine.item.SpellEngineItems;
import net.spell_engine.neoforge.compat.NeoForgeCompatFeatures;

@Mod(SpellEngineMod.ID)
public final class NeoForgeMod {
    public NeoForgeMod(IEventBus modBus) {
        // Run our common setup.
        SpellEngineMod.init();
        NeoForgeCompatFeatures.init();
        modBus.addListener(RegisterEvent.class, NeoForgeMod::register);
    }

    public static void register(RegisterEvent event) {
        event.register(RegistryKeys.ENTITY_TYPE, reg -> {
            SpellEngineMod.registerEntityTypes();
        });
        event.register(RegistryKeys.PARTICLE_TYPE, reg -> {
            SpellEngineParticles.register();
        });
        event.register(RegistryKeys.STATUS_EFFECT, reg -> {
            SpellEngineEffects.register();
        });
        event.register(RegistryKeys.ITEM, reg -> {
            SpellEngineItems.register();
        });
        event.register(RegistryKeys.SOUND_EVENT, reg -> {
            SpellEngineSounds.register();
        });
        event.register(RegistryKeys.BLOCK, reg -> {
            // Warning this registers not only blocks!
            // May cause issues, cba for now :)
            SpellEngineMod.registerSpellBinding();
        });
        event.register(RegistryKeys.CRITERION, reg -> {
            SpellEngineMod.registerCriteria();
        });
    }
}
