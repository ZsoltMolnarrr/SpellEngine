package net.spell_engine.compat.accessories;

import io.wispforest.accessories.api.AccessoryItem;
import io.wispforest.accessories.api.SoundEventData;
import io.wispforest.accessories.api.slot.SlotReference;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.sound.SoundEvent;

import java.util.function.Supplier;

public class SpellHostAccessoryItem extends AccessoryItem {
    private final Supplier<RegistryEntry<SoundEvent>> equipSound;

    public SpellHostAccessoryItem(Item.Settings settings, Supplier<RegistryEntry<SoundEvent>> equipSound) {
        super(settings);
        this.equipSound = equipSound;
    }

    @Override
    public boolean isEnchantable(ItemStack stack) {
        return false;
    }

    @Override
    public boolean canUnequip(ItemStack stack, SlotReference reference) {
        var isOnCooldown = false;
        var entity = reference.entity();
        if (entity instanceof PlayerEntity player) {
            isOnCooldown = !player.isCreative() && player.getItemCooldownManager().isCoolingDown(stack.getItem());
        }
        return super.canUnequip(stack, reference) && !isOnCooldown;
    }

    public SoundEventData getEquipSound(ItemStack stack, SlotReference reference) {
        var entry = this.equipSound.get();
        if (entry != null) {
            return new SoundEventData(entry, 1.0F, 1.0F);
        } else {
            return null;
        }
    }
}
