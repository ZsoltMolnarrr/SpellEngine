package net.spell_engine.fabric.compat.trinkets;

import dev.emi.trinkets.api.SlotReference;
import dev.emi.trinkets.api.TrinketComponent;
import dev.emi.trinkets.api.TrinketsApi;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.spell_engine.compat.container.ContainerCompat;
import net.spell_engine.internals.container.SpellContainerSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/// Trinkets 3.7.2 (1.20.1) slot integration.
///
/// API differences vs Trinkets 3.10 (1.21) that this replaces:
/// - no `TrinketEquipCallback` event: spell-container dirtiness relies on the per-tick `DirtyChecker` that
///   `SpellContainerSource.ItemEntry.of(name, source)` installs (it re-reads the equipped stacks every tick
///   and compares instance-wise), exactly as the 1.20.1 legacy branch worked without an equip event;
/// - `SlotReference` has no `getId()`: the slot is identified through `inventory().getSlotType()`
///   (`getGroup()` / `getName()`), so the spell-book slot is `spell` / `book`;
/// - `TrinketComponent` extends Cardinal Components' `ComponentV3` (CCA 5.2.0 is nested in the Trinkets jar
///   and must be on the compile classpath — see fabric/build.gradle).
public class TrinketsCompat {
    private static final String MOD_ID = TrinketsCompatHeader.MOD_ID;
    /// Slot group / name of the spell book slot (see `resourcepacks/trinkets_compat/data/trinkets/slots/spell/book.json`).
    private static final String SPELL_BOOK_GROUP = "spell";
    private static final String SPELL_BOOK_SLOT = "book";
    private static boolean intialized = false;
    private static boolean enabled = false;

    public static boolean init() {
        if (intialized) {
            return enabled;
        }
        intialized = true;
        enabled = FabricLoader.getInstance().isModLoaded(MOD_ID);
        if (!enabled) {
            return enabled;
        }

        ContainerCompat.addProvider(TrinketsCompat::getAll);

        final var spellSourceName = "trinkets";
        SpellContainerSource.addItemSource(
                SpellContainerSource.ItemEntry.of(
                        spellSourceName,
                        (player, name) -> getEquippedStacks(player)
                ),
                SpellContainerSource.MAIN_HAND.name()
        );
        // Trinkets 3.7 has no TrinketEquipCallback; the ItemEntry's default DirtyChecker polls the
        // equipped stacks each tick, so equip/unequip is still picked up within a tick.

        TrinketsItemHelper.register();

        return enabled;
    }

    public static boolean isEnabled() {
        return enabled;
    }

    private static Optional<TrinketComponent> componentOf(PlayerEntity player) {
        return TrinketsApi.getTrinketComponent(player);
    }

    private static boolean isSpellBookSlot(SlotReference reference) {
        var slotType = reference.inventory().getSlotType();
        return SPELL_BOOK_GROUP.equals(slotType.getGroup()) && SPELL_BOOK_SLOT.equals(slotType.getName());
    }

    private static List<ItemStack> getAll(PlayerEntity player) {
        var component = componentOf(player);
        if (component.isEmpty()) {
            return List.of();
        }
        var stacks = new ArrayList<ItemStack>();
        for (var pair : component.get().getAllEquipped()) {
            stacks.add(pair.getRight());
        }
        return stacks;
    }

    public static List<ItemStack> getEquippedStacks(PlayerEntity player) {
        var component = componentOf(player);
        if (component.isEmpty()) {
            return List.of();
        }
        var equipped = new ArrayList<ItemStack>();
        for (var pair : component.get().getAllEquipped()) {
            var stack = pair.getRight();
            if (stack.isEmpty()) {
                continue;
            }
            // Prioritize the spell book slot
            if (isSpellBookSlot(pair.getLeft())) {
                equipped.add(0, stack);
            } else {
                equipped.add(stack);
            }
        }
        return equipped;
    }
}
