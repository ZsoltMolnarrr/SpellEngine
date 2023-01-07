package net.spell_engine.spellbinding;

import net.minecraft.block.EnchantingTableBlock;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.*;
import net.minecraft.screen.slot.Slot;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.spell_engine.SpellEngineMod;
import net.spell_engine.internals.SpellContainerHelper;
import net.spell_engine.internals.SpellRegistry;

public class SpellBindingScreenHandler extends ScreenHandler {
    public static final ScreenHandlerType<SpellBindingScreenHandler> HANDLER_TYPE = new ScreenHandlerType(SpellBindingScreenHandler::new);
    public static final int MAXIMUM_SPELL_COUNT = 10;
    // State
    private final Inventory inventory = new SimpleInventory(2) {
        @Override
        public void markDirty() {
            super.markDirty();
            SpellBindingScreenHandler.this.onContentChanged(this);
        }
    };

    private final ScreenHandlerContext context;

    public final int[] spellId = new int[MAXIMUM_SPELL_COUNT];
    public final int[] spellCost = new int[MAXIMUM_SPELL_COUNT];
    public final int[] spellLevelRequirement = new int[MAXIMUM_SPELL_COUNT];

    public SpellBindingScreenHandler(int syncId, PlayerInventory playerInventory) {
        this(syncId, playerInventory, ScreenHandlerContext.EMPTY);
    }

    public SpellBindingScreenHandler(int syncId, PlayerInventory playerInventory, ScreenHandlerContext context) {
        super(HANDLER_TYPE, syncId);
        this.context = context;
        this.addSlot(new Slot(this.inventory, 0, 15, 47) {
            @Override
            public boolean canInsert(ItemStack stack) {
                return SpellContainerHelper.hasValidContainer(stack);
            }

            @Override
            public int getMaxItemCount() {
                return 1;
            }
        });
        this.addSlot(new Slot(this.inventory, 1, 35, 47) {
            @Override
            public boolean canInsert(ItemStack stack) {
                return stack.isOf(Items.LAPIS_LAZULI);
            }
        });

        for (int i = 0; i < 3; ++i) {
            for (int j = 0; j < 9; ++j) {
                this.addSlot(new Slot(playerInventory, j + i * 9 + 9, 8 + j * 18, 84 + i * 18));
            }
        }
        for (int i = 0; i < 9; ++i) {
            this.addSlot(new Slot(playerInventory, i, 8 + i * 18, 142));
        }

        for (int i = 0; i < MAXIMUM_SPELL_COUNT; ++i) {
            this.addProperty(Property.create(this.spellId, i));
            this.addProperty(Property.create(this.spellCost, i));
            this.addProperty(Property.create(this.spellLevelRequirement, i));
        }
    }

    public int getLapisCount() {
        ItemStack itemStack = this.inventory.getStack(1);
        if (itemStack.isEmpty()) {
            return 0;
        }
        return itemStack.getCount();
    }

    @Override
    public void onContentChanged(Inventory inventory) {
        if (inventory != this.inventory) {
            return;
        }
        ItemStack itemStack = inventory.getStack(0);
        if (itemStack.isEmpty() || !SpellContainerHelper.hasValidContainer(itemStack)) {
            for (int i = 0; i < MAXIMUM_SPELL_COUNT; ++i) {
                this.spellId[i] = 0;
                this.spellCost[i] = 0;
                this.spellLevelRequirement[i] = 0;
            }
        } else {
            this.context.run((world, pos) -> {
                int j;
                int libraryPower = 0;
                for (BlockPos blockPos : EnchantingTableBlock.BOOKSHELF_OFFSETS) {
                    if (!EnchantingTableBlock.canAccessBookshelf(world, pos, blockPos)) continue;
                    ++libraryPower;
                }
                var offers = SpellBinding.offersFor(itemStack, libraryPower);
                for (int i = 0; i < MAXIMUM_SPELL_COUNT; ++i) {
                    if (i < offers.size()) {
                        var offer = offers.get(i);
                        this.spellId[i] = offer.id();
                        this.spellCost[i] = offer.cost();
                        this.spellLevelRequirement[i] = offer.levelRequirement();
                    } else {
                        this.spellId[i] = 0;
                        this.spellCost[i] = 0;
                        this.spellLevelRequirement[i] = 0;
                    }
                }
                this.sendContentUpdates();
            });
        }
    }

