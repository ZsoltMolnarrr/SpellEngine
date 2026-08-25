package net.spell_engine.fabric.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.MinecraftClient;
import net.spell_engine.client.gui.HudRenderHelper;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.gui.screen.ingame.HandledScreens;
import net.spell_engine.client.SpellEngineClient;
import net.spell_engine.client.input.Keybindings;
import net.spell_engine.client.render.BeamRenderer;
import net.spell_engine.client.render.CustomModelRegistry;
import net.spell_engine.client.render.SpellCloudRenderer;
import net.spell_engine.client.render.SpellModelEffectRenderer;
import net.spell_engine.client.render.SpellProjectileRenderer;
import net.spell_engine.entity.SpellCloud;
import net.spell_engine.entity.SpellModelEffect;
import net.spell_engine.entity.SpellProjectile;
import net.spell_engine.spellbinding.SpellBindingScreen;
import net.spell_engine.spellbinding.SpellBindingScreenHandler;
import net.spell_engine.spellbinding.spellchoice.SpellChoiceScreen;
import net.spell_engine.spellbinding.spellchoice.SpellChoiceScreenHandler;

public final class FabricClientMod implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        SpellEngineClient.init();
        FabricClientNetwork.init();

        // Loader-specific registrations, delegating to the loader-neutral SpellEngineClient logic.
        HandledScreens.register(SpellBindingScreenHandler.HANDLER_TYPE, SpellBindingScreen::new);
        HandledScreens.register(SpellChoiceScreenHandler.HANDLER_TYPE, SpellChoiceScreen::new);
        EntityRendererRegistry.register(SpellProjectile.ENTITY_TYPE, SpellProjectileRenderer::new);
        EntityRendererRegistry.register(SpellCloud.ENTITY_TYPE, SpellCloudRenderer::new);
        EntityRendererRegistry.register(SpellModelEffect.ENTITY_TYPE, SpellModelEffectRenderer::new);
        SpellEngineClient.registerParticleAppearances(new SpellEngineClient.ParticleAppearanceRegistrar() {
            @Override
            public <T extends net.minecraft.particle.ParticleEffect> void register(net.minecraft.particle.ParticleType<T> type, SpellEngineClient.SpriteFactory<T> factory) {
                ParticleFactoryRegistry.getInstance().register(type, factory::create);
            }
        });
        ClientLifecycleEvents.CLIENT_STARTED.register(client -> SpellEngineClient.onClientStarted());
        // Spell HUD (hotbar, cast bar, error messages) as its own HUD element, right after the vanilla status-bar
        // group (mount health is its last element on Fabric; NeoForge registers above AIR_LEVEL, the same spot).
        // Elements get their own GUI layer, so it is composited above the status bars instead of underneath them.
        HudElementRegistry.attachElementAfter(VanillaHudElements.MOUNT_HEALTH, HudRenderHelper.HUD_ELEMENT_ID, (context, tickCounter) ->
                HudRenderHelper.renderHudElement(context, tickCounter.getTickProgress(true)));
        ItemTooltipCallback.EVENT.register((stack, tooltipContext, tooltipType, lines) ->
                SpellEngineClient.addTooltipLines(stack, tooltipType, lines));
        // 1.21.9+ world render events are extraction/main split; AFTER_TRANSLUCENT is gone. Beams draw in END_MAIN.
        WorldRenderEvents.END_MAIN.register(context ->
                BeamRenderer.renderAfterTranslucent(context.matrices(), context.gameRenderer().getCamera(),
                        MinecraftClient.getInstance().getRenderTickCounter().getTickProgress(true)));

        registerKeyBindings();
        FabricModelDiscovery.install();
    }

    private static void registerKeyBindings() {
        for (var keybinding: Keybindings.all()) {
            KeyBindingHelper.registerKeyBinding(keybinding);
        }
    }

}
