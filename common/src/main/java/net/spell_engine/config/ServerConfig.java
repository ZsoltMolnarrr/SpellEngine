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

    @Comment("""
            Allow actions based on relations:
            +----------------+-----------+----------+----------+
            |                | FRIENDLY  | NEUTRAL  | HOSTILE  |
            +----------------+-----------+----------+----------+
            | DIRECT DAMAGE  | 🚫        | ✅      | ✅       |
            | AREA DAMAGE    | 🚫        | 🚫      | ✅       |
            | DIRECT HEALING | ✅        | ✅      | 🚫       |
            | AREA HEALING   | ✅        | 🚫      | 🚫       |
            +----------------+-----------+----------+----------+
            Entities within the same team are considered FRIENDLY
            """)
    public TargetHelper.Relation player_relation_to_teamless_players = TargetHelper.Relation.NEUTRAL;
    public TargetHelper.Relation player_relation_to_villagers = TargetHelper.Relation.NEUTRAL;
    public TargetHelper.Relation player_relation_to_passives = TargetHelper.Relation.HOSTILE;
    public TargetHelper.Relation player_relation_to_hostiles = TargetHelper.Relation.HOSTILE;
    public TargetHelper.Relation player_relation_to_other = TargetHelper.Relation.HOSTILE;
}
