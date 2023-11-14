package net.spell_engine.compat;

import net.fabricmc.loader.api.FabricLoader;
import net.mehvahdjukaar.supplementaries.common.items.QuiverItem;
import net.minecraft.entity.player.PlayerEntity;

public class QuiverCompat {
    public static boolean hasArrow(PlayerEntity player) {
        if (FabricLoader.getInstance().isModLoaded("supplementaries")) {
            // var quiver = QuiverItem.getQuiver((Object)player);
        }
    }
}
