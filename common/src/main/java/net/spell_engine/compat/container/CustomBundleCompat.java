package net.spell_engine.compat.container;

import com.github.theredbrain.bundleapi.item.CustomBundleItem;

/// BundleAPI 1.1.0.001 (1.20.1) integration. `CustomBundleItem` no longer extends `BundleItem` and there is
/// no `CustomBundleContentsComponent`; contents live in the stack's `Items` NBT list with the vanilla
/// bundle layout (`CustomBundleContents.ITEMS_KEY == "Items"`), so the shared NBT adapter from
/// {@link ContainerCompat} is reused unchanged — only the item check is BundleAPI-specific.
///
/// Kept in its own class so the `CustomBundleItem` reference is only linked when `bundleapi` is loaded.
public class CustomBundleCompat {
    public static void init() {
        ContainerCompat.resolvers.add(itemStack -> {
            if (itemStack.getItem() instanceof CustomBundleItem) {
                return new ContainerCompat.NbtBundleAdapter(ContainerCompat.readContents(itemStack));
            }
            return null;
        });
    }
}
