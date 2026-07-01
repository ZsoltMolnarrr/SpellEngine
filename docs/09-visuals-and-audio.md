# Visuals & Audio

## Sounds

Sounds use the `Sound` object: `{ "id": "namespace:sound_event_id" }`.

Built-in sounds are defined in [`SpellEngineSounds.java`](../common/src/main/java/net/spell_engine/fx/SpellEngineSounds.java). They cover generic magic casting, projectile travel, impacts, and school-specific effects (fire, frost, arcane, etc.).

Sounds can appear at multiple points:

| Location | Description |
|---|---|
| `active.cast.start_sound` | Plays once at the start of casting |
| `active.cast.sound` | Loops during the cast |
| `release.sound` | Plays when the spell fires |
| `impacts[].sound` | Plays on each impact |
| `area_impact.sound` | Plays on area impact |
| `deliver.clouds[].spawn.sound` | Plays when a cloud spawns |
| `deliver.clouds[].presence_sound` | Loops while the cloud exists |
| `deliver.projectile.projectile.travel_sound` | Loops while projectile travels |

## Particles

Particles use the `ParticleBatch` object. A batch spawns multiple particles at once with a given shape.

```json
{
  "id": "spell_engine:sparkle",
  "shape": "SPHERE",
  "origin": "CENTER",
  "count": 8,
  "spread": 0.3,
  "speed": 0.1
}
```

| Field | Description |
|---|---|
| `id` | Particle type identifier |
| `shape` | Spawn shape: `SPHERE`, `PIPE`, `LINE`, `LINE_VERTICAL`, `PILLAR` |
| `origin` | Anchor point: `CENTER`, `FEET`, `GROUND` |
| `count` | Number of particles per batch |
| `spread` | Positional spread radius |
| `speed` | Particle velocity |

Built-in particles are in [`SpellEngineParticles.java`](../common/src/main/java/net/spell_engine/fx/SpellEngineParticles.java). Vanilla particle ids also work.

Particles can appear at:

| Location | Description |
|---|---|
| `active.cast.particles` | During cast |
| `release.particles` | On release |
| `impacts[].particles` | On each impact |
| `area_impact.particles` | On area splash |
| `deliver.melee.swing.particles` | On each melee swing |
| `deliver.clouds[].spawn.particles` | When a cloud spawns |
| `deliver.clouds[].client_data.particles` | Ambient tick particles on cloud |
| `deliver.clouds[].client_data.interval_particles` | Interval particles on cloud |
| `deliver.projectile.projectile.client_data.travel_particles` | While projectile is in flight |
| `impacts[].action.teleport.depart_particles` | At departure location |
| `impacts[].action.teleport.arrive_particles` | At arrival location |

## Model FX

Model FX are animated 3D spell models spawned as short-lived world entities. Unlike particles they support smooth geometric animations — scale, translate, and rotate — over their lifetime. Use them for visuals like shockwave rings, ground spikes, or teleport portals.

Key limitations:
- Models as whole can be animated, but not individual parts inside a model

### Model file

Place the model at:

```
assets/MOD_ID/models/spell_effect/MY_EFFECT.json
```

Spell Engine discovers and registers these automatically. No Java registration is needed.

### Fields

```json
{
  "model_id": "mymod:spell_effect/ground_slam",
  "light_emission": "GLOW",
  "scale": 1.0,
  "duration": 30,
  "positioning": "CENTER"
}
```

| Field | Default | Description |
|---|---|---|
| `model_id` | — | Fully qualified model identifier |
| `light_emission` | `GLOW` | `NONE` (standard), `GLOW` (beacon beam shader), `RADIATE` (emissive) |
| `scale` | `1.0` | Base scale of the model |
| `duration` | `20` | Lifetime in ticks |
| `positioning` | `CENTER` | `CENTER`, `FEET`, `GROUND` — where the entity anchors |

### Transforms

`initial` defines the starting state before any animation runs. Each entry is a transform applied at full strength (progress = 1).

`animations` defines how the model changes over time. Each entry is a transform extended with timing.

