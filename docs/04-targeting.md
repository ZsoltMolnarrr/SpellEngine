# Targeting

The `target` block defines who the spell selects before delivery begins.

## Target Types

| Type | Description |
|---|---|
| `NONE` | No targeting — delivery fires immediately with no entity context |
| `CASTER` | Selects the player themselves (self-buffs, self-heals) |
| `AIM` | Selects the entity or ground position under the cursor |
| `BEAM` | Continuous raycast from caster to cursor; selects all entities along the line |
| `AREA` | Selects all entities in a sphere centred on the caster |
| `FROM_TRIGGER` | Passive spells only — inherits the target from the triggering event |

```json
"target": { "type": "AIM" }
```

## How Targeting Feeds into Delivery

Targeting resolves a list of entities (and optionally a world position) which are then passed to the delivery stage. The way that list is used depends on the delivery type:

- `DIRECT` — impacts are applied to each entity individually.
- `PROJECTILE` / `METEOR` — one projectile is fired **per entity** in the target list. With `AIM` this is typically one or zero projectiles. With `BEAM` it can be several.
- `CLOUD` — one cloud is placed per entity (or at the target location if no entity).

This means targeting type and delivery type must be chosen together. A `PROJECTILE` spell with `AREA` targeting will fire one projectile at every entity in range simultaneously.

## AIM

`AIM` resolves to at most one entity (the closest one under the cursor) plus the ground-level position the cursor points at. Both are forwarded to delivery — the entity for homing/direct impacts, the position for ground-targeted deliveries like `CLOUD` or `METEOR`.

```json
"target": {
  "type": "AIM",
  "aim": {
    "required": false,
    "sticky": false,
    "use_caster_as_fallback": false,
    "reposition_vertically": 0.0
  }
}
```

| Field | Description |
|---|---|
| `required` | If `true`, an entity must be under the cursor or the cast fails with a HUD error message |
| `sticky` | Locks onto the initially targeted entity for the full cast duration |
| `use_caster_as_fallback` | Uses the caster as target when no entity is under the cursor |
| `reposition_vertically` | Shifts the aim position up or down (blocks) when targeting ground; ignored when an entity is targeted |

> With `required: false` and no entity targeted, delivery still fires — aimed at the cursor's ground position. This is the typical setup for ground-targeted spells like clouds and meteors.

## BEAM

Selects all entities that fall within the continuous raycast line from the caster to the cursor. Delivery fires once per entity found. The beam's visual properties are purely cosmetic and do not affect targeting logic.

```json
"target": {
  "type": "BEAM",
  "beam": {
    "color_rgba": 16711680,
    "inner_color_rgba": 4294967295,
    "width": 0.15,
    "luminance": "HIGH",
    "flow": 1.0
  }
}
```

## AREA

Selects all eligible entities within a sphere centred on the caster's mid-body. Unlike `AIM`, area spells **always proceed to cooldown** even if no entities are hit — an empty area is still a valid cast.

```json
"target": {
  "type": "AREA",
  "area": {
    "distance_dropoff": "NONE",
    "horizontal_range_multiplier": 1.0,
    "vertical_range_multiplier": 0.5,
    "include_caster": false
  }
}
```

| Field | Description |
|---|---|
| `distance_dropoff` | `NONE` = flat power for all targets; `SQUARED` = smooth falloff — full power at the centre, zero at the edge |
| `horizontal_range_multiplier` | Scales the radius along the X/Z axes |
| `vertical_range_multiplier` | Scales the radius along the Y axis — use values below `1.0` to create a flat disc rather than a full sphere |
| `include_caster` | Whether the caster is included in the target list |

> `SQUARED` dropoff formula: `multiplier = (range² − dist²) / range²`. An entity exactly halfway out receives ~75% power; one at the edge receives 0%.

## Target Cap

`target.cap` limits how many entities can be in the final target list. When the cap is exceeded, entities are sorted by distance to the caster and only the closest ones are kept.

```json
"target": { "type": "AREA", "cap": 5 }
```

`cap: 0` means unlimited.

## Target Conditions (Impacts)

Conditions that gate or modify individual impacts per target are set on each impact via `target_modifiers` — see [Impacts](06-impacts.md#conditional-execution).
