package net.spell_engine.utils;

import net.minecraft.world.inventory.Slot;

public class WrappedSlot extends Slot {
    public Slot wrapped;
    public WrappedSlot(Slot slot) {
        super(slot.container, slot.getContainerSlot(), slot.x, slot.y);
        wrapped = slot;
    }
}
