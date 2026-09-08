package net.spell_engine.spellbinding;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.LightType;
import net.minecraft.world.World;

// Copied from EnchantingTableBlockEntity
public class SpellBindingBlockEntity extends BlockEntity {
    // Vanilla BlockEntityType.Builder (loader-neutral); build(null) skips the datafixer type, as FabricBlockEntityTypeBuilder.build() did.
    public static BlockEntityType<SpellBindingBlockEntity> ENTITY_TYPE = BlockEntityType.Builder.create(SpellBindingBlockEntity::new, SpellBindingBlock.INSTANCE).build(null);

    public int ticks;
    public float nextPageAngle;
    public float pageAngle;
    public float flipRandom;
    public float flipTurn;
    public float nextPageTurningSpeed;
    public float pageTurningSpeed;
    public float bookRotation;
    public float lastBookRotation;
    public float targetBookRotation;
    private static final Random RANDOM = Random.create();

    /// Whether {@link #serverTick} has already had its one look at the block light here.
    private boolean blockLightChecked = false;

    public SpellBindingBlockEntity(BlockPos pos, BlockState state) {
        super(ENTITY_TYPE, pos, state);
    }

    /// Server side, and a one-shot: everything after the first tick returns on the first line.
    ///
    /// Block light is baked into the saved chunk. The light engine only revisits a position when a block
    /// change tells it to (`WorldChunk#setBlockState` compares the old and new luminance) — a block whose
    /// luminance changes in *code* invalidates nothing, so every table placed before the block emitted
    /// light keeps the dark light values its chunk was saved with, for as long as the world lives.
    /// Nudge the light engine once per table per load; it settles into a no-op after the first fix,
    /// because by then the stored light already matches.
    public static void serverTick(World world, BlockPos pos, BlockState state, SpellBindingBlockEntity blockEntity) {
        if (blockEntity.blockLightChecked) {
            return;
        }
        blockEntity.blockLightChecked = true;
        if (world.getLightLevel(LightType.BLOCK, pos) < state.getLuminance()) {
            world.getChunkManager().getLightingProvider().checkBlock(pos);
        }
    }

    public static void tick(World world, BlockPos pos, BlockState state, SpellBindingBlockEntity blockEntity) {
        float g;
        blockEntity.pageTurningSpeed = blockEntity.nextPageTurningSpeed;
        blockEntity.lastBookRotation = blockEntity.bookRotation;
        PlayerEntity playerEntity = world.getClosestPlayer((double)pos.getX() + 0.5, (double)pos.getY() + 0.5, (double)pos.getZ() + 0.5, 3.0, false);
        if (playerEntity != null) {
            double d = playerEntity.getX() - ((double)pos.getX() + 0.5);
            double e = playerEntity.getZ() - ((double)pos.getZ() + 0.5);
            blockEntity.targetBookRotation = (float) MathHelper.atan2(e, d);
            blockEntity.nextPageTurningSpeed += 0.1f;
            if (blockEntity.nextPageTurningSpeed < 0.5f || RANDOM.nextInt(40) == 0) {
                float f = blockEntity.flipRandom;
                do {
                    blockEntity.flipRandom += (float)(RANDOM.nextInt(4) - RANDOM.nextInt(4));
                } while (f == blockEntity.flipRandom);
            }
        } else {
            blockEntity.targetBookRotation += 0.02f;
            blockEntity.nextPageTurningSpeed -= 0.1f;
        }
        while (blockEntity.bookRotation >= (float)Math.PI) {
            blockEntity.bookRotation -= (float)Math.PI * 2;
        }
        while (blockEntity.bookRotation < (float)(-Math.PI)) {
            blockEntity.bookRotation += (float)Math.PI * 2;
        }
        while (blockEntity.targetBookRotation >= (float)Math.PI) {
            blockEntity.targetBookRotation -= (float)Math.PI * 2;
        }
        while (blockEntity.targetBookRotation < (float)(-Math.PI)) {
            blockEntity.targetBookRotation += (float)Math.PI * 2;
        }
        for (g = blockEntity.targetBookRotation - blockEntity.bookRotation; g >= (float)Math.PI; g -= (float)Math.PI * 2) {
        }
        while (g < (float)(-Math.PI)) {
            g += (float)Math.PI * 2;
        }
        blockEntity.bookRotation += g * 0.4f;
        blockEntity.nextPageTurningSpeed = MathHelper.clamp(blockEntity.nextPageTurningSpeed, 0.0f, 1.0f);
        ++blockEntity.ticks;
        blockEntity.pageAngle = blockEntity.nextPageAngle;
        float h = (blockEntity.flipRandom - blockEntity.nextPageAngle) * 0.4f;
        float i = 0.2f;
        h = MathHelper.clamp(h, -0.2f, 0.2f);
        blockEntity.flipTurn += (h - blockEntity.flipTurn) * 0.9f;
        blockEntity.nextPageAngle += blockEntity.flipTurn;
    }
}
