package net.spell_engine.config;

import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;
import me.shedaniel.cloth.clothconfig.shadowed.blue.endless.jankson.Comment;
import net.spell_engine.utils.TargetHelper;

import java.util.LinkedHashMap;

@Config(name = "server")
public class ServerConfig implements ConfigData { public ServerConfig() {}
    @Comment("Default `0.2` matches the same as movement speed during vanilla item usage (such as bow)")
    public float movement_speed_while_casting_spell = 0.2F;
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
    @Comment("Spell book creation level requirement")
    public int spell_book_binding_level_requirement = 3;
    @Comment("Spell book creation level cost")
    public int spell_book_binding_level_cost = 1;

    @Comment("Apply `Spell Casting from Spell Book` capability to anything that subclasses Sword")
    public boolean add_spell_casting_to_swords = true;
    @Comment("Apply `Spell Casting from Spell Book` capability to any item matching this regex. (Not applied of empty)")
    public String add_spell_casting_regex = "";
    @Comment("Do not apply `Spell Casting from Spell Book` capability to any item matching this regex. (Not applied of empty)")
    public String blacklist_spell_casting_regex = "";

    @Comment("""
            Relations determine which cases the effect of a player casted spell can effect a target.
            +----------------+-----------+---------------+----------+----------+--------+
            |                | FRIENDLY  | SEMI_FRIENDLY | NEUTRAL  | HOSTILE  | MIXED  |
            +----------------+-----------+---------------+----------+----------+--------+
            | DIRECT DAMAGE  | 🚫        | ✅            | ✅       | ✅       | ✅    |
            | AREA DAMAGE    | 🚫        | 🚫            | 🚫       | ✅       | ✅    |
            | DIRECT HEALING | ✅        | ✅            | ✅       | 🚫       | ✅    |
            | AREA HEALING   | ✅        | ✅            | 🚫       | 🚫       | ✅    |
            +----------------+-----------+---------------+----------+----------+--------+
            
            The various relation related configs are being checked in the following order:
            - `player_relations`
            - `player_relation_to_passives`
            - `player_relation_to_hostiles`
            - `player_relation_to_other`
            (The first relation to be found for the target will be applied.)
            """)
    public LinkedHashMap<String, TargetHelper.Relation> player_relations = new LinkedHashMap<>() {{
        put("minecraft:player", TargetHelper.Relation.SEMI_FRIENDLY);
        put("minecraft:villager", TargetHelper.Relation.SEMI_FRIENDLY);
        put("minecraft:iron_golem", TargetHelper.Relation.NEUTRAL);
        put("guardvillagers:guard", TargetHelper.Relation.SEMI_FRIENDLY);
    }};

    @Comment("Relation to unspecified entities those are instance of PassiveEntity(Yarn)")
    public TargetHelper.Relation player_relation_to_passives = TargetHelper.Relation.HOSTILE;
    @Comment("Relation to unspecified entities those are instance of HostileEntity(Yarn)")
    public TargetHelper.Relation player_relation_to_hostiles = TargetHelper.Relation.HOSTILE;
    @Comment("Fallback relation")
    public TargetHelper.Relation player_relation_to_other = TargetHelper.Relation.HOSTILE;
}
