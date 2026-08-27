package net.spell_engine.fabric.compat.trinkets;

import eu.pb4.trinkets.api.TrinketSlotAccess;
import eu.pb4.trinkets.api.callback.TrinketCallback;
import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/// Base item for spell books / scrolls worn in a Trinkets slot.
///
/// Trinkets Updated 4.0 has no `TrinketItem` base class any more: per-item behaviour is a
/// {@link TrinketCallback}, which Trinkets resolves via `item instanceof TrinketCallback`
/// (`TrinketCallback.getCallback`). Slot compatibility is data-driven — the built-in
/// `trinkets_compat` pack tags the items into `trinkets:spell/book` etc., which the default
/// slot validator (`trinkets:default`) accepts — so no `TrinketEquippable` component is needed.
public class SpellHostTrinketItem extends Item implements TrinketCallback {
    private final SoundEvent equipSound;

    public SpellHostTrinketItem(Properties settings, SoundEvent equipSound) {
        super(settings);
        this.equipSound = equipSound;
    }

    @Override
    public boolean canUnequip(ItemStack stack, TrinketSlotAccess slot, LivingEntity entity) {
        var isOnCooldown = false;
        if (entity instanceof Player player) {
            isOnCooldown = !player.isCreative() && player.getCooldowns().isOnCooldown(stack);
        }
        return TrinketCallback.super.canUnequip(stack, slot, entity) && !isOnCooldown;
    }

    /// Right-click equips into the first free matching slot — the old `TrinketItem#use` behaviour
    /// (Trinkets Updated routes `Item#use` through this when it returns `true`).
    @Override
    public boolean canEquipFromUse(ItemStack stack, LivingEntity entity) {
        return true;
    }

    /// No Trinkets-side equip sound (old Trinkets returned `null` for non-`Equipment` items);
    /// the sound is played client-side from {@link #onEquip} so GUI equips are covered too.
    @Override
    public @Nullable Holder<SoundEvent> getEquipSound(ItemStack stack, TrinketSlotAccess slot, LivingEntity entity) {
        return null;
    }

    @Override
    public void onEquip(ItemStack stack, TrinketSlotAccess slot, LivingEntity entity) {
        TrinketCallback.super.onEquip(stack, slot, entity);

        if (entity.level().isClientSide() // Play sound only on client
                && entity.tickCount > 100      // Avoid playing sound on entering world / dimension
        ) {
            entity.playSound(this.equipSound, 1.0F, 1.0F);
        }
    }
}
