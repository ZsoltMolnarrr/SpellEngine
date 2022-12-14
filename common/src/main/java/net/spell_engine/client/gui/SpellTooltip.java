package net.spell_engine.client.gui;

import com.ibm.icu.text.DecimalFormat;
import net.spell_engine.SpellEngineMod;
import net.spell_engine.internals.SpellCasterItemStack;
import net.spell_engine.internals.SpellHelper;
import net.spell_engine.internals.SpellRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;

import java.util.List;

public class SpellTooltip {
    private static final String damageToken = "{damage}";
    private static final String healToken = "{heal}";

    public static void addSpellInfo(ItemStack itemStack, List<Text> lines) {
        var player = MinecraftClient.getInstance().player;
        if (player == null) {
            return;
        }
        if ((Object)itemStack instanceof SpellCasterItemStack stack) {
            // var spellId = stack.getSpellId();
//            var spell = SpellRegistry.getSpell(spellId);
//            if(stack.getSpellContainer() != null) {
//                lines.add(Text.of(""));
//
//                lines.add(Text.translatable("spell.tooltip.when_used")
//                        .append(Text.literal(" "))
//                        .append(Text.translatable(spellKeyPrefix(spellId) + ".name").formatted(Formatting.BOLD))
//                        .formatted(Formatting.GRAY));
//
//                var description = I18n.translate(spellKeyPrefix(spellId) + ".description");
//                var estimatedOutput = SpellHelper.estimate(spell, player, itemStack);
//                description = replaceTokens(description, damageToken, estimatedOutput.damage());
//                description = replaceTokens(description, healToken, estimatedOutput.heal());
//                lines.add(Text.literal(" ")
//                        .append(Text.translatable(description))
//                        .formatted(Formatting.GRAY));
//
//                var castDuration = SpellHelper.getCastDuration(player, spell, itemStack);
//                var castTimeKey = keyWithPlural("spell.tooltip.cast_time", castDuration);
//                var castTime = I18n.translate(castTimeKey).replace("{duration}", formattedNumber(castDuration));
//                lines.add(Text.literal(" ")
//                        .append(Text.literal(castTime))
//                        .formatted(Formatting.GOLD));
//
//                var rangeKey = keyWithPlural("spell.tooltip.range", spell.range);
//                var range = I18n.translate(rangeKey).replace("{range}", formattedNumber(spell.range));
//                lines.add(Text.literal(" ")
//                        .append(Text.literal(range))
//                        .formatted(Formatting.GOLD));
//
//                var cooldownDuration = SpellHelper.getCooldownDuration(player, spell, itemStack);
//                if (cooldownDuration > 0) {
//                    String cooldown;
//                    if (spell.cost.cooldown_proportional) {
//                        cooldown = I18n.translate("spell.tooltip.cooldown.proportional");
//                    } else {
//                        var cooldownKey = keyWithPlural("spell.tooltip.cooldown", cooldownDuration);
//                        cooldown = I18n.translate(cooldownKey).replace("{duration}", formattedNumber(cooldownDuration));
//                    }
//                    lines.add(Text.literal(" ")
//                            .append(Text.literal(cooldown))
//                            .formatted(Formatting.GOLD));
//                }
//
//                var showItemCost = true;
//                var config = SpellEngineMod.config;
//                if (config != null) {
//                    showItemCost = config.spell_cost_item_allowed;
//                }
//                if (showItemCost && spell.cost != null && spell.cost.item_id != null && !spell.cost.item_id.isEmpty()) {
//                    var item = Registry.ITEM.get(new Identifier(spell.cost.item_id));
//                    if (item != null) {
//                        var ammoKey = keyWithPlural("spell.tooltip.ammo", 1); // Add variable ammo count later
//                        var itemName = I18n.translate(item.getTranslationKey());
//                        var ammo = I18n.translate(ammoKey).replace("{item}", itemName);
//                        var hasItem = SpellHelper.ammoForSpell(player, spell, itemStack).satisfied();
//                        lines.add(Text.literal(" ")
//                                .append(Text.literal(ammo).formatted(hasItem ? Formatting.GREEN : Formatting.RED)));
//                    }
//                }
//            }
        }
    }

    private static String replaceTokens(String text, String token, List<SpellHelper.EstimatedValue> values) {
        for (int i = 0; i < values.size(); ++i) {
            var range = values.get(i);
            boolean indexedTokens = values.size() > 1;
            token = indexedTokens ? (token + "_" + i) : token;
            text = text.replace(token, formattedRange(range.min(), range.max()));
        }
        return text;
    }

    private static String formattedRange(double min, double max) {
        if (min == max) {
            return formattedNumber((float) min);
        }
        return formattedNumber((float) min) + " - " + formattedNumber((float) max);
    }

    private static String formattedNumber(float number) {
        DecimalFormat formatter = new DecimalFormat();
        formatter.setMaximumFractionDigits(1);
        return formatter.format(number);
    }

    private static String keyWithPlural(String key, float value) {
        if (value != 1) {
            return key + ".plural";
        }
        return key;
    }

    private static String spellKeyPrefix(Identifier spellId) {
        // For example: `spell.spell_engine.fireball`
        return "spell." + spellId.getNamespace() + "." + spellId.getPath();
    }
}
