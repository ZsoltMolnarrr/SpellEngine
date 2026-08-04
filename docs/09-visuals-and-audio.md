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

A particle effect is a `ParticleGroup`. It has three parts:

```json
{
  "id": "spell_engine:magic_spell",
  "appearance": { "color": 4284940287, "motion": "ASCEND" },
  "batch":    { "shape": "PIPE", "count": 8, "min_speed": 0.05, "max_speed": 0.1 }
}
```

| Part | Meaning |
|---|---|
| `id` | Which registered particle to spawn — picks the texture and its defaults |
| `appearance` | What one particle looks like and how it moves. Sent to the client as one payload |
| `batch` | How many to spawn, where to place them, what velocity to give them. Resolved before any particle exists |

The split is the rule of thumb for finding a field: *"is this about one particle, or about the whole group?"* Colour, size, fade and motion are `appearance`. Count, shape, placement and speed are `batch`.

### Authoring in Java

Use [`ParticleGroupBuilder`](../common/src/main/java/net/spell_engine/api/spell/fx/ParticleGroupBuilder.java). Named methods configure the particle; `batch(...)` configures the batch and closes the builder:

```java
ParticleGroupBuilder.magic(SpellEngineParticles.magic_spell, Motion.ASCEND)
        .color(Color.ARCANE)
        .batch(Batches.casting(8, 0.1F));
```

**Starting points:**

| Call | Use for |
|---|---|
| `of(entry)` / `of(id)` | Any particle |
| `magic(entry, motion[, color])` | The `magic_*` particles, choosing how they move |
| `zone(entry)` | A flat effect on the ground plane — runes, circles, shockwaves |
| `aura(entry)` | An upright effect that follows and scales with its entity |

`zone` and `aura` are the same textures rendered two ways. In earlier versions these were registered as separate `area_*` / `aura_*` particles; orientation is now chosen at the call site.

**Batch presets** live in `ParticleGroupBuilder.Batches` as composable `Consumer<Batch>` values:

| Preset | Layout |
|---|---|
| `impact(count, speed)` | Burst outward from the target's centre |
| `casting(count, speed)` | Rising swirl around the caster's feet, at double body radius |
| `travel(count, speed)` | Trailing wake around a projectile, oriented to its flight path |
| `cloud(count, speed)` | Column rising from within the source's footprint |
| `shockwave(count, speed, preTravel)` | Expanding ring along the floor |
| `ground(count)` | Flat on the floor below the source, motionless |
| `placed(count)` | On the source itself, motionless — auras, explosions |
| `popUp()` | A sign or icon drifting above the entity's head |
| `helix(count, speed, degreesPerTick, offset)` | One strand of a spiralling wake — two `180` apart make a double helix |
| `cone(count, speed, angle)` | Forward in a spread cone |

Presets compose with `andThen`, and anything not covered stays reachable with a plain lambda:

```java
.batch(Batches.impact(10, 0.3F).andThen(b -> b.extent(0.5F)))
.batch(b -> b.shape(Shape.SPHERE).count(4).speed(0.1F, 0.4F))
```

### Entry defaults

Every registered particle carries its own defaults — lifetime, render sheet, base scale, colouring, motion. A builder only sets what you ask for; everything else falls back to the entry. That is what lets one texture serve several roles.

Three fields **multiply** with the entry's value instead of replacing it:

| Field | `scale(2F)` means |
|---|---|
| `scale`, `opacity`, `playback_speed` | twice the entry's own size, whatever that is |

Everything else overrides outright. Built-in particles are listed in [`SpellEngineParticles.java`](../common/src/main/java/net/spell_engine/fx/SpellEngineParticles.java).

### `appearance` fields

| Field | Default | Description |
|---|---|---|
| `playback_speed` | `1.0` | Scales the whole timeline — lifetime and sprite animation together. `2.5` is a quick flash, `0.5` lingers. Negative runs the sprite sequence backwards |
| `lifetime_variance` | `0.0` | Random lifetime spread, `0..1`. `0.5` gives each particle a life in `[0.5x, 1.5x]` so a batch thins out instead of vanishing at once |
| `color` | `-1` | Tint, packed RGBA. `-1` leaves the texture untinted. The alpha component multiplies `opacity` |
| `color_variance` | `0.0` | Random darkening at spawn, `0..1`. Gives a batch visible internal variation |
| `opacity` | `1.0` | Peak opacity |
| `opacity_curve` | none | Fade envelope — see below |
| `scale` | `1.0` | Size at spawn |
| `scale_with` | `NONE` | Multiplies `scale` by a magnitude resolved at emit time. `RANGE` = the spell's effective range. Only resolved for effects emitted as part of a `visuals` bundle |
| `scale_variance` | `0.0` | Random size spread, as a fraction of `scale` |
| `scale_multiplier` | `1.0` | Size at death, as a multiple of the spawn size. Only read when `scale_easing` is set |
| `scale_easing` | none | Interpolates `scale` toward `scale * scale_multiplier` over the lifetime |
| `facing` | entry | `CAMERA`, `GROUND` (flat on the floor), `UPRIGHT` (Y-axis billboard), `VELOCITY` (aligned to travel) |
| `glow` | entry | Renders at full brightness, ignoring world light |
| `render` | entry | `OPAQUE`, `TRANSLUCENT`, `LIT` |
| `motion` | entry | Motion preset — see below |
| `gravity` | preset | Downward acceleration per tick. Negative values rise |
| `drag` | preset | Fraction of velocity kept each tick |
| `collides` | `false` | Collides with blocks instead of passing through |
| `attachment` | `NONE` | `POSITION` rides the source entity, `POSITION_SCALED` also scales with it |

