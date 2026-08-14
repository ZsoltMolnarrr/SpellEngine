# Spell Engine 1.10 — Migration Guide

1.10 rebuilds the particle system and consolidates the FX fields on `Spell`. It is
**breaking with no compatibility shim**: old fields are deleted, not deprecated. Existing
spell JSON and Java authoring must be updated.

It also **relocates a non-spell package** — equipment config moves out of `api.*` — which is a
compile-only break for downstream mods (§6).

Everything else in the spell API is unchanged.

---

## 1. Why

The old particle system worked, but three things were fixed at registration time that
content wanted to vary per use:

- **Orientation.** A ground-plane texture and a camera-facing texture were two separate
  registered particles — `area_effect_574` and `aura_effect_574` — pointing at the same file.
- **Motion.** The eight magic shapes times four motion presets were registered as **32**
  particle types, so `magic_spell_ascend` and `magic_spell_burst` were unrelated ids.
- **Everything else.** Colour tint, scale, lifetime and entity-following only worked on
  "template" particles. On the ~10 plain ones the fields were silently ignored.

Behaviour also lived in seven hand-written particle classes with a hand-wired factory per
entry, so adding a look meant writing Java, not data.

1.10 moves all of it into data. One generic particle implementation reads one payload, so
a single registered texture can now be spawned camera-facing or flat, rising or bursting,
tinted, scaled, faded and time-stretched — from the call site, with no new registration.

---

## 2. `SpellEngineParticles`

### One `Entry` type

`Entry` (plain) and `TemplateEntry` (customisable) are merged. Every entry is a texture plus
its own natural defaults:

```java
public static final Entry flame = add(new Entry("flame", Texture.vanilla("flame")).lifetime(16)
        .defaults(p -> p.render(Render.LIT).scale(0.15F, 0.33F).drag(0.96F).collides(true)));
```

The category lists (`simpleEntries()`, `templateEntries()`, `areaEffects()`, `signEffects()`)
are gone — there is one `entries()` list. `Entry.lifetime()` returns the texture's frame count
when animated, otherwise the configured static value (default 20).

### Entries removed

| Removed | Replacement |
|---|---|
| `aura_effect_*` (14 entries) | The matching `area_effect_*` id + `facing = CAMERA` |
| `magic_<shape>_<motion>` (32 entries) | `magic_<shape>` (8 entries) + `motion` on the effect |
| `MagicParticles` nested class | Flat fields: `SpellEngineParticles.magic_arcane`, `.magic_frost`, … |
| `MagicParticles.get(Shape, Motion)` | The constant directly, or `ParticleGroupBuilder.magic(entry, motion)` |

Thirteen aura-only textures were re-registered under `area_effect_*` names. Registered particle
ids for everything else are unchanged, so resource packs are unaffected.

---

## 3. `ParticleGroup`

`ParticleBatch` is replaced by `ParticleGroup` (called `ParticleGroupEffect` in early 1.10
snapshots). It has three parts:

```json
{
  "id": "spell_engine:magic_spell",
  "appearance": { "color": 4284940287, "motion": "ASCEND" },
  "batch": { "shape": "PIPE", "count": 8, "min_speed": 0.05, "max_speed": 0.1 }
}
```

| Part | Meaning |
|---|---|
| `id` | Which registered particle — picks the texture and its defaults |
| `appearance` | What **one** particle looks like and how it moves. The complete client payload |
| `batch` | How many to spawn, where, and with what velocity. Resolved before any particle exists |

The rule for finding a field: *is this about one particle, or about the whole group?*

**Entry defaults fill in the rest.** An effect only sets what it wants; unset fields come from
the registered entry. `scale`, `opacity` and `playback_speed` **multiply** with the entry's
value — `scale(2F)` means twice the entry's own size. Everything else overrides.

