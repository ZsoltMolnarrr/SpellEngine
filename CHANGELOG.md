# 1.9.10

Functional changes:
- Targeted entities may only be highlighted if not invisible

API changes:
- Added true volumetric collision detection for spell projectiles
- Added server-side entry point for entity spell casting (SpellHelper.java)

# 1.9.9

Functional changes:
- Added server-side config fields for spell binding level requirements
- Removed auto hand swap feature (due to colliding with weapon skills)

# 1.9.8

Functional changes:
- Fixed crash without Better Combat #174
- Fixed melee skill modifiers:
  - attack damage bonus
  - momentum bonus
- Fixed immobilize effect attribute modifiers

# 1.9.7

Functional changes:
- Fixed melee skill animations for replay mod
- Added new server config options:
  - `spell_binding_level_cost_offset`
  - `spell_binding_level_cost_min`
- Rebalanced some of the weapon skill cooldowns

API Changes:
- Added new shared status effect: `spell_engine:immobilize`
  - Immobilizes the target, preventing movement and jumping
- Added melee delivery related spell modifier fields:
  - `spell.modifier.melee_momentum_add` adding extra momentum to all melee attacks
  - `spell.modifier.melee_slipperiness_add` adding extra slipperiness to the caster while performing melee attacks
  - `spell.modifier.melee_damage_multiplier` multiplying damage of all melee attacks
  - `melee_attacks` list of additional melee attacks
- Added new impact type: `IMMUNITY` to provide temporary invulnerability to certain damage types, and effects
  - Specific damage type, or tag of damage types
  - Directness check
  - Duration
  - Additionally available via java API: `LivingEntityImmunity`
  - Old `EntityImmunity` API is now deprecated
- Melee attack triggers now support spell condition requirement

# 1.9.6

Hotfix:
- Fixed `arrow context` not being cleaned up properly, causing Barrage skill to degrade ranged damage over (short) time

# 1.9.5

Functional changes:
- Improved handling of number keys concurrently to vanilla item switching, fixes #153

API Changes:
- Added new spell delivery method `AFFECT_ARROW` allowing passive spells to apply `arrow_perks` and impacts to any fired arrow 

# 1.9.4

Functional changes:
- Fixed some weapon factory constants
- Fixed ranged weapon pull time constants

# 1.9.3

Functional changes:
- Improve interaction between melee skills and given momentum while being airborne

API Changes:
- Added new melee attack skill fields related to momentum
  - `spell.deliver.melee.allow_airborne` (default: true) - to hold up using momentum based melee skills while airborne
  - `spell.deliver.melee.attack.allow_momentum_airborne` (default: false) - whether the attack specific forward momentum can be applied while airborne
- Added `SPAWN` impact intent specifier to enable spawning onto hostile targets

# 1.9.2

Functional changes:
- Fixed issues of Flurry skill (windup time, animation speed, cooldown proportionality)
- Fixed issues of Swift Strikes skill (windup time)
- Fixed fallback config applying to Axes
- Fixed movement friction of attack skills on NeoForge 
- Fine tune momentum given by Swipe and Thrust skills

API changes:
- Added new spell modifier `channel_ticks_add`, to add extra channel ticks to channeling spells
- Added new SpellEvents:
  - `SpellEvents.CASTING_ATTEMPT` (with Pre & Post stages) - to be able to inject custom failure reason
  - `SpellEvents.COST_CONSUME` - executed when spell cost is being consumed

# 1.9.1

Fix NeoForge launch crash.

# 1.9.0

DISCLAIMER: All spell books and spell scrolls will be reset, due to major API changes. Some (looted) weapons with custom spell containers become non-functional, and need to be re-obtained. Apologies for the inconvenience.

API Breaking Changes:
- Reworked `SpellContainer` structure
  - Removed `content` field, replace by `access` field
  - Removed `is_proxy` fields, replace by `access` field
  - Add new `access` field, controlling the spell resolution behavior (`ANY, NONE, MAGIC, ARCHERY, CONTAINED, TAG`)
  - Add new `access_param` field, providing additional parameter for certain access types (such as tag name for `TAG` access type)
  - Add new `extra_tier_binding` field to `SpellContainer`, to specify spell choice limit per spell tiers
- Reworked spell tag conventions
  - `<NAMESPACE>:spell_books/<TAG_NAME>` - spell collections in this folder, are explicitly marked for generating spell books
  - `<NAMESPACE>:spell_scrolls/<TAG_NAME>` - spell collections in this folder, are explicitly marked for generating scrolls
  - `<NAMESPACE>:weapon/<WEAPON_NAME>` - spell collections meant for weapons (such as Wizard Staff)
- Fully data driven Spell Scrolls
  - New item id: `spell_engine:spell_scroll`
  - Generated for all spells listed under tags located in `spell_scroll/` folder  (such as: `#wizards:spell_scroll/fire`)
  - Automatically assigned item model based on tag id: `<NAMESPACE>:models/item/spell_scroll/<TAG_NAME>.json`
  - Automatically assigned custom name based on tag id, translation key: `item.<NAMESPACE>.spell_scroll/<TAG_NAME>`
- Fully data driven Spell Books
  - All spell books now use the same item id: `spell_engine:spell_book`
  - Generated for all spells listed under tags located in `spell_books/` folder (such as: `#wizards:spell_book/fire`)
  - Automatically assigned item model based on tag id: `<NAMESPACE>:models/item/spell_book/<TAG_NAME>.json`
  - Automatically assigned custom name based on tag id, translation key: `item.<NAMESPACE>.spell_book/<TAG_NAME>`
  - Custom spell books can be made with datapacks, for any combination of spells, just with a spell tag (and some additional assets)
- Spell Data structure changes
  - Casting and release animations are now wrapped into a `PlayerAnimation` object, instead of a plain String
  - `PlayerAnimation` supports: `overrides`, `speed`
  - Reworked `spell.active.cast.channel_ticks` now representing the number of releases during channeling, instead of a duration interval (in game ticks) between channel releases
- Relocated all equipment related APIs to a new dedicated package `rpg_series.item`, to better separate them from the core spell engine APIs
  - types moved: `Equipment`, `Armor`, `ConfigurableAttributes`, `Weapon`  

