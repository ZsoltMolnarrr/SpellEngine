package net.spell_engine.api.status_effect;

import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffect;

import java.util.HashMap;
import java.util.Map;

public final class CustomModelStatusEffect {
    private static final Map<StatusEffect, Renderer> renderers = new HashMap<>();

    public static void register(StatusEffect statusEffect, Renderer renderer) {
        renderers.put(statusEffect, renderer);
    }

    public static Renderer rendererOf(StatusEffect statusEffect) {
        return renderers.get(statusEffect);
    }

    public interface Renderer {
        void renderEffect(int amplifier, LivingEntity livingEntity, float delta, MatrixStack matrixStack,
                          VertexConsumerProvider vertexConsumers, int light);
    }
}
