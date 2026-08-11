package net.spell_engine.neoforge.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.ModelIdentifier;
import net.minecraft.util.Identifier;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.spell_engine.SpellEngineMod;
import net.spell_engine.client.SpellEngineClient;
import net.spell_engine.client.gui.ConfigMenuScreen;
import net.spell_engine.client.gui.HudRenderHelper;
import net.spell_engine.client.input.Keybindings;
import net.spell_engine.client.render.BeamRenderer;
import net.spell_engine.client.render.CustomModelRegistry;

@EventBusSubscriber(modid = SpellEngineMod.ID, value = Dist.CLIENT)
public class NeoForgeClientMod {
    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        SpellEngineClient.init();

        // Game-bus client events (tooltip lines, beam world-render pass) — subscribed here since this
        // class is on the mod bus; the callbacks live in loader-neutral common code.
        NeoForge.EVENT_BUS.addListener(ItemTooltipEvent.class, tooltip ->
                SpellEngineClient.addTooltipLines(tooltip.getItemStack(), tooltip.getFlags(), tooltip.getToolTip()));
        NeoForge.EVENT_BUS.addListener(RenderLevelStageEvent.class, render -> {
            if (render.getStage() == RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
                BeamRenderer.renderAfterTranslucent(render.getPoseStack(), render.getCamera(), render.getPartialTick().getTickDelta(true));
            }
        });
        event.enqueueWork(SpellEngineClient::onClientStarted);

        ModLoadingContext.get().registerExtensionPoint(IConfigScreenFactory.class, () -> (modContainer, parent) -> new ConfigMenuScreen(parent));
    }

    public static final Identifier SPELL_HUD_LAYER_ID = Identifier.of(SpellEngineMod.ID, "spell_hud");
    @SubscribeEvent
    public static void registerGuiOverlaysEvent(RegisterGuiLayersEvent event) {
        event.registerBelow(VanillaGuiLayers.CHAT, SPELL_HUD_LAYER_ID, (guiGraphics, deltaTracker) -> {
            if (MinecraftClient.getInstance().options.hudHidden) { return; }
            HudRenderHelper.render(guiGraphics, deltaTracker.getTickDelta(true));
        });
    }

    @SubscribeEvent
    public static void registerKeys(RegisterKeyMappingsEvent event){
        for(var keybinding: Keybindings.all()) {
            event.register(keybinding);
        }
    }

    @SubscribeEvent // on the mod event bus only on the physical client
    public static void registerParticleProviders(RegisterParticleProvidersEvent event) {
        SpellEngineClient.registerParticleAppearances(new SpellEngineClient.ParticleAppearanceRegistrar() {
            @Override
            public <T extends ParticleEffect> void register(ParticleType<T> type, SpellEngineClient.SpriteFactory<T> factory) {
                event.registerSpriteSet(type, factory::create);
            }
        });
    }

    @SubscribeEvent
    public static void registerEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
        SpellEngineClient.registerEntityRenderers(event::registerEntityRenderer);
    }

    @SubscribeEvent
    public static void registerAdditionalModels(ModelEvent.RegisterAdditional event) {
        // WARNING! Models registered like this, need to be retrieved with `ModelIdentifier.standalone(id)` !!

        // Register custom models from registry
        for (var id: CustomModelRegistry.getModelIds()) {
            var modelId = ModelIdentifier.standalone(id);
            event.register(modelId);
        }

        // Register dynamically discovered spell models (scrolls, books, projectiles, effects)
        NeoForgeModelDiscovery.registerCustomModels(event);
    }
}