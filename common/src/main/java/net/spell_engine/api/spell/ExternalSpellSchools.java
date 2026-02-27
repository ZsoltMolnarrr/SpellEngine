package net.spell_engine.api.spell;

import net.fabric_extras.ranged_weapon.api.EntityAttributes_RangedWeapon;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Identifier;
import net.spell_engine.utils.AttributeModifierUtil;
import net.spell_power.SpellPowerMod;
import net.spell_power.api.SpellSchool;
import net.spell_power.api.SpellSchools;

public class ExternalSpellSchools {
    private static RegistryEntry<EntityAttribute> rangedDamageAttribute() {
        if (FabricLoader.getInstance().isModLoaded("ranged_weapon_api")) {
            return EntityAttributes_RangedWeapon.DAMAGE.entry;
        } else {
            return EntityAttributes.GENERIC_ATTACK_DAMAGE; // Vanilla attack damage used as fallback
        }
    }

    public static final SpellSchool PHYSICAL_MELEE = new SpellSchool(SpellSchool.Archetype.MELEE,
            Identifier.of(SpellPowerMod.ID, "physical_melee"),
            0xb3b3b3,
            DamageTypes.PLAYER_ATTACK,
            EntityAttributes.GENERIC_ATTACK_DAMAGE);
    public static final SpellSchool PHYSICAL_RANGED = new SpellSchool(SpellSchool.Archetype.ARCHERY,
            Identifier.of(SpellPowerMod.ID, "physical_ranged"),
            0x805e4d,
            DamageTypes.ARROW,
            rangedDamageAttribute() // Extra compatibility for the absence of `ranged_weapon_api`
    );
    public static final SpellSchool DEFENSE = new SpellSchool(SpellSchool.Archetype.MELEE,
            Identifier.of(SpellPowerMod.ID, "defense"),
            0xcccccc,
            DamageTypes.PLAYER_ATTACK,
            EntityAttributes.GENERIC_ARMOR);
    public static final SpellSchool HEALTH = new SpellSchool(SpellSchool.Archetype.MELEE,
            Identifier.of(SpellPowerMod.ID, "health"),
            0xcc0000,
            DamageTypes.PLAYER_ATTACK,
            EntityAttributes.GENERIC_MAX_HEALTH);

    private static boolean initialized = false;
    public static void init() {
        if (initialized) { return; }

        // Sync attack power to client so physical attack damage spells can be estimated.
        // Probably several other mods perform this operation, but its no problem.
        EntityAttributes.GENERIC_ATTACK_DAMAGE.value().setTracked(true);
        PHYSICAL_MELEE.addSource(SpellSchool.Trait.POWER, SpellSchool.Apply.ADD, query -> {
            return query.entity().getAttributeValue(EntityAttributes.GENERIC_ATTACK_DAMAGE);
        });
        PHYSICAL_MELEE.addSource(SpellSchool.Trait.HASTE, SpellSchool.Apply.ADD, query -> {
            return AttributeModifierUtil.multipliersOf(EntityAttributes.GENERIC_ATTACK_SPEED, query.entity()) - 1.0;
        });
        SpellSchools.configureSpellHaste(PHYSICAL_MELEE);
        SpellSchools.register(PHYSICAL_MELEE);

        if (FabricLoader.getInstance().isModLoaded("ranged_weapon_api")) {
            PHYSICAL_RANGED.addSource(SpellSchool.Trait.POWER, SpellSchool.Apply.ADD, query -> {
                return query.entity().getAttributeValue(EntityAttributes_RangedWeapon.DAMAGE.entry);
            });
            PHYSICAL_RANGED.addSource(SpellSchool.Trait.HASTE, SpellSchool.Apply.ADD, query -> {
                var haste = query.entity().getAttributeValue(EntityAttributes_RangedWeapon.HASTE.entry); // 110
                var rate = EntityAttributes_RangedWeapon.HASTE.asMultiplier(haste);    // For example: 110/100 = 1.1
                return rate - 1;  // 0.1
            });
        }
        SpellSchools.register(PHYSICAL_RANGED);

        DEFENSE.addSource(SpellSchool.Trait.POWER, SpellSchool.Apply.ADD, query -> {
            return query.entity().getAttributeValue(EntityAttributes.GENERIC_ARMOR);
        });
        SpellSchools.register(DEFENSE);

        HEALTH.addSource(SpellSchool.Trait.POWER, SpellSchool.Apply.ADD, query -> {
            return query.entity().getAttributeValue(EntityAttributes.GENERIC_MAX_HEALTH);
        });
        SpellSchools.register(HEALTH);

        initialized = true;
    }
}
