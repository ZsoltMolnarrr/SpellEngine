# Spell Engine — Spell Creation Guide

> Covers Spell Engine **1.9.0+**

## Contents

| Page | Description |
|---|---|
| [01 · Getting Started](01-getting-started.md) | Two workflows (JSON vs Java datagen), file locations, required resources |
| [02 · Spell Anatomy](02-spell-anatomy.md) | Top-level fields, magic schools, spell types overview |
| [03 · Casting](03-casting.md) | Instant / charged / channeled, animations, release |
| [04 · Targeting](04-targeting.md) | Target types, AIM/BEAM/AREA options, target cap |
| [05 · Delivery](05-delivery.md) | How spells reach targets: projectile, meteor, cloud, melee, stash, etc. |
| [06 · Impacts](06-impacts.md) | Damage, heal, effects, fire, teleport, cooldown, area impacts, conditions |
| [07 · Cost](07-cost.md) | Cooldown, exhaust, item consumption, cooldown groups |
| [08 · Triggers](08-triggers.md) | Passive spell triggers — types, filters, conditions |
| [09 · Visuals & Audio](09-visuals-and-audio.md) | Sounds, particles, projectile models, beam visuals |

## Reference Implementations

For complex, real-world spell definitions see these content mods:

| Mod | What to look at |
|---|---|
| **Wizards** | `WizardSpells.java` — projectiles, beams, channeled, meteors, clouds |
| **Paladins** | `PaladinSpells.java` — healing, area heals, banners, barriers |
| **Archers** | `ArcherSpells.java` — archery skills, SHOOT_ARROW delivery |
| **Relics** | `RelicSpells.java` — passive spells with triggers and stash effects |

## Key Source Files

- [`Spell.java`](../common/src/main/java/net/spell_engine/api/spell/Spell.java) — canonical data structure (JSON mirrors this exactly)
- [`SpellBuilder.java`](../common/src/main/java/net/spell_engine/api/datagen/SpellBuilder.java) — Java factory helpers
- [`SpellGenerator.java`](../common/src/main/java/net/spell_engine/api/datagen/SpellGenerator.java) — datagen base class
- [`SpellEngineSounds.java`](../common/src/main/java/net/spell_engine/fx/SpellEngineSounds.java) — built-in sound ids
- [`SpellEngineParticles.java`](../common/src/main/java/net/spell_engine/fx/SpellEngineParticles.java) — built-in particle ids
- [`player_animations/`](../common/src/main/resources/assets/spell_engine/player_animations/) — built-in animation ids
