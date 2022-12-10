package net.spell_engine.runes;

import net.spell_engine.SpellEngineMod;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.screen.ForgingScreenHandler;
import net.minecraft.screen.ScreenHandlerContext;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.slot.Slot;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;

// Mostly copied from SmithingScreenHandler
public class RuneCraftingScreenHandler extends ForgingScreenHandler {
    public static final ScreenHandlerType<RuneCraftingScreenHandler> HANDLER_TYPE = new ScreenHandlerType(RuneCraftingScreenHandler::new);
    private final World world;
    @Nullable
    private RuneCraftingRecipe currentRecipe;
    private final List<RuneCraftingRecipe> recipes;

    public RuneCraftingScreenHandler(int syncId, PlayerInventory playerInventory) {
        this(syncId, playerInventory, ScreenHandlerContext.EMPTY);
    }

    public RuneCraftingScreenHandler(int syncId, PlayerInventory playerInventory, ScreenHandlerContext context) {
        super(HANDLER_TYPE, syncId, playerInventory, context);
        this.world = playerInventory.player.world;
        this.recipes = this.world.getRecipeManager().listAllOfType(RuneCraftingRecipe.TYPE);
    }

    public RuneCraftingScreenHandler(int syncId, PlayerInventory playerInventory, PacketByteBuf packetByteBuf) {
        this(syncId, playerInventory, ScreenHandlerContext.EMPTY);
    }

    protected boolean canUse(BlockState state) {
        return state.isOf(RuneCraftingBlock.INSTANCE);
    }

    protected boolean canTakeOutput(PlayerEntity player, boolean present) {
        return this.currentRecipe != null && this.currentRecipe.matches(this.input, this.world);
    }

    protected void onTakeOutput(PlayerEntity player, ItemStack stack) {
        stack.onCraft(player.world, player, stack.getCount());
        this.output.unlockLastRecipe(player);
        this.decrementStack(0);
        this.decrementStack(1);

        var runeCrafter = (RuneCrafter)player;
        if (runeCrafter.getLastCrafted() < 1) {
            return;
        }
        runeCrafter.setLastCrafted(0);
        SoundEvent runeCraftingSound = new SoundEvent(new Identifier(SpellEngineMod.MOD_ID, "rune_crafting"));
        world.playSound(player.getX(), player.getY(), player.getZ(), runeCraftingSound, SoundCategory.BLOCKS, world.random.nextFloat() * 0.1F + 0.9F, 1, true);
    }

    private void decrementStack(int slot) {
        ItemStack itemStack = this.input.getStack(slot);
        itemStack.decrement(1);
        this.input.setStack(slot, itemStack);
    }

    public void updateResult() {
        List<RuneCraftingRecipe> list = this.world.getRecipeManager().getAllMatches(RuneCraftingRecipe.TYPE, this.input, this.world);
        if (list.isEmpty()) {
            this.output.setStack(0, ItemStack.EMPTY);
        } else {
            this.currentRecipe = (RuneCraftingRecipe)list.get(0);
            ItemStack itemStack = this.currentRecipe.craft(this.input);
            this.output.setLastRecipe(this.currentRecipe);
            this.output.setStack(0, itemStack);
        }
    }

    protected boolean isUsableAsAddition(ItemStack stack) {
        return this.recipes.stream().anyMatch((recipe) -> {
            return recipe.testAddition(stack);
        });
    }

    public boolean canInsertIntoSlot(ItemStack stack, Slot slot) {
        return slot.inventory != this.output && super.canInsertIntoSlot(stack, slot);
    }
}
