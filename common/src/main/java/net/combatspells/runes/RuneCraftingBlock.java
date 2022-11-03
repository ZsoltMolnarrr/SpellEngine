package net.combatspells.runes;

import net.fabricmc.fabric.api.object.builder.v1.block.FabricBlockSettings;
import net.minecraft.block.BlockState;
import net.minecraft.block.CraftingTableBlock;
import net.minecraft.block.Material;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.ScreenHandlerContext;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class RuneCraftingBlock extends CraftingTableBlock {
    public static final String NAME = "rune_crafting";
    public static final RuneCraftingBlock INSTANCE = new RuneCraftingBlock(FabricBlockSettings.of(Material.REPAIR_STATION).hardness(2));
    private static final Text SCREEN_TITLE = Text.translatable("gui.combatspells.rune_crafting");

    public RuneCraftingBlock(Settings settings) {
        super(settings);
    }

    public NamedScreenHandlerFactory createScreenHandlerFactory(BlockState state, World world, BlockPos pos) {
        return new SimpleNamedScreenHandlerFactory((syncId, inventory, player) -> {
            return new RuneCraftingScreenHandler(syncId, inventory, ScreenHandlerContext.create(world, pos));
        }, SCREEN_TITLE);
    }

    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (world.isClient) {
            return ActionResult.SUCCESS;
        } else {
            player.openHandledScreen(state.createScreenHandlerFactory(world, pos));
            return ActionResult.CONSUME;
        }
    }
}
