package net.spell_engine.api.item.set;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.registry.RegistryKey;
import net.spell_engine.api.item.SpellItemData;
import net.spell_engine.client.gui.AttributeModifierTooltip;
import net.spell_engine.client.gui.SpellTooltip;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class EquipmentSetTooltip {
    public static void appendLines(ItemStack stack, List<Text> tooltip) {
        if (SpellItemData.hasEquipmentSet(stack)) {
            var text = textFor(stack, MinecraftClient.getInstance().player);
            if (!text.isEmpty()) {
                tooltip.addAll(text);
            }
        }
    }

    public static List<Text> textFor(ItemStack stack, @Nullable PlayerEntity player) {
        var text = new ArrayList<Text>();
        var setId = SpellItemData.getEquipmentSet(stack);
        if (setId == null || player == null || player.getWorld() == null) {
            return text;
        }
        var optionalEntry = EquipmentSetRegistry.from(player.getWorld()).getEntry(RegistryKey.of(EquipmentSetRegistry.KEY, setId));
        if (optionalEntry.isPresent()) {
            var equipmentSetEntry = optionalEntry.get();
            var equipmentSet = equipmentSetEntry.value();
            var setSize = equipmentSet.items().size();

            var activeSets = ((EquipmentSet.Owner) player).getActiveEquipmentSets();
            List<ItemStack> wornItems = List.of();
            for (var entry : activeSets) {
                if (entry.set().getKey().get().equals(equipmentSetEntry.getKey().get())) {
                    wornItems = entry.items();
                }
            }

            text.add(Text.literal(" "));

            // Title:
            // Justicar Raiment (2/4)
            text.add(
                    Text.translatable(EquipmentSet.translationKey(equipmentSetEntry))
                            .append(Text.literal(" (" + wornItems.size() + "/" + setSize + ")"))
                            .formatted(Formatting.GOLD)
            );

            // Items:
            //  Justicar Helm
            //  Justicar Chestplate
            //  Justicar Leggings
            //  Justicar Boots
            for (var item : equipmentSet.items()) {
                var isWorn = wornItems.stream().anyMatch(wornItem -> wornItem.isOf(item.value()));
                text.add(
                        Text.literal(" ").append(
                                Text.translatable(item.value().getTranslationKey())
                                        .formatted(isWorn ? Formatting.GRAY : Formatting.DARK_GRAY)
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

    public static List<Text> bonusText(PlayerEntity player, ItemStack itemStack, EquipmentSet.Bonus bonus, boolean isActive) {
        var bonusTitle = Text.translatable("equipment_set.logic.bonus.count", bonus.requiredPieceCount());
        var bonusLines = new ArrayList<Text>();
        if (bonus.attributes() != null) {
            for (var modifier: bonus.attributes().modifiers()) {
                AttributeModifierTooltip.append(bonusLines::add, player, modifier.attribute().value(), modifier.modifier());
            }
        }
        if (bonus.spells() != null) {
            var spellText = SpellTooltip.getSpellInfo(itemStack, bonus.spells(), player, true, false);
            if (spellText != null) {
                bonusLines.addAll(spellText.content());
            }
        }

        var finalLines = new ArrayList<Text>();
        Formatting formatting = isActive ? Formatting.GRAY : Formatting.DARK_GRAY;
        if (bonusLines.size() == 1) {
            var line = bonusLines.get(0);
            finalLines.add(
                    bonusTitle.append(isActive ? line : Text.literal(line.getString()))
                            .formatted(formatting)
            );
        } else {
            finalLines.add(bonusTitle.formatted(formatting));
            for (var line : bonusLines) {
                finalLines.add(
                        Text.literal(" ")
                                .append(isActive ? line : Text.literal(line.getString()))
                                .formatted(formatting)
                );
            }
        }

        return finalLines;
    }
}
