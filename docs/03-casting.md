# Casting

Casting is configured inside `active.cast` for `ACTIVE` spells.

## Cast Modes

`duration` defines the casting time. When `duration > 0`, the `type` field selects **one** casting
mechanic, and only the matching sub-block (`channel` / `charge`) is read:

| `type` | Mechanic |
|---|---|
| `STANDARD` (default) | Single delivery on full completion; cannot be released early. |
| `CHANNEL` | Repeated deliveries spread evenly across the duration. |
| `CHARGE` | May be released early; the bonus scales with how long it was held. |

> Summons (AI casters) only perform `INSTANT` and `STANDARD` casts. `CHANNEL` and `CHARGE` rely on
> player-input timing, so summons silently skip them.

### Instant
`duration: 0` — spell fires immediately on key press. `type` is ignored.

```json
"cast": { "duration": 0 }
```

### Standard
`duration > 0`, `type: "STANDARD"` (the default) — player holds the key, spell fires once when the
cast bar completes.

```json
"cast": { "duration": 1.0 }
```

### Channeled
`type: "CHANNEL"` — spell fires `channel.ticks` times, evenly spaced over `duration` seconds.

```json
"cast": { "duration": 2.0, "type": "CHANNEL", "channel": { "ticks": 4 } }
```
> `channel.ticks` is the **total number of releases**, not a tick interval.

| `channel` field | Type | Default | Description |
|---|---|---|---|
| `ticks` | int | `0` | Number of deliveries during channeling |
| `release_fx` | bool | `false` | Send release FX/animation on each channel tick |

### Charged
`type: "CHARGE"` — the player holds to charge and may **release early**. The base spell always fires;
on top of it, `charge.bonus` (a [spell modifier](06-impacts.md)) is applied **scaled by how long the
spell was held**, mapped through `charge.curve`. "The longer you hold, the stronger it hits."

```json
"cast": {
  "duration": 1.5,
  "type": "CHARGE",
  "charge": {
    "curve": "EASE_IN_QUART",
    "min_release_ratio": 0.2,
    "bonus": {
      "power_modifier": { "power_multiplier": 1.5 },
      "projectile_launch": { "velocity": 0.8 },
      "projectile_scale_multiply": 1.0,
      "range_add": 32.0
    }
  }
}
```

| `charge` field | Type | Default | Description |
|---|---|---|---|
| `min_release_ratio` | float | `0.2` | Below this charge ratio the cast fizzles (no impact, no cost) |
| `curve` | enum | `LINEAR` | Maps raw charge ratio (0..1) to the bonus scaling factor |
| `bonus` | Modifier | `{}` | Bonus applied at 100% charge, scaled down toward 0 by the curved ratio |

- The `bonus` reuses the spell-modifier vocabulary, so the same fields equipment/talents use drive
  the charge — e.g. `power_modifier` (harder hit), `projectile_launch` (faster), `projectile_perks`
  (pierce/ricochet), `projectile_scale_multiply` (bigger projectile render + hitbox), `range_add`,
  effect amplifiers, etc. (`spell_pattern` is ignored; `impact_filters` still scope which impacts it boosts.)
- A spell instantly cast by a temporary effect releases at full charge automatically.
- `curve` values (per [easings.net](https://easings.net)): `LINEAR`, `EASE_IN_QUAD`, `EASE_OUT_QUAD`,
  `EASE_IN_OUT_QUAD`, `EASE_IN_QUART`, `EASE_OUT_QUART`, `EASE_IN_OUT_QUART`, `EASE_IN_EXPO`,
  `EASE_OUT_EXPO`, `EASE_IN_OUT_EXPO`. IN = slow start (rewards near-full charges), OUT = fast early
  payoff, IN_OUT = slow at both ends.

## Cast Fields

| Field | Type | Default | Description |
|---|---|---|---|
| `duration` | float | `0` | Cast time in seconds (0 = INSTANT) |
| `type` | enum | `STANDARD` | Casting mechanic: `STANDARD`, `CHANNEL`, or `CHARGE` |
| `channel` | object | `null` | Channeling config (read when `type: CHANNEL`) |
| `charge` | object | `null` | Charge config (read when `type: CHARGE`) |
| `animation` | object | `null` | Player animation during cast phase |
| `animation_pitch` | bool | `true` | Whether animation follows player pitch |
| `haste_affected` | bool | `true` | Whether spell haste shortens this cast |
| `movement_speed` | float | `0.2` | Movement speed multiplier while casting |
| `start_sound` | Sound | `null` | Played once at cast start |
| `sound` | Sound | `null` | Looped during cast |
| `particles` | array | `[]` | Particles emitted during cast |

> **Deprecated:** the flat `channel_ticks` / `channeled_release_fx` fields still work (they take
> priority when set, for backward compatibility) but are superseded by `type: CHANNEL` + `channel`.

## Building in Java

```java
SpellBuilder.Casting.instant(spell);                 // INSTANT
SpellBuilder.Casting.cast(spell, 1.0F);              // STANDARD
SpellBuilder.Casting.channel(spell, 2.0F, 4);        // CHANNEL (4 ticks over 2s)

var charge = SpellBuilder.Casting.charge(spell, 1.5F, Curve.EASE_IN_QUART); // CHARGE
charge.min_release_ratio = 0.2F;
charge.bonus.power_modifier = new Spell.Impact.Modifier();
charge.bonus.power_modifier.power_multiplier = 1.5F;
charge.bonus.projectile_scale_multiply = 1.0F;
```

## Release

The `release` block controls what happens when the spell fires (after cast finishes).

```json
"release": {
  "animation": { "id": "spell_engine:one_handed_projectile_release" },
  "sound": { "id": "spell_engine:generic_cast_1" },
  "particles": [ { ... } ]
}
```

## Animations

Animation IDs follow the pattern `spell_engine:ANIMATION_NAME`. Available built-in animations are the filenames (without `.json`) inside [`player_animations/`](../common/src/main/resources/assets/spell_engine/player_animations/).

Common pairs:

| Cast | Release |
|---|---|
| `one_handed_projectile_charge` | `one_handed_projectile_release` |
| `one_handed_area_charge` | `one_handed_area_release` |
| `one_handed_healing_charge` | `one_handed_healing_release` |
| `one_handed_sky_charge` | — |
| `two_handed_channeling` | — |
| `archery_pull` | `archery_release` |

In JSON:
```json
"animation": { "id": "spell_engine:one_handed_projectile_charge" }
```

In Java:
```java
spell.active.cast.animation = PlayerAnimation.of("spell_engine:one_handed_projectile_charge");
```
