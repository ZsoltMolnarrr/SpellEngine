# Impacts

`impacts` is a top-level array of impact objects. Each impact defines one effect applied to the target when delivery lands.

## Impact Structure

```json
{
  "chance": 1.0,
  "school": null,
  "action": { "type": "DAMAGE", "damage": { "spell_power_coefficient": 1.0 } },
  "particles": [ { ... } ],
  "sound": { "id": "..." },
  "target_modifiers": [ { ... } ]
}
```

| Field | Description |
|---|---|
| `chance` | Probability to execute this impact (0–1). Rolled independently per impact. |
| `school` | Override the spell school for power lookup on this impact only (uses spell school if null) |
| `action` | What happens — see action types below |
| `particles` | Particles spawned at the target on impact |
| `sound` | Sound played at the target on impact |
| `target_modifiers` | Conditions that gate or modify power — see [Conditional Execution](#conditional-execution) |

## How Impacts Execute

All impacts in the list are evaluated in order for each target. A few rules govern execution:

**Intent filtering.** Each impact is classified as either harmful (DAMAGE, FIRE, DISRUPT, STATUS_EFFECT on debuffs) or helpful (HEAL, STATUS_EFFECT on buffs). Once the first impact succeeds against a given target and establishes an intent, any subsequent impacts with the *opposite* intent are skipped for that same target. This prevents a single spell from simultaneously damaging and healing an enemy.

To apply a helpful effect to the caster as part of an otherwise harmful spell, set `action.apply_to_caster: true` — that impact ignores intent filtering and always applies to the caster regardless of who the spell targets.

**Power scaling.** Final damage or heal amount = `spell_power_coefficient × Spell Power × channel_multiplier × distance_multiplier`.

- `channel_multiplier` is automatically computed for channeled spells so that total damage over the full channel equals one non-channeled hit.
- `distance_multiplier` comes from `AREA` target dropoff (1.0 at centre, 0.0 at edge when using `SQUARED`).

**School weaknesses.** Elemental weaknesses configured in `config/spell_engine/elemental_weaknesses.json` are automatically prepended to `target_modifiers` for the relevant school. They behave identically to per-impact conditions and can boost or reduce power for specific entity types without any extra data in the spell itself.

## Action Types

### DAMAGE
```json
"action": {
  "type": "DAMAGE",
  "damage": {
    "spell_power_coefficient": 1.2,
    "knockback": 1.0,
    "bypass_iframes": true
  }
}
```

Final damage = `spell_power_coefficient × Spell Power`. Critical strikes and vulnerability are evaluated by the SpellPower system automatically.

`bypass_iframes: true` (the default) resets the target's invulnerability timer before applying damage. This allows a spell to hit the same entity multiple times in rapid succession — important for channeled spells and multi-projectile bursts. Set to `false` if you want vanilla iframe behaviour.

`knockback` is a multiplier on the engine's base knockback strength, further multiplied by `context.total()` (so channeled ticks apply reduced knockback proportionally). Knockback direction comes from the impact position, not the caster's facing.

### HEAL
```json
"action": {
  "type": "HEAL",
  "heal": { "spell_power_coefficient": 1.0 }
}
```

For channeled heals, the amount is additionally multiplied by the caster's spell haste, so faster casting also increases healing throughput.

### STATUS_EFFECT
```json
"action": {
  "type": "STATUS_EFFECT",
  "status_effect": {
    "effect_id": "minecraft:slowness",
    "duration": 5.0,
    "amplifier": 0,
    "apply_mode": "SET"
  }
}
```

`apply_mode` options:
- `SET` — replaces any existing instance (refreshes duration and resets amplifier)
- `ADD` — stacks amplifier on top of existing stacks, up to `amplifier_cap`
- `REMOVE` — removes matching effects (configure via the `remove` sub-object)

`amplifier_power_multiplier` scales additional stacks with Spell Power — useful for effects that should grow stronger with gear.

### FIRE
```json
"action": {
  "type": "FIRE",
  "fire": { "duration": 3.0 }
}
```

### TELEPORT
```json
"action": {
  "type": "TELEPORT",
  "teleport": { "mode": "FORWARD", "forward": { "distance": 8.0 } }
}
```
`mode`: `FORWARD` (caster blinks forward) or `BEHIND_TARGET` (caster appears behind the target entity).

### AGGRO
```json
"action": {
  "type": "AGGRO",
  "aggro": { "mode": "SET", "only_if_targeted": false }
}
```
`mode`: `SET` (taunt — mob attacks caster) or `CLEAR` (disengage — mob stops targeting caster).

### DISRUPT
```json
"action": {
  "type": "DISRUPT",
  "disrupt": { "shield_blocking": true, "item_usage_seconds": 1.0 }
}
```
Cancels active shield blocking and/or interrupts item usage (eating, drinking) for the specified duration.

### COOLDOWN
Reads and modifies cooldown timers of other spells on the caster or target:
```json
"action": {
  "type": "COOLDOWN",
  "cooldown": {
    "actives": { "id": "*", "duration_multiplier": 0.0 }
  }
}
```
`duration_multiplier: 0.0` resets the cooldown entirely. `duration_add` adds or subtracts seconds. `id` supports universal pattern matching: `*` = all, `#tag` = tag, `~regex` = regex, bare string = exact id.

### SPAWN
```json
"action": {
  "type": "SPAWN",
  "spawn": { "entity_type_id": "mymod:my_entity", "time_to_live_seconds": 10 }
}
```
Spawns an entity at the target position. The entity can implement `SpellEntity.Spawned` to receive context about the spawning caster and spell.

### IMMUNITY
```json
"action": {
  "type": "IMMUNITY",
  "immunity": {
    "damage_type": null,
    "duration_ticks": 20,
    "effect_any_harmful": false
  }
}
```
Grants temporary immunity to a damage type (or all damage if `null`). `effect_any_harmful: true` also blocks harmful status effects.

### CUSTOM
Calls a Java handler registered via `SpellHandlers.customImpact.put(id, handler)`.

---

## Area Impact

`area_impact` splashes impacts to all entities near the primary impact point.

```json
"area_impact": {
  "radius": 4.0,
  "area": { "distance_dropoff": "SQUARED" },
  "particles": [ { ... } ],
  "sound": { "id": "..." },
  "triggering_action_type": null,
  "execute_action_type": null,
  "force_indirect": false
}
```

| Field | Description |
|---|---|
| `radius` | Splash radius in blocks |
| `area.distance_dropoff` | Power falloff for splash targets (`NONE` or `SQUARED`) |
| `triggering_action_type` | If set, area impact only fires if the primary target received an impact of this type (e.g. only splash on successful damage, not on a miss or blocked hit) |
| `execute_action_type` | If set, only impacts of this type execute in the splash — others are skipped |
| `force_indirect` | Skips the primary target entirely; splash only hits surrounding entities. Useful for pure explosion spells |

The splash runs the full `impacts` list on every entity found in the radius, subject to the same intent filtering and conditions as the primary impact. Distance dropoff is applied per splash target.

---

## Conditional Execution

`target_modifiers` on an impact adds conditions that control whether the impact fires and how much power it has:

```json
"target_modifiers": [
  {
    "all_required": false,
    "conditions": [ { "health_percent_below": 0.5 } ],
    "execute": "ALLOW",
    "modifier": { "power_multiplier": 0.5 }
  }
]
```

| Field | Description |
|---|---|
| `all_required` | `true` = all conditions must be met (AND); `false` = any condition is enough (OR) |
| `conditions` | List of target conditions to evaluate |
| `execute` | `ALLOW` = run only if conditions met; `DENY` = run only if conditions not met; `PASS` = always run |
| `modifier.power_multiplier` | Multiplies base power when conditions are met |
| `modifier.critical_chance_bonus` | Flat bonus to critical strike chance |
| `modifier.critical_damage_bonus` | Flat bonus to critical strike damage multiplier |

Multiple `target_modifiers` entries are evaluated in order. Any `ALLOW`/`DENY` modifier that fails its conditions causes the impact to be skipped. `PASS` modifiers apply their power changes regardless.

### Target Conditions

| Field | Description |
|---|---|
| `health_percent_above` | Target's current HP as a fraction of max (0–1) must be above this |
| `health_percent_below` | Target's current HP as a fraction of max (0–1) must be below this |
| `entity_type` | Entity type id or tag (`#minecraft:undead`); supports `~regex` |
| `entity_predicate_id` | ID of a registered `SpellEntityPredicate` |
