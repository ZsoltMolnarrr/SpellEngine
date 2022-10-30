package net.combatspells.client;

import com.ibm.icu.text.DecimalFormat;
import net.combatspells.api.SpellHelper;
import net.combatspells.internals.SpellCasterItemStack;
import net.combatspells.internals.SpellRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;

import java.util.List;

public class SpellTooltip {

    // "enchantment.spelldamage.spell_power.desc" : "Increases the all kind of spell damage you deal",

    public static void addSpellInfo(ItemStack itemStack, List<Text> lines) {
        var player = MinecraftClient.getInstance().player;
        if (player == null) {
            return;
        }
        if ((Object)itemStack instanceof SpellCasterItemStack stack) {
            var spellId = stack.getSpellId();
            var spell = SpellRegistry.getSpell(spellId);
            if(stack.getSpellId() != null && spell != null) {
                lines.add(Text.of(""));

                lines.add(Text.translatable("spell.tooltip.when_used")
                        .append(Text.literal(" "))
                        .append(Text.translatable(spellKeyPrefix(spellId) + ".name").formatted(Formatting.BOLD))
                        .formatted(Formatting.GRAY));
                lines.add(Text.literal(" ")
                        .append(Text.translatable(spellKeyPrefix(spellId) + ".description"))
                        .formatted(Formatting.GRAY));

                var castTimeKey = keyWithPlural("spell.tooltip.cast_time", spell.cast.duration);
                var castTime = I18n.translate(castTimeKey).replace("{duration}", formattedNumber(spell.cast.duration));
                lines.add(Text.literal(" ")
                        .append(Text.literal(castTime))
                        .formatted(Formatting.GOLD));
                var rangeKey = keyWithPlural("spell.tooltip.range", spell.cast.duration);
                var range = I18n.translate(rangeKey).replace("{range}", formattedNumber(spell.range));
                lines.add(Text.literal(" ")
                        .append(Text.literal(range))
                        .formatted(Formatting.GOLD));

                if (spell.cost != null && spell.cost.item_id != null && !spell.cost.item_id.isEmpty()) {
                    var item = Registry.ITEM.get(new Identifier(spell.cost.item_id));
                    if (item != null) {
                        var ammoKey = keyWithPlural("spell.tooltip.ammo", 1); // Add variable ammo count later
                        var itemName = I18n.translate(item.getTranslationKey());
                        var ammo = I18n.translate(ammoKey).replace("{item}", itemName);
                        var hasItem = SpellHelper.ammoForSpell(player, spell, itemStack).satisfied();
                        lines.add(Text.literal(" ")
                                .append(Text.literal(ammo).formatted(hasItem ? Formatting.GREEN : Formatting.RED)));
                    }
                }
            }
        }
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
        // For example: `spell.combatspells.fireball`
        return "spell." + spellId.getNamespace() + "." + spellId.getPath();
    }
}
