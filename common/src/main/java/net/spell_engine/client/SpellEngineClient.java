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
import net.spell_engine.api.spell.fx.ParticleBatch;
import net.spell_engine.client.compatibility.CompatFeatures;
import net.spell_engine.client.gui.SpellTooltip;
import net.spell_engine.client.particle.*;
import net.spell_engine.client.render.*;
import net.spell_engine.client.util.Color;
import net.spell_engine.config.ClientConfig;
import net.spell_engine.config.ClientConfigWrapper;
import net.spell_engine.config.HudConfig;
import net.spell_engine.entity.SpellCloud;
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
        final var magicSnareParticles = new ParticleBatch(
                SpellEngineParticles.MagicParticles.get(
                        SpellEngineParticles.MagicParticles.Shape.SPARK,
                        SpellEngineParticles.MagicParticles.Motion.DECELERATE).id().toString(),
                ParticleBatch.Shape.CIRCLE, ParticleBatch.Origin.FEET,
                2F, 0.15F, 0.15F)
                .preSpawnTravel(5)
                .invert();
        CustomParticleStatusEffect.register(
                SpellEngineEffects.IMMOBILIZE.effect,
                new BuffParticleSpawner(new ParticleBatch[]{ magicSnareParticles
                        .copy().color(Color.PHYSICAL_BLUE.toRGBA()) })
        );

    }

    public static void registerParticleAppearances() {
        /* Adds our particle textures to vanilla's Texture Atlas so it can be shown properly.
         * Modify the namespace and particle id accordingly.
         *
         * This is only used if you plan to add your own textures for the particle. Otherwise, remove  this.*/
//        ClientSpriteRegistryCallback.event(PlayerScreenHandler.BLOCK_ATLAS_TEXTURE).register(((atlasTexture, registry) -> {
//            for(var entry: Particles.all()) {
//                if (entry.usesCustomTexture) {
//                    registry.register(entry.id);
//                }
//            }
//        }));

        /* Registers our particle client-side.
         * First argument is our particle's instance, created previously on ExampleMod.
         * Second argument is the particle's factory. The factory controls how the particle behaves.
         * In this example, we'll use FlameParticle's Factory.*/

        // Elemental

        ParticleFactoryRegistry.getInstance().register(SpellEngineParticles.flame.particleType(), SpellFlameParticle.FlameFactory::new);
        ParticleFactoryRegistry.getInstance().register(SpellEngineParticles.flame_spark.particleType(), SpellFlameParticle.AnimatedFlameFactory::new);
        ParticleFactoryRegistry.getInstance().register(SpellEngineParticles.flame_ground.particleType(), SpellFlameParticle.AnimatedFlameFactory::new);
        ParticleFactoryRegistry.getInstance().register(SpellEngineParticles.flame_medium_a.particleType(), SpellFlameParticle.MediumFlameFactory::new);
        ParticleFactoryRegistry.getInstance().register(SpellEngineParticles.flame_medium_b.particleType(), SpellFlameParticle.MediumFlameFactory::new);
        ParticleFactoryRegistry.getInstance().register(SpellEngineParticles.snowflake.particleType(), SpellSnowflakeParticle.FrostFactory::new);
        ParticleFactoryRegistry.getInstance().register(SpellEngineParticles.frost_shard.particleType(), SpellFlameParticle.FrostShard::new);

        ParticleFactoryRegistry.getInstance().register(SpellEngineParticles.electric_arc_A.particleType(), SpellFlameParticle.ElectricSparkFactory::new);
        ParticleFactoryRegistry.getInstance().register(SpellEngineParticles.electric_arc_B.particleType(), SpellFlameParticle.ElectricSparkFactory::new);

        // Physical

        ParticleFactoryRegistry.getInstance().register(SpellEngineParticles.smoke_medium.particleType(), SpellFlameParticle.SmokeFactory::new);

        // Misc

        ParticleFactoryRegistry.getInstance().register(SpellEngineParticles.weakness_smoke.particleType(), SpellFlameParticle.WeaknessSmokeFactory::new);

        ParticleFactoryRegistry.getInstance().register(
                SpellEngineParticles.shield_small.particleType(), (provider) -> new UniversalSpellParticle.Opaque(provider, SpellEngineParticles.MagicParticleFamily.Motion.DECELERATE)
        );

        ParticleFactoryRegistry.getInstance().register(SpellEngineParticles.dripping_blood.particleType(), SpellSnowflakeParticle.DrippingBloodFactory::new);
        ParticleFactoryRegistry.getInstance().register(SpellEngineParticles.roots.particleType(), ShiftedParticle.RootsFactory::new);

        // Macro, billboard, whatever

        ParticleFactoryRegistry.getInstance().register(SpellEngineParticles.fire_explosion.particleType(), SpellExplosionParticle.TemplateFactory::new);

        ParticleFactoryRegistry.getInstance().register(SpellEngineParticles.smoke_large.particleType(), SpellSmokeParticle.CosySmokeFactory::new);

        for (var entry: SpellEngineParticles.areaEffects()) {
            ParticleFactoryRegistry.getInstance().register(
                    entry.particleType(), (provider) -> new SpellAreaParticle.Factory(provider, entry.texture(), entry.fading(), entry.orientation())
            );
        }
        for (var entry: SpellEngineParticles.signEffects()) {
            ParticleFactoryRegistry.getInstance().register(
                    entry.particleType(), (provider) -> new SpellFlameParticle.SignFactory(provider, entry.texture())
            );
        }
        for (var variant: SpellEngineParticles.MagicParticles.all) {
            ParticleFactoryRegistry.getInstance().register(
                    variant.entry().particleType(), (provider) -> new SpellUniversalParticle.MagicVariant(provider, variant)
            );
        }
    }
}
