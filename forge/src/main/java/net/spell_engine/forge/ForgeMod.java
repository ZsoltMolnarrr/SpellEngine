package net.spell_engine.forge;

import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKeys;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.event.entity.EntityAttributeModificationEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.registries.DataPackRegistryEvent;
import net.minecraftforge.registries.RegisterEvent;
import net.spell_engine.SpellEngineMod;
import net.spell_engine.api.effect.SpellEngineEffects;
import net.spell_engine.api.entity.SpellEngineAttributes;
import net.spell_engine.compat.EnchantmentAllowBridge;
import net.spell_engine.forge.client.ForgeClientMod;
import net.spell_engine.forge.compat.ForgeCompatFeatures;
import net.spell_engine.forge.network.ForgeNetwork;
import net.spell_engine.fx.SpellEngineParticles;
import net.spell_engine.fx.SpellEngineSounds;
import net.spell_engine.item.SpellEngineItems;
import net.spell_engine.spellbinding.SpellBindRandomlyLootFunction;
import net.spell_engine.spellbinding.SpellBinding;
import net.spell_engine.spellbinding.SpellBindingBlock;
import net.spell_engine.spellbinding.SpellBindingBlockEntity;
import net.spell_engine.spellbinding.SpellBindingScreenHandler;
import net.spell_engine.spellbinding.spellchoice.SpellChoiceFeature;
import net.spell_engine.spellbinding.spellchoice.SpellChoiceScreenHandler;

/// Forge 47 entrypoint (1.20.1 port). Target is Forge 47 only — NeoForge 1.20.1 loads this jar unchanged, so
/// only APIs present at 47.1 (the NeoForge fork point) are used.
///
/// Wiring, in order:
/// 1. constructor: networking channel (c1), common init, compat init, criteria (a static map on 1.20.1, no
///    registry window), mod-bus listeners, client delegation (c4);
/// 2. `RegisterEvent` (mod bus, one call per registry, in Forge's known-registry order): the idempotent
///    common `registerX()` functions, each inside its own registry's window — Forge locks every vanilla
///    registry outside its `RegisterEvent` window, so nothing may register from `<clinit>` or `init()`;
/// 3. `EntityAttributeModificationEvent`: Spell Engine's living-entity attributes attached to every living
///    type (replaces the Fabric-only `LivingEntityAttributesMixin`);
/// 4. `EntityAttributeCreationEvent`: buffered summoned-entity attribute containers ({@link SummonedEntityAttributeRegistrar});
/// 5. `DataPackRegistryEvent.NewRegistry`: buffered synced datapack registries ({@link SyncedDataRegistrar}, spell registry);
/// 6. `FMLCommonSetupEvent`: post-registration work (enchant-permit bridge over the complete enchantment registry);
/// 7. `BuildCreativeModeTabContentsEvent`: buffered item-group entries ({@link PlatformEventsImpl#dispatchItemGroup}).
@Mod(SpellEngineMod.ID)
public final class ForgeMod {
    // FMLJavaModLoadingContext.get() is flagged for removal by late 47.x builds, but the
    // constructor-injected replacement doesn't exist on early 47.x; get() works on all of [47,).
    @SuppressWarnings("removal")
    public ForgeMod() {
        // c1: SimpleChannel + packet registration. Must precede common init, which may already send/queue
        // packets through Platform.util() (and registers the play-phase handlers that reference the channel).
        ForgeNetwork.register();

        // Common (loader-neutral) setup: configs, spell registry request, PlatformEvents subscriptions, compat.
        // Registers nothing into Forge-locked registries (that happens in RegisterEvent below).
        SpellEngineMod.init();
        ForgeCompatFeatures.init();
        // Criteria are a static map on 1.20.1 (`Criteria.register(...)`), not a registry: no RegisterEvent
        // window exists for them, so they are registered straight from the constructor, as on Fabric.
        SpellEngineMod.registerCriteria();

        var modBus = FMLJavaModLoadingContext.get().getModEventBus();
        // Explicit event classes: Forge 47's plain addListener(Consumer) infers the event type from the
        // lambda via TypeTools, which is fragile; the 4-arg overload takes it directly.
        modBus.addListener(EventPriority.NORMAL, false, RegisterEvent.class, ForgeMod::register);
        // Living-entity attributes (healing_taken, damage_taken, evasion_chance) on every living type.
        modBus.addListener(EventPriority.NORMAL, false, EntityAttributeModificationEvent.class, ForgeMod::attachLivingAttributes);
        // Summoned-entity default attributes are buffered by content mods during entity registration
        // and supplied here, the only point Forge accepts them. Works for every mod's summons since
        // the buffer is static and this event accepts any entity type.
        modBus.addListener(EventPriority.NORMAL, false, EntityAttributeCreationEvent.class, SummonedEntityAttributeRegistrar::onCreateAttributes);
        // Synced datapack registries buffered during common init (replaces DynamicRegistries.registerSynced).
        // INTEGRATOR: c1 kept SyncedDataRegistrar + this listener as the spell-registry slot; codec choice lives in common.
        modBus.addListener(EventPriority.NORMAL, false, DataPackRegistryEvent.NewRegistry.class, SyncedDataRegistrar::onNewRegistry);
        // Post-registration setup (all RegisterEvents done, registries frozen).
        modBus.addListener(EventPriority.NORMAL, false, FMLCommonSetupEvent.class, ForgeMod::onCommonSetup);
        // Creative-tab entries buffered during common init (replaces Fabric's ItemGroupEvents).
        modBus.addListener(EventPriority.NORMAL, false, BuildCreativeModeTabContentsEvent.class, ForgeMod::onBuildCreativeTabContents);

        if (FMLEnvironment.dist == Dist.CLIENT) {
            // c4: client setup, GUI overlays, key mappings, particle providers, entity renderers, models.
            ForgeClientMod.register(modBus);
        }
    }

