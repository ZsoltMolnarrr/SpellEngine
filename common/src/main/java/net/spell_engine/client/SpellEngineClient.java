package net.spell_engine.client;

import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.serializer.JanksonConfigSerializer;
import me.shedaniel.autoconfig.serializer.PartitioningSerializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.gui.screen.ingame.HandledScreens;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactories;
import net.minecraft.item.BowItem;
import net.minecraft.item.CrossbowItem;
import net.minecraft.registry.Registries;
import net.spell_engine.SpellEngineMod;
import net.spell_engine.api.effect.CustomParticleStatusEffect;
import net.spell_engine.api.effect.SpellEngineEffects;
import net.spell_engine.api.item.set.EquipmentSetTooltip;
import net.spell_engine.api.render.BuffParticleSpawner;
import net.spell_engine.api.render.StunParticleSpawner;
import net.spell_engine.api.spell.fx.ParticleGroupBuilder;
import net.spell_engine.api.spell.fx.ParticleGroupEffect;
import net.spell_engine.client.compatibility.CompatFeatures;
import net.spell_engine.client.gui.SpellTooltip;
import net.spell_engine.client.particle.*;
import net.spell_engine.client.render.*;
import net.spell_engine.client.util.Color;
import net.spell_engine.config.ClientConfig;
import net.spell_engine.config.ClientConfigWrapper;
import net.spell_engine.config.HudConfig;
import net.spell_engine.entity.SpellCloud;
import net.spell_engine.entity.SpellModelEffect;
import net.spell_engine.entity.SpellProjectile;
import net.spell_engine.fx.SpellEngineParticles;
import net.spell_engine.rpg_series.client.RPGSeriesCoreClient;
import net.spell_engine.spellbinding.SpellBindingBlockEntity;
import net.spell_engine.spellbinding.SpellBindingScreen;
import net.spell_engine.spellbinding.SpellBindingScreenHandler;
import net.spell_engine.spellbinding.spellchoice.SpellChoiceScreen;
import net.spell_engine.spellbinding.spellchoice.SpellChoiceScreenHandler;
import net.tiny_config.ConfigManager;

public class SpellEngineClient {
    public static ClientConfig config;

    public static ConfigManager<HudConfig> hudConfig = new ConfigManager<>
            ("hud_config", HudConfig.createDefault())
            .builder()
            .setDirectory(SpellEngineMod.ID)
            .sanitize(true)
            .validate(HudConfig::isValid)
            .build();

    public static void init() {
        AutoConfig.register(ClientConfigWrapper.class, PartitioningSerializer.wrap(JanksonConfigSerializer::new));
        config = AutoConfig.getConfigHolder(ClientConfigWrapper.class).getConfig().client;
        hudConfig.refresh();

        ClientNetwork.initializeHandlers();

        ClientLifecycleEvents.CLIENT_STARTED.register((client) -> {
            injectRangedWeaponModelPredicates();
        });

        HandledScreens.register(SpellBindingScreenHandler.HANDLER_TYPE, SpellBindingScreen::new);
        HandledScreens.register(SpellChoiceScreenHandler.HANDLER_TYPE, SpellChoiceScreen::new);
        BlockEntityRendererFactories.register(SpellBindingBlockEntity.ENTITY_TYPE, SpellBindingBlockEntityRenderer::new);
        CompatFeatures.initialize();
        BeamRenderer.setup();
        registerEffectParticles();

        ItemTooltipCallback.EVENT.register((itemStack, tooltipContext, tooltipType, lines) -> {
            SpellTooltip.addSpellLines(itemStack, tooltipType, lines);
            EquipmentSetTooltip.appendLines(itemStack, lines);
        });
        EntityRendererRegistry.register(SpellProjectile.ENTITY_TYPE, SpellProjectileRenderer::new);
        EntityRendererRegistry.register(SpellCloud.ENTITY_TYPE, SpellCloudRenderer::new);
        EntityRendererRegistry.register(SpellModelEffect.ENTITY_TYPE, SpellModelEffectRenderer::new);
        ModelEffectOperations.registerDefaults();

        RPGSeriesCoreClient.init();
    }

    private static void injectRangedWeaponModelPredicates() {
        for(var itemId: Registries.ITEM.getIds()) {
            var item = Registries.ITEM.get(itemId);
            if (item instanceof BowItem) {
                ModelPredicateHelper.injectBowSkillUsePredicate(item);
            } else if (item instanceof CrossbowItem) {
                ModelPredicateHelper.injectCrossBowSkillUsePredicate(item);
            }
        }
    }

    private static void registerEffectParticles() {
        CustomParticleStatusEffect.register(
                SpellEngineEffects.STUN.effect,
                new StunParticleSpawner()
        );
        final var magicSnareParticles = ParticleGroupBuilder
                .magic(SpellEngineParticles.magic_spark, ParticleGroupEffect.Motion.DECELERATE, Color.PHYSICAL_BLUE)
                .batch(ParticleGroupBuilder.Batches.shockwave(2F, 0.15F, 5)
                        .andThen(b -> b.invert(true)));
        CustomParticleStatusEffect.register(
                SpellEngineEffects.IMMOBILIZE.effect,
                new BuffParticleSpawner(magicSnareParticles)
        );

        // Blood dripping off a bleeding entity; count scales with stacks, dripping a few times a second.
        final var bleedParticles = ParticleGroupBuilder
                .of(SpellEngineParticles.dripping_blood)
                .batch(ParticleGroupBuilder.Batches.impact(1F, 0.3F).andThen(b -> b.speed(0.1F, 0.3F)));
        CustomParticleStatusEffect.register(
                SpellEngineEffects.BLEED.effect,
                new BuffParticleSpawner(bleedParticles)
                        .withFrequency(5)
        );
    }

    public static void registerParticleAppearances() {
        // One generic factory serves every entry: appearance and behaviour are
        // resolved from the entry's defaults + the spawning effect's payload.
        for (var entry: SpellEngineParticles.entries()) {
            ParticleFactoryRegistry.getInstance().register(entry.type(),
                    (provider) -> new SpellParticle.Factory(provider, entry));
        }
    }
}