package net.spell_engine.neoforge.compat.curios;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.spell_engine.client.SpellEngineClient;

import java.util.List;

public class SpellScrollCurioItem extends SpellHostCurioItem {
    public SpellScrollCurioItem(Item.Properties settings, SoundEvent equipSound) {
        super(settings, equipSound);
    }

    public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag type) {
        if (SpellEngineClient.config.showSpellBindingTooltip) {
            tooltip.add(Component
                    .translatable("item.spell_engine.scroll.table_hint")
                    .withStyle(ChatFormatting.GRAY)
            );
        }
    }
}