See [Visuals & Audio](09-visuals-and-audio.md#particles) for the full field reference.

### Field mapping

| `ParticleBatch` | `ParticleGroup` |
|---|---|
| `particle_id` | `id` (moved to the root) |
| `origin: FEET` | `batch.anchor = ENTITY`, `batch.vertical_origin = 0.1` ⚠️ not `0` |
| `origin: CENTER` / `OVER_HEAD` | `vertical_origin = 0.5` / `1.5` |
| `origin: GROUND` / `LAUNCH_POINT` | `batch.anchor = GROUND` / `LAUNCH_POINT` |
| `shape: WIDE_PIPE` | `shape = PIPE`, `batch.width_factor = 2` |
| `rotation: LOOK` | `batch.alignment = LOOK` |
| `roll` / `roll_offset` | `batch.roll_per_tick` / `roll_offset` (`roll` was already a rate) |
| `pre_spawn_travel` | `batch.pre_travel` |
| `extent` ≥ 1000 sentinel | `batch.width_factor = 0` (makes `extent` absolute) |
| `color_rgba` | `appearance.color` |
| `scale` | `appearance.scale` |
| `follow_entity: true` | `appearance.attachment = POSITION` |
| `max_age` (lifetime multiplier) | `appearance.playback_speed` — ⚠️ **reciprocal**: `0.4` → `2.5` |
| `count` below `1` (spawn chance) | `batch.chance` — ⚠️ sub-`1` `count` now means a **period**, not a chance |

`count` below `1` also changed meaning. It used to be a spawn chance; it is now a period —
`0.25` emits one particle every 4th tick rather than one time in four. Same average density,
evenly spaced instead of random. If you wanted the randomness, move the value to the new
`chance` field. The period form needs a tick loop, so it only applies to continuous FX; at a
one-shot site a fractional `count` simply emits, and `chance` is the way to make it occasional.

Status effect particles built with `BuffParticleSpawner` keep the random look by default —
its `spacing` is `RANDOM` unless you ask for `EVEN`.

`max_age` is the one that silently changes meaning. It multiplied the lifetime; `playback_speed`
scales the whole timeline, so a shorter life is a *higher* number.

### New capability

`facing` (`CAMERA` / `GROUND` / `UPRIGHT` / `VELOCITY`) · `motion` presets (`STATIC`, `FLOAT`,
`ASCEND`, `DECELERATE`, `BURST`, `DRIFT`) with `gravity` / `drag` overrides · `render` sheet and
`glow` · `opacity_curve` (fade in / hold / fade out, eased) · `scale_multiplier` + `scale_easing`
· `color_variance`, `scale_variance`, `lifetime_variance` · `playback_speed` (negative plays the
sprite sequence backwards) · `collides` · `Shape.NONE` for a single placed, motionless billboard.

### Authoring

`ParticleGroupBuilder` is the entry point. Named methods configure the appearance; `batch(...)`
configures the batch and returns the finished effect:

```java
ParticleGroupBuilder.magic(SpellEngineParticles.magic_spell, Motion.ASCEND)
        .color(Color.ARCANE)
        .batch(Batches.casting(8, 0.1F));
```

Starting points: `of(entry)`, `magic(entry, motion)`, `zone(entry)` (ground plane),
`aura(entry)` (camera-facing, follows and scales with the entity). `Batches` holds composable
layout presets — `impact`, `casting`, `travel`, `cloud`, `shockwave`, `ground`, `placed`,
`popUp`, `helix`, `cone` — combinable with `andThen`.

---

## 4. Spell data structure

### One-shot FX is bundled

Every site describing a **moment** now carries a single `visuals` bundle (`Fx.Visuals`:
`particles` + `models`) with `sound` beside it.

```json
"impacts": [ {
  "action": { "type": "DAMAGE", "damage": { "spell_power_coefficient": 1.0 } },
  "visuals": { "particles": [ … ], "models": [ … ] },
  "sound": { "id": "…" }
} ]
```

| Was | Now |
|---|---|
| `release.particles` + `release.model_fx` | `release.visuals` |
| `impacts[].particles` + `.model_fx` | `impacts[].visuals` |
| `area_impact.particles` + `.model_fx` | `area_impact.visuals` |
| `deliver.melee.attacks[].particles` + `.model_fx` | `…attacks[].visuals` |
| `deliver.clouds[].spawn` / `.despawn` particles + models | `.spawn.visuals` / `.despawn.visuals` |
| `deliver.clouds[].impact_particles` | `deliver.clouds[].impact` |
| `target.beam.block_hit_particles` | `target.beam.block_hit` |
| `teleport.depart_particles` + `depart_model_fx` | `teleport.depart` |
| `teleport.arrive_particles` + `arrive_model_fx` | `teleport.arrive` |
| `teleport.fizzle.particles` | `teleport.fizzle.visuals` |
| `arrow_perks.launch_particles` | `arrow_perks.launch_visuals` |
| `modifiers[].release_particles` | `modifiers[].release` |
| `VFX` (summon `spawn_fx` / `despawn_fx`, `group_spawn_fx`) | `Fx.Visuals`; inner `model_fx` → `models` |

A site that previously set particles and models **separately** now writes one bundle:

```java
// before
cloud.spawn.particles = List.of( … );
cloud.spawn.model_fx  = frostSpikeModels();

// after
cloud.spawn.visuals = Fx.Visuals.of( … ).models(frostSpikeModels());
```

The same applies to `release`, which had *two* particle lists — `particles` and
`scaled_with_ranged`. Both collapse into a single `release.visuals`; the range-scaled entries
just carry `scale_with = RANGE` (see below). That merge is the point: one list, mixed sizing.

**Continuous FX deliberately stayed plain lists** — they describe an ongoing state, not a
moment, and are emitted on a different schedule: `active.cast.particles`,
`deliver.clouds[].client_data.particles` and `.interval_particles`,
`…projectile.client_data.travel_particles`, `arrow_perks.travel_particles`, and a summon
behaviour's `existence_particles`.

Sound and light stay as sibling fields rather than joining the bundle. One grouping object
composes with any number of siblings; pre-combined `visuals+sound`, `visuals+light`,
`visuals+sound+light` variants would multiply out and force a rename whenever a site gained or
lost an FX kind.

### Helpers that return lists

`Fx.Visuals.of(...)` is varargs. Authoring helpers that return a `List` need the list forms:

```java
Fx.Visuals.ofParticles(myParticleList);      // static
someVisuals.particles(myParticleList);       // fluent
someVisuals.models(myModelList);
```

Returning `Fx.Visuals` from the helper instead is usually tidier — a helper named
`fireImpactParticles()` returning a bundle is better renamed `fireImpactVisuals()`.

### Range scaling is declarative

`release.scaled_with_ranged` and `release.model_fx_scaled_with_ranged` are gone. An effect now
declares its own sizing:

```java
ParticleGroupBuilder.of(SpellEngineParticles.area_swirl)
        .scaleWith(Fx.ScaleWith.RANGE)
        .batch(Batches.placed(1));
```

Because the declaration sits on the **individual effect**, one `visuals` bundle freely mixes
range-scaled and fixed-size effects — impossible when the whole bundle was the unit.

Emission sites bind only the magnitudes they can honestly supply (`Fx.Context`). `RANGE` is
bound at release, where a spell's reach genuinely describes the size of what is drawn; a site
anchored on a distant target does not bind it, and an effect asking for it there keeps its
authored size and warns once rather than inventing a number.

> ⚠️ **Semantic change.** The old field **replaced** a particle's `scale` with the range.
> `scale_with` **multiplies** by it. A particle ported from `scaled_with_ranged` wants
> `scale = 1` — an authored `scale` that used to be dead code now silently takes effect.
> Models already multiplied and port unchanged.

---

## 5. Other removals in 1.10

| Removed | Replacement |
|---|---|
| `Spell.ProjectileModel`, `ProjectileData.Client.model`, `Cloud.ClientData.model`, `ArrowPerks.override_render` | `composite_model` (projectiles, arrows), `model_fx` (clouds) |
| `Cast.channel_ticks`, `Cast.channeled_release_fx`, `resolvedType()` | `cast.type = CHANNEL` + a `channel` block; read `cast.type` directly |
| `ModelEffect.Easing` | Top-level `Easing` (same constants; also used by particle curves) |
| `EntityImmunity` (`api.effect`) | `LivingEntityImmunity` (`api.entity`) — `apply(entity, damageType, damageTypeTag, indirect, effectAnyHarmful, ticks)` |
| `RemoveOnHit.configure(effect, boolean)` | `RemoveOnHit.configure(effect, Trigger)` — `Trigger.ANY_HIT` for the old `true` |

---

## 6. Package relocations

Not every 1.10 break is about FX. This line also **relocates packages that never belonged
where they sat**. A relocation is a **pure move**: the classes are byte-for-byte identical —
same names, fields, signatures and behaviour — only their package, and therefore their `import`
line, changes. It breaks compilation in downstream mods and nowhere else. There is no runtime
component and no data component; spell JSON, resource packs and generated files are untouched.

### Moves in 1.10

| From | To | What moved |
|---|---|---|
| `net.spell_engine.api.config` | `net.spell_engine.rpg_series.config` | Equipment/attribute config: `WeaponConfig`, `ArmorSetConfig`, `ShieldConfig`, `EffectConfig`, `AttributeModifier`, `ConditionalAttributes`, `ConfigFile`, `ConfigUtil` |

**Why.** `api.*` is the spell-authoring API; none of these eight classes describe a spell. They
configure weapon / armor / shield stats and attribute modifiers, and are consumed by the item
API that already lives under `net.spell_engine.rpg_series.item`. Moving them beside their only
structural owner puts equipment config in the equipment package and drops an `rpg_series → api`
back-reference that inverted the intended layering. `rpg_series` is an equally public surface —
downstream mods already import `rpg_series.item.*` — so nothing becomes less reachable.

### Fixing a downstream mod

Every reference was a plain `import` — no wildcard imports, no fully-qualified inline uses — so
the fix is one substitution across the mod's Java sources:

```bash
grep -rl 'net\.spell_engine\.api\.config' <mod>/ | grep -v /build/ | grep '\.java$' \
  | xargs sed -i '' 's/net\.spell_engine\.api\.config/net.spell_engine.rpg_series.config/g'
```

Then set `spell_engine_version` to the 1.10 build that contains the move and rebuild. A
relocation can only surface as an unresolved-import / `cannot find symbol` compile error, so a
clean `:fabric:compileJava` on each consumer is the whole verification. Grep for the old package
name first to confirm nothing lingers.

> Within SpellEngine, `rpg_series.config.Defaults` was renamed to `LootDefaults` in the same
> pass, to disambiguate it from the incoming equipment-config classes. It is SE-internal — no
> downstream mod imported it — so it needs no action outside SpellEngine.

### Recipe for any future move

This is the template for every package relocation this line ships, not a one-off:

1. The classes move **unchanged** — never fold a rename or signature change into a move; keep the
   two as separate, individually reviewable steps.
2. Downstream is a **mechanical import rewrite** — the two-line grep/sed above, retargeted at the
   old and new package names.
3. **Confirm with a grep** for the old package name before building.
4. **Verify by compiling** each consumer — a move has no runtime or data surface, so if it
   compiles, it is done.

---

## 7. Migration checklist

1. **Particle construction** — replace every `new ParticleBatch(...)` with
   `ParticleGroupBuilder`. Work through the [field mapping](#field-mapping); watch
   `FEET → 0.1`, `WIDE_PIPE → PIPE + width_factor 2`, and the reciprocal `max_age`.
2. **Particle ids** — `magic_<shape>_<motion>` → `magic_<shape>` + `.motion(...)`;
   `aura_effect_N` → `ParticleGroupBuilder.aura(area_effect_N)`.
3. **Types** — `ParticleBatch[]` fields become `List<ParticleGroup>`.
4. **Bundle one-shot FX** — move `particles` / `model_fx` pairs into `visuals` per the
   [table](#one-shot-fx-is-bundled). Where they were set on separate lines, merge them into one
   bundle. Leave continuous lists alone — a compile error is the reliable signal: continuous
   sites still accept `particles`, moment sites no longer do.
5. **Range scaling** — move `scaled_with_ranged` effects into `visuals` with
   `scale_with = RANGE`, and **reset their `scale` to 1**.
6. **Legacy models / channelling** — see [other removals](#5-other-removals-in-110).
7. **Package imports** — rewrite `net.spell_engine.api.config.*` imports to
   `net.spell_engine.rpg_series.config.*`, then bump `spell_engine_version`. See
   [package relocations](#6-package-relocations).
8. **Rebuild and re-run datagen**, then diff the generated JSON. The generated files are the
   real contract; a field that silently stopped serialising shows up there.

### Vanilla particle ids still work

`"minecraft:flame"`, `"crit"`, `"smoke"` and third-party ids are supported, but only the
`batch` geometry applies — their factories know nothing about our payload, so the whole
`appearance` block is ignored. Register an equivalent if you need colouring, scaling or a
lifetime change.
