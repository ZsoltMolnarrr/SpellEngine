package net.spell_engine;

import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.serializer.JanksonConfigSerializer;
import me.shedaniel.autoconfig.serializer.PartitioningSerializer;
import net.minecraft.advancement.criterion.Criteria;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
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

        Platform.util().registerSyncedDataRegistry(SpellRegistry.KEY, SpellRegistry.LOCAL_CODEC, SpellRegistry.NETWORK_CODEC_V2);

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
                var remove = RemoveOnHit.removeCount(entity.getWorld(), effect.value(), source);
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
        Registry.register(Registries.BLOCK, SpellBinding.ID, SpellBindingBlock.INSTANCE);
        Registry.register(Registries.BLOCK_ENTITY_TYPE, SpellBinding.ID, SpellBindingBlockEntity.ENTITY_TYPE);
        Registry.register(Registries.SCREEN_HANDLER, SpellBinding.ID, SpellBindingScreenHandler.HANDLER_TYPE);
        Registry.register(Registries.LOOT_FUNCTION_TYPE, SpellBindRandomlyLootFunction.ID, SpellBindRandomlyLootFunction.TYPE);
        Registry.register(Registries.SCREEN_HANDLER, SpellChoiceFeature.ID, SpellChoiceScreenHandler.HANDLER_TYPE);
    }

    public static void registerEntityTypes() {
        // Vanilla EntityType.Builder (loader-neutral) replaces FabricEntityTypeBuilder.
        // Note: vanilla `dimensions(w, h)` produces "changing" dimensions; the former `fixed(...)`
        // is a no-op difference for these never-scaled entities.
        SpellProjectile.ENTITY_TYPE = Registry.register(
                Registries.ENTITY_TYPE,
                Identifier.of(SpellEngineMod.ID, "spell_projectile"),
                EntityType.Builder.<SpellProjectile>create(SpellProjectile::new, SpawnGroup.MISC)
                        .dimensions(0.25F, 0.25F) // dimensions in Minecraft units of the render
                        .makeFireImmune()
                        .maxTrackingRange(128)
                        .trackingTickInterval(2)
                        .build("spell_projectile")
        );
        SpellCloud.ENTITY_TYPE = Registry.register(
                Registries.ENTITY_TYPE,
                Identifier.of(SpellEngineMod.ID, "spell_area_effect"),
                EntityType.Builder.<SpellCloud>create(SpellCloud::new, SpawnGroup.MISC)
                        .dimensions(6F, 0.5F) // dimensions in Minecraft units of the render
                        .makeFireImmune()
                        .maxTrackingRange(128)
                        .trackingTickInterval(20)
                        .build("spell_area_effect")
        );
        SpellModelEffect.ENTITY_TYPE = Registry.register(
                Registries.ENTITY_TYPE,
                Identifier.of(SpellEngineMod.ID, "spell_model_effect"),
                EntityType.Builder.<SpellModelEffect>create(SpellModelEffect::new, SpawnGroup.MISC)
                        .dimensions(0.5F, 0.5F)
                        .makeFireImmune()
                        .maxTrackingRange(128)
                        .trackingTickInterval(20)
                        .build("spell_model_effect")
        );
    }

    public static void registerCriteria() {
        Criteria.register(EnchantmentSpecificCriteria.ID.toString(), EnchantmentSpecificCriteria.INSTANCE);
        Criteria.register(SpellCastCriteria.ID.toString(), SpellCastCriteria.INSTANCE);

        Criteria.register(SpellBindingCriteria.ID.toString(), SpellBindingCriteria.INSTANCE);
        Criteria.register(SpellBookCreationCriteria.ID.toString(), SpellBookCreationCriteria.INSTANCE);
    }
}