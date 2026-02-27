package net.spell_engine.compat.accessories;

import io.wispforest.accessories.api.events.AccessoryChangeCallback;
import io.wispforest.accessories.api.AccessoriesCapability;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.spell_engine.api.spell.container.SpellContainerHelper;
import net.spell_engine.compat.container.ContainerCompat;
import net.spell_engine.internals.container.SpellContainerSource;

import java.util.ArrayList;
import java.util.List;

public class AccessoriesCompat {
    private static final String MOD_ID = AccessoriesCompatHeader.MOD_ID;
    private static final String SLOT_SPELL_BOOK = "spell_book";
    private static boolean intialized = false;
    private static boolean enabled = false;

    public static boolean init() {
        if (intialized) {
            return enabled;
        }
        intialized = true;
        // Seems like Fabric specific query works better on NeoForge :)
        // as pure neo query doesn't have data at early runtime
        enabled = FabricLoader.getInstance().isModLoaded(MOD_ID);
        if (!enabled) {
            return enabled;
        }

        ContainerCompat.addProvider(AccessoriesCompat::getAll);

        final var spellSourceName = "accessories";
        SpellContainerSource.addItemSource(
                SpellContainerSource.ItemEntry.of(
                        spellSourceName,
                        (player, name) -> getEquippedStacks(player)
                ),
                SpellContainerSource.MAIN_HAND.name()
        );

        AccessoryChangeCallback.EVENT.register((prevStack, currentStack, slotReference, stateChange) -> {
            if (slotReference.entity() instanceof PlayerEntity player) {
                SpellContainerSource.setDirty(player, spellSourceName);
            }
        });

        AccessoriesItemHelper.register();

        return enabled;
    }

    private static List<ItemStack> getAll(PlayerEntity player) {
        var capability = AccessoriesCapability.getOptionally(player);
        if (capability.isEmpty()) {
            return List.of();
        }

        var accessories = new ArrayList<ItemStack>();
        var accessoriesContainer = capability.get();

        accessoriesContainer.getContainers().forEach((slotType, container) -> {
            var accessoriesInv = container.getAccessories();
            for (int i = 0; i < accessoriesInv.size(); i++) {
                ItemStack stack = accessoriesInv.getStack(i);
                if (!stack.isEmpty()) {
                    accessories.add(stack);
                }
            }
        });

        return accessories;
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static List<SpellContainerSource.SourcedContainer> getSpellContainers(PlayerEntity player, String sourceName) {
        var capability = AccessoriesCapability.getOptionally(player);
        if (capability.isEmpty()) {
            return List.of();
        }

        var spellBooks = new ArrayList<SpellContainerSource.SourcedContainer>();
        var others = new ArrayList<SpellContainerSource.SourcedContainer>();
        var accessoriesContainer = capability.get();

        accessoriesContainer.getContainers().forEach((slotType, container) -> {
            var accessoriesInv = container.getAccessories();
            for (int i = 0; i < accessoriesInv.size(); i++) {
                ItemStack stack = accessoriesInv.getStack(i);
                if (stack.isEmpty()) {
                    continue;
                }

                var spellContainer = SpellContainerHelper.containerFromItemStack(stack);
                if (spellContainer != null && spellContainer.isValid()) {
                    // Prioritize spell book slots
                    if (slotType.contains(SLOT_SPELL_BOOK)) {
                        spellBooks.add(new SpellContainerSource.SourcedContainer(sourceName, stack, spellContainer));
                    } else {
                        others.add(new SpellContainerSource.SourcedContainer(sourceName, stack, spellContainer));
                    }
                }
            }
        });

        spellBooks.addAll(others);
        return spellBooks;
    }

    public static List<ItemStack> getEquippedStacks(PlayerEntity player) {
        var capability = AccessoriesCapability.getOptionally(player);
        if (capability.isEmpty()) {
            return List.of();
        }

        var equipped = new ArrayList<ItemStack>();
        var accessoriesContainer = capability.get();

        accessoriesContainer.getContainers().forEach((slotType, container) -> {
            var accessoriesInv = container.getAccessories();
            for (int i = 0; i < accessoriesInv.size(); i++) {
                ItemStack stack = accessoriesInv.getStack(i);
                if (stack.isEmpty()) {
                    continue;
                }

                // Prioritize spell book slots
                if (slotType.contains(SLOT_SPELL_BOOK)) {
                    equipped.addFirst(stack);
                } else {
                    equipped.add(stack);
                }
            }
        });

        return equipped;
    }

    public static ItemStack getSpellBookStack(PlayerEntity player) {
        if (!enabled) {
            return ItemStack.EMPTY;
        }

        var capability = AccessoriesCapability.getOptionally(player);
        if (capability.isEmpty()) {
            return ItemStack.EMPTY;
        }

        var container = capability.get().getContainers().get(SLOT_SPELL_BOOK);
        if (container != null) {
            var accessoriesInv = container.getAccessories();
            if (!accessoriesInv.isEmpty()) {
                return accessoriesInv.getStack(0);
            }
        }

        return ItemStack.EMPTY;
    }
}