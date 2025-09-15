package net.spell_engine.compat.container;

import com.github.theredbrain.bundleapi.BundleAPI;
import com.github.theredbrain.bundleapi.component.type.CustomBundleContentsComponent;
import net.minecraft.item.ItemStack;

import java.util.List;

public class CustomBundleCompat {
    public static void init() {
        ContainerCompat.resolvers.add(itemStack -> {
            var bundle = itemStack.get(BundleAPI.CUSTOM_BUNDLE_CONTENTS_COMPONENT);
            if (bundle != null) {
                return new CustomBundleAdapter(bundle);
            }
            return null;
        });
    }

    public record CustomBundleAdapter(CustomBundleContentsComponent component) implements ContainerCompat.Adapter {
        @Override
        public int size() {
            return component.size();
        }

        @Override
        public ItemStack get(int index) {
            return component.get(index);
        }

        @Override
        public ContainerCompat.Adapter createNewWithContents(List<ItemStack> contents) {
            var newBundle = new CustomBundleContentsComponent.Builder(component).clear();
            for (var stackToAdd : contents) { // Reversed as putting items manually results reversed order
                newBundle.add(stackToAdd);
            }
            return new CustomBundleAdapter(newBundle.build());
        }

        @Override
        public void attachTo(ItemStack itemStack) {
            itemStack.set(BundleAPI.CUSTOM_BUNDLE_CONTENTS_COMPONENT, this.component);
        }
    }
}
