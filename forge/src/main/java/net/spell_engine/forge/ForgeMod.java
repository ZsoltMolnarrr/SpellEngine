package net.spell_engine.forge;

import net.minecraft.registry.RegistryKeys;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.registries.DataPackRegistryEvent;
import net.minecraftforge.registries.RegisterEvent;
import net.spell_engine.SpellEngineMod;
import net.spell_engine.api.effect.SpellEngineEffects;
import net.spell_engine.forge.client.ForgeClientMod;
import net.spell_engine.forge.compat.ForgeCompatFeatures;
import net.spell_engine.fx.SpellEngineParticles;
import net.spell_engine.fx.SpellEngineSounds;
import net.spell_engine.item.SpellEngineItems;

/// Forge 47 entrypoint (skeleton for the 1.20.1 port). Mirrors the NeoForge entrypoint shape:
/// common init in the constructor, registration through `RegisterEvent`, summoned-entity attributes
/// through `EntityAttributeCreationEvent`, synced datapack registries through
/// `DataPackRegistryEvent.NewRegistry`, creative tab entries through `BuildCreativeModeTabContentsEvent`.
/// Client wiring is only touched behind a `Dist.CLIENT` check (see {@link ForgeClientMod}).
@Mod(SpellEngineMod.ID)
public final class ForgeMod {
    // FMLJavaModLoadingContext.get() is flagged for removal by late 47.x builds, but the
    // constructor-injected replacement doesn't exist on early 47.x; get() works on all of [47,).
    @SuppressWarnings("removal")
    public ForgeMod() {
        // Run our common setup.
        SpellEngineMod.init();
        ForgeCompatFeatures.init();
        // TODO 1.20.1: criteria are not a registry on 1.20.1 (`Criteria.register(...)` is a static map, no
        // RegisterEvent phase) — registered straight from the constructor; cluster c7 owner to confirm.
        SpellEngineMod.registerCriteria();

        var modBus = FMLJavaModLoadingContext.get().getModEventBus();
        // Explicit event classes: Forge 47's plain addListener(Consumer) infers the event type from the
        // lambda via TypeTools, which is fragile; the 4-arg overload takes it directly.
        modBus.addListener(EventPriority.NORMAL, false, RegisterEvent.class, ForgeMod::register);
        // Summoned-entity default attributes are buffered by content mods during entity registration
        // and supplied here, the only point Forge accepts them. Works for every mod's summons since
        // the buffer is static and this event accepts any entity type.
        modBus.addListener(EventPriority.NORMAL, false, EntityAttributeCreationEvent.class, SummonedEntityAttributeRegistrar::onCreateAttributes);
        // Synced datapack registries buffered during common init (replaces DynamicRegistries.registerSynced).
        modBus.addListener(EventPriority.NORMAL, false, DataPackRegistryEvent.NewRegistry.class, SyncedDataRegistrar::onNewRegistry);
        // Creative-tab entries buffered during common init (replaces Fabric's ItemGroupEvents).
        modBus.addListener(EventPriority.NORMAL, false, BuildCreativeModeTabContentsEvent.class, ForgeMod::onBuildCreativeTabContents);

        if (FMLEnvironment.dist == Dist.CLIENT) {
            ForgeClientMod.register(modBus);
        }
    }

    private static void onBuildCreativeTabContents(BuildCreativeModeTabContentsEvent event) {
        PlatformEventsImpl.dispatchItemGroup(event.getTabKey(), event, event.getParameters());
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
    }
}
