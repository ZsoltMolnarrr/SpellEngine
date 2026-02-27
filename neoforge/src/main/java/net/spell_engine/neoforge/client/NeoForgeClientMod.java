package net.spell_engine.neoforge.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.ModelIdentifier;
import net.minecraft.util.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.spell_engine.SpellEngineMod;
import net.spell_engine.client.SpellEngineClient;
import net.spell_engine.client.gui.ConfigMenuScreen;
import net.spell_engine.client.gui.HudRenderHelper;
import net.spell_engine.client.input.Keybindings;
import net.spell_engine.client.render.CustomModelRegistry;

@EventBusSubscriber(modid = SpellEngineMod.ID, value = Dist.CLIENT)
public class NeoForgeClientMod {
    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        SpellEngineClient.init();

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
        SpellEngineClient.registerParticleAppearances();
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