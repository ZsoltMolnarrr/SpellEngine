package net.spell_engine.api.spell;
import net.spell_engine.Platform;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Identifier;
import net.fabric_extras.ranged_weapon.api.EntityAttributes_RangedWeapon;
import net.spell_engine.utils.AttributeModifierUtil;
import net.spell_power.SpellPowerMod;
import net.spell_power.api.SpellSchool;
import net.spell_power.api.SpellSchools;

public class ExternalSpellSchools {
    private static final RegistryEntry<EntityAttribute> ATTACK_DAMAGE_ENTRY = Registries.ATTRIBUTE.getEntry(EntityAttributes.GENERIC_ATTACK_DAMAGE);
    private static final RegistryEntry<EntityAttribute> ATTACK_SPEED_ENTRY = Registries.ATTRIBUTE.getEntry(EntityAttributes.GENERIC_ATTACK_SPEED);

    /// Null-safe read of `GENERIC_ATTACK_DAMAGE`.
    ///
    /// On 1.20.1 that attribute is registered by `HostileEntity.createHostileAttributes()` — **not** by
    /// `createLivingAttributes()` or `createMobAttributes()` — while these school sources take an
    /// arbitrary `LivingEntity` (a spell's caster, or an arrow's owner via `SpellImpacts.arrowImpact`).
    /// `LivingEntity#getAttributeValue` *throws* for an unregistered attribute, so a villager, snow
    /// golem or any animal casting a PHYSICAL_MELEE-school spell crashed the server (hostiles and iron
    /// golems are fine — they add the attribute themselves). Absent ⇒ contributes no melee power,
    /// which is the correct neutral for an entity that has no attack-damage attribute at all.
    private static double attackDamageOf(LivingEntity entity) {
        return entity.getAttributes().hasAttribute(EntityAttributes.GENERIC_ATTACK_DAMAGE)
                ? entity.getAttributeValue(EntityAttributes.GENERIC_ATTACK_DAMAGE)
                : 0;
    }

    /// The off-hand weapon's flat attack damage bonus, scaled by the wielder's multiplicative attack
    /// damage modifiers. Held item modifiers are only contributed to the attribute container by the
    /// main hand, so an off-hand weapon is invisible to `GENERIC_ATTACK_DAMAGE` and its bonus has to
    /// be read off the stack, then scaled the same way the attribute would have scaled it.
    private static double offHandAttackDamage(LivingEntity entity) {
        var offHandStack = entity.getOffHandStack();
        var base = entity.getAttributes().hasAttribute(EntityAttributes.GENERIC_ATTACK_DAMAGE)
                ? entity.getAttributeBaseValue(EntityAttributes.GENERIC_ATTACK_DAMAGE)
                : 0;
        var weaponDamage = base
                + AttributeModifierUtil.flatBonusFrom(offHandStack, ATTACK_DAMAGE_ENTRY);
        if (weaponDamage == 0) {
            return 0;
        }
        return weaponDamage * AttributeModifierUtil.multipliersOf(ATTACK_DAMAGE_ENTRY, entity);
    }

    // MARK: Ranged Weapon API bridge
    //
    // RangedWeaponAPI (2.3.4.001+1.20.1, Fabric + Forge) is compile-only and optional at runtime. Its classes are
    // only touched behind `isModLoaded("ranged_weapon_api")`, inside the nested holder below, so a runtime without
    // RWA never resolves them. The bridge reads the *attribute objects* off `EntityAttributes_RangedWeapon`
    // rather than looking the ids up in the registry: on Forge this runs from the mod constructor, before any
    // `RegisterEvent`, when RWA's attributes are not registered yet — the objects already exist and are the
    // same instances RWA registers later in its ATTRIBUTE window. Without RWA vanilla attack damage is the fallback.

    private static final String RANGED_WEAPON_API_MOD_ID = "ranged_weapon_api";

    private static boolean rangedWeaponApiLoaded() {
        return Platform.util().isModLoaded(RANGED_WEAPON_API_MOD_ID);
    }

    /// The only place RWA types are named. Never load this class unless {@link #rangedWeaponApiLoaded()}.
    private static final class RangedWeaponApiBridge {
        static EntityAttribute damage() {
            return EntityAttributes_RangedWeapon.DAMAGE.attribute;
        }
        static EntityAttribute haste() {
            return EntityAttributes_RangedWeapon.HASTE.attribute;
        }
        static double hasteMultiplier(double hasteValue) {
            return EntityAttributes_RangedWeapon.HASTE.asMultiplier(hasteValue); // For example: 110/100 = 1.1
        }
    }

    private static RegistryEntry<EntityAttribute> rangedDamageAttribute() {
        var attribute = rangedWeaponApiLoaded()
                ? RangedWeaponApiBridge.damage()
                : EntityAttributes.GENERIC_ATTACK_DAMAGE; // Vanilla attack damage used as fallback
        // Forge: a direct entry until RWA's ATTRIBUTE window runs; every consumer only dereferences `value()`.
        return Registries.ATTRIBUTE.getEntry(attribute);
    }

