package net.spell_engine.fabric.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.spell_engine.client.gui.HudRenderHelper;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.MenuScreens;
import net.spell_engine.client.SpellEngineClient;
import net.spell_engine.client.input.Keybindings;
import net.spell_engine.client.render.BeamRenderer;
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
        MenuScreens.register(SpellBindingScreenHandler.HANDLER_TYPE, SpellBindingScreen::new);
        MenuScreens.register(SpellChoiceScreenHandler.HANDLER_TYPE, SpellChoiceScreen::new);
        EntityRendererRegistry.register(SpellProjectile.ENTITY_TYPE, SpellProjectileRenderer::new);
        EntityRendererRegistry.register(SpellCloud.ENTITY_TYPE, SpellCloudRenderer::new);
        EntityRendererRegistry.register(SpellModelEffect.ENTITY_TYPE, SpellModelEffectRenderer::new);
        SpellEngineClient.registerParticleAppearances(new SpellEngineClient.ParticleAppearanceRegistrar() {
            @Override
            public <T extends net.minecraft.core.particles.ParticleOptions> void register(net.minecraft.core.particles.ParticleType<T> type, SpellEngineClient.SpriteFactory<T> factory) {
                ParticleProviderRegistry.getInstance().register(type, factory::create);
            }
        });
        ClientLifecycleEvents.CLIENT_STARTED.register(client -> SpellEngineClient.onClientStarted());
        // Spell HUD (hotbar, cast bar, error messages) as its own HUD element, right after the vanilla status-bar
        // group (mount health is its last element on Fabric; NeoForge registers above AIR_LEVEL, the same spot).
        // Elements get their own GUI layer, so it is composited above the status bars instead of underneath them.
        HudElementRegistry.attachElementAfter(VanillaHudElements.MOUNT_HEALTH, HudRenderHelper.HUD_ELEMENT_ID, (context, tickCounter) ->
                HudRenderHelper.renderHudElement(context, tickCounter.getGameTimeDeltaPartialTick(true)));
        ItemTooltipCallback.EVENT.register((stack, tooltipContext, tooltipType, lines) ->
                SpellEngineClient.addTooltipLines(stack, tooltipType, lines));
        // 26.1: `WorldRenderEvents` → `LevelRenderEvents` (extraction/main split); beams draw in END_MAIN.
        LevelRenderEvents.END_MAIN.register(context ->
                BeamRenderer.renderAfterTranslucent(context.poseStack(), context.gameRenderer().getMainCamera(),
                        Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(true)));

        registerKeyBindings();
        FabricModelDiscovery.install();
    }

    private static void registerKeyBindings() {
        for (var keybinding: Keybindings.all()) {
            KeyMappingHelper.registerKeyMapping(keybinding);
        }
    }

}
