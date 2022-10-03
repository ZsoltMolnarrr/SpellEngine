package net.combatspells.client;

import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.serializer.JanksonConfigSerializer;
import me.shedaniel.autoconfig.serializer.PartitioningSerializer;
import net.combatspells.CombatSpells;
import net.combatspells.config.HudConfig;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.minecraft.client.MinecraftClient;
import net.combatspells.client.animation.AnimationRegistry;
import net.combatspells.config.ClientConfig;
import net.combatspells.config.ClientConfigWrapper;
import net.tinyconfig.ConfigManager;

public class CombatSpellsClient {
    public static ClientConfig config;

    public static ConfigManager<HudConfig> hudConfig = new ConfigManager<HudConfig>
            ("hud_config", HudConfig.createDefault())
            .builder()
            .setDirectory(CombatSpells.MOD_ID)
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
    }
}
