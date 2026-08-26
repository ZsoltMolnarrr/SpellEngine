package net.spell_engine.internals.cost;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.spell_engine.SpellEngineMod;
import net.spell_engine.api.spell.Spell;
import net.spell_engine.compat.container.ContainerCompat;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

public class Ammo {
    private static final Identifier SPELL_INFINITY = Identifier.fromNamespaceAndPath(SpellEngineMod.ID, "spell_infinity");

    public record Searched(@Nullable TagKey<Item> tag, @Nullable Item item) {
        public static Searched from(String stringId) {
            if (stringId.startsWith("#")) {
                return new Searched(TagKey.create(Registries.ITEM, Identifier.parse(stringId.substring(1))), null);
            } else {
                return new Searched(null, BuiltInRegistries.ITEM.getValue(Identifier.parse(stringId)));
            }
        }
        public boolean isValid() {
            return item != null || tag != null;
        }
        public boolean matches(ItemStack stack) {
            if (tag != null) {
                return stack.is(tag);
            } else if (item != null) {
                return stack.is(item);
            }
            return false;
        }
        public Predicate<ItemStack> asPredicate() {
            return this::matches;
        }
        public String getTranslationKey() {
            if (tag != null) {
                // TagKey#getTranslationKey is a Fabric API interface injection (absent on NeoForge); same format inline
                var id = tag.location();
                return "tag." + tag.registry().identifier().getPath() + "." + id.getNamespace() + "." + id.getPath().replace("/", ".");
            } else if (item != null) {
                return item.getDescriptionId();
            }
            return "";
        }
    }

    public record Source(ItemStack itemStack, int found, boolean isContainer) { }
    public record Result(boolean satisfied, Searched item, int consume, List<Source> sources) { }
    public static Result ammoForSpell(Player player, Spell spell, ItemStack casterStack) {
        boolean satisfied = true;
        Searched ammo = null;
        int consume = 0;
        List<Source> sources = List.of();
        if (spell.cost.item != null && spell.cost.item.id != null && !spell.cost.item.id.isEmpty()) {
            ammo = Searched.from(spell.cost.item.id);
            if (player.getAbilities().instabuild
                    || !SpellEngineMod.config.spell_cost_item_allowed) {
                return new Result(satisfied, ammo, consume, sources);
            }
            var id = Identifier.parse(spell.cost.item.id);
            var needsArrow = id.getPath().contains("arrow");

            var enchantmentQuery = needsArrow
                    ? player.level().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).get(Enchantments.INFINITY)
                    : player.level().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).get(SPELL_INFINITY);
            if (enchantmentQuery.isPresent() &&
                    EnchantmentHelper.getItemEnchantmentLevel(enchantmentQuery.get(), casterStack) > 0) { // Has infinity
                return new Result(satisfied, ammo, consume, sources);
            }

            if(ammo.isValid()) {
                var amountNeeded = spell.cost.item.amount;
                sources = findSources(player, ammo, amountNeeded);
                var amountAvailable = sources.stream().mapToInt(Source::found).sum();
                satisfied = amountAvailable >= amountNeeded;
                consume = (satisfied && spell.cost.item.consume) ? amountAvailable : 0;
            }
        }
        return new Result(satisfied, ammo, consume, sources);
    }

    public static List<Source> findSources(Player player, Searched searched, int totalAmount) {
        ArrayList<Source> sources = new ArrayList<>();
        var foundAmount = 0;
        var container = findContainer(player, searched.asPredicate(), totalAmount);
        if (container != null) {
            sources.add(container);
            foundAmount += container.found();
        }
        if (foundAmount < totalAmount) {
            var inventory = player.getInventory();
            for (int i = 0; i < inventory.getContainerSize(); ++i) {
                var stack = inventory.getItem(i);
                if (searched.matches(stack)) {
                    var source = sourceFromStack(stack, totalAmount - foundAmount);
                    sources.add(source);
                    foundAmount += source.found();
                }
                if (foundAmount >= totalAmount) {
                    break;
                }
            }
        }
        return sources;
    }

    private static Source sourceFromStack(ItemStack stack, int amount) {
        var found = Math.min(stack.getCount(), amount);
        return new Source(stack, found, false);
    }

    @Nullable public static Ammo.Source findContainer(Player player, Predicate<ItemStack> item, int amount) {
        for (var provider : ContainerCompat.providers) {
            var stacks = provider.apply(player);
            for (var stack : stacks) {
                var found = Math.min(findInContainer(stack, item), amount);
                if (found > 0) {
                    return new Ammo.Source(stack, found, true);
                }
            }
        }
        return null;
    }

    public static int findInContainer(ItemStack containerStack, Predicate<ItemStack> consumedItem) {
        int found = 0;
        var bundle = ContainerCompat.getContainerComponent(containerStack);
        if (bundle != null) {
            for (int i = 0; i < bundle.size(); i++) {
                var storedStack = bundle.get(i);
                if (consumedItem.test(storedStack)) {
                    found += storedStack.getCount();
                }
            }
        }
        return found;
    }

    public static ItemStack findFirstInContainer(ItemStack containerStack, Predicate<ItemStack> consumedItem) {
        var bundle = ContainerCompat.getContainerComponent(containerStack);
        if (bundle != null) {
            for (int i = 0; i < bundle.size(); i++) {
                var storedStack = bundle.get(i);
                if (consumedItem.test(storedStack)) {
                    return storedStack;
                }
            }
        }
        return ItemStack.EMPTY;
    }

    public static void consume(Result result, Player player) {
        if (result.consume() > 0) {
            for (var source: result.sources()) {
                if (source.isContainer()) {
                    takeFromContainer(source.itemStack(), result.item(), source.found());
                } else {
                    ContainerHelper.clearOrCountMatchingItems(player.getInventory(), result.item().asPredicate(), source.found(), false);
//                    for (int i = 0; i < result.consume(); i++) {
//                        player.getInventory().removeOne(source.itemStack());
//                    }
                }
            }
        }
    }

    public static int takeFromContainer(ItemStack containerStack, Searched consumedItem, int amount) {
        return takeFromContainer(containerStack, consumedItem.asPredicate(), amount);
    }

    public static int takeFromContainer(ItemStack containerStack, Predicate<ItemStack> consumedItem, int amount) {
        int taken = 0;
        var bundle = ContainerCompat.getContainerComponent(containerStack);
        var toDecreement = amount;
        if (bundle != null) {
            var putBack = new ArrayList<ItemStack>();
            for (int i = 0; i < bundle.size(); i++) {
                var storedStack = bundle.get(i);
                if (consumedItem.test(storedStack)) {
                    var decrementable = Math.min(storedStack.getCount(), toDecreement);
                    storedStack.shrink(decrementable);
                    toDecreement -= decrementable;
                    taken += decrementable;
                }
                if (!storedStack.isEmpty()) {
                    putBack.add(storedStack);
                }
            }
            var newBundle = bundle.createNewWithContents(putBack.reversed());
            newBundle.attachTo(containerStack);
        }
        return taken;
    }
}
