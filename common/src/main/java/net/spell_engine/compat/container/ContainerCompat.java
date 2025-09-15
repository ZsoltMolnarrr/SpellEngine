package net.spell_engine.compat.container;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.BundleContentsComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public class ContainerCompat {
    public static final ArrayList< Function<PlayerEntity, List<ItemStack>> > providers = new ArrayList<>();
    public static void addProvider(Function<PlayerEntity, List<ItemStack>> provider) {
        providers.add(provider);
    }

    public static void init() {
        resolvers.add(itemStack -> {
            var bundle = itemStack.get(DataComponentTypes.BUNDLE_CONTENTS);
            if (bundle != null) {
                return new VanillaBundleAdapter(bundle);
            }
            return null;
        });
        if (FabricLoader.getInstance().isModLoaded("bundleapi")) {
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

    public record VanillaBundleAdapter(BundleContentsComponent component) implements Adapter {
        @Override
        public int size() {
            return component.size();
        }

        @Override
        public ItemStack get(int index) {
            return component.get(index);
        }

        @Override
        public Adapter createNewWithContents(List<ItemStack> contents) {
            var newBundle = new BundleContentsComponent.Builder(component).clear();
            for (var stackToAdd : contents) { // Reversed as putting items manually results reversed order
                newBundle.add(stackToAdd);
            }
            return new VanillaBundleAdapter(newBundle.build());
        }

        @Override
        public void attachTo(ItemStack itemStack) {
            itemStack.set(DataComponentTypes.BUNDLE_CONTENTS, this.component);
        }
    }
}
