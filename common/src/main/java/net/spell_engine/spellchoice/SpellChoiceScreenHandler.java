package net.spell_engine.spellchoice;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.resource.featuretoggle.FeatureFlags;
import net.minecraft.screen.Property;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ScreenHandlerContext;
import net.minecraft.screen.ScreenHandlerType;
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

    private final ScreenHandlerContext context;
    private ItemStack itemStack = ItemStack.EMPTY;

    // Constructor called from client
    public SpellChoiceScreenHandler(int syncId, PlayerInventory playerInventory) {
        this(syncId, ItemStack.EMPTY, playerInventory, ScreenHandlerContext.EMPTY);
    }

    // Full constructor
    public SpellChoiceScreenHandler(int syncId, ItemStack stack, PlayerInventory playerInventory, ScreenHandlerContext context) {
        super(HANDLER_TYPE, syncId);
        this.context = context;
        this.itemStack = stack;

        // Register properties for syncing
        for (int i = 0; i < MAXIMUM_SPELL_COUNT; ++i) {
            this.addProperty(Property.create(this.spellId, i));
        }

        // Initialize spell offers from mainhand item
        updateSpellOffers(playerInventory.player);
    }

    private void updateSpellOffers(PlayerEntity player) {
        // Clear all spell IDs first
        for (int i = 0; i < MAXIMUM_SPELL_COUNT; ++i) {
            this.spellId[i] = SPELL_ID_RAW_NONE;
        }

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
}
