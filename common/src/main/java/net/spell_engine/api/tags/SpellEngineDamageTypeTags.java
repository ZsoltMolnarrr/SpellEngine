package net.spell_engine.api.tags;

import net.minecraft.entity.damage.DamageType;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;
import net.spell_engine.SpellEngineMod;

import java.util.ArrayList;

public class SpellEngineDamageTypeTags {
    public static final ArrayList<TagKey<DamageType>> ALL = new ArrayList<>();
    private static TagKey<DamageType> create(String id) {
        var tag = TagKey.of(RegistryKeys.DAMAGE_TYPE, Identifier.of(SpellEngineMod.ID, id));
        ALL.add(tag);
        return tag;
    }
    public static final TagKey<DamageType> EVADABLE = create("evadable");
}
