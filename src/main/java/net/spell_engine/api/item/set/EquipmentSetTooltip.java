package net.spell_engine.api.item.set;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.spell_engine.api.spell.SpellDataComponents;
import net.spell_engine.mixin.client.ItemStackTooltipAccessor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class EquipmentSetTooltip {
    public static void appendLines(ItemStack stack, List<Text> tooltip) {
        if (stack.get(SpellDataComponents.EQUIPMENT_SET) != null) {
            var text = textFor(stack, MinecraftClient.getInstance().player);
            if (!text.isEmpty()) {
                tooltip.addAll(text);
            }
        }
    }

    public static List<Text> textFor(ItemStack stack, @Nullable PlayerEntity player) {
        var text = new ArrayList<Text>();
        var component = stack.get(SpellDataComponents.EQUIPMENT_SET);
        if (component != null && player != null && player.getWorld() != null) {
            var registry = player.getWorld().getRegistryManager().get(EquipmentSetRegistry.KEY);
            var optionalSet = registry.getEntry(component.id());
            if (optionalSet.isPresent()) {
                var equipmentSetEntry = optionalSet.get();
                var equipmentSet = equipmentSetEntry.value();
                var setSize = equipmentSet.items().size();

                var activeSets = ((EquipmentSet.Owner) player).getActiveEquipmentSets();
                List<ItemStack> wornItems = List.of();
                for (var entry : activeSets) {
                    if (entry.set().getKey().get().equals(equipmentSetEntry.getKey().get())) {
                        wornItems = entry.items();
                    }
                }

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
                            Text.translatable(item.value().getTranslationKey())
                                    .formatted(isWorn ? Formatting.GRAY : Formatting.DARK_GRAY)
                    );
                }

                // Bonuses:
                // (2) Set : +5% Attack Damage
                // (4) Set : Reduces cooldown of Judgement by 3 seconds
                for (var bonus: equipmentSet.bonuses()) {
                    var isActive = wornItems.size() >= bonus.requiredPieceCount();
                    text.addAll(bonusText(player, bonus, isActive));
                }
            }
        }
        return text;
    }

    public static List<Text> bonusText(PlayerEntity player, EquipmentSet.Bonus bonus, boolean isActive) {
        var bonusTitle = Text.translatable("equipment_set.bonus.count", bonus.requiredPieceCount());
        var bonusLines = new ArrayList<Text>();
        if (bonus.attributes() != null) {
            var tooltipUtil = (ItemStackTooltipAccessor) (Object) ItemStack.EMPTY;
            for (var modifier: bonus.attributes().modifiers()) {
                tooltipUtil
                        .spellEngine_appendAttributeModifierTooltip(
                                bonusLines::add,
                                player,
                                modifier.attribute(),
                                modifier.modifier()
                        );
            }
        }
        if (bonus.spells() != null) {
            // TODO: Add spell container text
        }

        var finalLines = new ArrayList<Text>();
        Formatting formatting = isActive ? Formatting.GRAY : Formatting.DARK_GRAY;
        if (bonusLines.size() == 1) {
            finalLines.add(
                    bonusTitle.append(bonusLines.get(0))
                            .formatted(formatting)
            );
        } else {
            finalLines.add(bonusTitle);
            for (var line : bonusLines) {
                finalLines.add(
                        Text.literal(" ")
                                .append(line)
                                .formatted(formatting)
                );
            }
        }

        return finalLines;
    }
}
