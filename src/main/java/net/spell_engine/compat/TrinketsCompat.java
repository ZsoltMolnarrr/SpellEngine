package net.spell_engine.compat;

import dev.emi.trinkets.api.TrinketsApi;
import net.fabricmc.fabric.api.util.TriState;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.spell_engine.SpellEngineMod;
import net.spell_engine.api.item.trinket.SpellBookTrinketItem;
import net.spell_engine.api.spell.SpellContainer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

import static net.spell_engine.internals.SpellContainerHelper.containerFromItemStack;

public class TrinketsCompat {
    private static boolean enabled = false;

    public static void init() {
        enabled = FabricLoader.getInstance().isModLoaded("trinkets");

        if (enabled) {
            TrinketsApi.registerTrinketPredicate(new Identifier(SpellEngineMod.ID, "spell_book"), (itemStack, slotReference, livingEntity) -> {
                if (itemStack.getItem() instanceof SpellBookTrinketItem) {
                    return TriState.TRUE;
                }
                return TriState.DEFAULT;
            });
        }
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static List<String> getEquippedSpells(SpellContainer proxyContainer, PlayerEntity player) {
        if (!enabled) return Collections.emptyList();

        var component = TrinketsApi.getTrinketComponent(player);

        if (component.isEmpty() || proxyContainer == null || !proxyContainer.is_proxy) {
            return Collections.emptyList();
        }

        var trinketComponent = component.get();
        var allowedContent = proxyContainer.content;
        var items = new LinkedHashSet<ItemStack>();
        var spellBookSlot = trinketComponent.getInventory().get("charm").get("spell_book");

        // Add the spell book slot first
        items.add(spellBookSlot.getStack(0));

        // Add all other equipped items
        trinketComponent.getAllEquipped().forEach(pair -> items.add(pair.getRight()));

        // Extract spell IDs from the containers
        var collectedSpellIds = new LinkedHashSet<>(proxyContainer.spell_ids);
        for (ItemStack stack : items) {
            if (stack.isEmpty()) continue;

            var container = containerFromItemStack(stack);
            if (container != null && container.isValid() && container.content == allowedContent) {
                collectedSpellIds.addAll(container.spell_ids);
            }
        }

        return new ArrayList<>(collectedSpellIds);
    }
}