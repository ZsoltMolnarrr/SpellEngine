package net.spell_engine.neoforge.compat.curios;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundEvent;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurio;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

public class SpellHostCurioItem extends Item implements ICurioItem {
    private final SoundEvent equipSound;

    public SpellHostCurioItem(Item.Settings settings, SoundEvent equipSound) {
        super(settings);
        this.equipSound = equipSound;
    }

    @Override
    public boolean canUnequip(SlotContext slotContext, ItemStack stack) {
        var isOnCooldown = false;
        if (slotContext.entity() instanceof PlayerEntity player) {
            isOnCooldown = !player.isCreative() && player.getItemCooldownManager().isCoolingDown(stack);
        }
        return ICurioItem.super.canUnequip(slotContext, stack) && !isOnCooldown;
    }

    @Override
    public ICurio.SoundInfo getEquipSound(SlotContext slotContext, ItemStack stack) {
        return new ICurio.SoundInfo(this.equipSound, 1.0F, 1.0F);
    }

    @Override
    public void onEquip(SlotContext slotContext, ItemStack prevStack, ItemStack stack) {
        var entity = slotContext.entity();
        if (entity == null) {
            return;
        }
        var world = entity.getEntityWorld();
        if (world.isClient()                        // the server broadcast below reaches every nearby client
                || entity.age <= 100                // gear already worn when entering a world/dimension
                || prevStack.isOf(stack.getItem())) // same item, only its data changed
        {
            return;
        }
        world.playSound(null, entity.getBlockPos(), this.equipSound, entity.getSoundCategory(), 1.0F, 1.0F);
    }

    @Override
    public void onEquipFromUse(SlotContext slotContext, ItemStack stack) {
        // Silent on purpose. `onEquip` above already fires for every equip path, this one included.
        // Curios' default would route through `ICurio`'s stateless instance, which resolves
        // `getEquipSound` on that instance rather than on this item, playing a generic sound.
    }
}
