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

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/// Resolves which `loot_affiliation` item tags apply to a loot roll, based on the spell book(s)
/// equipped by the looting player (and optionally by their online team members).
public class ClassAffiliation {
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
    }
}
