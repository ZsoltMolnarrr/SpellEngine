package net.spell_engine.spellbinding;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.EnchantingTableBlock;
import net.spell_engine.SpellEngineMod;
import net.spell_engine.api.tags.SpellEngineItemTags;
import net.spell_engine.api.spell.registry.SpellRegistry;
import net.spell_engine.api.spell.container.SpellContainerHelper;
import net.spell_engine.fx.SpellEngineSounds;
import net.spell_engine.item.SpellEngineItems;
import net.spell_engine.item.UniversalSpellBookItem;

import java.util.Arrays;

public class SpellBindingScreenHandler extends AbstractContainerMenu {
    public static final MenuType<SpellBindingScreenHandler> HANDLER_TYPE = new MenuType(SpellBindingScreenHandler::new, FeatureFlags.VANILLA_SET);
    public static final int MAXIMUM_SPELL_COUNT = 32;
    public static final int INIT_SYNC_ID = 14999;
    private static final int SPELL_ID_RAW_NONE = -1;
    // State
    private final Container inventory = new SimpleContainer(2) {
        @Override
        public void setChanged() {
            super.setChanged();
            SpellBindingScreenHandler.this.slotsChanged(this);
        }
    };

    private final ContainerLevelAccess context;
    private boolean creative = false;

    // MARK: Synchronized data
    public final int[] mode = { SpellBinding.Mode.SPELL.ordinal() };
    public final int[] spellId = new int[MAXIMUM_SPELL_COUNT];
    public final int[] spellLevelCost = new int[MAXIMUM_SPELL_COUNT];
    public final int[] spellLevelRequirement = new int[MAXIMUM_SPELL_COUNT];
    public final int[] spellPoweredByLib = new int[MAXIMUM_SPELL_COUNT];
    public final int[] spellLapisCost = new int[MAXIMUM_SPELL_COUNT];

    public SpellBindingScreenHandler(int syncId, Inventory playerInventory) {
        this(syncId, playerInventory, ContainerLevelAccess.NULL);
    }

