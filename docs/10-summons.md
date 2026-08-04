# Spell Engine — Summons

## In a nutshell

A **summon** is a temporary, owner-bound entity spawned by a spell. It casts spells, attacks, and despawns on a timer. Data controls what it does. Java defines what it is.

To create one:

1. **Model + Java export** 🛠️ — exported as Java, wired to the standard animation states.
2. **Entity class extends `SummonedEntity`** 🛠️ — this is what makes it data-controllable.
3. **A Summon definition** 📐 — the data: what to spawn, the formation, and its behaviour.

**Result:** an entity that scales with its owner, picks targets, casts spells, and fights — no per-entity AI code.

> 🛠️ = one-time Java/asset work · 📐 = data, per spell

---

## Contents

| Section | For | Description |
|---|---|---|
| [1 · Overview](#1--overview) | everyone | Core concepts and lifecycle |
| [2 · Capability Reference](#2--capability-reference) | 📐 spell authors | Every field you can set in data |
| [3 · Authoring a Summonable Entity](#3--authoring-a-summonable-entity) | 🛠️ entity developers | Building the entity in Java |
| [4 · Limitations & Roadmap](#4--limitations--roadmap) | everyone | What it can't do yet |

---

## 1 · Overview

### 1.1 What it is

The **Summon impact** is a spell impact (`action.type = "SUMMON"`) that spawns one or more entities to act for the caster.

It looks similar to the built-in `SPAWN` action, but they serve different purposes:

| | `SPAWN` | `SUMMON` |
|---|---|---|
| Spawns an entity | ✅ | ✅ |
| Entity has full combat AI | ❌ | ✅ |
| Casts spells / attacks | ❌ | ✅ |
| Configured by a behaviour block | ❌ | ✅ |

Use `SPAWN` for simple props (a cloud, a marker). Use `SUMMON` for a pet that fights.

### 1.2 Mental model

Three parts work together:

- **The entity** 🛠️ — built once in Java (model + class). Reusable across many spells.
- **The Summon definition** 📐 — the data: which entity, how many, where, and how it behaves.
- **The spell** 📐 — references the summon through a `SUMMON` impact.

You build the entity once. After that, you spawn and re-tune it entirely from data.

### 1.3 Lifecycle

Every summon moves through three phases on a fixed timer, then disappears:

```
cast ─▶ [ spawn ] ─▶ [ active ] ─▶ [ despawn ] ─▶ gone
         can't act    fights        can't act
         (intro)                    (outro)
```

- The summon is placed at **cast time** — anchored to where the caster stood and faced.
- World spawn can be **delayed** per entity, so formations appear in sequence.
- During **spawn** and **despawn** the entity can't move or act (room for an intro/outro animation).

### 1.4 Ownership & factions

Every summon remembers an **owner** (the caster). The owner decides who is friend and who is foe:

- Enemies of the owner are valid targets.
- The owner and the owner's other pets are never attacked.
- Friendly summons (healers) can help the owner and allies.

---

## 2 · Capability Reference 📐

This section is for spell authors. It lists everything you can set on a summon.

A `SUMMON` impact looks like this:

```json
{
  "action": {
    "type": "SUMMON",
    "summon": {
      "entity_type_id": "wizards:frost_elemental",
      "spawn_count": 1,
      "placements": [
        { "location_offset_by_look": 2, "force_onto_ground": true }
      ],
      "behaviour": {
        "lifespan": { "active_seconds": 30 },
        "movement": { "follow": {} },
        "targeting": { "automatic_targeting": "HOSTILE" },
        "actions": [
          { "type": "SPELL_CAST", "spell_cast": { "spell_id": "wizards:frost_shard", "cooldown": 20 } }
        ]
      }
    }
  }
}
```

### 2.1 Top-level fields

| Field | Default | Description |
|---|---|---|
| `entity_type_id` | — | Registry id of the entity to spawn. Must be a summonable entity (see §3). |
| `behaviour` | `{}` | How the summon behaves once spawned. Covered in §2.3–2.10. |
| `placements` | `[]` | Where each entity appears. See §2.2. |
| `spawn_count` | `1` | How many entities to spawn per group. |
| `group_placements` | `[]` | Optional offsets to repeat the whole formation. See §2.2. |
| `group_count` | `1` | How many groups to spawn. |

### 2.2 Formation & placement

Controls **where** summons appear around the caster and **how a batch is arranged** — from a single pet at the caster's feet to a staggered line or a repeated formation.

Placement reuses Spell Engine's standard `EntityPlacement` (the same one the `SPAWN` action uses). The handy fields:

| Field | Description |
|---|---|
| `location_offset_by_look` | Blocks away from the caster, along their facing. |
| `location_yaw_offset` | Rotates that offset. `0` = front, `90` = right, `180` = behind, `270` = left. |
| `location_offset_y` | Vertical nudge (e.g. to float a turret). |
| `force_onto_ground` | Snap down to solid ground. |
| `apply_yaw` / `apply_pitch` | Face the spawned entity along the caster's aim. |
| `delay_ticks` | Wait this many ticks before this entity appears. |

**How counts and slots combine:**

- `spawn_count` entities are spawned. Each takes the next slot in `placements`, wrapping around if there are more entities than slots.
- If `group_placements` is set, the **whole formation repeats** `group_count` times, each copy shifted by the next group offset.

So you build a small formation in `placements`, then optionally stamp copies of it with `group_placements`.

**Examples**

A single pet, 2 blocks ahead:

```json
{ "spawn_count": 1, "placements": [ { "location_offset_by_look": 2, "force_onto_ground": true } ] }
```

A row of 3 floating turrets behind the caster, appearing one after another:

```json
{
  "spawn_count": 3,
  "placements": [
    { "location_offset_by_look": 0,   "location_offset_y": 1, "apply_yaw": true, "delay_ticks": 0 },
    { "location_offset_by_look": 1.5, "location_yaw_offset": 90,  "location_offset_y": 1, "apply_yaw": true, "delay_ticks": 10 },
    { "location_offset_by_look": 1.5, "location_yaw_offset": 270, "location_offset_y": 1, "apply_yaw": true, "delay_ticks": 20 }
  ],
  "group_placements": [ { "location_offset_by_look": 2, "location_yaw_offset": 180 } ]
}
```

### 2.3 Lifespan

Sets **how long the summon sticks around** — every summon is temporary and vanishes on its own. The intro (`spawn`) and outro (`despawn`) phases are quiet windows where it can't act, **so its spawn/despawn animations have time to play** before and after the fight.

| Field | Default | Description |
|---|---|---|
| `spawn_ticks` | `10` | Intro phase length, in ticks. |
| `active_seconds` | `60` | Active phase length, in **seconds**. |
| `despawn_ticks` | `10` | Outro phase length, in ticks. |

Total life (ticks) = `spawn_ticks + active_seconds × 20 + despawn_ticks`.

### 2.4 Movement

Decides whether the summon **roams or stays put**, and how it keeps up with its owner. Wire it as a roaming pet (`follow` + `wander`) or a fixed turret (`can_move: false`, no gravity).

| Field | Default | Description |
|---|---|---|
| `can_move` | `true` | Set `false` for a stationary turret. |
| `affected_by_gravity` | `true` | Set `false` to float. |
| `is_pushable` | `true` | Whether other entities can shove it. |
| `collision` | `ENEMIES` | `NONE` (pass through all), `ALL` (solid to all), `ENEMIES` (solid only to foes). |
| `follow` | *(off)* | If present, the summon follows its owner. See below. |
| `wander` | on | Idle drifting when there's nothing to do. |

`follow` sub-fields:

| Field | Default | Description |
|---|---|---|
| `start_distance` | `10` | Starts following when the owner is this far. |
| `stop_distance` | `4` | Stops following when this close. |
| `teleport_after_distance` | `12` | Teleports to the owner past this distance. `0` disables. |

### 2.5 Targeting

Decides **who the summon fights and how it chooses**. It can join the owner's target, strike back when hit, or hunt nearby enemies on its own — or, for a healer, seek out wounded allies instead.

| Field | Default | Description |
|---|---|---|
| `automatic_targeting` | `NONE` | `NONE`, `HOSTILE` (attack nearby enemies), `FRIENDLY` (heal nearby wounded allies), `BOTH`. |
| `revenge` | `true` | Fights back when hit. |
| `attack_with_owner` | `true` | Joins the owner's target. |
| `attack_with_owner_hits` | `1` | How many times the owner must hit a foe before the summon switches to it. |
| `look_around` | `true` | Idle head movement. |
| `detection_range` | follow range | How far it looks for targets. See below. |
| `clear_condition` | *(off)* | Optional rules for dropping a target. See below. |

`detection_range.mode`:

- `FOLLOW_RANGE` — use the entity's vanilla follow-range attribute (default).
- `MAXIMUM_ACTION_RANGE` — only as far as it can actually act (its longest spell or melee reach). Good for turrets.
- `STATIC` — a fixed `value` in blocks.

`clear_condition` (all optional, each fires on its own):

- `after_ticks` — drop the target after holding it this long.
- `out_of_detection_range` — drop the target once it flees past `multiplier ×` detection range.
- `on_action_completed` — chance to drop the target after an attack or cast finishes.

### 2.6 Actions

The `actions` list is what the summon *does*. **Order matters** — earlier actions are preferred; later ones fill in when the earlier ones can't run (out of range, on cooldown).

Each entry is either a melee attack or a spell cast.

#### Melee attack

`"type": "MELEE_ATTACK"`, configured under `melee_attack`.

Melee here is more than a single instant hit — it's a small timed mechanic:

- **Windup.** Each swing has a duration, and the hit lands *partway through* it (set by `windup`, a `0`–`1` fraction). The strike only connects if the target is still in reach at that moment — so a target can step out and dodge the swing.
- **Reach.** A swing won't start past `max_range`, and reach grows with the summon's size, so a scaled-up summon hits from farther.
- **Blast radius.** Set `radius` to turn a single hit into a small area strike: when the main hit lands, every hostile around the primary target is hit too. The blast grows with the summon's size, and the owner and friendly pets are never caught in it.

| Field | Default | Description |
|---|---|---|
| `max_range` | `3` | Won't start a swing past this. `0` = no limit. |
| `speed` | `1.2` | Swings per second. |
| `duration` | `20` | Length of one swing, in ticks. |
| `windup` | `0.5` | When in the swing the hit lands (`0` = start, `1` = end). |
| `radius` | `0` | Blast radius around the hit. `0` = single target. |
| `movement_speed` | `1.0` | Pathfinding speed while chasing. |
| `swing_sound` / `impact_sound` | — | Sound ids. |
| `animation_variants` | `[1]` | Pool of swing animations to pick from. |

#### Spell cast

`"type": "SPELL_CAST"`, configured under `spell_cast`. **Important:** summons can only cast a subset of spells — see §4.3 before choosing one.

| Field | Default | Description |
|---|---|---|
| `spell_id` | — | The spell to cast. Must be an **active, non-channeled** spell (§4.3). |
| `cooldown` | `20` | Ticks between casts (the spell's own cooldown wins if it has one). |
| `aiming` | — | Where it fires. See below. |
| `range` | — | How close it gets before casting. See below. |
| `cast_animation_variants` | `[1]` | Cast-windup animations to pick from. |
| `release_animation_variants` | `[1]` | Release animations to pick from. |
| `release_animation_duration` | `15` | How long the release animation plays, in ticks. |

`aiming`:

- `accept_target` (`true`) — fire at the acquired target when there is one.
- `fallback` — what to do with no target: `NONE` (don't fire), `FORWARD` (fire straight ahead, like a turret), `SELF` (aim at its own feet, for self-centered effects).

`range` (all values are **fractions of the spell's effective range**, so they auto-scale per caster):

- `min` (`0`) — won't cast closer than this.
- `max` (`1`) — won't cast farther than this. Above `1` means "walk in from beyond range first".
- `preferred` (`0.75`) — the standoff distance it tries to hold while casting.

### 2.7 Attribute scaling

Makes the summon **grow in power with its owner**, so it stays useful at any level instead of being locked to fixed stats. Each entry adds an owner-based bonus on top of the summon's base attributes: **`base + ownerValue × coefficient`**.

```json
"attribute_scaling": {
  "entries": [
    {
      "attribute_id": "minecraft:generic.max_health",
      "modifiers": [
        { "attribute_id": "spell_power:frost", "base": 0, "coefficient": 2.0 }
      ]
    }
  ]
}
```

This reads: *the summon's max health gains 2 × the owner's frost spell power.* The summon spawns at full health regardless of scaling.

> A summon's spells deal damage based on **its own** spell power, not the owner's (see §4.3). Scale the matching spell-school attribute here, or its spells will hit for almost nothing.

### 2.8 Combat protection

Controls **whether the summon can be hurt or targeted at all**. Turn it off for turret-style summons that should keep firing without being focused down.

| Field | Default | Description |
|---|---|---|
| `is_attackable` | `true` | Set `false` to make the summon untargetable and invulnerable. |

### 2.9 Sounds

Optional **audio for key moments** in the summon's life. Leave any blank to fall back to the vanilla default (or silence).

| Field | Plays when |
|---|---|
| `spawn` / `despawn` | Entering that phase. |
| `hurt` / `death` | Taking damage / dying. |
| `ambient` | Periodic idle noise. |
| `step` | Each footstep. |

### 2.9b Visual FX

The visual counterparts to the sounds above. `spawn_fx` and `despawn_fx` are
one-shot **moments** — each an `Fx.Visuals` bundle of particles and models, emitted
server-side at that entity. `existence_particles` is a **presence**: it repeats while
the summon is alive, and is spawned client-side from a config synced once, so it
costs no per-tick network traffic.

| Field | Emitted |
|---|---|
| `spawn_fx` | Once, as the summon enters the world |
| `despawn_fx` | Once, as it begins winding down |
| `existence_particles[]` | Every `interval_ticks` while ACTIVE, offset by `offset_ticks` |

```java
behaviour.spawn_fx = Fx.Visuals.of(
        ParticleGroupBuilder.of(SpellEngineParticles.magic_arcane)
                .batch(ParticleGroupBuilder.Batches.impact(15, 0.5F)));
```

A summon *group* — several entities from one cast — can also play a single shared
moment at the group's anchor, authored on the spell rather than the behaviour:
`impacts[].action.summon.group_spawn_fx` with `group_spawn_sound` beside it.

See [Particles](09-visuals-and-audio.md#particles) for the effect structure itself.

### 2.10 Dimensions

Overrides the summon's **hitbox size** when you need it to differ from the registered model — e.g. a compact box for a floating turret.

| Field | Default | Description |
|---|---|---|
| `dimensions` | inherit | Optional `{ "width", "height" }`. Leave unset to use the registered size. |

---

## 3 · Authoring a Summonable Entity 🛠️

This section is for developers building the entity itself. You do this **once per entity type**; afterwards everything is data.

### 3.1 The entity contract

A summonable entity must extend **`SummonedEntity`**. That base class is the engine: it reads the behaviour from the data and builds all the AI, movement, targeting, scaling, and lifecycle for you.

```java
public class FrostElementalEntity extends SummonedEntity {
    public static final Identifier ID = Identifier.of(WizardsMod.ID, "frost_elemental");
    public static EntityType<FrostElementalEntity> TYPE;

    public FrostElementalEntity(EntityType<? extends FrostElementalEntity> type, World world) {
        super(type, world);
    }
}
```

That's the whole class. No AI code — the behaviour drives it.

### 3.2 Registering the entity type

Register like any other entity. One detail matters:

```java
FrostElementalEntity.TYPE = Registry.register(
    Registries.ENTITY_TYPE,
    FrostElementalEntity.ID,
    EntityType.Builder.<FrostElementalEntity>create(FrostElementalEntity::new, SpawnGroup.MISC)
        .dimensions(1F, 2F)   // yields `changing`, not `fixed` — see below
        .maxTrackingRange(64)
        .build()
);
```

Use **changing** dimensions if the summon ever scales with size (via attribute scaling) — the two-arg `.dimensions(w, h)` builder already yields `changing`. With `fixed`, size changes are silently ignored and the melee reach won't grow with the model. Use `fixed` only for summons that never resize.

### 3.3 Base attributes

You don't write `createMobAttributes`. Instead you describe a summon's defaults as a `SummonedEntityConfig.Entry` — the four common attributes plus any custom spell-school attributes — and register it with `SummonedEntities.registerAttributes`:

```java
public static SummonedEntityConfig.Entry frostDefaults() {
    var e = new SummonedEntityConfig.Entry();
    e.common = new SummonedEntityConfig.CommonAttributes(30, 0.25, 4); // health, speed, attack
    e.common.follow_range = 32;
    e.custom.add(new SummonedEntityConfig.CustomAttribute(SpellSchools.FROST.id.toString(), 1));
    return e;
}
```

The register call takes the id, the `EntityType` you just built, and an **attribute source** — a plain `Function<Identifier, SummonedEntityConfig.Entry>` that yields the entry for a given entity id:

```java
SummonedEntities.registerAttributes(FrostElementalEntity.ID, FrostElementalEntity.TYPE, id -> frostDefaults());
```

What the helper does:

- **Resolves the entry** by calling `source.apply(id)` and **builds the container** from it (`SummonedEntity.createAttributes`) — the common attributes plus each custom spell-school attribute — then registers it for the type. It throws if the source returns `null`.
- **Nothing else.** SpellEngine holds no config state, no shared file, and never writes to disk. Where the entry comes from — a config file your mod owns, or an inline constant — is entirely your mod's business.

> **Why pass the `EntityType`?** Earlier the helper took only the id and looked the type up in the registry, which forced you to call it *after* the type was registered. It now takes the type you just built, so **there is no ordering requirement** — register the attributes right where you create the type (as above). The id is passed through to the source.

#### Choosing an attribute source

The `Function` is the decoupling seam: SpellEngine never learns *where* your defaults live. Two patterns cover every mod (both are in the class mods today):

**A. Config file your mod owns** *(recommended for class-mod summons)* — gives server owners an editable file, and lets **your mod version that config independently** of SpellEngine and every other mod. Own a `ConfigManager<SummonedEntityConfig>` in your own directory, seed it from your entries' defaults, and inject `config.value::entryFor` (the built-in `entryFor(Identifier)` **is** the `Function`):

```java
// Declared AFTER the entity constants, so `entries` is populated when seededDefaults() runs.
public static final ConfigManager<SummonedEntityConfig> summonConfig = new ConfigManager<>
        ("summoned_entities", seededDefaults())
        .builder()
        .setDirectory(WizardsMod.ID)   // -> config/wizards/summoned_entities.json
        .schemaVersion(1)              // bump to reset users' files after a defaults change
        .sanitize(true)
        .build();

// in register(), after summonConfig.refresh():
SummonedEntities.registerAttributes(entry.id, livingType, summonConfig.value::entryFor);
```

The config is keyed by the **full entity id** (`namespace:path`), so a mod's file can hold all its own summons without collision. Bumping just this mod's `schemaVersion` resets only this mod's file.

**B. Inline constant** *(no config file)* — for summons you never intend to expose. Just hand the default straight in:

```java
SummonedEntities.registerAttributes(entry.id, livingType, id -> entry.summonConfig);
```

> **No TinyConfig required.** `registerAttributes` depends only on `java.util.function.Function` and the `Entry` DTO. `SummonedEntityConfig` (a `VersionableConfig`) is a *reusable file model* for pattern A — not part of the seam. Pattern B touches neither it nor TinyConfig.

#### Custom spell-school attributes matter

A summon's spells deal damage from **its own** spell power (§4.3), so add the matching school attribute (e.g. `spell_power:frost`) in `custom`, or its spells hit for almost nothing. Attribute scaling (§2.7) then adds the owner's bonuses **on top** of these base values.

### 3.4 Binding the summon

Today, a `Summon` definition lives in Java and is connected to a spell through a **custom-impact handler id** (this is the temporary path until summons become a true data impact — see §4.1).

```java
SpellHandlers.registerCustomImpact(
    Identifier.of("wizards", "summon_frost_elemental"),
    (spell, power, caster, target, context) -> {
        spawn(frostElementalDefinition, spell, caster, context);
        return new SpellHandlers.ImpactResult(true, false);
    });
```

The spell then references it with a `CUSTOM` impact whose `handler` is `wizards:summon_frost_elemental`.

### 3.5 Animation states (client)

`SummonedEntity` exposes a fixed set of animation states. Your model plays the matching animation for each:

| State | When it runs |
|---|---|
| `spawn` / `despawn` | Intro / outro phases. |
| `idle` / `move` | Standing still / walking. |
| `attack` | A melee swing. |
| `spell_cast` | Charging a spell. |
| `spell_release` | Firing a spell. |

Attack and cast animations support **variants** (numbered alternatives) so swings and casts don't look repetitive. The engine picks one from the pool you set in the data and tells the client which to play.

### 3.6 Renderer & model

Build the model and animations like any modern entity, **exported as Java** (a model class plus Java-defined animations), and register a renderer for the entity type. Wire each animation state from §3.5 to its keyframe animation. The release/attack speed is stretched automatically to match the action's configured duration.

### 3.7 Sounds & assets

Add your sound ids to `sounds.json` as usual. Any id that resolves there can be used in the behaviour's `sounds` block (§2.9) and in melee `swing_sound` / `impact_sound`.

### 3.8 NBT persistence

Summons save and reload automatically. What's stored: the owner, the remaining lifespan, and the behaviour. Animations and AI rebuild themselves on load. You don't need to write any save/load code.

### 3.9 End-to-end checklist

To add a brand-new summon:

1. Create the entity class extending `SummonedEntity`. *(§3.1)*
2. Register the entity type and its attributes. *(§3.2–3.3)*
3. Build and register the model, animations, and renderer. *(§3.5–3.6)*
4. Add any sounds. *(§3.7)*
5. Define its `Summon` (entity id + behaviour + placements) and bind it. *(§3.4)*
6. Add a `SUMMON`/`CUSTOM` impact to the spell that calls it.

---

## 4 · Limitations & Roadmap

Three things to know before planning a summon. Each is **user-facing**, not an internal detail.

### 4.1 Summons only work on specially-prepared entities

A summon can't be pointed at just any entity. Summon behaviour only applies to entities whose class **extends `SummonedEntity`** — that base class is what reads the behaviour and runs the AI, lifecycle, scaling, and animations. You can't summon a vanilla mob (a Pig, a Zombie) or any third-party entity that wasn't built for this; behaviour would have nothing to attach to.

In practice this means every summon needs a purpose-built Java entity compiled into a mod. Data **tunes** a prepared summon — it can't turn an ordinary entity into one. *(A future data form is planned for the behaviour, but the entity itself will always need to be the prepared type.)*

### 4.2 Models need custom animation wiring

A summon's model can't just be dropped in. It must be **exported as Java** and wired by hand to the standard animation states (spawn, idle, move, attack, cast, release, despawn). A plain JSON model, or one missing these states, won't animate correctly.

### 4.3 Limited spell support — the big one

When a summon casts, it does **not** go through the normal player casting path. It uses a separate, server-side, **single-shot** entry point (`SpellHelper.targetAndPerformSpell`) that resolves a target from the summon's facing and runs the spell's delivery once. That design causes three hard limits:

**Only active, non-channeled spells fire.** Before a cast even starts, channeled spells are rejected outright (any spell whose cast resolves to a non-zero channel tick count — i.e. `type: CHANNEL` with `channel.ticks > 0`), and so are passive/modifier spells. This is structural, not an oversight: channeling means *repeated, timed* deliveries spread across the cast, and the summon path has no loop to drive them — it fires exactly once.

- ✅ **Instant** spells — fire immediately.
- ✅ **Charged** spells — the summon waits the full cast time, then fires once at **full** strength (no partial-charge scaling).
- ❌ **Channeled** spells — **never fire at all.** This rules out continuous **beam** spells, channeled fire-breath, and similar — they simply do nothing as a summon action.

**Two delivery types silently do nothing.** The spell is accepted, the animation plays, the cooldown ticks — but no effect lands, because these delivery types require a real player:

- ❌ `MELEE` delivery (Better-Combat-style weapon swings) — needs a player; produces nothing.
- ❌ `AFFECT_ARROW` — needs a player's arrow context; no-op.
- ✅ Safe to use: `DIRECT`, `PROJECTILE`, `METEOR`, `CLOUD`, `STASH_EFFECT`. (`CUSTOM` works only if its handler doesn't assume a player.)

**No player bonuses or costs apply.** Gear/enchant spell modifiers (extra range, cooldown reduction, etc.) are skipped, and no ammo, exhaust, or durability is ever consumed. Most importantly, **a summon's spell damage scales off the summon's own spell power**, not the owner's — so you must grant the summon the matching spell-school attribute (§2.7), or its spells hit for almost nothing.

> **Rule of thumb:** give summons **instant** spells with `DIRECT`, `PROJECTILE`, `METEOR`, or `CLOUD` delivery. Anything channeled or melee-delivered won't work.
