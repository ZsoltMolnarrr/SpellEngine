package net.spell_engine.compat.trinkets;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.spell_engine.client.SpellEngineClient;

import java.util.List;

public class SpellScrollTrinketItem extends SpellHostTrinketItem {
    public SpellScrollTrinketItem(Settings settings, SoundEvent equipSound) {
        super(settings, equipSound);
    }

    public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Text> tooltip, TooltipType type) {
        if (SpellEngineClient.config.showSpellBindingTooltip) {
            tooltip.add(Text
                    .translatable("item.spell_engine.scroll.table_hint")
                    .formatted(Formatting.GRAY)
            );
        }
    }
}
