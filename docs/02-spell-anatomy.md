# Spell Anatomy

A spell JSON maps directly to the `Spell` Java class. Only fields you want to override from defaults need to be included.

## Top-Level Fields

| Field | Type | Default | Description |
|---|---|---|---|
| `school` | identifier | — | Magic school. Determines which Spell Power attribute scales damage/healing. |
| `type` | enum | `ACTIVE` | `ACTIVE`, `PASSIVE`, or `MODIFIER` — see [Spell Types](#spell-types) |
| `range` | float | `50` | Max target range in blocks |
| `range_mechanic` | enum / null | `null` | Set to `"MELEE"` to use melee attack range instead of `range` |
| `tier` | int | `1` | Quality (higher = better). Primary sort key — see [Sorting](#sorting) |
| `sub_tier` | int | `1` | Secondary sort key, between spells of the same `tier` |
| `group` | string | `""` | Thematic line a spell belongs to. Keeps related spells together when browsed — see [Sorting](#sorting) |
| `learn` | object / null | `null` | If present, spell appears in the Spell Binding Table. |

## Sorting

Spells are sorted two different ways, depending on where they are read.

**In a container** — the spell hotbar, an item tooltip — by `tier`, then `sub_tier`, then spell id. A container holds the spells of a single book or weapon, so only quality matters; `group` is ignored.

**In a catalog** — the Spell Binding Table, the creative menu — by **namespace**, then **`group`**, then `tier`, then `sub_tier`, then spell id. A catalog lists the spells of every installed mod at once, so it is laid out like a library: each mod's spells together, and within a mod, each group together, in ascending quality.

`group` is an arbitrary string; spells that share one are listed together, and groups are ordered alphabetically within their namespace. Two rules follow from that:

- **Prefix the group with the class or book it belongs to.** Groups are read next to every other mod's, so a bare `protection` says nothing about whose protection it is, and two books that both pick that name would have their spells interleaved.
- **A shared prefix keeps a book's lines adjacent**, since sorting is alphabetical: `arcane_evocation` and `arcane_sorcery` land side by side, while `evocation` and `sorcery` would be separated by every group in between.

A typical class defines two groups, offering one spell from each at every tier — the player picks a line as they level:

```
priest_holy        heal (T0), holy_shock (T1), holy_beam (T2), circle_of_healing (T3), lightwell (T4)
priest_discipline  ... barrier (T4)
```

Leaving `group` empty is fine for a spell that belongs to no line; it simply sorts ahead of every named group.

## Magic Schools

Built-in schools from the [SpellPower](../../SpellPower) mod (namespace `spell_power`):

| Identifier | Archetype |
|---|---|
| `spell_power:arcane` | MAGIC |
| `spell_power:fire` | MAGIC |
| `spell_power:frost` | MAGIC |
| `spell_power:healing` | MAGIC |
| `spell_power:lightning` | MAGIC |
| `spell_power:soul` | MAGIC |
| `spell_power:generic` | MAGIC |
| `spell_power:physical_melee` | MELEE |
| `spell_power:physical_melee_dual` | MELEE |
| `spell_power:physical_ranged` | ARCHERY |

`physical_melee_dual` matches `physical_melee`, except its spell power also includes the off-hand
weapon's damage. Use it for spells that strike with both held weapons.

Custom schools can be registered in Java via `SpellSchools.register(...)`.

---

## Spell Types

### ACTIVE
Player-triggered spells. Require an `active` block with casting configuration.

```json
{ "type": "ACTIVE", "active": { "cast": { "duration": 0.8 } } }
```

### PASSIVE
Triggered automatically by game events. Require a `passive` block with a `triggers` list. No player interaction.

```json
{ "type": "PASSIVE", "passive": { "triggers": [ { "type": "MELEE_IMPACT", "chance": 0.2 } ] } }
```

### MODIFIER
Modifies other spells matching a pattern. Uses a `modifiers` list.

```json
{
  "type": "MODIFIER",
  "modifiers": [ { "spell_pattern": "wizards:fireball", "range_add": 5.0 } ]
}
```

---

## The Execution Pipeline

Every spell (except MODIFIER) passes through the same pipeline when it fires. Understanding the pipeline — and which fields control each stage — is the key to building any spell.

```
[TRIGGER / CAST] → [TARGET] → [DELIVER] → [IMPACT]
```

### Stage 1 · Trigger / Cast — *when does the spell fire?*

**ACTIVE** spells are initiated by the player. The `active.cast` block controls the interaction:

| `cast.duration` | `cast.type` | Mode |
|---|---|---|
| `0` | — | **Instant** — fires on key press |
| `> 0` | `STANDARD` | **Casted** — hold for the full duration, fires once on completion |
| `> 0` | `CHANNEL` | **Channeled** — fires `channel.ticks` times spread over `duration` seconds |
| `> 0` | `CHARGE` | **Charged** — hold to charge, may release early; output scales with the charge (bow-like) |

**PASSIVE** spells fire automatically. The `passive.triggers` list defines the conditions — any matching trigger activates the spell. Chance, equipment requirements, and target/caster conditions can all be applied per trigger.

---

### Stage 2 · Target — *who or what is selected?*

`target.type` selects the entity or position the spell acts on:

| `target.type` | Selects |
|---|---|
| `CASTER` | The player casting the spell (self-buffs, self-heals) |
| `AIM` | The entity or ground position under the cursor |
| `BEAM` | A continuous raycast line from caster to cursor (channeled beams) |
| `AREA` | A sphere of entities around the target point (AoE) |
| `NONE` | No entity target — useful when delivery handles placement (e.g. clouds) |
| `FROM_TRIGGER` | Inherits the target from the triggering event (passive spells) |

The `range` field caps how far the target can be. `target.cap` limits how many entities can be affected.

---

### Stage 3 · Deliver — *how does the spell reach the target?*

`deliver.type` controls the delivery mechanism. The delivery is what physically brings the effect to the target:

| `deliver.type` | Behaviour |
|---|---|
| `DIRECT` | Impacts applied immediately — no travel, no projectile |
| `PROJECTILE` | Launches a tracked projectile; impacts fire on hit |
| `METEOR` | Drops a projectile from above the target point |
| `CLOUD` | Spawns a persistent area entity that applies impacts repeatedly |
| `SHOOT_ARROW` | Fires an actual arrow; impacts fire on arrow hit |
| `AFFECT_ARROW` | Enhances the next arrow the player naturally fires |
| `MELEE` | Performs an OBB swing — Better Combat-style hitbox attack |
| `STASH_EFFECT` | Applies a buff effect that re-delivers the spell on a future trigger |
| `CUSTOM` | Calls a Java-registered handler |

---

### Stage 4 · Impact — *what happens on hit?*

`impacts` is an array of actions executed on the targeted entity once delivery lands.

Each impact has an `action.type`:

| `action.type` | Effect |
|---|---|
| `DAMAGE` | Deals magic damage scaled by `spell_power_coefficient × Spell Power` |
| `HEAL` | Restores health scaled by `spell_power_coefficient × Spell Power` |
| `STATUS_EFFECT` | Applies, stacks, or removes a status effect |
| `FIRE` | Sets the target on fire |
| `TELEPORT` | Teleports caster or target (blink, behind target) |
| `AGGRO` | Taunts or disengages a mob |
| `DISRUPT` | Cancels shield blocking or item usage |
| `COOLDOWN` | Modifies cooldowns of other spells |
| `SPAWN` | Spawns an entity at the target |
| `IMMUNITY` | Grants temporary damage immunity |
| `CUSTOM` | Calls a Java-registered handler |

A spell can have **multiple impacts** — they all fire in order on each hit. A heal spell might simultaneously apply a status effect. A damage spell might also set the target on fire.

`area_impact` is an optional top-level field that splashes the `impacts` list to all entities within a radius of the primary hit.

---

## Combining Stages to Design Spells

The variety of spells comes entirely from mixing the four stages. Some examples:

**Instant self-buff** — cast immediately, target self, deliver directly, apply a status effect:
```
cast: instant → target: CASTER → deliver: DIRECT → impact: STATUS_EFFECT
```

**Charged projectile** — hold to charge, aim at enemy, launch a projectile, deal damage:
```
cast: charged → target: AIM → deliver: PROJECTILE → impact: DAMAGE
```

**Channeled healing beam** — hold to channel, beam toward ally, deliver directly each tick, heal:
```
cast: channeled → target: BEAM → deliver: DIRECT → impact: HEAL
```

**AoE ground spell** — instant cast, target area, cloud lingers and damages repeatedly:
```
cast: instant → target: AIM → deliver: CLOUD → impact: DAMAGE
```

**Passive proc** — triggers on melee hit with chance, targets the hit entity, deals bonus damage:
```
trigger: MELEE_IMPACT (25%) → target: FROM_TRIGGER → deliver: DIRECT → impact: DAMAGE
```

**"Next hit" buff** — instant cast, target self, stash an effect that re-fires on next attack:
```
cast: instant → target: CASTER → deliver: STASH_EFFECT → impact: DAMAGE (fires later)
```

---

## Full Skeleton

```json
{
  "school": "spell_power:fire",
  "type": "ACTIVE",
  "range": 30,
  "tier": 2,
  "active": {
    "cast": {
      "duration": 1.0,
      "type": "STANDARD",
      "animation": { "id": "spell_engine:one_handed_projectile_charge" }
    }
  },
  "release": {
    "animation": { "id": "spell_engine:one_handed_projectile_release" },
    "sound": { "id": "spell_engine:generic_cast_1" }
  },
  "target": { "type": "AIM" },
  "deliver": { "type": "PROJECTILE" },
  "impacts": [
    { "action": { "type": "DAMAGE", "damage": { "spell_power_coefficient": 1.2 } } }
  ],
  "area_impact": null,
  "cost": { "cooldown": { "duration": 4.0 }, "exhaust": 0.1 }
}
```

See the linked pages for every field in each stage.
