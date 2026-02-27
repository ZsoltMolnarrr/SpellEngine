package net.spell_engine.spellbinding.spellchoice;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.resource.featuretoggle.FeatureFlags;
import net.minecraft.screen.*;
import net.minecraft.screen.slot.Slot;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.Identifier;
import net.spell_engine.api.spell.SpellDataComponents;
import net.spell_engine.api.spell.container.SpellChoice;
import net.spell_engine.api.spell.container.SpellContainerHelper;
import net.spell_engine.api.spell.registry.SpellRegistry;
import net.spell_engine.fx.SpellEngineSounds;

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
        var spellChoice = SpellChoices.from(itemStack);
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
        try {
            // Get synced spell ID
            var rawId = spellId[id];
            if (rawId < 0) {
                return false;  // Invalid spell ID
            }

            // Get itemstack from slot
            var itemStack = getChoiceItemStack();
            if (itemStack.isEmpty()) {
                return false;  // No item to bind to
            }

            // Perform spell binding in world context
            this.context.run((world, pos) -> {
                // Get spell entry from registry
                var spellEntry = SpellRegistry.from(world).getEntry(rawId);
                if (spellEntry.isEmpty()) {
                    return;  // Invalid spell entry
                }

                // Extract spell identifier
                var selectedSpellId = spellEntry.get().getKey().get().getValue();

                // Bind spell to the item's spell container
                SpellContainerHelper.addSpell(world, selectedSpellId, itemStack);

                // Remove the spell choice component
                itemStack.set(SpellDataComponents.SPELL_CHOICE, SpellChoice.EMPTY);

                // Mark inventory dirty to trigger updates
                this.input.markDirty();
                this.onContentChanged(this.input);

                // Play sound feedback
                world.playSound(null, pos,
                    SpellEngineSounds.BIND_SPELL.soundEvent(),
                    SoundCategory.PLAYERS,
                    1.0f,
                    world.random.nextFloat() * 0.1f + 0.9f);
            });

            return true;  // Success
        } catch (Exception e) {
            System.err.println("Error in SpellChoiceScreenHandler.onButtonClick: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public void onContentChanged(Inventory inventory) {
        super.onContentChanged(inventory);
        updateSpellOffers();
    }
}
