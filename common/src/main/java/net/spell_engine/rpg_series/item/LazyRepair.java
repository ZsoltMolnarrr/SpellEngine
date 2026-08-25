package net.spell_engine.rpg_series.item;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.RepairableComponent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.Ingredient;
import net.minecraft.registry.entry.RegistryEntryList;
import net.spell_engine.api.spell.SpellDataComponents;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

/// A lazily-resolved anvil repair ingredient, kept alive next to the vanilla `minecraft:repairable` component.
///
/// Since 1.21.2 `Item.canRepair` is gone: anvil repair is `ItemStack#canRepairWith`, which only consults the
/// `minecraft:repairable` component, and that component bakes a `RegistryEntryList<Item>` at construction.
/// Two things cannot be expressed that way:
/// - a **cross-mod** ingredient (the other mod's item may not be registered yet when this item is constructed,
///   so `Ingredient.ofItems(Registries.ITEM.get(...))` resolves to a fallback / AIR at that point);
/// - a **config-driven** ingredient that is read after item registration.
///
/// This transient data component (`spell_engine:lazy_repair`, never serialised, never synced) stores the original
/// supplier. SpellEngine's `ItemStackRepairMixin` injects at the HEAD of `ItemStack#canRepairWith` and returns
/// `true` when the supplier's ingredient matches, otherwise the vanilla `repairable` check runs unchanged.
///
/// Semantics:
/// - `apply(settings, supplier)` does **both**: it bakes a `repairable` snapshot from whatever the supplier resolves
///   to right now (so the vanilla tooltip / data-driven consumers still see something sensible), and attaches the
///   lazy component for the anvil.
/// - The supplier is invoked on every anvil check; keep it cheap (an `Ingredient.ofItems(...)` lookup is fine).
/// - A `null` or empty ingredient at apply time simply skips the `repairable` snapshot; the lazy check still runs.
/// - It is an item *default* component: do not `stack.set(...)` it — the component has no persistent codec.
///
/// Used by {@link Weapon.CustomMaterial} and {@link Shield} (the default shield factory). Ranged weapons keep
/// their own equivalent inside RangedWeaponAPI (`CustomBow`/`CustomCrossbow` + its `ItemStackRepairMixin`).
public record LazyRepair(Supplier<Ingredient> ingredient) {

    /// Placeholder used by the packet codec; a stack should never carry this component in its patch.
    public static final LazyRepair NONE = new LazyRepair(() -> null);

    /// Bakes a `minecraft:repairable` snapshot from the supplier's current value and attaches the lazy component.
    public static Item.Settings apply(Item.Settings settings, @Nullable Supplier<Ingredient> supplier) {
        if (supplier == null) {
            return settings;
        }
        var ingredient = supplier.get();
        if (ingredient != null && !ingredient.isEmpty()) {
            settings.component(DataComponentTypes.REPAIRABLE,
                    new RepairableComponent(RegistryEntryList.of(ingredient.getMatchingItems().toList())));
        }
        settings.component(SpellDataComponents.LAZY_REPAIR, new LazyRepair(supplier));
        return settings;
    }

    /// `true` if `stack` carries a lazy repair ingredient that accepts `repairMaterial`.
    public static boolean matches(ItemStack stack, ItemStack repairMaterial) {
        var lazy = stack.get(SpellDataComponents.LAZY_REPAIR);
        if (lazy == null || lazy.ingredient() == null) {
            return false;
        }
        var ingredient = lazy.ingredient().get();
        return ingredient != null && ingredient.test(repairMaterial);
    }
}
