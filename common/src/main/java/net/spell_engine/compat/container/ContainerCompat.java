package net.spell_engine.compat.container;
import net.spell_engine.Platform;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BundleItem;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/// Read/write access to item containers (bundles) for ammo lookup (`Ammo.findContainer` & co).
///
/// 1.20.1 has no data components: bundle contents are the stack's `Items` NBT list (a list of item-stack
/// compounds, most recently inserted first — see vanilla `BundleItem.addToBundle`). Both the vanilla
/// bundle and BundleAPI 1.1.0.001's `CustomBundleItem` use exactly this layout, so a single NBT adapter
/// serves both; only the item check differs (see {@link CustomBundleCompat}).
public class ContainerCompat {
    public static final String BUNDLE_API_MOD_ID = "bundleapi";
    /// Vanilla `BundleItem.ITEMS_KEY` (private there); BundleAPI's `CustomBundleContents.ITEMS_KEY` is the same.
    public static final String ITEMS_KEY = "Items";

    public static final ArrayList< Function<PlayerEntity, List<ItemStack>> > providers = new ArrayList<>();
    public static void addProvider(Function<PlayerEntity, List<ItemStack>> provider) {
        providers.add(provider);
    }

    public static void init() {
        resolvers.add(itemStack -> {
            if (itemStack.getItem() instanceof BundleItem) {
                return new NbtBundleAdapter(readContents(itemStack));
            }
            return null;
        });
        if (Platform.util().isModLoaded(BUNDLE_API_MOD_ID)) {
            CustomBundleCompat.init();
        }
    }

    @Nullable public static Adapter getContainerComponent(ItemStack itemStack) {
        for (var resolver: resolvers) {
            var adapter = resolver.getContainerAdapter(itemStack);
            if (adapter != null) {
                return adapter;
            }
        }
        return null;
    }

    public static final List<Resolver> resolvers = new ArrayList<>();
    public interface Resolver {
        Adapter getContainerAdapter(ItemStack itemStack);
    }

    public interface Adapter {
        int size();
        ItemStack get(int index);
        Adapter createNewWithContents(List<ItemStack> contents);
        void attachTo(ItemStack itemStack);
    }

    // MARK: NBT layout helpers (shared with CustomBundleCompat)

    /// The stacks stored in the container's `Items` list, in stored order (newest first). Missing list → empty.
    public static List<ItemStack> readContents(ItemStack container) {
        var nbt = container.getNbt();
        if (nbt == null || !nbt.contains(ITEMS_KEY, NbtElement.LIST_TYPE)) {
            return List.of();
        }
        var list = nbt.getList(ITEMS_KEY, NbtElement.COMPOUND_TYPE);
        var stacks = new ArrayList<ItemStack>(list.size());
        for (int i = 0; i < list.size(); i++) {
            var stack = ItemStack.fromNbt(list.getCompound(i));
            if (!stack.isEmpty()) {
                stacks.add(stack);
            }
        }
        return stacks;
    }

    /// Replaces the container's `Items` list with `contents` (written in the given order). An empty list
    /// removes the key, matching vanilla's `dropAllBundledItems`.
    public static void writeContents(ItemStack container, List<ItemStack> contents) {
        if (contents.isEmpty()) {
            container.removeSubNbt(ITEMS_KEY);
            return;
        }
        var list = new NbtList();
        for (var stack : contents) {
            if (stack.isEmpty()) { continue; }
            list.add(stack.writeNbt(new NbtCompound()));
        }
        container.getOrCreateNbt().put(ITEMS_KEY, list);
    }

    /// Immutable snapshot of a bundle's contents. `createNewWithContents` mirrors the 1.21 component builder
    /// semantics (each added stack goes to the front, so the resulting order is the reverse of the input —
    /// callers hand in an already reversed list, see `Ammo.takeFromContainer`).
    public record NbtBundleAdapter(List<ItemStack> stacks) implements Adapter {
        @Override
        public int size() {
            return stacks.size();
        }

        @Override
        public ItemStack get(int index) {
            return stacks.get(index);
        }

        @Override
        public Adapter createNewWithContents(List<ItemStack> contents) {
            var newContents = new ArrayList<ItemStack>(contents.size());
            for (var stackToAdd : contents) {
                if (stackToAdd.isEmpty()) { continue; }
                newContents.add(0, stackToAdd);
            }
            return new NbtBundleAdapter(newContents);
        }

        @Override
        public void attachTo(ItemStack itemStack) {
            writeContents(itemStack, this.stacks);
        }
    }
}
