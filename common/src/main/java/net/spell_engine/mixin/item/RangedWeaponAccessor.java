package net.spell_engine.mixin.item;

import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.RangedWeaponItem;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Hand;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.List;

@Mixin(RangedWeaponItem.class)
public interface RangedWeaponAccessor {
    @Invoker("shootAll")
    public void shootAll_SpellEngine(
            ServerWorld world,
            LivingEntity shooter,
            Hand hand,
            ItemStack stack,
            List<ItemStack> projectiles,
            float speed,
            float divergence,
            boolean critical,
            @Nullable LivingEntity target
    );

    @Invoker("load")
    public static List<ItemStack> load_SpellEngine(ItemStack stack, ItemStack projectileStack, LivingEntity shooter) {
        throw new AssertionError();
    }
}