    public static final SpellSchool PHYSICAL_MELEE = new SpellSchool(SpellSchool.Archetype.MELEE,
            new Identifier(SpellPowerMod.ID, "physical_melee"),
            0xb3b3b3,
            DamageTypes.PLAYER_ATTACK,
            Registries.ATTRIBUTE.getEntry(EntityAttributes.GENERIC_ATTACK_DAMAGE));
    /// Behaves as {@link #PHYSICAL_MELEE}, except its power also counts the off-hand weapon.
    /// For spells that strike with both held weapons.
    public static final SpellSchool PHYSICAL_MELEE_DUAL = new SpellSchool(SpellSchool.Archetype.MELEE,
            new Identifier(SpellPowerMod.ID, "physical_melee_dual"),
            0xb3b3b3,
            DamageTypes.PLAYER_ATTACK,
            Registries.ATTRIBUTE.getEntry(EntityAttributes.GENERIC_ATTACK_DAMAGE));
    public static final SpellSchool PHYSICAL_RANGED = new SpellSchool(SpellSchool.Archetype.ARCHERY,
            new Identifier(SpellPowerMod.ID, "physical_ranged"),
            0x805e4d,
            DamageTypes.ARROW,
            rangedDamageAttribute() // Extra compatibility for the absence of `ranged_weapon_api`
    );
    public static final SpellSchool DEFENSE = new SpellSchool(SpellSchool.Archetype.MELEE,
            new Identifier(SpellPowerMod.ID, "defense"),
            0xcccccc,
            DamageTypes.PLAYER_ATTACK,
            Registries.ATTRIBUTE.getEntry(EntityAttributes.GENERIC_ARMOR));
    public static final SpellSchool HEALTH = new SpellSchool(SpellSchool.Archetype.MELEE,
            new Identifier(SpellPowerMod.ID, "health"),
            0xcc0000,
            DamageTypes.PLAYER_ATTACK,
            Registries.ATTRIBUTE.getEntry(EntityAttributes.GENERIC_MAX_HEALTH));

    private static boolean initialized = false;
    public static void init() {
        if (initialized) { return; }

        // Sync attack power to client so physical attack damage spells can be estimated.
        // Probably several other mods perform this operation, but its no problem.
        EntityAttributes.GENERIC_ATTACK_DAMAGE.setTracked(true);
        PHYSICAL_MELEE.addSource(SpellSchool.Trait.POWER, SpellSchool.Apply.ADD, query -> {
            return attackDamageOf(query.entity());
        });
        PHYSICAL_MELEE.addSource(SpellSchool.Trait.HASTE, SpellSchool.Apply.ADD, query -> {
            return AttributeModifierUtil.multipliersOf(ATTACK_SPEED_ENTRY, query.entity()) - 1.0;
        });
        SpellSchools.configureSpellHaste(PHYSICAL_MELEE);
        SpellSchools.register(PHYSICAL_MELEE);

        // Same power as PHYSICAL_MELEE, plus the off-hand weapon. The attack damage attribute only
        // accounts for the main hand, since vanilla weapons declare their modifiers for MAINHAND.
        PHYSICAL_MELEE_DUAL.addSource(SpellSchool.Trait.POWER, SpellSchool.Apply.ADD, query -> {
            return attackDamageOf(query.entity());
        });
        PHYSICAL_MELEE_DUAL.addSource(SpellSchool.Trait.POWER, SpellSchool.Apply.ADD, query -> {
            return offHandAttackDamage(query.entity());
        });
        PHYSICAL_MELEE_DUAL.addSource(SpellSchool.Trait.HASTE, SpellSchool.Apply.ADD, query -> {
            return AttributeModifierUtil.multipliersOf(ATTACK_SPEED_ENTRY, query.entity()) - 1.0;
        });
        SpellSchools.configureSpellHaste(PHYSICAL_MELEE_DUAL);
        SpellSchools.register(PHYSICAL_MELEE_DUAL);

        if (rangedWeaponApiLoaded()) {
            var rwaDamage = RangedWeaponApiBridge.damage();
            var rwaHaste = RangedWeaponApiBridge.haste();
            PHYSICAL_RANGED.addSource(SpellSchool.Trait.POWER, SpellSchool.Apply.ADD, query -> {
                return query.entity().getAttributeValue(rwaDamage);
            });
            PHYSICAL_RANGED.addSource(SpellSchool.Trait.HASTE, SpellSchool.Apply.ADD, query -> {
                var haste = query.entity().getAttributeValue(rwaHaste); // 110
                var rate = RangedWeaponApiBridge.hasteMultiplier(haste);    // For example: 110/100 = 1.1
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
