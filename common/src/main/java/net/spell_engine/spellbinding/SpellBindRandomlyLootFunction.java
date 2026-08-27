package net.spell_engine.spellbinding;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.functions.LootItemConditionalFunction;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.level.storage.loot.providers.number.NumberProvider;
import net.minecraft.world.level.storage.loot.providers.number.NumberProviders;
import net.spell_engine.SpellEngineMod;
import net.spell_engine.api.spell.Spell;
import net.spell_engine.api.spell.SpellDataComponents;
import net.spell_engine.api.spell.container.SpellContainer;
import net.spell_engine.api.spell.registry.SpellRegistry;
import net.spell_engine.api.spell.container.SpellContainerHelper;
import net.spell_engine.item.ScrollItem;
import net.spell_engine.item.SpellEngineItems;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public class SpellBindRandomlyLootFunction extends LootItemConditionalFunction {
    public static final String NAME = "spell_bind_randomly";
    public static final Identifier ID = Identifier.fromNamespaceAndPath(SpellEngineMod.ID, NAME);

    public static final MapCodec<SpellBindRandomlyLootFunction> CODEC = RecordCodecBuilder.mapCodec(
            instance -> commonFields(instance)
                    .<String, NumberProvider, NumberProvider>and(
                            instance.group(
                                    Codec.STRING.fieldOf("pool").orElse(null).forGetter(function -> function.pool),
                                    NumberProviders.CODEC.fieldOf("tier").forGetter(function -> function.tier),
                                    NumberProviders.CODEC.fieldOf("count").forGetter(function -> function.count)
                            )
                    )
                    .apply(instance, SpellBindRandomlyLootFunction::new)
    );

    private final NumberProvider tier;
    @Nullable private final String pool;
    @Nullable private final NumberProvider count;

    private SpellBindRandomlyLootFunction(List<LootItemCondition> conditions, String pool, NumberProvider tier, NumberProvider count) {
        super(conditions);
        this.pool = pool;
        this.tier = tier;
        this.count = count;
    }

    @Override
    public MapCodec<SpellBindRandomlyLootFunction> codec() {
        return CODEC;
    }


    @Nullable TagKey<Spell> getSpellTag() {
        if (this.pool == null || this.pool.isEmpty()) {
            return null;
        }
        Identifier id;
        if (this.pool.startsWith("#")) {
            id = Identifier.parse(this.pool.substring(1));
        } else {
            id = Identifier.parse(this.pool);
        }
        return TagKey.create(SpellRegistry.KEY, id);
    }

    @Override
    public ItemStack run(ItemStack stack, LootContext context) {
        @Nullable final var spellTag = getSpellTag();
        final var selectedTier = this.tier != null ? this.tier.getInt(context) : -1;
        @Nullable var existingContainer = SpellContainerHelper.containerFromItemStack(stack);
        final List<Identifier> alreadyPresentSpells = existingContainer != null
                ? existingContainer.spell_ids().stream().map(Identifier::parse).toList()
                : List.of();
        var spells = SpellRegistry.stream(context.getLevel())
                .filter(entry -> {
                    var id = entry.unwrapKey().get().identifier();
                    return (selectedTier < 0 || entry.value().tier == selectedTier)
                            // && (entry.value().active != null && entry.value().active.scroll != null)
                            && (spellTag == null || entry.is(spellTag))
                            && !alreadyPresentSpells.contains(id);
                })
                .toList();

        ArrayList<Holder<Spell>> selectedSpells = new ArrayList<>();
        if (!spells.isEmpty()) {
            var selectedCount = this.count != null ? this.count.getInt(context) : 1;
            var retryAttempts = 3;
            for (int i = 0; i < selectedCount; i++) {
                var entry = spells.get(context.getRandom().nextInt(spells.size()));
                while (
                        (retryAttempts > 0) &&
                                // Reroll if
                                // already selected
                                (
                                        selectedSpells.contains(entry)
                                )
                ) {
                    entry = spells.get(context.getRandom().nextInt(spells.size()));
                    retryAttempts -= 1;
                }

                selectedSpells.add(entry);
            }
        }

        if (!selectedSpells.isEmpty()) {
            var newContainer = existingContainer != null ? existingContainer : SpellContainer.EMPTY;
            var newSpellIds = selectedSpells.stream().map(entry -> entry.unwrapKey().get().identifier().toString()).toList();
            newContainer = newContainer
                    .withAdditionalSpell(newSpellIds);
            var sortedSpellIds = SpellContainerHelper.sortedSpells(context.getLevel(), newContainer.spell_ids());
            newContainer = newContainer.copyWith(sortedSpellIds);

            stack.set(SpellDataComponents.SPELL_CONTAINER, newContainer);

            if (stack.getItem() == SpellEngineItems.SCROLL.get()) {
                ScrollItem.onSpellAdded(stack, selectedSpells.getFirst(), ScrollItem.resolveSpellPool(context.getLevel(), selectedSpells.getFirst()));
            }
        } else {
            if (stack.getItem() == SpellEngineItems.SCROLL.get()) {
                return ItemStack.EMPTY;
            }
        }

        return stack;
    }

//    public static ConditionalLootFunction.Builder<?> builder(String pool, LootNumberProvider tier) {
//        return builder(conditions -> new SpellBindRandomlyLootFunction(conditions, tier, null));
//    }

    public static LootItemConditionalFunction.Builder<?> builder(String pool, NumberProvider tier, NumberProvider count) {
        return simpleBuilder(conditions -> new SpellBindRandomlyLootFunction(conditions, pool, tier, count));
    }
}

