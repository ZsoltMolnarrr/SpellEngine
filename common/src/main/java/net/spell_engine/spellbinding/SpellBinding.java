package net.spell_engine.spellbinding;

import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.spell_engine.SpellEngineMod;
import net.spell_engine.api.spell.Spell;
import net.spell_engine.api.spell.registry.SpellRegistry;
import net.spell_engine.api.spell.container.SpellContainerHelper;
import net.spell_engine.api.tags.SpellEngineItemTags;
import net.spell_engine.api.tags.SpellTags;
import net.spell_engine.spellbinding.spellchoice.SpellChoices;

import java.util.*;
import java.util.stream.Collectors;

public class SpellBinding {
    public static final Identifier ADVANCEMENT_VISIT_ID = Identifier.fromNamespaceAndPath(SpellEngineMod.ID, "visit_spell_binding_table");
    public static final String name = "spell_binding";
    public static final Identifier ID = Identifier.fromNamespaceAndPath(SpellEngineMod.ID, name);
    private static final float LIBRARY_POWER_BASE = 10;
    private static final float LIBRARY_POWER_MULTIPLIER = 1.5F;
    private static final int LIBRARY_POWER_CAP = 18;
    public static final int BOOK_OFFSET = 1;
    public enum Mode { SPELL, BOOK }
    public record Offer(int id, int levelCost, int levelRequirement, int lapisCost, boolean isPowered) {  }
    public record OfferResult(Mode mode, List<Offer> offers) { }

    public static List<TagKey<Spell>> availableSpellBookTags(Level world) {
        var wrapper = world.registryAccess().lookup(SpellRegistry.KEY);
        if (wrapper.isEmpty()) {
            return List.of();
        }
        return wrapper.get().getTags()
                .filter(tag -> tag.key().location().getPath().startsWith(SpellTags.SPELL_BOOK_PREFIX)
                        && tag.size() > 0)  // Filter out empty tags
                .map(tag -> tag.key())
                .sorted(Comparator.comparing(tag -> tag.location().getNamespace() + "_" + tag.location().getPath()))
                .toList();
    }

