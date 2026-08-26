package net.spell_engine.mixin.item;

import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileWeaponItem;

@Mixin(ProjectileWeaponItem.class)
public interface RangedWeaponAccessor {
    @Invoker("shoot")
    public void shootAll_SpellEngine(
            ServerLevel world,
            LivingEntity shooter,
            InteractionHand hand,
            ItemStack stack,
            List<ItemStack> projectiles,
            float speed,
            float divergence,
            boolean critical,
            @Nullable LivingEntity target
    );

    @Invoker("draw")
    public static List<ItemStack> load_SpellEngine(ItemStack stack, ItemStack projectileStack, LivingEntity shooter) {
        throw new AssertionError();
    }
}
