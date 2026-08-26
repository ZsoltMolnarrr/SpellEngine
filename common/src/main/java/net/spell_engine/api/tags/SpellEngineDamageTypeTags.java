package net.spell_engine.api.tags;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageType;
import net.spell_engine.SpellEngineMod;

import java.util.ArrayList;

public class SpellEngineDamageTypeTags {
    public static final ArrayList<TagKey<DamageType>> ALL = new ArrayList<>();
    private static TagKey<DamageType> create(String id) {
        var tag = TagKey.create(Registries.DAMAGE_TYPE, Identifier.fromNamespaceAndPath(SpellEngineMod.ID, id));
        ALL.add(tag);
        return tag;
    }
    public static final TagKey<DamageType> EVADABLE = create("evadable");
}