API Additions:
- Added new `spell_choices` data component
  - Defines a one-time single spell selection, from a given spell tag, before first use
  - Designed for weapons meant for multiple classes (such as Wizard Staff)
- Add choices in Spell Books (Spell Binding Screen)
  - Spells in the same tier are presented as choices
  - Spell tags of spell books can be expanded with choices by third party content
  - Behavior controlled by new `extra_tier_binding` field of spell container
- Added new centralized weapon factory APIs (in `rpg_series` package)
  - Add `Weapons.java` API, creating melee and magic weapons
  - Add `Shields.java` API, creating shields
  - Add `RangedWeapons.java` API, creating bows and crossbows
- Added automatic item model registration for assets in the following folders:
  - `models/item/spell_book/`
  - `models/item/spell_scroll/`
  - `models/spell_projectile/`
  - `models/spell_effect/`
- Added shared spell cooldowns
  - Spells with the same coodown group set each other on cooldown
  - `spell.cost.cooldown.group` field specifies the cooldown group
  - To be used for weapon specific spells
- Added new delivery type: `MELEE`, for performing better combat alike weapon swings
  - Upon performing this delivery type, the caster gains melee attacks, to be automatically executed
  - Attack duration may be static, or attack speed based
  - Custom hitbox definition, using OBB collision detection, ran on the client side
  - Supports fx: animation, sound, particles
  - Includes optional momentum to be gained with the attack
  - Supports additional impacts to be performed on hit
- Add new impact type: `DISRUPT`
  - Can disable: shield blocking, item usage
- Spell Data structure changes
  - Added `spell.cost.cooldown.attempt_duration` to prevent multiple performs of delayed deliveries
- Added `WeaponAttributeGenerator` to datagen API

Functional changes:
- RPG Series core dedicated sub-package now includes melee weapon skills
  - Whirlwind (designed for double axes) - Hold to spin around, dealing {damage} damage per second, to nearby enemies.
  - Cleave (designed for axes) - Performs a spin attack, dealing {damage} damage to nearby enemies.
  - Ground Slam (designed for great hammers) - Leaps into the air and slams into the ground, dealing {damage} damage to nearby enemies.
  - Smash (designed for maces) - SmashesDelivers a strike with powerful knockback, disabling shield and item usage of target.
  - Flurry (designed for claymores) - Hold to unleash a rapid series of strikes, while also gaining momentum.
  - Swift Strikes (designed for swords) - Unleash a rapid series of strikes.
  - Impale (designed for spears) - Throws your weapon forwards, dealing {damage} damage and powerful knockback.
  - Fan of Knives (designed for daggers) - Throws several of your blades in a cone, dealing {damage} damage, bouncing off terrain up to 2 times.
  - Thrust (designed for glaives) - Lunge forward with your weapon, striking all enemies along your path.
  - Swipe (designed for sickles) - Slide forward with your weapon, striking all enemies along your path.
- Added fallback config for automatic spell container assignment to weapons without spell container
  - Supports various pattern matching methods (item id, item tag, regex, inversion)
  - Config file: `config/spell_engine/weapon_fallbacks.json`
  - Removed related old config options from `server.json5`
- Added new server config options:
  - `melee_skills_area_focus_mode` - Determines the focus mode (AREA vs DIRECT) for melee skills.
  - `spell_book_additional_cooldown` - Additional cooldown in seconds applied to spell book items, to prevent quick swapping and casting."
- Improved spell channeling mechanics
  - Channel ticks are now calculated more accurately, spell haste actually increasing tick frequency 
  - Melee skill haste - casting duration is now effected by attack speed multiplier bonuses
- Fixed operation of spell variant, target:`AIM`+ deliver:`DIRECT` + area impact
- Fixed Player data corruption caused by invalid spell identifiers #170
- Update loot injection defaults: Hellish Trials, Draugr Invasion
- Improved Friend or Foe logic on FTB Teams membership, thanks to LeDok

# 1.8.19

API Changes:
- Update `SpellBuilder.Trigger` `meleeKills` and `rangedKill` methods to rely on spell school archetype
- Reorganize `SpellBuilder.Trigger` methods melee and ranged impact trigger methods

# 1.8.18

API Changes:
- Deprecated `SpellBuilder.Triggers.meleeKill`
- Added  `SpellBuilder.Triggers.meleeKills` (to include attack and skill based kills too)

# 1.8.17

