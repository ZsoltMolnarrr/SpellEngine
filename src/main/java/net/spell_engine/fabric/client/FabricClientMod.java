package net.spell_engine.fabric.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.spell_engine.api.item.set.EquipmentSetTooltip;
import net.spell_engine.client.SpellEngineClient;
import net.spell_engine.client.gui.SpellTooltip;
import net.spell_engine.client.input.Keybindings;
import net.spell_engine.client.particle.*;
import net.spell_engine.client.render.CustomModelRegistry;
import net.spell_engine.client.render.SpellCloudRenderer;
import net.spell_engine.client.render.SpellProjectileRenderer;
import net.spell_engine.entity.SpellCloud;
import net.spell_engine.entity.SpellProjectile;
import net.spell_engine.fx.SpellEngineParticles;

public class FabricClientMod implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        SpellEngineClient.initialize();
        registerKeyBindings();

        // HudRenderCallback.EVENT.register - had issues, rendering my content in random order
        // Invocation of HudRenderHelper.render() moved into InGameHudMixin
//        HudRenderCallback.EVENT.register((context, tickCounter) -> {
//            if (!MinecraftClient.getInstance().options.hudHidden) {
//                HudRenderHelper.render(context, tickCounter.getTickDelta(true));
//            }
//        });

        ItemTooltipCallback.EVENT.register((itemStack, tooltipContext, tooltipType, lines) -> {
            SpellTooltip.addSpellLines(itemStack, tooltipType, lines);
            EquipmentSetTooltip.appendLines(itemStack, lines);
        });
        EntityRendererRegistry.register(SpellProjectile.ENTITY_TYPE, SpellProjectileRenderer::new);
        EntityRendererRegistry.register(SpellCloud.ENTITY_TYPE, SpellCloudRenderer::new);

        registerParticleAppearances();
    }

    private void registerParticleAppearances() {
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

        for (var variant: SpellEngineParticles.MAGIC_FAMILY_VARIANTS.get()) {
            ParticleFactoryRegistry.getInstance().register(
                    variant.particleType(), (provider) -> new UniversalSpellParticle.MagicVariant(provider, variant)
            );
        }

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

        ParticleFactoryRegistry.getInstance().register(SpellEngineParticles.sign_charge.particleType(), SpellFlameParticle.RageSignFactory::new);
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
                    entry.particleType(), (provider) -> new SpellAreaParticle.Factory(provider, entry.texture(), entry.fading())
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

        ModelLoadingPlugin.register(pluginCtx -> {
            pluginCtx.addModels(CustomModelRegistry.modelIds);
        });
    }

    private void registerKeyBindings() {
        for(var keybinding: Keybindings.all()) {
            KeyBindingHelper.registerKeyBinding(keybinding);
        }
    }
}
