package net.spell_engine.neoforge.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.spell_engine.SpellEngineMod;
import net.spell_engine.client.SpellEngineClient;
import net.spell_engine.client.gui.ConfigMenuScreen;
import net.spell_engine.client.gui.HudRenderHelper;
import net.spell_engine.client.input.Keybindings;

@EventBusSubscriber(modid = SpellEngineMod.ID, value = Dist.CLIENT)
public class NeoForgeClientMod {
    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        SpellEngineClient.init();

        ModLoadingContext.get().registerExtensionPoint(IConfigScreenFactory.class, () -> (modContainer, parent) -> new ConfigMenuScreen(parent));
    }

    @SubscribeEvent
    public static void onRenderGuiLayerPost(RenderGuiLayerEvent.Pre event) {
        HudRenderHelper.render(event.getGuiGraphics(), event.getPartialTick().getTickDelta(true));
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
}