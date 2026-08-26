package net.spell_engine.internals.target;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.scores.Team;
import net.spell_engine.SpellEngineMod;
import net.spell_engine.compat.MultipartEntityCompat;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public class EntityRelations {
    /// Owner UUID of a tameable without resolving the entity (1.21.11 replaced `getOwnerUuid` with a lazy reference)
    @org.jetbrains.annotations.Nullable
    public static java.util.UUID ownerUuid(net.minecraft.world.entity.OwnableEntity tameable) {
        var reference = tameable.getOwnerReference();
        return reference == null ? null : reference.getUUID();
    }

    public static EntityRelation getRelation(LivingEntity attacker, Entity target) {
        var config = SpellEngineMod.config;
        if (attacker == target) {
            return EntityRelation.ALLY;
        }
        target = MultipartEntityCompat.coalesce(target);

        if (attacker instanceof OwnableEntity attackerTameable) {
            var owner = attackerTameable.getOwner();
            if (owner != null) {
                if (target == owner) {
                    return config.summoned_relation_to_owner;
                }
            }
        }
        if (target instanceof OwnableEntity tameable) {
            // Ownership is resolved by UUID, NOT by `Tameable.getOwner()`. Vanilla's default
            // implementation of that is `getWorld().getPlayerByUuid(uuid)`, which returns null
            // whenever the owner is offline or in another dimension — on a server, the common case.
            // Falling through on null dropped the pet into the generic `PassiveEntity` branch below,
            // where `player_relation_to_passives` (HOSTILE by default) marked every unattended tamed
            // animal as an enemy. Note that `AbstractHorseEntity` is `Tameable` too, so this is the
            // branch that decides whether a player's horse is safe.
            var ownerUuid = ownerUuid(tameable);
            if (ownerUuid != null) {
                if (ownerUuid.equals(attacker.getUUID())) {
                    return config.player_relation_to_owned_pets;
                }
                var owner = tameable.getOwner();
                if (owner != null) {
                    return getRelation(attacker, owner);
                }
                // Owner is set but not present to be classified. Deliberately a return rather than a
                // fall-through: the relation must not flip based on whether that player happens to
                // be logged in.
                return config.player_relation_to_absent_owner_pets;
            }
        }
        // FRIENDLY, not NEUTRAL: paintings and item frames must stay out of area damage, and NEUTRAL
        // no longer grants that (see TABLE_OF_ULTIMATE_JUSTICE). FRIENDLY keeps the exact behaviour
        // they had — deliberately breakable by a direct hit, immune to a stray AoE — and the healing
        // rows it additionally opts into are no-ops on a non-living entity.
        if (target instanceof HangingEntity) {
            return EntityRelation.FRIENDLY;
        }

        for (var matcher: TEAM_MATCHERS.values()) {
            var relation = matcher.getRelation(attacker, target);
            if (relation != null) {
                return relation.areTeammates()
                        ? (relation.friendlyFireAllowed() ? config.player_relation_to_teammates : EntityRelation.ALLY)
                        : EntityRelation.HOSTILE;
            }
        }

        var targetTypeEntry = BuiltInRegistries.ENTITY_TYPE.wrapAsHolder(target.getType());
        var id = targetTypeEntry.unwrapKey().get().identifier();
        var mappedRelation = config.player_relations.get(id.toString());
        if (mappedRelation != null) {
            return mappedRelation;
        }
        for (var entry: getRelationTagsCache().entrySet()) {
            if (targetTypeEntry.is(entry.getKey())) {
                return entry.getValue();
            }
        }
        if (target instanceof AgeableMob) {
            return EntityRelation.coalesce(config.player_relation_to_passives, EntityRelation.HOSTILE);
        }
        if (target instanceof Monster) {
            return EntityRelation.coalesce(config.player_relation_to_hostiles, EntityRelation.HOSTILE);
        }
        return EntityRelation.coalesce(config.player_relation_to_other, EntityRelation.HOSTILE);
    }

    private static Map<TagKey<EntityType<?>>, EntityRelation> RELATION_TAG_CACHE = null;
    private static Map<TagKey<EntityType<?>>, EntityRelation> getRelationTagsCache() {
        if (RELATION_TAG_CACHE == null) {
            RELATION_TAG_CACHE = new HashMap<>();
            for (var entrySet: SpellEngineMod.config.player_relation_tags.entrySet()) {
                var tagString = entrySet.getKey();
                var relation = entrySet.getValue();
                var tag = TagKey.create(Registries.ENTITY_TYPE, Identifier.parse(tagString));
                RELATION_TAG_CACHE.put(tag, relation);
            }
        }
        return RELATION_TAG_CACHE;
    }

    public record TeamRelation(boolean areTeammates, boolean friendlyFireAllowed) { }
    public interface TeamMatcher { @Nullable TeamRelation getRelation(Entity attacker, Entity target); }
    private static final Map<String, TeamMatcher> TEAM_MATCHERS = new LinkedHashMap<>();
    public static void registerTeamMatcher(String name, TeamMatcher matcher) {
        TEAM_MATCHERS.put(name, matcher);
    }
    static {
        registerTeamMatcher("vanilla", (entity1, entity2) -> {
            var team1 = entity1.getTeam();
            var team2 = entity2.getTeam();
            if (team1 == null || team2 == null) {
                return null;
            }
            var friendlyFire = team1.isAllowFriendlyFire();
            return new TeamRelation(entity1.isAlliedTo(entity2), friendlyFire);
        });
    }

    // Make sure this complies with comment in `ServerConfig`
    //
    // NEUTRAL allows AREA DAMAGE. It reads as a monotone gradient: harm rises left to right, help
    // falls, and every column is distinct. That last part is what makes NEUTRAL usable as a relation
    // at all — while its two damage rows matched FRIENDLY's, NEUTRAL meant nothing more than
    // "FRIENDLY, minus group heals", and there was no value in this enum meaning "fair game to
    // damage, but not an enemy".
    //
    // That absence is what forced `player_relation_to_passives` to HOSTILE: not because anyone
    // considers a cow an enemy, but because HOSTILE was the only column that let a player's AoE hit
    // one. Summon AI then read that column — chosen purely for its damage permissions — as a
    // statement of enmity, and hunted down livestock. Passives are NEUTRAL now, and HOSTILE means
    // "enemy" and nothing else.
    private static final boolean[][] TABLE_OF_ULTIMATE_JUSTICE = {
            // ALLY     FRIENDLY        NEUTRAL HOSTILE MIXED
            { false,    true,           true,   true,   true }, // Direct Damage
            { false,    false,          true,   true,   true }, // Area Damage
            { true,     true,           true,   false,  true }, // Direct Healing
            { true,     true,           false,  false,  true }, // Area Healing
    };

    public static boolean actionAllowed(SpellTarget.FocusMode focusMode, SpellTarget.Intent intent, LivingEntity attacker, Entity target) {
        var relation = getRelation(attacker, target);

        int row = 0;
        if (intent == SpellTarget.Intent.HELPFUL) {
            row += 2;
        }
        if (focusMode == SpellTarget.FocusMode.AREA) {
            row += 1;
        }

        int column = 0;
        switch (relation) {
            case ALLY -> {
                column = 0;
            }
            case FRIENDLY -> {
                column = 1;
            }
            case NEUTRAL -> {
                column = 2;
            }
            case HOSTILE -> {
                column = 3;
            }
            case MIXED -> {
                column = 4;
            }
        }
        return TABLE_OF_ULTIMATE_JUSTICE[row][column];
    }

    // Generalized copy of shouldDamagePlayer
    public static boolean allowedToHurt(Entity e1, Entity e2) {
        Team abstractTeam = e1.getTeam();
        Team abstractTeam2 = e2.getTeam();
        if (abstractTeam == null) {
            return true;
        } else {
            return !abstractTeam.isAlliedTo(abstractTeam2) || abstractTeam.isAllowFriendlyFire();
        }
    }
}
