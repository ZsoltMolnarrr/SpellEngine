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
`type: "CHARGE"` — the player holds to charge and may **release early**. Works like a bow: the
spell's base impact values describe a **full charge**, and releasing earlier deals proportionally
less. "The longer you hold, the stronger it hits."

Two mechanics scale with the hold time (both mapped through `charge.curve`):

1. **Innate output scaling** (on by default) — the spell's damage/heal/knockback output is
   multiplied by the curved charge ratio. `output_scaling` controls how strongly:
   ```
   output multiplier = 1 - output_scaling * (1 - curve(charge ratio))
   ```
   With the default `1.0` the output is fully proportional to the curved ratio (a half-charge
   LINEAR release deals half damage). Smaller values dampen the swing — e.g. `0.33` confines it
   to the top third (a zero-charge release would still deal ~67%); `0` disables innate scaling
   entirely (constant output regardless of hold time).
2. **The `bonus` modifier** (optional) — a [spell modifier](06-impacts.md) describing extra,
   non-output effects at 100% charge, scaled down toward zero for earlier releases. Use it for
   things the output multiplier can't express: faster/bigger projectiles, added range, perks, etc.

Because the listed impact values mean "full charge", tooltip damage estimation stays truthful:
the `{damage}` range widens downward to the weakest allowed release automatically.

```json
"cast": {
  "duration": 1.5,
  "type": "CHARGE",
  "charge": {
    "curve": "EASE_IN_QUART",
    "min_release_ratio": 0.2,
    "output_scaling": 0.33,
    "bonus": {
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
| `output_scaling` | float | `1.0` | How strongly the curved ratio scales innate output: `1` = fully proportional (bow-like), `0` = constant output |
| `curve` | enum | `LINEAR` | Maps raw charge ratio (0..1) to the output/bonus scaling factor |
| `bonus` | Modifier | `{}` | Bonus applied at 100% charge, scaled down toward 0 by the curved ratio |

- Don't put `power_modifier` in `bonus` to make damage scale with charge — that's what
  `output_scaling` is for, and unlike the bonus it is reflected in tooltip damage estimation.
- The `bonus` reuses the spell-modifier vocabulary, so the same fields equipment/talents use drive
  the charge — e.g. `projectile_launch` (faster), `projectile_perks` (pierce/ricochet),
  `projectile_scale_multiply` (bigger projectile render + hitbox), `range_add`, effect amplifiers,
  etc. (`spell_pattern` is ignored; `impact_filters` still scope which impacts it boosts.)
- A spell instantly cast by a temporary effect releases at full charge automatically.
- `curve` values (per [easings.net](https://easings.net)): `LINEAR`, `EASE_IN_QUAD`, `EASE_OUT_QUAD`,
  `EASE_IN_OUT_QUAD`, `EASE_IN_QUART`, `EASE_OUT_QUART`, `EASE_IN_OUT_QUART`, `EASE_IN_EXPO`,
  `EASE_OUT_EXPO`, `EASE_IN_OUT_EXPO`. IN = slow start (rewards near-full charges), OUT = fast early
  payoff, IN_OUT = slow at both ends. The curve shapes both the innate output multiplier and the
  `bonus` — e.g. `EASE_IN_QUART` keeps most of the payoff in the last stretch of the hold.

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
charge.output_scaling = 0.33F;                   // damage swings in the top third; 1 (default) = fully proportional
charge.bonus.projectile_scale_multiply = 1.0F;   // non-output extras still go through `bonus`
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
