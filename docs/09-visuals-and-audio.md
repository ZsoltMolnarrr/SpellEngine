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
| `deliver.projectile.projectile.client_data.travel_particles` | While projectile is in flight |

## Projectile Models

Custom projectile visuals are defined under `deliver.projectile.projectile.client_data.model`:

```json
"model": {
  "model_id": "mymod:spell_projectile/my_projectile",
  "scale": 1.0,
  "light_emission": "GLOW",
  "orientation": "TOWARDS_MOTION"
}
```

Place the model file at `assets/MOD_ID/models/spell_projectile/MY_PROJECTILE.json` — Spell Engine registers these automatically. No Java registration needed.

`orientation` options: `TOWARDS_CAMERA`, `TOWARDS_MOTION`, `ALONG_MOTION`.

Dynamic lighting (`light_level` field) requires the LambDynamicLights mod.

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
