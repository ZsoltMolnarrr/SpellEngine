package net.spell_engine.client.gui;

import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

/// Vanilla-style attribute modifier tooltip lines (`+5 Attack Damage`, `+10% Movement Speed`, ...).
///
/// 1.21 exposes this as `ItemStack.appendAttributeModifierTooltip` (which the former
/// `ItemStackTooltipAccessor` invoked); on 1.20.1 the same logic is inlined into `ItemStack.getTooltip`,
/// so it is reproduced here for the lines Spell Engine renders outside an item's own modifiers
/// (equipment set bonuses). Mirrors the 1.20.1 `ItemStack.getTooltip` attribute loop line by line,
/// minus the "equals" (base-merged) branch, which only applies to an item's own attack modifiers.
public final class AttributeModifierTooltip {
    private AttributeModifierTooltip() { }

    public static void append(Consumer<Text> textConsumer, @Nullable PlayerEntity player,
                              EntityAttribute attribute, EntityAttributeModifier modifier) {
        double value = modifier.getValue();
        double shown;
        if (modifier.getOperation() == EntityAttributeModifier.Operation.MULTIPLY_BASE
                || modifier.getOperation() == EntityAttributeModifier.Operation.MULTIPLY_TOTAL) {
            shown = value * 100.0;
        } else if (attribute.equals(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE)) {
            shown = value * 10.0;
        } else {
            shown = value;
        }
        var operationId = modifier.getOperation().getId();
        var attributeName = Text.translatable(attribute.getTranslationKey());
        if (value > 0.0) {
            textConsumer.accept(Text.translatable("attribute.modifier.plus." + operationId,
                    ItemStack.MODIFIER_FORMAT.format(shown), attributeName).formatted(Formatting.BLUE));
        } else if (value < 0.0) {
            shown *= -1.0;
            textConsumer.accept(Text.translatable("attribute.modifier.take." + operationId,
                    ItemStack.MODIFIER_FORMAT.format(shown), attributeName).formatted(Formatting.RED));
        } else {
            // 1.21 prints a zero modifier as an "equals" line; keep that so set bonuses never vanish silently.
            textConsumer.accept(ScreenTexts.space().append(Text.translatable("attribute.modifier.equals." + operationId,
                    ItemStack.MODIFIER_FORMAT.format(shown), attributeName)).formatted(Formatting.DARK_GREEN));
        }
    }
}
