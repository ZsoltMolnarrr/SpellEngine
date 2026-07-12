package net.spell_engine.api.effect;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffect;
import net.spell_engine.client.util.Color;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Makes the held item of an affected entity glow, similar to the enchantment glint,
 * but in a configurable color and much brighter.
 * <p>
 * Register from common init, on both sides: the client can only glow what it knows about,
 * and it learns of the effect through {@link Synchronized}, which the server has to agree to.
 */
public final class GlowingItemStatusEffect {
    /**
     * Glow contributed by a single status effect.
     * `opacityPerStack` is contributed once per stack (amplifier + 1), and drives how bright
     * the glow burns, so a stacked effect glows stronger than a single application of it.
     */
    public record Glow(Color color, float opacityPerStack) {
        public static final float DEFAULT_OPACITY_PER_STACK = 0.25F;

        public Glow(Color color) {
            this(color, DEFAULT_OPACITY_PER_STACK);
        }
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

    @Nullable
    public static Glow glowOf(StatusEffect statusEffect) {
        return glows.get(statusEffect);
    }

    /**
     * The combined glow of every registered glowing effect currently active on `entity`,
     * or `null` if it has none.
     * <p>
     * Colors are averaged, weighted by the opacity each effect contributes, so the stronger
     * (or more stacked) effect pulls the blend towards its own color. The alpha of the result
     * carries the total opacity of the blend, capped at fully opaque.
     */
    @Nullable
    public static Color resolve(LivingEntity entity) {
        if (glows.isEmpty()) {
            return null;
        }
        float red = 0, green = 0, blue = 0, totalOpacity = 0;
        for (var entry: Synchronized.effectsOf(entity)) {
            var glow = glows.get(entry.effect());
            if (glow == null) {
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
