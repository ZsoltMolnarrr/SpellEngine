# Delivery

The `deliver` block controls how the spell reaches its target after targeting resolves.

## Delivery Types

| Type | Description |
|---|---|
| `DIRECT` | Impacts applied immediately on each resolved target — no travel |
| `PROJECTILE` | Launches a spell projectile entity; impacts fire when it hits |
| `METEOR` | Drops a projectile from above the target point; impacts fire on landing |
| `CLOUD` | Spawns a persistent area entity that applies impacts repeatedly |
| `SHOOT_ARROW` | Fires an actual arrow item; impacts fire on arrow hit |
| `AFFECT_ARROW` | Registers impacts on the next arrow the player fires naturally (no arrow spawned) |
| `MELEE` | OBB hitbox swing; collision detection runs client-side |
| `STASH_EFFECT` | Applies a buff status effect that re-delivers the spell on a future event |
| `CUSTOM` | Calls a Java-registered handler |

```json
"deliver": { "type": "DIRECT" }
```

## DIRECT

Impacts are applied immediately to each entity in the target list. No entity is spawned, no travel time.

When targeting produces no entity but a ground position is available (e.g. `AIM` pointed at the floor), `DIRECT` can still execute an `area_impact` at that location. This lets you create ground-targeted AoE spells without a projectile:

```
target: AIM (no entity needed) + deliver: DIRECT + area_impact: { radius: 4 }
```

## PROJECTILE

Spawns a `SpellProjectile` entity per resolved target. The projectile travels from the caster's shoulder level along the look vector and fires impacts when it hits an entity or a block.

```json
"deliver": {
  "type": "PROJECTILE",
  "projectile": {
    "launch_properties": { "velocity": 1.5 },
    "projectile": {
      "divergence": 0,
      "homing_angle": 2.0,
      "perks": { "pierce": 1, "bounce": 0, "ricochet": 0 }
    }
  }
}
```

Key behaviours:

| Field | Description |
|---|---|
| `launch_properties.velocity` | Travel speed |
| `launch_properties.extra_launch_count` | Additional projectiles fired after the first, with `extra_launch_delay` ticks between each |
| `launch_properties.extra_launch_mod` | For channeled spells, only fire extra projectiles on every Nth channel tick |
| `projectile.divergence` | Random spread angle — creates a shotgun-like pattern |
| `projectile.homing_angle` | Degrees/tick the projectile steers towards its followed target |
| `direct_towards_target` | If `true`, aims directly at the entity rather than inheriting the caster's look direction |
| `direction_offsets[]` | Per-launch yaw/pitch offsets — cycled by sequence index or channel tick index |

For channeled projectile spells, each channel tick fires one projectile. `direction_offsets` cycles through the offset list using the channel tick index, which allows multi-directional bursts (e.g. `arcane_missile`).

## METEOR

Spawns a `SpellProjectile` in `FALL` mode above the target, which drops straight down. Impacts fire on landing.

```json
"deliver": {
  "type": "METEOR",
  "meteor": {
    "launch_height": 12,
    "launch_radius": 0,
    "projectile": { ... }
  }
}
```

| Field | Description |
|---|---|
| `launch_height` | How many blocks above the target the projectile spawns |
| `launch_radius` | Random horizontal offset at spawn — spreads a volley around the target point |
| `projectile.divergence` | Applied only from the second projectile onward (`divergence_requires_sequence: 1`) |

When `extra_launch_count > 0`, a volley of meteors is fired with staggered delays, creating a rain effect.

## CLOUD

Spawns one `SpellCloud` entity per target (or at the target location). The cloud lives for `time_to_live_seconds`, then checks for entities in its `volume` area every `impact_tick_interval` ticks and runs the spell's impacts on each one found.

```json
"deliver": {
  "type": "CLOUD",
  "clouds": [
    {
      "volume": { "radius": 3.0, "area": { "vertical_range_multiplier": 0.3 } },
      "time_to_live_seconds": 5,
      "impact_tick_interval": 8,
      "impact_cap": 0,
      "placement": { "location_offset_by_look": 4.0, "force_onto_ground": true }
    }
  ]
}
```

`clouds` is an array — you can spawn multiple clouds per cast (e.g. a fan of fire patches). Each cloud uses the `placement` sub-object to position itself relative to the caster's look direction.

## SHOOT_ARROW

Fires a standard Minecraft arrow carrying the spell's impacts. The arrow behaves like a normal arrow but triggers `performImpacts` on hit.

```json
"deliver": {
  "type": "SHOOT_ARROW",
  "shoot_arrow": {
    "consume_arrow": true,
    "divergence": 3.0,
    "arrow_critical_strike": true
  }
}
```

Use the top-level `arrow_perks` block to modify arrow damage, velocity, pierce, or override its visual model.

## AFFECT_ARROW

Does not fire an arrow. Instead, registers the spell on the caster's next natural bow/crossbow shot. When that arrow lands, the spell's impacts fire on the hit entity in addition to normal arrow damage.

Useful for archery buffs that enhance the next shot rather than fire a new projectile.

## MELEE

Sends an `AttackAvailable` packet to the client, which performs OBB (oriented bounding box) collision detection for the swing and reports hits back to the server. This is Better Combat-compatible and produces natural-feeling melee arcs.

```json
"deliver": {
  "type": "MELEE",
  "melee": {
    "allow_airborne": true,
    "attacks": [
      {
        "damage_bonus": 0.0,
        "duration": 10,
        "delay": 0.25,
        "additional_strikes": 0,
        "hitbox": { "length": 1.2, "width": 1.0, "height": 1.0, "arc": 120 },
        "animation": { "id": "spell_engine:weapon_cleave" }
      }
    ]
  }
}
```

`attacks` is a list — each entry is one swing. For channeled spells, the channel tick index cycles through the attacks list (e.g. alternating left/right slashes for a flurry skill).

| Field | Description |
|---|---|
| `damage_bonus` | Additive damage multiplier on top of the spell's normal impact damage |
| `duration` | Swing window in ticks (0 = use weapon attack cooldown) |
| `delay` | Fraction of `duration` before the hitbox activates (windup) |
| `additional_strikes` | Extra hits on the same swing (e.g. double-hit) |
| `forward_momentum` | Pushes the caster forward during the swing |
| `hitbox.arc` | Angular cone in degrees — 0 means no angular check |

## STASH_EFFECT

Applies a status effect to the target. The effect stores the spell's triggers; when one of those triggers fires (e.g. the next melee hit), the spell's impacts execute at that moment.

```json
"deliver": {
  "type": "STASH_EFFECT",
  "stash_effect": {
    "id": "mymod:my_buff_effect",
    "duration": 10,
    "amplifier": 0,
    "stacking": false,
    "consume": 1,
    "impact_mode": "PERFORM",
    "triggers": [ { "type": "MELEE_IMPACT" } ]
  }
}
```

`impact_mode`:
- `PERFORM` — executes impacts directly on the target available at trigger time.
- `TRANSFER` — spawns a projectile at trigger time that carries the impacts to a distant target.

## Delivery Delay

All delivery types support a top-level `delay` field (in ticks). The delivery is scheduled for that many ticks in the future. When the delay expires, both the caster and all targets are validated to be alive before proceeding.

```json
"deliver": { "type": "DIRECT", "delay": 10 }
```

This is useful for telegraphed strikes (e.g. a meteor that shows a warning indicator before landing) or for desynchronising the visual cast from the actual damage window.
