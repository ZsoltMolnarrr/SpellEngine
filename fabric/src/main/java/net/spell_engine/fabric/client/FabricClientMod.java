package net.spell_engine.fabric.client;

import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry;
import net.fabricmc.fabric.api.event.client.ClientSpriteRegistryCallback;
import net.minecraft.client.particle.FlameParticle;
import net.minecraft.screen.PlayerScreenHandler;
import net.spell_engine.SpellEngineMod;
import net.spell_engine.client.SpellTooltip;
import net.spell_engine.client.projectile.SpellProjectileRenderer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.util.math.MatrixStack;
import net.spell_engine.client.CombatSpellsClient;
import net.spell_engine.client.gui.HudRenderHelper;
import net.spell_engine.particle.Particles;

public class FabricClientMod implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        CombatSpellsClient.initialize();
        HudRenderCallback.EVENT.register((MatrixStack matrixStack, float tickDelta) -> {
            HudRenderHelper.render(matrixStack, tickDelta);
        });

        EntityRendererRegistry.register(SpellEngineMod.SPELL_PROJECTILE, (context) ->
                new SpellProjectileRenderer(context));

        ItemTooltipCallback.EVENT.register((itemStack, context, lines) -> {
            SpellTooltip.addSpellInfo(itemStack, lines);
        });
    }

    private void registerParticleAppearances() {
        ClientSpriteRegistryCallback.event(PlayerScreenHandler.BLOCK_ATLAS_TEXTURE).register(((atlasTexture, registry) -> {
            registry.register(Particles.Fire.ID);
        }));

        /* Registers our particle client-side.
         * First argument is our particle's instance, created previously on ExampleMod.
         * Second argument is the particle's factory. The factory controls how the particle behaves.
         * In this example, we'll use FlameParticle's Factory.*/
        ParticleFactoryRegistry.getInstance().register(Particles.Fire.particle, FlameParticle.Factory::new);
    }
}