    public static OfferResult offersFor(Level world, boolean creative, ItemStack itemStack, ItemStack consumableStack, int libraryPower) {
        if (itemStack.getItem() == Items.BOOK) {
            var tags = availableSpellBookTags(world);
            var offers = new ArrayList<Offer>();
            if (SpellEngineMod.config.spell_book_creation_enabled) {
                for (int i = 0; i < tags.size(); ++i) {
                    offers.add(new Offer(
                            i + BOOK_OFFSET,
                            SpellEngineMod.config.spell_book_creation_cost,
                            SpellEngineMod.config.spell_book_creation_requirement,
                            0,
                            true));
                }
            }
            return new OfferResult(Mode.BOOK, offers);
        }

        var choices = SpellChoices.from(itemStack);
        if (choices != null) {
            return new OfferResult(Mode.SPELL, List.of());
        }
        var container = SpellContainerHelper.containerFromItemStack(itemStack);
        if (container == null) {
            return new OfferResult(Mode.SPELL, List.of());
        }
        var pool = SpellRegistry.entries(world, container.pool());
        if (pool == null || pool.isEmpty()) {
            return new OfferResult(Mode.SPELL, List.of());
        }

        List<Holder<Spell>> spells;
        var consumableContainer = SpellContainerHelper.containerFromItemStack(consumableStack);
        var scrollMode = false;
        if (consumableStack.is(SpellEngineItemTags.SPELL_BOOK_MERGEABLE) && consumableContainer != null) {
            scrollMode = true;
            var spellRegistry = SpellRegistry.from(world);
            var consumableSpells = consumableContainer.spell_ids().stream()
                    .map(Identifier::parse)
                    .map(spellRegistry::get)
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .toList();
            var availableSpellIds = pool.stream()
                    .map(entry -> entry.unwrapKey().get().identifier())
                    .collect(Collectors.toSet());
            spells = consumableSpells.stream()
                    .filter(entry -> {
                        var spellId = entry.unwrapKey().get().identifier();
                        return availableSpellIds.contains(spellId) || creative;
                    })
                    .map(entry -> (Holder<Spell>) entry)
                    .toList();
        } else {
            spells = pool;
        }

        var spellMap = new HashMap<Identifier, Spell>(); // Refactor: remove this conversion
        spells.forEach(entry -> {
            var spell = entry.value();
            spellMap.put(entry.unwrapKey().get().identifier(), spell);
        });
        final var finalScrollMode = scrollMode;
        return new OfferResult(Mode.SPELL,
                spellMap.entrySet().stream()
                .sorted(SpellContainerHelper.catalogSorter)
                .map(entry -> {
                    var spell = entry.getValue();
                    if (finalScrollMode) {
                        var cost = spell.tier * SpellEngineMod.config.spell_scroll_level_cost_per_tier
                                + SpellEngineMod.config.spell_scroll_apply_cost_base;
                        var levelRequirement = 1;
                        return new Offer(
                                rawSpellId(world, entry.getKey()),
                                cost,
                                levelRequirement,
                                0,
                                true);
                    } else {
                        if (spell.learn != null && spell.tier > 0) {
                            var cost = spell.tier * spell.learn.level_cost_per_tier;
                            var levelCost = Math.max(
                                    SpellEngineMod.config.spell_binding_level_cost_min,
                                    cost * SpellEngineMod.config.spell_binding_level_cost_multiplier + SpellEngineMod.config.spell_binding_level_cost_offset);

                            var levelRequirementDefault = spell.tier * spell.learn.level_requirement_per_tier;
                            var levelRequirement = Math.max(
                                    SpellEngineMod.config.spell_binding_level_requirement_min,
                                    levelRequirementDefault * SpellEngineMod.config.spell_binding_level_requirement_multiplier + SpellEngineMod.config.spell_binding_level_requirement_offset);
                            return new Offer(
                                    rawSpellId(world, entry.getKey()),
                                    levelCost,
                                    levelRequirement,
                                    cost * SpellEngineMod.config.spell_binding_lapis_cost_multiplier,
                                    (libraryPower >= LIBRARY_POWER_CAP)
                                            || ((LIBRARY_POWER_BASE + libraryPower * LIBRARY_POWER_MULTIPLIER) >= levelRequirement)
                            );
                        }
                    }
                    return null;
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList())
        );
    }

    private static int rawSpellId(Level world, Identifier spellId) {
        var registry = SpellRegistry.from(world);
        var entry = registry.get(spellId).get();
        return registry.getId(entry.value());
    }

    public static class State {
        public enum ApplyState { ALREADY_APPLIED, NO_MORE_SLOT, TIER_CONFLICT, APPLICABLE, INVALID }
        public ApplyState state;
        public State(ApplyState state, Requirements requirements) {
            this.state = state;
            this.requirements = requirements;
        }

        public Requirements requirements;
        public record Requirements(int lapisCost, int levelCost, int requiredLevel) {
            public boolean satisfiedFor(Player player, int lapisCount) {
                return player.isCreative() ||
                        (metRequiredLevel(player)
                        && hasEnoughLapis(lapisCount)
                        && hasEnoughLevelsToSpend(player));
            }

            public boolean metRequiredLevel(Player player) {
                return player.experienceLevel >= requiredLevel;
            }

            public boolean hasEnoughLapis(int lapisCount) {
                return lapisCount >= lapisCost;
            }

            public boolean hasEnoughLevelsToSpend(Player player) {
                return player.experienceLevel >= levelCost;
            }
        }

        public static State of(Level world, int spellId, ItemStack itemStack, int levelCost, int requiredLevel, int lapisCost) {
            var registry = SpellRegistry.from(world);
            var spellEntry = registry.get(spellId);
            if (spellEntry.isEmpty()) {
                return new State(ApplyState.INVALID, null);
            }
            return State.of(world, spellEntry.get().unwrapKey().get().identifier(), itemStack, levelCost, requiredLevel, lapisCost);
        }

        public static State of(Level world, Identifier spellId, ItemStack itemStack, int levelCost, int requiredLevel, int lapisCost) {
            var container = SpellContainerHelper.containerFromItemStack(itemStack);
            var requirements = new Requirements(
                    lapisCost,
                    levelCost,
                    requiredLevel);
            if (container == null) {
                return new State(ApplyState.INVALID, requirements);
            }
            if (container.spell_ids().contains(spellId.toString())) {
                return new State(ApplyState.ALREADY_APPLIED, requirements);
            }
            if (container.max_spell_count() > 0 && container.spell_ids().size() >= container.max_spell_count()) {
                return new State(ApplyState.NO_MORE_SLOT, requirements);
            }

            // Check for tier conflicts
            var registry = SpellRegistry.from(world);
            var spellEntry = registry.get(spellId);
            if (spellEntry.isPresent()) {
                var newSpellTier = spellEntry.get().value().tier;
                var otherSpellsInTier = 0;
                // Check existing spell with the same tier
                for (var existingSpellIdString : container.spell_ids()) {
                    var existingSpellId = Identifier.parse(existingSpellIdString);
                    var existingSpellEntry = registry.get(existingSpellId);
                    if (existingSpellEntry.isPresent() && existingSpellEntry.get().value().tier == newSpellTier) {
                        otherSpellsInTier += 1;
                    }
                }
                if (otherSpellsInTier >= container.binding_mutex_count()) {
                    return new State(ApplyState.TIER_CONFLICT, requirements);
                }
            }

            return new State(ApplyState.APPLICABLE, requirements);
        }

        public boolean readyToApply(Player player, int lapisCount) {
            return state == SpellBinding.State.ApplyState.APPLICABLE
                    && requirements != null
                    && requirements.satisfiedFor(player, lapisCount);
        }

        public static State forBook(int cost, int requiredLevel) {
            var requirements = new Requirements(0, cost, requiredLevel);
            return new State(ApplyState.APPLICABLE, requirements);
        }
    }
}
