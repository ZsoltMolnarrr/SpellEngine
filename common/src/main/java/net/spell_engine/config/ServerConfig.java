package net.spell_engine.config;

import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;
import me.shedaniel.cloth.clothconfig.shadowed.blue.endless.jankson.Comment;
import net.spell_engine.utils.TargetHelper;

@Config(name = "server")
public class ServerConfig implements ConfigData {
    @Comment("Spell caster items in the offhand can be used")
    public boolean offhand_casting_allowed = false;
    @Comment("Allow spells to bypass invulnerability frames. This is required in order for high attack frequency spells (such as beams) to work.")
    public boolean bypass_iframes = true;
    @Comment("Spell haste reduces the cooldown time of abilities")
    public boolean haste_affects_cooldown = true;
    @Comment("Spell costs exhausts (hunger) will be multiplied with this value. Set `0` for no exhaust.")
    public float spell_cost_exhaust_multiplier = 1F;
    @Comment("Spells should cost items. Set `false` to remove rune (or other item) cost from all spells.")
    public boolean spell_cost_item_allowed = true;
    @Comment("Spells should damage items on use. Set `false` to disable.")
    public boolean spell_cost_durability_allowed = true;
    @Comment("If set true, a Fireball doesn't collide with an ally, a healing projectile doesn't collide with an enemy")
    public boolean projectiles_pass_thru_irrelevant_targets = true;
    public int spell_book_binding_level_requirement = 3;
    public int spell_book_binding_level_cost = 1;

    @Comment("Apply `Spell Casting from Spell Book` capability to anything that subclasses Sword")
    public boolean add_spell_casting_to_swords = true;
    @Comment("Apply `Spell Casting from Spell Book` capability to any item matching this regex. (Not applied of empty)")
    public String add_spell_casting_regex = "";

    @Comment("""
            Allow actions based on relations:
            +----------------+-----------+---------------+----------+----------+--------+
            |                | FRIENDLY  | SEMI_FRIENDLY | NEUTRAL  | HOSTILE  | MIXED  |
            +----------------+-----------+---------------+----------+----------+--------+
            | DIRECT DAMAGE  | 🚫        | ✅            | ✅       | ✅       | ✅    |
            | AREA DAMAGE    | 🚫        | 🚫            | 🚫       | ✅       | ✅    |
            | DIRECT HEALING | ✅        | ✅            | ✅       | 🚫       | ✅    |
            | AREA HEALING   | ✅        | ✅            | 🚫       | 🚫       | ✅    |
            +----------------+-----------+---------------+----------+----------+--------+
            Any entities within the same team are considered FRIENDLY for each other.
            """)
    public TargetHelper.Relation player_relation_to_teamless_players = TargetHelper.Relation.SEMI_FRIENDLY;
    public TargetHelper.Relation player_relation_to_villagers = TargetHelper.Relation.SEMI_FRIENDLY;
    public TargetHelper.Relation player_relation_to_passives = TargetHelper.Relation.HOSTILE;
    public TargetHelper.Relation player_relation_to_hostiles = TargetHelper.Relation.HOSTILE;
    public TargetHelper.Relation player_relation_to_other = TargetHelper.Relation.HOSTILE;
}
