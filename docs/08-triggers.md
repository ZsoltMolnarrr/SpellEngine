# Triggers

Triggers are used by `PASSIVE` spells. They define what game event fires the spell.

A passive spell runs its full delivery and impacts pipeline whenever a matching trigger fires — no player input required.

## Structure

```json
{
  "type": "PASSIVE",
  "passive": {
    "triggers": [
      {
        "type": "MELEE_IMPACT",
        "chance": 0.25
      }
    ]
  }
}
```

Multiple triggers can be listed — any of them firing will activate the spell.

## Trigger Types

| Type | Fires when… |
|---|---|
| `MELEE_IMPACT` | The carrier lands a melee attack |
| `ARROW_IMPACT` | An arrow fired by the carrier hits |
| `ARROW_SHOT` | The carrier shoots an arrow |
| `SPELL_CAST` | The carrier casts a spell |
| `SPELL_IMPACT_ANY` | Any spell impact occurs |
| `SPELL_IMPACT_SPECIFIC` | A specific spell's impact occurs (filtered by `spell` condition) |
| `SPELL_AREA_IMPACT` | A spell's area impact fires |
| `DAMAGE_TAKEN` | The carrier takes damage |
| `SHIELD_BLOCK` | The carrier blocks with a shield |
| `EVASION` | The carrier evades an attack |
| `ROLL` | The carrier performs a Combat Roll (requires Combat Roll mod) |
| `EFFECT_TICK` | A status effect ticks on the carrier |

## Trigger Fields

| Field | Type | Default | Description |
|---|---|---|---|
| `type` | enum | — | Trigger type (required) |
| `chance` | float | `1.0` | Probability to fire (0–1) |
| `stage` | enum | `POST` | `PRE` (before event) or `POST` (after event) |
| `fire_delay` | int | `0` | Ticks to wait before executing |
| `cap_per_tick` | int | `0` | Max executions per game tick (0 = unlimited) |
| `chance_batching` | bool | `false` | Roll chance once per tick, apply result to all targets |
| `equipment_condition` | slot / null | `null` | Spell must be on item in this slot to trigger. Use `"MAINHAND"` for weapon passives. |
| `caster_conditions` | array | `null` | Conditions the caster must meet |
| `target_conditions` | array | `null` | Conditions the target must meet |
| `spell` | object | `null` | Spell filter (for spell-related trigger types) |
| `impact` | object | `null` | Impact filter (for `SPELL_IMPACT_SPECIFIC`) |
| `damage` | object | `null` | Damage filter (for `DAMAGE_TAKEN`) |

## Spell Condition Filter

Used with `SPELL_CAST`, `SPELL_IMPACT_SPECIFIC`, etc.:

```json
"spell": {
  "school": "spell_power:fire",
  "type": "ACTIVE",
  "id": null,
  "archetype": null,
  "cooldown_min": 0
}
```

`id` supports universal pattern matching: `#tag`, `~regex`, or exact id.

## Impact Condition Filter

Used with `SPELL_IMPACT_SPECIFIC`:

```json
"impact": {
  "impact_type": "DAMAGE",
  "critical": true
}
```

## Damage Condition Filter

Used with `DAMAGE_TAKEN`:

```json
"damage": {
  "amount_min": 5.0,
  "fatal": false,
  "damage_type": "minecraft:magic"
}
```

## Effect Tick Condition

Used with `EFFECT_TICK`:

```json
"effect": { "id": "mymod:my_effect" }
```

## Examples

**25% proc on any melee hit:**
```json
{ "type": "MELEE_IMPACT", "chance": 0.25 }
```

**Trigger only when wielding the spell's item in main hand:**
```json
{ "type": "MELEE_IMPACT", "equipment_condition": "MAINHAND" }
```

**On fire spell impact:**
```json
{ "type": "SPELL_IMPACT_SPECIFIC", "spell": { "school": "spell_power:fire" }, "impact": { "impact_type": "DAMAGE" } }
```

**On taking fatal damage (pre-event, for death prevention):**
```json
{ "type": "DAMAGE_TAKEN", "stage": "PRE", "damage": { "fatal": true } }
```

For more complex examples see `RelicSpells.java` in the Relics mod.
