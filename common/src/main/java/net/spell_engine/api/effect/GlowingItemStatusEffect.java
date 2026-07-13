package net.spell_engine.api.effect;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.AttributeModifiersComponent;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.tag.TagKey;
import net.spell_engine.client.util.Color;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Makes the held weapon of an affected entity glow, similar to the enchantment glint,
 * but in a configurable color and much brighter.
 * <p>
 * Register from common init, on both sides: the client can only glow what it knows about,
 * and it learns of the effect through {@link Synchronized}, which the server has to agree to.
 */
public final class GlowingItemStatusEffect {
    /**
     * Glow contributed by a single status effect.
     * <p>
     * `opacityPerStack` is contributed once per stack (amplifier + 1), and drives how bright
     * the glow burns, so a stacked effect glows stronger than a single application of it.
     * <p>
     * `items` narrows the glow to a chosen tag. Left null, it falls back to whatever counts as a
     * weapon by {@link #isHeldEquipment}.
     */
    public record Glow(Color color, float opacityPerStack, @Nullable TagKey<Item> items) {
        public static final float DEFAULT_OPACITY_PER_STACK = 0.25F;

        public Glow(Color color) {
            this(color, DEFAULT_OPACITY_PER_STACK, null);
        }

        public Glow(Color color, float opacityPerStack) {
            this(color, opacityPerStack, null);
        }

        public boolean applies(ItemStack stack) {
            return items != null ? stack.isIn(items) : isHeldEquipment(stack);
        }
    }

    /**
     * Whether an item is one the glow should take to by default: a weapon or a tool, but not armor.
     * <p>
     * Told apart by which slot the item's attribute modifiers speak for. A weapon buffs the hand that
     * holds it, armor buffs the slot it is worn in, so a chestplate held in hand stays dark. Items with
     * nothing to say about any slot, spell books and the like, are not weapons and do not glow either.
     */
    public static boolean isHeldEquipment(ItemStack stack) {
        var modifiers = stack.getOrDefault(DataComponentTypes.ATTRIBUTE_MODIFIERS, AttributeModifiersComponent.DEFAULT);
        for (var entry: modifiers.modifiers()) {
            if (entry.slot().matches(EquipmentSlot.MAINHAND) || entry.slot().matches(EquipmentSlot.OFFHAND)) {
                return true;
            }
        }
        return false;
    }

    private static final Map<StatusEffect, Glow> glows = new HashMap<>();

    public static void register(StatusEffect statusEffect, Glow glow) {
        glows.put(statusEffect, glow);
        // A glow is only ever as visible as the effect behind it. Effects are not synchronized to
        // clients by default, and an unsynchronized one would glow on nobody, not even its own holder.
        Synchronized.configure(statusEffect, true);
    }

    public static void register(StatusEffect statusEffect, Color color, float opacityPerStack) {
        register(statusEffect, new Glow(color, opacityPerStack));
    }

    public static void register(StatusEffect statusEffect, Color color, float opacityPerStack, TagKey<Item> items) {
        register(statusEffect, new Glow(color, opacityPerStack, items));
    }

    @Nullable
    public static Glow glowOf(StatusEffect statusEffect) {
        return glows.get(statusEffect);
    }

    /**
     * The combined glow that the registered glowing effects active on `entity` cast onto `stack`,
     * or `null` if none of them reach it.
     * <p>
     * Each effect judges the item for itself, so one may light up a sword while another, bound to its own
     * tag, passes it over. Colors are averaged, weighted by the opacity each contributes, so the stronger
     * (or more stacked) effect pulls the blend towards its own color. The alpha of the result carries the
     * total opacity of the blend, capped at fully opaque.
     */
    @Nullable
    public static Color resolve(LivingEntity entity, ItemStack stack) {
        if (glows.isEmpty() || stack.isEmpty()) {
            return null;
        }
        float red = 0, green = 0, blue = 0, totalOpacity = 0;
        for (var entry: Synchronized.effectsOf(entity)) {
            var glow = glows.get(entry.effect());
            if (glow == null || !glow.applies(stack)) {
                continue;
            }
            var stacks = entry.amplifier() + 1;
            var opacity = glow.opacityPerStack() * stacks;
            if (opacity <= 0) {
                continue;
            }
            red += glow.color().red() * opacity;
            green += glow.color().green() * opacity;
            blue += glow.color().blue() * opacity;
            totalOpacity += opacity;
        }
        if (totalOpacity <= 0) {
            return null;
        }
        return new Color(red / totalOpacity, green / totalOpacity, blue / totalOpacity,
                Math.min(totalOpacity, 1F));
    }
}
