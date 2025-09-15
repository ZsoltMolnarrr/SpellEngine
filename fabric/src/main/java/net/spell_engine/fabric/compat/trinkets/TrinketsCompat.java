package net.spell_engine.fabric.compat.trinkets;

import dev.emi.trinkets.api.TrinketsApi;
import dev.emi.trinkets.api.event.TrinketEquipCallback;
import net.fabricmc.fabric.api.util.TriState;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.spell_engine.SpellEngineMod;
import net.spell_engine.api.item.trinket.ISpellBookItem;
import net.spell_engine.compat.container.ContainerCompat;
import net.spell_engine.api.spell.container.SpellContainerHelper;
import net.spell_engine.fx.SpellEngineSounds;
import net.spell_engine.internals.container.SpellContainerSource;
import net.spell_engine.item.SpellEngineItems;

import java.util.ArrayList;
import java.util.List;

public class TrinketsCompat {
    private static boolean intialized = false;
    private static boolean enabled = false;

    public static void init() {
        if (intialized) {
            return;
        }
        enabled = FabricLoader.getInstance().isModLoaded("trinkets");

        if (enabled) {
            TrinketsApi.registerTrinketPredicate(Identifier.of(SpellEngineMod.ID, "spell_book"), (itemStack, slotReference, livingEntity) -> {
                if (ISpellBookItem.isSpellBook(itemStack.getItem())) {
                    return TriState.TRUE;
                }
                return TriState.DEFAULT;
            });
            ContainerCompat.addProvider(TrinketsCompat::getAll);

            final var spellSourceName = "trinkets";
            SpellContainerSource.addItemSource(
                    SpellContainerSource.ItemEntry.of(
                            spellSourceName,
                            (player, name) -> getEquippedStacks(player)
                    ),
                    SpellContainerSource.MAIN_HAND.name()
            );
            TrinketEquipCallback.EVENT.register((stack, slot, entity) -> {
                if (entity instanceof PlayerEntity player) {
                    SpellContainerSource.setDirty(player, spellSourceName);
                }
            });
        }
        intialized = true;

        SpellEngineItems.setSpellScrollFactory(
                () -> new SpellScrollTrinketItem(new Item.Settings().maxCount(1), SpellEngineSounds.SPELLBOOK_EQUIP.soundEvent())
        );
        SpellEngineItems.setSpellBookFactory(
                (poolId) -> new SpellBookTrinketItem(new Item.Settings().maxCount(1), poolId, SpellEngineSounds.SPELLBOOK_EQUIP.soundEvent())
        );
    }

    private static List<ItemStack> getAll(PlayerEntity player) {
        var component = TrinketsApi.getTrinketComponent(player);
        if (component.isEmpty()) {
            return List.of();
        }
        var trinketComponent = component.get();
        return trinketComponent.getAllEquipped().stream().map(reference -> reference.getRight()).toList();
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static List<SpellContainerSource.SourcedContainer> getSpellContainers(PlayerEntity player, String sourceName) {
        var component = TrinketsApi.getTrinketComponent(player);
        if (component.isEmpty()) {
            return List.of();
        }
        var spellBooks = new ArrayList<SpellContainerSource.SourcedContainer>();
        var others = new ArrayList<SpellContainerSource.SourcedContainer>();
        var trinketComponent = component.get();
        trinketComponent.getAllEquipped().forEach(pair -> {
            var stack = pair.getRight();
            if (stack.isEmpty()) {
                return;
            }
            var container = SpellContainerHelper.containerFromItemStack(stack);
            if (container != null && container.isValid()) {
                if (pair.getLeft().getId().contains("spell/book")) {
                    spellBooks.add(new SpellContainerSource.SourcedContainer(sourceName, stack, container));
                } else {
                    others.add(new SpellContainerSource.SourcedContainer(sourceName, stack, container));
                }
            }
        });

        spellBooks.addAll(others);
        return spellBooks;
    }

    public static List<ItemStack> getEquippedStacks(PlayerEntity player) {
        var component = TrinketsApi.getTrinketComponent(player);
        if (component.isEmpty()) {
            return List.of();
        }
        var equipped = new ArrayList<ItemStack>();
        var trinketComponent = component.get();
        trinketComponent.getAllEquipped().forEach(pair -> {
            var stack = pair.getRight();
            if (stack.isEmpty()) {
                return;
            }
            if (pair.getLeft().getId().contains("spell/book")) {
                equipped.addFirst(stack);
            } else {
                equipped.add(stack);
            }
        });
        return equipped;
    }

    public static ItemStack getSpellBookStack(PlayerEntity player) {
        if (!enabled) {
            return ItemStack.EMPTY;
        }
        var component = TrinketsApi.getTrinketComponent(player);
        if (component.isEmpty()) {
            return ItemStack.EMPTY;
        }
        return component.get().getInventory().get("spell").get("book").getStack(0);
    }
}