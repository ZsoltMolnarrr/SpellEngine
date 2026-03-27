# Getting Started

## Two Workflows

### 1 · JSON Authoring (no Java required)

Create a file at:
```
data/YOUR_MOD_ID/spell/YOUR_SPELL_ID.json
```

The file content mirrors the [`Spell`](../common/src/main/java/net/spell_engine/api/spell/Spell.java) Java class — every field name is the JSON key. Spell Engine loads all files found under this path automatically; no registration code needed.

Minimal example (instant self-heal on cast):
```json
{
  "school": "spell_power:healing",
  "type": "ACTIVE",
  "active": { "cast": { "duration": 0 } },
  "target": { "type": "CASTER" },
  "deliver": { "type": "DIRECT" },
  "impacts": [
    { "action": { "type": "HEAL", "heal": { "spell_power_coefficient": 1.0 } } }
  ],
  "cost": { "cooldown": { "duration": 6.0 } }
}
```

### 2 · Java Datagen

Use `SpellBuilder` to construct `Spell` objects in code, then export them via `SpellGenerator`.

```java
// 1. Build the spell
Spell spell = SpellBuilder.createSpellActive();
spell.school = SpellSchools.FIRE;
SpellBuilder.Casting.cast(spell, 1.0f, "spell_engine:one_handed_projectile_charge");
spell.target.type = Spell.Target.Type.AIM;
spell.deliver.type = Spell.Delivery.Type.PROJECTILE;
spell.impacts = List.of(SpellBuilder.Impacts.damage(1.2f));
SpellBuilder.Cost.cooldown(spell, 4.0f);

// 2. Register it in your DataGenerator
public class MySpellGen extends SpellGenerator {
    @Override
    public void generateSpells(Builder builder) {
        builder.add(Identifier.of("mymod", "fireball"), spell);
    }
}

// 3. Add the provider in your DataGeneratorEntrypoint
pack.addProvider(MySpellGen::new);
```

Running datagen writes the spell JSON to your resources, which is then bundled with your mod.

## Reference Implementations

These open-source mods use Spell Engine and serve as real-world examples for both workflows:

| Mod | What it covers |
|---|---|
| [Wizards](https://github.com/ZsoltMolnarrr/Wizards) | Projectiles, beams, channeled spells, meteors, clouds |
| [Paladins](https://github.com/ZsoltMolnarrr/Paladins) | Healing, area heals, banners, barriers |
| [Archers](https://github.com/ZsoltMolnarrr/Archers) | Archery skills, `SHOOT_ARROW` delivery |
| [Relics](https://github.com/ZsoltMolnarrr/Relics) | Passive spells, triggers, stash effects |
| [Arsenal](https://github.com/ZsoltMolnarrr/Arsenal) | Melee weapon skills, `MELEE` delivery |

## Required Resources

| Resource | Path |
|---|---|
| Spell data | `data/MOD_ID/spell/SPELL_ID.json` |
| Spell icon | `assets/MOD_ID/textures/spell/SPELL_ID.png` |
| Display name | lang key `spell.MOD_ID.SPELL_ID.name` |
| Description | lang key `spell.MOD_ID.SPELL_ID.description` |

The description supports value tokens — see [`SpellTooltip.java`](../common/src/main/java/net/spell_engine/client/gui/SpellTooltip.java) for available tokens (e.g. `{damage}`, `{heal}`).
