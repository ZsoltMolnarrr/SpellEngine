package net.spell_engine.client;

import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.serializer.JanksonConfigSerializer;
import me.shedaniel.autoconfig.serializer.PartitioningSerializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreens;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactories;
import net.minecraft.util.Identifier;
import net.spell_engine.SpellEngineMod;
import net.spell_engine.api.client.CustomModels;
import net.spell_engine.api.status_effect.CustomModelStatusEffect;
import net.spell_engine.api.status_effect.CustomParticleStatusEffect;
import net.spell_engine.client.animation.AnimationRegistry;
import net.spell_engine.client.render.SpellBindingBlockEntityRenderer;
import net.spell_engine.config.ClientConfig;
import net.spell_engine.config.ClientConfigWrapper;
import net.spell_engine.config.HudConfig;
import net.spell_engine.spellbinding.SpellBindingBlockEntity;
import net.spell_engine.spellbinding.SpellBindingScreen;
import net.spell_engine.spellbinding.SpellBindingScreenHandler;
import net.spell_engine.wizards.client.FrostShieldRenderer;
import net.spell_engine.wizards.client.FrozenRenderer;
import net.tinyconfig.ConfigManager;

import java.util.List;

public class SpellEngineClient {
    public static ClientConfig config;

    public static ConfigManager<HudConfig> hudConfig = new ConfigManager<HudConfig>
            ("hud_config", HudConfig.createDefault())
            .builder()
            .setDirectory(SpellEngineMod.ID)
            .sanitize(true)
            .build();

    public static void initialize() {
        AutoConfig.register(ClientConfigWrapper.class, PartitioningSerializer.wrap(JanksonConfigSerializer::new));
        config = AutoConfig.getConfigHolder(ClientConfigWrapper.class).getConfig().client;
        hudConfig.refresh();

        ClientNetwork.initializeHandlers();

        ClientLifecycleEvents.CLIENT_STARTED.register((client) -> {
            var resourceManager = MinecraftClient.getInstance().getResourceManager();
            AnimationRegistry.load(resourceManager);
        });

        HandledScreens.register(SpellBindingScreenHandler.HANDLER_TYPE, SpellBindingScreen::new);
        BlockEntityRendererFactories.register(SpellBindingBlockEntity.ENTITY_TYPE, SpellBindingBlockEntityRenderer::new);
    }
}
