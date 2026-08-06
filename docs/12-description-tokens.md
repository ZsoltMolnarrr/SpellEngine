# Description Tokens

A spell's description (lang key `spell.MOD_ID.SPELL_ID.description`) may contain **tokens** —
placeholders wrapped in `{ }` that the tooltip renderer replaces with concrete numbers when the
tooltip is drawn. This keeps the shown value in lockstep with the spell's actual data: change a
coefficient or a chance and the tooltip updates itself, because the number is derived from the same
`Spell` (and, for some tokens, the viewing player) that drives gameplay.

Tokens come in two shapes: **simple named tokens** drawn from a fixed vocabulary, and the parametric
**effect token** for reading a status effect's attribute modifiers.

---

## Simple tokens

Simple tokens are single names the engine substitutes from the spell's structured data — for example
`{damage}` and `{heal}` (resolved from the viewer's spell power, shown as a min–max range),
`{trigger_chance}`, `{effect_duration}`, `{effect_amplifier}`, `{impact_range}`, and the summon
counts. When a spell produces several values for one token (e.g. two damage impacts), the token is
indexed 1-based: `{damage_1}`, `{damage_2}`.

The full list of names lives in [`TooltipTokens`](../common/src/main/java/net/spell_engine/api/spell/tooltip/TooltipTokens.java)
as constants; the renderer that fills them is
[`SpellTooltip`](../common/src/main/java/net/spell_engine/client/gui/SpellTooltip.java).

---

## The effect token

Simple tokens can't express "the value of a status effect's attribute modifier" — effects can carry
several modifiers, in different operation modes, scaled by amplifier. The **effect token** is a
parametric token that reads those values straight off the registered `StatusEffect`:

```
{effect|<effect_id>|<amplifier>|<attribute>|<format>}
```

| Field | Required | Meaning | Default when omitted |
|---|---|---|---|
| `effect_id` | yes | namespaced status-effect id, e.g. `arsenal:frostbite` | — |
| `amplifier` | no | **0-based** engine amplifier; value = `base × (amplifier + 1)` | `0` |
| `attribute` | no | which modifier to read, by attribute id, e.g. `minecraft:generic.movement_speed` | the effect's **sole** modifier |
| `format` | no | sign handling (see below) | signed |

### Amplifier

The amplifier is the raw engine amplifier and feeds vanilla's `base × (amplifier + 1)` scaling
directly, so `0` is the base level. "The value at N stacks" is amplifier `N − 1`.

> ⚠️ This is the *internal* amplifier, not the display level. The `{effect_amplifier}` simple token
> shows `amplifier + 1`; the effect token's field does not add one.

### Attribute selection

A modifier is selected by its **attribute id**. A blank attribute field selects the effect's sole
modifier — which is only deterministic when the effect has exactly one (the effect's modifier map is
unordered, so there is no reliable "first"). An effect with several modifiers, such as Frostbite
(movement **and** attack speed), must name the attribute; use one token per value you want to show.

### Format — sign only

Percentage-vs-flat is **not** a choice: it is derived from the modifier's operation
(`ADD_MULTIPLIED_BASE`/`_TOTAL` render as a percentage, `ADD_VALUE` as a flat number). The format
field only controls the sign, the one thing the operation can't tell us.

| `format` | Keyword | Renders |
|---|---|---|
| Signed (default) | *(omitted)* | value as-is — a negative keeps its `-`, a positive has no prefix: `-25%`, `10%`, `3` |
| Absolute | `abs` | `\|value\|` — for "reduce ... by 30%" phrasing where the stored value is negative |
| Forced sign | `+` | always explicit: `+20%`, `-20%`, `+3` |

---

## Building tokens (Java)

Build tokens with the `TooltipTokens.effect(...)` overloads rather than hand-writing the string.
`TooltipTokens` is **dependency-free and server-safe** — build from spell data definitions here, not
from the client-only `SpellTooltip`, which would crash a dedicated server.

```java
import net.spell_engine.api.spell.tooltip.TooltipTokens;
import net.spell_engine.api.spell.tooltip.TooltipTokens.Format;

// The effect's sole modifier, base level, signed:
TooltipTokens.effect(GUARDING.id)                              // {effect|arsenal:guarding}

// A stacking value at 4 stacks (amplifier 3):
TooltipTokens.effect(RAMPAGING.id, 3)                          // {effect|arsenal:rampaging|3}

// A named modifier, absolute value:
TooltipTokens.effect(FROSTBITE.id, 0,
        Identifier.of(EntityAttributes.GENERIC_MOVEMENT_SPEED.getIdAsString()),
        Format.ABS)   // {effect|arsenal:frostbite|0|minecraft:generic.movement_speed|abs}
```

Embed the result in the description string:

```java
var description = "On melee hit: {trigger_chance} chance to reduce the target's armor by "
        + TooltipTokens.effect(SUNDERING.id, 0, null, Format.ABS)
        + " for {effect_duration} seconds.";
```

The builders emit a **canonical minimal** string: trailing default fields are dropped, but a
defaulted field is still written when a later field is non-default (so positions stay aligned —
`{effect|id|0||abs}` names no attribute but keeps its slot). One meaning always produces one exact
string, which is what lets the renderer cache resolution keyed on the token text.

---

## How it resolves (and why it's cheap)

At render time the renderer scans the description for `{effect|...}` occurrences, reads the named
modifier off `Registries.STATUS_EFFECT` (amplifier scaling applied by vanilla's
`forEachAttributeModifier`), and formats it. An effect token's value depends only on the status-effect
registry — never on the viewing player — so each distinct token resolves **once** and is memoised
until a registry resync. Steady-state cost per tooltip frame is a `contains("{effect|")` check plus a
map lookup per token. An unresolvable token (unknown effect, ambiguous sole-modifier, malformed) is
left visible in the tooltip for debugging rather than blanked.

See [`SpellTooltip.computeEffectToken`](../common/src/main/java/net/spell_engine/client/gui/SpellTooltip.java)
for the resolver and [`TooltipTokens.Format`](../common/src/main/java/net/spell_engine/api/spell/tooltip/TooltipTokens.java)
for the builder and formatting.
