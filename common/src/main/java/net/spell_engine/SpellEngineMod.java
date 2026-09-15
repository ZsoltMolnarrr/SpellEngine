package net.spell_engine;

import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.serializer.JanksonConfigSerializer;
import me.shedaniel.autoconfig.serializer.PartitioningSerializer;
import net.minecraft.advancement.criterion.Criteria;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.spell_engine.api.effect.RemoveOnHit;
import net.spell_engine.api.effect.SpellEngineEffects;
import net.spell_engine.api.enchantment.SpellEngineEnchantments;
import net.spell_engine.api.entity.SpellEngineAttributes;
import net.spell_engine.api.effect.StatusEffectClassification;
import net.spell_engine.api.item.set.EquipmentSetFeature;
import net.spell_engine.api.spell.ExternalSpellSchools;
import net.spell_engine.api.spell.weakness.SpellSchoolWeakness;
import net.spell_engine.api.spell.event.SpellEvents;
import net.spell_engine.api.spell.registry.SpellRegistry;
import net.spell_engine.compat.CompatFeatures;
import net.spell_engine.config.FallbackConfig;
import net.spell_engine.config.ServerConfig;
import net.spell_engine.config.ServerConfigWrapper;
import net.spell_engine.config.WeaknessConfig;
import net.tiny_config.ConfigManager;
import net.spell_engine.entity.SpellCloud;
import net.spell_engine.entity.SpellModelEffect;
import net.spell_engine.entity.SpellProjectile;
import net.spell_engine.fx.SpellEngineParticles;
import net.spell_engine.fx.SpellEngineSounds;
import net.spell_engine.misc.SpellEngineCommands;
import net.spell_engine.internals.SpellTriggers;
import net.spell_engine.internals.container.SpellAssignments;
import net.spell_engine.internals.container.SpellContainerSource;
import net.spell_engine.misc.criteria.EnchantmentSpecificCriteria;
import net.spell_engine.misc.criteria.SpellCastCriteria;
import net.spell_engine.internals.delivery.SpellStashHelper;
import net.spell_engine.network.ServerNetwork;
import net.spell_engine.rpg_series.RPGSeriesCore;
import net.spell_engine.spellbinding.*;
import net.spell_engine.spellbinding.spellchoice.SpellChoiceFeature;
import net.spell_engine.spellbinding.spellchoice.SpellChoiceScreenHandler;
import net.spell_engine.utils.StatusEffectUtil;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class SpellEngineMod {
    public static final String ID = "spell_engine";
    public static String modName() {
        return I18n.translate("spell_engine.mod_name");
    }

    public static ServerConfig config;

    public static ConfigManager<WeaknessConfig> weaknessConfig = new ConfigManager<>
            ("elemental_weaknesses", SpellSchoolWeakness.createDefault())
            .builder()
            .setDirectory(ID)
            .sanitize(true)
            .validate(WeaknessConfig::isValid)
            .build();

    public static ConfigManager<FallbackConfig> fallbackConfig = new ConfigManager<>
            ("weapon_fallback", FallbackConfig.defaults())
            .builder()
            .setDirectory(ID)
            .sanitize(true)
            .validate(FallbackConfig::isValid)
            .build();

    // Base-attribute config for summoned entities is no longer centralized here: each content mod owns
    // its own source (a config file it versions independently, or inline constants) and injects it into
    // SummonedEntities.registerAttributes as a Function<Identifier, SummonedEntityConfig.Entry>.

    public static void init() {
        AutoConfig.register(ServerConfigWrapper.class, PartitioningSerializer.wrap(JanksonConfigSerializer::new));
        config = AutoConfig.getConfigHolder(ServerConfigWrapper.class).getConfig().server;
        weaknessConfig.refresh();
        fallbackConfig.refresh();

        Platform.util().registerSyncedDataRegistry(SpellRegistry.KEY, SpellRegistry.CODEC, SpellRegistry.CODEC);

        SpellAssignments.init();

        SpellEvents.SPELL_CAST.register(args -> {
            SpellCastCriteria.INSTANCE.trigger((ServerPlayerEntity) args.caster(), args.spell());
        });

        ExternalSpellSchools.init();
        RPGSeriesCore.init();
        SpellStashHelper.init();
        SpellTriggers.init();
        SpellContainerSource.init();
        StatusEffectClassification.init();
        EquipmentSetFeature.init();
        CompatFeatures.initialize();

        SpellEngineCommands.register();

        PlatformEvents.onIncomingDamage(SpellEngineMod::onIncomingDamage);
        // Re-sync spell cooldowns and containers when a player joins or changes dimension.
        PlatformEvents.onPlayerJoin(ServerNetwork::onPlayerConnectOrChangeWorld);
        PlatformEvents.onPlayerChangedWorld(ServerNetwork::onPlayerConnectOrChangeWorld);
    }

    /// Damage-incoming hook (side effect only, never denies damage): strips RemoveOnHit status
    /// effects from the victim. Wired to `ServerLivingEntityEvents.ALLOW_DAMAGE` on Fabric and to
    /// `LivingIncomingDamageEvent` on NeoForge.
    public static void onIncomingDamage(LivingEntity entity, DamageSource source, float amount) {
        var attacker = source.getAttacker();
        if (amount > 0 && attacker != null) {
            var effectChanges = new ArrayList<StatusEffectUtil.Diff>();
            for (var instance : entity.getStatusEffects()) {
                var effect = instance.getEffectType();
                var remove = RemoveOnHit.removeCount(entity.getWorld(), effect, source);
                if (remove > 0) {
                    effectChanges.add(new StatusEffectUtil.Diff(instance, instance.getAmplifier() - remove));
                } else if (remove < 0) {
                    effectChanges.add(new StatusEffectUtil.Diff(instance, -1));
                }
            }
            StatusEffectUtil.applyChanges(entity, effectChanges);
        }
    }

    // MARK: Registration seam (idempotent; see class-level notes on loader wiring)
    //
    // Fabric (`FabricMod.onInitialize`): call every `register*()` below directly, in this order:
    //   registerAttributes() [normally already done by the fabric-only `EntityAttributes` <clinit> mixin],
    //   registerEntityTypes(), registerSounds(), registerParticles(), registerStatusEffects(),
    //   SpellEngineItems.register(), registerCriteria(), registerSpellBinding(), registerEnchantments(), then init().
    // Forge 47 (`ForgeMod`): one mod-bus `RegisterEvent` listener switching on `event.getRegistryKey()`:
    //   ATTRIBUTE → registerAttributes(); ENTITY_TYPE → registerEntityTypes(); SOUND_EVENT → registerSounds();
    //   PARTICLE_TYPE → registerParticles(); STATUS_EFFECT → registerStatusEffects(); ENCHANTMENT → registerEnchantments();
    //   BLOCK → registerSpellBindingBlock(); BLOCK_ENTITY_TYPE → registerSpellBindingBlockEntity();
    //   SCREEN_HANDLER → registerScreenHandlers(); LOOT_FUNCTION_TYPE → registerLootFunctionTypes(); ITEM → SpellEngineItems.register();
    //   plus `EntityAttributeModificationEvent` → `attributesToAttach()` on every living type; `registerCriteria()` from the
    //   mod constructor (vanilla `Criteria` is a plain static map on 1.20.1, not a Forge registry).

    /// Registers the Spell Engine entity attributes (`spell_engine:healing_taken`, `damage_taken`, `evasion_chance`).
    public static void registerAttributes() {
        SpellEngineAttributes.register();
    }

    /// Raw attributes every living entity's default container must receive
    /// (Fabric: `createLivingAttributes` RETURN mixin; Forge: `EntityAttributeModificationEvent`).
    public static List<EntityAttribute> attributesToAttach() {
        return SpellEngineAttributes.attributesToAttach();
    }

    public static void registerStatusEffects() {
        SpellEngineEffects.register();
    }

    public static void registerParticles() {
        SpellEngineParticles.register();
    }

    public static void registerSounds() {
        SpellEngineSounds.register();
    }

    /// `spell_engine:spell_infinity` (Java enchantment on 1.20.1).
    public static void registerEnchantments() {
        SpellEngineEnchantments.register();
    }

    /// Fabric convenience: the spell-binding block, its block entity, the screen handlers and the loot function
    /// type in one go. Forge must call the per-registry functions below from their own `RegisterEvent` windows
    /// (registering into any other registry from a window throws "Can not register to a locked registry").
    public static void registerSpellBinding() {
        registerSpellBindingBlock();
        registerSpellBindingBlockEntity();
        registerScreenHandlers();
        registerLootFunctionTypes();
    }

    public static void registerSpellBindingBlock() {
        if (Registries.BLOCK.containsId(SpellBinding.ID)) { return; }
        Registry.register(Registries.BLOCK, SpellBinding.ID, SpellBindingBlock.INSTANCE);
    }

    public static void registerSpellBindingBlockEntity() {
        if (Registries.BLOCK_ENTITY_TYPE.containsId(SpellBinding.ID)) { return; }
        Registry.register(Registries.BLOCK_ENTITY_TYPE, SpellBinding.ID, SpellBindingBlockEntity.ENTITY_TYPE);
    }

    public static void registerScreenHandlers() {
        if (!Registries.SCREEN_HANDLER.containsId(SpellBinding.ID)) {
            Registry.register(Registries.SCREEN_HANDLER, SpellBinding.ID, SpellBindingScreenHandler.HANDLER_TYPE);
        }
        if (!Registries.SCREEN_HANDLER.containsId(SpellChoiceFeature.ID)) {
            Registry.register(Registries.SCREEN_HANDLER, SpellChoiceFeature.ID, SpellChoiceScreenHandler.HANDLER_TYPE);
        }
    }

    public static void registerLootFunctionTypes() {
        if (Registries.LOOT_FUNCTION_TYPE.containsId(SpellBindRandomlyLootFunction.ID)) { return; }
        Registry.register(Registries.LOOT_FUNCTION_TYPE, SpellBindRandomlyLootFunction.ID, SpellBindRandomlyLootFunction.TYPE);
    }

    public static void registerEntityTypes() {
        entityTypesToRegister().forEach((id, type) -> Registry.register(Registries.ENTITY_TYPE, id, type));
    }

    /// Builds Spell Engine's entity types, assigns the `ENTITY_TYPE` static fields, and returns them keyed by
    /// the id they register under. Creation only — nothing is written here, so a loader that registers entity
    /// types itself (Forge) iterates this instead.
    ///
    /// **Must run inside the `ENTITY_TYPE` registration window**: `EntityType.Builder#build` constructs an
    /// intrusive registry holder, which throws `Registry is already frozen` outside it.
    public static Map<Identifier, EntityType<?>> entityTypesToRegister() {
        if (SpellProjectile.ENTITY_TYPE != null) { return Map.of(); }
        // Vanilla EntityType.Builder (loader-neutral) replaces FabricEntityTypeBuilder.
        // Note: vanilla `setDimensions(w, h)` produces "changing" dimensions; the former `fixed(...)`
        // is a no-op difference for these never-scaled entities.
        SpellProjectile.ENTITY_TYPE = EntityType.Builder.<SpellProjectile>create(SpellProjectile::new, SpawnGroup.MISC)
                .setDimensions(0.25F, 0.25F) // dimensions in Minecraft units of the render
                .makeFireImmune()
                .maxTrackingRange(128)
                .trackingTickInterval(2)
                .build("spell_projectile");
        SpellCloud.ENTITY_TYPE = EntityType.Builder.<SpellCloud>create(SpellCloud::new, SpawnGroup.MISC)
                .setDimensions(6F, 0.5F) // dimensions in Minecraft units of the render
                .makeFireImmune()
                .maxTrackingRange(128)
                .trackingTickInterval(20)
                .build("spell_area_effect");
        SpellModelEffect.ENTITY_TYPE = EntityType.Builder.<SpellModelEffect>create(SpellModelEffect::new, SpawnGroup.MISC)
                .setDimensions(0.5F, 0.5F)
                .makeFireImmune()
                .maxTrackingRange(128)
                .trackingTickInterval(20)
                .build("spell_model_effect");

        var types = new LinkedHashMap<Identifier, EntityType<?>>();
        types.put(new Identifier(SpellEngineMod.ID, "spell_projectile"), SpellProjectile.ENTITY_TYPE);
        types.put(new Identifier(SpellEngineMod.ID, "spell_area_effect"), SpellCloud.ENTITY_TYPE);
        types.put(new Identifier(SpellEngineMod.ID, "spell_model_effect"), SpellModelEffect.ENTITY_TYPE);
        return types;
    }

    private static boolean criteriaRegistered = false;

    /// 1.20.1: `Criteria.register(Criterion)` keys by `Criterion#getId()`; not a Forge registry, so it is
    /// safe to call from the mod constructor / Fabric init. Idempotent (vanilla throws on duplicate ids).
    public static void registerCriteria() {
        if (criteriaRegistered) { return; }
        criteriaRegistered = true;
        Criteria.register(EnchantmentSpecificCriteria.INSTANCE);
        Criteria.register(SpellCastCriteria.INSTANCE);

        Criteria.register(SpellBindingCriteria.INSTANCE);
        Criteria.register(SpellBookCreationCriteria.INSTANCE);
    }
}