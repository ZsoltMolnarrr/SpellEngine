package net.spell_engine.utils;

import net.minecraft.inventory.Inventory;
import net.minecraft.screen.slot.Slot;

public class WrappedSlot extends Slot {
    public Slot wrapped;
    public WrappedSlot(Slot slot) {
        super(slot.inventory, slot.getIndex(), slot.x, slot.y);
        wrapped = slot;
    }
}
