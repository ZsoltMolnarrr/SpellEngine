# Content Development Guidelines

## Principles

### 1. Quality over quantity

Whatever content or mechanic you put into the game, people will spend their hardly allocated time with it. They deserve the best we provide with reasonable effort.

Examples: 
- Newly added spells should have some unique mechanic or trait. Spells those are just recolored versions of already existing ones, are discouraged.
- When adding armor sets to the game, 1 armor set with high quality artwork is much preferred over 3 armor sets with mediocre artwork.

### 2. Freedom of choice

Do not create hard requirements for equipping/using items/spells. As these:
- Are most often counterintuitive, difficult to explain the limitations to players. 
- Don't fit the vanilla flow of the game, where there no such requirements. Even Minecraft Dungeons allows players to equip whatever combination gear they want.

Instead of hard locking equipment for certain classes, adjust attributes of items in a way that is *most optimal* for particular builds, while less effective for others.

### 3. Obsolescence

__Classes__
All playable classes are roughly equal in terms of usefulness, with different strengths and weakness to complement each other. 
When comparing two classes, there should be no class that is superior in most circumstances.

Example:
- Warriors are heavily armored, dealing medicore damage in melee, able to withstand many attacks before going down 
- Wizards are lightly armored, dealing high magic damage (piercing armor), unable to withstand more than a few attacks

__Equipment__
Armors and Weapons are organized into tiers. (Similar to how vanilla Armors and Swords)
Newly added equipment into a particular tier:
- Must not make any other piece within the same tier obsolete
  - Negative example: creating some new armor piece that is always better for warriors than existing warrior equipment
- Must not disrupt class balance
  - Negative example: creating a heavily armored magic armor set, that cancels out the weaknesses of Wizards (turning them into an always winning class)


# Balancing

## Attributes

### Armor
Instead of an ever-stackable bonus, armor treated as what determines the weight ("how heavily armored a class it is"). 
Classes (and their spells) are designed with their armor weight in mind, hence armor bonus across various armor tiers should be relatively unchanged, to avoid erasing weaknesses of certain classes.
(This design is similar to World of Warcraft, where various of armor tiers of a class have only marginal differences in armor bonus)

Armor weight classes:
- Light: Cloth (8-10x :shield:)
  - Example: Wizards, Priests
- Medium: Leather/Mail (10-14x :shield:)
  - Example: Archers, Rogues
- Heavy: Plate (16-20x :shield:)
  - Example: Paladins, Warriors

  ### Attack Damage

The logic below applies to both (melee) Attack Damage, and Ranged Attack Damage.

Bonus Assignment recommendation:
- Weapons: Flat bonus (`+5 Attack Damage`)
- Armor, Trinkets: Percentage bonus (`%5 Attack Damage`)

:warning: Do not apply flat bonus damage on other than weapons, as this would break the linearity of damage scaling.
(For example: `+1 Attack Damage` on armor piece would have a greater DPS increase for fast weapons, compared to slow weapons. While this might be technically desirable, it is hardly intuitive for most players.)

### Spell Power

For gameplay consistency with melee damage, Spell Power uses the same bonus assignment as Attack Damage.
This also allows bonus providers (such as Trinkets) to have equal bonuses for melee and magic users.
- Weapons: Flat bonus (`+5 Fire Spell Power`)
- Armor, Trinkets: Percentage bonus (`%5 Fire Spell Power`)

:warning:  Applying flat bonuses onto armor may be multiplied by enchantments and trinkets and other factors can make them easily overpowered. Hence it is not recommended.

(Exception example: Paladin armor gives flat spell power bonus, as paladins are meant to use any melee weapon without Spell Power flat bonus.)

## Spell DPS Configuration

### How damage resolves

A damaging impact deals:

```
damage = coefficient × Spell Power(school) × crit
```

`Spell Power(school)` is simply the value of that school's Spell Power attribute (e.g. `spell_power:fire`) on the caster. The **coefficient** — the only damage knob that lives in the spell definition — is nothing more than a multiplier on that attribute. This is why balancing is done in coefficient-space: one number scales the whole spell against the caster's gear.

> If a `damage` impact omits `spell_power_coefficient`, it defaults to `1.0` (full Spell Power). Always set it explicitly.

