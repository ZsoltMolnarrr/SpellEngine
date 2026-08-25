package net.spell_engine.neoforge.compat.curios;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.neoforged.neoforge.common.NeoForge;
import net.spell_engine.Platform;
import net.spell_engine.compat.container.ContainerCompat;
import net.spell_engine.internals.container.SpellContainerSource;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.event.CurioChangeEvent;

import java.util.ArrayList;
import java.util.List;

public class CuriosCompat {
    private static final String MOD_ID = CuriosCompatHeader.MOD_ID;
    private static final String SLOT_SPELL_BOOK = "spell_book";
    private static boolean initialized = false;
    private static boolean enabled = false;

    public static boolean init() {
        if (initialized) {
            return enabled;
        }
        initialized = true;
        enabled = Platform.util().isModLoaded(MOD_ID);
        if (!enabled) {
            return enabled;
        }

        ContainerCompat.addProvider(CuriosCompat::getAll);

        final var spellSourceName = "curios";
        SpellContainerSource.addItemSource(
                SpellContainerSource.ItemEntry.of(
                        spellSourceName,
                        (player, name) -> getEquippedStacks(player)
                ),
                SpellContainerSource.MAIN_HAND.name()
        );

        // Curios 14: CurioChangeEvent is abstract; listen to both concrete variants (item swapped / item state changed)
        NeoForge.EVENT_BUS.addListener((CurioChangeEvent.Item event) -> {
            if (event.getEntity() instanceof PlayerEntity player) {
                SpellContainerSource.setDirty(player, spellSourceName);
            }
        });
        NeoForge.EVENT_BUS.addListener((CurioChangeEvent.State event) -> {
            if (event.getEntity() instanceof PlayerEntity player) {
                SpellContainerSource.setDirty(player, spellSourceName);
            }
        });

        CuriosItemHelper.register();

        return enabled;
    }

    public static boolean isEnabled() {
        return enabled;
    }

    private static List<ItemStack> getAll(PlayerEntity player) {
        var inventory = CuriosApi.getCuriosInventory(player);
        if (inventory.isEmpty()) {
            return List.of();
        }

        var stacks = new ArrayList<ItemStack>();
        inventory.get().getCurios().forEach((slotType, stacksHandler) -> {
            var slotStacks = stacksHandler.getStacks();
            for (int i = 0; i < slotStacks.getSlots(); i++) {
                ItemStack stack = slotStacks.getStackInSlot(i);
                if (!stack.isEmpty()) {
                    stacks.add(stack);
                }
            }
        });

        return stacks;
    }

    public static List<ItemStack> getEquippedStacks(PlayerEntity player) {
        var inventory = CuriosApi.getCuriosInventory(player);
        if (inventory.isEmpty()) {
            return List.of();
        }

        var equipped = new ArrayList<ItemStack>();
        inventory.get().getCurios().forEach((slotType, stacksHandler) -> {
            var slotStacks = stacksHandler.getStacks();
            for (int i = 0; i < slotStacks.getSlots(); i++) {
                ItemStack stack = slotStacks.getStackInSlot(i);
                if (stack.isEmpty()) {
                    continue;
                }

                // Prioritize spell book slots
                if (slotType.equals(SLOT_SPELL_BOOK)) {
                    equipped.addFirst(stack);
                } else {
                    equipped.add(stack);
                }
            }
        });

        return equipped;
    }
}
