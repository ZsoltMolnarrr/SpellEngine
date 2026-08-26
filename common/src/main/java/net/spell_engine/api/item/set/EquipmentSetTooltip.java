package net.spell_engine.api.item.set;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.spell_engine.api.spell.SpellDataComponents;
import net.spell_engine.client.gui.SpellTooltip;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class EquipmentSetTooltip {
    public static void appendLines(ItemStack stack, List<Component> tooltip) {
        if (stack.get(SpellDataComponents.EQUIPMENT_SET) != null) {
            var text = textFor(stack, Minecraft.getInstance().player);
            if (!text.isEmpty()) {
                tooltip.addAll(text);
            }
        }
    }

    public static List<Component> textFor(ItemStack stack, @Nullable Player player) {
        var text = new ArrayList<Component>();
        var component = stack.get(SpellDataComponents.EQUIPMENT_SET);
        if (component == null) {
            return text;
        }
        var optionalEntry = EquipmentSetRegistry.from(player.level()).get(component);
        if (optionalEntry.isPresent() && player != null && player.level() != null) {
            var equipmentSetEntry = optionalEntry.get();
            var equipmentSet = equipmentSetEntry.value();
            var setSize = equipmentSet.items().size();

            var activeSets = ((EquipmentSet.Owner) player).getActiveEquipmentSets();
            List<ItemStack> wornItems = List.of();
            for (var entry : activeSets) {
                if (entry.set().unwrapKey().get().equals(equipmentSetEntry.unwrapKey().get())) {
                    wornItems = entry.items();
                }
            }

            text.add(Component.literal(" "));

            // Title:
            // Justicar Raiment (2/4)
            text.add(
                    Component.translatable(EquipmentSet.translationKey(equipmentSetEntry))
                            .append(Component.literal(" (" + wornItems.size() + "/" + setSize + ")"))
                            .withStyle(ChatFormatting.GOLD)
            );

            // Items:
            //  Justicar Helm
            //  Justicar Chestplate
            //  Justicar Leggings
            //  Justicar Boots
            for (var item : equipmentSet.items()) {
                var isWorn = wornItems.stream().anyMatch(wornItem -> wornItem.is(item.value()));
                text.add(
                        Component.literal(" ").append(
                                Component.translatable(item.value().getDescriptionId())
                                        .withStyle(isWorn ? ChatFormatting.GRAY : ChatFormatting.DARK_GRAY)
                        )
                );
            }

            // Bonuses:
            // (2) Set : +5% Attack Damage
            // (4) Set : Reduces cooldown of Judgement by 3 seconds
            for (var bonus: equipmentSet.bonuses()) {
                var isActive = wornItems.size() >= bonus.requiredPieceCount();
                text.addAll(bonusText(player, stack, bonus, isActive));
            }
        }
        return text;
    }

    public static List<Component> bonusText(Player player, ItemStack itemStack, EquipmentSet.Bonus bonus, boolean isActive) {
        var bonusTitle = Component.translatable("equipment_set.logic.bonus.count", bonus.requiredPieceCount());
        var bonusLines = new ArrayList<Component>();
        if (bonus.attributes() != null) {
            // 1.21.5+: per-modifier tooltip lines are produced by AttributeModifiersComponent.Display (no ItemStack invoker needed)
            var display = ItemAttributeModifiers.Display.attributeModifiers();
            for (var modifier: bonus.attributes().modifiers()) {
                display.apply(bonusLines::add, player, modifier.attribute(), modifier.modifier());
            }
        }
        if (bonus.spells() != null) {
            var spellText = SpellTooltip.getSpellInfo(itemStack, bonus.spells(), player, true, false);
            if (spellText != null) {
                bonusLines.addAll(spellText.content());
            }
        }

        var finalLines = new ArrayList<Component>();
        ChatFormatting formatting = isActive ? ChatFormatting.GRAY : ChatFormatting.DARK_GRAY;
        if (bonusLines.size() == 1) {
            var line = bonusLines.get(0);
            finalLines.add(
                    bonusTitle.append(isActive ? line : Component.literal(line.getString()))
                            .withStyle(formatting)
            );
        } else {
            finalLines.add(bonusTitle.withStyle(formatting));
            for (var line : bonusLines) {
                finalLines.add(
                        Component.literal(" ")
                                .append(isActive ? line : Component.literal(line.getString()))
                                .withStyle(formatting)
                );
            }
        }

        return finalLines;
    }
}
