package net.spell_engine.mixin.entity;

import com.google.common.collect.Maps;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemCooldowns;
import net.minecraft.world.item.ItemStack;
import net.spell_engine.utils.ItemCooldownManagerExtension;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

@Mixin(ItemCooldowns.class)
public abstract class ItemCooldownManagerMixin implements ItemCooldownManagerExtension {

    /**
     * Provides a way to get the last cooldown duration of an item (cooldown group).
     * AccessWidener is tedious to use, hence we just make a copy of the durations set.
     */

    @Shadow public abstract Identifier getCooldownGroup(ItemStack stack);

    @Unique
    private final Map<Identifier, Integer> durations = Maps.newHashMap();

    public int SE_getLastCooldownDuration(ItemStack stack) {
        return durations.getOrDefault(getCooldownGroup(stack), 0);
    }

    @Inject(method = "addCooldown(Lnet/minecraft/resources/Identifier;I)V", at = @At("HEAD"))
    private void set_HEAD(Identifier groupId, int duration, CallbackInfo ci) {
        durations.put(groupId, duration);
    }

    @Inject(method = "removeCooldown", at = @At("RETURN"))
    private void remove_RETURN(Identifier groupId, CallbackInfo ci) {
        durations.remove(groupId);
    }
}