### The DPS yardstick

Once attributes are set, each spell's coefficient is tuned against a common yardstick:

```
DPS = coefficient / cast time
```

This normalizes spells of different cast speeds onto one scale, so a slow, hard-hitting spell and a fast, weak one can be compared fairly.

> **Channeled spells are the exception.** Their coefficient is already a *per-second* value: total damage = `coefficient × Spell Power × channel duration`, so **DPS equals the coefficient directly**, regardless of the channel's tick count. The tick count only controls how the damage is *chunked* (fewer ticks = larger, less frequent hits) — it does **not** change total throughput. So for a channeled spell, read `DPS = coefficient`.

Guidelines:

- **DPS scales with tier.** A school's baseline single-target DPS climbs from ~`0.5` at T0 up to ~`1.0` at its top tier.
- **Utility is paid for with damage.** Any spell carrying a rider — DoT, slow, snare, pierce, AoE, knockback — takes a lower coefficient to offset the added value. The stronger the effect, the deeper the cut (see Frost Nova).
- **AoE gets a small budget bump.** Area spells rarely land full damage on every target, so their nominal DPS may sit slightly above the single-target line to compensate for falloff.

Reference values across the Wizard schools:

| School | Tier | Spell | Coeff | Cast (s) | DPS | Notes |
|---|---|---|---|---|---|---|
| Arcane | T0 | Bolt | 0.6 | 1.0 | 0.60 | baseline |
| Arcane | T1 | Missile | 0.6 | 0.75 | 0.80 | fast cast |
| Arcane | T2 | Blast | 0.9 | 1.5 | 0.60 | low per-cast; stacks Arcane Charge to ramp power |
| Arcane | T2 | Beam | 1.0 | 1.0 | 1.00 | channeled |
| Fire | T0 | Scorch | 0.6 | 1.2 | 0.50 | + fire DoT |
| Fire | T1 | Ball | 0.9 | 1.5 | 0.60 | |
| Fire | T2 | Breath | 0.8 | 1.0 | 0.80 | AoE — budgeted up to ~0.9 for falloff |
| Fire | T3 | Meteor | 1.0 | 1.0 | 1.00 | |
| Frost | T0 | Shard | 0.5 | 1.0 | 0.50 | pierces |
| Frost | T1 | Bolt | 0.8 | 1.2 | 0.67 | cut to ~0.66 to pay for shatter + slow |
| Frost | T2 | Nova | 0.2 | 1.0 | 0.20 | heavily cut for its snare effect |
| Frost | T3 | Shield | — | — | — | utility, no damage |

## Case study #1 : Balancing Frost Death Knights

Frost Death Knight is designed to be a class that is:
- heavily armor
- dealing hybrid damage (some physical, some spell)

To fit into the RPG Series, we first need to consider what other equipment it would clash with. These are:
- Warrior Equipment
- Frost Wizard Equipment

Essentially players should be allowed to use gear interchangably, but the main goal is to ensure the most **optimal** selection of gear all classes is what that was designed for them.

Lets see our boundaries:
- Frost Wizard T1 armor piece grants: +20% Spell Power, +1-2 Armor
- Warrior T1 armor piece grants: +4% Attack Damage, +3-5 Armor

We need to balance with all relevant attributes in mind. Lets see what kind of mistakes we could make, for our DK T1 armor piece:
- Armor should be, +3-5 as we determine Armor Weight first.
- +20% Spell Power with +3-5 Armor
  - This would cause wizards to choose DK armor, instead their own, to gain more armor and loose nothing
- +5% Attack Damage with +3-5 Armor 
  - This would cause warriors to choose DK armor, instead their own, to gain more damage and loose nothing
The solution is half way in between:
- +10% Spell Power, +2% Attack Damage, +3-5 Armor

After these attributes are defined. Spell damage coefficients need to be carefully adjusted to deal the expected amount of damage.

This kind of balancing also allows that DKs can gear up using miscellaneous Dungeon Reward pieces:
- Vanilla Armor for survivability
- Frost Wizard Armor for Frost Spell Power
- Warrior pieces for melee damage
- DK armor of course
But the most optimal equipment is still going to be their own set.