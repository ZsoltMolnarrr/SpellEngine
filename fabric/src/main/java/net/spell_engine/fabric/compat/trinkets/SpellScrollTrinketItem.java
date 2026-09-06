package net.spell_engine.fabric.compat.trinkets;

import net.minecraft.client.item.TooltipContext;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.Text;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import net.minecraft.util.Formatting;
import net.spell_engine.client.SpellEngineClient;

import java.util.List;

public class SpellScrollTrinketItem extends SpellHostTrinketItem {
    public SpellScrollTrinketItem(Settings settings, SoundEvent equipSound) {
        super(settings, equipSound);
    }

    @Override
    public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
        if (SpellEngineClient.config.showSpellBindingTooltip) {
            tooltip.add(Text
                    .translatable("item.spell_engine.scroll.table_hint")
                    .formatted(Formatting.GRAY)
            );
        }
    }
}