Functional changes:
- Fix Accessories mod compatibility without class mods (Relics #16)
- Update translations

# 1.8.16

Functional changes:
- Fix obscure tooltip related concurrency crash #154
- Update translations

# 1.8.15

Functional changes:
- Update loot injection defaults (vanilla loot tables)

# 1.8.14

Functional changes:
- Fix Damage Taken attribute being applied twice

# 1.8.13

Functional changes:
- Fix crash for entities without critical attributes

# 1.8.12

Functional changes:
- Spell Volatility enchantment now applies for Weapons
- Fix crash without Spell Power mod (tiny config embed)

# 1.8.11

Functional changes:
- Added compatibility for Critical Strike mod
  - Physical Melee and Ranged attacks can now critically strike based on Critical Strike mod's attributes
  - Melee and Ranged impact events (Spell Triggers) are now flagged for critical strikes
- Attempt to fix turkish lowercasing issues #152

API Changes:
- Add conditional attributes to config structures
  - WeaponConfig
  - ArmorSetConfig.Piece
  - ShieldConfig

# 1.8.10

Functional changes:
- Updated Fabric slot mod compatibility
  - ships with Accessories and Trinkets support, only one set of the compatibility data files will be loaded, depending on which mod is present
  - priority config available for when both are present: `config/spell_engine/fabric_compatibility.json`
- Update NeoForge slot mod compatibility
  - only ships with Accessories mod compatibility
- Updated translations

# 1.8.9

Functional changes:
- Improve Poison effect damage calculation, now doesn't kill

# 1.8.8

Functional changes:
- Fix dev crash: using a StashEffect Spell with a weapon in the off-hand #147
- Fix Poison effect not dealing damage at stack 1 #148

# 1.8.7

Functional changes:
- Improve Spell Hotbar handling, restored config for priority handler

# 1.8.6

Functional changes:
- Update loot injection defaults (RPG Structures)
- Add loot injection `skip_conditions` flag for Wither
- Update some translations

# 1.8.5

Functional changes:
- Loot injection to entity loot tables, now comes with `killed_by_player` condition, to prevent automatic farming
- Improve some config loading safety

# 1.8.4

Functional changes:
- Attempt some compatibility fix with Minecolonies

# 1.8.3

Functional changes:
- Updated NeoForge dependencies
- "Primary Spell" now show up as "Main Spell" for better clarity
- Add "Missing target" spell casting failure reason, for some spells
- Add "Passive" suffix to passive spell names in tooltips 
- Add spell book explanation support
- Add global elemental weakness and resistances, configurable in `config/spell_engine/elemental_weaknesses.json`
- Fix Power Shot does not apply Mark at close ranges #133
- Fix Mobs panic indefinitely #142 (NeoForge)
- Fix all Poison related skills (by fixing the vanilla Poison effect to scale linearly with amplifier)
- Improve Spell Hotbar interaction with vanilla `Use` keybinding (spell casting and item usage should no longer overlap, offhand blocks are no longer placed after spell casting)
- Improve evasion sound effect
- Evasion no longer works while casting spells or using items (configurable)

API changes:
- Remove deprecated `Spell.Active.Scroll` structure definition
- Remove deprecated particles with hardcoded colors (such as: magic_white_stripe_float)
- Custom Effect renderers are now automatically scaled with the entity (can be disabled at registration)

# 1.8.2

Functional changes:
- Fix crash on archery bow-melee swap

# 1.8.1

Functional changes:
- Fix generic Spell Power base value for existing players

API changes:
- Reworked Spell Container merge logic
  - Now ignores `contentType` when collecting containers
  - Accessible spells are filtered by Spell School archetype
  - with an additional new field `spell.secondary_archetype`

# 1.8.0

Project changes:
- Move to Architectury workspace
- Add support for Accessories mod (Fabric & NeoForge)
- Fabric installations can choose between Trinkets and Accessories mod (config/spell_engine/fabric_compatibility.json)

Functional changes:
- Improved beam rendering on vanilla pipeline

# 1.7.3

Functional changes:
- Fix crash when Custom NPC applies potion effects to player #131
- Fix crash onEvasion for entities

# 1.7.2

API Changes:
- Add some common sounds
- Add some particles

# 1.7.1

Functional changes:
- Add spell cooldown reset announcements
- Update loot injection defaults
- Fix `flame_ground` particle frames
- Fix rare spell cloud presence sound resolution crashes
- Fix Melee and Ranged spell schools double counting enchantments

API Changes:
- Rework `TAUNT` action
  - Now called `AGGRO`
  - Options: `SET`, `CLEAR`
- Extend `SpellBuilder` with new spell making methods
- Vertically rendered `area_...` particle effects renamed to `aura_...`
- Improved Spell Area Effect renderer
  - Properly follows entity motion
  - Aura effects automatically scale with followed entity
- Add new `area` and `aura` particle effects
- Stash effects are now scalable with the new field: `amplifier_power_multiplier`
- Allow `DIRECT` spell delivery, to perform its impacts with no target, when `area_impact` is present
- Add new external Spell Schools (Defense, Health)
- Add new spell trigger: `EFFECT_TICK`
  - Only works for specifically coded Status Effect implementations
  - Reference implementation: `TickingStatusEffect`
- Add new mechanic: Evasion
  - new attribute: `spell_engine:evasion_chance` (applied to all living entities)
  - melee attacks and ranged attacks can be evaded, preventing damage taken
  - evadable damage types listed under `spell_engine:evadable` damage type tag
- Add new spell trigger: `EVASION`
  - triggers when evading an attack
- Add new spell trigger: `DAMAGE_TAKEN` with stage `PRE` 
  - triggers when taking damage, before the damage is applied (damage amount is unmitigated)

# 1.7.0

WARNING: All players must use the same version, due to networking changes.
(Players with Spell Engine 1.6.X, won't be able to connect to servers with Spell Engine 1.7.0)

Functional changes:
- Fixed spell container caching (spells sometimes not showing up on hotbar)
- Fixed projectiles hitting the caster sometimes (chain reaction), fixes #117
- Fixed direct spells delivering additional area impacts
- Fixed granular status effect removal (by spells)
- Improve Spell Registry synchronization (ditching base64), fixes #119
- Improve "On Cooldown" HUD message, no to be shown right after spell cast
- Update loot injection defaults
  - Now include Armor Tier 4, and Armor Tier 5 entries
  - Update Dungeons Arise: Infested Temple, Kisegi Sanctuary

API changes:
- Add new spell type `MODIFIER`, to allow modifying existing spells in narrow pre-defined ways
  - `range_add` to add extra range to spells 
  - `power_modifier` to add extra power, crit chance, crit damage
  - `effect_amplifier_add` to add extra amplifier to status effects
  - `effect_duration_add` to add extra duration to status effects
  - `cooldown_duration_deduct` to reduce cooldown duration
  - `projectile_launch` to modify projectile launch parameters (count, velocity, etc...)
  - `projectile_perks` to modify projectile perks (ricochet, bounce etc...)
  - `additional_placements` to extend placements of spell clouds
  - `mutate_impacts` and `impacts` to add additional impacts to the spell
  - `stash_amplifier_add` to add extra amplifier to spell stashes
  - spell specific modifiers are stored in a cached manner 
- Add Equipment Sets functionality, for creating item set bonuses 
  - Fully data driven (new data type, stored in DynamicRegistry), defined in data files
  - Data file location: `data/NAMESPACE/equipment_set/MY_SET.json`
  - Equipment set bonuses can provide: attributes, spells (active/passive/modifier)
  - Supports any kind of equipment (weapons, armors, shields, trinkets...)
- Spell tooltips now automatically work for all token types, in plural mode
  - such as: `{effect_duration_1}`, `{effect_duration_2}`
- Universal pattern matching logic now supports universal negate, such as:
  - `!namespace:path` matches everything except `namespace:path`
  - `!#namespace:path` matches all items except ones inside `namespace:path` tag
  - `!~my_regex` matches everything except the regex
- Add server side spell container sources
  - Synced to players
  - Managing its content requires granular add/remove operations and imperative sync
- Spell structure changes:
  - Add spell cloud `impact_cap` (for trap-like mechanics)
  - Add new impact action: `TAUNT`, forcing entities to attack the caster
  - Add `area_impact.triggering_action_type`, for only matching impact types to trigger area impact
  - Add `area_impact.execute_action_type`, for filtering impact types to execute area impact actions
  - Add `area_impact.skip_center_target`, to skip center target for area impact
  - Add `impact.chance`, to allow random chance for impact to happen
  - Add new trigger `SPELL_AREA_IMPACT` firing after `area_impact` is executed
  - Cloud delivery now supports location
- Status Effect `RemoveOnHit` API reworked, expanded:
  - Indirect spell damage events (such as area effects) now produce DamageSource that counts as indirect
  - `RemoveOnHit` accepts trigger type, to only be removed upon direct or indirect hits
  - `RemoveOnHit` accepts removal chance
  - `RemoveOnHit` accepts remove stack count
- Update included particle types
  - magic/vertical_stripe particles are now animated
  - Add `sign_fist` particle type
- Add SpellEngineEffects, for common status effects, such as:
  - `spell_engine:stun`

# 1.6.12

Functional changes:
- Fix orbiting effect renderer stutter (for example: Divine Protection)

# 1.6.11

Functional changes:
- Fix effect removal out of bounds warning
- Fix passive spells triggering on already dead targets
- Bundles no longer have support for the Quiver slot
- Improve FTB Teams support, thanks to Muon #115

API changes:
- Add Status Effect impact, `amplifier_cap` field

# 1.6.10

Functional changes:
- Friend or Foe logic now supports FTB Teams membership
- Friend or Foe logic extended with additional configuration options
- Update loot injection defaults

API changes:
- Status effect impact type, remove mode, now supports id pattern
- Universal pattern matcher now supports negated explicit match (prefix: `!`)

# 1.6.9

Functional changes:
- Attempt to fix stuck spell casting sounds
- Update spell hotbar Mouse keybind visualisation
- Update translations
- Data tags, for compatibility with the reworked Amplify Spell enchantment
- Data tags, for implicit compatibility with Spell Volatility enchantment

API changes:
- Data gen API, now accepts `magical` parameter for armor tag generation (Spell Volatility)

# 1.6.8

Functional changes:
- Improve loot injection defaults (DnT combat shrines)
- Add safeguard against collision detection infinite loop

# 1.6.7

Functional changes:
- Improve loot injection defaults
- Spell scrolls now generate with custom names based on spell 

API Changes:
- Add `SpellEvents.HEAL`
- Add `SpellTagsNumbered` api to assign numbers to spell tags
- Spell scrolls can now have custom texture based on tag membership of the first contained spell

# 1.6.6

Functional changes:
- Add Spell Cast criteria (`spell_engine:spell_cast`)

# 1.6.5

Functional changes:
- Spell scrolls can now be disassembled for XP, using vanilla Grindstone
- Update advancements

# 1.6.4

Functional changes:
- Update loot injection defaults (Friends or Foes, Dungeons and Taverns tweaks)

# 1.6.3

Functional changes:
- Update loot injection defaults
- Disable spell casting while doing combat roll  

API Changes:
- Add new player animations, thanks to Forg
- Add wand weapon type (split from staves) 

# 1.6.2

Functional changes:
- Fix loot injection crash, for incomplete tag caches

# 1.6.1

Functional changes:
- Fix some translations typos
- Fix various loot injection problems
- Improve loot injection defaults

# 1.6.0

Functional changes:
- Fix spell scroll tooltips
- Reworked spell binding loot function (`spell_engine:spell_bind_randomly`), now supports multiple parameters
  - (new) spell pool
  - spell tier
  - (new) spell count
- Loot injection now supports item tag filters

API Changes:
- BREAKING! Changed path for loot tier tags
- Add some more generic sounds
- Add spell delivery delay
- Add spell trigger cap per tick
- Add impact `attribute_from_target` field
- Add spell trigger melee condition fields (`melee.is_combo`, `melee.is_offhand`) (based on Better Combat combo)
- Add spell trigger spell condition fields
- Add spell cooldown impact
- Add SpellProjectile custom bounding box with volumetric collision detection
- Add some new template particles, supporting parameter fields from ParticleBatches
  - color
  - scale
  - follow_entity
  - max_age
- Add template particles: spell sparkle variants
- Add template particles: spell area effect variants
- Add template particles: sign variants
- `spell.active.scroll` is now deprecated, marked for removal

# 1.5.5

Functional changes:
- Spell batching and world scheduler related bug fixes

API Changes:
- Add field to hide tooltip header
- Add `equipment_condition` field to spell triggers
- Add more spell container constructors

# 1.5.4

Functional changes:
- Restore data driven spell assignments
- Allow passive spells to work from off hand

API Changes:
- Add spell batching (trigger, cooldown)

# 1.5.3

Functional changes:
- Improve spell projectile stability
- Fix block tags

API Changes:
- Improve weapon entry creation and parameters
- Fix SpellTriggers.onSpellImpactAny missing type cast

# 1.5.2

Functional changes:
- Slightly reduce relic drop changes

API Changes:
- Add improved sound generator utility

# 1.5.1

Functional changes:
- Add `/spell_cooldown` command to reset specific or all spell cooldowns for given players
- Add Spell Cooldown are now persistence and synchronization (between game sessions and dimension changes)
- Add relic loot tags and injection entries (bumped loot config: `config/rpg_series/loot_equipment_v1`)
- SpellBooks on cooldown can now be unequipped in creative mode
- Fix spell tooltip power estimation causing rare crashes
- Fix overly reactive spell hotbar
- Fix knockback direction of directly delivered spell impacts
- Fix spell cloud data tracker
- Fix BuffParticleSpawner spawned batch shape
- Fix spell projectile chain reaction forwarding null spell entry

API changes:
- Add maxSpellCount option for spell book creation
- Extend BuffParticleSpawner constructors

# 1.5.0

IMPORTANT DISCLAIMER:
- This update requires content mods to be updated, due to major API changes

Functional changes:
- Built in trinket slots
  - Spell Book slot `spell/book` (enabled by default)
  - Spell Scroll slot `spell/scroll` (disabled by default)
  - Quiver slot (in standalone group) `misc/quiver` (enabled by default)
  - Quiver slot (in the `spell group`) `spell/quiver` (disabled by default)
- Add engine level immunity against slowing, rooting and stunning effects for bosses

API Changes:
- Build with Fabric Loom 1.9
- BREAKING CHANGES!
  - Completely refreshed spell data structure 
  - Add spell `type` do differentiate between `ACTIVE` and `PASSIVE` spells
  - Spell structure: `cast` and `scroll` moved into `active` object
  - Spell structure: `cost` reworked to be more structured
  - Spell structure: `release.target` split into `target` and `deliver`
  - Completely rewrite spell stashes, now powered by unified spell triggers, stash effects are now automatically linked
  - Rewrite spell impact target conditions, now unified with spell trigger target conditions
- Rewrite spell container merge logic
  - Spell containers are now resolved from: main-hand, off-hand, equipment, trinkets (plugin)
  - Custom spell container sources can be added
  - Spell containers are cached for improved performance
  - Add spell container `slot` field, to allow offhand containers
- Rewrite ammo handling
  - Add support for ammo lookup in container items (Bundle) 
  - Add support for multiple ammo item cost
  - Add quiver slot for Quivers and Rune Pouches
- Add support for passive spells
  - Add spell triggers (for passives and stashes): ARROW_SHOT, ARROW_IMPACT, MELEE_IMPACT, SPELL_IMPACT_ANY, SPELL_IMPACT_SPECIFIC, DAMAGE_TAKEN, ROLL
- Removed `HealthImpacting` status effect configuration interface. Replaced by new attributes:
  - `spell_engine:healing_taken`
  - `spell_engine:damage_taken`
- Misc technical
  - Impact.apply_to_caster now overrides all intent checks, add effect remove action
  - Add StatusEffectClassification to check action impairing effects
  - Included sound entries are now available as static references in `SpellEngineSounds`
  - StatusEffect impact can now be used for helpful and harmful dispels
  - Spell Projectile data synchronization rewritten
- Add generic data file providers to be used by any content mod
  - SpellGenerator
  - SimpleParticleGenerator
  - SimpleSoundGenerator
- Magic related particles are now sorted and systematically generated
  - Name formula: `magic_FAMILY_SHAPE_MOTION`
  - Example: `magic_frost_impact_burst`
  - For all variants, check out `net.spell_engine.fx.Particles`
  - ParticleBatch `PIPE` shape now fixed, use `WIDE_PIPE` for old behaviour
  - Clean up magic related particle effects
- Restructure config related api packages

# 1.4.5

- Fix Spell Registry synchronization, datapacks should no longer cause connection failure

# 1.4.4

- Fix Spell Projectiles sometimes visually glitching after piercing a target
- Fix Spell Projectiles spamming the console
- Allays and Iron Golems are now considered as friendly by default 

# 1.4.3

- Improve spell projectile interpolation

# 1.4.2

- Fix high luminance of beams spell layer ordering
- Fix brightness for some spell particles

# 1.4.1

- Fix Arcane Charge not applying to spell caster
- Fix unstable multiplayer alongside Better Combat mod
- Improve render effect of spell beams (with added configuration to disable high luminance)
- Restore disabling creation of certain spell books, by adding to them to `spell_engine:non_craftable_spell_books` item tag

API Changes:
- Add `spell.projectile.homing_after_absolute_distance` to allow homing projectiles to start homing after reaching certain distance
- Add `spell.projectile.homing_after_relative_distance` to allow homing projectiles to start homing after reaching certain distance relative to the target
- Add `spell.release.target.projectile.direction_offsets` to allow shooting projectile into various directions
- Add `channelTickIndex` to ImpactContext, to allow tracking channeling progress

# 1.4.0

IMPORTANT DISCLAIMER:
- Items in the spell book trinket slot will be lost (as the slot itself is being relocated)
- This update requires content mods to be updated, due to major API changes

Functional changes:
- Add support for spell scroll slot, can be enabled using data pack
- Rework trinkets integration, to declare custom slot group
- Rework container merge logic
- Spell cooldowns now get imposed onto the hosting item
- Durability cost of spell cast will be imposed on the source ItemStack of the spell (if possible)
- Spell projectiles may perform area impacts when colliding blocks
- Fix empty spell scrolls generated in loot chests
- Updated loot defaults (Illager Invasion)

API Changes:
- Add `spell.cost.cooldown_hosting_item` to disable imposing spell cooldowns onto the hosting item
- Add `spell.range_melee` to match spell range with melee attack range (EIR)
- Add `spell.impact.target_conditions` allowing entity type specific immunities, weaknesses and resistances

# 1.3.2

- Fix dedicated server crash

# 1.3.1

- Fix container merge logic

# 1.3.0

Functional changes:
- Add Spell Scrolls, automatically generated for all spells, found in loot chests, can be added to matching spell books
- Add support for any spell book logic, any item can be turned into a spell book by adding to `spell_engine:spell_books` tag, and adding proper spell container to it
- Improved Spell Container merge logic, to allow resolving spells from equipment (New config options available in `spell_engine/server.json5`, starting with `spell_container_from` )
- Renamed loot config (responsible for equipment loot injection) to `rpg_series/loot_equipment.json`
- Add separate config file for Spell Scroll loot injection, `rpg_series/loot_scrolls.json`
- Update loot config defaults (Aether Villages, BOMD: Obsidilith, DNT: End Castle, Dungeons Arise: Shiraz Palace, Aviary)
- Spell Books and Spell Scrolls placeable into Chiseled Bookshelf
- Fix spell tooltip ordering with advanced tooltip
- Fix loot injection from non tag entries

API Changes:
- Add `spell.learn.enabled` field to disable unlocking via Spell Binding Table
- Add `spell.scroll` object, defining various spell scroll related parameters
- Spell container from held item, now requires `is_proxy = true` to cast spells

# 1.2.2

Functional changes:
- Fix HUD rendering issues

# 1.2.1

Functional changes:
- Modify loot config, to reduce loot frequency in some of the commonly occurring chests
- Restore LambdaDynamicLights compatibility

API Changes:
- Add `spell.cast.animation_pitch` field

# 1.2.0

Functional changes:
- Rework spell hotbar logic around `Use` key (right-click), to enable compatibility with weapons those have a right click use (such as Trident)
- Rewrite first person camera compatibility (to support FirstPersonModel and Real Camera)
- Fix item use while spell casting
- Fix rendering on hidden HUD (Fabric API related)
- Fix spamming console with advancement info
- Fix misc render crash #87
- Fix spell projectile rendering order issues

API Changes:
- BREAKING - Deprecated all item usage related fields and types in Spell.java  
- Internal: SpellHelper.performImpacts now requires array of impacts to be supplied
- Extend ArrowPerks API with custom array of impacts
- Add SpellStash capability to StatusEffects to store a spell

# 1.1.2

- Improve safety of Spell Projectile persistence
- Fix rare case where offhand item use was incorrectly shown on Spell Hotbar 

# 1.1.1

- Improve safety of effect ID synchronization

# 1.1.0

Functional changes:
- Allow falling projectile (Meteor alike) spells to be casted on the ground
- Netherite (and above) armor and weapons get automatic fireproof setting
- Fix some tooltip issues
- Reinstate Trinkets mod requirement
- Disable Dynamic Lights compat, to avoid crashing, as updated alternatives violate class path

API Changes:
- BREAKING, internal! - SpellHelper.performSpell expects SpellTargetResult instead List of entities
- Add spell release type `METEOR` `requires_entity` field

# 1.0.5

Functional changes:
- Improve auto swap feature, to prioritize block interactions
- Fix targeting Ender Dragon with spells #63

# 1.0.4

- Lower Fabric API version requirement

# 1.0.3

- Improve spell Beam rendering (no longer casts shadows, no longer conflicting with other transparent blocks, such as water)
- Improve Friend or Foe logic, direct damage within teams with friendly fire enabled is now allowed as expected
- Add loot table injections to: Trial Chambers chests, Stronghold Corridor
- Disable right-click interaction while actively casting spells
- Allow running on 1.21

# 1.0.2

- Fix spell cooldowns causing disconnects on dedicated servers

# 1.0.1

- Fix Spell binding table crafting

# 1.0.0

- Update to Minecraft 1.21.1

Functional changes:
- Player scale increasing spell range
- Add Spell Projectile safeguards against crashing
- Improve SpellBook tooltips

API Changes:
- BREAKING! Asset directory for animations have been renamed from `spell_animations` to `player_animations`
- Add DataComponent for Spell Container
- Spell Container is now immutable as record
- Add Spell Projectile launch sound

# 0.15.10

Functional changes:
- Improve auto swap feature to consider tools #71
- Client side configurable tooltip of "Casts spells from equipped Spell Book" 

# 0.15.9

Functional changes:
- Spell Binding Table spell entries now have more clear requirement and cost information
- Add `spell_book_creation_enabled` config option to disable spell book creation at the Spell Binding Table
- Add `spell_binding_level_cost_multiplier` config option
- Add `spell_binding_lapis_cost_multiplier` config option

# 0.15.8

Functional changes:
- Reduce spell book creation level requirement
- Attempt to fix deseralization crash #62
- Fix Arcane Blast targeting allies
- Fix stuck casting sounds

# 0.15.7

API Changes:
- Extend item config attribute resolution

# 0.15.6

Functional changes:
- Reworked loot injection system, now able to spawn enchanted loot from tags
- Update Spell Infinity custom application condition, custom items can now be enabled by adding to `spell_engine:enchant_spell_infinity` tag

# 0.15.5

Functional changes:
- Disable class switching during cooldowns
- Add spellbook equip sound

# 0.15.4

Functional changes:
- Improve automatic hand swap feature

API Changes:
- Add throw related player animations
- Make rage buff particles translucent

# 0.15.3

Functional changes:
- Improve automatic hand swap feature
- Hide Spell Hotbar when player is in Spectator mode

# 0.15.2

Functional changes:
- Improve automatic hand swap feature

# 0.15.1

Functional changes:
- Add automatic hand swap (client configurable feature)
  - Works when having a melee weapon and a skill use weapon in main and off hands
  - Attack key will swap the melee weapon to the main hand
  - Use key will swap the skill use weapon to the main hand
  - Typically useful for archers (bow + spear)
- Fix random crashes of Spell Projectiles
- Update to latest Shoulder Surfing API

# 0.15.0

Functional changes:
- Improve Spell Binding obfuscation style (thanks to fzzyhammers)
- Add new particle effects and player animations
- Add global cooldown after instant spell cast (configurable)
- Update advancements scope and basic structure

API Changes:
- BREAKING! Replace spell projectile `ProjectileModel.RenderMode` with `Orientation`
- BREAKING! Spell tooltip: Multiple placeholders of the same kind have new format (example: `{damage_1}`, `{damage_2}` ...) 
- Update Fabric Loader to 15+ for embedded MixinExtras
- Add custom spell tooltip mutators (refactored SpellTooltip internals)
- Add teleport "BEHIND_TARGET" teleport action type
- Add spell projectile model rendered as held item (for throw skills)
- Add spell projectile travel sound
- Add spell specific movement speed multiplier during casting

# 0.14.3

API Changes:
- BREAKING! - Migrated to new version of Spell Power Attribute API
- Migrated to new version of Ranged Weapon API, Projectile Damage Attribute is no longer being used
- Expose `PHSICAL_RANGED` and `PHYSICAL_MELEE` schools into public package (ExternalSpellSchools)

Functional changes:
- Add obfuscated spell binding entries, when Spell Binding Table is not having enough supporting bookshelves
- Migrate to latest API of Shoulder Surfing
- Draw Speed attribute (`ranged_weapon:haste`) working as haste for archery skills (`PHSICAL_RANGED` school)

# 0.13.3

API Changes:
- Change embedding scope of `ExtraRadius` to `AreaImpact` to be more widely applicable

Functional changes:
- Update Italian translation, thanks to Zano1999
- Fix render glitches of spell objects without emitted light, when not using shaders
- Add `{cloud_radius}` placeholder support to spell tooltip

# 0.13.2

- Add particle batch extent special behaviour
- Add Spell Cloud extra radius
- Add Spell Cloud center model rendering

# 0.13.1

- Fix launch crash on dedicated servers 

# 0.13.0

API changes:
- Add `group` field to spell data, to group spells together (Spells with the same group override each other, prioritized by tier)
- Add particle batch `invert` field, to spawn particles with reverse motion
- Add particle batch `pre_spawn_travel` field, to offset particle position relative to motion
- Add particle batch `roll` and `roll_offset` fields, to spawn particles with rotated motion vector
- Add `nature_spark_mini` particle
- Add new impact action type: `SPAWN`, for spawning entities
- Add new impact action type: `TELEPORT`, to move the caster around
- Add barebone immunity API
- Add ShaderCompat helper to determine active shader
- Add CustomLayers raw constructor
- Add two-way entity collision API 
- Add SpellCloud `presence_sound` data field, batch spawning, custom positioning and timing
- Add SpellCloud `spawn` structure for particles and sounds for spawning
- Add SpellCloud LambDynamicLights support
- Add new spawn directives for Meteor spawning

Functional changes:
- Spell Container resolution
  - Trinkets mod is now technically optional, to enable better interoperability for Forge players
  - When Trinkets mod is missing, spell books can be put into the offhand slot (needs to be enabled in `config/server.json5 spell_book_offhand`)
  - Spell Containers are now resolved and combined from all equipped trinket slots (prioritizing Spell Book slot first)
- Usable offhand items (such as Shields) are now visible on the Spell Hotbar
- Add new loot config using rpg series item tags, into `config/rpg_series/loot.json`
- Loot configuration now supports item tag id entries
- Spell particle emitting entity yaw and pitch now being synchronized
- Fix projectile pitch setting #40
- Fix some spells unable to hit Ender Dragon
- Update Italian translation, thanks to Zano1999
- Piglins love RPG Series golden weapons

# 0.12.5

- Fix channeled spell particles not being rendered, from player behind (for example: Fire Breath) 
- Add some comments to config

# 0.12.4

- Add missing translation for Spell Area Effect entity
- Add support for Supplementaries Quiver, for Archery skills

# 0.12.3

Functional changes:
- Add generic item use skills (`spell_engine:use_item`, `spell_engine:use_offhand_item`)
- Add sneak to bypass Spell Hotbar (disabled by default, client configurable)
- Disable spell casting in Spectator mode
- Fix random crashes on PersistentProjectileEntity
- Fix Spell Binding Table arrow color states

API Changes:
- Add armor set modifier to allow/disable spell power enchantments

# 0.12.2

Functional changes:
- Fix on cooldown error messages when spell gets into cooldown
- Update Spell Binding Table arrow button appearance

API Changes:
- Add arrow perks damage multiplier

# 0.12.1

- Disable hotbar directly for spell books
- Disable MultiShot arrows being able to bypass iframes
- Update Italian translation, thanks to Zano1999

# 0.12.0

Functional changes:
- Update Spell Book slot icon
- Spell hotbar now renders fancy mouse and keyboard icons
- Fix dropping item not cancelling spell casting
- Fix swapping to caster item with number keys starting spell casting right away
- Spells with `arrow` item cost, now rely on vanilla Infinity enchantment
- Cancel spell casting upon opening GUI

API Breaking changes:
- Add area effect capability to any spell impact (moved from SpellProjectile)
- Rework the data part of `PROJECTILE` and `METEOR` release types
- In `ProjectileData.ClientData` projectile model related data has been moved into a subfield named `model`
- ItemConfig.Attribute `name` field retired, now full attribute id needs to be specified in `id` field
- ProjectileModel `RenderMode` new default is now `DEEP`

API Additions:
- Add spell impact specific schools definition
- Add new spell area effect cloud release action
- Add `content` field to Spell Container to indicate the type of supported spells (Spell vs Archery skill)
- Add `mode` field for spells, to allow using items instead of casting spells
- Add `casting_animates_ranged_weapon` for spells, to animate held bow pull state based on spell cast progress
- Add `light_level` field to Spell Projectile client data, to allow emitting ambient light (using LambDynamicLights)
- `PHYSICAL_RANGED` school can now be used for spells, given that Projectile Damage Attribute mod is installed
- Arrows being shot while casting spell with `"mode": "ITEM_USE"`, or shot with `"type": "SHOOT_ARROW"` can perform impact actions of the spell, can have their custom projectile model
- ItemConfig `attributes[].id` field now accepts projectile damage and combat roll related attributes. Third party attributes can be support via Mixin into `AttributeResolver`
- Add `HealthImpacting` interface for status effects, to modify damage and healing taken
- Add some shared status effect renderers: `OrbitingEffectRenderer`, `StunParticleSpawner`
- Fix spell tooltip indexed tokens

Other changes:
- Update MixinExtras to 0.2.0

# 0.11.0

- Add mouse scrolling to spell binding table GUI
- Fix item damage causing spell casting interrupt
- Keep order of `player_relations` in server config

# 0.10.0

Spell Hotbar can be controlled via Keybindings!
- Multiple hotkey behaviors available (hold to cast, press to cast), configurable for different casting mechanics separately
- Custom hotkeys can be assigned
- Vanilla hotkeys (such as use key, item hotbar keys) can be used, when no custom hotkeys are assigned
- Switching between item and spell hotbar is no longer needed, nor possible
- Spell Hotbar keybind is rendered on HUD

Other changes:
- Spell Haste effects spell casting animation playback speed
- Spell data files can now specify if Spell Haste should affect cooldown and casting speed
- Internal refactor for the entire spell casting mechanism
- Spell casting no longer stutters when quick switching between spells
- Optimise spell projectile sync payload
- Fix server config `player_relations` being reset on every launch

# 0.9.32

- Fix projectiles ricocheting to allies
- Fix projectile area impacts affecting allies
- Improve luminance of rendered beam spells
- Remove Architechtury toolchain, replaced with pure Fabric toolchain

# 0.9.31

- Add Spell Projectile light emission data field

# 0.9.30

- Add safeguards against SpellProjectile perks being null

# 0.9.29

- Add universal Spell Projectile perks: ricochet, bounce, pierce
- Add support for Spell Projectiles performing area effects
- Add support for Spell Projectiles to be spawned multiple times
- Add Spell Projectile launch events to Java API
- Add italian translation by Zano1999 #21
- Improve area effect in-radius check
- Fix right click to use abilities for blacklisted and datapack disabled items
- Fix HUD render blend glitch
- Migrate to new Fabric API model loading functions

# 0.9.28

- Add configurable entity relations (by arbitrary entity id). Iron Golem and Guard Villagers included by default.

# 0.9.27

- Restore and improve Shoulder Surfing compatibility
- Spell books can be removed from Spell Binding Table

# 0.9.26

- Support Minecraft 1.20.1

# 0.9.25

- Add protection against random crashes caused by UI #19

# 0.9.24

- Add Spell Binding Table item hint
- Fix stunned horses being able to walk around, thanks to Noaaan!

# 0.9.23

- Improve spell tooltip footer
- Fix spell damage estimation when having zero spell power

# 0.9.22

- Fix crash with Artifacts #16

# 0.9.21

- Add book placeholder icon to Spell Binding table GUI
- Add support for enchanted items in loot configuration
- Disable spell haste for generic weapons

# 0.9.20

- Increase Spell Binding table offer count cap (from 10, to 32)

# 0.9.19

- Add fallback compat blacklist regex

# 0.9.18

- Fix Spell Book creation in multiplayer

# 0.9.17

- Add Spell Book system (requires on Trinkets mod)
- Add Spell Book creation advancement criteria
- Add spell casting capability for weapons (data pack compatible, server configurable: `add_spell_casting_to_swords`, `add_spell_casting_regex`)
- Refactor enchantment restriction internal API
- Fix incompatibility with Taterzens #11 (samolego/Taterzens#132) - Thanks to Nillo-Code
- Fix keybind for viewing spell info #12
- Fix spell swords not harvesting cobwebs fast enough
- Extend Java API with custom spell handlers

# 0.9.16

- Add `SEMI_FRIENDLY` and `MIXED` entity relations
- Add `spell_pool` condition to spell binding advancement criteria
- Remove advancements spell cast criteria (due to theoretical poor performance)
- Set `generic.attack_damage` vanilla attribute to be synchronized over to clients
- Fix use spell_cost_item_allowed still requiring at least 1 rune
- Allow Fire Aspect for staves

API breaking changes:
- `spell.impact[].action.status_effect.apply_to_caster` was moved to `spell.impact[].action.apply_to_caster`

JSON API changes:
- Add min_power to `spell.impact[].action`
- Area effects can now target the caster too
- Add new particles
- Add support for dual intent spells

Java API changes:
- Extend armor and weapon creation API
- Extend particle effect JSON API
- Add loot configuration API

# 0.9.15

- Add Ukrainian translation, thanks to un_roman
- Fix issues for Turkish players

# 0.9.14

- Improve spell cast sync
- Prevent spam click cheesing channelled spells

# 0.9.13

- Add FirstPersonModel support
- Remove use deprecated Spell Power API

# 0.9.12

- Fix server launch crash
- Fix Spell Binding Table mining properties

# 0.9.11

- Add new particle effects
- Add action impairing status effect system (aka CC, for example: Stun, Silence)
- Add entry and config definitions of armors and weapons to API package
- Allow offhand items to be used while ALT is held (such as Bow, Shield)
- Allow no pool in spell containers if spell id list is non-empty (wands)
- Simplify tooltip headers
- Remove dependency to Better Combat (first person animations are now supported by PlayerAnimator)

# 0.9.10

All spell bindings have been reset due to a major API change! We apologize for the inconvenience.

- Change spell assignment by introducing spell pools (API breaking change!)
- Fix sound stuck casting sound when swapping hands 

# 0.9.9

- Add Shoulder Surfing adaptive crosshair support 

# 0.9.8

- Update dependencies
- Make Better Combat mandatory due to beam render glitch

# 0.9.7

- Add spell cast attempt failure reason to HUD
- Changed custom model registration behaviour, no longer defaults to item subfolder (API breaking change!)
- Improve HUD config data structure
- Improve spell cast synchronization
- Fix mixin extras error for dependent projects
- Fix spell caster items preventing shield blocking

# 0.9.6

- Fix empty nbt tag causes items not to stack #5
- Improve mixin compatibility, lift breaks on Carry On

# 0.9.5

- Add spell power caching
- Add sticky targets
- Add filtering invalid targets 
- Add proper friend or foe logic (configurable, now consistent with Better Combat)
- Add teammates being able to shoot projectiles through each other (configurable)
- Add spell hotbar control hint, update default client config
- Fix spell projectiles knocking back targets at incorrect angle

# 0.9.4

- Improve spell hotbar visibility in HUD config screen
- Specify Fabric API version requirement
- Add breaks flag for Carry On :(

# 0.9.3

- Add Spell Binding advancement criteria
- Add specific enchantment advancement criteria
- Fix channeled spells not released when switching to other items
- Remove some redundant configs

# 0.9.2

- Add Spell Hotbar indicator for minimized spells
- Add Spell Binding Table tooltip hint to empty staves 
- Add StaffItem to API
- Allow Knockback and Looting enchantments for StaffItem
- Improve tooltip logic
- Improve target highlighting
- Fix crash when Better Combat is absent
- Fix Spell Binding Table no offers in multiplayer
- Fix Spell Binding Table disconnect in multiplayer
- Fix server crashes

# 0.9.1

- Initial alpha release