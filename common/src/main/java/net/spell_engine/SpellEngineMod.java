package net.spell_engine;

import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.serializer.JanksonConfigSerializer;
import me.shedaniel.autoconfig.serializer.PartitioningSerializer;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.spell_engine.api.effect.EntityTints;
import net.spell_engine.api.effect.RemoveOnHit;
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
import net.spell_engine.misc.SpellEngineCommands;
import net.spell_engine.internals.SpellEngineAttachments;
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

public class SpellEngineMod {
    public static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
    public static final String ID = "spell_engine";
    public static String modName() {
        return I18n.get("spell_engine.mod_name");
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

        Platform.util().registerSyncedDataRegistry(SpellRegistry.KEY, SpellRegistry.LOCAL_CODEC, SpellRegistry.NETWORK_CODEC_V2);
        SpellEngineAttachments.init();

        SpellAssignments.init();

        SpellEvents.SPELL_CAST.register(args -> {
            SpellCastCriteria.INSTANCE.trigger((ServerPlayer) args.caster(), args.spell());
        });

        if (Platform.util().isDevelopmentEnvironment()) {
            // Test configuration for some APIs
            EntityTints.register(MobEffects.POISON.value(), 0x8888FF88);
        }

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
        var attacker = source.getEntity();
        if (amount > 0 && attacker != null) {
            var effectChanges = new ArrayList<StatusEffectUtil.Diff>();
            for (var instance : entity.getActiveEffects()) {
                var effect = instance.getEffect();
                var remove = RemoveOnHit.removeCount(entity.level(), effect.value(), source);
                if (remove > 0) {
                    effectChanges.add(new StatusEffectUtil.Diff(instance, instance.getAmplifier() - remove));
                } else if (remove < 0) {
                    effectChanges.add(new StatusEffectUtil.Diff(instance, -1));
                }
            }
            StatusEffectUtil.applyChanges(entity, effectChanges);
        }
    }

    public static void registerSpellBinding() {
        Registry.register(BuiltInRegistries.BLOCK, SpellBinding.ID, SpellBindingBlock.INSTANCE);
        Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, SpellBinding.ID, SpellBindingBlockEntity.ENTITY_TYPE);
        Registry.register(BuiltInRegistries.MENU, SpellBinding.ID, SpellBindingScreenHandler.HANDLER_TYPE);
        Registry.register(BuiltInRegistries.LOOT_FUNCTION_TYPE, SpellBindRandomlyLootFunction.ID, SpellBindRandomlyLootFunction.CODEC);
        Registry.register(BuiltInRegistries.MENU, SpellChoiceFeature.ID, SpellChoiceScreenHandler.HANDLER_TYPE);
    }

    public static void registerEntityTypes() {
        // Vanilla EntityType.Builder (loader-neutral) replaces FabricEntityTypeBuilder.
        // Note: vanilla `dimensions(w, h)` produces "changing" dimensions; the former `fixed(...)`
        // is a no-op difference for these never-scaled entities.
        SpellProjectile.ENTITY_TYPE = Registry.register(
                BuiltInRegistries.ENTITY_TYPE,
                Identifier.fromNamespaceAndPath(SpellEngineMod.ID, "spell_projectile"),
                EntityType.Builder.<SpellProjectile>of(SpellProjectile::new, MobCategory.MISC)
                        .sized(0.25F, 0.25F) // dimensions in Minecraft units of the render
                        .fireImmune()
                        .clientTrackingRange(128)
                        .updateInterval(2)
                        .build(ResourceKey.create(Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath(ID, "spell_projectile")))
        );
        SpellCloud.ENTITY_TYPE = Registry.register(
                BuiltInRegistries.ENTITY_TYPE,
                Identifier.fromNamespaceAndPath(SpellEngineMod.ID, "spell_area_effect"),
                EntityType.Builder.<SpellCloud>of(SpellCloud::new, MobCategory.MISC)
                        .sized(6F, 0.5F) // dimensions in Minecraft units of the render
                        .fireImmune()
                        .clientTrackingRange(128)
                        .updateInterval(20)
                        .build(ResourceKey.create(Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath(ID, "spell_area_effect")))
        );
        SpellModelEffect.ENTITY_TYPE = Registry.register(
                BuiltInRegistries.ENTITY_TYPE,
                Identifier.fromNamespaceAndPath(SpellEngineMod.ID, "spell_model_effect"),
                EntityType.Builder.<SpellModelEffect>of(SpellModelEffect::new, MobCategory.MISC)
                        .sized(0.5F, 0.5F)
                        .fireImmune()
                        .clientTrackingRange(128)
                        .updateInterval(20)
                        .build(ResourceKey.create(Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath(ID, "spell_model_effect")))
        );
    }

    public static void registerCriteria() {
        CriteriaTriggers.register(EnchantmentSpecificCriteria.ID.toString(), EnchantmentSpecificCriteria.INSTANCE);
        CriteriaTriggers.register(SpellCastCriteria.ID.toString(), SpellCastCriteria.INSTANCE);

        CriteriaTriggers.register(SpellBindingCriteria.ID.toString(), SpellBindingCriteria.INSTANCE);
        CriteriaTriggers.register(SpellBookCreationCriteria.ID.toString(), SpellBookCreationCriteria.INSTANCE);
    }
}