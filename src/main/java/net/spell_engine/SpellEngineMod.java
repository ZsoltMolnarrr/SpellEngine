package net.spell_engine;

import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.serializer.JanksonConfigSerializer;
import me.shedaniel.autoconfig.serializer.PartitioningSerializer;
import net.fabricmc.fabric.api.event.registry.DynamicRegistries;
import net.minecraft.advancement.criterion.Criteria;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.spell_engine.api.effect.StatusEffectClassification;
import net.spell_engine.api.spell.ExternalSpellSchools;
import net.spell_engine.api.spell.event.SpellEvents;
import net.spell_engine.api.spell.registry.SpellRegistry;
import net.spell_engine.config.ServerConfig;
import net.spell_engine.config.ServerConfigWrapper;
import net.spell_engine.internals.SpellEngineCommands;
import net.spell_engine.internals.SpellTriggers;
import net.spell_engine.internals.container.SpellAssignments;
import net.spell_engine.internals.container.SpellContainerSource;
import net.spell_engine.internals.criteria.EnchantmentSpecificCriteria;
import net.spell_engine.internals.criteria.SpellCastCriteria;
import net.spell_engine.internals.delivery.SpellStashHelper;
import net.spell_engine.network.ServerNetwork;
import net.spell_engine.fx.SpellEngineParticles;
import net.spell_engine.rpg_series.RPGSeriesCore;
import net.spell_engine.spellbinding.*;

public class SpellEngineMod {
    public static final String ID = "spell_engine";
    public static String modName() {
        return I18n.translate("spell_engine.mod_name");
    }

    public static ServerConfig config;

    public static void init() {
        AutoConfig.register(ServerConfigWrapper.class, PartitioningSerializer.wrap(JanksonConfigSerializer::new));
        config = AutoConfig.getConfigHolder(ServerConfigWrapper.class).getConfig().server;

        DynamicRegistries.registerSynced(SpellRegistry.KEY, SpellRegistry.LOCAL_CODEC, SpellRegistry.NETWORK_CODEC);

        SpellAssignments.init();
        ServerNetwork.init();
        SpellEngineParticles.register();

        Criteria.register(EnchantmentSpecificCriteria.ID.toString(), EnchantmentSpecificCriteria.INSTANCE);
        Criteria.register(SpellCastCriteria.ID.toString(), SpellCastCriteria.INSTANCE);
        SpellEvents.SPELL_CAST.register(args -> {
            SpellCastCriteria.INSTANCE.trigger((ServerPlayerEntity) args.caster(), args.spell());
        });

        ExternalSpellSchools.init();
        RPGSeriesCore.init();
        SpellStashHelper.init();
        SpellTriggers.init();
        SpellContainerSource.init();
        StatusEffectClassification.init();
        SpellEngineCommands.register();
    }

    public static void registerSpellBinding() {
        Registry.register(Registries.BLOCK, SpellBinding.ID, SpellBindingBlock.INSTANCE);
        Registry.register(Registries.BLOCK_ENTITY_TYPE, SpellBinding.ID, SpellBindingBlockEntity.ENTITY_TYPE);
        Registry.register(Registries.SCREEN_HANDLER, SpellBinding.ID, SpellBindingScreenHandler.HANDLER_TYPE);
        Criteria.register(SpellBindingCriteria.ID.toString(), SpellBindingCriteria.INSTANCE);
        Criteria.register(SpellBookCreationCriteria.ID.toString(), SpellBookCreationCriteria.INSTANCE);
        Registry.register(Registries.LOOT_FUNCTION_TYPE, SpellBindRandomlyLootFunction.ID, SpellBindRandomlyLootFunction.TYPE);
    }
}