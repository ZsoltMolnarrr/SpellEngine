package net.spell_engine.fabric.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.spell_engine.client.SpellEngineClient;
import net.spell_engine.client.input.Keybindings;
import net.spell_engine.client.render.BeamRenderer;
import net.spell_engine.client.render.CustomModelRegistry;

public final class FabricClientMod implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        SpellEngineClient.init();
        FabricClientNetwork.init();

        // Loader-specific registrations, delegating to the loader-neutral SpellEngineClient logic.
        SpellEngineClient.registerEntityRenderers(EntityRendererRegistry::register);
        SpellEngineClient.registerParticleAppearances(new SpellEngineClient.ParticleAppearanceRegistrar() {
            @Override
            public <T extends net.minecraft.particle.ParticleEffect> void register(net.minecraft.particle.ParticleType<T> type, SpellEngineClient.SpriteFactory<T> factory) {
                ParticleFactoryRegistry.getInstance().register(type, factory::create);
            }
        });
        ClientLifecycleEvents.CLIENT_STARTED.register(client -> SpellEngineClient.onClientStarted());
        ItemTooltipCallback.EVENT.register((stack, tooltipContext, tooltipType, lines) ->
                SpellEngineClient.addTooltipLines(stack, tooltipType, lines));
        WorldRenderEvents.AFTER_TRANSLUCENT.register(context ->
                BeamRenderer.renderAfterTranslucent(context.matrixStack(), context.camera(), context.tickCounter().getTickDelta(true)));

        registerKeyBindings();
        registerModels();
        ModelLoadingPlugin.register(new FabricModelDiscovery());
    }

    private static void registerKeyBindings() {
        for (var keybinding: Keybindings.all()) {
            KeyBindingHelper.registerKeyBinding(keybinding);
        }
    }

    private static void registerModels() {
        ModelLoadingPlugin.register(pluginCtx -> {
            pluginCtx.addModels(CustomModelRegistry.getModelIds());
        });
    }
}
