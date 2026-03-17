package net.spell_engine.config;

import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;
import me.shedaniel.cloth.clothconfig.shadowed.blue.endless.jankson.Comment;
import net.spell_engine.internals.target.EntityRelation;

import java.util.LinkedHashMap;

@Config(name = "server")
public class ServerConfig implements ConfigData { public ServerConfig() {}
    @Comment("Applied as multiplier on top of spell.cast.movement_speed. Default value of 1.0 means no change.")
    public float movement_multiplier_speed_while_casting = 1F;
    @Comment("Allow spells to bypass invulnerability frames. This is required in order for high attack frequency spells (such as beams) to work.")
    public boolean bypass_iframes = true;
    @Comment("Spell haste reduces the cooldown time of abilities")
    public boolean haste_affects_cooldown = true;
    @Comment("Spell costs exhausts (hunger) will be multiplied with this value. Set `0` for no exhaust.")
    public float spell_cost_exhaust_multiplier = 1F;
    @Comment("Spells should levelCost items. Set `false` to remove rune (or other item) levelCost from all spells.")
    public boolean spell_cost_item_allowed = true;
    @Comment("Spells should damage items on use. Set `false` to disable.")
    public boolean spell_cost_durability_allowed = true;
    @Comment("The time in ticks of global cooldown to apply to all instant cast spells when casted.")
    public int spell_instant_cast_global_cooldown = 0;
    @Comment("Players cannot unequip a spell book, if one of the spells in it is on cooldown.")
    public boolean spell_item_cooldown_lock = true;
    @Comment("Additional cooldown in seconds applied to equipped spell book item, after casting a spell from them, to prevent quick swapping and casting.")
    public float spell_book_additional_cooldown = 10F;
    @Comment("Players can use the Spell Binding Table to create spell books.")
    public boolean spell_book_creation_enabled = true;
    @Comment("Spell book creation level levelCost")
    public int spell_book_creation_cost = 1;
    @Comment("Spell book creation level requirement")
    public int spell_book_creation_requirement = 1;
    @Comment("Spell binding level levelCost multiplier")
    public int spell_binding_level_cost_multiplier = 1;
    @Comment("Spell binding level levelCost offset (added after multiplier)")
    public int spell_binding_level_cost_offset = 0;
    @Comment("Spell binding level levelCost minimum levelCost after applying multiplier and offset")
    public int spell_binding_level_cost_min = 1;
    @Comment("Spell binding level requirement multiplier")
    public int spell_binding_level_requirement_multiplier = 1;
    @Comment("Spell binding level requirement offset (added after multiplier)")
    public int spell_binding_level_requirement_offset = 0;
    @Comment("Spell binding level requirement minimum level requirement after applying multiplier and offset")
    public int spell_binding_level_requirement_min = 1;
    @Comment("Spell binding lapis lazuli levelCost multiplier")
    public int spell_binding_lapis_cost_multiplier = 1;
    @Comment("Spell binding allow unbinding spells")
    public boolean spell_binding_allow_unbinding = true;

    public int spell_scroll_level_cost_per_tier = 0;
    public int spell_scroll_apply_cost_base = 1;

    @Comment("Allow spell containers be cached for faster improved server performance. Might be buggy.")
    public boolean spell_container_caching = true;
    @Comment("Allow any spell containers to be resolved from the offhand not just offhand specific ones.")
    public boolean spell_container_from_offhand_any = false;
    @Comment("If set true, a Fireball doesn't collide with an ally, a healing projectile doesn't collide with an enemy")
    public boolean projectiles_pass_thru_irrelevant_targets = true;
    @Comment("Auto swap Bow & Spear cooldown ticks to apply for attack and itemUse")
    public int auto_swap_cooldown = 5;

    @Comment("""
            Evasion to work within certain angle of attack
            Example values:
            - `0` - any attack can be evaded
            - `90` (default) - no attack from behind can be evaded
            """)
    public float attribute_evasion_angle = 120F;
    @Comment("Allow evasion to work while the player is casting a spell")
    public boolean attribute_evasion_allowed_while_spell_casting = false;
    @Comment("Allow evasion to work while the player is using an item (e.g. eating, drawing a bow)")
    public boolean attribute_evasion_allowed_while_item_usage = false;
    @Comment("Determines the focus mode (AREA vs DIRECT) for melee skills.")
    public boolean melee_skills_area_focus_mode = true;

    @Comment("""
            Relations determine which cases the effect of a player casted spell can effect a target.
            +----------------+-------+----------+----------+----------+--------+
            |                | ALLY  | FRIENDLY | NEUTRAL  | HOSTILE  | MIXED  |
            +----------------+-------+----------+----------+----------+--------+
            | DIRECT DAMAGE  | 🚫    | ✅       | ✅       | ✅       | ✅    |
            | AREA DAMAGE    | 🚫    | 🚫       | 🚫       | ✅       | ✅    |
            | DIRECT HEALING | ✅    | ✅       | ✅       | 🚫       | ✅    |
            | AREA HEALING   | ✅    | ✅       | 🚫       | 🚫       | ✅    |
            +----------------+-------+----------+----------+----------+--------+
            
            The various relation related configs are being checked in the following order:
            - `player_relations`
            - `player_relation_to_passives`
            - `player_relation_to_hostiles`
            - `player_relation_to_other`
            (The first relation to be found for the target will be applied.)
            """)
    public LinkedHashMap<String, EntityRelation> player_relations = new LinkedHashMap<>() {{
        put("minecraft:player", EntityRelation.FRIENDLY);
        put("minecraft:villager", EntityRelation.FRIENDLY);
        put("minecraft:allay", EntityRelation.FRIENDLY);
        put("minecraft:iron_golem", EntityRelation.FRIENDLY);
        put("guardvillagers:guard", EntityRelation.FRIENDLY);
        put("minecraft:cat", EntityRelation.FRIENDLY);
        put("minecraft:minecart", EntityRelation.FRIENDLY);
    }};
    public LinkedHashMap<String, EntityRelation> player_relation_tags = new LinkedHashMap<>() {{
        // put("minecraft:undead", TargetHelper.Relation.HOSTILE);
    }};
    @Comment("Relation to self, and self owned pets (tamed entities), changing this to `FRIENDLY` will make it impossible to hit pets with weapons")
    public EntityRelation player_relation_to_owned_pets = EntityRelation.FRIENDLY;
    @Comment("Relation to teammates (entities in the same team), changing this to `FRIENDLY` will automatically disable friendly fire for all teams")
    public EntityRelation player_relation_to_teammates = EntityRelation.FRIENDLY;
    @Comment("Relation to unspecified entities those are instance of PassiveEntity(Yarn)")
    public EntityRelation player_relation_to_passives = EntityRelation.HOSTILE;
    @Comment("Relation to unspecified entities those are instance of HostileEntity(Yarn)")
    public EntityRelation player_relation_to_hostiles = EntityRelation.HOSTILE;
    @Comment("Fallback relation")
    public EntityRelation player_relation_to_other = EntityRelation.HOSTILE;
}
