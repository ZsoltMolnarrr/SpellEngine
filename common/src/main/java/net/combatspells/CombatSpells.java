package net.combatspells;

import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.serializer.JanksonConfigSerializer;
import me.shedaniel.autoconfig.serializer.PartitioningSerializer;
import net.combatspells.attribute_assigner.AttributeAssigner;
import net.combatspells.config.ServerConfig;
import net.combatspells.config.ServerConfigWrapper;
import net.combatspells.internals.SpellRegistry;
import net.combatspells.network.ServerNetwork;
import net.minecraft.client.resource.language.I18n;

public class CombatSpells {
    public static final String MOD_ID = "combatspells";
    public static String modName() {
        return I18n.translate("combatspells.mod_name");
    }

    public static ServerConfig config;

    public static void init() {
        AutoConfig.register(ServerConfigWrapper.class, PartitioningSerializer.wrap(JanksonConfigSerializer::new));
        config = AutoConfig.getConfigHolder(ServerConfigWrapper.class).getConfig().server;

        SpellRegistry.initialize();
        AttributeAssigner.initialize();

        ServerNetwork.initializeHandlers();
    }
}