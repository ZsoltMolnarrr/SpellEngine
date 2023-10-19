package net.spell_engine;

import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.serializer.JanksonConfigSerializer;
import me.shedaniel.autoconfig.serializer.PartitioningSerializer;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.advancement.criterion.Criteria;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import net.spell_engine.api.enchantment.Enchantments_SpellEngine;
import net.spell_engine.api.item.AttributeResolver;
import net.spell_engine.api.item.trinket.SpellBooks;
import net.spell_engine.api.item.weapon.StaffItem;
import net.spell_engine.api.spell.SpellContainer;
import net.spell_engine.config.EnchantmentsConfig;
import net.spell_engine.config.ServerConfig;
import net.spell_engine.config.ServerConfigWrapper;
import net.spell_engine.internals.SpellContainerHelper;
import net.spell_engine.internals.SpellRegistry;
import net.spell_engine.internals.criteria.EnchantmentSpecificCriteria;
import net.spell_engine.network.ServerNetwork;
import net.spell_engine.particle.Particles;
import net.spell_engine.spellbinding.*;
import net.spell_power.api.enchantment.EnchantmentRestriction;
import net.spell_power.api.enchantment.Enchantments_SpellPower;
import net.spell_power.api.enchantment.SpellPowerEnchanting;
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

    public static void init() {
        AttributeResolver.setup();
        AutoConfig.register(ServerConfigWrapper.class, PartitioningSerializer.wrap(JanksonConfigSerializer::new));
        config = AutoConfig.getConfigHolder(ServerConfigWrapper.class).getConfig().server;
        enchantmentConfig.refresh();

        SpellRegistry.initialize();
        ServerNetwork.initializeHandlers();
        Particles.register();

        Criteria.register(EnchantmentSpecificCriteria.INSTANCE);

        EnchantmentRestriction.permit(Enchantments.KNOCKBACK, itemStack -> itemStack.getItem() instanceof StaffItem);
        EnchantmentRestriction.permit(Enchantments.LOOTING, itemStack -> itemStack.getItem() instanceof StaffItem);
        EnchantmentRestriction.permit(Enchantments.FIRE_ASPECT, itemStack -> itemStack.getItem() instanceof StaffItem);
        EnchantmentRestriction.prohibit(Enchantments_SpellPower.HASTE, itemStack -> {
            var item = itemStack.getItem();
            EquipmentSlot slot;
            if (item instanceof ArmorItem armorItem) {
                slot = armorItem.getSlotType();
            } else {
                slot = EquipmentSlot.MAINHAND;
            }
            var empty = SpellPowerEnchanting.relevantSchools(itemStack, slot).isEmpty();
            return empty;
        });

        SpellPowerEnchanting.allowForWeapon(itemStack -> {
            var container = SpellContainerHelper.containerFromItemStack(itemStack);
            return container != null && container.isValid() && container.content != SpellContainer.ContentType.ARCHERY;
        });

        // Sync attack power to client so physical attack damage spells can be estimated.
        // Probably several other mods perform this operation, but its no problem.
        EntityAttributes.GENERIC_ATTACK_DAMAGE.setTracked(true);
    }

    public static void registerSpellBinding() {
        Registry.register(Registries.BLOCK, SpellBinding.ID, SpellBindingBlock.INSTANCE);
        Registry.register(Registries.BLOCK_ENTITY_TYPE, SpellBinding.ID, SpellBindingBlockEntity.ENTITY_TYPE);
        Registry.register(Registries.ITEM, SpellBinding.ID, SpellBindingBlock.ITEM);
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.FUNCTIONAL).register(content -> {
            content.add(SpellBindingBlock.ITEM);
        });
        Registry.register(Registries.SCREEN_HANDLER, SpellBinding.ID, SpellBindingScreenHandler.HANDLER_TYPE);
        Criteria.register(SpellBindingCriteria.INSTANCE);
        Criteria.register(SpellBookCreationCriteria.INSTANCE);
    }

    public static void registerEnchantments() {
        enchantmentConfig.value.apply();
        for(var entry: Enchantments_SpellEngine.all.entrySet()) {
            Registry.register(Registries.ENCHANTMENT, entry.getKey(), entry.getValue());
        }
    }
}