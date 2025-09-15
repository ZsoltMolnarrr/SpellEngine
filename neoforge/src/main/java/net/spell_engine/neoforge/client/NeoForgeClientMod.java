package net.spell_engine.neoforge.client;

import net.bettercombat.client.Keybindings;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.spell_engine.SpellEngineMod;
import net.spell_engine.client.SpellEngineClient;
import net.spell_engine.client.gui.HudRenderHelper;

@EventBusSubscriber(modid = SpellEngineMod.ID, value = Dist.CLIENT)
public class NeoForgeClientMod {
    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        SpellEngineClient.init();

//        ModLoadingContext.get().registerExtensionPoint(IConfigScreenFactory.class, () -> {
//            return (IConfigScreenFactory) (modContainer, parent) -> AutoConfig.getConfigScreen(ClientConfigWrapper.class, parent).get();
//        });
    }

    @SubscribeEvent
    public static void onRenderGuiLayerPost(RenderGuiLayerEvent.Post event) {
        HudRenderHelper.render(event.getGuiGraphics(), event.getPartialTick().getTickDelta(true));
    }

    @SubscribeEvent
    public static void registerKeys(RegisterKeyMappingsEvent event){
        SpellEngineClient.registerKeyBindings();
    }
}