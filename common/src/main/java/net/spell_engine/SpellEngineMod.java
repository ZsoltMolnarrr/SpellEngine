package net.spell_engine;

import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.serializer.JanksonConfigSerializer;
import me.shedaniel.autoconfig.serializer.PartitioningSerializer;
import net.spell_engine.api.Enchantments_CombatSpells;
import net.spell_engine.attribute_assigner.AttributeAssigner;
import net.spell_engine.config.EnchantmentsConfig;
import net.spell_engine.config.ServerConfig;
import net.spell_engine.config.ServerConfigWrapper;
import net.spell_engine.entity.SpellProjectile;
import net.spell_engine.internals.SpellRegistry;
import net.spell_engine.network.ServerNetwork;
import net.spell_engine.particle.Particles;
import net.spell_engine.runes.RuneCraftingBlock;
import net.spell_engine.runes.RuneCraftingRecipe;
import net.spell_engine.runes.RuneCraftingScreenHandler;
import net.spell_engine.runes.RuneItems;
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

public class SpellEngineMod {
    public static final String ID = "spell_engine";
    public static String modName() {
        return I18n.translate("spell_engine.mod_name");
    }

    public static ServerConfig config;

    public static ConfigManager<EnchantmentsConfig> enchantmentConfig = new ConfigManager<EnchantmentsConfig>
            ("enchantments", new EnchantmentsConfig())
            .builder()
            .setDirectory(ID)
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
        Particles.register();
    }

    public static void registerRuneCrafting() {
        Registry.register(Registry.RECIPE_TYPE, new Identifier(ID, RuneCraftingRecipe.ID), RuneCraftingRecipe.TYPE);
        Registry.register(Registry.RECIPE_SERIALIZER, new Identifier(ID, RuneCraftingRecipe.ID), RuneCraftingRecipe.SERIALIZER);
        Registry.register(Registry.BLOCK, new Identifier(ID, RuneCraftingBlock.NAME), RuneCraftingBlock.INSTANCE);
        Registry.register(Registry.ITEM, new Identifier(ID, RuneCraftingBlock.NAME), new BlockItem(RuneCraftingBlock.INSTANCE, new FabricItemSettings().group(ItemGroup.DECORATIONS)));
        BlockRenderLayerMap.INSTANCE.putBlock(RuneCraftingBlock.INSTANCE, RenderLayer.getCutout());
        Registry.register(Registry.SCREEN_HANDLER, new Identifier(ID, RuneCraftingRecipe.ID), RuneCraftingScreenHandler.HANDLER_TYPE);
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