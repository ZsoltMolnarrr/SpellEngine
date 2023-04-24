package net.spell_engine.mixin.criteria;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.spell_engine.internals.SpellRegistry;
import net.spell_engine.internals.criteria.SpellCastCriteria;
import net.spell_engine.internals.criteria.SpellCastHistory;
import net.spell_power.api.MagicSchool;
import org.spongepowered.asm.mixin.Mixin;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Mixin(ServerPlayerEntity.class)
public class ServerPlayerSpellCastCriteria implements SpellCastHistory {
    private static Map<Identifier, Set<Identifier>> castMap() {
        Map<Identifier, Set<Identifier>> map = new HashMap<>();
        for(var entry: SpellRegistry.pools().entrySet()) {
            map.put(entry.getKey(), new HashSet<>());
        }
        return map;
    }

    // Key: Pool id, Value: Spell Ids
    public Map<Identifier, Set<Identifier>> spellCastHistory = new HashMap<>();

    @Override
    public void saveSpellCast(Identifier spellId) {
        if (spellCastHistory.isEmpty()) {
            spellCastHistory = castMap();
        }
        var serverPlayer = (ServerPlayerEntity) (Object) this;
        for (var poolId: SpellRegistry.poolsOfSpell(spellId)) {
            var poolHistory = spellCastHistory.get(poolId);
            poolHistory.add(spellId);
            var pool = SpellRegistry.spellPool(poolId);

            // If all spells have been casted
            if (poolHistory.size() == pool.spellIds().size()) {
                SpellCastCriteria.INSTANCE.trigger(serverPlayer, poolId);
            }
        }
    }
}