    @Override
    public boolean canUse(PlayerEntity player) {
        return EnchantmentScreenHandler.canUse(this.context, player, SpellBindingBlock.INSTANCE);
    }

    @Override
    public void close(PlayerEntity player) {
        super.close(player);
        this.context.run((world, pos) -> this.dropInventory(player, this.inventory));
    }

    @Override
    public ItemStack transferSlot(PlayerEntity player, int index) {
        ItemStack itemStack = ItemStack.EMPTY;
        Slot slot = (Slot) this.slots.get(index);
        if (slot != null && slot.hasStack()) {
            ItemStack itemStack2 = slot.getStack();
            itemStack = itemStack2.copy();
            if (index == 0) {
                if (!this.insertItem(itemStack2, 2, 38, true)) {
                    return ItemStack.EMPTY;
                }
            } else if (index == 1) {
                if (!this.insertItem(itemStack2, 2, 38, true)) {
                    return ItemStack.EMPTY;
                }
            } else if (itemStack2.isOf(Items.LAPIS_LAZULI)) {
                if (!this.insertItem(itemStack2, 1, 2, true)) {
                    return ItemStack.EMPTY;
                }
            } else if (!((Slot) this.slots.get(0)).hasStack() && ((Slot) this.slots.get(0)).canInsert(itemStack2)) {
                ItemStack itemStack3 = itemStack2.copy();
                itemStack3.setCount(1);
                itemStack2.decrement(1);
                ((Slot) this.slots.get(0)).setStack(itemStack3);
            } else {
                return ItemStack.EMPTY;
            }
            if (itemStack2.isEmpty()) {
                slot.setStack(ItemStack.EMPTY);
            } else {
                slot.markDirty();
            }
            if (itemStack2.getCount() == itemStack.getCount()) {
                return ItemStack.EMPTY;
            }
            slot.onTakeItem(player, itemStack2);
        }
        return itemStack;
    }

    private static Identifier soundId = new Identifier(SpellEngineMod.ID, "bind_spell");
    @Override
    public boolean onButtonClick(PlayerEntity player, int id) {
        try {
            var rawId = spellId[id];
            var cost = spellCost[id];
            var requiredLevel = spellLevelRequirement[id];
            var lapisCount = getLapisCount();
            var weaponStack = getStacks().get(0);
            var lapisStack = getStacks().get(1);
            var spellIdOptional = SpellRegistry.fromRawId(rawId);
            if (spellIdOptional.isEmpty()) {
                return false;
            }
            var spellId = spellIdOptional.get();
            var binding = SpellBinding.State.of(spellId, weaponStack, cost, requiredLevel);
            if (binding.state == SpellBinding.State.ApplyState.INVALID) {
                return false;
            }
            if (!binding.readyToApply(player, lapisCount)) {
                return false;
            }
            this.context.run((world, pos) -> {
                SpellContainerHelper.addSpell(spellId, weaponStack);

                if (!player.isCreative()) {
                    lapisStack.decrement(binding.requirements.lapisCost());
                }
                applyLevelCost(player, binding.requirements.levelCost());
                this.inventory.markDirty();
                this.onContentChanged(this.inventory);
                var soundEvent = new SoundEvent(soundId);
                world.playSound(null, pos, soundEvent, SoundCategory.BLOCKS, 1.0f, world.random.nextFloat() * 0.1f + 0.9f);
            });

            return true;
        } catch (Exception e) {
            System.err.println(e.getMessage());
            e.printStackTrace();
        }
        return false;
    }

    private static void applyLevelCost(PlayerEntity player, int levelCost) {
        player.experienceLevel -= levelCost;
        if (player.experienceLevel < 0) {
            player.experienceLevel = 0;
            player.experienceProgress = 0.0f;
            player.totalExperience = 0;
        }
        if (player instanceof ServerPlayerEntity serverPlayer) {
            serverPlayer.setExperienceLevel(player.experienceLevel); // Triggers XP sync
        }
    }
}
