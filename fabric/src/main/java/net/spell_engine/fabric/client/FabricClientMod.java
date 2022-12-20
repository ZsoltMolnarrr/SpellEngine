package net.spell_engine.fabric.client;

import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry;
import net.fabricmc.fabric.api.event.client.ClientSpriteRegistryCallback;
import net.minecraft.screen.PlayerScreenHandler;
import net.spell_engine.SpellEngineMod;
import net.spell_engine.client.gui.SpellTooltip;
import net.spell_engine.client.particle.GenericDamageParticle;
import net.spell_engine.client.particle.GenericSpellParticle;
import net.spell_engine.client.particle.SpellFlameParticle;
import net.spell_engine.client.particle.SpellSnowflakeParticle;
import net.spell_engine.client.render.SpellProjectileRenderer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.util.math.MatrixStack;
import net.spell_engine.client.SpellEngineClient;
import net.spell_engine.client.gui.HudRenderHelper;
import net.spell_engine.particle.Particles;

public class FabricClientMod implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        SpellEngineClient.initialize();
        HudRenderCallback.EVENT.register((MatrixStack matrixStack, float tickDelta) -> {
            HudRenderHelper.render(matrixStack, tickDelta);
        });

        EntityRendererRegistry.register(SpellEngineMod.SPELL_PROJECTILE, (context) ->
                new SpellProjectileRenderer(context));

        ItemTooltipCallback.EVENT.register((itemStack, context, lines) -> {
            SpellTooltip.addSpellInfo(itemStack, lines);
        });
        registerParticleAppearances();
    }

    private void registerParticleAppearances() {
        /* Adds our particle textures to vanilla's Texture Atlas so it can be shown properly.
         * Modify the namespace and particle id accordingly.
         *
         * This is only used if you plan to add your own textures for the particle. Otherwise, remove  this.*/
        ClientSpriteRegistryCallback.event(PlayerScreenHandler.BLOCK_ATLAS_TEXTURE).register(((atlasTexture, registry) -> {
            for(var entry: Particles.all) {
                if (entry.usesCustomTexture) {
                    registry.register(entry.id);
                }
            }
        }));

        /* Registers our particle client-side.
         * First argument is our particle's instance, created previously on ExampleMod.
         * Second argument is the particle's factory. The factory controls how the particle behaves.
         * In this example, we'll use FlameParticle's Factory.*/
        ParticleFactoryRegistry.getInstance().register(Particles.flame.particleType, SpellFlameParticle.Factory::new);
        ParticleFactoryRegistry.getInstance().register(Particles.arcane_spell.particleType, GenericSpellParticle.ArcaneSpellFactory::new);
        ParticleFactoryRegistry.getInstance().register(Particles.snowflake.particleType, SpellSnowflakeParticle.Factory::new);
        ParticleFactoryRegistry.getInstance().register(Particles.frost.particleType, GenericDamageParticle.FrostFactory::new);
    }
}
