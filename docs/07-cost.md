# Cost

The `cost` block defines what is consumed when the spell is cast. Applied to the caster after casting completes.

```json
"cost": {
  "exhaust": 0.1,
  "durability": 1,
  "cooldown": { "duration": 4.0 },
  "item": { "id": "mymod:mana_rune", "amount": 1 }
}
```

## Fields

| Field | Type | Default | Description |
|---|---|---|---|
| `exhaust` | float | `0.1` | Hunger exhaustion added |
| `durability` | int | `1` | Durability consumed from the spell's hosting item |
| `cooldown` | object | — | Cooldown placed on the spell after cast |
| `item` | object / null | `null` | Item consumed from inventory |
| `effect_id` | string / null | `null` | Status effect removed on cast (useful for channeled spells) |
| `batching` | bool | `false` | Defers cost to end of tick (for triggered spells hitting multiple targets) |

## Cooldown

```json
"cooldown": {
  "duration": 6.0,
  "group": null,
  "haste_affected": true,
  "hosting_item": true,
  "attempt_duration": 0.0,
  "proportional": false
}
```

| Field | Description |
|---|---|
| `duration` | Cooldown in seconds |
| `group` | Shared cooldown group id — all spells in the same group share one cooldown timer. Built-in group: `"weapon"` |
| `haste_affected` | Whether spell haste reduces this cooldown |
| `hosting_item` | Whether the cooldown is also placed on the item itself |
| `attempt_duration` | Cooldown applied at cast *attempt* (before delivery), useful for spells with long delivery delays |
| `proportional` | Scales cooldown duration with channeling duration |

### Shared Cooldowns (Groups)

Assign the same `group` to multiple spells to make them share a cooldown — casting one puts all others on cooldown too.

```json
"cost": { "cooldown": { "duration": 8.0, "group": "my_class_skills" } }
```

## Item Cost

```json
"cost": {
  "item": {
    "id": "spell_engine:rune",
    "amount": 1,
    "consume": true
  }
}
```

`id` can be a direct item id or an item tag (`#mymod:runes`). Set `consume: false` to only *check* for the item without consuming it (e.g. archery skills that check for arrows but use the vanilla arrow logic).

## Global Cooldown

Instant spells (`duration: 0`) get a 0.5s global cooldown by default, preventing rapid-fire spam. This is applied automatically and is separate from the spell's own `cost.cooldown.duration`.
