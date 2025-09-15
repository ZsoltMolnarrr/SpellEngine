package net.spell_engine.fabric;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.spell_engine.Platform;
import net.spell_engine.fabric.compat.trinkets.TrinketsCompat;

public class PlatformImpl {
    public static Platform.Type getPlatformType() {
        return Platform.Type.FABRIC;
    }

    public static class FabricUtil implements Platform.Util {
        @Override
        public boolean isModLoaded(String modid) {
            return FabricLoader.getInstance().isModLoaded(modid);
        }

        @Override
        public void awakeSlotModCompat() {
            TrinketsCompat.init();
        }

        @Override
        public ItemStack getSpellBookSlot(PlayerEntity player) {
            return TrinketsCompat.getSpellBookStack(player);
        }
    }
    private static final Platform.Util UTIL = new FabricUtil();
    public static Platform.Util util() {
        return UTIL;
    }
}
