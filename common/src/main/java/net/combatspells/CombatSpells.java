package net.combatspells;

import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.serializer.JanksonConfigSerializer;
import me.shedaniel.autoconfig.serializer.PartitioningSerializer;
import net.combatspells.api.Enchantments_CombatSpells;
import net.combatspells.attribute_assigner.AttributeAssigner;
import net.combatspells.config.EnchantmentsConfig;
import net.combatspells.config.ServerConfig;
import net.combatspells.config.ServerConfigWrapper;
import net.combatspells.entity.SpellProjectile;
import net.combatspells.internals.SpellRegistry;
import net.combatspells.network.ServerNetwork;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.entity.EntityType;
import net.minecraft.util.registry.Registry;
import net.tinyconfig.ConfigManager;

public class CombatSpells {
    public static final String MOD_ID = "combatspells";
    public static String modName() {
        return I18n.translate("combatspells.mod_name");
    }

    public static ServerConfig config;

    public static ConfigManager<EnchantmentsConfig> enchantmentConfig = new ConfigManager<EnchantmentsConfig>
            ("enchantments", new EnchantmentsConfig())
            .builder()
            .setDirectory(MOD_ID)
            .sanitize(true)
            .build();
    public static EntityType<SpellProjectile> SPELL_PROJECTILE;

    public static void init() {
        AutoConfig.register(ServerConfigWrapper.class, PartitioningSerializer.wrap(JanksonConfigSerializer::new));
        config = AutoConfig.getConfigHolder(ServerConfigWrapper.class).getConfig().server;
        enchantmentConfig.refresh();

        SpellRegistry.initialize();
        AttributeAssigner.initialize();

        ServerNetwork.initializeHandlers();
    }

    public static void registerEnchantments() {
        enchantmentConfig.value.apply();
        for(var entry: Enchantments_CombatSpells.all.entrySet()) {
            Registry.register(Registry.ENCHANTMENT, entry.getKey(), entry.getValue());
        }
    }
}