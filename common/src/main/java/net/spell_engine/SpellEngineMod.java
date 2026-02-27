package net.spell_engine;

import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.serializer.JanksonConfigSerializer;
import me.shedaniel.autoconfig.serializer.PartitioningSerializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.registry.DynamicRegistries;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.minecraft.advancement.criterion.Criteria;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.SpawnGroup;
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
import net.spell_engine.entity.SpellProjectile;
import net.spell_engine.internals.SpellEngineCommands;
import net.spell_engine.internals.SpellTriggers;
import net.spell_engine.internals.container.SpellAssignments;
import net.spell_engine.internals.container.SpellContainerSource;
import net.spell_engine.internals.criteria.EnchantmentSpecificCriteria;
import net.spell_engine.internals.criteria.SpellCastCriteria;
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

    public static void init() {
        AutoConfig.register(ServerConfigWrapper.class, PartitioningSerializer.wrap(JanksonConfigSerializer::new));
        config = AutoConfig.getConfigHolder(ServerConfigWrapper.class).getConfig().server;
        weaknessConfig.refresh();
        fallbackConfig.refresh();

        DynamicRegistries.registerSynced(SpellRegistry.KEY, SpellRegistry.LOCAL_CODEC, SpellRegistry.NETWORK_CODEC_V2);

        SpellAssignments.init();
        ServerNetwork.init();

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

        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
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
            return true;
        });
    }

    public static void registerSpellBinding() {
        Registry.register(Registries.BLOCK, SpellBinding.ID, SpellBindingBlock.INSTANCE);
        Registry.register(Registries.BLOCK_ENTITY_TYPE, SpellBinding.ID, SpellBindingBlockEntity.ENTITY_TYPE);
        Registry.register(Registries.SCREEN_HANDLER, SpellBinding.ID, SpellBindingScreenHandler.HANDLER_TYPE);
        Registry.register(Registries.LOOT_FUNCTION_TYPE, SpellBindRandomlyLootFunction.ID, SpellBindRandomlyLootFunction.TYPE);
        Registry.register(Registries.SCREEN_HANDLER, SpellChoiceFeature.ID, SpellChoiceScreenHandler.HANDLER_TYPE);
    }

    public static void registerEntityTypes() {
        SpellProjectile.ENTITY_TYPE = Registry.register(
                Registries.ENTITY_TYPE,
                Identifier.of(SpellEngineMod.ID, "spell_projectile"),
                FabricEntityTypeBuilder.<SpellProjectile>create(SpawnGroup.MISC, SpellProjectile::new)
                        .dimensions(EntityDimensions.fixed(0.25F, 0.25F)) // dimensions in Minecraft units of the render
                        .fireImmune()
                        .trackRangeBlocks(128)
                        .trackedUpdateRate(2)
                        .build()
        );
        SpellCloud.ENTITY_TYPE = Registry.register(
                Registries.ENTITY_TYPE,
                Identifier.of(SpellEngineMod.ID, "spell_area_effect"),
                FabricEntityTypeBuilder.<SpellCloud>create(SpawnGroup.MISC, SpellCloud::new)
                        .dimensions(EntityDimensions.changing(6F, 0.5F)) // dimensions in Minecraft units of the render
                        .fireImmune()
                        .trackRangeBlocks(128)
                        .trackedUpdateRate(20)
                        .build()
        );
    }

    public static void registerCriteria() {
        Criteria.register(EnchantmentSpecificCriteria.ID.toString(), EnchantmentSpecificCriteria.INSTANCE);
        Criteria.register(SpellCastCriteria.ID.toString(), SpellCastCriteria.INSTANCE);

        Criteria.register(SpellBindingCriteria.ID.toString(), SpellBindingCriteria.INSTANCE);
        Criteria.register(SpellBookCreationCriteria.ID.toString(), SpellBookCreationCriteria.INSTANCE);
    }
}