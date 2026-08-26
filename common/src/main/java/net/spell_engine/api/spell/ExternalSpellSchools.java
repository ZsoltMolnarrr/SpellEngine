package net.spell_engine.api.spell;
import net.spell_engine.Platform;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.rpg_foundation.ranged_weapon.api.EntityAttributes_RangedWeapon;
import net.spell_engine.utils.AttributeModifierUtil;
import net.spell_power.SpellPowerMod;
import net.spell_power.api.SpellSchool;
import net.spell_power.api.SpellSchools;

public class ExternalSpellSchools {
    /// The off-hand weapon's flat attack damage bonus, scaled by the wielder's multiplicative attack
    /// damage modifiers. Held item modifiers are only contributed to the attribute container by the
    /// main hand, so an off-hand weapon is invisible to `GENERIC_ATTACK_DAMAGE` and its bonus has to
    /// be read off the stack, then scaled the same way the attribute would have scaled it.
    private static double offHandAttackDamage(LivingEntity entity) {
        var offHandStack = entity.getOffhandItem();
        var weaponDamage = entity.getAttributeBaseValue(Attributes.ATTACK_DAMAGE)
                + AttributeModifierUtil.flatBonusFrom(offHandStack, Attributes.ATTACK_DAMAGE);
        if (weaponDamage == 0) {
            return 0;
        }
        return weaponDamage * AttributeModifierUtil.multipliersOf(Attributes.ATTACK_DAMAGE, entity);
    }

    private static Holder<Attribute> rangedDamageAttribute() {
        if (Platform.util().isModLoaded("ranged_weapon_api")) {
            return EntityAttributes_RangedWeapon.DAMAGE.entry;
        } else {
            return Attributes.ATTACK_DAMAGE; // Vanilla attack damage used as fallback
        }
    }

    public static final SpellSchool PHYSICAL_MELEE = new SpellSchool(SpellSchool.Archetype.MELEE,
            Identifier.fromNamespaceAndPath(SpellPowerMod.ID, "physical_melee"),
            0xb3b3b3,
            DamageTypes.PLAYER_ATTACK,
            Attributes.ATTACK_DAMAGE);
    /// Behaves as {@link #PHYSICAL_MELEE}, except its power also counts the off-hand weapon.
    /// For spells that strike with both held weapons.
    public static final SpellSchool PHYSICAL_MELEE_DUAL = new SpellSchool(SpellSchool.Archetype.MELEE,
            Identifier.fromNamespaceAndPath(SpellPowerMod.ID, "physical_melee_dual"),
            0xb3b3b3,
            DamageTypes.PLAYER_ATTACK,
            Attributes.ATTACK_DAMAGE);
    public static final SpellSchool PHYSICAL_RANGED = new SpellSchool(SpellSchool.Archetype.ARCHERY,
            Identifier.fromNamespaceAndPath(SpellPowerMod.ID, "physical_ranged"),
            0x805e4d,
            DamageTypes.ARROW,
            rangedDamageAttribute() // Extra compatibility for the absence of `ranged_weapon_api`
    );
    public static final SpellSchool DEFENSE = new SpellSchool(SpellSchool.Archetype.MELEE,
            Identifier.fromNamespaceAndPath(SpellPowerMod.ID, "defense"),
            0xcccccc,
            DamageTypes.PLAYER_ATTACK,
            Attributes.ARMOR);
    public static final SpellSchool HEALTH = new SpellSchool(SpellSchool.Archetype.MELEE,
            Identifier.fromNamespaceAndPath(SpellPowerMod.ID, "health"),
            0xcc0000,
            DamageTypes.PLAYER_ATTACK,
            Attributes.MAX_HEALTH);

    private static boolean initialized = false;
    public static void init() {
        if (initialized) { return; }

        // Sync attack power to client so physical attack damage spells can be estimated.
        // Probably several other mods perform this operation, but its no problem.
        Attributes.ATTACK_DAMAGE.value().setSyncable(true);
        PHYSICAL_MELEE.addSource(SpellSchool.Trait.POWER, SpellSchool.Apply.ADD, query -> {
            return query.entity().getAttributeValue(Attributes.ATTACK_DAMAGE);
        });
        PHYSICAL_MELEE.addSource(SpellSchool.Trait.HASTE, SpellSchool.Apply.ADD, query -> {
            return AttributeModifierUtil.multipliersOf(Attributes.ATTACK_SPEED, query.entity()) - 1.0;
        });
        SpellSchools.configureSpellHaste(PHYSICAL_MELEE);
        SpellSchools.register(PHYSICAL_MELEE);

        // Same power as PHYSICAL_MELEE, plus the off-hand weapon. The attack damage attribute only
        // accounts for the main hand, since vanilla weapons declare their modifiers for MAINHAND.
        PHYSICAL_MELEE_DUAL.addSource(SpellSchool.Trait.POWER, SpellSchool.Apply.ADD, query -> {
            return query.entity().getAttributeValue(Attributes.ATTACK_DAMAGE);
        });
        PHYSICAL_MELEE_DUAL.addSource(SpellSchool.Trait.POWER, SpellSchool.Apply.ADD, query -> {
            return offHandAttackDamage(query.entity());
        });
        PHYSICAL_MELEE_DUAL.addSource(SpellSchool.Trait.HASTE, SpellSchool.Apply.ADD, query -> {
            return AttributeModifierUtil.multipliersOf(Attributes.ATTACK_SPEED, query.entity()) - 1.0;
        });
        SpellSchools.configureSpellHaste(PHYSICAL_MELEE_DUAL);
        SpellSchools.register(PHYSICAL_MELEE_DUAL);

        if (Platform.util().isModLoaded("ranged_weapon_api")) {
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
            return query.entity().getAttributeValue(Attributes.ARMOR);
        });
        SpellSchools.register(DEFENSE);

        HEALTH.addSource(SpellSchool.Trait.POWER, SpellSchool.Apply.ADD, query -> {
            return query.entity().getAttributeValue(Attributes.MAX_HEALTH);
        });
        SpellSchools.register(HEALTH);

        initialized = true;
    }
}
