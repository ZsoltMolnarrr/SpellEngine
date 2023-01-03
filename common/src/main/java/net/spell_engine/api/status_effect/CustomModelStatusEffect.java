package net.spell_engine.api.status_effect;

import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;

public interface CustomModelStatusEffect {
    void renderEffect(int amplifier, LivingEntity livingEntity, float delta, MatrixStack matrixStack,
                      VertexConsumerProvider vertexConsumers, int light);
}
