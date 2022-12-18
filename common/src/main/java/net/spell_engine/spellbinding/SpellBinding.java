package net.spell_engine.spellbinding;

import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.spell_engine.SpellEngineMod;
import net.spell_engine.api.spell.Spell;
import net.spell_engine.internals.SpellContainerHelper;
import net.spell_engine.internals.SpellRegistry;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class SpellBinding {
    public static final String name = "spell_binding";
    public static final Identifier ID = new Identifier(SpellEngineMod.ID, name);

    public record Offer(int id, int cost, int levelRequirement) {  }

    public static List<Offer> offersFor(ItemStack itemStack) {
        var container = SpellContainerHelper.containerFromItemStack(itemStack);
        if (container == null) {
            return List.of();
        }
        return SpellRegistry.all().entrySet().stream()
                .filter(entry -> entry.getValue().school == container.school)
                .sorted(new Comparator<Map.Entry<Identifier, Spell>>() {
                    @Override
                    public int compare(Map.Entry<Identifier, Spell> spell1, Map.Entry<Identifier, Spell> spell2) {
                        if (spell1.getValue().learn.tier > spell2.getValue().learn.tier) {
                            return 1;
                        }  else if (spell1.getValue().learn.tier < spell2.getValue().learn.tier) {
                            return -1;
                        } else {
                            return spell1.getKey().toString().compareTo(spell2.getKey().toString());
                        }
                    }
                })
                .map(entry -> new Offer(
                    SpellRegistry.rawId(entry.getKey()),
                    entry.getValue().learn.tier * entry.getValue().learn.level_cost_per_tier,
                    entry.getValue().learn.tier * entry.getValue().learn.level_requirement_per_tier
                ))
                .collect(Collectors.toList());
    }
}
