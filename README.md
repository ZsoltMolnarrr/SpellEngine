![Title](.github/title.png)

<div align="center">

<a href="">![Java 17](https://img.shields.io/badge/Java%2017-ee9258?logo=coffeescript&logoColor=ffffff&labelColor=606060&style=flat-square)</a>
<a href="">![Environment: Client & Server](https://img.shields.io/badge/environment-Client%20&%20Server-1976d2?style=flat-square)</a>
<a href="">[![Discord](https://img.shields.io/discord/973561601519149057.svg?label=&logo=discord&logoColor=ffffff&color=7389D8&labelColor=6A7EC2&style=flat-square)](https://discord.gg/KN9b3pjFTM)</a>

</div>

## 🪄️ Features

Data driven API
- 🗡️ Spells can be assigned to any weapon (data driven with automatic weapon compatibility)
- 🔮 Spells deal damage based on [Spell Power](https://github.com/ZsoltMolnarrr/SpellPower) entity attributes
- ✍️ Spells defined in JSON format with hot-reloading and network synchronization
- 📦 Spell Container System with proxy mode, equipment slots, and dynamic spell resolution
- 🔄 Universal pattern matching for tags, regex, and exact matches across all spell properties
- 🛠️ Programmatic spell generation with SpellBuilder and SpellGenerator for mod developers
- ⚙️ Spells have a comprehensive set of mechanical behaviours:
    - **Spell Types**: Active (casted), Passive (triggered), Modifier (spell-modifying)
    - **Cast Modes**: Instant, charged, channeled, with configurable haste effects
    - **Trigger System**: 14+ trigger types (melee, arrow, spell, damage, evasion, roll, etc.) with conditional logic
    - **Targeting**: Aim, Beam, Area, Caster, None, FromTrigger - with caps, conditions, and health-based limits
    - **Delivery**: Direct, Projectile, Meteor, Cloud, ShootArrow, StashEffect, Custom - with delays and multi-placement
    - **Projectile Features**: Homing, ricochet, bounce, pierce, chain reactions, divergence, custom hitboxes
    - **Impact Actions**: Damage, Heal, StatusEffect, Fire, Teleport, Spawn, Cooldown, Aggro, Custom
    - **Conditional Logic**: Target modifiers, impact filters, chance-based execution, entity type conditions
    - **Cost System**: Exhaust, items/runes, cooldowns, durability, status effect consumption, with batching
    - **Equipment Sets**: Set bonuses providing spells, attributes, and special abilities

Fancy audio and visuals
- 🔈 Advanced sound system: casting start/loop, release, impact, travel sounds with randomization
- ✨ Sophisticated particle system with shapes, entity following, scaling, and custom magical particles
- 🎨 Custom Item/Block models for projectiles, status effects, and spell clouds with BlockBench support
- 🤸 Player animations at all spell stages with pitch control and ranged weapon animation support
- 💡 Dynamic lighting integration (LambDynamicLights) for magical illumination
- 🌈 Customizable beam rendering with colors, textures, width, and flow effects
- 📍 Area effect visualization with ground indicators and range-scaled particles

In game features
- 🔧 Spell selection and casting visible on HUD (fully player configurable)
- 😌 QoL features: automatic spell cast release, client prediction, smart targeting
- ⛓️ Spell Binding Table for adding spells to weapons and creating spell books
- 📜 Spell Scroll system with creative tab generation and dungeon loot integration
- 🎒 Equipment integration: Spell books, trinket slots (Trinkets/Accessories), automatic weapon detection
- ⚡ Spell Infinity enchantment with configurable item tag support
- 🎮 Commands: `/spell_cooldowns` for server administration and debugging
- 💰 Advanced loot system with `spell_bind_randomly` function for dynamic spell assignment

Developer & Mod Integration
- 🔌 Extensive mod compatibility: Better Combat, Combat Roll, FTB Teams, Shoulder Surfing, and more
- 📊 Comprehensive event system for spell casting, healing, and projectile lifecycle
- 🏗️ Custom handler registration for delivery methods and impact actions
- 🎯 Entity predicate system for complex targeting conditions
- 🔧 Performance optimizations: batching, scheduling, client-side prediction
- 📝 Data generation framework for programmatic spell creation and validation
- 🌐 Multi-platform support (Fabric & NeoForge) with unified API


## ⌨️ Game technical features

### Spells

Spells functionality allows creating custom spells, with various mechanics.

Primary types:
- Active - player interactable (casted) spells, performing wide set of impacts
- Passive - non-interactable spells, triggered by various events, performing wide set of impacts
- Modifier - non-interactable spells, modifying existing spells in pre-defined ways

Fully data driven, (stored in a DynamicRegistry).
- Data file example path: `resources/data/MOD_ID/spells/SPELL_ID.json`
- Assigned to items using Spell Assignments type (see below)

Data type: `Spell` object (see [Spell](common/src/main/java/net/spell_engine/api/spell/Spell.java) for details)

### Item Components

#### Spell Container

Defines spell casting capability for the item. Including:
- spell book access
- contained spells
- binding pool (spell tag), the set of spells that can be bound to the item
- maximum number of spells it can hold

Data type: `SpellContainer` object (see [Spell Container](common/src/main/java/net/spell_engine/api/spell/container/SpellContainer.java) for details)

#### Spell Choice

Defines a set of spells available for the item. Upon first use, player can choose one of the spells from the set to be bound to the item.

Designed for weapons, meant to be used by multiple classes. For example: Wizard Staff that can be used by any of the Wizard specializations.

Data type: `SpellChoice` object (see [Spell Choice](common/src/main/java/net/spell_engine/api/spell/container/SpellChoice.java) for details)

#### Equipment Set

Defines equipment set assigned to the item.

See [Equipment sets](#equipment-sets) section below for details.

Data type: `Identifier` (points to equipment set id)

### Spell assignments

Spell containers can be assigned to an item in multiple ways. These methods have a priority order, Spell Engine will resolve the spell container from the highest priority method available.
1. ItemStack (meta data) component
2. Item default component
3. Spell Assignment data file
4. Automatic (fallback) container assignment done by Spell Engine

#### Assignment with ItemStack (meta data) component

Assigning a spell container to an item, using a game command:
```
/give @p minecraft:wooden_sword[spell_engine:spell_container={access:MAGIC, spell_ids: ["wizards:fireball"] }]
```

#### Assignment with Item default component

Most items are assigned their default spell container using this method.

This method is primarily meant for mod developers, to hard-code the default spell container to their custom items.

Example item definition with hard-coded default component (java code):
```java
public static final Weapon.Entry noviceWand = add(Weapons.damageWand(
                NAMESPACE, "wand_novice",
                Equipment.Tier.TIER_0, () -> Ingredient.ofItems(Items.STICK),
                List.of(SpellSchools.FIRE.id))
        .spellContainer(SpellContainers.forMagicWeapon().withSpell("wizards:scorch"))
);
```

Some third party tools offer ways to override this, in a data driven way.
- [Default Components mod](https://modrinth.com/mod/default-components) (Fabric)

#### Assignment with Spell Assignment Data File (Legacy)

Assigning a spell container to an item, using a data file.

Example data file, located at `data/NAMESPACE/spell_assignments/ITEM_NAME.json`
```json
{
  "access": "MAGIC",
  "spell_ids": [ "wizards:fireball" ]
}
```

#### Fallback assignment

This is a configurable feature of Spell Engine. Tries to detect weapon type of items (such as: sword, axe, bow, etc.) using tags or regex, in order to automatically assign a relevant spell container. 

Config file: `config/spell_engine/weapon_fallback.json`

### Equipment sets

Equipment Sets functionality, allows creating item set bonuses.

Primary features:
- Equipment set bonuses can provide:
  - attributes modifiers
  - any kind of spells
- Supports any kind of equipment (weapons, armors, shields, trinkets...)

Fully data driven, (stored in a DynamicRegistry).

Equipment sets require a two-way association:
- Define the set with a data file
  - Referring all items part of the set (alongside the bonuses)
  - Example path: `resources/data/NAMESPACE/equipment_set/SET_NAME.json`
- Assign the set to items, using an item component
  - Example item with an equipment set: `/give @p minecraft:iron_boots[spell_engine:equipment_set="NAMESPACE:SET_NAME"]`

### Extra inventory slots

Fabric version 
- ships with Accessories and Trinkets support, only one set of the compatibility data files will be loaded, depending on which mod is present
- priority config available for when both are present: `config/spell_engine/fabric_compatibility.json`

NeoForge version
- only ships with Accessories mod compatibility

#### Trinkets integration

The following slots are implemented, using Trinkets mod:
- Spell Book slot `spell/book` (enabled by default)
- Spell Scroll slot `spell/scroll` (disabled by default)
- Quiver slot (in standalone group) `misc/quiver` (enabled by default)
- Quiver slot (in the `spell group`) `spell/quiver` (disabled by default)

#### Accessories integration

The following slots are implemented, using Accessories mod:
- Spell Book slot `spell/book` (enabled by default)
- Spell Scroll slot `spell/scroll` (disabled by default)
- Quiver slot `spell/quiver` (enabled by default)
- Spell Trinket slot `spell/trinket` (enabled by default)

### Tags for customization

Check out the various tags (for items, entities, spells) [here](common/src/main/java/net/spell_engine/api/tags).

### Commands

- `/spell_cooldowns` command (added in Spell Engine 1.5.1). Use to reset specific or all spell cooldowns of given players.

### Loot functions

- `spell_engine:spell_bind_randomly` (added in Spell Engine 1.6.0)
  - Binds one or more random spells to an item.
  - Parameters
    - `pool` Spell tag (optional): tag of spells to choose from
    - `tier` NumberProvider (optional): tier of spells to select from
    - `count` NumberProvider (optional): number of spells to bind
  - Examples use cases:
    - Spell scrolls with a random spells
    - Partially filled spell books
    - Vanilla (or third party weapons) with spell assignments
    - Equipment with additional spells

## 📦️ Game Content

This mod is primarily a library for developers, but it comes with few generic content, primarily to allow spell book creation and spell binding.

### Items

#### Spell Book

ID: `spell_engine:book`

Spell books are items that can hold multiple spells. They are the primary source of spells for players.

Spell book variants are automatically generated (use the same underlying item), offered by
- the Spell Binding Table
- the Spell Engine Creative Tab

Fully data driven Spell Books
- Automatically generated for all spell books listed under tags located in `spell_book/` folder (`<NAMESPACE>:spell_book/<TAG_NAME>`)
- Automatically assigned item model based on tag id: `<NAMESPACE>:models/item/spell_book/<TAG_NAME>.json`
- Automatically assigned custom name based on tag id, translation key: `item.<NAMESPACE>.spell_book/<TAG_NAME>

**Creating spell books**
1. Create your spell book tag, by creating a JSON file at: `data/NAMESPACE/tags/spell_book/BOOK_NAME.json`.
2. Add language resources the spell book:
  - `item.NAMESPACE.spell_book/BOOK_NAME`: "My Spell Book"
  - `item.NAMESPACE.spell_book/BOOK_NAME.description`: "A powerful spell book containing many spells."
3. Add custom item model for the spell book:
  - `assets/NAMESPACE/models/item/spell_book/BOOK_NAME.json`

**Disabling spell books**
1. Create a datapack, with an empty spell book tag for the spell book you want to disable.

#### Spell Scroll

ID: `spell_engine:spell_scroll`

Spell scrolls are items with one spell bound to them
- can be attached Spell Books those binding pool contains the spell of the scroll (using the Spell Binding Table)
- can be equipped into Spell Book slot (and Spell Scroll slot if enabled), to use standalone

The purpose of spell scrolls, is to allow players to collect spells from loot, instead of crafting them, similar to how enchanted books work compared to regular enchantments.

Fully data driven Spell Scrolls
- Automatically generated for all spells listed under tags located in `spell_scroll/` folder (`<NAMESPACE>:spell_scroll/<TAG_NAME>`)
- Automatically assigned item model based on tag id: `<NAMESPACE>:models/item/spell_scroll/<TAG_NAME>.json`
- Automatically assigned custom name based on tag id, translation key: `item.<NAMESPACE>.spell_scroll/<TAG_NAME>`

### Blocks

#### Spell Binding Table block

- ID: `spell_engine:spell_binding`
- Use it to create spell books, and bind spells to them

### Enchantments

#### Spell Infinity

- ID: `spell_engine:spell_infinity`
- Effect: negates spell cast rune cost 
- Applicable: for items under the item tag `spell_engine:enchantable/spell_infinity`

## 🔧 Configuration

Client side:
- Generic client settings 
  - Accessible via the in-game mod menu (or by editing `config/spell_engine/client.json5`)
  - Manual editing is not recommended
- Spell HUD settings
  - Accessible via the in-game mod menu (or by editing `config/spell_engine/hud_config.json`)
  - Manual editing is not recommended

Server side:
- Generic server settings - Settings for spell mechanics and friend or foe logic
  - Accessible by editing `config/spell_engine/server.json5`
  - Synced to clients upon connection
- Elemental weaknesses config - Defines the weaknesses of entity types to different magic schools.
  - Accessible by editing `config/spell_engine/elemental_weaknesses.json`
- Weapon fallback config - Defines the rules for automatic spell container assignment to items.
  - Accessible by editing `config/spell_engine/weapon_fallback.json`
- Spell container templates config - Defines the templates for automatic spell container assignment to items.
  - Accessible by editing `config/spell_engine/spell_container_templates.json`

## 🤝 Compatibility for third party content

### 🤖 Automatic compatibility

Some weapon types automatically get spell casting capability.
Visit the Fallback Assignment section above for details.

### 🗡️ Adding spell casting capability for weapons

Spell Engine is primarily data-driven, to specify what spells an item can cast, create a JSON file at: `data/MOD_ID/spell_assignments/ITEM_NAME.json`. (For example: `data/minecraft/spell_assignments/golden_axe.json`)

Example: enable "Allows spell casting" for a specific item 
```
{
  "access": "MAGIC"
}
```

For ranged weapons (bows and crossbows):
```
{
  "access": "ARCHERY"
}
```

Example: pre-bind spells to a specific item
```
{
  "access": "MAGIC"
  "spell_ids": [ "wizards:fireball" ]
}
```

Example: allow spell binding from a specific spell pool to a specific item 
```
{
  "pool": "wizards:fire"
}
```

Any combination of these features above can be made.

For example: an item that allows casting from the equipped Spell Book, has Frostbolt and Frost Nova spell pre-bound, and arcane spells can be bound to it 
```
{
  "access": "MAGIC",
  "spell_ids": [ "wizards:frostbolt", "wizards:frost_nova" ],
  "pool": "wizards:arcane"
}
```

### 🚫 Disabling spell casting capability for weapons

Spell casting for weapons can be disabled, with an empty data file.

Example - Disabling spell casting for Stone Sword:
`data/minecraft/spell_assignments/stone_sword.json`
```
{ }
```

In this case even automatic compatibility won't be able to assign any spell casting capability to the item.

### ✨ Adding spell power attributes for items

Install [Spell Power Attributes](https://github.com/ZsoltMolnarrr/SpellPower), use its Java API.

Example:
```
// You will not a mutable attribute modifier multimap
ImmutableMultimap.Builder<EntityAttribute, EntityAttributeModifier> builder = ImmutableMultimap.builder();

// +3  Fire Spell Power
builder.put(EntityAttributes_SpellPower.POWER.get(SpellSchool.FIRE),
                        new EntityAttributeModifier(
                                "Modifier name",
                                3,
                                EntityAttributeModifier.Operation.ADDITION));

// +5% Spell Critical Chance
builder.put(EntityAttributes_SpellPower.CRITICAL_CHANCE,
                        new EntityAttributeModifier(
                                "Modifier name",
                                0.05,
                                EntityAttributeModifier.Operation.MULTIPLY_BASE));
```

# 🔨 Using Spell Engine as mod developer

❗️ API IS NOT FINALIZED, MAY INTRODUCE BREAKING CHANGE AT ANY POINT.

## 📖 Spell Creation Guide

See **[docs/](docs/README.md)** for the full spell creation documentation, covering:
- Two authoring workflows (JSON and Java datagen)
- Spell anatomy and execution pipeline
- Casting, targeting, delivery, impacts, cost, triggers
- Visuals and audio

## Installation

Add this mod as dependency into your build.gradle file.

```groovy
maven {
    name = 'Modrinth'
    url = 'https://api.modrinth.com/maven'
    content {
        includeGroup 'maven.modrinth'
    }
}
```

```groovy
modImplementation("maven.modrinth:spell-engine:${project.spell_engine_version}")
```

Install dependencies:
- [Spell Power](https://github.com/ZsoltMolnarrr/SpellPower)
- [Player Animator](https://github.com/KosmX/minecraftPlayerAnimator)
- [Cloth Config](https://github.com/shedaniel/cloth-config)
- [Mixin Extras](https://github.com/LlamaLad7/MixinExtras) (no need to include in your mod, just have it present in the development environment)
  
(Can be done locally by putting release jars into `/run/fabric/mods`, or can be resolved from maven and like Spell Engine.)

## 🪄 Assigning spells to items

### Create a pool of spells

Spell pools are simply a collection of spells, defined using tags.

Create your pool (spell tag), by creating a JSON file at: `resources/data/MOD_ID/tags/spell/MY_SPELL_TAG.json`.

Example, an arbitrary set spells:
```json
{
  "spell_ids": [
    "wizards:fireball",
    "wizards:fire_breath",
    "wizards:fire_meteor"
  ]
}
```

Example, all spells of a specified magic school:
```json
{
  "all_of_schools": ["FIRE"]
}
```

The two solutions can be combined.

### Assign to the item

Assign zero, one or more spells to an item, by creating a JSON file at: `resources/data/MOD_ID/spell_assignments/ITEM_ID.json`.

Your JSON file will be parsed into a [Spell Container](common/src/main/java/net/spell_engine/api/spell/container/SpellContainer.java).

Example wand (one spell assigned, no more can be added)
```
{
  "spell_ids": [ "MOD_ID:SPELL_ID" ]
}
```

Example staff (zero spell assigned, 3 can be added)
```
{
  "pool": "MODID:POOL_ID",
  "max_spell_count": 3,
  "spell_ids": [ ]
}
```

When an item has an assigned Spell Container, it will be eligible for Spell Power enchantments.