**Motion presets** supply gravity, drag, spawn-velocity shaping and a lifetime factor:

| Motion | Behaviour |
|---|---|
| `STATIC` | Keeps its spawn velocity. No gravity, no drag |
| `FLOAT` | Gentle randomised drift with mild drag |
| `ASCEND` | Rises steadily against gravity |
| `DECELERATE` | Strong drag — travels a short distance then halts |
| `BURST` | Thrown outward hard, then falls. Short-lived |
| `DRIFT` | Falls while drifting sideways, damping as it settles — snow, ash, embers |

**Opacity curve** is an `Easing.Curve`: ramp up, hold, ramp down. `hold` is the fraction of the lifetime spent at full opacity; whatever is left is split between whichever ramps are set.

```java
.fadeOut(0.7F, Easing.LINEAR)     // hold 70%, then fade
.fadeInOut(0.4F, Easing.LINEAR)   // fade in, hold 40%, fade out
```

```json
"opacity_curve": { "in": "LINEAR", "out": "LINEAR", "hold": 0.4 }
```

### `batch` fields

| Field | Default | Description |
|---|---|---|
| `count` | `1.0` | Particles per tick. `1`+ is a count per emission; below `1` it is a period — `0.25` emits one every 4th tick. The sub-`1` form needs a tick loop, so it only applies to continuous FX |
| `chance` | `1.0` | Probability the batch emits at all, `0..1`. Works everywhere, and composes with a fractional `count` |
| `shape` | `CIRCLE` | Placement and initial velocity pattern — see below |
| `anchor` | `ENTITY` | `ENTITY`, `GROUND` (first solid block below), `LAUNCH_POINT` (the caster's hand) |
| `vertical_origin` | `0.5` | Offset from the anchor, in units of the source's height. `0` feet, `0.5` centre, `1` head, `1.5` above |
| `alignment` | `WORLD` | `LOOK` rotates the whole pattern into the source's aim direction |
| `min_speed` / `max_speed` | `0.0` | Randomised initial speed range |
| `angle` | `0.0` | Cone spread in degrees |
| `extent` | `0.0` | Radial offset added to the source's own radius |
| `width_factor` | `1.0` | Multiplier on the source's width when placing. `0` makes `extent` absolute; `2` doubles the radius |
| `pre_travel` | `0.0` | Distance travelled along the spawn direction before appearing |
| `roll_per_tick` | `0.0` | Degrees the pattern rotates per tick — successive spawns trace a helix |
| `roll_offset` | `0.0` | Starting angle of `roll_per_tick` |
| `invert` | `false` | Flips the initial velocity, turning outward patterns inward |

**Shapes:**

| Shape | Placement / velocity |
|---|---|
| `NONE` | Exactly on the origin, motionless. For single placed billboards — explosions, ground decals. Ignores speed, `extent` and `pre_travel` |
| `CIRCLE` | Outward along the horizontal plane |
| `SPHERE` | Outward in a random 3D direction |
| `PILLAR` | Upward, from a random point *within* the radius |
| `PIPE` | Upward, from a random point *on* the radius |
| `CONE` | Forward, spread by `angle` |
| `LINE` | Straight forward |
| `LINE_VERTICAL` | Straight up |

> **Sizing ground decals:** a particle quad spans `±scale`, so it renders **twice** its scale value in blocks. A decal meant to cover a 3-block impact radius wants `scale(radius * 0.5F)`, not `scale(radius)`.

### Vanilla particles

Vanilla and third-party ids (`"minecraft:flame"`, `"crit"`, `"smoke"`) work, but only the `batch` geometry applies — their factories know nothing about our payload, so the entire `appearance` block is ignored. Register an equivalent if you need colouring, scaling or a lifetime change.

### Where particles can appear

A **moment** — something that happens once, at an instant — carries its particles and its
models together in a single `visuals` bundle (`Fx.Visuals`), with a `sound` field beside it
where the moment has audio of its own. A **presence** — something ongoing, emitted on a
schedule — stays a plain particle list.

Moments (`visuals.particles` + `visuals.models`):

| Location | Description |
|---|---|
| `release.visuals` | On release, at the caster |
| `impacts[].visuals` | On each entity an impact lands on |
| `area_impact.visuals` | On area splash, at the centre |
| `deliver.melee.attacks[].visuals` | On each melee swing |
| `deliver.clouds[].spawn.visuals` | When a cloud spawns |
| `deliver.clouds[].despawn.visuals` | When a cloud begins winding down |
| `deliver.clouds[].impact.visuals` | On each entity a cloud impacts |
| `impacts[].action.teleport.depart` / `.arrive` | Pre- and post-teleport position |
| `impacts[].action.teleport.fizzle.visuals` | Where an aborted teleport leaves the caster |
| `arrow_perks.launch_visuals` | On the shooter, as an arrow leaves |
| `target.beam.block_hit` | Where a beam meets a solid block |
| `impacts[].action.summon.group_spawn_fx` | Once per summoned group, at the group's anchor |
| `modifiers[].release` | Added to the release of any spell a modifier matches |

Two more live on a summon's behaviour rather than on the spell — see
[Summons](10-summons.md):

| Location | Description |
|---|---|
| `spawn_fx` | Per summoned entity, as it enters the world |
| `despawn_fx` | Per summoned entity, as it begins winding down |

Presences (plain lists):

| Location | Description |
|---|---|
| `active.cast.particles` | During cast |
| `deliver.clouds[].client_data.particles` | Ambient tick particles on cloud |
| `deliver.clouds[].client_data.interval_particles` | Interval particles on cloud |
| `deliver.projectile.projectile.client_data.travel_particles` | While projectile is in flight |
| `arrow_perks.travel_particles` | While an affected arrow is in flight |
| `existence_particles[].particles` | On a summon's behaviour, per interval while it lives |

### Sizing an effect by the spell's range

An effect can declare that its size follows a magnitude only known where it is emitted,
rather than being a fixed number:

```java
ParticleGroupBuilder.of(SpellEngineParticles.area_swirl)
        .scaleWith(Fx.ScaleWith.RANGE)
        .batch(Batches.placed(1));
```

The authored `scale` stays a **coefficient** — `scale(0.5F)` with `RANGE` draws at half the
spell's range — so leaving it at its default means "exactly the range".

Because the declaration sits on the individual effect, a bundle freely mixes scaled and
unscaled effects; they are emitted together, and only the ones that ask to be scaled are
copied. `RANGE` resolves the caster's *effective* reach (melee range mechanic and range
modifiers included), and is bound at release. Sites anchored on a target do not bind it —
the caster's range says nothing about how big an effect on a distant target should be — so
an effect asking for it there keeps its authored size and logs a one-time warning.

