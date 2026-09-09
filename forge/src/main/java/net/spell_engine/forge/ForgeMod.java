package net.spell_engine.forge;

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
import net.spell_engine.api.enchantment.SpellEngineEnchantments;
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
/// 2. `RegisterEvent` (mod bus, one call per registry, in Forge's known-registry order): the content
///    `common` exposes through its `…ToRegister()` methods, registered through this event's helper, each
///    inside its own registry's window — Forge locks every vanilla registry outside its `RegisterEvent`
///    window, so nothing may register from `<clinit>` or `init()`;
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

    /// One listener for every registry; `RegisterEvent#register(key, consumer)` only runs the consumer when
    /// the event is for that key, so each block below executes inside exactly its own registry's window.
    ///
    /// The loops are **duplicated here on purpose** rather than delegated to `common`'s `registerX()`
    /// methods: a plain `Registry.register` is not usable on this loader, because Forge only clears the
    /// vanilla registry's own lock from 47.4.0 onwards — on 47.0–47.3 and NeoForge 1.20.1 it throws
    /// "Can not register to a locked registry" even inside the correct `RegisterEvent` window, and our
    /// `mods.toml` declares `loaderVersion = "[47,)"`. The helper this event hands out is the API every
    /// build of `[47,)` sanctions, so Forge iterates the same content `common` exposes through its
    /// `…ToRegister()` methods and registers it itself. `common` keeps its vanilla-shaped `registerX()` for
    /// Fabric, which is untouched.
    ///
    /// Two rules govern the grouping. `event.register` has no `else` and no throw, so content filed under a
    /// key that does not match the event **vanishes silently** — hence `creative_mode_tab` (event 65) gets
    /// its own block instead of riding along with `item` (event 7), as it used to. And `Item`, `Block` and
    /// `EntityType` take an intrusive registry holder in their *constructor*, so each `…ToRegister()` that
    /// builds one must stay inside its own registry's window.
    public static void register(RegisterEvent event) {
        // Replaces the Fabric-only EntityAttributesMixin (<clinit> TAIL on EntityAttributes).
        event.register(RegistryKeys.ATTRIBUTE, helper -> {
            SpellEngineAttributes.attributesToRegister().forEach(helper::register);
            // The helper returns void, so the RegistryEntry fields gameplay reads (EvasionLogic) are
            // filled in afterwards from the registry.
            SpellEngineAttributes.linkEntries();
        });
        event.register(RegistryKeys.SOUND_EVENT, helper -> {
            SpellEngineSounds.soundsToRegister().forEach(helper::register);
            // Armor and shield materials hold `Entry#entry()`; only the register-reference path fills it in.
            SpellEngineSounds.linkEntries();
        });
        event.register(RegistryKeys.STATUS_EFFECT, helper -> {
            SpellEngineEffects.effectsToRegister().forEach(helper::register);
            SpellEngineEffects.linkEntries();
        });
        event.register(RegistryKeys.BLOCK, helper ->
                helper.register(SpellBinding.ID, SpellBindingBlock.INSTANCE));
        event.register(RegistryKeys.ENTITY_TYPE, helper ->
                SpellEngineMod.entityTypesToRegister().forEach(helper::register));
        event.register(RegistryKeys.ITEM, helper ->
                // Triggers the slot-mod item factories (Curios) via awakeSlotModCompat().
                SpellEngineItems.itemsToRegister().forEach(helper::register));
        // `creative_mode_tab` is event 65, 58 events after `item` — its own window, or the write is dropped.
        event.register(RegistryKeys.ITEM_GROUP, helper ->
                helper.register(SpellEngineItems.Group.ID, SpellEngineItems.Group.SPELLS));
        event.register(RegistryKeys.PARTICLE_TYPE, helper ->
                SpellEngineParticles.particlesToRegister().forEach(helper::register));
        event.register(RegistryKeys.BLOCK_ENTITY_TYPE, helper ->
                helper.register(SpellBinding.ID, SpellBindingBlockEntity.ENTITY_TYPE));
        event.register(RegistryKeys.SCREEN_HANDLER, helper -> {
            helper.register(SpellBinding.ID, SpellBindingScreenHandler.HANDLER_TYPE);
            helper.register(SpellChoiceFeature.ID, SpellChoiceScreenHandler.HANDLER_TYPE);
        });
        event.register(RegistryKeys.LOOT_FUNCTION_TYPE, helper ->
                helper.register(SpellBindRandomlyLootFunction.ID, SpellBindRandomlyLootFunction.TYPE));
        event.register(RegistryKeys.ENCHANTMENT, helper ->
                SpellEngineEnchantments.enchantmentsToRegister().forEach(helper::register));
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
