# Casting

Casting is configured inside `active.cast` for `ACTIVE` spells.

## Cast Modes

### Instant
`duration: 0` — spell fires immediately on key press.

```json
"cast": { "duration": 0 }
```

### Charged
`duration > 0`, `channel_ticks: 0` — player holds the key, spell fires on release.

```json
"cast": { "duration": 1.0 }
```

### Channeled
`duration > 0`, `channel_ticks > 0` — spell fires `channel_ticks` times, evenly spaced over `duration` seconds.

```json
"cast": { "duration": 2.0, "channel_ticks": 4 }
```
> `channel_ticks` is the **total number of releases**, not a tick interval.

## Cast Fields

| Field | Type | Default | Description |
|---|---|---|---|
| `duration` | float | `0` | Cast time in seconds |
| `channel_ticks` | int | `0` | Number of releases during channeling |
| `animation` | object | `null` | Player animation during cast phase |
| `animation_pitch` | bool | `true` | Whether animation follows player pitch |
| `haste_affected` | bool | `true` | Whether spell haste shortens this cast |
| `movement_speed` | float | `0.2` | Movement speed multiplier while casting |
| `start_sound` | Sound | `null` | Played once at cast start |
| `sound` | Sound | `null` | Looped during cast |
| `particles` | array | `[]` | Particles emitted during cast |

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
