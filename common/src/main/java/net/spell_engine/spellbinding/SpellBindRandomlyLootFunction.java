package net.spell_engine.spellbinding;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.item.ItemStack;
import net.minecraft.loot.condition.LootCondition;
import net.minecraft.loot.context.LootContext;
import net.minecraft.loot.context.LootContextParameter;
import net.minecraft.loot.function.ConditionalLootFunction;
import net.minecraft.loot.function.LootFunctionType;
import net.minecraft.loot.provider.number.LootNumberProvider;
import net.minecraft.loot.provider.number.LootNumberProviderTypes;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;
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

public class SpellBindRandomlyLootFunction extends ConditionalLootFunction {
    public static final String NAME = "spell_bind_randomly";
    public static final Identifier ID = Identifier.of(SpellEngineMod.ID, NAME);

    public static final MapCodec<SpellBindRandomlyLootFunction> CODEC = RecordCodecBuilder.mapCodec(
            instance -> addConditionsField(instance)
                    .<String, LootNumberProvider, LootNumberProvider>and(
                            instance.group(
                                    Codec.STRING.fieldOf("pool").orElse(null).forGetter(function -> function.pool),
                                    LootNumberProviderTypes.CODEC.fieldOf("tier").forGetter(function -> function.tier),
                                    LootNumberProviderTypes.CODEC.fieldOf("count").forGetter(function -> function.count)
                            )
                    )
                    .apply(instance, SpellBindRandomlyLootFunction::new)
    );
    public static final LootFunctionType<SpellBindRandomlyLootFunction> TYPE = new LootFunctionType<SpellBindRandomlyLootFunction>(CODEC);

    private final LootNumberProvider tier;
    @Nullable private final String pool;
    @Nullable private final LootNumberProvider count;

    private SpellBindRandomlyLootFunction(List<LootCondition> conditions, String pool, LootNumberProvider tier, LootNumberProvider count) {
        super(conditions);
        this.pool = pool;
        this.tier = tier;
        this.count = count;
    }

    @Override
    public LootFunctionType<SpellBindRandomlyLootFunction> getType() {
        return TYPE;
    }

    @Override
    public Set<LootContextParameter<?>> getRequiredParameters() {
        // return this.tier.getRequiredParameters();
        return Set.of();
    }

    @Nullable TagKey<Spell> getSpellTag() {
        if (this.pool == null || this.pool.isEmpty()) {
            return null;
        }
        Identifier id;
        if (this.pool.startsWith("#")) {
            id = Identifier.of(this.pool.substring(1));
        } else {
            id = Identifier.of(this.pool);
        }
        return TagKey.of(SpellRegistry.KEY, id);
    }

    @Override
    public ItemStack process(ItemStack stack, LootContext context) {
        @Nullable final var spellTag = getSpellTag();
        final var selectedTier = this.tier != null ? this.tier.nextInt(context) : -1;
        @Nullable var existingContainer = SpellContainerHelper.containerFromItemStack(stack);
        final List<Identifier> alreadyPresentSpells = existingContainer != null
                ? existingContainer.spell_ids().stream().map(Identifier::of).toList()
                : List.of();
        var spells = SpellRegistry.stream(context.getWorld())
                .filter(entry -> {
                    var id = entry.getKey().get().getValue();
                    return (selectedTier < 0 || entry.value().tier == selectedTier)
                            // && (entry.value().active != null && entry.value().active.scroll != null)
                            && (spellTag == null || entry.isIn(spellTag))
                            && !alreadyPresentSpells.contains(id);
                })
                .toList();

        ArrayList<RegistryEntry<Spell>> selectedSpells = new ArrayList<>();
        if (!spells.isEmpty()) {
            var selectedCount = this.count != null ? this.count.nextInt(context) : 1;
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
            var newSpellIds = selectedSpells.stream().map(entry -> entry.getKey().get().getValue().toString()).toList();
            newContainer = newContainer
                    .withAdditionalSpell(newSpellIds);
            var sortedSpellIds = SpellContainerHelper.sortedSpells(context.getWorld(), newContainer.spell_ids());
            newContainer = newContainer.copyWith(sortedSpellIds);

            stack.set(SpellDataComponents.SPELL_CONTAINER, newContainer);

            if (stack.getItem() == SpellEngineItems.SCROLL.get()) {
                ScrollItem.onSpellAdded(stack, selectedSpells.getFirst(), ScrollItem.resolveSpellPool(context.getWorld(), selectedSpells.getFirst()));
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

    public static ConditionalLootFunction.Builder<?> builder(String pool, LootNumberProvider tier, LootNumberProvider count) {
        return builder(conditions -> new SpellBindRandomlyLootFunction(conditions, pool, tier, count));
    }
}

