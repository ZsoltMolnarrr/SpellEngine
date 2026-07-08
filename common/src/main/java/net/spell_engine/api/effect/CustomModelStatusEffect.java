package net.spell_engine.api.effect;

import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffect;

import java.util.HashMap;
import java.util.Map;

public final class CustomModelStatusEffect {
    public interface Renderer {
        void renderEffect(int appliedAtAge, int amplifier, LivingEntity livingEntity, float delta, MatrixStack matrixStack,
                          VertexConsumerProvider vertexConsumers, int light);
        /** @deprecated Implement {@link #renderEffect(int, int, LivingEntity, float, MatrixStack, VertexConsumerProvider, int)}
         *  instead — it additionally receives {@code appliedAtAge}. Retained only for binary compatibility. */
        @Deprecated(forRemoval = true)
        default void renderEffect(int amplifier, LivingEntity livingEntity, float delta, MatrixStack matrixStack,
                          VertexConsumerProvider vertexConsumers, int light) {
            renderEffect(0, amplifier, livingEntity, delta, matrixStack, vertexConsumers, light);
        }
    }
    public record Args(boolean scaleWithEntity) {
        public static final Args DEFAULT = new Args(true);
    }
    public record Entry(Renderer renderer, Args args) { }

    private static final Map<StatusEffect, Entry> renderers = new HashMap<>();

    public static void register(StatusEffect statusEffect, Renderer renderer) {
        register(statusEffect, renderer, Args.DEFAULT);
    }

    public static void register(StatusEffect statusEffect, Renderer renderer, Args args) {
        renderers.put(statusEffect, new Entry(renderer, args));
    }

    public static Entry entryOf(StatusEffect statusEffect) {
        return renderers.get(statusEffect);
    }

    public static Renderer rendererOf(StatusEffect statusEffect) {
        return renderers.get(statusEffect).renderer();
    }
}
