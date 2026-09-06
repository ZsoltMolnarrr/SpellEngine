package net.spell_engine.forge.compat.curios;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.EventPriority;
import net.spell_engine.Platform;
import net.spell_engine.compat.container.ContainerCompat;
import net.spell_engine.internals.container.SpellContainerSource;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.event.CurioChangeEvent;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/// Curios 5.14.1+1.20.1 (Forge 47) slot integration.
///
/// API shape differences vs the Curios 9 (NeoForge) code this replaces:
/// - `CuriosApi.getCuriosInventory(entity)` returns a `LazyOptional<ICuriosItemHandler>` (capability), resolved
///   with `resolve()`; the handler/stacks API (`getCurios()`, `ICurioStacksHandler#getStacks()`) is identical.
/// - `ICurioItem` is still auto-detected on the item class (`CuriosEventHandler` attaches the capability to any
///   stack whose item implements it), so no `CuriosApi.registerCurio` call is needed.
/// - `CurioChangeEvent` is unchanged (game bus, `getEntity()` / `getIdentifier()` / `getFrom()` / `getTo()`).
/// - Slot data files keep the `data/<modid>/curios/slots/*.json` + `curios/entities/*.json` layout (Curios 5.2+);
///   the slot item tags live under `data/curios/tags/items/<slot>.json` (1.20.1 plural folder).
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

        // Explicit event class: Forge 47's plain addListener(Consumer) infers the event type from the lambda
        // via TypeTools, which is fragile; the 4-arg overload takes it directly.
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, CurioChangeEvent.class, event -> {
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

    private static Optional<ICuriosItemHandler> inventoryOf(PlayerEntity player) {
        return CuriosApi.getCuriosInventory(player).resolve();
    }

    private static List<ItemStack> getAll(PlayerEntity player) {
        var inventory = inventoryOf(player);
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
        var inventory = inventoryOf(player);
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
                    equipped.add(0, stack);
                } else {
                    equipped.add(stack);
                }
            }
        });

        return equipped;
    }
}
