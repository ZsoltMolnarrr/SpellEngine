# 0.9.7

- Changed custom model registration behaviour, no longer defaults to item subfolder (API breaking change!)
- Improve HUD config data structure
- Fix mixin extras error for dependent projects

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