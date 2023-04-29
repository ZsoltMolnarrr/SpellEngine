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