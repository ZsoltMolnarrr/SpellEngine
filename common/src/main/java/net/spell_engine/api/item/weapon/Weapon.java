package net.spell_engine.api.item.weapon;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ToolMaterial;
import net.minecraft.item.ToolMaterials;
import net.minecraft.recipe.Ingredient;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.Identifier;
import net.minecraft.util.Lazy;
import net.spell_engine.Platform;
import net.spell_engine.api.item.ConfigurableAttributes;
import net.spell_engine.api.item.ItemConfig;
import net.spell_power.api.attributes.SpellAttributes;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

public class Weapon {
    public static final class Entry {
        private final String namespace;
        private final String name;
        private final CustomMaterial material;
        private final Item item;
        private final ItemConfig.Weapon defaults;
        private @Nullable String requiredMod;

        public Entry(String namespace, String name, CustomMaterial material, Item item, ItemConfig.Weapon defaults, @Nullable String requiredMod) {
            this.namespace = namespace;
            this.name = name;
            this.material = material;
            this.item = item;
            this.defaults = defaults;
            this.requiredMod = requiredMod;
        }

        public Identifier id() {
            return new Identifier(namespace, name);
        }

        public Entry attribute(ItemConfig.SpellAttribute attribute) {
            defaults.add(attribute);
            return this;
        }

        public Entry requires(String modName) {
            this.requiredMod = modName;
            return this;
        }

        public boolean isRequiredModInstalled() {
            if (requiredMod == null || requiredMod.isEmpty()) {
                return true;
            }
            return Platform.isModLoaded(requiredMod);
        }

        public String name() {
            return name;
        }

        public CustomMaterial material() {
            return material;
        }

        public Item item() {
            return item;
        }

        public ItemConfig.Weapon defaults() {
            return defaults;
        }

        public @Nullable String requiredMod() {
            return requiredMod;
        }
    }

    // MARK: Material

    public static class CustomMaterial implements ToolMaterial {
        public static CustomMaterial matching(ToolMaterials vanillaMaterial, Supplier<Ingredient> repairIngredient) {
            var material = new CustomMaterial();
            material.miningLevel = vanillaMaterial.getMiningLevel();
            material.durability = vanillaMaterial.getDurability();
            material.miningSpeed = vanillaMaterial.getMiningSpeedMultiplier();
            material.enchantability = vanillaMaterial.getEnchantability();
            material.ingredient = new Lazy(repairIngredient);
            return material;
        }

        private int miningLevel = 0;
        private int durability = 0;
        private float miningSpeed = 0;
        private int enchantability = 0;
        private Lazy<Ingredient> ingredient = null;

        @Override
        public int getDurability() {
            return durability;
        }

        @Override
        public float getMiningSpeedMultiplier() {
            return miningSpeed;
        }

        @Override
        public float getAttackDamage() {
            return 0;
        }

        @Override
        public int getMiningLevel() {
            return miningLevel;
        }

        @Override
        public int getEnchantability() {
            return enchantability;
        }

        @Override
        public Ingredient getRepairIngredient() {
            return (Ingredient)this.ingredient.get();
        }
    }

    // MARK: Registration

    public static void register(Map<String, ItemConfig.Weapon> configs, List<Entry> entries, RegistryKey<ItemGroup> itemGroupKey) {
        for(var entry: entries) {
            var config = configs.get(entry.name);
            if (config == null) {
                config = entry.defaults;
                configs.put(entry.name(), config);
            }
            if (!entry.isRequiredModInstalled()) { continue; }
            var item = entry.item();
            ((ConfigurableAttributes)item).setAttributes(attributesFrom(config));
            Registry.register(Registries.ITEM, entry.id(), item);
        }
        ItemGroupEvents.modifyEntriesEvent(itemGroupKey).register(content -> {
            for(var entry: entries) {
                content.add(entry.item());
            }
        });
    }

    private static Multimap<EntityAttribute, EntityAttributeModifier> attributesFrom(ItemConfig.Weapon config) {
        ImmutableMultimap.Builder<EntityAttribute, EntityAttributeModifier> builder = ImmutableMultimap.builder();
        builder.put(EntityAttributes.GENERIC_ATTACK_DAMAGE,
                new EntityAttributeModifier(
                        ItemAccessor.ATTACK_DAMAGE_MODIFIER_ID(),
                        "Weapon modifier",
                        config.attack_damage,
                        EntityAttributeModifier.Operation.ADDITION));
        builder.put(EntityAttributes.GENERIC_ATTACK_SPEED,
                new EntityAttributeModifier(
                        ItemAccessor.ATTACK_SPEED_MODIFIER_ID(),
                        "Weapon modifier",
                        config.attack_speed,
                        EntityAttributeModifier.Operation.ADDITION));
        for(var attribute: config.spell_attributes) {
            if (attribute.value == 0) {
                continue;
            }
            try {
                var entityAttribute = SpellAttributes.all.get(attribute.name).attribute;
                builder.put(entityAttribute,
                        new EntityAttributeModifier(
                                entityAttribute.weaponUUID,
                                "Weapon modifier",
                                attribute.value,
                                attribute.operation));
            } catch (Exception e) {
                System.err.println("Failed to add item attribute modifier: " + e.getMessage());
            }
        }
        return builder.build();
    }

    private static abstract class ItemAccessor extends Item {
        public ItemAccessor(Settings settings) { super(settings); }
        public static UUID ATTACK_DAMAGE_MODIFIER_ID() { return ATTACK_DAMAGE_MODIFIER_ID; }
        public static UUID ATTACK_SPEED_MODIFIER_ID() { return ATTACK_SPEED_MODIFIER_ID; }
    }
}
