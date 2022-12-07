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
import net.combatspells.runes.RuneCraftingBlock;
import net.combatspells.runes.RuneCraftingRecipe;
import net.combatspells.runes.RuneCraftingScreenHandler;
import net.combatspells.runes.RuneItems;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.item.v1.FabricItemSettings;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.entity.EntityType;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemGroup;
import net.minecraft.util.Identifier;
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

    public static void registerRuneCrafting() {
        Registry.register(Registry.RECIPE_TYPE, new Identifier(MOD_ID, RuneCraftingRecipe.ID), RuneCraftingRecipe.TYPE);
        Registry.register(Registry.RECIPE_SERIALIZER, new Identifier(MOD_ID, RuneCraftingRecipe.ID), RuneCraftingRecipe.SERIALIZER);
        Registry.register(Registry.BLOCK, new Identifier(MOD_ID, RuneCraftingBlock.NAME), RuneCraftingBlock.INSTANCE);
        Registry.register(Registry.ITEM, new Identifier(MOD_ID, RuneCraftingBlock.NAME), new BlockItem(RuneCraftingBlock.INSTANCE, new FabricItemSettings().group(ItemGroup.DECORATIONS)));
        BlockRenderLayerMap.INSTANCE.putBlock(RuneCraftingBlock.INSTANCE, RenderLayer.getCutout());
        Registry.register(Registry.SCREEN_HANDLER, new Identifier(MOD_ID, RuneCraftingRecipe.ID), RuneCraftingScreenHandler.HANDLER_TYPE);
    }

    public static void registerEnchantments() {
        enchantmentConfig.value.apply();
        for(var entry: Enchantments_CombatSpells.all.entrySet()) {
            Registry.register(Registry.ENCHANTMENT, entry.getKey(), entry.getValue());
        }
    }

    public static void registerItems() {
        for(var entry: RuneItems.all.entrySet()) {
            Registry.register(Registry.ITEM, entry.getKey(), entry.getValue());
        }
    }
}