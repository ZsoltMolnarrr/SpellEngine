package net.spell_engine.compat;

import net.fabricmc.loader.api.FabricLoader;
import net.mehvahdjukaar.supplementaries.common.items.QuiverItem;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;

public class QuiverCompat {

    private static boolean enabled = false;
    public static void init() {
        enabled = FabricLoader.getInstance().isModLoaded("supplementaries");
    }

    public static boolean hasArrow(Item item, PlayerEntity shooter) {
        if (enabled) {
            var quiver = QuiverItem.getQuiver(shooter);
            if (!quiver.isEmpty()) {
                var data = QuiverItem.getQuiverData(quiver);
                if (data != null) {
                    var selected = data.getSelected(itemStack -> itemStack.isOf(item));
                    if (!selected.isEmpty()) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public static boolean consumeArrow(Item item, PlayerEntity shooter) {
        if (enabled) {
            var quiver = QuiverItem.getQuiver(shooter);
            if (!quiver.isEmpty()) {
                var data = QuiverItem.getQuiverData(quiver);
                if (data != null) {
                    var selected = data.getSelected(itemStack -> itemStack.isOf(item));
                    if (selected.isEmpty()) {
                        return false;
                    }
                    data.consumeArrow();
                    return true;
                }
            }
        }
        return false;
    }
}
