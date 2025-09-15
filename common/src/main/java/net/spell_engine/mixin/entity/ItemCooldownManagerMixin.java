package net.spell_engine.mixin.entity;

import com.google.common.collect.Maps;
import net.minecraft.entity.player.ItemCooldownManager;
import net.minecraft.item.Item;
import net.spell_engine.utils.ItemCooldownManagerExtension;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

@Mixin(ItemCooldownManager.class)
public class ItemCooldownManagerMixin implements ItemCooldownManagerExtension {

    /**
     * The goal of this mixin is to provide a way to get the last cooldown duration of an item.
     * AccessWidener is tedious to use, hence we just make a copy of the durations set.
     */

    public int SE_getLastCooldownDuration(Item item) {
        return durations.getOrDefault(item, 0);
    }

    @Unique
    private final Map<Item, Integer> durations = Maps.newHashMap();

    @Inject(method = "set", at = @At("HEAD"))
    private void set_HEAD(Item item, int duration, CallbackInfo ci) {
        durations.put(item, duration);
    }

    @Inject(method = "remove", at = @At("RETURN"))
    private void remove_RETURN(Item item, CallbackInfo ci) {
        durations.remove(item);
    }

}
