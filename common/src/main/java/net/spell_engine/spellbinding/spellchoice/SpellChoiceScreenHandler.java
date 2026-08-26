package net.spell_engine.spellbinding.spellchoice;

import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.spell_engine.api.spell.SpellDataComponents;
import net.spell_engine.api.spell.container.SpellChoice;
import net.spell_engine.api.spell.container.SpellContainerHelper;
import net.spell_engine.api.spell.registry.SpellRegistry;
import net.spell_engine.fx.SpellEngineSounds;

public class SpellChoiceScreenHandler extends AbstractContainerMenu {
    public static final int MAXIMUM_SPELL_COUNT = 32;
    private static final int SPELL_ID_RAW_NONE = -1;

    public static final MenuType<SpellChoiceScreenHandler> HANDLER_TYPE =
        new MenuType<>(SpellChoiceScreenHandler::new, FeatureFlags.VANILLA_SET);

    // Synchronized spell IDs (raw registry IDs)
    public final int[] spellId = new int[MAXIMUM_SPELL_COUNT];
    protected final Container input;
    protected final Slot slot;
    private final ContainerLevelAccess context;

    // Constructor called from client
    public SpellChoiceScreenHandler(int syncId, Inventory playerInventory) {
        this(syncId, ItemStack.EMPTY, playerInventory, ContainerLevelAccess.NULL);
    }

    public static class ReadOnlySlot extends Slot {
        public ReadOnlySlot(Container inventory, int index, int x, int y) {
            super(inventory, index, x, y);
        }
        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }
        @Override
        public boolean mayPickup(Player player) {
            return false;
        }
        public boolean isHighlightable() {
            return false;
        }
    }

    // Full constructor
    public SpellChoiceScreenHandler(int syncId, ItemStack stack, Inventory playerInventory, ContainerLevelAccess context) {
        super(HANDLER_TYPE, syncId);
        this.context = context;
        this.input = new SimpleContainer(1) {
            @Override
            public void setChanged() {
                super.setChanged();
                SpellChoiceScreenHandler.this.slotsChanged(this);
            }
        };
        this.slot = this.addSlot(new ReadOnlySlot(this.input, 0, (176 - 16) / 2, (166 - 48) / 2));
        // Register properties for syncing
        for (int i = 0; i < MAXIMUM_SPELL_COUNT; ++i) {
            this.addDataSlot(DataSlot.shared(this.spellId, i));
        }
        slot.setByPlayer(stack);
        // Initialize spell offers from mainhand item
        updateSpellOffers();
    }

    public ItemStack getChoiceItemStack() {
        // return this.input.getStack(0);
        return this.slot.getItem();
    }

    private void updateSpellOffers() {
        // Clear all spell IDs first
        for (int i = 0; i < MAXIMUM_SPELL_COUNT; ++i) {
            this.spellId[i] = SPELL_ID_RAW_NONE;
        }

        var itemStack = this.getChoiceItemStack();

        if (itemStack.isEmpty() || itemStack.get(SpellDataComponents.SPELL_CHOICE) == null /* not contains(): Yarn/NeoForge name mismatch */) {
            return;
        }

        // Get spell choice data
        var spellChoice = SpellChoices.from(itemStack);
        if (spellChoice == null || spellChoice.pool() == null || spellChoice.pool().isEmpty()) {
            return;
        }

        // Resolve spells from pool on server
        this.context.execute((world, pos) -> {
            var poolId = Identifier.parse(spellChoice.pool());
            var spells = SpellRegistry.entries(world, poolId);
            var registry = SpellRegistry.from(world);

            for (int i = 0; i < Math.min(spells.size(), MAXIMUM_SPELL_COUNT); ++i) {
                var spellEntry = spells.get(i);
                this.spellId[i] = registry.getId(spellEntry.value());
            }

            this.broadcastChanges();
        });
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
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
            this.context.execute((world, pos) -> {
                // Get spell entry from registry
                var spellEntry = SpellRegistry.from(world).get(rawId);
                if (spellEntry.isEmpty()) {
                    return;  // Invalid spell entry
                }

                // Extract spell identifier
                var selectedSpellId = spellEntry.get().unwrapKey().get().identifier();

                // Bind spell to the item's spell container
                SpellContainerHelper.addSpell(world, selectedSpellId, itemStack);

                // Apply the chosen spell's component changes (appearance, name, ...) to the item.
                // Read before clearing the component below.
                var spellChoice = SpellChoices.from(itemStack);
                if (spellChoice != null) {
                    var changes = spellChoice.applyOnChoiceFor(selectedSpellId);
                    if (!changes.isEmpty()) {
                        itemStack.applyComponentsAndValidate(changes);
                    }
                }

                // Remove the spell choice component
                itemStack.set(SpellDataComponents.SPELL_CHOICE, SpellChoice.EMPTY);

                // Mark inventory dirty to trigger updates
                this.input.setChanged();
                this.slotsChanged(this.input);

                // Play sound feedback
                world.playSound(null, pos,
                    SpellEngineSounds.BIND_SPELL.soundEvent(),
                    SoundSource.PLAYERS,
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
    public void slotsChanged(Container inventory) {
        super.slotsChanged(inventory);
        updateSpellOffers();
    }
}
