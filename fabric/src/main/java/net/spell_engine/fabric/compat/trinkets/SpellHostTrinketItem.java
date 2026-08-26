package net.spell_engine.fabric.compat.trinkets;

import dev.emi.trinkets.api.SlotReference;
import dev.emi.trinkets.api.TrinketItem;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public class SpellHostTrinketItem extends TrinketItem {
    private final SoundEvent equipSound;

    public SpellHostTrinketItem(Properties settings, SoundEvent equipSound) {
        super(settings);
        this.equipSound = equipSound;
    }

    @Override
    public boolean canUnequip(ItemStack stack, SlotReference slot, LivingEntity entity) {
        var isOnCooldown = false;
        if (entity instanceof Player player) {
            isOnCooldown = !player.isCreative() && player.getCooldowns().isOnCooldown(stack);
        }
        return super.canUnequip(stack, slot, entity) && !isOnCooldown;
    }

    @Override
    public void onEquip(ItemStack stack, SlotReference slot, LivingEntity entity) {
        super.onEquip(stack, slot, entity);

        if (entity.level().isClientSide() // Play sound only on client
                && entity.tickCount > 100      // Avoid playing sound on entering world / dimension
        ) {
            entity.playSound(this.equipSound, 1.0F, 1.0F);
        }
    }
}
