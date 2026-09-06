package net.spell_engine.api.spell;
import net.spell_engine.Platform;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Identifier;
import net.spell_engine.utils.AttributeModifierUtil;
import net.spell_power.SpellPowerMod;
import net.spell_power.api.SpellSchool;
import net.spell_power.api.SpellSchools;
import org.jetbrains.annotations.Nullable;

public class ExternalSpellSchools {
    private static final RegistryEntry<EntityAttribute> ATTACK_DAMAGE_ENTRY = Registries.ATTRIBUTE.getEntry(EntityAttributes.GENERIC_ATTACK_DAMAGE);
    private static final RegistryEntry<EntityAttribute> ATTACK_SPEED_ENTRY = Registries.ATTRIBUTE.getEntry(EntityAttributes.GENERIC_ATTACK_SPEED);

    /// The off-hand weapon's flat attack damage bonus, scaled by the wielder's multiplicative attack
    /// damage modifiers. Held item modifiers are only contributed to the attribute container by the
    /// main hand, so an off-hand weapon is invisible to `GENERIC_ATTACK_DAMAGE` and its bonus has to
    /// be read off the stack, then scaled the same way the attribute would have scaled it.
    private static double offHandAttackDamage(LivingEntity entity) {
        var offHandStack = entity.getOffHandStack();
        var weaponDamage = entity.getAttributeBaseValue(EntityAttributes.GENERIC_ATTACK_DAMAGE)
                + AttributeModifierUtil.flatBonusFrom(offHandStack, ATTACK_DAMAGE_ENTRY);
        if (weaponDamage == 0) {
            return 0;
        }
        return weaponDamage * AttributeModifierUtil.multipliersOf(ATTACK_DAMAGE_ENTRY, entity);
    }

    // MARK: Ranged Weapon API bridge
    //
    // RWA only exists as a Fabric artifact on 1.20.1 and is compile-only in `common`, so it is never referenced
    // by class here: its attributes (`ranged_weapon:damage`, `ranged_weapon:haste`, registered by RWA's own
    // `EntityAttributes` <clinit> mixin, base 100 for haste) are resolved through the attribute registry by id.
    // Without RWA (e.g. any Forge runtime) vanilla attack damage is the fallback.

    private static final String RANGED_WEAPON_API_MOD_ID = "ranged_weapon_api";
    private static final Identifier RWA_DAMAGE_ID = new Identifier("ranged_weapon", "damage");
    private static final Identifier RWA_HASTE_ID = new Identifier("ranged_weapon", "haste");
    private static final double RWA_HASTE_BASE = 100.0;

    private static boolean rangedWeaponApiLoaded() {
        return Platform.util().isModLoaded(RANGED_WEAPON_API_MOD_ID);
    }

    @Nullable
    private static EntityAttribute rangedWeaponApiAttribute(Identifier id) {
        if (!rangedWeaponApiLoaded()) { return null; }
        return Registries.ATTRIBUTE.get(id);
    }

    private static RegistryEntry<EntityAttribute> rangedDamageAttribute() {
        var rwaDamage = rangedWeaponApiAttribute(RWA_DAMAGE_ID);
        var attribute = rwaDamage != null
                ? rwaDamage
                : EntityAttributes.GENERIC_ATTACK_DAMAGE; // Vanilla attack damage used as fallback
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
            return query.entity().getAttributeValue(EntityAttributes.GENERIC_ATTACK_DAMAGE);
        });
        PHYSICAL_MELEE.addSource(SpellSchool.Trait.HASTE, SpellSchool.Apply.ADD, query -> {
            return AttributeModifierUtil.multipliersOf(ATTACK_SPEED_ENTRY, query.entity()) - 1.0;
        });
        SpellSchools.configureSpellHaste(PHYSICAL_MELEE);
        SpellSchools.register(PHYSICAL_MELEE);

        // Same power as PHYSICAL_MELEE, plus the off-hand weapon. The attack damage attribute only
        // accounts for the main hand, since vanilla weapons declare their modifiers for MAINHAND.
        PHYSICAL_MELEE_DUAL.addSource(SpellSchool.Trait.POWER, SpellSchool.Apply.ADD, query -> {
            return query.entity().getAttributeValue(EntityAttributes.GENERIC_ATTACK_DAMAGE);
        });
        PHYSICAL_MELEE_DUAL.addSource(SpellSchool.Trait.POWER, SpellSchool.Apply.ADD, query -> {
            return offHandAttackDamage(query.entity());
        });
        PHYSICAL_MELEE_DUAL.addSource(SpellSchool.Trait.HASTE, SpellSchool.Apply.ADD, query -> {
            return AttributeModifierUtil.multipliersOf(ATTACK_SPEED_ENTRY, query.entity()) - 1.0;
        });
        SpellSchools.configureSpellHaste(PHYSICAL_MELEE_DUAL);
        SpellSchools.register(PHYSICAL_MELEE_DUAL);

        var rwaDamage = rangedWeaponApiAttribute(RWA_DAMAGE_ID);
        var rwaHaste = rangedWeaponApiAttribute(RWA_HASTE_ID);
        if (rwaDamage != null && rwaHaste != null) {
            PHYSICAL_RANGED.addSource(SpellSchool.Trait.POWER, SpellSchool.Apply.ADD, query -> {
                return query.entity().getAttributeValue(rwaDamage);
            });
            PHYSICAL_RANGED.addSource(SpellSchool.Trait.HASTE, SpellSchool.Apply.ADD, query -> {
                var haste = query.entity().getAttributeValue(rwaHaste); // 110
                var rate = haste / RWA_HASTE_BASE;    // For example: 110/100 = 1.1
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
