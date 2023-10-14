package net.spell_engine.client;

import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.serializer.JanksonConfigSerializer;
import me.shedaniel.autoconfig.serializer.PartitioningSerializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreens;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactories;
import net.minecraft.item.BowItem;
import net.minecraft.item.CrossbowItem;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.spell_engine.SpellEngineMod;
import net.spell_engine.client.animation.AnimationRegistry;
import net.spell_engine.client.render.ModelPredicateHelper;
import net.spell_engine.client.render.SpellBindingBlockEntityRenderer;
import net.spell_engine.config.ClientConfig;
import net.spell_engine.config.ClientConfigWrapper;
import net.spell_engine.config.HudConfig;
import net.spell_engine.spellbinding.SpellBindingBlockEntity;
import net.spell_engine.spellbinding.SpellBindingScreen;
import net.spell_engine.spellbinding.SpellBindingScreenHandler;
import net.tinyconfig.ConfigManager;

public class SpellEngineClient {
    public static ClientConfig config;

    public static ConfigManager<HudConfig> hudConfig = new ConfigManager<HudConfig>
            ("hud_config", HudConfig.createDefault())
            .builder()
            .setDirectory(SpellEngineMod.ID)
            .sanitize(true)
            .validate(HudConfig::isValid)
            .build();

    public static void initialize() {
        AutoConfig.register(ClientConfigWrapper.class, PartitioningSerializer.wrap(JanksonConfigSerializer::new));
        config = AutoConfig.getConfigHolder(ClientConfigWrapper.class).getConfig().client;
        hudConfig.refresh();

        ClientNetwork.initializeHandlers();

        ClientLifecycleEvents.CLIENT_STARTED.register((client) -> {
            var resourceManager = MinecraftClient.getInstance().getResourceManager();
            AnimationRegistry.load(resourceManager);
            injectRangedWeaponModelPredicates();
        });

        HandledScreens.register(SpellBindingScreenHandler.HANDLER_TYPE, SpellBindingScreen::new);
        BlockEntityRendererFactories.register(SpellBindingBlockEntity.ENTITY_TYPE, SpellBindingBlockEntityRenderer::new);
    }

    private static void injectRangedWeaponModelPredicates() {
        for(var itemId: Registries.ITEM.getIds()) {
            var item = Registries.ITEM.get(itemId);
            if (item instanceof BowItem) {
                ModelPredicateHelper.injectBowSkillUsePredicate(item);
            } else if (item instanceof CrossbowItem) {
                ModelPredicateHelper.injectCrossBowSkillUsePredicate(item);
            }
        }
    }
}
