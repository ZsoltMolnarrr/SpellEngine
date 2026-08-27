package net.spell_engine.fabric.compat.trinkets;

import eu.pb4.trinkets.api.TrinketAttachment;
import eu.pb4.trinkets.api.TrinketsApi;
import eu.pb4.trinkets.api.event.TrinketEquipCallback;
import eu.pb4.trinkets.api.event.TrinketUnequipCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.spell_engine.SpellEngineMod;
import net.spell_engine.compat.container.ContainerCompat;
import net.spell_engine.internals.container.SpellContainerSource;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/// Trinkets Updated 4.0 (`eu.pb4.trinkets.api`) integration: exposes the worn trinkets as a spell
/// container source (`"trinkets"`) and to the generic container view, and marks the source dirty on
/// equip / unequip so the spell container cache is rebuilt.
public class TrinketsCompat {
    private static final String MOD_ID = TrinketsCompatHeader.MOD_ID;
    private static boolean intialized = false;
    private static boolean enabled = false;

    public static boolean init() {
        if (intialized) {
            return enabled;
        }
        intialized = true;
        var loader = FabricLoader.getInstance();
        enabled = loader.isModLoaded(MOD_ID) || loader.isModLoaded(TrinketsCompatHeader.MOD_ID_UPDATED);
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
        // Yumi events take a listener id. Unequip is a separate event now (old Trinkets fired
        // TrinketEquipCallback for any slot change); listen to both so removals invalidate too.
        var listenerId = Identifier.fromNamespaceAndPath(SpellEngineMod.ID, "spell_container_dirty");
        TrinketEquipCallback.EVENT.register(listenerId, (stack, slot, entity) -> {
            if (entity instanceof Player player) {
                SpellContainerSource.setDirty(player, spellSourceName);
            }
        });
        TrinketUnequipCallback.EVENT.register(listenerId, (stack, slot, entity) -> {
            if (entity instanceof Player player) {
                SpellContainerSource.setDirty(player, spellSourceName);
            }
        });

        TrinketsItemHelper.register();

        return enabled;
    }

    @Nullable
    private static TrinketAttachment attachment(Player player) {
        return TrinketsApi.getAttachment(player);
    }

    private static List<ItemStack> getAll(Player player) {
        var attachment = attachment(player);
        if (attachment == null) {
            return List.of();
        }
        return attachment.getAllEquipped().stream().map(pair -> pair.getB()).toList();
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static List<ItemStack> getEquippedStacks(Player player) {
        var attachment = attachment(player);
        if (attachment == null) {
            return List.of();
        }
        var equipped = new ArrayList<ItemStack>();
        attachment.getAllEquipped().forEach(pair -> {
            var stack = pair.getB();
            if (stack.isEmpty()) {
                return;
            }
            // Slot type id is `<group>/<slot>`, e.g. `spell/book`
            if (pair.getA().slotType().getId().contains("spell/book")) {
                equipped.addFirst(stack);
            } else {
                equipped.add(stack);
            }
        });
        return equipped;
    }
}
