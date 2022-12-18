package net.spell_engine.spellbinding;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ScreenHandlerContext;
import net.minecraft.screen.ScreenHandlerType;

public class SpellBindingScreenHandler extends ScreenHandler {
    public static final ScreenHandlerType<SpellBindingScreenHandler> HANDLER_TYPE = new ScreenHandlerType(SpellBindingScreenHandler::new);

    // State
    private final ScreenHandlerContext context;

    public SpellBindingScreenHandler(int syncId, PlayerInventory playerInventory) {
        this(syncId, playerInventory, ScreenHandlerContext.EMPTY);
    }

    public SpellBindingScreenHandler(int syncId, PlayerInventory playerInventory, ScreenHandlerContext context) {
        super(HANDLER_TYPE, syncId);
        this.context = context;
    }

    @Override
    public ItemStack transferSlot(PlayerEntity player, int index) {
        return null;
    }

    @Override
    public boolean canUse(PlayerEntity player) {
        return true;
    }
}
