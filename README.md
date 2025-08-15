![Title](.github/title.png)

<div align="center">

<a href="">![Java 17](https://img.shields.io/badge/Java%2017-ee9258?logo=coffeescript&logoColor=ffffff&labelColor=606060&style=flat-square)</a>
<a href="">![Environment: Client & Server](https://img.shields.io/badge/environment-Client%20&%20Server-1976d2?style=flat-square)</a>
<a href="">[![Discord](https://img.shields.io/discord/973561601519149057.svg?label=&logo=discord&logoColor=ffffff&color=7389D8&labelColor=6A7EC2&style=flat-square)](https://discord.gg/KN9b3pjFTM)</a>

</div>

## 🪄️ Features

Data driven API
- 🗡️ Spells can be assigned to any weapon (data driven)
- 🔮 Spells deal damage based on [Spell Power](https://github.com/ZsoltMolnarrr/SpellPower) entity attributes
- ✍️ Spells defined in JSON format
- ⚙️ Spells have a set of different mechanical behaviours:
    - Active spells, can be casted, with various options: charged, channeled, instant
    - Passive spells can be triggered: melee impact, arrow impact, spell impact (and much more...)
    - Targeting mode: Aim, Beam, Area (and  more...)
    - Delivery mode: Direct, Projectile, Cloud (and more...), including custom coded
    - Impact actions: Damage, Heal, StatusEffect, Teleport, Spawn (and more...), including custom coded
    - Cost: exhaust (hunger), item (runes), cooldown (time), durability, consume effects
    - Support for various archery skills

Fancy audio and visuals
- 🔈 Spells have sound effects: at the start of casting, while casting, at release, at impact
- ✨ Spells have particle effects (any particle can be referenced by id), and the engine offers its custom set magical of particles
- 🎨 Custom Item/Block models can be used for Spell Projectiles and Status Effects
- 🤸 Custom player animations can be played at different stages of spell casting

In game features
- 🔧 Spell selection and casting is visible on the HUD (fully player configurable)
- 😌 QoL features included (such as automatic spell cast release)
- ⛓️ Add spells to eligible weapons using the Spell Binding Table
- 💰 Loot table injection


## ⌨️ Game technical features

### Spells

Spells functionality allows creating custom spells, with various mechanics.

Primary types:
- Active - player interactable (casted) spells, performing wide set of impacts
- Passive - non-interactable spells, triggered by various events, performing wide set of impacts
- Modifier - non-interactable spells, modifying existing spells in pre-defined ways

Fully data driven, (stored in a DynamicRegistry).
- Data file example path: `resources/data/MOD_ID/spells/SPELL_ID.json`
- Assigned to items susing Spell Assignments type (see below)

### Spell assignments

Spells can be assigned to items, by pairing a `SpellContainer` to the item.

A [Spell Container](src/main/java/net/spell_engine/api/spell/container/SpellContainer.java) can be assigned to an item, or hosted by a skill tree node. Contains information about:
- List of spells it holds
- Maximum number of spells it can hold
- Whether it is a proxy (allows casting all currently owned spells)

Spell Container to item assignment methods (listed in priority of use):
- Item data component from meta data (for example: spawning item with command)
- Item data component from default components 
- Spell Assignment data file (this is a legacy feature)
- Automatic (fallback) container assignment done by Spell Engine   

#### Assignment with Item Component

Assigning a spell container to an item, using a game command:
```
/give @p wizards:staff_wizard[spell_engine:spell_container={is_proxy:true, spell_ids: ["wizards:fireball"] }]
```

#### Assignment with Data File

Assigning a spell container to an item, using a data file.

Example data file, located at `data/NAMESPACE/spell_assignments/ITEM_NAME.json`
```json
{
  "is_proxy": true,
  "spell_ids": [ "wizards:fireball" ]
}
```

### Equipment sets

Equipment Sets functionality, allows creating item set bonuses.

Primary features:
- Equipment set bonuses can provide:
  - attributes modifiers
  - any kind of spells
- Supports any kind of equipment (weapons, armors, shields, trinkets...)

Fully data driven, (stored in a DynamicRegistry).

Equipment sets require a two-way association:
- Define the set in a data file
  - Example path: `resources/data/NAMESPACE/equipment_set/SET_NAME.json`
- Assign the set to items, using an item component
  - Example item with an equipment set: `/give @p minecraft:iron_boots[spell_engine:equipment_set="NAMESPACE:SET_NAME"]`

### Extra inventory slots

#### Trinkets integration

The following slots are implemented, using Trinkets mod:
- Spell Book slot `spell/book` (enabled by default)
- Spell Scroll slot `spell/scroll` (disabled by default)
- Quiver slot (in standalone group) `misc/quiver` (enabled by default)
- Quiver slot (in the `spell group`) `spell/quiver` (disabled by default)

### Tags for customization

Check out the various tags (for items, entities, spells) [here](src/main/java/net/spell_engine/api/tags).

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

This mod is primarily a batch of tools (APIs) for developers, but it comes with few generic content, primarily to allow spell book creation and spell binding.

### Items

#### Spell Scroll

- ID: `spell_engine:scroll`
- Functions analogously to vanilla Enchanted Books. 
  - Spell scroll is generated in creative mode tab, for all spells
  - Spell scrolls with randomly bound spells can be found in dungeons loot chests
- Can be placed into Spell Books, using the Spell Binding Table
- Can be equipped into Spell Book slot (and Spell Scroll slot if enabled), to use without a spell book

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

### Fabric

Client side settings can be accessed via the [Mod Menu](https://github.com/TerraformersMC/ModMenu).

### Server

**Server side** configuration can be found in the `config` directory, after running the game with the mod installed.

## 🤝 Compatibility for third party content

### 🤖 Automatic compatibility

Sword like weapons are automatically picked up, and assigned _spell casting from spell book_ capability.
This feature is turned on by default, it can be disabled in `config/spell_engine/server.json5`, black listing and white listing are also supported in form of regex. 

### 🗡️ Adding spell casting capability for weapons

Spell Engine is primarily data-driven, to specify what spells an item can cast, create a JSON file at: `data/MOD_ID/spell_assignments/ITEM_NAME.json`. (For example: `data/minecraft/spell_assignments/golden_axe.json`)

Example: enable "Casts spells from equipped Spell Book" for a specific item 
```
{
  "is_proxy": true
}
```

For ranged weapons (bows and crossbows):
```
{
  "is_proxy": true,
  "content": "ARCHERY"
}
```

Example: pre-bind spells to a specific item
```
{
  "is_proxy": true
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
  "is_proxy": true
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

❗️ DOCUMENTATION IS INCOMPLETE!

❗️ API IS NOT FINALIZED, MAY INTRODUCE BREAKING CHANGE AT ANY POINT.

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

## ⭐️ Creating a spell

Create a new file at `resources/data/MOD_ID/spells/SPELL_ID.json`.

Write the content of the JSON file to match the structure of the [Spell](src/main/java/net/spell_engine/api/spell/Spell.java) class. Your JSON will be parsed into a Spell instance.

Spells are automatically registered. 

### Resources

#### Sound
You can register a sound effects to be used for different stages of the spell casting. Alternatively you can use generic magic school related sound effects provided by this mod.

#### Icon
Place your spell icon to the following location: `resources/assets/MOD_ID/textures/spell/SPELL_ID.png`

#### Name and description

Add name and description entries into your translation file.
```
"spell.MOD_ID.SPELL_ID.name": "Spell Name",
"spell.MOD_ID.SPELL_ID.description": "Shoots and epic energy ball that does {damage} lightning damage",
```
The description supports multiple string [token](common/src/main/java/net/spell_engine/client/gui/SpellTooltip.java) to display dynamic information.

#### Custom models

Spells can render custom models, using Item/Block format (can be created using BlockBench).
Place your model to: `resources/assets/MOD_ID/models/item/MY_PROJECTILE_NAME.json`
Register your own model, like this:

```
CustomModels.registerModelIds(List.of(
    Identifier.of(MOD_ID, "MY_PROJECTILE_NAME")
));
```

##### Projectiles

Custom model projectiles can be used by just referencing the registered projectile model.

##### Status effects

Custom models can be used to be rendered over entities affected by custom status effects.

Create a renderer, implementing `CustomModelStatusEffect.Renderer`, and register it:
```
`CustomModelStatusEffect.register(MyStatusEffects.myEffectInstance, new MyRenderer());`
```
Example (Frost Shield, renders a big block around to player):
```
public class FrostShieldRenderer implements CustomModelStatusEffect.Renderer {
    public static final Identifier modelId_base = Identifier.of(WizardsMod.ID, "frost_shield_base");
    private static final RenderLayer BASE_RENDER_LAYER = RenderLayer.getTranslucentMovingBlock();
    @Override
    public void renderEffect(int amplifier, LivingEntity livingEntity, float delta, MatrixStack matrixStack, VertexConsumerProvider vertexConsumers, int light) {
        float yOffset = 0.51F; // y + 0.01 to avoid Y fighting
        matrixStack.push();
        matrixStack.translate(0, yOffset, 0); // y + 0.01 to avoid Y fighting
        CustomModels.render(BASE_RENDER_LAYER, MinecraftClient.getInstance().getItemRenderer(), modelId_base,
                matrixStack, vertexConsumers, light, livingEntity.getId());
        matrixStack.pop();
    }
}
```

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

Your JSON file will be parsed into a [Spell Container](src/main/java/net/spell_engine/api/spell/container/SpellContainer.java).

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

## ✨ Audio and visuals

Spell Engine has multiple kind of assets built in: 
- Sound effects (you can find the available sounds [here](src/main/java/net/spell_engine/fx/SpellEngineSounds.java))
- Particle effects (you can find the available effects [here](src/main/java/net/spell_engine/fx/Particles.java))
- Player Animations (you can find the available animations [here](src/main/resources/assets/spell_engine/player_animations))

These assets are referenced in spell json files, by using their Identifier.