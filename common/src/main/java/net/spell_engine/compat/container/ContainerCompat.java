package net.spell_engine.compat.container;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BundleContents;
import net.spell_engine.Platform;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public class ContainerCompat {
    public static final ArrayList< Function<Player, List<ItemStack>> > providers = new ArrayList<>();
    public static void addProvider(Function<Player, List<ItemStack>> provider) {
        providers.add(provider);
    }

    public static void init() {
        resolvers.add(itemStack -> {
            var bundle = itemStack.get(DataComponents.BUNDLE_CONTENTS);
            if (bundle != null) {
                return new VanillaBundleAdapter(bundle);
            }
            return null;
        });
        if (Platform.util().isModLoaded("bundleapi")) {
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

    public record VanillaBundleAdapter(BundleContents component) implements Adapter {
        @Override
        public int size() {
            return component.size();
        }

        @Override
        public ItemStack get(int index) {
            return component.items().get(index).create();
        }

        @Override
        public Adapter createNewWithContents(List<ItemStack> contents) {
            var newBundle = new BundleContents.Mutable(component).clearItems();
            for (var stackToAdd : contents) { // Reversed as putting items manually results reversed order
                newBundle.tryInsert(stackToAdd);
            }
            return new VanillaBundleAdapter(newBundle.toImmutable());
        }

        @Override
        public void attachTo(ItemStack itemStack) {
            itemStack.set(DataComponents.BUNDLE_CONTENTS, this.component);
        }
    }
}
