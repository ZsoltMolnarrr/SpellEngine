package net.spell_engine.rpg_series.loot;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.loot.context.LootContext;
import net.minecraft.loot.context.LootContextParameters;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;
import net.spell_engine.internals.container.SpellContainerSource;
import net.spell_engine.rpg_series.tags.RPGSeriesItemTags;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/// Resolves which `loot_affiliation` item tags apply to a loot roll, based on the spell book(s)
/// equipped by the looting player (and optionally by their online team members).
///
/// How a player is affiliated can be replaced, see {@link #resolver}.
public class ClassAffiliation {
    /// Determines the loot affiliation of a single player: the item tags, those items are relevant for them.
    /// Return an empty collection when it cannot be determined, loot is rolled as configured then.
    /// Called on the server, for the looting player (and for each online team member, when enabled).
    @FunctionalInterface
    public interface Resolver {
        Collection<TagKey<Item>> resolve(PlayerEntity player);
    }

    /// Default logic: equipped spell book of the pool `<namespace>:spell_book/<name>`
    /// -> item tag `<namespace>:loot_affiliation/<name>`
    public static final Resolver SPELL_BOOK_RESOLVER = ClassAffiliation::fromSpellBooks;

    /// Replace to determine the loot affiliation of players on a different basis
    /// (class system of another mod, skill tree, attributes...), tags of any id can be returned.
    /// To extend the default logic instead of replacing it, call {@link #SPELL_BOOK_RESOLVER} within.
    public static Resolver resolver = SPELL_BOOK_RESOLVER;

    // A loot table generation rolls several pools / entries with the same context, resolve once
    private static final Map<LootContext, Set<TagKey<Item>>> CACHE_SELF = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<LootContext, Set<TagKey<Item>>> CACHE_TEAM = Collections.synchronizedMap(new WeakHashMap<>());

    /// Empty when the affiliation cannot be determined (no player, missing or unknown spell book).
    public static Set<TagKey<Item>> resolve(LootContext context, boolean includeTeam) {
        var cache = includeTeam ? CACHE_TEAM : CACHE_SELF;
        var cached = cache.get(context);
        if (cached != null) { return cached; }
        var looter = looter(context);
        Set<TagKey<Item>> result = looter != null ? of(looter, includeTeam) : Set.of();
        cache.put(context, result);
        return result;
    }

    @Nullable private static PlayerEntity looter(LootContext context) {
        if (context.get(LootContextParameters.THIS_ENTITY) instanceof PlayerEntity player) {
            return player; // Chests, vaults, archaeology...
        }
        var killer = context.get(LootContextParameters.LAST_DAMAGE_PLAYER);
        if (killer != null) {
            return killer;
        }
        if (context.get(LootContextParameters.ATTACKING_ENTITY) instanceof PlayerEntity player) {
            return player;
        }
        return null;
    }

    public static Set<TagKey<Item>> of(PlayerEntity player, boolean includeTeam) {
        var tags = new LinkedHashSet<TagKey<Item>>();
        collect(player, tags);
        var team = includeTeam ? player.getScoreboardTeam() : null;
        var server = player.getServer();
        if (team != null && server != null) {
            // Online members only, the equipment of offline players is not available
            for (var name: team.getPlayerList()) {
                var member = server.getPlayerManager().getPlayer(name);
                if (member != null && member != player) {
                    collect(member, tags);
                }
            }
        }
        return tags;
    }

    private static void collect(PlayerEntity player, Set<TagKey<Item>> tags) {
        var resolved = resolver.resolve(player);
        if (resolved != null) {
            tags.addAll(resolved);
        }
    }

    private static Collection<TagKey<Item>> fromSpellBooks(PlayerEntity player) {
        var tags = new ArrayList<TagKey<Item>>();
        for (var source: SpellContainerSource.getSpellsOf(player).sources()) {
            var pool = source.container().pool();
            if (pool == null || pool.isEmpty()) { continue; }
            var poolId = Identifier.tryParse(pool.startsWith("#") ? pool.substring(1) : pool);
            if (poolId == null) { continue; }
            var tag = RPGSeriesItemTags.LootAffiliation.get(poolId);
            if (tag != null) {
                tags.add(tag);
            }
        }
        return tags;
    }
}
