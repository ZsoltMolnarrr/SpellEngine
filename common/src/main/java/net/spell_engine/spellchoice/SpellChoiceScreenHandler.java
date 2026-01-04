package net.spell_engine.spellchoice;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.resource.featuretoggle.FeatureFlags;
import net.minecraft.screen.*;
import net.minecraft.screen.slot.Slot;
import net.minecraft.util.Identifier;
import net.spell_engine.api.spell.SpellDataComponents;
import net.spell_engine.api.spell.registry.SpellRegistry;

public class SpellChoiceScreenHandler extends ScreenHandler {
    public static final int MAXIMUM_SPELL_COUNT = 32;
    private static final int SPELL_ID_RAW_NONE = -1;

    public static final ScreenHandlerType<SpellChoiceScreenHandler> HANDLER_TYPE =
        new ScreenHandlerType<>(SpellChoiceScreenHandler::new, FeatureFlags.VANILLA_FEATURES);

    // Synchronized spell IDs (raw registry IDs)
    public final int[] spellId = new int[MAXIMUM_SPELL_COUNT];
    protected final Inventory input;
    protected final Slot slot;
    private final ScreenHandlerContext context;

    // Constructor called from client
    public SpellChoiceScreenHandler(int syncId, PlayerInventory playerInventory) {
        this(syncId, ItemStack.EMPTY, playerInventory, ScreenHandlerContext.EMPTY);
    }

    public static class ReadOnlySlot extends Slot {
        public ReadOnlySlot(Inventory inventory, int index, int x, int y) {
            super(inventory, index, x, y);
        }
        @Override
        public boolean canInsert(ItemStack stack) {
            return false;
        }
        @Override
        public boolean canTakeItems(PlayerEntity player) {
            return false;
        }
        public boolean canBeHighlighted() {
            return false;
        }
    }

    // Full constructor
    public SpellChoiceScreenHandler(int syncId, ItemStack stack, PlayerInventory playerInventory, ScreenHandlerContext context) {
        super(HANDLER_TYPE, syncId);
        this.context = context;
        this.input = new SimpleInventory(1) {
            @Override
            public void markDirty() {
                super.markDirty();
                SpellChoiceScreenHandler.this.onContentChanged(this);
            }
        };
        this.slot = this.addSlot(new ReadOnlySlot(this.input, 0, (176 - 16) / 2, (166 - 48) / 2));
        // Register properties for syncing
        for (int i = 0; i < MAXIMUM_SPELL_COUNT; ++i) {
            this.addProperty(Property.create(this.spellId, i));
        }
        slot.setStack(stack);
        // Initialize spell offers from mainhand item
        updateSpellOffers();
    }

    public ItemStack getChoiceItemStack() {
        // return this.input.getStack(0);
        return this.slot.getStack();
    }

    private void updateSpellOffers() {
        // Clear all spell IDs first
        for (int i = 0; i < MAXIMUM_SPELL_COUNT; ++i) {
            this.spellId[i] = SPELL_ID_RAW_NONE;
        }

        var itemStack = this.getChoiceItemStack();

        if (itemStack.isEmpty() || !itemStack.contains(SpellDataComponents.SPELL_CHOICE)) {
            return;
        }

        // Get spell choice data
        var spellChoice = itemStack.get(SpellDataComponents.SPELL_CHOICE);
        if (spellChoice == null || spellChoice.pool() == null || spellChoice.pool().isEmpty()) {
            return;
        }

        // Resolve spells from pool on server
        this.context.run((world, pos) -> {
            var poolId = Identifier.of(spellChoice.pool());
            var spells = SpellRegistry.entries(world, poolId);
            var registry = SpellRegistry.from(world);

            for (int i = 0; i < Math.min(spells.size(), MAXIMUM_SPELL_COUNT); ++i) {
                var spellEntry = spells.get(i);
                this.spellId[i] = registry.getRawId(spellEntry.value());
            }

            this.sendContentUpdates();
        });
    }

    @Override
    public boolean canUse(PlayerEntity player) {
        return true;
    }

    @Override
    public ItemStack quickMove(PlayerEntity player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean onButtonClick(PlayerEntity player, int id) {
        // TODO: Handle spell selection
        // For now, just return true to acknowledge the click
        return true;
    }

    @Override
    public void onContentChanged(Inventory inventory) {
        super.onContentChanged(inventory);
        updateSpellOffers();
    }
}