### Example — frost impact

```java
impact.visuals = Fx.Visuals.of(
        ParticleGroupBuilder.magic(SpellEngineParticles.magic_frost, Motion.BURST)
                .color(Color.FROST)
                .batch(Batches.impact(15, 0.4F)),
        ParticleGroupBuilder.zone(SpellEngineParticles.area_effect_474)
                .scale(radius * 0.5F)
                .color(Color.FROST)
                .batch(Batches.ground(1)));
```

A short-lived burst of frost shards thrown outward from the target, over a motionless decal lying flat on the floor and sized to match the damage radius.

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
| `scale_with` | `NONE` | Multiplies `scale` by a magnitude resolved at emit time — `RANGE` for the spell's effective range |
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
"visuals": {
  "models": [
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
}
```

**How it works:** the initial state places the model one block underground at scale 0. Over ticks 0–10 it rises to the surface and scales up to full size. It holds from ticks 10–20, then sinks back underground over ticks 20–30.

> **Scale note:** scale deltas accumulate additively across all transforms and are applied as a single call. An initial `x: -1` combined with a base scale of 1 results in scale 0 (invisible). Animating `x: +1` over 10 ticks grows it back to scale 1.

### Attachment points

Models live in the same `visuals` bundle as particles — under `visuals.models`, at every
location listed in [Where particles can appear](#where-particles-can-appear):

| Location | Spawned at |
|---|---|
| `release.visuals.models` | Caster position |
| `impacts[].visuals.models` | Target position |
| `area_impact.visuals.models` | Area center |
| `deliver.melee.attacks[].visuals.models` | Caster position |
| `deliver.clouds[].spawn.visuals.models` | Cloud spawn position |
| `impacts[].action.teleport.depart.models` | Pre-teleport position |
| `impacts[].action.teleport.arrive.models` | Post-teleport position |

A model can size itself by the spell's range in the same way a particle does — set
`scale_with` to `RANGE`, and its authored `scale` becomes the coefficient.

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