All transforms use the same operation types and `x`, `y`, `z` fields:

| Operation | Effect of `x`, `y`, `z` |
|---|---|
| `scale` | Delta added to base scale per axis. `x: 1` doubles width. Accumulated additively across all scale transforms. |
| `translate` | Offset in blocks per axis |
| `rotate` | Degrees of rotation per axis |

#### Animation-only fields

| Field | Default | Description |
|---|---|---|
| `start` | `0` | Tick when the animation begins |
| `end` | `20` | Tick when the animation reaches full effect |
| `easing` | `LINEAR` | Easing curve — see below |

#### Easing

All functions from [easings.net](https://easings.net) are supported, using the same naming convention:

`LINEAR`, `EASE_IN_SINE`, `EASE_OUT_SINE`, `EASE_IN_OUT_SINE`, `EASE_IN_QUAD`, `EASE_OUT_QUAD`, `EASE_IN_OUT_QUAD`, `EASE_IN_CUBIC`, `EASE_OUT_CUBIC`, `EASE_IN_OUT_CUBIC`, `EASE_IN_QUART`, `EASE_OUT_QUART`, `EASE_IN_OUT_QUART`, `EASE_IN_QUINT`, `EASE_OUT_QUINT`, `EASE_IN_OUT_QUINT`, `EASE_IN_EXPO`, `EASE_OUT_EXPO`, `EASE_IN_OUT_EXPO`, `EASE_IN_CIRC`, `EASE_OUT_CIRC`, `EASE_IN_OUT_CIRC`, `EASE_IN_BACK`, `EASE_OUT_BACK`, `EASE_IN_OUT_BACK`, `EASE_IN_ELASTIC`, `EASE_OUT_ELASTIC`, `EASE_IN_OUT_ELASTIC`, `EASE_IN_BOUNCE`, `EASE_OUT_BOUNCE`, `EASE_IN_OUT_BOUNCE`

### Example — spike rising from the ground

```json
"model_fx": [
  {
    "model_id": "mymod:spell_effect/ground_spike",
    "light_emission": "GLOW",
    "duration": 30,
    "initial": [
      { "operation": "translate", "y": -1.0 },
      { "operation": "scale", "x": -1.0, "y": -1.0, "z": -1.0 }
    ],
    "animations": [
      { "operation": "scale",     "start": 0,  "end": 10, "x": 1.0, "y": 1.0, "z": 1.0, "easing": "EASE_OUT_CUBIC" },
      { "operation": "translate", "start": 0,  "end": 10, "y": 1.0,            "easing": "EASE_OUT_CUBIC" },
      { "operation": "scale",     "start": 20, "end": 30, "x": -1.0, "y": -1.0, "z": -1.0, "easing": "EASE_IN_CUBIC" },
      { "operation": "translate", "start": 20, "end": 30, "y": -1.0,            "easing": "EASE_IN_CUBIC" }
    ]
  }
]
```

**How it works:** the initial state places the model one block underground at scale 0. Over ticks 0–10 it rises to the surface and scales up to full size. It holds from ticks 10–20, then sinks back underground over ticks 20–30.

> **Scale note:** scale deltas accumulate additively across all transforms and are applied as a single call. An initial `x: -1` combined with a base scale of 1 results in scale 0 (invisible). Animating `x: +1` over 10 ticks grows it back to scale 1.

### Attachment points

`model_fx` is supported at the following locations:

| Location | Spawned at |
|---|---|
| `release.model_fx` | Caster position |
| `impacts[].model_fx` | Target position |
| `area_impact.model_fx` | Area center |
| `deliver.melee.swing.model_fx` | Caster position |
| `deliver.clouds[].spawn.model_fx` | Cloud spawn position |
| `impacts[].action.teleport.depart_model_fx` | Pre-teleport position |
| `impacts[].action.teleport.arrive_model_fx` | Post-teleport position |

### Custom operations

Third-party mods can register additional transform operations client-side:

```java
ModelEffectOperations.register("my_op", (matrices, progress, transform) -> {
    // matrices: current MatrixStack
    // progress: 0.0–1.0 with easing already applied
    // transform: the Transform object with x, y, z fields
});
```

## Projectile Models

A projectile can render one or more custom 3D models, each independently oriented, spun, and animated with the [Model FX](#model-fx) system. Models are defined under `deliver.projectile.projectile.client_data.composite_model`:

```json
"composite_model": {
  "models": [
    {
      "orientation": "TOWARDS_MOTION",
      "rotate_degrees_per_tick": 2,
      "fx": {
        "model_id": "mymod:spell_projectile/my_projectile",
        "scale": 1.0,
        "light_emission": "GLOW"
      }
    }
  ]
}
```

Place each model file at `assets/MOD_ID/models/spell_projectile/MY_PROJECTILE.json` — Spell Engine registers these automatically. No Java registration needed.

### Per-model fields

Each entry in `models` combines projectile-specific placement with a Model FX definition:

| Field | Default | Description |
|---|---|---|
| `orientation` | `TOWARDS_MOTION` | Facing relative to travel: `TOWARDS_CAMERA` (billboard), `TOWARDS_MOTION`, `ALONG_MOTION` |
| `rotate_degrees_per_tick` | `2` | Continuous spin about the view/motion axis. Set `0` for a static (e.g. flat) model |
| `rotate_degrees_offset` | `0` | Fixed rotation offset about that axis |
| `use_held_item` | `false` | Render the caster's held item instead of `fx.model_id` |
| `fx` | — | A [Model FX](#model-fx) definition — `model_id`, `light_emission`, `scale`, `positioning`, `initial`, `animations` — driving the model's appearance and animation |

The `fx` block is a full Model FX definition, so a projectile model animates exactly like a spawned Model FX (scale/translate/rotate over `initial` + `animations`, with easing). Animation time is the **projectile's age** in ticks — use it for one-shot transitions such as a scale-in on spawn; for continuous rotation use `rotate_degrees_per_tick` (animations are bounded by `start`/`end`). Two Model FX fields are unused here: `duration` (a projectile lives until impact) and `follow_entity` (the model is already rendered on the projectile); `positioning` is honored.

### Multiple models

List several entries in `models` to composite layered visuals — e.g. a motion-aligned core plus a camera-facing glow:

```json
"composite_model": {
  "models": [
    { "fx": { "model_id": "mymod:spell_projectile/fireball_core", "light_emission": "RADIATE" } },
    { "orientation": "TOWARDS_CAMERA", "rotate_degrees_per_tick": 0,
      "fx": { "model_id": "mymod:spell_projectile/fireball_glow", "light_emission": "GLOW", "scale": 1.5 } }
  ]
}
```

### Arrows

The same structure drives custom arrow visuals via `arrow_perks.composite_model`. `use_held_item` is not available for arrows (they have no captured held item), so such entries are skipped.

Dynamic lighting (`client_data.light_level` field) requires the LambDynamicLights mod.

> **Deprecated:** the legacy single-model fields `deliver.projectile.projectile.client_data.model` and `arrow_perks.override_render` are superseded by `composite_model` and slated for removal. Migrate to `composite_model` with a single `models` entry (`rotate_degrees_per_tick` defaults to `2`, matching the old default, so behaviour is unchanged).

## Spell Effect Models

Custom models rendered over entities with a specific status effect (e.g. a visual shield). Place model at `assets/MOD_ID/models/spell_effect/MY_EFFECT.json`. Register a renderer in Java:

```java
CustomModelStatusEffect.register(MyEffects.MY_EFFECT, new MyEffectRenderer());
```

See `FrostShieldRenderer` in the Wizards mod for a full example.

## Beam Visuals

For `BEAM` targeting, visual properties are configured on `target.beam`:

```json
"beam": {
  "color_rgba": 4278190335,
  "inner_color_rgba": 4294967295,
  "width": 0.15,
  "luminance": "HIGH",
  "texture_id": "textures/entity/beacon_beam.png",
  "flow": 1.0
}
```

`color_rgba` is a packed RGBA long (e.g. `0xFF0000FF` = opaque red).