    /// One listener for every registry; `RegisterEvent#register(key, consumer)` only runs the consumer when the
    /// event is for that key, so each block below executes inside its own registry's unlocked window.
    /// The called functions are the same idempotent `registerX()` entry points Fabric invokes from
    /// `FabricMod.onInitialize` / its `<clinit>`-TAIL mixins.
    public static void register(RegisterEvent event) {
        // INTEGRATOR: c5 — the attribute/effect/item/sound/particle/entity function names below are the
        // skeleton's; adjust here if c5 renames them.
        event.register(RegistryKeys.ATTRIBUTE, reg -> {
            // Replaces the Fabric-only EntityAttributesMixin (<clinit> TAIL on EntityAttributes).
            SpellEngineAttributes.register();
        });
        event.register(RegistryKeys.SOUND_EVENT, reg -> {
            SpellEngineSounds.register();
        });
        event.register(RegistryKeys.STATUS_EFFECT, reg -> {
            SpellEngineEffects.register();
        });
        event.register(RegistryKeys.BLOCK, reg -> {
            Registry.register(Registries.BLOCK, SpellBinding.ID, SpellBindingBlock.INSTANCE);
        });
        event.register(RegistryKeys.ENTITY_TYPE, reg -> {
            SpellEngineMod.registerEntityTypes();
        });
        event.register(RegistryKeys.ITEM, reg -> {
            // Also registers the `spell_engine:generic` item group: ITEM_GROUP is a vanilla-only registry
            // (not Forge-wrapped), unfrozen for the whole RegisterEvent phase, so registering it from the
            // ITEM window is fine. Triggers the slot-mod item factories (Curios) via awakeSlotModCompat().
            SpellEngineItems.register();
        });
        event.register(RegistryKeys.PARTICLE_TYPE, reg -> {
            SpellEngineParticles.register();
        });
        event.register(RegistryKeys.BLOCK_ENTITY_TYPE, reg -> {
            Registry.register(Registries.BLOCK_ENTITY_TYPE, SpellBinding.ID, SpellBindingBlockEntity.ENTITY_TYPE);
        });
        event.register(RegistryKeys.SCREEN_HANDLER, reg -> {
            Registry.register(Registries.SCREEN_HANDLER, SpellBinding.ID, SpellBindingScreenHandler.HANDLER_TYPE);
            Registry.register(Registries.SCREEN_HANDLER, SpellChoiceFeature.ID, SpellChoiceScreenHandler.HANDLER_TYPE);
        });
        event.register(RegistryKeys.LOOT_FUNCTION_TYPE, reg -> {
            Registry.register(Registries.LOOT_FUNCTION_TYPE, SpellBindRandomlyLootFunction.ID, SpellBindRandomlyLootFunction.TYPE);
        });
        // The five Registry.register calls above are `SpellEngineMod.registerSpellBinding()` split per registry:
        // that function registers block + block entity + screen handlers + loot function type in one go, which
        // Forge rejects ("Can not register to a locked registry") since only one registry is unlocked per event.
        // INTEGRATOR: c5 — if registerSpellBinding() gets split into per-registry functions, call those instead.
    }

    /// Replaces the Fabric-only `LivingEntityAttributesMixin` (`createLivingAttributes` RETURN): fired after
    /// registration, once every living entity type's default attributes are built; also covers third-party mobs.
    private static void attachLivingAttributes(EntityAttributeModificationEvent event) {
        for (var entityType : event.getTypes()) {
            for (var entry : SpellEngineAttributes.all) {
                if (!event.has(entityType, entry.attribute)) {
                    event.add(entityType, entry.attribute, entry.baseValue);
                }
            }
        }
    }

    private static void onCommonSetup(FMLCommonSetupEvent event) {
        // Enchant-applicability callbacks (PlatformEvents.onAllowEnchanting) are bridged into Spell Power's
        // EnchantmentRestriction per enchantment; the registry is complete only now.
        event.enqueueWork(EnchantmentAllowBridge::installAll);
    }

    private static void onBuildCreativeTabContents(BuildCreativeModeTabContentsEvent event) {
        PlatformEventsImpl.dispatchItemGroup(event.getTabKey(), event, event.getParameters());
    }
}