    public SpellBindingScreenHandler(int syncId, Inventory playerInventory, ContainerLevelAccess context) {
        super(HANDLER_TYPE, syncId);
        this.context = context;
        this.addSlot(new Slot(this.inventory, 0, 15, 47) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.getItem() == Items.BOOK || SpellContainerHelper.hasBindableContainer(stack);
            }

            @Override
            public int getMaxStackSize() {
                return 1;
            }
        });
        this.addSlot(new Slot(this.inventory, 1, 35, 47) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.is(Items.LAPIS_LAZULI) || stack.is(SpellEngineItemTags.SPELL_BOOK_MERGEABLE);
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
            this.addDataSlot(DataSlot.shared(this.spellId, i));
            this.addDataSlot(DataSlot.shared(this.spellLevelCost, i));
            this.addDataSlot(DataSlot.shared(this.spellLevelRequirement, i));
            this.addDataSlot(DataSlot.shared(this.spellPoweredByLib, i));
            this.addDataSlot(DataSlot.shared(this.spellLapisCost, i));
        }
        this.addDataSlot(DataSlot.shared(this.mode, 0));
        Arrays.fill(this.spellId, SPELL_ID_RAW_NONE);
        if (playerInventory.player instanceof ServerPlayer serverPlayer) {
            SpellBindingCriteria.INSTANCE.trigger(serverPlayer, SpellBinding.ADVANCEMENT_VISIT_ID, true);
        }
    }

    public int getLapisCount() {
        ItemStack itemStack = this.inventory.getItem(1);
        if (itemStack.isEmpty()) {
            return 0;
        }
        return itemStack.getCount();
    }

    public SpellBinding.Mode getMode() {
        return SpellBinding.Mode.values()[this.mode[0]];
    }

    @Override
    public void slotsChanged(Container inventory) {
        if (inventory != this.inventory) {
            return;
        }
        ItemStack mainStack = inventory.getItem(0);
        ItemStack consumableStack = inventory.getItem(1);
        if (mainStack.isEmpty() || !(SpellContainerHelper.hasValidContainer(mainStack) || mainStack.getItem() == Items.BOOK)) {
            this.mode[0] = SpellBinding.Mode.SPELL.ordinal();
            for (int i = 0; i < MAXIMUM_SPELL_COUNT; ++i) {
                this.spellId[i] = SPELL_ID_RAW_NONE;
                this.spellLevelCost[i] = 0;
                this.spellLevelRequirement[i] = 0;
                this.spellPoweredByLib[i] = 0;
                this.spellLapisCost[i] = 0;
            }
        } else {
            this.context.execute((world, pos) -> {
                int j;
                int libraryPower = 0;
                for (BlockPos blockPos : EnchantingTableBlock.BOOKSHELF_OFFSETS) {
                    if (!EnchantingTableBlock.isValidBookShelf(world, pos, blockPos)) continue;
                    ++libraryPower;
                }
                var offerResult = SpellBinding.offersFor(world, creative, mainStack, consumableStack, libraryPower);
                this.mode[0] = offerResult.mode().ordinal();
                var offers = offerResult.offers();
                for (int i = 0; i < MAXIMUM_SPELL_COUNT; ++i) {
                    if (i < offers.size()) {
                        var offer = offers.get(i);
                        this.spellId[i] = offer.id();
                        this.spellLevelCost[i] = offer.levelCost();
                        this.spellLevelRequirement[i] = offer.levelRequirement();
                        this.spellPoweredByLib[i] = offer.isPowered() ? 1 : 0;
                        this.spellLapisCost[i] = offer.lapisCost();
                    } else {
                        this.spellId[i] = SPELL_ID_RAW_NONE;
                        this.spellLevelCost[i] = 0;
                        this.spellLevelRequirement[i] = 0;
                        this.spellPoweredByLib[i] = 0;
                        this.spellLapisCost[i] = 0;
                    }
                }
                this.broadcastChanges();
            });
        }
    }

    @Override
    public boolean stillValid(Player player) {
        return EnchantmentMenu.stillValid(this.context, player, SpellBindingBlock.INSTANCE);
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        this.context.execute((world, pos) -> this.clearContainer(player, this.inventory));
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack itemStack = ItemStack.EMPTY;
        Slot slot = (Slot) this.slots.get(index);
        if (slot != null && slot.hasItem()) {
            ItemStack itemStack2 = slot.getItem();
            itemStack = itemStack2.copy();
            if (index == 0) {
                if (!this.moveItemStackTo(itemStack2, 2, 38, true)) {
                    return ItemStack.EMPTY;
                }
            } else if (index == 1) {
                if (!this.moveItemStackTo(itemStack2, 2, 38, true)) {
                    return ItemStack.EMPTY;
                }
            } else if (itemStack2.is(Items.LAPIS_LAZULI) || itemStack2.is(SpellEngineItemTags.SPELL_BOOK_MERGEABLE)) {
                if (!this.moveItemStackTo(itemStack2, 1, 2, true)) {
                    return ItemStack.EMPTY;
                }
            } else if (!((Slot) this.slots.get(0)).hasItem() && ((Slot) this.slots.get(0)).mayPlace(itemStack2)) {
                ItemStack itemStack3 = itemStack2.copy();
                itemStack3.setCount(1);
                itemStack2.shrink(1);
                ((Slot) this.slots.get(0)).setByPlayer(itemStack3);
            } else {
                return ItemStack.EMPTY;
            }
            if (itemStack2.isEmpty()) {
                slot.setByPlayer(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
            if (itemStack2.getCount() == itemStack.getCount()) {
                return ItemStack.EMPTY;
            }
            slot.onTake(player, itemStack2);
        }
        return itemStack;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        this.creative = player.isCreative();
        if (id == INIT_SYNC_ID) { return false; }
        try {
            var mode = SpellBinding.Mode.values()[this.mode[0]];
            var rawId = spellId[id];
            var levelCost = spellLevelCost[id];
            var requiredLevel = spellLevelRequirement[id];
            var poweredByLib = spellPoweredByLib[id];
            var lapisCost = spellLapisCost[id];
            var lapisCount = getLapisCount();
            var mainStack = getItems().get(0);
            var consumableStack = getItems().get(1);
            var playerWorld = player.level();

            switch (mode) {
                case SPELL -> {
                    var spellEntry = SpellRegistry.from(playerWorld).get(rawId);
                    if (spellEntry.isEmpty()) {
                        return false;
                    }
                    var spellId = spellEntry.get().unwrapKey().get().identifier();
                    var binding = SpellBinding.State.of(playerWorld, spellId, mainStack, levelCost, requiredLevel, lapisCost);

                    if (allowUnbinding() && binding.state == SpellBinding.State.ApplyState.ALREADY_APPLIED) {
                        this.context.execute((world, pos) -> {
                            SpellContainerHelper.removeSpell(world, spellId, mainStack);
                            this.inventory.setChanged();
                            this.slotsChanged(this.inventory);
                            world.playSound(null, pos, SpellEngineSounds.UNBIND_SPELL.soundEvent(), SoundSource.BLOCKS, 1.0f, world.random.nextFloat() * 0.1f + 0.9f);
                        });
                        return true;
                    }

                    if (poweredByLib == 0) {
                        return false;
                    }
                    if (binding.state == SpellBinding.State.ApplyState.INVALID) {
                        return false;
                    }
                    if (!binding.readyToApply(player, lapisCount)) {
                        return false;
                    }
                    this.context.execute((world, pos) -> {
                        SpellContainerHelper.addSpell(world, spellId, mainStack);

                        if (consumableStack.is(SpellEngineItemTags.SPELL_BOOK_MERGEABLE)) {
                            consumableStack.shrink(1);
                        } else {
                            if (!player.isCreative()) {
                                consumableStack.shrink(binding.requirements.lapisCost());
                            }
                        }
                        applyLevelCost(player, binding.requirements.levelCost());
                        this.inventory.setChanged();
                        this.slotsChanged(this.inventory);
                        world.playSound(null, pos, SpellEngineSounds.BIND_SPELL.soundEvent(), SoundSource.BLOCKS, 1.0f, world.random.nextFloat() * 0.1f + 0.9f);
                        if (player instanceof ServerPlayer serverPlayer) {
                            var container = SpellContainerHelper.containerFromItemStack(mainStack);
                            var poolId = SpellContainerHelper.getPoolId(container);
                            if (poolId != null) {
                                var pool = SpellRegistry.entries(world, container.pool());
                                var isComplete = container.spell_ids().size() == SpellContainerHelper.poolTierSize(pool);
                                SpellBindingCriteria.INSTANCE.trigger(serverPlayer, poolId, isComplete);
                                // System.out.println("Triggering advancement SpellBindingCriteria.INSTANCE spell_pool: " + poolId + " isComplete: " + isComplete);
                            } else {
                                SpellBindingCriteria.INSTANCE.trigger(serverPlayer, null, false);
                            }
                        }
                    });
                }
                case BOOK -> {
                    if (poweredByLib == 0) {
                        return false;
                    }
                    var tags = SpellBinding.availableSpellBookTags(player.level());
                    var tagIndex = rawId - SpellBinding.BOOK_OFFSET;
                    if (tagIndex < 0 || tagIndex >= tags.size()) {
                        return false;
                    }
                    var tag = tags.get(tagIndex);

                    // Create UniversalSpellBookItem stack
                    var itemStack = new ItemStack(SpellEngineItems.SPELL_BOOK.get());
                    if (!UniversalSpellBookItem.applyFromTag(itemStack, tag)) {
                        return false;
                    }

                    var container = SpellContainerHelper.containerFromItemStack(itemStack);
                    if (container == null || !container.isValid() || container.pool() == null) {
                        return false;
                    }
                    var poolId = Identifier.parse(container.pool());
                    var binding = SpellBinding.State.forBook(levelCost, requiredLevel);
                    if (binding.state == SpellBinding.State.ApplyState.INVALID) {
                        return false;
                    }
                    if (!binding.readyToApply(player, lapisCount)) {
                        return false;
                    }

                    this.context.execute((world, pos) -> {
                        this.slots.get(0).setByPlayer(itemStack);
                        if (!player.isCreative()) {
                            consumableStack.shrink(binding.requirements.lapisCost());
                        }
                        applyLevelCost(player, binding.requirements.levelCost());
                        this.inventory.setChanged();
                        this.slotsChanged(this.inventory);
                        world.playSound(null, pos, SpellEngineSounds.BIND_SPELL.soundEvent(), SoundSource.BLOCKS, 1.0f, world.random.nextFloat() * 0.1f + 0.9f);

                        if (player instanceof ServerPlayer serverPlayer) {
                            SpellBookCreationCriteria.INSTANCE.trigger(serverPlayer, poolId);
                        }
                    });
                }
            }
            return true;
        } catch (Exception e) {
            System.err.println(e.getMessage());
            e.printStackTrace();
        }
        return false;
    }

    private static void applyLevelCost(Player player, int levelCost) {
        player.experienceLevel -= levelCost;
        if (player.experienceLevel < 0) {
            player.experienceLevel = 0;
            player.experienceProgress = 0.0f;
            player.totalExperience = 0;
        }
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.setExperienceLevels(player.experienceLevel); // Triggers XP sync
        }
    }

    public boolean allowUnbinding() {
        return SpellEngineMod.config.spell_binding_allow_unbinding;
    }
}
