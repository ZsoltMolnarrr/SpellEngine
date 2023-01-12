package net.spell_engine.mixin.criteria;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.spell_engine.internals.SpellRegistry;
import net.spell_engine.internals.criteria.SpellCastHistory;
import net.spell_power.api.MagicSchool;
import org.spongepowered.asm.mixin.Mixin;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Mixin(ServerPlayerEntity.class)
public class ServerPlayerSpellCastCriteria implements SpellCastHistory {
    private static Map<MagicSchool, Set<Identifier>> castMap() {
        Map<MagicSchool, Set<Identifier>> map = new HashMap<>();
        for(var school: MagicSchool.values()) {
            map.put(school, new HashSet<>());
        }
        return map;
    }

    public Map<MagicSchool, Set<Identifier>> spellCastHistory = castMap();

    @Override
    public void saveSpellCast(MagicSchool magicSchool, Identifier id) {
        spellCastHistory.get(magicSchool).add(id);
    }

    @Override
    public boolean hasCastedAllOf(MagicSchool magicSchool) {
        return spellCastHistory.get(magicSchool).size() == SpellRegistry.numberOfSpells(magicSchool);
    }
}
