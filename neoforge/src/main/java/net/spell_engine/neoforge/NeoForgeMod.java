package net.spell_engine.neoforge;

import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DataPackRegistryEvent;
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
        PlatformEventsImpl.registerShieldBlockBridge();
        NeoForgeCompatFeatures.init();
        modBus.addListener(RegisterEvent.class, NeoForgeMod::register);
        // Summoned-entity default attributes are buffered by content mods during entity registration
        // and supplied here, the only point NeoForge accepts them. Works for every mod's summons since
        // the buffer is static and this event accepts any entity type.
        modBus.addListener(EntityAttributeCreationEvent.class, SummonedEntityAttributeRegistrar::onCreateAttributes);
        // Synced datapack registries buffered during common init (replaces DynamicRegistries.registerSynced).
        modBus.addListener(DataPackRegistryEvent.NewRegistry.class, SyncedDataRegistrar::onNewRegistry);
        // Creative-tab entries buffered during common init (replaces Fabric's ItemGroupEvents).
        modBus.addListener(BuildCreativeModeTabContentsEvent.class, NeoForgeMod::onBuildCreativeTabContents);
    }

    private static void onBuildCreativeTabContents(BuildCreativeModeTabContentsEvent event) {
        PlatformEventsImpl.dispatchItemGroup(event.getTabKey(), event, event.getParameters());
    }

    public static void register(RegisterEvent event) {
        // Synced entity attachments created during common init (see SpellEngineAttachments).
        NeoForgeSyncedEntityData.onRegister(event);
        event.register(Registries.ENTITY_TYPE, reg -> {
            SpellEngineMod.registerEntityTypes();
        });
        event.register(Registries.PARTICLE_TYPE, reg -> {
            SpellEngineParticles.register();
        });
        event.register(Registries.MOB_EFFECT, reg -> {
            SpellEngineEffects.register();
        });
        event.register(Registries.ITEM, reg -> {
            SpellEngineItems.register();
        });
        event.register(Registries.SOUND_EVENT, reg -> {
            SpellEngineSounds.register();
        });
        event.register(Registries.BLOCK, reg -> {
            // Warning this registers not only blocks!
            // May cause issues, cba for now :)
            SpellEngineMod.registerSpellBinding();
        });
        event.register(Registries.TRIGGER_TYPE, reg -> {
            SpellEngineMod.registerCriteria();
        });
    }
}
