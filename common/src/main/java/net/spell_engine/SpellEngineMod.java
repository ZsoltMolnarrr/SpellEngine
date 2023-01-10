package net.spell_engine;

import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.serializer.JanksonConfigSerializer;
import me.shedaniel.autoconfig.serializer.PartitioningSerializer;
import net.fabricmc.fabric.api.item.v1.FabricItemSettings;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.EntityType;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.util.registry.Registry;
import net.spell_engine.api.enchantment.EnchantmentRestriction;
import net.spell_engine.api.enchantment.Enchantments_CombatSpells;
import net.spell_engine.api.item.StaffItem;
import net.spell_engine.config.EnchantmentsConfig;
import net.spell_engine.config.ServerConfig;
import net.spell_engine.config.ServerConfigWrapper;
import net.spell_engine.entity.SpellProjectile;
import net.spell_engine.internals.SpellRegistry;
import net.spell_engine.network.ServerNetwork;
import net.spell_engine.particle.Particles;
import net.spell_engine.spellbinding.SpellBinding;
import net.spell_engine.spellbinding.SpellBindingBlock;
import net.spell_engine.spellbinding.SpellBindingBlockEntity;
import net.spell_engine.spellbinding.SpellBindingScreenHandler;
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
        ServerNetwork.initializeHandlers();
        Particles.register();

        EnchantmentRestriction.alleviate(Enchantments.KNOCKBACK, itemStack -> itemStack.getItem() instanceof StaffItem);
        EnchantmentRestriction.alleviate(Enchantments.LOOTING, itemStack -> itemStack.getItem() instanceof StaffItem);
    }

    public static void registerSpellBinding() {
        Registry.register(Registry.BLOCK, SpellBinding.ID, SpellBindingBlock.INSTANCE);
        Registry.register(Registry.BLOCK_ENTITY_TYPE, SpellBinding.ID, SpellBindingBlockEntity.ENTITY_TYPE);
        Registry.register(Registry.ITEM, SpellBinding.ID, new BlockItem(SpellBindingBlock.INSTANCE, new FabricItemSettings().group(ItemGroup.DECORATIONS)));
        Registry.register(Registry.SCREEN_HANDLER, SpellBinding.ID, SpellBindingScreenHandler.HANDLER_TYPE);
    }

    public static void registerEnchantments() {
        enchantmentConfig.value.apply();
        for(var entry: Enchantments_CombatSpells.all.entrySet()) {
            Registry.register(Registry.ENCHANTMENT, entry.getKey(), entry.getValue());
        }
    }
}